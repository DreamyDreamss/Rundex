-- 행정동 경계 적재 — app/src/main/assets/regions/korea.geojson (전국 3482개, 이미 EPSG:4326)
-- ogr2ogr 불필요. psql 하나로 적재한다.
-- 실행: psql "<Session pooler 연결문자열>" -f supabase/load_regions.sql
-- (schema.sql 을 먼저 실행해 regions 테이블/PostGIS가 있어야 함)

-- 1) geojson 파일 전체를 한 줄 text 로 읽는다 (탭/제어문자 없음 → CSV quote/delim을 0x01/0x02로 회피)
create temp table _raw(j text);
\copy _raw from 'D:/Rundex/app/src/main/assets/regions/korea.geojson' with (format csv, quote E'\x01', delimiter E'\x02')

-- 2) FeatureCollection 펼쳐서 regions 로 적재 (Polygon/MultiPolygon 모두 ST_Multi 로 정규화)
insert into regions(code, name, sido, geom)
select f->'properties'->>'code',
       f->'properties'->>'name',
       null,
       st_multi(st_setsrid(st_geomfromgeojson(f->>'geometry'), 4326))::geometry(MultiPolygon,4326)
from (select j::jsonb as jb from _raw) r,
     jsonb_array_elements(r.jb->'features') as f
on conflict (code) do nothing;

-- 3) 검증
select count(*) as regions_loaded from regions;
select code, name from regions order by code limit 5;
