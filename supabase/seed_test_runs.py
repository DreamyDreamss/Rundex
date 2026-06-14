"""테스트 시드 — 익명 유저 N명 생성 후 서울 곳곳 공개 런 업로드 (실제 API 경로)."""
import json, math, re, time, urllib.request

ROOT = "D:/Rundex/app/src/main/java/com/rundex/routepoc/ApiConfig.kt"
cfg = open(ROOT, encoding="utf-8").read()
BASE = re.search(r'const val BASE_URL = "([^"]+)"', cfg).group(1)
ANON = re.search(r'const val ANON_KEY = "([^"]+)"', cfg).group(1)

def post(path, body, token=None, base=None):
    url = (base or BASE) + path
    data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("apikey", ANON)
    req.add_header("Authorization", f"Bearer {token or ANON}")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req) as r:
        return r.status, r.read().decode()

def patch(path, body, token):
    url = BASE + path
    req = urllib.request.Request(url, data=json.dumps(body).encode(), method="PATCH")
    req.add_header("apikey", ANON)
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Content-Type", "application/json")
    req.add_header("Prefer", "return=representation")
    with urllib.request.urlopen(req) as r:
        return r.status, r.read().decode()

def haversine(a, b):
    R = 6371000.0
    p1, p2 = math.radians(a[1]), math.radians(b[1])
    dphi = math.radians(b[1] - a[1]); dl = math.radians(b[0] - a[0])
    x = math.sin(dphi/2)**2 + math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*R*math.asin(math.sqrt(x))

def route(lon0, lat0, steps, dlon, dlat, wig=0.0006):
    pts = []
    for i in range(steps):
        w = wig * math.sin(i * 0.9)
        pts.append([round(lon0 + dlon*i + w, 6), round(lat0 + dlat*i + w*0.7, 6)])
    return pts

# (이름, 시작 lon, lat, 스텝, dlon, dlat)
USERS = [
    ("김러너",     126.9895, 37.5512, 22,  0.0011,  0.0006),  # 남산 일대
    ("한강크루",   126.9412, 37.5285, 26,  0.0016, -0.0002),  # 여의도 한강
    ("새벽조깅러", 127.0276, 37.4979, 20,  0.0009,  0.0009),  # 강남
    ("도봉산호랑이",127.0450, 37.6688, 18, -0.0007,  0.0011),  # 도봉
    ("러닝머신",   126.9220, 37.5563, 24,  0.0013,  0.0005),  # 홍대~합정
    ("성수동질주", 127.0560, 37.5445, 21,  0.0010, -0.0008),  # 성수
]

now_ms = int(time.time() * 1000)
ok = 0
for idx, (name, lon, lat, steps, dlon, dlat) in enumerate(USERS):
    try:
        st, body = post("/auth/v1/signup", {})
        sess = json.loads(body)
        token = sess.get("access_token"); uid = (sess.get("user") or {}).get("id")
        if not token or not uid:
            print(f"[{name}] signup 실패: {body[:120]}"); continue
        patch(f"/rest/v1/profiles?id=eq.{uid}", {"display_name": name}, token)

        coords = route(lon, lat, steps, dlon, dlat)
        dist = sum(haversine(coords[i], coords[i+1]) for i in range(len(coords)-1))
        dur = int(dist / 2.7 * 1000)  # ~2.7 m/s
        started = now_ms - (idx+1)*3600_000 - int(dist/2.7*1000)
        payload = {
            "startedAt": started, "endedAt": started + dur,
            "distanceM": round(dist, 1), "durationMs": dur,
            "source": "free", "visibility": "public", "coordinates": coords,
        }
        st, body = post("/rest/v1/rpc/submit_run", {"payload": payload}, token)
        print(f"[{name}] run {st}  dist={dist:.0f}m pts={len(coords)}  {body[:90]}")
        ok += 1
    except Exception as e:
        print(f"[{name}] 오류: {e}")

print(f"\n완료: {ok}/{len(USERS)} 공개 런 생성")
