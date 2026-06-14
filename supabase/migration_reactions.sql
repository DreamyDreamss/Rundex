-- ───────────────────────────────────────────────────────────────
-- 마이그레이션: 피드 좋아요(run_reactions) + public_feed 노출
-- ───────────────────────────────────────────────────────────────

create table if not exists run_reactions (
  run_id  uuid references runs(id) on delete cascade,
  user_id uuid references profiles(id) on delete cascade,
  emoji   text not null default '❤️',
  created_at timestamptz not null default now(),
  primary key (run_id, user_id)
);
alter table run_reactions enable row level security;
drop policy if exists runrx_own on run_reactions;
create policy runrx_own on run_reactions for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
drop policy if exists runrx_read on run_reactions;
create policy runrx_read on run_reactions for select using (
  exists (select 1 from runs r where r.id = run_reactions.run_id and r.visibility = 'public'));

-- public_feed 재생성 — like_count, liked_by_me 추가
drop view if exists public_feed;
create view public_feed
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
    r.caption       as caption,
    r.tags          as tags,
    st_asgeojson(r.geom) as geojson,
    (select count(*) from run_region_ledger l where l.run_id = r.id) as region_count,
    (select count(*) from run_reactions rr where rr.run_id = r.id) as like_count,
    exists(select 1 from run_reactions rr where rr.run_id = r.id and rr.user_id = auth.uid()) as liked_by_me
  from runs r
  join profiles p on p.id = r.user_id
  where r.visibility = 'public';
