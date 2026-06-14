-- 러닝 도감 — Supabase 스키마 (갈래 A: Supabase 단독)
-- Supabase SQL Editor에 그대로 실행. 인증은 Supabase Auth(익명 로그인) 사용.
-- 적용 순서: 1) schema.sql  2) functions.sql  3) seed.sql(선택)

create extension if not exists postgis;

-- 프로필 (auth.users 1:1) -------------------------------------------
create table if not exists profiles (
  id            uuid primary key references auth.users(id) on delete cascade,
  handle        text unique,
  display_name  text not null default '러너',
  bio           text,
  rep_title_ids text[] not null default '{}',
  created_at    timestamptz not null default now()
);

-- 신규 가입 시 프로필 자동 생성
-- ※ security definer 함수는 search_path 를 고정해야 한다. 익명 가입은 auth 컨텍스트에서
--   트리거가 도는데 search_path 에 public 이 없으면 profiles 를 못 찾아 가입이 500 으로 실패한다.
create or replace function handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  u text := replace(gen_random_uuid()::text, '-', '');
begin
  -- 신규 사용자에게 DB에 겹치지 않는 기본 닉네임/핸들 부여
  --   display_name: '러너#A3F9' (친근한 표시용, 난수 4자리)
  --   handle:       'runner_xxxxxxxx' (unique 제약으로 고유 보장, 난수 8자리)
  insert into public.profiles(id, handle, display_name)
    values (new.id, 'runner_' || substr(u, 1, 8), '러너#' || upper(substr(u, 1, 4)))
    on conflict (id) do nothing;
  return new;
end; $$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function handle_new_user();

-- 행정동 경계(전국) -------------------------------------------------
create table if not exists regions (
  code text primary key,
  name text not null,
  sido text, sigungu text,
  geom geometry(MultiPolygon,4326) not null
);
create index if not exists regions_geom_gix on regions using gist(geom);

-- 러닝 기록 --------------------------------------------------------
create table if not exists runs (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references profiles(id) on delete cascade,
  started_at  timestamptz not null,
  ended_at    timestamptz not null,
  distance_m  double precision not null,
  duration_ms bigint not null,
  source      text not null default 'free',
  visibility  text not null default 'private',
  geom        geometry(LineString,4326),
  created_at  timestamptz not null default now()
);
create index if not exists runs_user_idx on runs(user_id, started_at desc);
create index if not exists runs_geom_gix on runs using gist(geom);

create table if not exists run_region_ledger (
  run_id uuid references runs(id) on delete cascade,
  region_code text references regions(code),
  meters double precision not null,
  primary key(run_id, region_code)
);

-- 발견 도감 -------------------------------------------------------
create table if not exists dex_entries (
  user_id uuid references profiles(id) on delete cascade,
  region_code text references regions(code),
  first_visit_at timestamptz not null default now(),
  total_m double precision not null default 0,
  primary key(user_id, region_code)
);

-- 테마 도감 -------------------------------------------------------
create table if not exists theme_collections (
  id uuid primary key default gen_random_uuid(),
  slug text unique not null,
  title text not null,
  cover_url text
);
create table if not exists theme_places (
  id uuid primary key default gen_random_uuid(),
  collection_id uuid references theme_collections(id) on delete cascade,
  name text not null,
  geom geometry(Point,4326) not null,
  radius_m integer not null default 120
);
create index if not exists theme_places_geom_gix on theme_places using gist(geom);
create table if not exists theme_progress (
  user_id uuid references profiles(id) on delete cascade,
  place_id uuid references theme_places(id) on delete cascade,
  collected_at timestamptz not null default now(),
  run_id uuid references runs(id),
  primary key(user_id, place_id)
);

-- 칭호 -----------------------------------------------------------
create table if not exists titles (
  id uuid primary key default gen_random_uuid(),
  code text unique not null,
  name text not null,
  type text not null,          -- complete|grade|milestone|limited
  criteria jsonb not null default '{}'
);
create table if not exists user_titles (
  user_id uuid references profiles(id) on delete cascade,
  title_id uuid references titles(id),
  earned_at timestamptz not null default now(),
  primary key(user_id, title_id)
);
-- 보유율(희소성) 뷰
create or replace view title_rarity as
  select t.code,
         count(ut.user_id) as owners,
         coalesce(count(ut.user_id)::float / nullif((select count(*) from profiles),0), 0) as rarity
  from titles t left join user_titles ut on ut.title_id = t.id
  group by t.code;

-- 추천 경로(UGC) -------------------------------------------------
create table if not exists routes (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid references profiles(id) on delete set null,
  name text not null,
  distance_m double precision not null,
  difficulty smallint not null default 1,
  geom geometry(LineString,4326) not null,
  is_public boolean not null default true,
  created_at timestamptz not null default now()
);
create index if not exists routes_geom_gix on routes using gist(geom);
create table if not exists route_ratings (
  route_id uuid references routes(id) on delete cascade,
  user_id uuid references profiles(id) on delete cascade,
  stars smallint not null check (stars between 1 and 5),
  review text,
  created_at timestamptz not null default now(),
  primary key(route_id, user_id)
);

-- 소셜 ----------------------------------------------------------
create table if not exists follows (
  follower_id uuid references profiles(id) on delete cascade,
  followee_id uuid references profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(follower_id, followee_id)
);
create table if not exists activities (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references profiles(id) on delete cascade,
  type text not null,
  payload jsonb not null default '{}',
  created_at timestamptz not null default now()
);
create index if not exists activities_user_idx on activities(user_id, created_at desc);
create table if not exists reactions (
  activity_id uuid references activities(id) on delete cascade,
  user_id uuid references profiles(id) on delete cascade,
  emoji text not null,
  primary key(activity_id, user_id, emoji)
);

-- 크루 ----------------------------------------------------------
create table if not exists crews (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  owner_id uuid references profiles(id) on delete set null,
  join_code text unique not null default substr(replace(gen_random_uuid()::text,'-',''),1,6),
  created_at timestamptz not null default now()
);
create table if not exists crew_members (
  crew_id uuid references crews(id) on delete cascade,
  user_id uuid references profiles(id) on delete cascade,
  role text not null default 'member',
  joined_at timestamptz not null default now(),
  primary key(crew_id, user_id)
);
create table if not exists crew_goals (
  id uuid primary key default gen_random_uuid(),
  crew_id uuid references crews(id) on delete cascade,
  kind text not null,                 -- distance|regions ...
  target double precision not null,
  progress double precision not null default 0,
  period_start date, period_end date
);

-- RLS -----------------------------------------------------------
alter table profiles       enable row level security;
alter table runs           enable row level security;
alter table dex_entries    enable row level security;
alter table theme_progress enable row level security;
alter table user_titles    enable row level security;
alter table routes         enable row level security;
alter table route_ratings  enable row level security;
alter table follows        enable row level security;
alter table activities     enable row level security;
alter table reactions      enable row level security;

-- 프로필: 누구나 읽기, 본인만 수정
create policy profiles_read   on profiles for select using (true);
create policy profiles_write  on profiles for update using (auth.uid() = id);
-- 본인 소유 데이터만 읽고 쓰기
create policy runs_own   on runs   for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
-- 공개(public) 런은 누구나 읽기 — 피드용. (permissive 정책은 OR 결합되어 본인 비공개 런도 본인은 계속 보임)
create policy runs_public_read on runs for select using (visibility = 'public');
-- 공개 런의 동거리 원장은 누구나 읽기 (피드 '지나는 동 N곳' 집계용)
alter table run_region_ledger enable row level security;
create policy ledger_public_read on run_region_ledger for select using (
  exists (select 1 from runs r where r.id = run_region_ledger.run_id and r.visibility = 'public'));
create policy dex_own    on dex_entries for select using (auth.uid() = user_id);
create policy theme_own  on theme_progress for select using (auth.uid() = user_id);
create policy ut_own     on user_titles for select using (auth.uid() = user_id);
-- 추천 경로: 공개는 누구나 읽기, 등록/수정은 본인
create policy routes_read  on routes for select using (is_public or auth.uid() = owner_id);
create policy routes_write on routes for all using (auth.uid() = owner_id) with check (auth.uid() = owner_id);
create policy ratings_rw   on route_ratings for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy ratings_read on route_ratings for select using (true);
-- 소셜
create policy follows_rw   on follows for all using (auth.uid() = follower_id) with check (auth.uid() = follower_id);
create policy follows_read on follows for select using (true);
create policy act_read     on activities for select using (true);
create policy react_rw     on reactions for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- 크루: 멤버만 읽기, 본인 멤버십만 관리 (생성/가입은 RPC가 처리)
alter table crews        enable row level security;
alter table crew_members enable row level security;
alter table crew_goals   enable row level security;
create policy crews_read   on crews for select using (
  exists (select 1 from crew_members m where m.crew_id = crews.id and m.user_id = auth.uid()));
-- ※ crew_members 정책이 crew_members 를 직접 참조하면 무한재귀(42P17)가 난다.
--   security definer 함수로 멤버십을 확인해 재귀를 끊는다.
create or replace function is_crew_member(c uuid, u uuid) returns boolean
  language sql security definer stable set search_path = public as $$
    select exists(select 1 from crew_members m where m.crew_id = c and m.user_id = u);
  $$;
create policy cm_read      on crew_members for select using (is_crew_member(crew_id, auth.uid()));
create policy cm_self      on crew_members for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy goals_read   on crew_goals for select using (
  exists (select 1 from crew_members m where m.crew_id = crew_goals.crew_id and m.user_id = auth.uid()));

-- 테마 정의 / 칭호 정의 / 지역 = 공개 읽기 전용
-- ※ Supabase 는 SQL 로 만든 테이블에도 RLS 가 켜질 수 있다(정책 없으면 anon 은 0행).
--   읽기 정책만 주고 쓰기 정책은 두지 않아 "공개 읽기 + 쓰기 차단"을 명시적으로 보장한다.
alter table theme_collections enable row level security;
alter table theme_places      enable row level security;
alter table titles            enable row level security;
alter table regions           enable row level security;
create policy themecol_read   on theme_collections for select using (true);
create policy themeplace_read on theme_places      for select using (true);
create policy titles_read     on titles            for select using (true);
create policy regions_read    on regions           for select using (true);

-- 공개 피드 뷰 (인스타형) — 공개 런 + 작성자 프로필 + 경로(GeoJSON) + 지나는 동 수.
-- security_invoker=on: 호출자 RLS가 적용되어 runs_public_read 로 공개 런만 노출된다.
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
