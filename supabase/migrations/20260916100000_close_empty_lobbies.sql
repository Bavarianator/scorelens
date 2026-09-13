-- Scorelens Online – offene Lobby ohne Beitritt nach 10 Minuten automatisch schließen.
-- Der Host steckt beim Anlegen selbst in lobby_players (create_lobby); "niemand beigetreten" heißt also:
-- höchstens ein Spieler drin und niemand hat sie eröffnet (status 'open'), 10 Minuten seit created_at.
-- Der Client bekommt das über die bestehende DELETE-Behandlung im Lobby-Kanal mit (OnlineController.onLobbyMessage).
-- Idempotent wie die anderen Migrationen; eigener Cron-Job, weil public.cleanup() nur einmal täglich läuft.

create or replace function public.close_empty_lobbies()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from public.lobbies l
  where l.status = 'open'
    and l.created_at < now() - interval '10 minutes'
    and (select count(*) from public.lobby_players lp where lp.lobby_id = l.id) <= 1;
$$;

-- Nur der Cron-Job (läuft als postgres) darf das aufrufen, nicht die Data-API
revoke all on function public.close_empty_lobbies() from public, anon, authenticated;

-- cron.schedule mit Jobname ist ein Upsert
select cron.schedule('scorelens-close-empty-lobbies', '*/2 * * * *', $$select public.close_empty_lobbies()$$);
