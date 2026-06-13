-- 러닝 도감 — 서버 권위 로직 (RPC)
-- 클라 ApiClient.submitRun → supabase.rpc('submit_run', { payload }) 로 호출

-- 누적 거리 → 등급 라벨 (앱 Grades.kt 와 동일 규칙)
create or replace function grade_of(total_m double precision) returns text as $$
  select case
    when total_m >= 200000 then 'GOLD'
    when total_m >=  50000 then 'SILVER'
    when total_m >=  10000 then 'BRONZE'
    else 'CARD' end;
$$ language sql immutable;

-- 러닝 업로드 처리: 동거리 분배 + 도감/등급 + 테마 + 칭호
-- payload: { startedAt(ms), endedAt(ms), distanceM, durationMs, source,
--            coordinates: [[lon,lat], ...] }
create or replace function submit_run(payload jsonb)
returns jsonb
language plpgsql
security definer
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

  -- 좌표 → LineString
  select st_makeline(array(
    select st_setsrid(st_makepoint((c->>0)::float, (c->>1)::float), 4326)
    from jsonb_array_elements(payload->'coordinates') c
  )) into line;

  insert into runs(user_id, started_at, ended_at, distance_m, duration_ms, source, geom)
  values (
    uid,
    to_timestamp((payload->>'startedAt')::bigint / 1000.0),
    to_timestamp((payload->>'endedAt')::bigint / 1000.0),
    (payload->>'distanceM')::float,
    (payload->>'durationMs')::bigint,
    coalesce(payload->>'source','free'),
    line
  ) returning id into new_run_id;

  -- 동별 거리 분배 (경로 ∩ 행정동)
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

  -- 테마 도감 (반경 통과)
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

  -- 칭호 평가 (마일스톤/등급) — 보유 안 한 것만 부여
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

  -- 활동 피드
  if jsonb_array_length(new_regions) > 0 or jsonb_array_length(grade_ups) > 0 then
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

-- 크루 생성: 크루 + 소유자 멤버십을 한 번에 만들고 크루를 반환
create or replace function create_crew(crew_name text)
returns crews
language plpgsql security definer as $$
declare uid uuid := auth.uid(); c crews;
begin
  if uid is null then raise exception 'unauthenticated'; end if;
  insert into crews(name, owner_id) values (crew_name, uid) returning * into c;
  insert into crew_members(crew_id, user_id, role) values (c.id, uid, 'owner');
  return c;
end; $$;

-- 크루 가입: 가입 코드로 크루를 찾아 멤버로 추가하고 크루를 반환
create or replace function join_crew(code text)
returns crews
language plpgsql security definer as $$
declare uid uuid := auth.uid(); c crews;
begin
  if uid is null then raise exception 'unauthenticated'; end if;
  select * into c from crews where join_code = lower(code);
  if c.id is null then raise exception 'crew not found'; end if;
  insert into crew_members(crew_id, user_id) values (c.id, uid)
    on conflict do nothing;
  return c;
end; $$;
