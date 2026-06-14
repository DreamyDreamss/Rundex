# -*- coding: utf-8 -*-
"""행정동 경계 geom 정확도 재임포트.

기존 regions.geom 은 동당 평균 8정점으로 과단순화 → 지도 폴리곤이 각짐.
raqoon886/Local_HangJeongDong(행정안전부 기준) GeoJSON 의 정확 경계를
adm_cd(= 우리 regions.code 와 동일 7자리 체계)로 정확 매칭해 덮어쓴다.

PAT 는 SUPABASE_PAT env 로 주입:  SUPABASE_PAT=sbp_... python reimport_boundaries.py
"""
import json, urllib.request, urllib.parse, os

PAT = os.environ["SUPABASE_PAT"]
REF = "jtbtqmcwjtuzsfpqzunf"
UA = "Mozilla/5.0 (Windows NT 10.0) Chrome/120 Safari/537.36"
BASE = "https://raw.githubusercontent.com/raqoon886/Local_HangJeongDong/master/"
SIDOS = ["서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시", "대전광역시",
         "울산광역시", "세종특별자치시", "경기도", "강원도", "충청북도", "충청남도",
         "전라북도", "전라남도", "경상북도", "경상남도", "제주특별자치도"]
BATCH = 20


def db(sql):
    req = urllib.request.Request(
        f"https://api.supabase.com/v1/projects/{REF}/database/query",
        data=json.dumps({"query": sql}).encode("utf-8"), method="POST")
    req.add_header("Authorization", f"Bearer {PAT}")
    req.add_header("Content-Type", "application/json")
    req.add_header("User-Agent", UA)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:300]


def fetch(sido):
    url = BASE + "hangjeongdong_" + urllib.parse.quote(sido) + ".geojson"
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read().decode("utf-8"))


def main():
    ours = set(r["code"] for r in json.loads(db("select code from regions")[1]))
    total_ok = 0
    for sido in SIDOS:
        try:
            gj = fetch(sido)
        except Exception as e:
            print(sido, "FETCH FAIL", repr(e)); continue
        feats = [f for f in gj["features"] if f["properties"].get("adm_cd") in ours]
        ok = 0
        for i in range(0, len(feats), BATCH):
            stmts = []
            for f in feats[i:i + BATCH]:
                code = f["properties"]["adm_cd"]
                gjson = json.dumps(f["geometry"])
                stmts.append(
                    "update regions set geom=ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON('%s'),4326)) where code='%s';"
                    % (gjson, code))
            st, body = db("\n".join(stmts))
            if st in (200, 201):
                ok += len(stmts)
            else:
                print(sido, "batch", i, "FAIL", st, body[:120])
        total_ok += ok
        print(f"{sido}: {ok}/{len(feats)} 적용 (전체 features {len(gj['features'])})")
    print("총 적용:", total_ok)
    print("AFTER 전국 정점:", db(
        "select round(avg(st_npoints(geom))) avg_pts, min(st_npoints(geom)) mn, "
        "max(st_npoints(geom)) mx, count(*) n from regions")[1])


if __name__ == "__main__":
    main()
