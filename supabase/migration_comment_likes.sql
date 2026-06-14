-- 댓글 좋아요 + 댓글목록 RPC(좋아요수·내가 눌렀는지 포함)
create table if not exists comment_likes (
  comment_id uuid not null references run_comments(id) on delete cascade,
  user_id uuid not null references profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (comment_id, user_id)
);
alter table comment_likes enable row level security;
drop policy if exists cl_read on comment_likes;
create policy cl_read on comment_likes for select using (true);
drop policy if exists cl_own on comment_likes;
create policy cl_own on comment_likes for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create or replace function run_comments_list(p_run_id uuid)
returns jsonb language sql security definer set search_path to 'public' as $function$
  select coalesce(jsonb_agg(jsonb_build_object(
      'id', c.id, 'name', p.display_name, 'handle', p.handle,
      'text', c.text, 'created_at', c.created_at,
      'likes', (select count(*) from comment_likes cl where cl.comment_id = c.id),
      'liked_by_me', exists(select 1 from comment_likes cl where cl.comment_id = c.id and cl.user_id = auth.uid())
    ) order by c.created_at), '[]')
  from run_comments c join profiles p on p.id = c.user_id
  where c.run_id = p_run_id;
$function$;
grant execute on function run_comments_list(uuid) to anon, authenticated;
