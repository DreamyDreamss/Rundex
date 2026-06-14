"""폰 계정에 도감(서울 동 색칠) + 러닝 기록(공개/비공개, 캡션·태그) 시드."""
import json, urllib.request

import os
PAT = os.environ["SUPABASE_PAT"]   # export SUPABASE_PAT=sbp_... 로 주입
REF = "jtbtqmcwjtuzsfpqzunf"
UID = "37baec39-079b-46eb-bdff-95f51e729879"

SQL = f"""
do $$
declare
  uid uuid := '{UID}';
  codes text[];
  rid uuid;
begin
  -- 1) 도감: 서울 중심부 20개 동을 4등급으로 색칠
  with seoul as (
    select code, row_number() over (order by code) rn
    from regions
    where st_y(st_centroid(geom)) between 37.50 and 37.60
      and st_x(st_centroid(geom)) between 126.95 and 127.08
    limit 20
  )
  insert into dex_entries(user_id, region_code, total_m)
  select uid, code, (array[4000,18000,70000,260000])[1 + (rn % 4)::int]
  from seoul
  on conflict (user_id, region_code) do update set total_m = excluded.total_m;

  select array_agg(region_code order by region_code) into codes
    from dex_entries where user_id = uid;

  -- 2) 러닝 기록 (공개 2 + 비공개 2)
  insert into runs(user_id, started_at, ended_at, distance_m, duration_ms, source, visibility, caption, tags, geom)
  values (uid, now()-interval '3 days', now()-interval '3 days'+interval '42 min', 7200, 2520000, 'free', 'public',
    '오랜만에 장거리 한 바퀴 🔥', array['장거리','한강','주말런'], null) returning id into rid;
  insert into run_region_ledger(run_id, region_code, meters)
    select rid, c, 1100 from unnest(codes[1:6]) c on conflict do nothing;

  insert into runs(user_id, started_at, ended_at, distance_m, duration_ms, source, visibility, caption, tags, geom)
  values (uid, now()-interval '1 day'-interval '4 hours', now()-interval '1 day'-interval '4 hours'+interval '24 min', 4300, 1440000, 'free', 'public',
    '퇴근 후 가볍게 야간런 🌙', array['야간런','5K','루틴'], null) returning id into rid;
  insert into run_region_ledger(run_id, region_code, meters)
    select rid, c, 700 from unnest(codes[4:9]) c on conflict do nothing;

  insert into runs(user_id, started_at, ended_at, distance_m, duration_ms, source, visibility, geom)
  values (uid, now()-interval '5 days', now()-interval '5 days'+interval '30 min', 5000, 1800000, 'free', 'private', null)
  returning id into rid;
  insert into run_region_ledger(run_id, region_code, meters)
    select rid, c, 800 from unnest(codes[2:5]) c on conflict do nothing;

  insert into runs(user_id, started_at, ended_at, distance_m, duration_ms, source, visibility, geom)
  values (uid, now()-interval '8 hours', now()-interval '8 hours'+interval '18 min', 3200, 1080000, 'free', 'private', null)
  returning id into rid;
  insert into run_region_ledger(run_id, region_code, meters)
    select rid, c, 600 from unnest(codes[7:12]) c on conflict do nothing;
end $$;
"""

req = urllib.request.Request(
    f"https://api.supabase.com/v1/projects/{REF}/database/query",
    data=json.dumps({"query": SQL}).encode(), method="POST")
req.add_header("Authorization", f"Bearer {PAT}")
req.add_header("Content-Type", "application/json")
with urllib.request.urlopen(req) as r:
    print("apply HTTP", r.status, r.read().decode()[:200])

# 검증
for label, q in [
    ("dex 동수/등급", f"select count(*) n, round(min(total_m)) lo, round(max(total_m)) hi from dex_entries where user_id='{UID}'"),
    ("runs(공개/비공개)", f"select visibility, count(*) c from runs where user_id='{UID}' group by visibility"),
]:
    req = urllib.request.Request(
        f"https://api.supabase.com/v1/projects/{REF}/database/query",
        data=json.dumps({"query": q}).encode(), method="POST")
    req.add_header("Authorization", f"Bearer {PAT}")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req) as r:
        print(label, "->", r.read().decode())
