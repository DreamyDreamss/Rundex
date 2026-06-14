-- ───────────────────────────────────────────────────────────────
-- 마이그레이션: 크루 커뮤니티 (추천 크루 + 채팅 + 챌린지)
-- ───────────────────────────────────────────────────────────────

alter table crews add column if not exists description text;
alter table crews add column if not exists is_public boolean not null default true;
alter table crew_goals add column if not exists title text;

-- crew_members RLS 무한재귀(42P17) 수정 — security definer 함수로 멤버십 확인
create or replace function is_crew_member(c uuid, u uuid) returns boolean
  language sql security definer stable set search_path = public as $$
    select exists(select 1 from crew_members m where m.crew_id = c and m.user_id = u);
  $$;
drop policy if exists cm_read on crew_members;
create policy cm_read on crew_members for select using (is_crew_member(crew_id, auth.uid()));

-- 크루 채팅 ------------------------------------------------------
create table if not exists crew_messages (
  id uuid primary key default gen_random_uuid(),
  crew_id uuid references crews(id) on delete cascade,
  user_id uuid references profiles(id) on delete cascade,
  text text not null,
  created_at timestamptz not null default now()
);
create index if not exists crew_messages_idx on crew_messages(crew_id, created_at);
alter table crew_messages enable row level security;
drop policy if exists crewmsg_read on crew_messages;
create policy crewmsg_read on crew_messages for select using (
  exists (select 1 from crew_members m where m.crew_id = crew_messages.crew_id and m.user_id = auth.uid()));
drop policy if exists crewmsg_write on crew_messages;
create policy crewmsg_write on crew_messages for insert with check (
  user_id = auth.uid()
  and exists (select 1 from crew_members m where m.crew_id = crew_messages.crew_id and m.user_id = auth.uid()));

-- 공개(추천) 크루 목록 — 멤버 수 + 가입여부 -----------------------
create or replace function list_public_crews()
returns table(id uuid, name text, description text, member_count bigint, joined boolean)
language sql security definer set search_path = public as $$
  select c.id, c.name, c.description,
    (select count(*) from crew_members m where m.crew_id = c.id) as member_count,
    exists(select 1 from crew_members m where m.crew_id = c.id and m.user_id = auth.uid()) as joined
  from crews c
  where c.is_public
  order by member_count desc, c.created_at desc
  limit 50;
$$;

-- id 로 크루 가입 -----------------------------------------------
create or replace function join_crew_by_id(p_crew_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'unauthenticated'; end if;
  insert into crew_members(crew_id, user_id) values (p_crew_id, uid) on conflict do nothing;
end; $$;

-- 크루 상세(멤버 + 내가 멤버인지) -------------------------------
create or replace function crew_detail(p_crew_id uuid)
returns jsonb language sql security definer set search_path = public as $$
  select jsonb_build_object(
    'id', c.id, 'name', c.name, 'description', c.description,
    'joinCode', c.join_code,
    'isMember', exists(select 1 from crew_members m where m.crew_id=c.id and m.user_id=auth.uid()),
    'members', coalesce((
      select jsonb_agg(jsonb_build_object('name', p.display_name, 'role', m.role) order by m.joined_at)
      from crew_members m join profiles p on p.id = m.user_id where m.crew_id = c.id), '[]')
  ) from crews c where c.id = p_crew_id;
$$;

-- 활성 챌린지 진행률(멤버 기간 누적거리) ------------------------
create or replace function crew_challenge(p_crew_id uuid)
returns jsonb language sql security definer set search_path = public as $$
  with g as (
    select * from crew_goals where crew_id = p_crew_id
    order by coalesce(period_end, date '2999-01-01') desc limit 1
  )
  select case when (select id from g) is null then null else (
    select jsonb_build_object(
      'title', coalesce(g.title, '크루 챌린지'),
      'targetM', g.target,
      'progressM', coalesce((
        select sum(r.distance_m) from runs r
        join crew_members m on m.user_id = r.user_id
        where m.crew_id = p_crew_id
          and (g.period_start is null or r.started_at >= g.period_start)
          and (g.period_end is null or r.started_at < g.period_end + interval '1 day')), 0),
      'periodEnd', g.period_end)
    from g) end;
$$;

-- 챌린지 생성(멤버) — km 목표 + 일수 ----------------------------
create or replace function set_crew_challenge(p_crew_id uuid, p_title text, p_target_km double precision, p_days int)
returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'unauthenticated'; end if;
  if not exists(select 1 from crew_members m where m.crew_id = p_crew_id and m.user_id = uid)
    then raise exception 'not a member'; end if;
  insert into crew_goals(crew_id, kind, target, progress, period_start, period_end, title)
    values (p_crew_id, 'distance', p_target_km * 1000, 0, current_date, current_date + p_days, p_title);
end; $$;

-- [2026-06] crew_challenge 에 기여도 리더보드(contributors) 추가 — 멤버별 누적 km
create or replace function crew_challenge(p_crew_id uuid)
returns jsonb language sql security definer set search_path to 'public' as $function$
  with g as (
    select * from crew_goals where crew_id = p_crew_id
    order by coalesce(period_end, date '2999-01-01') desc limit 1
  )
  select case when (select id from g) is null then null else (
    select jsonb_build_object(
      'title', coalesce(g.title, '크루 챌린지'),
      'targetM', g.target,
      'progressM', coalesce((
        select sum(r.distance_m) from runs r
        join crew_members m on m.user_id = r.user_id
        where m.crew_id = p_crew_id
          and (g.period_start is null or r.started_at >= g.period_start)
          and (g.period_end is null or r.started_at < g.period_end + interval '1 day')), 0),
      'periodEnd', g.period_end,
      'contributors', coalesce((
        select jsonb_agg(jsonb_build_object('name', p.display_name, 'm', sub.m) order by sub.m desc)
        from (
          select r.user_id, sum(r.distance_m) m from runs r
          join crew_members mm on mm.user_id = r.user_id
          where mm.crew_id = p_crew_id
            and (g.period_start is null or r.started_at >= g.period_start)
            and (g.period_end is null or r.started_at < g.period_end + interval '1 day')
          group by r.user_id
        ) sub join profiles p on p.id = sub.user_id), '[]'))
    from g) end;
$function$;
