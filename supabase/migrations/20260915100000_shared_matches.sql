-- Scorelens Online – lokales Match auch dem Konto des Mitspielers gutschreiben.
-- Der Gastgeber scannt in der lokalen Lobby den Freundes-QR-Code des Gegners (Spieler-ID = Nutzer-ID); nach dem
-- Spiel legt share_match den MatchRecord zusätzlich im Verlauf jedes beteiligten Kontos ab, das nächste
-- syncMatches auf dessen Gerät holt ihn. Idempotent wie die anderen Migrationen.

-- Dasselbe Match liegt jetzt je Konto einmal vor
alter table public.saved_matches drop constraint if exists saved_matches_pkey;
alter table public.saved_matches add primary key (id, user_id);

-- ponytail: wer die Nutzer-ID (Freundes-Link/QR) kennt, kann Matches in diesen Verlauf legen – wie bei
--           Freundschaftsanfragen; bei Missbrauch auf public.are_friends(pid) einschränken
create or replace function public.share_match(p_record jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  me uuid := (select auth.uid());
  s jsonb;
  pid uuid;
begin
  if me is null then
    raise exception 'Nicht angemeldet' using errcode = '42501';
  end if;
  if coalesce(p_record ->> 'id', '') = '' or jsonb_typeof(p_record -> 'players') <> 'array' then
    raise exception 'Ungültiges Match' using errcode = '22023';
  end if;
  for s in select * from jsonb_array_elements(p_record -> 'players') loop
    begin
      pid := (s ->> 'playerId')::uuid;
    exception when others then
      continue; -- lokale Spieler-IDs sind auch UUIDs, Bots ("bot-3") nicht
    end;
    if pid = me or not exists (select 1 from public.profiles where id = pid) then
      continue;
    end if;
    insert into public.saved_matches (id, user_id, mode, played_at, record)
    values (p_record ->> 'id', pid, coalesce(p_record ->> 'mode', 'X01'), coalesce((p_record ->> 'finishedAt')::bigint, 0), p_record)
    on conflict (id, user_id) do nothing;
  end loop;
end;
$$;

grant execute on function public.share_match(jsonb) to authenticated, service_role;
