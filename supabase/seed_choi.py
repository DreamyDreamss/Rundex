# -*- coding: utf-8 -*-
"""최상훈(marucarry) 계정에 서비스 전 기능 테스트 데이터 일괄 시드.
도감 / 러닝기록(공개·비공개·캡션·태그·경로) / 칭호 / 팔로우(양방향) /
좋아요(=알림) / 추천경로·북마크·평점 / 크루(소유·가입·채팅·챌린지)."""
import json, urllib.request, os, math

PAT = os.environ["SUPABASE_PAT"]   # export SUPABASE_PAT=sbp_... 로 주입
REF = "jtbtqmcwjtuzsfpqzunf"
UID = "6901f996-a735-4c04-bc14-5d037fe1137f"   # 최상훈 / marucarry

# 다른 테스트 유저들 (팔로우/좋아요/크루 상대)
OTHERS = {
    "seongsu":  "00e88086-9bb9-4251-a5f0-bdcff00e589a",  # 성수동질주
    "machine":  "9a4203be-2a9b-4c79-b200-9b1aa0a48bcd",  # 러닝머신
    "dobong":   "b3ea342c-a94b-4bf8-bd14-0d1c315c9dbf",  # 도봉산호랑이
    "dawn":     "8cafa735-4301-4f9d-93df-9ee96cf04c6c",  # 새벽조깅러
    "hangang":  "ed528b8c-8a94-4480-b195-3a6eb2646101",  # 한강크루
    "kim":      "7e83f01b-fb81-47ca-971d-3c4de83c27af",  # 김러너
    "runner1":  "e0ea4351-c3d5-48f9-9eb9-4b3ccd2d9eb8",
    "dog":      "37baec39-079b-46eb-bdff-95f51e729879",  # 전설의개
}
CREW_HANGANG = "ef242798-6c0e-4b45-8b93-fb35a6a2f807"  # 한강 러닝 크루
CREW_DAWN    = "f5045329-e144-4513-b858-830538466ce0"  # 새벽 5K 클럽

# 서울 중심부 동(코드, 이름, lon, lat) — 도감/경로 베이스
DONGS = [
    ("1101053","사직동",126.9725,37.5709),("1101061","종로1234가동",126.9918,37.5722),
    ("1101064","이화동",127.0055,37.5766),("1102055","명동",126.9866,37.5610),
    ("1102052","소공동",126.9756,37.5617),("1102058","장충동",127.0033,37.5526),
    ("1102069","신당동",127.0154,37.5633),("1102071","약수동",127.0125,37.5474),
    ("1103051","후암동",126.9827,37.5477),("1103063","이촌1동",126.9718,37.5143),
    ("1103065","이태원1동",126.9954,37.5307),("1103069","서빙고동",126.9895,37.5194),
    ("1103070","보광동",127.0030,37.5224),("1103073","한강로동",126.9709,37.5270),
    ("1103074","한남동",127.0080,37.5346),("1102060","을지로동",126.9984,37.5640),
    ("1102054","회현동",126.9791,37.5539),("1102057","필동",126.9946,37.5546),
    ("1102070","다산동",127.0108,37.5529),("1102072","청구동",127.0173,37.5536),
    ("1101058","교남동",126.9664,37.5684),("1101067","창신1동",127.0160,37.5696),
    ("1102068","중림동",126.9680,37.5545),("1103053","남영동",126.9755,37.5427),
    ("1103058","효창동",126.9637,37.5399),("1103066","이태원2동",126.9947,37.5387),
    ("1103071","청파동",126.9689,37.5450),("1103072","원효로1동",126.9677,37.5354),
    ("1101057","무악동",126.9612,37.5747),("1101063","종로56가동",127.0073,37.5702),
    ("1102059","광희동",127.0061,37.5627),("1102073","동화동",127.0203,37.5576),
    ("1103052","용산2가동",126.9865,37.5386),("1103059","용문동",126.9606,37.5354),
    ("1102065","신당5동",127.0237,37.5609),
]


def linestring(lon, lat, dist_m, n=22, seed=0):
    """베이스 좌표 주변으로 살짝 구불구불한 루프형 LINESTRING WKT 생성."""
    # 대략 dist_m 에 맞춰 반경 결정 (위경도 1도 ≈ 111km)
    radius = (dist_m / (2 * math.pi)) / 111000.0
    pts = []
    for i in range(n):
        t = (i / (n - 1)) * 2 * math.pi
        wob = 1 + 0.18 * math.sin(t * 3 + seed)   # 길처럼 흔들림
        x = lon + radius * wob * math.cos(t) * 1.25
        y = lat + radius * wob * math.sin(t)
        pts.append(f"{x:.6f} {y:.6f}")
    return "LINESTRING(" + ",".join(pts) + ")"


# 러닝 기록: (일전, 시각h, 거리m, 시간ms, 공개여부, 캡션, 태그[], 베이스동index, [지나는 동 index])
RUNS = [
    (1,  7, 10240, 3180000, "public",  "아침 한강 10K, 오늘 페이스 좋았다 🔥", ["한강","10K","아침런","페이스업"], 13, [13,9,11,3]),
    (2, 21, 5320, 1920000, "public",  "퇴근 후 야간런 🌙 도심 야경 최고", ["야간런","5K","도심","루틴"], 3, [3,15,16]),
    (3,  6, 21300, 7020000, "public",  "하프 도전 성공! 다리가 후들 💪", ["하프","21K","챌린지","자기기록"], 8, [8,7,18,19]),
    (4,  9, 6800, 2460000, "private", "비 와서 짧게만", ["빗속런","조깅"], 22, [22,20,0]),
    (5, 13, 8450, 2880000, "public",  "주말 남산 한 바퀴, 오르막 빡세다 ⛰️", ["남산","언덕","주말런"], 16, [16,4,5,2]),
    (6, 16,  4120, 1500000, "public",  "가볍게 동네 한 바퀴", ["동네런","회복런","5K"], 9, [9,12,10]),
    (7, 20, 12600, 4020000, "public",  "장거리 빌드업 LSD, 천천히 오래 🐢", ["LSD","장거리","빌드업"], 13, [13,12,11,14,9]),
    (8, 25, 5050, 1860000, "private", "컨디션 별로라 끊어감", ["조깅","회복런"], 7, [7,6,19]),
    (9, 28, 42230, 14400000, "public", "풀코스 완주!!! 인생 첫 마라톤 🏅🎉", ["풀코스","마라톤","42K","완주","인생런"], 13, [13,9,11,14,3,4]),
    (10,32,  7300, 2520000, "public",  "오랜만에 이태원 언덕런", ["이태원","언덕","주말런"], 10, [10,14,12]),
    (11,35,  3200, 1140000, "private", "테스트 겸 짧게", ["조깅"], 0, [0,20]),
    (12,40,  9100, 3060000, "public",  "한강 다리 5개 찍기 미션 클리어 🌉", ["한강","다리","미션","9K"], 13, [13,9,3,11]),
]

# 획득 칭호 코드
TITLE_CODES = ["first_card","dist_10","dist_21","dist_42","dist_100","dist_200",
               "explorer_10","explorer_25","first_bronze","first_silver","first_gold",
               "night_owl","early_bird","theme_hangang"]

# 대표 칭호 (프로필 상단)
REP_CODES = ["dist_42","explorer_25","theme_hangang"]


def build_sql():
    s = []
    s.append("do $$")
    s.append("declare uid uuid := '%s'; rid uuid; tid uuid;" % UID)
    s.append("begin")
    # ---- 0) 청소 (재실행 대비) ----
    s.append("delete from run_region_ledger where run_id in (select id from runs where user_id=uid);")
    s.append("delete from run_reactions where run_id in (select id from runs where user_id=uid);")
    s.append("delete from runs where user_id=uid;")
    s.append("delete from dex_entries where user_id=uid;")
    s.append("delete from user_titles where user_id=uid;")
    s.append("delete from follows where follower_id=uid or followee_id=uid;")
    s.append("delete from route_bookmarks where user_id=uid;")
    s.append("delete from routes where owner_id=uid;")
    s.append("delete from route_ratings where user_id=uid;")

    # ---- 1) 프로필 ----
    s.append("update profiles set display_name='최상훈', bio='평일 야간런 · 주말 한강 LSD ☕️ 풀코스 1회 완주 / 함께 뛰어요!' where id=uid;")

    # ---- 2) 도감: 동별 등급 분포 ----
    grades = [5000, 9000, 22000, 45000, 80000, 150000, 320000]
    for i, (code, name, lon, lat) in enumerate(DONGS):
        tm = grades[i % len(grades)] + (i * 137 % 4000)
        days = 3 + (i % 30)
        s.append("insert into dex_entries(user_id,region_code,first_visit_at,total_m) "
                 "values (uid,'%s',now()-interval '%d days',%d) "
                 "on conflict (user_id,region_code) do update set total_m=excluded.total_m;"
                 % (code, days, tm))

    # ---- 3) 러닝 기록 + 지나는 동 ----
    for idx, (days, hr, dist, dur, vis, cap, tags, bi, regs) in enumerate(RUNS):
        wkt = linestring(DONGS[bi][2], DONGS[bi][3], dist, seed=idx)
        tag_arr = "array[" + ",".join("'%s'" % t for t in tags) + "]"
        cap_sql = "'%s'" % cap.replace("'", "''")
        s.append(
            "insert into runs(user_id,started_at,ended_at,distance_m,duration_ms,source,visibility,caption,tags,geom) "
            "values (uid, now()-interval '%d days'-interval '%d hours', "
            "now()-interval '%d days'-interval '%d hours'+(%d||' milliseconds')::interval, "
            "%d,%d,'free','%s',%s,%s, ST_GeomFromText('%s',4326)) returning id into rid;"
            % (days, 24 - hr, days, 24 - hr, dur, dist, dur, vis, cap_sql, tag_arr, wkt))
        per = max(1, dist // len(regs))
        for r in regs:
            s.append("insert into run_region_ledger(run_id,region_code,meters) "
                     "values (rid,'%s',%d) on conflict do nothing;" % (DONGS[r][0], per))
        # 공개런이면 다른 유저들이 좋아요(알림 소스)
        if vis == "public":
            likers = list(OTHERS.values())[: 2 + (idx % 5)]
            for lu in likers:
                s.append("insert into run_reactions(run_id,user_id,emoji,created_at) "
                         "values (rid,'%s','%s',now()-interval '%d days') on conflict do nothing;"
                         % (lu, "❤️", days))

    # ---- 4) 칭호 ----
    for code in TITLE_CODES:
        s.append("select id into tid from titles where code='%s';" % code)
        s.append("if tid is not null then insert into user_titles(user_id,title_id,earned_at) "
                 "values (uid,tid,now()-interval '%d days') on conflict do nothing; end if;"
                 % (5 + (hash(code) % 30)))
    # 대표 칭호
    rep_ids = "array(select id from titles where code in (%s))" % ",".join("'%s'" % c for c in REP_CODES)
    s.append("update profiles set rep_title_ids=%s where id=uid;" % rep_ids)

    # ---- 5) 팔로우 (양방향) ----
    following = ["seongsu", "machine", "hangang", "kim", "dawn", "dobong"]
    followers = ["seongsu", "machine", "dawn", "kim", "runner1", "dog", "hangang"]
    for k in following:
        s.append("insert into follows(follower_id,followee_id) values (uid,'%s') on conflict do nothing;" % OTHERS[k])
    for k in followers:
        s.append("insert into follows(follower_id,followee_id) values ('%s',uid) on conflict do nothing;" % OTHERS[k])

    # ---- 6) 추천 경로 + 북마크 + 평점 ----
    routes = [
        ("남산 순환 코스", 6200, 3, True, 4),
        ("한강 반포 야경런", 8500, 2, True, 13),
        ("이태원 언덕 인터벌", 4800, 4, True, 10),
        ("도심 한 바퀴 (광화문~시청)", 5300, 2, True, 0),
        ("용산 가족공원 조깅", 3600, 1, True, 32),
    ]
    for i, (rn, rd, diff, pub, bi) in enumerate(routes):
        wkt = linestring(DONGS[bi][2], DONGS[bi][3], rd, n=18, seed=100 + i)
        s.append("insert into routes(owner_id,name,distance_m,difficulty,geom,is_public,created_at) "
                 "values (uid,'%s',%d,%d,ST_GeomFromText('%s',4326),%s,now()-interval '%d days') returning id into rid;"
                 % (rn.replace("'", "''"), rd, diff, wkt, "true" if pub else "false", 10 + i))
        s.append("insert into route_bookmarks(user_id,route_id) values (uid,rid) on conflict do nothing;")
        # 다른 유저 평점
        for j, lu in enumerate(list(OTHERS.values())[:3]):
            s.append("insert into route_ratings(route_id,user_id,stars,review) "
                     "values (rid,'%s',%d,%s) on conflict do nothing;"
                     % (lu, 4 + (j % 2), "'좋은 코스에요'" if j == 0 else "null"))

    # ---- 7) 크루: 한강크루 가입 + 채팅 + 챌린지 ----
    for crew in (CREW_HANGANG, CREW_DAWN):
        s.append("insert into crew_members(crew_id,user_id,role,joined_at) "
                 "values ('%s',uid,'member',now()-interval '12 days') on conflict do nothing;" % crew)
    msgs = [
        (CREW_HANGANG, "kim", "오늘 저녁 반포 번개런 가실분?", 2, 20),
        (CREW_HANGANG, UID, "저요! 7시 잠수교 앞에서 봬요 🙌", 2, 19),
        (CREW_HANGANG, "hangang", "좋아요 페이스는 6분대로 편하게 가요", 2, 18),
        (CREW_HANGANG, UID, "오늘 10K 찍고 왔습니다 다들 수고요 🔥", 1, 7),
        (CREW_DAWN, "dawn", "내일 새벽 5시 한강공원 모입니다", 3, 5),
        (CREW_DAWN, UID, "참석이요 ☕️", 3, 4),
    ]
    for crew, who, txt, d, h in msgs:
        u = UID if who == UID else OTHERS[who]
        s.append("insert into crew_messages(crew_id,user_id,text,created_at) "
                 "values ('%s','%s','%s',now()-interval '%d days'-interval '%d hours');"
                 % (crew, u, txt.replace("'", "''"), d, h))
    # 크루 챌린지(목표)
    s.append("insert into crew_goals(crew_id,kind,target,progress,period_start,period_end,title) "
             "values ('%s','distance',100000,63400,(now()-interval '6 days')::date,(now()+interval '8 days')::date,'이번 주 100km 함께 달리기') "
             "on conflict do nothing;" % CREW_HANGANG)

    s.append("end $$;")
    return "\n".join(s)


def post(sql):
    req = urllib.request.Request(
        f"https://api.supabase.com/v1/projects/{REF}/database/query",
        data=json.dumps({"query": sql}).encode("utf-8"), method="POST")
    req.add_header("Authorization", f"Bearer {PAT}")
    req.add_header("Content-Type", "application/json")
    req.add_header("User-Agent", "Mozilla/5.0 (Windows NT 10.0) Chrome/120 Safari/537.36")
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:600]


if __name__ == "__main__":
    sql = build_sql()
    st, body = post(sql)
    print("APPLY", st, body[:300])
    # 검증
    checks = {
        "runs(공개/비공개)": f"select visibility,count(*) c from runs where user_id='{UID}' group by visibility",
        "dex 동수": f"select count(*) n from dex_entries where user_id='{UID}'",
        "칭호": f"select count(*) n from user_titles where user_id='{UID}'",
        "팔로잉/팔로워": f"select (select count(*) from follows where follower_id='{UID}') ing,(select count(*) from follows where followee_id='{UID}') er",
        "받은좋아요": f"select count(*) n from run_reactions where run_id in (select id from runs where user_id='{UID}')",
        "북마크경로": f"select count(*) n from route_bookmarks where user_id='{UID}'",
        "크루채팅": f"select count(*) n from crew_messages where user_id='{UID}'",
    }
    res = []
    for label, qq in checks.items():
        _, b = post(qq)
        res.append(f"{label} -> {b}")
    with open(r"D:/Rundex/_seed_result.txt", "w", encoding="utf-8") as f:
        f.write(f"APPLY {st} {body[:300]}\n" + "\n".join(res))
    print("verify written")
