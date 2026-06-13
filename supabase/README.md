# Supabase 백엔드 (갈래 A — Supabase 단독)

별도 서버 없이 **Supabase = DB + Auth + Storage + API** 로 MVP를 굴린다.
복잡한 지오 로직은 Postgres 함수(RPC, `submit_run`)로 처리한다.

## 셋업

1. supabase.com에서 프로젝트 생성
2. **Authentication → Providers → Anonymous sign-ins** 켜기 (익명 로그인)
3. **SQL Editor**에서 순서대로 실행:
   1. `schema.sql` — 테이블 + RLS + 트리거
   2. `functions.sql` — `submit_run` 등 RPC
   3. (선택) `seed.sql` — 테마 컬렉션/칭호 시드, 행정동 경계 적재
4. **Project Settings → API** 에서 `Project URL`, `anon key` 복사

## 클라이언트 연결

`ApiConfig.kt` / `ApiClient` 를 Supabase에 맞게 채운다:

```
// ApiConfig.kt
BASE_URL = "https://<project-ref>.supabase.co"
ANON_KEY = "<anon key>"
```

- 익명 로그인: **앱이 자동 처리**(`RundexApp` 시작 시 `AuthClient.ensureSession()`).
  첫 실행에 `POST /auth/v1/signup`으로 익명 계정 발급, 이후 `refresh_token`으로 같은 계정 유지.
  → 대시보드에서 **Anonymous sign-ins 켜기 + ApiConfig 채우기**만 하면 됨(가입 화면 불필요)
- 런 업로드: `POST {BASE_URL}/rest/v1/rpc/submit_run`  body `{ "payload": { ... } }`
  헤더 `apikey: <anon>`, `Authorization: Bearer <access_token>`
- 도감/테마/칭호/추천경로/피드: PostgREST 자동 API
  예) `GET {BASE_URL}/rest/v1/routes?is_public=eq.true&order=created_at.desc`
  예) `GET {BASE_URL}/rest/v1/title_rarity?code=eq.explorer_10`

> 안드로이드 클라는 이미 `ApiClient`에 호출 지점이 깔려 있다. Supabase 경로(`/rest/v1/...`, `/rpc/...`)에 맞게 path만 매핑하면 된다.

## 행정동 경계 적재 (`regions`)

통계청 SGIS 또는 행안부 행정동 경계 SHP → GeoJSON 변환 후:
```bash
# EPSG:5179 → 4326 변환 + 적재 (ogr2ogr 예시)
ogr2ogr -f PostgreSQL PG:"<conn>" dong.geojson \
  -nln regions -t_srs EPSG:4326 -lco GEOMETRY_NAME=geom
```
컬럼명을 `code,name,geom`에 맞춘다. MVP는 서울만 먼저 적재해도 된다.

## 다음
- 추천경로 등록 시 출발지 trim은 클라(`RoutePrivacy`) + 서버 둘 다 적용 권장
- `title_rarity`는 뷰 → 부하 커지면 머티리얼라이즈드 뷰 + 주기 refresh
- 피드는 초기엔 `activities` + `follows` 조인(read), 규모 커지면 fan-out
