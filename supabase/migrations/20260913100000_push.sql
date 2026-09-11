-- Scorelens Online – FCM-Tokens für Push-Mitteilungen bei geschlossener App (Einladungen, Freundschaftsanfragen).
-- Der Versand läuft nicht in der Datenbank, sondern im Relay unter push/ (beobachtet invites/friendships per Realtime).
-- Idempotent wie die anderen Migrationen.

create table if not exists public.push_tokens (
  user_id     uuid not null references public.profiles (id) on delete cascade,
  token       text not null,
  updated_at  timestamptz not null default now(),
  primary key (user_id, token)
);
alter table public.push_tokens enable row level security;

-- Nur der Nutzer selbst sieht und pflegt seine Tokens; das Relay liest mit service_role.
drop policy if exists push_tokens_own on public.push_tokens;
create policy push_tokens_own on public.push_tokens for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

grant select, insert, update, delete on public.push_tokens to authenticated, service_role;
