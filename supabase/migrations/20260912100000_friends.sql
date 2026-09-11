-- Scorelens Online – Profilbilder, Freunde und Einladungen.
-- Idempotent wie die erste Migration.
--
-- profiles.avatar : Profilbild als Base64-JPEG (max. 128 px, ~7 KB) – kein Storage-Bucket nötig, läuft auch selfhost.
-- friendships     : Freundschaft je Paar (requester → addressee), status pending | accepted.
-- invites         : Einladung eines Freundes in eine Lobby (Realtime → der Freund sieht sofort "Beitreten").

-- ponytail: Avatar liegt in der profiles-Zeile und wandert mit jedem select=* mit (Lobby-Listen, matches.players);
--           bei > 128 px oder Datenverkehrsproblemen in eine eigene Tabelle/Storage auslagern
alter table public.profiles add column if not exists avatar text;
alter table public.profiles drop constraint if exists profiles_avatar_size;
alter table public.profiles add constraint profiles_avatar_size check (avatar is null or length(avatar) <= 40000);

-- ---------------------------------------------------------------------------------------------------------------
-- Freundschaften
-- ---------------------------------------------------------------------------------------------------------------
create table if not exists public.friendships (
  requester   uuid not null references public.profiles (id) on delete cascade,
  addressee   uuid not null references public.profiles (id) on delete cascade,
  status      text not null default 'pending' check (status in ('pending', 'accepted')),
  created_at  timestamptz not null default now(),
  primary key (requester, addressee),
  check (requester <> addressee)
);
create index if not exists friendships_addressee_idx on public.friendships (addressee);
alter table public.friendships replica identity full;
alter table public.friendships enable row level security;

drop policy if exists friendships_select on public.friendships;
create policy friendships_select on public.friendships for select to authenticated
  using (requester = (select auth.uid()) or addressee = (select auth.uid()));
-- Anfrage annehmen: nur der Empfänger
drop policy if exists friendships_update on public.friendships;
create policy friendships_update on public.friendships for update to authenticated
  using (addressee = (select auth.uid())) with check (addressee = (select auth.uid()));
-- Ablehnen / entfernen: beide Seiten
drop policy if exists friendships_delete on public.friendships;
create policy friendships_delete on public.friendships for delete to authenticated
  using (requester = (select auth.uid()) or addressee = (select auth.uid()));

create or replace function public.are_friends(p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.friendships f
    where f.status = 'accepted'
      and ((f.requester = (select auth.uid()) and f.addressee = p_user) or (f.requester = p_user and f.addressee = (select auth.uid())))
  );
$$;

-- Freundschaftsanfrage (per QR-Code = Nutzer-ID oder aus der Namenssuche). Gibt es schon eine Anfrage in
-- Gegenrichtung, wird sie direkt angenommen. Idempotent.
create or replace function public.request_friend(p_user uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := (select auth.uid());
begin
  if me is null then
    raise exception 'Nicht angemeldet' using errcode = '42501';
  end if;
  if p_user = me then
    raise exception 'Das bist du selbst' using errcode = 'P0001';
  end if;
  if not exists (select 1 from public.profiles where id = p_user) then
    raise exception 'Spieler nicht gefunden' using errcode = 'P0002';
  end if;
  update public.friendships set status = 'accepted' where requester = p_user and addressee = me and status = 'pending';
  if found then return; end if;
  insert into public.friendships (requester, addressee) values (me, p_user) on conflict do nothing;
end;
$$;

-- Freundesliste mit Profil, Kopf-an-Kopf-Bilanz (abgeschlossene Online-Matches) und offener Lobby des Freundes.
create or replace function public.friends()
returns table (
  id uuid, name text, color bigint, avatar text, avg numeric, matches integer, wins integer,
  status text, incoming boolean, played integer, won integer, lobby_code text
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
      where lp.user_id = p.id and l.status = 'open' order by l.updated_at desc limit 1) as lobby_code
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

-- ---------------------------------------------------------------------------------------------------------------
-- Einladungen in eine Lobby
-- ---------------------------------------------------------------------------------------------------------------
create table if not exists public.invites (
  lobby_id    uuid not null references public.lobbies (id) on delete cascade,
  from_id     uuid not null references public.profiles (id) on delete cascade,
  to_id       uuid not null references public.profiles (id) on delete cascade,
  code        text not null,
  created_at  timestamptz not null default now(),
  primary key (lobby_id, to_id)
);
create index if not exists invites_to_idx on public.invites (to_id);
alter table public.invites replica identity full;
alter table public.invites enable row level security;

drop policy if exists invites_select on public.invites;
create policy invites_select on public.invites for select to authenticated
  using (from_id = (select auth.uid()) or to_id = (select auth.uid()));
drop policy if exists invites_insert on public.invites;
create policy invites_insert on public.invites for insert to authenticated
  with check (from_id = (select auth.uid()) and public.is_lobby_member(lobby_id) and public.are_friends(to_id));
drop policy if exists invites_delete on public.invites;
create policy invites_delete on public.invites for delete to authenticated
  using (from_id = (select auth.uid()) or to_id = (select auth.uid()));

-- ---------------------------------------------------------------------------------------------------------------
-- Rechte und Realtime
-- ---------------------------------------------------------------------------------------------------------------
grant select, update, delete on public.friendships to authenticated, service_role;
grant select, insert, delete on public.invites to authenticated, service_role;
grant execute on function public.are_friends(uuid), public.request_friend(uuid), public.friends() to authenticated, service_role;

do $$
declare
  t text;
begin
  foreach t in array array['friendships', 'invites'] loop
    if not exists (
      select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
    ) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end;
$$;
