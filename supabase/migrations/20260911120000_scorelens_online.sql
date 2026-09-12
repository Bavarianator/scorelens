-- Scorelens Online – Datenbankschema für Supabase (supabase.com oder Self-Hosting, siehe selfhost/).
-- Idempotent: kann mehrfach ausgeführt werden (create if not exists / create or replace / drop policy if exists).
--
-- Tabellen:  profiles (Spielerprofil je Konto), lobbies + lobby_players (Online-Lobby wie bei Autodarts),
--            matches (ein Spiel einer Lobby; Seed + Einstellungen + Spielerreihenfolge) und
--            match_events (Ereignisprotokoll: throw / next / undo – jeder Client spielt es deterministisch nach).
-- Realtime:  lobbies, lobby_players, matches, match_events sind in der Publikation supabase_realtime
--            (postgres_changes). Zugriffsrechte über RLS.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------------------------------------------
-- Profile
-- ---------------------------------------------------------------------------------------------------------------
create table if not exists public.profiles (
  id          uuid primary key references auth.users (id) on delete cascade,
  name        text not null check (char_length(name) between 1 and 32),
  -- ARGB-Farbe des Avatars wie in der App (Long)
  color       bigint not null default 4282339765,
  -- Kennzahlen aus abgeschlossenen Online-X01-Matches (finish_match)
  avg         numeric(6, 2) not null default 0,
  darts       integer not null default 0,
  points      integer not null default 0,
  matches     integer not null default 0,
  wins        integer not null default 0,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);
alter table public.profiles enable row level security;

drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles for select to authenticated using (true);
drop policy if exists profiles_insert_own on public.profiles;
create policy profiles_insert_own on public.profiles for insert to authenticated with check ((select auth.uid()) = id);
drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles for update to authenticated
  using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

-- Profil automatisch beim Anlegen eines Kontos (E-Mail, OAuth oder anonym)
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  colors bigint[] := array[4282339765,4293212469,4282622023,4294675456,4287505578,4278234305,4292352864,4285353025,4278426597,4286362434];
  display text;
begin
  display := coalesce(
    nullif(new.raw_user_meta_data ->> 'name', ''),
    nullif(new.raw_user_meta_data ->> 'full_name', ''),
    nullif(new.raw_user_meta_data ->> 'user_name', ''),
    nullif(split_part(coalesce(new.email, ''), '@', 1), ''),
    'Gast'
  );
  insert into public.profiles (id, name, color)
  values (new.id, left(display, 32), colors[1 + floor(random() * array_length(colors, 1))::int])
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------------------------------------------
-- Lobbies
-- ---------------------------------------------------------------------------------------------------------------
create table if not exists public.lobbies (
  id                uuid primary key default gen_random_uuid(),
  -- 6 Zeichen ohne verwechselbare Symbole; Beitritt per Code
  code              text not null unique,
  host_id           uuid not null references public.profiles (id) on delete cascade,
  name              text not null default '',
  -- GameSettings-JSON der App (Modus, Startwert, Legs, …)
  settings          jsonb not null,
  is_public         boolean not null default true,
  max_players       integer not null default 2 check (max_players between 2 and 6),
  status            text not null default 'open' check (status in ('open', 'running', 'closed')),
  current_match_id  uuid,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);
create index if not exists lobbies_public_idx on public.lobbies (status, is_public, created_at desc);
create index if not exists lobbies_host_idx on public.lobbies (host_id);
alter table public.lobbies replica identity full;
alter table public.lobbies enable row level security;

create table if not exists public.lobby_players (
  lobby_id   uuid not null references public.lobbies (id) on delete cascade,
  user_id    uuid not null references public.profiles (id) on delete cascade,
  position   integer not null default 0,
  ready      boolean not null default false,
  joined_at  timestamptz not null default now(),
  primary key (lobby_id, user_id)
);
create index if not exists lobby_players_user_idx on public.lobby_players (user_id);
alter table public.lobby_players replica identity full;
alter table public.lobby_players enable row level security;

-- ---------------------------------------------------------------------------------------------------------------
-- Matches und Ereignisprotokoll
-- ---------------------------------------------------------------------------------------------------------------
create table if not exists public.matches (
  id          uuid primary key default gen_random_uuid(),
  lobby_id    uuid not null references public.lobbies (id) on delete cascade,
  -- Zufalls-Seed der Spiel-Engine (zufälliger Startspieler, Hidden Cricket …) – auf allen Geräten identisch
  seed        bigint not null,
  settings    jsonb not null,
  -- Spieler in Wurfreihenfolge: [{"id": uuid, "name": text, "color": bigint}, …]
  players     jsonb not null,
  status      text not null default 'running' check (status in ('running', 'finished', 'aborted')),
  winner_id   uuid,
  -- PlayerMatchStats-Liste der App beim Abschluss
  stats       jsonb,
  created_at  timestamptz not null default now(),
  finished_at timestamptz
);
create index if not exists matches_lobby_idx on public.matches (lobby_id, created_at desc);
alter table public.matches enable row level security;

create table if not exists public.match_events (
  match_id    uuid not null references public.matches (id) on delete cascade,
  seq         integer not null check (seq > 0),
  user_id     uuid not null,
  kind        text not null check (kind in ('throw', 'next', 'undo')),
  number      integer,
  multiplier  integer,
  x           real,
  y           real,
  hold        boolean not null default false,
  -- Zeitstempel des Wurfs (Epoch-Millisekunden des Clients, für das Wurfprotokoll)
  at          bigint not null default 0,
  -- Zufällige Client-Kennung: der Absender erkennt sein eigenes Echo
  client      text not null default '',
  created_at  timestamptz not null default now(),
  primary key (match_id, seq)
);
alter table public.match_events enable row level security;

-- ---------------------------------------------------------------------------------------------------------------
-- Hilfsfunktionen (security definer, damit RLS-Policies keine rekursiven Abfragen brauchen)
-- ---------------------------------------------------------------------------------------------------------------
create or replace function public.is_lobby_member(p_lobby uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.lobby_players lp
    where lp.lobby_id = p_lobby and lp.user_id = (select auth.uid())
  );
$$;

create or replace function public.is_lobby_host(p_lobby uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.lobbies l where l.id = p_lobby and l.host_id = (select auth.uid())
  );
$$;

create or replace function public.is_match_player(p_match uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.matches m
    where m.id = p_match
      and m.players @> jsonb_build_array(jsonb_build_object('id', (select auth.uid())::text))
  );
$$;

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists lobbies_touch on public.lobbies;
create trigger lobbies_touch before update on public.lobbies for each row execute function public.touch_updated_at();
drop trigger if exists profiles_touch on public.profiles;
create trigger profiles_touch before update on public.profiles for each row execute function public.touch_updated_at();

-- Beitritt nur in offene Lobbys mit freiem Platz (atomar, auch bei gleichzeitigen Beitritten)
create or replace function public.lobby_players_guard()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  l public.lobbies%rowtype;
  n integer;
begin
  select * into l from public.lobbies where id = new.lobby_id for update;
  if not found then
    raise exception 'Lobby nicht gefunden' using errcode = 'P0002';
  end if;
  if l.status <> 'open' then
    raise exception 'Lobby ist nicht offen' using errcode = 'P0001';
  end if;
  select count(*) into n from public.lobby_players where lobby_id = new.lobby_id;
  if n >= l.max_players then
    raise exception 'Lobby ist voll' using errcode = 'P0001';
  end if;
  if new.position = 0 then
    new.position := n + 1;
  end if;
  return new;
end;
$$;

drop trigger if exists lobby_players_guard on public.lobby_players;
create trigger lobby_players_guard before insert on public.lobby_players
  for each row execute function public.lobby_players_guard();

-- ---------------------------------------------------------------------------------------------------------------
-- RLS-Policies
-- ---------------------------------------------------------------------------------------------------------------
-- Lobbys: alle angemeldeten Spieler dürfen lesen (öffentliche Liste, Beitritt per Code, Zuschauen)
drop policy if exists lobbies_select on public.lobbies;
create policy lobbies_select on public.lobbies for select to authenticated using (true);
drop policy if exists lobbies_insert on public.lobbies;
create policy lobbies_insert on public.lobbies for insert to authenticated with check (host_id = (select auth.uid()));
drop policy if exists lobbies_update on public.lobbies;
create policy lobbies_update on public.lobbies for update to authenticated
  using (host_id = (select auth.uid())) with check (host_id = (select auth.uid()));
drop policy if exists lobbies_delete on public.lobbies;
create policy lobbies_delete on public.lobbies for delete to authenticated using (host_id = (select auth.uid()));

drop policy if exists lobby_players_select on public.lobby_players;
create policy lobby_players_select on public.lobby_players for select to authenticated using (true);
drop policy if exists lobby_players_insert on public.lobby_players;
create policy lobby_players_insert on public.lobby_players for insert to authenticated with check (user_id = (select auth.uid()));
drop policy if exists lobby_players_update on public.lobby_players;
create policy lobby_players_update on public.lobby_players for update to authenticated
  using (user_id = (select auth.uid()) or public.is_lobby_host(lobby_id))
  with check (user_id = (select auth.uid()) or public.is_lobby_host(lobby_id));
drop policy if exists lobby_players_delete on public.lobby_players;
create policy lobby_players_delete on public.lobby_players for delete to authenticated
  using (user_id = (select auth.uid()) or public.is_lobby_host(lobby_id));

drop policy if exists matches_select on public.matches;
create policy matches_select on public.matches for select to authenticated using (true);
drop policy if exists matches_insert on public.matches;
create policy matches_insert on public.matches for insert to authenticated with check (public.is_lobby_host(lobby_id));
drop policy if exists matches_update on public.matches;
create policy matches_update on public.matches for update to authenticated
  using (public.is_match_player(id)) with check (public.is_match_player(id));

drop policy if exists match_events_select on public.match_events;
create policy match_events_select on public.match_events for select to authenticated using (true);
drop policy if exists match_events_insert on public.match_events;
create policy match_events_insert on public.match_events for insert to authenticated
  with check (user_id = (select auth.uid()) and public.is_match_player(match_id));

-- ---------------------------------------------------------------------------------------------------------------
-- RPCs (atomare Abläufe)
-- ---------------------------------------------------------------------------------------------------------------
create or replace function public.random_lobby_code()
returns text
language plpgsql
set search_path = ''
as $$
declare
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  code text := '';
  i integer;
begin
  for i in 1..6 loop
    code := code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
  end loop;
  return code;
end;
$$;

-- Lobby anlegen, Host tritt sofort bei. Alte, nicht laufende Lobbys (> 1 Tag) werden dabei aufgeräumt.
create or replace function public.create_lobby(p_settings jsonb, p_public boolean default true, p_max_players integer default 2, p_name text default '')
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := (select auth.uid());
  new_id uuid;
  new_code text;
begin
  if me is null then
    raise exception 'Nicht angemeldet' using errcode = '42501';
  end if;
  -- Alte Lobbys räumt der Cron-Job public.cleanup() auf (Migration …_leaderboard_cleanup.sql)
  -- Eigene alte Lobbys schließen (ein Host, eine Lobby)
  delete from public.lobbies where host_id = me;
  loop
    new_code := public.random_lobby_code();
    exit when not exists (select 1 from public.lobbies where code = new_code);
  end loop;
  insert into public.lobbies (code, host_id, name, settings, is_public, max_players)
  values (new_code, me, left(coalesce(p_name, ''), 40), p_settings, coalesce(p_public, true), least(greatest(coalesce(p_max_players, 2), 2), 6))
  returning id into new_id;
  insert into public.lobby_players (lobby_id, user_id, position, ready) values (new_id, me, 1, true);
  return new_id;
end;
$$;

-- Beitritt per Code; gibt die Lobby-ID zurück (Fehler, wenn voll oder nicht offen)
create or replace function public.join_lobby(p_code text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := (select auth.uid());
  l public.lobbies%rowtype;
begin
  if me is null then
    raise exception 'Nicht angemeldet' using errcode = '42501';
  end if;
  select * into l from public.lobbies where code = upper(trim(p_code));
  if not found then
    raise exception 'Kein Spiel mit diesem Code' using errcode = 'P0002';
  end if;
  if exists (select 1 from public.lobby_players where lobby_id = l.id and user_id = me) then
    return l.id;
  end if;
  insert into public.lobby_players (lobby_id, user_id) values (l.id, me);
  return l.id;
end;
$$;

-- Lobby verlassen: Host geht → nächster Spieler wird Host, letzter Spieler → Lobby wird gelöscht
create or replace function public.leave_lobby(p_lobby uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := (select auth.uid());
  l public.lobbies%rowtype;
  next_host uuid;
begin
  select * into l from public.lobbies where id = p_lobby for update;
  if not found then
    return;
  end if;
  delete from public.lobby_players where lobby_id = p_lobby and user_id = me;
  select user_id into next_host from public.lobby_players where lobby_id = p_lobby order by position, joined_at limit 1;
  if next_host is null then
    delete from public.lobbies where id = p_lobby;
  elsif l.host_id = me then
    update public.lobbies set host_id = next_host, status = case when status = 'running' then 'open' else status end where id = p_lobby;
  end if;
end;
$$;

-- Schnelles Spiel ("Gegner finden"): offene, öffentliche Lobby mit freiem Platz, Host-Average möglichst nah am eigenen.
-- Gibt die Lobby-ID zurück oder null (dann legt die App selbst eine öffentliche Lobby an).
create or replace function public.quick_match(p_mode text default 'X01')
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := (select auth.uid());
  my_avg numeric := coalesce((select avg from public.profiles where id = me), 0);
  target uuid;
begin
  if me is null then
    raise exception 'Nicht angemeldet' using errcode = '42501';
  end if;
  select l.id into target
  from public.lobbies l
  join public.profiles h on h.id = l.host_id
  where l.status = 'open' and l.is_public and l.host_id <> me
    and coalesce(l.settings ->> 'mode', 'X01') = p_mode
    and (select count(*) from public.lobby_players lp where lp.lobby_id = l.id) < l.max_players
    and l.updated_at > now() - interval '6 hours'
  order by abs(h.avg - my_avg), l.created_at
  limit 1;
  if target is null then
    return null;
  end if;
  insert into public.lobby_players (lobby_id, user_id) values (target, me) on conflict do nothing;
  return target;
end;
$$;

-- Match starten (nur Host): Match-Zeile anlegen, Lobby auf running setzen
create or replace function public.start_match(p_lobby uuid, p_seed bigint, p_settings jsonb, p_players jsonb)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := (select auth.uid());
  new_id uuid;
begin
  if not exists (select 1 from public.lobbies where id = p_lobby and host_id = me) then
    raise exception 'Nur der Host kann starten' using errcode = '42501';
  end if;
  if jsonb_array_length(p_players) < 2 then
    raise exception 'Mindestens zwei Spieler' using errcode = 'P0001';
  end if;
  update public.matches set status = 'aborted', finished_at = now() where lobby_id = p_lobby and status = 'running';
  insert into public.matches (lobby_id, seed, settings, players) values (p_lobby, p_seed, p_settings, p_players) returning id into new_id;
  update public.lobbies set status = 'running', current_match_id = new_id where id = p_lobby;
  return new_id;
end;
$$;

-- Match abschließen: Ergebnis speichern, Profile aktualisieren (X01: Average aus Darts/Punkten), Lobby wieder öffnen.
-- Idempotent: nur das erste Gerät, das das Ende meldet, schreibt.
create or replace function public.finish_match(p_match uuid, p_winner uuid, p_stats jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  m public.matches%rowtype;
  s jsonb;
  pid uuid;
begin
  select * into m from public.matches where id = p_match for update;
  if not found or m.status <> 'running' then
    return;
  end if;
  if not public.is_match_player(p_match) then
    raise exception 'Kein Spieler dieses Matches' using errcode = '42501';
  end if;
  update public.matches set status = 'finished', winner_id = p_winner, stats = p_stats, finished_at = now() where id = p_match;
  update public.lobbies set status = 'open', current_match_id = null where id = m.lobby_id;
  for s in select * from jsonb_array_elements(coalesce(p_stats, '[]'::jsonb)) loop
    begin
      pid := (s ->> 'playerId')::uuid;
    exception when others then
      continue;
    end;
    update public.profiles p set
      matches = p.matches + 1,
      wins = p.wins + case when pid = p_winner then 1 else 0 end,
      darts = p.darts + case when coalesce(m.settings ->> 'mode', 'X01') = 'X01' then coalesce((s ->> 'dartsThrown')::int, 0) else 0 end,
      points = p.points + case when coalesce(m.settings ->> 'mode', 'X01') = 'X01' then coalesce((s ->> 'pointsScored')::int, 0) else 0 end
    where p.id = pid;
    update public.profiles p set avg = case when p.darts = 0 then 0 else round(p.points::numeric / p.darts * 3, 2) end where p.id = pid;
  end loop;
end;
$$;

-- Match abbrechen (jeder Spieler): Lobby wieder öffnen
create or replace function public.abort_match(p_match uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  m public.matches%rowtype;
begin
  select * into m from public.matches where id = p_match for update;
  if not found or m.status <> 'running' then
    return;
  end if;
  if not public.is_match_player(p_match) then
    raise exception 'Kein Spieler dieses Matches' using errcode = '42501';
  end if;
  update public.matches set status = 'aborted', finished_at = now() where id = p_match;
  update public.lobbies set status = 'open', current_match_id = null where id = m.lobby_id;
end;
$$;

-- ---------------------------------------------------------------------------------------------------------------
-- Rechte und Realtime
-- ---------------------------------------------------------------------------------------------------------------
grant usage on schema public to anon, authenticated, service_role;
grant select, insert, update, delete on public.profiles, public.lobbies, public.lobby_players, public.matches, public.match_events to authenticated, service_role;
grant select on public.profiles, public.lobbies to anon;
grant execute on function public.create_lobby(jsonb, boolean, integer, text), public.join_lobby(text), public.leave_lobby(uuid),
  public.quick_match(text), public.start_match(uuid, bigint, jsonb, jsonb), public.finish_match(uuid, uuid, jsonb), public.abort_match(uuid),
  public.is_lobby_member(uuid), public.is_lobby_host(uuid), public.is_match_player(uuid) to authenticated, service_role;
revoke execute on function public.handle_new_user() from public, anon, authenticated;

do $$
begin
  if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    create publication supabase_realtime;
  end if;
end;
$$;

do $$
declare
  t text;
begin
  foreach t in array array['lobbies', 'lobby_players', 'matches', 'match_events'] loop
    if not exists (
      select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
    ) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end;
$$;
