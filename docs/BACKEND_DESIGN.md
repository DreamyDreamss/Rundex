# 러닝 도감 — 백엔드 / DB 설계서

> 클라이언트(Android)는 이 문서의 API 계약(`ApiClient`)에 맞춰 이미 구현되어 있다.
> 서버를 이 명세대로 올리면 소셜·추천경로·희소성·크루까지 바로 붙는다.

- 스택 권장: **FastAPI + PostgreSQL 16 + PostGIS 3.4**, 인증 JWT, 파일/이미지 S3 호환 스토리지
- 좌표계: 저장은 `geography(…,4326)` 또는 `geometry(…,4326)` + GiST. 거리계산은 `geography`.
- 모든 timestamp는 UTC `timestamptz`.

---

## 1. 인증 / 세션

기기 최초 실행 시 익명 가입 → 이후 핸들 설정.

| Method | Path | 설명 |
|---|---|---|
| POST | `/v1/auth/device` | `{deviceId}` → `{userId, token}` (익명 발급/복구) |
| POST | `/v1/auth/refresh` | 토큰 갱신 |
| GET  | `/v1/me` | 내 프로필 |
| PATCH| `/v1/me` | `{displayName, bio, representativeTitleIds[]}` |

클라 헤더: `Authorization: Bearer <token>`, `X-Device-Id: <uuid>`.

---

## 2. 도메인별 REST API

### 2.1 러닝 기록 동기화
| Method | Path | Body / 응답 |
|---|---|---|
| POST | `/v1/runs` | 러닝 종료 시 업로드. `{startedAt, endedAt, distanceM, durationMs, encodedPolyline, source: "free"|"follow", visibility}` → `{runId, dexDelta, newTitles, themeDelta}` |
| GET  | `/v1/runs?cursor=` | 내 기록 페이지네이션 |
| GET  | `/v1/runs/{id}` | 단건 (GPX는 `?format=gpx`) |

> 서버가 업로드된 polyline을 **행정동 경계와 교차(`ST_Intersection`)** 시켜 동별 거리 분배 → 도감/등급/칭호/테마를 **서버 권위(authoritative)** 로 재계산해 응답. 클라는 낙관적 표시 후 응답으로 보정.

### 2.2 발견 도감 / 누적 등급
| Method | Path | 설명 |
|---|---|---|
| GET | `/v1/dex` | 내 발견 동 목록 + 완성률(`discovered/total`) + 등급 |
| GET | `/v1/dex/{regionCode}` | 동 상세(누적 m, 등급, 첫방문) |
| GET | `/v1/regions?bbox=` | 경계 타일(지도 오버레이용, 간략화 geom) |

### 2.3 테마 도감 (큐레이션)
| Method | Path | 설명 |
|---|---|---|
| GET | `/v1/themes` | 컬렉션 목록 `{slug,title,cover,placeCount,collectedCount}` |
| GET | `/v1/themes/{slug}` | 장소 목록 + 내 수집 여부 |
| (수집은 `/v1/runs` 업로드 시 서버가 반경 통과 판정으로 자동 처리) |

### 2.4 칭호 / 희소성
| Method | Path | 설명 |
|---|---|---|
| GET | `/v1/titles` | 전체 칭호 정의 + 내 보유 + **보유율(rarity%)** |
| GET | `/v1/titles/{code}` | 단건 + 보유자 수/비율 |
| PATCH | `/v1/me` | 대표 칭호 1~3 설정(§1) |

### 2.5 추천 경로 (Discover / UGC)
| Method | Path | 설명 |
|---|---|---|
| GET | `/v1/routes?sort=popular|rating|distance&near=lat,lng&difficulty=` | 추천 경로 목록 |
| POST | `/v1/routes` | 등록 `{name, encodedPolyline, distanceM, difficulty}` — **출발지 trim 필수** |
| GET | `/v1/routes/{id}` | 상세 + 평점 평균 |
| POST | `/v1/routes/{id}/ratings` | `{stars, review}` |
| POST | `/v1/routes/{id}/report` | 신고 |

### 2.6 소셜 (팔로우 / 피드 / 반응)
| Method | Path | 설명 |
|---|---|---|
| POST/DELETE | `/v1/users/{id}/follow` | 단방향 팔로우/해제 |
| GET | `/v1/users/{id}` | 공개 프로필(대표 칭호 포함) |
| GET | `/v1/feed?cursor=` | 팔로우 + 본인 활동 피드 |
| POST | `/v1/activities/{id}/reactions` | `{emoji}` |
| GET | `/v1/activities/{id}` | 활동 상세 |

활동(activity) 타입: `run_completed`, `region_discovered`, `grade_up`, `title_earned`, `theme_completed`, `route_published`.

### 2.7 크루 (후순위)
| Method | Path |
|---|---|
| POST `/v1/crews`, GET `/v1/crews/{id}`, POST `/v1/crews/{id}/members`, GET `/v1/crews/{id}/feed`, GET `/v1/crews/{id}/goals` |

---

## 3. DB 스키마 (PostGIS DDL)

```sql
CREATE EXTENSION IF NOT EXISTS postgis;

-- 사용자 ------------------------------------------------------------
CREATE TABLE users (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  handle          text UNIQUE,
  display_name    text NOT NULL DEFAULT '러너',
  bio             text,
  rep_title_ids   uuid[] NOT NULL DEFAULT '{}',   -- 대표 칭호 1~3
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE devices (
  device_id   text PRIMARY KEY,
  user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at  timestamptz NOT NULL DEFAULT now()
);

-- 행정동 경계(전국) -------------------------------------------------
CREATE TABLE regions (
  code      text PRIMARY KEY,             -- 행정동 코드
  name      text NOT NULL,
  sido      text, sigungu text,
  geom      geometry(MultiPolygon,4326) NOT NULL
);
CREATE INDEX regions_geom_gix ON regions USING gist (geom);

-- 러닝 기록 --------------------------------------------------------
CREATE TABLE runs (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  started_at    timestamptz NOT NULL,
  ended_at      timestamptz NOT NULL,
  distance_m    double precision NOT NULL,
  duration_ms   bigint NOT NULL,
  source        text NOT NULL DEFAULT 'free',   -- free|follow
  visibility    text NOT NULL DEFAULT 'private', -- private|public
  geom          geometry(LineString,4326) NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX runs_user_idx ON runs(user_id, started_at DESC);
CREATE INDEX runs_geom_gix ON runs USING gist (geom);

-- 동별 거리 원장(감사/재계산) --------------------------------------
CREATE TABLE run_region_ledger (
  run_id       uuid REFERENCES runs(id) ON DELETE CASCADE,
  region_code  text REFERENCES regions(code),
  meters       double precision NOT NULL,
  PRIMARY KEY (run_id, region_code)
);

-- 발견 도감(동별 누적) --------------------------------------------
CREATE TABLE dex_entries (
  user_id        uuid REFERENCES users(id) ON DELETE CASCADE,
  region_code    text REFERENCES regions(code),
  first_visit_at timestamptz NOT NULL,
  total_m        double precision NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, region_code)
);
-- 등급은 total_m로 파생(앱 Grades 규칙과 동일): 10/50/200km, 골드후 200km당 ★

-- 테마 도감 -------------------------------------------------------
CREATE TABLE theme_collections (
  id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  slug   text UNIQUE NOT NULL,
  title  text NOT NULL,
  cover_url text
);
CREATE TABLE theme_places (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  collection_id uuid REFERENCES theme_collections(id) ON DELETE CASCADE,
  name          text NOT NULL,
  geom          geometry(Point,4326) NOT NULL,
  radius_m      integer NOT NULL DEFAULT 120
);
CREATE INDEX theme_places_geom_gix ON theme_places USING gist (geom);
CREATE TABLE theme_progress (
  user_id      uuid REFERENCES users(id) ON DELETE CASCADE,
  place_id     uuid REFERENCES theme_places(id) ON DELETE CASCADE,
  collected_at timestamptz NOT NULL DEFAULT now(),
  run_id       uuid REFERENCES runs(id),
  PRIMARY KEY (user_id, place_id)
);

-- 칭호 -----------------------------------------------------------
CREATE TABLE titles (
  id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code      text UNIQUE NOT NULL,          -- explorer_10, theme:five-palaces ...
  name      text NOT NULL,
  type      text NOT NULL,                 -- complete|grade|milestone|limited
  criteria  jsonb NOT NULL
);
CREATE TABLE user_titles (
  user_id   uuid REFERENCES users(id) ON DELETE CASCADE,
  title_id  uuid REFERENCES titles(id),
  earned_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, title_id)
);
-- 보유율 캐시(주기 갱신 머티리얼라이즈드 뷰)
CREATE MATERIALIZED VIEW title_stats AS
  SELECT t.id title_id,
         count(ut.user_id) AS owners,
         count(ut.user_id)::float / NULLIF((SELECT count(*) FROM users),0) AS rarity
  FROM titles t LEFT JOIN user_titles ut ON ut.title_id = t.id
  GROUP BY t.id;

-- 추천 경로(UGC) -------------------------------------------------
CREATE TABLE routes (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id    uuid REFERENCES users(id) ON DELETE SET NULL,
  name        text NOT NULL,
  distance_m  double precision NOT NULL,
  difficulty  smallint NOT NULL DEFAULT 1,    -- 1~5
  geom        geometry(LineString,4326) NOT NULL,  -- 출발지 trim 적용 후
  is_public   boolean NOT NULL DEFAULT true,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX routes_geom_gix ON routes USING gist (geom);
CREATE TABLE route_ratings (
  route_id  uuid REFERENCES routes(id) ON DELETE CASCADE,
  user_id   uuid REFERENCES users(id) ON DELETE CASCADE,
  stars     smallint NOT NULL CHECK (stars BETWEEN 1 AND 5),
  review    text,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (route_id, user_id)
);
CREATE TABLE route_reports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id uuid REFERENCES routes(id) ON DELETE CASCADE,
  reporter_id uuid REFERENCES users(id),
  reason text, created_at timestamptz DEFAULT now()
);

-- 소셜 ----------------------------------------------------------
CREATE TABLE follows (
  follower_id uuid REFERENCES users(id) ON DELETE CASCADE,
  followee_id uuid REFERENCES users(id) ON DELETE CASCADE,
  created_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (follower_id, followee_id)
);
CREATE TABLE activities (
  id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id   uuid REFERENCES users(id) ON DELETE CASCADE,
  type      text NOT NULL,
  payload   jsonb NOT NULL,        -- run/region/title 요약 + 대표칭호 스냅샷
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX activities_user_idx ON activities(user_id, created_at DESC);
CREATE TABLE reactions (
  activity_id uuid REFERENCES activities(id) ON DELETE CASCADE,
  user_id     uuid REFERENCES users(id) ON DELETE CASCADE,
  emoji       text NOT NULL,
  PRIMARY KEY (activity_id, user_id, emoji)
);

-- 크루(후순위) ---------------------------------------------------
CREATE TABLE crews (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL, owner_id uuid REFERENCES users(id),
  created_at timestamptz DEFAULT now()
);
CREATE TABLE crew_members (
  crew_id uuid REFERENCES crews(id) ON DELETE CASCADE,
  user_id uuid REFERENCES users(id) ON DELETE CASCADE,
  role text NOT NULL DEFAULT 'member',
  PRIMARY KEY (crew_id, user_id)
);
CREATE TABLE crew_goals (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  crew_id uuid REFERENCES crews(id) ON DELETE CASCADE,
  kind text, target double precision, progress double precision DEFAULT 0,
  period_start date, period_end date
);
```

---

## 4. 서버 핵심 로직 (구현 시 주의)

1. **동별 거리 분배** — 업로드된 `runs.geom`을 `regions`와 `ST_Intersection`해 동별 길이를 `run_region_ledger`에 적재 → `dex_entries.total_m` 가산(UPSERT). 등급 변화 감지 시 `activities`(`grade_up`) 생성.
2. **테마 수집** — `ST_DWithin(geography(run.geom), geography(place.geom), place.radius_m)`로 통과 판정 → `theme_progress` UPSERT. 컬렉션 완성 시 `theme:<slug>` 칭호 부여.
3. **칭호 평가** — 마일스톤/등급/완성형 조건을 서버에서 재평가(클라와 동일 규칙, 서버가 권위). 신규 시 `user_titles` + `activities`(`title_earned`).
4. **보유율** — `title_stats` 머티뷰를 5~15분 주기 `REFRESH`.
5. **출발지 프라이버시** — 경로 공개/등록 시 시작·끝 일정 반경(기본 150m) 포인트를 **서버에서도 한번 더 trim**(클라가 보내도 신뢰하지 않음).
6. **치팅** — 업로드 시 구간 속도 상한·순간이동·평균속도 검사, 의심 run은 `flagged`로 도감 반영 보류.
7. **피드 팬아웃** — 활동 생성 시 fan-out-on-write(소규모) 또는 read 시 팔로위 조인(초기엔 read 조인이 단순).

---

## 5. 클라이언트 연동 지점 (이미 구현됨)

| 기능 | 클라 파일 | 서버 의존 |
|---|---|---|
| 런 업로드/동기화 | `ApiClient.submitRun` (TrackActivity 종료 시 best-effort) | `/v1/runs` |
| 테마 도감 판정 | `ThemeDex`/`ThemeMatcher` (로컬 seed 선반영, 서버가 권위) | `/v1/themes*` |
| 출발지 trim | `RoutePrivacy.trimEnds` (등록/공유 전 적용) | 서버 재검증 |
| 대표 칭호 | `ProfileActivity`(로컬 저장) → `PATCH /v1/me` | `/v1/me` |
| 추천경로/피드/크루 | `ApiClient`에 메서드 스텁 → 화면은 서버 가동 후 연결 | 해당 도메인 API |

> 클라는 **낙관적 로컬 반영 + 서버 응답 보정** 모델. 서버 없을 때도 로컬 기능(도감·테마·기록)은 동작하고, 서버가 뜨면 `ApiConfig.BASE_URL`만 채우면 동기화가 붙는다.
