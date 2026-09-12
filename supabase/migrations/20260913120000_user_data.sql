-- Scorelens Online – Einstellungen und lokale Spielerliste je Konto (Gerätewechsel), last-write-wins über updated_at.
-- Idempotent wie die anderen Migrationen.

create table if not exists public.user_data (
  user_id     uuid primary key references public.profiles (id) on delete cascade,
  -- AppSettings der App als jsonb (gerätespezifische Felder wie Lens-Kalibrierung werden beim Übernehmen ignoriert)
  settings    jsonb not null default '{}'::jsonb,
  -- lokale Spielerliste (Player[]) inkl. Avatare
  players     jsonb not null default '[]'::jsonb,
  -- Zeitstempel der letzten lokalen Änderung (epoch ms), entscheidet, welche Seite gewinnt
  updated_at  bigint not null default 0
);
alter table public.user_data enable row level security;

drop policy if exists user_data_own on public.user_data;
create policy user_data_own on public.user_data for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

grant select, insert, update, delete on public.user_data to authenticated, service_role;
