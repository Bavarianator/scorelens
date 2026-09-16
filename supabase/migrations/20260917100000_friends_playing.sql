-- Freundesliste: zusätzlich das laufende Match des Freundes (match_id), damit man ihm direkt zuschauen kann.
-- Rückgabetyp ändert sich, deshalb drop + create statt create or replace.
drop function if exists public.friends();

create function public.friends()
returns table (
  id uuid, name text, color bigint, avatar text, avg numeric, matches integer, wins integer,
  status text, incoming boolean, played integer, won integer, lobby_code text, match_id uuid
)
language sql
stable
security definer
set search_path = ''
as $$
  select p.id, p.name, p.color, p.avatar, p.avg, p.matches, p.wins,
    f.status, f.addressee = (select auth.uid()) as incoming,
    coalesce(h.played, 0)::integer, coalesce(h.won, 0)::integer,
    (select l.code from public.lobbies l join public.lobby_players lp on lp.lobby_id = l.id
      where lp.user_id = p.id and l.status = 'open' order by l.updated_at desc limit 1) as lobby_code,
    (select l.current_match_id from public.lobbies l join public.lobby_players lp on lp.lobby_id = l.id
      where lp.user_id = p.id and l.status = 'running' and l.current_match_id is not null order by l.updated_at desc limit 1) as match_id
  from public.friendships f
  join public.profiles p on p.id = case when f.requester = (select auth.uid()) then f.addressee else f.requester end
  left join lateral (
    select count(*) as played, count(*) filter (where m.winner_id = (select auth.uid())) as won
    from public.matches m
    where m.status = 'finished'
      and m.players @> jsonb_build_array(jsonb_build_object('id', (select auth.uid())::text))
      and m.players @> jsonb_build_array(jsonb_build_object('id', p.id::text))
  ) h on true
  where f.requester = (select auth.uid()) or f.addressee = (select auth.uid())
  order by f.status, p.name;
$$;

grant execute on function public.friends() to authenticated, service_role;
