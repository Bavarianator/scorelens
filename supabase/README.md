# Scorelens Online – Supabase-Projekt (supabase.com)

Alternative zum Self-Hosting (`selfhost/`): ein kostenloses Projekt auf supabase.com. Die App unterscheidet nicht
zwischen beiden – sie braucht nur URL und Anon-Key.

## Einrichten (ca. 10 Minuten)

1. Auf https://supabase.com ein Projekt anlegen (Free Tier reicht).
2. Schema einspielen – eine der beiden Varianten:
   - **SQL-Editor:** Inhalt aller Dateien in `migrations/` der Reihe nach einfügen und ausführen
     (`…_scorelens_online.sql` = Lobbys/Matches, `…_saved_matches.sql` = Cloud-Sicherung des Match-Verlaufs,
     `…_shared_matches.sql` = lokale Matches auch im Verlauf des Mitspielers, der per QR-Code mit seinem Konto mitspielt).
   - **CLI:** `supabase login` (Personal Access Token `sbp_…`, nicht der Secret Key), dann `supabase link --project-ref <ref>`
     und `supabase db push` (Konfiguration in `config.toml`).
3. **Authentication › URL Configuration:** `scorelens://auth/callback` unter *Redirect URLs* eintragen.
4. **Authentication › Providers:** E-Mail aktiv lassen (für Tests *Confirm email* ausschalten), optional
   *Anonymous sign-ins* (Gast-Login) sowie Google / GitHub / Discord (Supabase OAuth). Beim jeweiligen Anbieter
   als Redirect-URL `https://<projekt>.supabase.co/auth/v1/callback` hinterlegen.
5. **Project Settings › API:** *Project URL* und *anon public* Key (oder den neuen *Publishable key*) in der App
   eintragen: Startseite › Online spielen › Zahnrad.

Wer eine fertige APK mit voreingestelltem Server bauen will, setzt in `gradle.properties`
(oder `~/.gradle/gradle.properties`):

```
scorelens.supabaseUrl=https://<projekt>.supabase.co
scorelens.supabaseAnonKey=<anon key>
```

## Lokal entwickeln

`supabase start` startet den vollständigen Supabase-Stack per Docker (API unter http://127.0.0.1:54321, Studio unter
http://127.0.0.1:54323) und spielt die Migrationen ein; `supabase status` zeigt den Anon-Key. Für ein Handy im WLAN
die LAN-IP des Rechners statt 127.0.0.1 verwenden.

## Datenmodell

- `profiles` – Anzeigename, Avatar-Farbe, Online-Kennzahlen (3-Dart-Average, Matches, Siege); wird per Trigger beim
  Anlegen eines Kontos erzeugt.
- `lobbies`, `lobby_players` – Lobby mit 6-stelligem Code, Host, Spieleinstellungen (GameSettings-JSON der App),
  öffentlich/privat, max. Spieler, Status.
- `matches` – ein Spiel einer Lobby: Seed, Einstellungen, Spielerreihenfolge, Ergebnis.
- `match_events` – Ereignisprotokoll (`throw`, `next`, `undo`) mit laufender Nummer je Match. Jedes Gerät spielt
  dieselben Ereignisse in seine Spiel-Engine ein; Undo wirkt nur auf das eigene letzte Ereignis.
- RPCs: `create_lobby`, `join_lobby`, `leave_lobby`, `quick_match` (Gegner finden nach Average), `start_match`,
  `finish_match` (Ergebnis + Profil-Statistik), `abort_match`.
- Alle Tabellen mit Row Level Security; Realtime über `postgres_changes` (Publikation `supabase_realtime`).
