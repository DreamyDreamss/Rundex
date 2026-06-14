-- 다른 사용자 공개 프로필 RPC — 피드/친구찾기에서 러너 탭 시 노출.
-- 발견동·공개러닝수·칭호·팔로워/팔로잉·총거리 + 내가 팔로우 중인지(i_follow).
create or replace function public_profile(p_user uuid)
returns jsonb language sql security definer set search_path to 'public' as $$
  select jsonb_build_object(
    'id', p.id,
    'display_name', p.display_name,
    'handle', p.handle,
    'bio', p.bio,
    'dex', (select count(*) from dex_entries d where d.user_id = p.id),
    'runs', (select count(*) from runs r where r.user_id = p.id and r.visibility = 'public'),
    'titles', (select count(*) from user_titles t where t.user_id = p.id),
    'followers', (select count(*) from follows f where f.followee_id = p.id),
    'following', (select count(*) from follows f where f.follower_id = p.id),
    'total_m', coalesce((select sum(r.distance_m) from runs r
                          where r.user_id = p.id and r.visibility = 'public'), 0),
    'i_follow', exists(select 1 from follows f
                        where f.follower_id = auth.uid() and f.followee_id = p.id)
  ) from profiles p where p.id = p_user;
$$;
grant execute on function public_profile(uuid) to anon, authenticated;
