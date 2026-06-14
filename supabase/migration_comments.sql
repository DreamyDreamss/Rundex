-- 러닝 댓글 — 피드/프로필에서 공개 러닝에 댓글. public_feed 에 comment_count 추가.
create table if not exists run_comments (
  id uuid primary key default gen_random_uuid(),
  run_id uuid not null references runs(id) on delete cascade,
  user_id uuid not null references profiles(id) on delete cascade,
  text text not null,
  created_at timestamptz not null default now()
);
alter table run_comments enable row level security;

-- 공개 러닝이거나 내 러닝의 댓글만 읽기
drop policy if exists rc_read on run_comments;
create policy rc_read on run_comments for select using (
  exists(select 1 from runs r where r.id = run_comments.run_id
         and (r.visibility = 'public' or r.user_id = auth.uid())));
-- 본인 댓글만 작성/수정/삭제
drop policy if exists rc_own on run_comments;
create policy rc_own on run_comments for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
create index if not exists rc_run_idx on run_comments(run_id, created_at);

-- public_feed 에 comment_count 컬럼 추가(컬럼 추가 위해 drop 후 재생성)
drop view if exists public_feed;
create view public_feed with (security_invoker = true) as
select r.id as run_id, r.user_id, p.display_name, p.handle, p.rep_title_ids,
  r.distance_m, r.duration_ms, r.started_at, r.created_at, r.caption, r.tags,
  st_asgeojson(r.geom) as geojson,
  (select count(*) from run_region_ledger l where l.run_id = r.id) as region_count,
  (select count(*) from run_reactions rr where rr.run_id = r.id) as like_count,
  (select count(*) from run_comments c where c.run_id = r.id) as comment_count,
  exists(select 1 from run_reactions rr where rr.run_id = r.id and rr.user_id = auth.uid()) as liked_by_me
from runs r join profiles p on p.id = r.user_id
where r.visibility = 'public';
