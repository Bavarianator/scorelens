-- Scorelens Online – Cloud-Sicherung des lokalen Match-Verlaufs (MatchRecord der App als jsonb, je Konto).
-- Idempotent wie die erste Migration.

create table if not exists public.saved_matches (
  id          text primary key,
  user_id     uuid not null references public.profiles (id) on delete cascade,
  mode        text not null default 'X01',
  played_at   bigint not null default 0,
  -- vollständiger MatchRecord der App (Statistik + Wurfprotokoll)
  record      jsonb not null,
  created_at  timestamptz not null default now()
);
create index if not exists saved_matches_user_idx on public.saved_matches (user_id, played_at desc);
alter table public.saved_matches enable row level security;

drop policy if exists saved_matches_own on public.saved_matches;
create policy saved_matches_own on public.saved_matches for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

grant select, insert, update, delete on public.saved_matches to authenticated, service_role;
