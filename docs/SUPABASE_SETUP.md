# Supabase 백엔드 셋업 가이드 (처음부터)

> 이 문서 하나만 따라 하면 **빈 Supabase 계정 → 풀 기능 연결**까지 끝납니다.
> 클라이언트는 이미 다 구현돼 있어, 사실상 **(1) Supabase 프로젝트 띄우고 (2) SQL 3개 실행하고 (3) `ApiConfig.kt`에 URL/키 2줄 채우는** 작업입니다.
>
> 관련 파일
> - SQL: `supabase/schema.sql` → `supabase/functions.sql` → `supabase/seed.sql`
> - 연결 설정: `app/src/main/java/com/rundex/routepoc/ApiConfig.kt`
> - 인증/통신(이미 구현됨): `AuthClient.kt`, `ApiClient.kt`, `Session.kt`
> - 설계 배경: `docs/BACKEND_DESIGN.md`, `supabase/README.md`

---

## 0. 전체 그림 (5분 요약)

```
┌──────────── 앱(Android) ────────────┐        ┌──────── Supabase ────────┐
│ RundexApp 시작                       │        │                          │
│   └ AuthClient.ensureSession()       │ ─────▶ │ Auth: 익명 signup        │ ← Anonymous 켜야 함
│        (가입 폼 없이 익명 계정 발급) │        │   └ 트리거로 profiles 생성│
│ 러닝 종료                            │        │                          │
│   └ ApiClient.submitRun(payload)     │ ─────▶ │ RPC submit_run()         │ ← functions.sql
│        (낙관적 로컬 반영 후 서버 보정)│       │   └ 경로 ∩ regions       │ ← schema.sql + 경계 적재
│ 도감/테마/칭호/추천경로/피드         │ ─────▶ │ PostgREST 자동 API       │ ← schema.sql + seed.sql
└──────────────────────────────────────┘        └──────────────────────────┘
```

연결의 핵심 스위치는 단 하나 — **`ApiConfig.BASE_URL`이 비어 있으면 모든 서버 호출을 조용히 스킵**(로컬 전용 모드), 채워지면 동기화가 켜집니다. (`ApiConfig.enabled = BASE_URL.isNotBlank()`)

---

## 1. Supabase 프로젝트 생성

1. https://supabase.com 접속 → **Sign in**(GitHub 계정 추천) → **New project**
2. 입력
   - **Name**: `rundex` (자유)
   - **Database Password**: 강한 비밀번호 생성 → **반드시 따로 저장**(나중에 행정동 경계 적재 시 DB 접속에 필요)
   - **Region**: `Northeast Asia (Seoul) — ap-northeast-2` (한국 사용자면 지연 최소)
   - **Plan**: Free로 시작
3. **Create new project** → 프로비저닝 1~2분 대기 (DB가 깨어날 때까지)

---

## 2. 익명 로그인 켜기 (가입 폼 없는 자동 계정)

앱은 첫 실행에 `POST /auth/v1/signup`(빈 바디)으로 익명 계정을 만듭니다. 이게 꺼져 있으면 **422 / "Anonymous sign-ins are disabled"** 가 떨어지고 소셜·동기화가 전부 비활성됩니다.

1. 좌측 메뉴 **Authentication → Sign In / Providers** (구버전은 *Providers*)
2. **Anonymous sign-ins** 토글 **ON** → Save
3. (선택) **Authentication → Rate Limits** 에서 익명 가입 한도가 너무 낮지 않은지 확인. 기본값으로 개발은 충분합니다.

> 참고: 코드상 익명 로그인은 `AuthClient.ensureSession()`이 앱 시작 시 자동 호출 — 첫 실행엔 signup, 이후엔 `refresh_token`으로 같은 계정 유지. 가입 화면을 따로 만들 필요가 없습니다.

---

## 3. SQL 실행 (테이블 → 함수 → 시드)

좌측 메뉴 **SQL Editor → New query**. 아래 **순서 그대로** 3번 실행합니다. (각 파일 내용을 통째로 붙여넣고 **Run**)

| 순서  | 파일                       | 만드는 것                                                            |
| --- | ------------------------ | ---------------------------------------------------------------- |
| 1   | `supabase/schema.sql`    | PostGIS 확장, 모든 테이블, RLS 정책, **신규 가입 시 profiles 자동 생성 트리거**       |
| 2   | `supabase/functions.sql` | `submit_run`(동거리 분배·도감·등급·테마·칭호), `create_crew`, `join_crew` RPC |
| 3   | `supabase/seed.sql`      | 테마 컬렉션/장소(서울 37곳)·칭호 정의 21종                                      |

실행 팁
- 파일을 로컬에서 열어 전체 복사 → 에디터에 붙여넣기 → **Run** (Ctrl+Enter).
- `schema.sql` 첫 줄 `create extension if not exists postgis;` 가 PostGIS를 켭니다. 별도 설정 불필요.
- 모두 `if not exists` / `on conflict do nothing` 라서 **여러 번 실행해도 안전**(멱등).
- 1번에서 `permission denied to create extension "postgis"` 가 나오면, Supabase는 보통 `postgis`를 허용합니다. 그래도 막히면 **Database → Extensions** 에서 `postgis`를 검색해 Enable 후 첫 줄을 빼고 다시 실행하세요.

### 실행 후 즉시 확인
- **Table Editor** 에 `profiles, regions, runs, dex_entries, theme_collections, theme_places, titles, routes, activities, crews...` 가 보이면 1번 성공.
- **Database → Functions** 에 `submit_run / create_crew / join_crew / grade_of / handle_new_user` 가 있으면 2번 성공.
- `theme_collections` 5행, `titles` 21행이 차 있으면 3번 성공.

> 권한 메모: Supabase는 `public` 스키마 신규 테이블에 `anon`/`authenticated` 역할 접근을 기본 부여하고, RPC `execute`도 기본 공개입니다. 따로 GRANT를 줄 필요는 없습니다. 읽기 공개 테이블(`regions`, `theme_*`, `titles`, `title_rarity`)은 의도적으로 RLS를 켜지 않아 anon 키로 읽힙니다. 개인 데이터(`runs`, `dex_entries`...)는 RLS로 **본인 행만** 접근됩니다.

---

## 4. 행정동 경계 적재 (`regions`) — 도감의 핵심

`submit_run`은 업로드된 경로(LineString)를 `regions.geom`과 교차(`ST_Intersection`)시켜 **동별 거리**를 계산하고 도감/등급을 채웁니다. 즉 **`regions`가 비어 있으면 서버 도감이 채워지지 않습니다.** (앱은 여전히 돌아가지만 서버 권위 도감/등급 갱신이 빔)

> **MVP 팁**: 일단 4번을 건너뛰고 5번으로 가서 연결·익명로그인·테마/칭호만 먼저 검증해도 됩니다. 도감 서버 동기화는 경계 적재 후 켜집니다. 또는 **서울만 먼저** 적재해도 충분합니다.

### 4-A. 경계 데이터 받기
- 출처: **통계청 SGIS** 또는 **행정안전부 행정동 경계** (행정동 단위, SHP 또는 GeoJSON)
- 좌표계가 보통 **EPSG:5179(UTM-K)** 이므로 **EPSG:4326(WGS84)** 으로 변환 필요.

### 4-B. ogr2ogr로 바로 적재 (권장)
GDAL의 `ogr2ogr`가 변환+적재를 한 번에 합니다. (Windows: [OSGeo4W](https://trac.osgeo.org/osgeo4w/) 또는 `conda install -c conda-forge gdal`)

DB 접속 문자열은 **Project Settings → Database → Connection string → URI** 에서 복사 (1번에서 저장한 비밀번호 사용). 직접 연결(5432) 또는 세션 풀러를 쓰세요.

```bash
ogr2ogr -f PostgreSQL \
  PG:"host=db.<project-ref>.supabase.co port=5432 dbname=postgres user=postgres password=<DB비밀번호> sslmode=require" \
  dong.geojson \
  -nln regions_import \
  -t_srs EPSG:4326 \
  -lco GEOMETRY_NAME=geom \
  -nlt MULTIPOLYGON
```

위는 임시 테이블 `regions_import`로 넣은 뒤, **컬럼명을 `code/name/geom`에 맞춰** 본 테이블로 옮기는 방식이 안전합니다. SQL Editor에서:

```sql
-- 원본 컬럼명은 데이터마다 다름(예: adm_cd, adm_nm). 실제 컬럼명에 맞게 수정하세요.
insert into regions(code, name, sido, sigungu, geom)
select adm_cd::text,
       adm_nm::text,
       split_part(adm_nm, ' ', 1),   -- 시도 (대략)
       split_part(adm_nm, ' ', 2),   -- 시군구 (대략)
       st_multi(geom)::geometry(MultiPolygon,4326)
from regions_import
on conflict (code) do nothing;

-- 적재 검증
select count(*) from regions;               -- 전국 약 3,482개 (앱 데이터 기준)
select code, name from regions limit 5;
```

> `dong.geojson`이 EPSG:5179면 위 `-t_srs EPSG:4326`가 변환을 처리합니다. 입력이 SHP면 GeoJSON 대신 `.shp` 경로를 그대로 넣어도 됩니다(`-s_srs EPSG:5179`를 추가해 원본 좌표계를 명시하면 더 확실).

### 4-C. ogr2ogr가 어려우면 (대안)
- **SQL INSERT 생성**: GeoJSON을 스크립트로 돌려 `insert into regions(code,name,geom) values (..., st_geomfromgeojson('...'))` 형태로 만들어 SQL Editor에 붙여넣기. 데이터가 크면 시군구 단위로 나눠 실행.
- **서울만 우선**: 변환 단계에서 서울(`adm_cd` 11로 시작) 426개 동만 필터링해 적재 → 도감 데모는 충분.

적재가 끝나면 GiST 인덱스(`regions_geom_gix`)는 `schema.sql`에서 이미 만들어졌으니 교차 쿼리가 빠릅니다.

---

## 5. 앱에 URL/키 연결 (가장 중요한 2줄)

1. **Project Settings → API** 로 이동, 다음 둘을 복사:
   - **Project URL** → `https://<project-ref>.supabase.co`
   - **Project API keys → `anon` `public`** (절대 `service_role` 아님!)
2. `app/src/main/java/com/rundex/routepoc/ApiConfig.kt` 를 채웁니다:

```kotlin
object ApiConfig {
    const val BASE_URL = "https://<project-ref>.supabase.co"   // ← Project URL
    const val ANON_KEY = "eyJhbGciOi...<anon public key>..."    // ← anon public key

    val enabled: Boolean get() = BASE_URL.isNotBlank()
}
```

> ⚠️ 보안: `anon` 키는 클라이언트 공개용이 맞습니다(데이터는 RLS로 보호). **`service_role` 키는 절대 앱에 넣지 마세요** — RLS를 우회합니다. `ApiConfig.kt`를 리포지토리에 커밋해도 anon 키라면 일반적으로 허용되지만, 신경 쓰이면 `local.properties`/`BuildConfig`로 빼는 방식으로 바꿔도 됩니다(현재 코드는 상수 직접 사용).

3. 빌드 & 설치:
```bash
./gradlew assembleDebug        # 또는 Android Studio에서 Run
./gradlew installDebug
```

---

## 6. 연결 검증 (체크리스트)

앱을 깔고 한 번 실행한 뒤, Supabase 대시보드에서:

1. **익명 로그인** — **Authentication → Users** 에 `is_anonymous = true` 사용자 1명이 생겼나? → `AuthClient` 작동 OK.
2. **프로필 자동 생성** — **Table Editor → profiles** 에 그 유저 id로 1행이 생겼나? → `handle_new_user` 트리거 OK.
3. **러닝 업로드** — 앱에서 러닝을 짧게 기록·종료 → **Table Editor → runs** 에 행이 쌓이고, (regions 적재했다면) **dex_entries** 에 동별 누적이 잡히나? → `submit_run` RPC OK.
4. **읽기 API** — 브라우저나 curl로 공개 읽기 확인:
```bash
curl "https://<project-ref>.supabase.co/rest/v1/title_rarity?select=*" \
  -H "apikey: <anon key>"
# titles 21종이 rarity와 함께 JSON 배열로 떨어지면 PostgREST 연결 OK
```
5. **RPC 직접 호출**(선택) — SQL Editor가 아닌 실제 경로 점검:
```bash
curl -X POST "https://<project-ref>.supabase.co/rest/v1/rpc/submit_run" \
  -H "apikey: <anon key>" -H "Authorization: Bearer <anon key>" \
  -H "Content-Type: application/json" \
  -d '{"payload":{"startedAt":1718200000000,"endedAt":1718201800000,"distanceM":3000,"durationMs":1800000,"source":"free","coordinates":[[126.97,37.57],[126.98,37.58]]}}'
```
(anon 키만으로는 `auth.uid()`가 null → `unauthenticated` 예외가 정상. 실제 토큰이 있어야 끝까지 통과합니다. 즉 이 호출이 `unauthenticated`로 막히면 경로/함수는 살아있다는 뜻.)

---

## 7. 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| 앱이 계속 로컬 모드 (동기화 안 됨) | `ApiConfig.BASE_URL`이 비었거나 오타. `enabled`가 false면 `ApiClient`/`AuthClient`가 **조용히** 스킵 → 에러도 안 뜸. URL부터 확인. |
| `Anonymous sign-ins are disabled` (422) | 2번 단계 토글 OFF. Authentication에서 Anonymous 켜기. |
| 익명 유저는 생기는데 profiles가 빔 | `handle_new_user` 트리거 미생성 → `schema.sql` 재실행. |
| `runs`엔 쌓이는데 `dex_entries`가 안 참 | `regions`가 비었거나 경로가 어떤 동과도 교차 안 함(좌표계/좌표순서 확인). 좌표는 **[경도(lon), 위도(lat)]** 순서여야 함(`submit_run`이 `st_makepoint(lon,lat)` 사용). |
| 401/403 on `/rest/v1/...` | anon 키 누락/오타, 또는 개인 테이블을 토큰 없이 접근. 읽기는 공개 테이블만. 개인 데이터는 로그인 토큰 필요(앱이 자동 첨부). |
| `permission denied to create extension postgis` | Database → Extensions 에서 `postgis` 수동 Enable 후 해당 줄 빼고 재실행. |
| `regions` 적재 후에도 교차 0 | 좌표계가 4326이 아님(`ST_SRID(geom)` 확인) 또는 geom이 비유효(`ST_IsValid`). 필요시 `update regions set geom = st_makevalid(geom);` |
| 토큰 만료로 소셜 일시 중단 | `AuthClient.refresh()`가 `refresh_token`으로 자동 갱신. 갱신 실패해도 기존 토큰 유지하며 best-effort. 보통 재실행으로 회복. |

---

## 부록: 한눈에 보는 순서

```
1. supabase.com → New project (Region: Seoul, DB 비번 저장)
2. Authentication → Anonymous sign-ins ON
3. SQL Editor에서 순서대로 Run:
     schema.sql → functions.sql → seed.sql
4. regions 적재 (ogr2ogr, EPSG:5179→4326)   ← MVP면 나중/서울만 가능
5. Settings → API → Project URL + anon key 복사
     → ApiConfig.kt 의 BASE_URL / ANON_KEY 채우기
6. ./gradlew installDebug → 앱 1회 실행
7. 대시보드에서 익명유저·profiles·runs·dex_entries 확인
```

이걸로 도감·테마·칭호·추천경로·소셜·크루까지 풀 기능이 켜집니다.
