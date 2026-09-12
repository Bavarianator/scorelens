-- Scorelens Online – Turnier in der Lobby: der Host schreibt den Spielplan (Tournament-JSON der App) in die Lobby,
-- alle Clients sehen ihn über den bestehenden Lobby-Kanal. Jedes Turnierspiel ist ein normales Match der Lobby.
alter table public.lobbies add column if not exists tournament jsonb;
