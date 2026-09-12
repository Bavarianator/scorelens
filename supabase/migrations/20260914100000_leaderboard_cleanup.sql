-- Scorelens Online – Rangliste und nächtliches Aufräumen per pg_cron. Idempotent wie die anderen Migrationen.

-- ---------------------------------------------------------------------------------------------------------------
-- Rangliste: Top-100 nach Online-Average; security_invoker → RLS von profiles gilt (authenticated liest alles)
-- ---------------------------------------------------------------------------------------------------------------
create or replace view public.leaderboard with (security_invoker = true) as
  select id, name, color, avg, matches, wins, avatar
  from public.profiles
  where matches > 0
  order by avg desc, wins desc, matches desc
  limit 100;
grant select on public.leaderboard to authenticated;

-- ---------------------------------------------------------------------------------------------------------------
-- Aufräumen: alte Lobbys, Einladungen, Push-Tokens und inaktive Gastkonten. Läuft täglich 04:17 UTC.
-- Ersetzt das bisherige Gelegenheits-Löschen in create_lobby.
-- ---------------------------------------------------------------------------------------------------------------
create extension if not exists pg_cron;

create or replace function public.cleanup()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from public.lobbies where status <> 'running' and updated_at < now() - interval '1 day';
  delete from public.lobbies where status = 'running' and updated_at < now() - interval '3 days';
  delete from public.invites where created_at < now() - interval '1 day';
  delete from public.push_tokens where updated_at < now() - interval '90 days';
  -- Gastkonten ohne Anmeldung seit 30 Tagen; on delete cascade räumt profiles/saved_matches/push_tokens mit
  delete from auth.users where is_anonymous and coalesce(last_sign_in_at, created_at) < now() - interval '30 days';
$$;
-- Nur der Cron-Job (läuft als postgres) darf das aufrufen, nicht die Data-API
revoke all on function public.cleanup() from public, anon, authenticated;

-- cron.schedule mit Jobname ist ein Upsert
select cron.schedule('scorelens-cleanup', '17 4 * * *', $$select public.cleanup()$$);
