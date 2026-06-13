-- 러닝 도감 — 시드 데이터 (테마 컬렉션 / 칭호 정의)
-- 클라이언트(assets/themes/seoul_themes.json, Titles.kt)와 일치시킨다.

-- 테마 컬렉션 ----------------------------------------------------
insert into theme_collections(slug, title) values
  ('hangang-bridges', '한강 다리'),
  ('five-palaces',    '서울 5대 궁'),
  ('seoul-landmarks', '서울 랜드마크'),
  ('hangang-parks',   '한강 공원'),
  ('seoul-mountains', '서울 산·전망')
on conflict (slug) do nothing;

-- 장소 (collection slug, name, lon, lat, radius)
insert into theme_places(collection_id, name, geom, radius_m)
select c.id, v.name, st_setsrid(st_makepoint(v.lon, v.lat),4326), v.r
from theme_collections c
join (values
  ('hangang-bridges','광진교',127.1117,37.5448,170),
  ('hangang-bridges','잠실대교',127.0931,37.5206,170),
  ('hangang-bridges','청담대교',127.0531,37.5269,170),
  ('hangang-bridges','영동대교',127.0470,37.5283,170),
  ('hangang-bridges','성수대교',127.0360,37.5310,170),
  ('hangang-bridges','동호대교',127.0167,37.5305,170),
  ('hangang-bridges','한남대교',127.0096,37.5277,170),
  ('hangang-bridges','반포대교',126.9966,37.5130,170),
  ('hangang-bridges','동작대교',126.9794,37.5106,170),
  ('hangang-bridges','한강대교',126.9590,37.5169,170),
  ('hangang-bridges','마포대교',126.9456,37.5390,170),
  ('hangang-bridges','양화대교',126.9026,37.5390,170),
  ('five-palaces','경복궁',126.9770,37.5796,160),
  ('five-palaces','창덕궁',126.9910,37.5794,160),
  ('five-palaces','창경궁',126.9950,37.5841,160),
  ('five-palaces','덕수궁',126.9751,37.5658,140),
  ('five-palaces','경희궁',126.9686,37.5710,140),
  ('seoul-landmarks','N서울타워',126.9882,37.5512,160),
  ('seoul-landmarks','DDP',127.0090,37.5663,150),
  ('seoul-landmarks','롯데월드타워',127.1025,37.5125,180),
  ('seoul-landmarks','63빌딩',126.9405,37.5198,160),
  ('seoul-landmarks','광화문광장',126.9769,37.5725,150),
  ('seoul-landmarks','서울숲',127.0374,37.5443,200),
  ('seoul-landmarks','청계광장',126.9784,37.5689,140),
  ('hangang-parks','여의도한강공원',126.9326,37.5285,220),
  ('hangang-parks','반포한강공원',126.9959,37.5103,220),
  ('hangang-parks','뚝섬한강공원',127.0690,37.5300,220),
  ('hangang-parks','잠실한강공원',127.0820,37.5180,220),
  ('hangang-parks','난지한강공원',126.8770,37.5667,220),
  ('hangang-parks','망원한강공원',126.8975,37.5557,220),
  ('seoul-mountains','북악산',126.9817,37.5921,220),
  ('seoul-mountains','인왕산',126.9580,37.5810,220),
  ('seoul-mountains','안산',126.9430,37.5747,220),
  ('seoul-mountains','응봉산',127.0360,37.5483,180),
  ('seoul-mountains','아차산',127.1028,37.5556,240)
) as v(slug,name,lon,lat,r) on v.slug = c.slug
on conflict do nothing;

-- 칭호 정의 (앱 Titles.kt 와 동일) -------------------------------
insert into titles(code, name, type, criteria) values
  ('first_card',     '첫 발걸음',     'milestone', '{"discovered":1}'),
  ('explorer_10',    '10동 탐험가',   'milestone', '{"discovered":10}'),
  ('explorer_25',    '25동 유랑가',   'milestone', '{"discovered":25}'),
  ('explorer_50',    '50동 개척자',   'milestone', '{"discovered":50}'),
  ('explorer_100',   '백동 정복자',   'milestone', '{"discovered":100}'),
  ('dist_10',        '첫 10K',        'milestone', '{"lifetimeM":10000}'),
  ('dist_21',        '하프 러너',     'milestone', '{"lifetimeM":21097.5}'),
  ('dist_42',        '풀코스',        'milestone', '{"lifetimeM":42195}'),
  ('dist_100',       '백킬로 클럽',   'milestone', '{"lifetimeM":100000}'),
  ('dist_200',       '이백킬로 클럽', 'milestone', '{"lifetimeM":200000}'),
  ('dist_500',       '오백킬로 레전드','milestone','{"lifetimeM":500000}'),
  ('first_bronze',   '첫 단골',       'grade',     '{"grade":"BRONZE"}'),
  ('first_silver',   '실버 단골',     'grade',     '{"grade":"SILVER"}'),
  ('first_gold',     '골드 단골',     'grade',     '{"grade":"GOLD"}'),
  ('early_bird',     '얼리버드',      'limited',   '{"hourFrom":5,"hourTo":7}'),
  ('night_owl',      '야간 러너',     'limited',   '{"hourFrom":22,"hourTo":4}'),
  ('theme_hangang',  '한강 다리 정복자','complete', '{"slug":"hangang-bridges"}'),
  ('theme_palaces',  '궁궐 순례자',   'complete',  '{"slug":"five-palaces"}'),
  ('theme_landmarks','랜드마크 헌터', 'complete',  '{"slug":"seoul-landmarks"}'),
  ('theme_parks',    '한강 공원 마스터','complete', '{"slug":"hangang-parks"}'),
  ('theme_mountains','서울 등반가',   'complete',  '{"slug":"seoul-mountains"}')
on conflict (code) do nothing;
