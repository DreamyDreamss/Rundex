-- ───────────────────────────────────────────────────────────────
-- 러닝도감 마이그레이션: 홈 피드 + 공개여부 + 고유 닉네임
-- 기존 DB(schema.sql/functions.sql 적용본) 위에 "한 번" 실행하면 안전합니다.
-- (create or replace / drop policy if exists 가드로 재실행에도 깨지지 않음)
-- 적용: Supabase 대시보드 → SQL Editor → 붙여넣기 → Run
-- ───────────────────────────────────────────────────────────────

-- 1) 신규 가입 시 DB에 겹치지 않는 기본 닉네임/핸들 부여 ----------------
--    display_name: '러너#A3F9' (난수 4자리) / handle: 'runner_xxxxxxxx'(unique)
create or replace function handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  u text := replace(gen_random_uuid()::text, '-', '');
begin
  insert into public.profiles(id, handle, display_name)
    values (new.id, 'runner_' || substr(u, 1, 8), '러너#' || upper(substr(u, 1, 4)))
    on conflict (id) do nothing;
  return new;
end; $$;

-- 2) 공개(public) 런은 누구나 읽기 — 피드용 ---------------------------
drop policy if exists runs_public_read on runs;
create policy runs_public_read on runs for select using (visibility = 'public');

-- 2-1) 공개 런의 동거리 원장은 누구나 읽기 (피드 '지나는 동 N곳' 집계용)
alter table run_region_ledger enable row level security;
drop policy if exists ledger_public_read on run_region_ledger;
create policy ledger_public_read on run_region_ledger for select using (
  exists (select 1 from runs r where r.id = run_region_ledger.run_id and r.visibility = 'public'));

-- 3) 홈 피드 뷰 (인스타형): 공개 런 + 작성자 프로필 + 경로(GeoJSON) -------
create or replace view public_feed
  with (security_invoker = on) as
  select
    r.id            as run_id,
    r.user_id       as user_id,
    p.display_name  as display_name,
    p.handle        as handle,
    p.rep_title_ids as rep_title_ids,
    r.distance_m    as distance_m,
    r.duration_ms   as duration_ms,
    r.started_at    as started_at,
    r.created_at    as created_at,
    st_asgeojson(r.geom) as geojson,
    (select count(*) from run_region_ledger l where l.run_id = r.id) as region_count
  from runs r
  join profiles p on p.id = r.user_id
  where r.visibility = 'public';

-- 4) 러닝 업로드 RPC — payload.visibility 반영 + 공개일 때만 피드 게시 ----
create or replace function submit_run(payload jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := auth.uid();
  line geometry;
  new_run_id uuid;
  rec record;
  before_m double precision;
  after_m double precision;
  new_regions jsonb := '[]';
  grade_ups   jsonb := '[]';
  theme_delta jsonb := '[]';
  new_titles  jsonb := '[]';
  discovered_count int;
  lifetime_m double precision;
begin
  if uid is null then raise exception 'unauthenticated'; end if;

  select st_makeline(array(
    select st_setsrid(st_makepoint((c->>0)::float, (c->>1)::float), 4326)
    from jsonb_array_elements(payload->'coordinates') c
  )) into line;

  insert into runs(user_id, started_at, ended_at, distance_m, duration_ms, source, visibility, geom)
  values (
    uid,
    to_timestamp((payload->>'startedAt')::bigint / 1000.0),
    to_timestamp((payload->>'endedAt')::bigint / 1000.0),
    (payload->>'distanceM')::float,
    (payload->>'durationMs')::bigint,
    coalesce(payload->>'source','free'),
    coalesce(payload->>'visibility','private'),
    line
  ) returning id into new_run_id;

  for rec in
    select r.code, r.name,
           st_length(geography(st_intersection(line, r.geom))) as meters
    from regions r
    where line is not null and st_intersects(line, r.geom)
  loop
    if rec.meters <= 0 then continue; end if;

    insert into run_region_ledger(run_id, region_code, meters)
    values (new_run_id, rec.code, rec.meters)
    on conflict do nothing;

    select total_m into before_m from dex_entries
      where user_id = uid and region_code = rec.code;

    if before_m is null then
      insert into dex_entries(user_id, region_code, first_visit_at, total_m)
        values (uid, rec.code, now(), rec.meters);
      new_regions := new_regions || jsonb_build_object('code', rec.code, 'name', rec.name);
    else
      after_m := before_m + rec.meters;
      update dex_entries set total_m = after_m
        where user_id = uid and region_code = rec.code;
      if grade_of(before_m) <> grade_of(after_m) then
        grade_ups := grade_ups || jsonb_build_object(
          'name', rec.name, 'from', grade_of(before_m), 'to', grade_of(after_m));
      end if;
    end if;
  end loop;

  for rec in
    select tp.id, tp.name, tc.slug, tc.title
    from theme_places tp join theme_collections tc on tc.id = tp.collection_id
    where line is not null
      and st_dwithin(geography(line), geography(tp.geom), tp.radius_m)
      and not exists (select 1 from theme_progress p where p.user_id = uid and p.place_id = tp.id)
  loop
    insert into theme_progress(user_id, place_id, run_id) values (uid, rec.id, new_run_id);
    theme_delta := theme_delta || jsonb_build_object('slug', rec.slug, 'place', rec.name);
  end loop;

  select count(*) into discovered_count from dex_entries where user_id = uid;
  select coalesce(sum(distance_m),0) into lifetime_m from runs where user_id = uid;

  with cand as (
    select t.id, t.code, t.name from titles t
    where not exists (select 1 from user_titles ut where ut.user_id = uid and ut.title_id = t.id)
      and (
        (t.type='milestone' and t.criteria ? 'discovered' and discovered_count >= (t.criteria->>'discovered')::int) or
        (t.type='milestone' and t.criteria ? 'lifetimeM'  and lifetime_m      >= (t.criteria->>'lifetimeM')::float) or
        (t.type='grade'     and exists (select 1 from dex_entries d where d.user_id=uid and grade_of(d.total_m) = upper(t.criteria->>'grade')))
      )
  ), ins as (
    insert into user_titles(user_id, title_id)
      select uid, id from cand returning title_id
  )
  select coalesce(jsonb_agg(jsonb_build_object('name', c.name)), '[]') into new_titles from cand c;

  -- 공개(public) 런만 피드에 게시한다(비공개는 기록만 저장)
  if coalesce(payload->>'visibility','private') = 'public' then
    insert into activities(user_id, type, payload) values (
      uid, 'run_completed',
      jsonb_build_object('runId', new_run_id, 'distanceM', (payload->>'distanceM')::float,
        'newRegions', new_regions, 'gradeUps', grade_ups, 'themes', theme_delta));
  end if;

  return jsonb_build_object(
    'runId', new_run_id,
    'newRegions', new_regions,
    'gradeUps', grade_ups,
    'themeDelta', theme_delta,
    'newTitles', new_titles
  );
end;
$$;

-- 5) 과거 런 공개/비공개 전환 RPC ------------------------------------
create or replace function set_run_visibility(p_run_id uuid, p_visibility text)
returns void
language plpgsql security definer set search_path = public as $$
declare uid uuid := auth.uid(); r runs;
begin
  if uid is null then raise exception 'unauthenticated'; end if;
  if p_visibility not in ('public','private') then raise exception 'bad visibility'; end if;

  update runs set visibility = p_visibility
    where id = p_run_id and user_id = uid
    returning * into r;
  if r.id is null then raise exception 'run not found'; end if;

  if p_visibility = 'public' then
    insert into activities(user_id, type, payload)
      select uid, 'run_completed',
             jsonb_build_object('runId', r.id, 'distanceM', r.distance_m)
      where not exists (
        select 1 from activities a
        where a.user_id = uid and a.payload->>'runId' = r.id::text);
  else
    delete from activities
      where user_id = uid and payload->>'runId' = r.id::text;
  end if;
end; $$;
