-- 러닝 인사이트 — 주간 거리(8주)·최장거리·최고페이스·이달·누적
create or replace function my_insights()
returns jsonb language sql security definer set search_path to 'public' as $function$
  with weeks as (
    select (date_trunc('week', now()) - (i || ' weeks')::interval)::date as wk
    from generate_series(7,0,-1) i
  ),
  agg as (
    select date_trunc('week', started_at)::date wk, sum(distance_m) m, count(*) c
    from runs where user_id = auth.uid() group by 1
  )
  select jsonb_build_object(
    'weeks', (select coalesce(jsonb_agg(jsonb_build_object(
                'label', to_char(w.wk, 'MM/DD'), 'm', coalesce(a.m,0), 'c', coalesce(a.c,0)) order by w.wk), '[]')
              from weeks w left join agg a on a.wk = w.wk),
    'longestM', coalesce((select max(distance_m) from runs where user_id=auth.uid()),0),
    'longestMs', coalesce((select max(duration_ms) from runs where user_id=auth.uid()),0),
    'bestPace', (select min(duration_ms/1000.0 / nullif(distance_m/1000.0,0))
                 from runs where user_id=auth.uid() and distance_m >= 1000),
    'monthM', coalesce((select sum(distance_m) from runs where user_id=auth.uid()
                        and started_at >= date_trunc('month', now())),0),
    'totalM', coalesce((select sum(distance_m) from runs where user_id=auth.uid()),0),
    'totalRuns', (select count(*) from runs where user_id=auth.uid())
  );
$function$;
grant execute on function my_insights() to authenticated;
