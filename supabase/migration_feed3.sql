-- ───────────────────────────────────────────────────────────────
-- 마이그레이션 3: 도감 동별 내 기록 보기
-- ───────────────────────────────────────────────────────────────

-- 본인 런의 원장은 본인이 읽기 (비공개 포함) — 도감 동별 기록 조회용
drop policy if exists ledger_own_read on run_region_ledger;
create policy ledger_own_read on run_region_ledger for select using (
  exists (select 1 from runs r
          where r.id = run_region_ledger.run_id and r.user_id = auth.uid()));

-- 특정 행정동에서 내가 뛴 런 목록 (최신순)
create or replace function my_runs_in_region(p_region_code text)
returns table(run_id uuid, started_at timestamptz, distance_m double precision,
              duration_ms bigint, meters double precision, visibility text)
language sql security definer set search_path = public as $$
  select r.id, r.started_at, r.distance_m, r.duration_ms, l.meters, r.visibility
  from run_region_ledger l
  join runs r on r.id = l.run_id
  where l.region_code = p_region_code and r.user_id = auth.uid()
  order by r.started_at desc;
$$;
