# Scorelens Online – selbst gehostet (Docker)

Der Online-Modus der App braucht ein Supabase-Backend. Statt supabase.com kann es jeder selbst betreiben – dieser
Ordner enthält einen schlanken Stack (Postgres, Auth/GoTrue, REST/PostgREST, Realtime, Caddy als Gateway) mit den
offiziellen Supabase-Images. Ein Raspberry Pi 4 / kleiner VPS reicht (ca. 1,5 GB RAM).

## Start in 3 Schritten

```bash
cd selfhost
./setup.sh http://192.168.178.50:8000     # oder: ./setup.sh https://darts.example.com  (Domain → automatisches HTTPS)
docker compose up -d                       # erster Start: Images laden (~1 GB), Datenbank initialisieren, Schema einspielen
```

`setup.sh` gibt **Supabase-URL** und **Anon-Key** aus – genau diese beiden Werte in der App eintragen
(Startseite › Online spielen › Zahnrad, oder Einstellungen › Online-Modus). Danach registrieren (E-Mail + Passwort,
wird ohne Mailserver sofort bestätigt), als Gast spielen oder per OAuth anmelden.

- Schema aktualisieren (nach einem App-Update): `./migrate.sh` (idempotent).
- Datenbank-Oberfläche: `docker compose --profile studio up -d` → http://127.0.0.1:3000 (nur lokal; von außen per SSH-Tunnel).
- Logs: `docker compose logs -f auth rest realtime gateway`.
- Alles zurücksetzen: `docker compose down -v` (löscht die Datenbank!) und `.env` entfernen.

## Erreichbarkeit

| Variante | `setup.sh`-Argument | Hinweise |
|---|---|---|
| Nur im WLAN | `http://<LAN-IP>:8000` | Port 8000 (HTTP). Die App erlaubt Klartext-HTTP. |
| Über das Internet | `https://<domain>` | DNS-A-Record auf den Server, Ports 80 und 443 freigeben; Caddy holt das Zertifikat selbst. |
| Hinter eigenem Reverse-Proxy | `https://<domain>` und `CADDY_SITE_ADDRESS=:8000` in `.env` | Proxy → `localhost:8000`, WebSockets (`/realtime/v1/`) durchreichen. |

## Supabase OAuth (Google, GitHub, Discord)

1. Beim Anbieter eine OAuth-App anlegen; Redirect-/Callback-URL: `<SUPABASE_PUBLIC_URL>/auth/v1/callback`
   (z. B. `https://darts.example.com/auth/v1/callback`). Google verlangt dafür HTTPS.
2. In `.env` z. B. `GOOGLE_ENABLED=true`, `GOOGLE_CLIENT_ID=…`, `GOOGLE_SECRET=…` setzen.
3. `docker compose up -d` (Auth-Container startet neu).

Die App öffnet den Browser (`/auth/v1/authorize?provider=…`, PKCE) und wird über `scorelens://auth/callback`
zurückgerufen; diese URL ist in `ADDITIONAL_REDIRECT_URLS` bereits eingetragen.

## Was läuft wo

| Dienst | Image | Aufgabe |
|---|---|---|
| gateway | caddy | `/auth/v1` → auth, `/rest/v1` → rest, `/realtime/v1` → realtime (WebSocket); optional TLS |
| auth | supabase/gotrue | Konten, Passwort-Login, OAuth, Gast-Login, JWT |
| rest | postgrest | Tabellen und RPCs als REST-API, Zugriff über RLS |
| realtime | supabase/realtime | Live-Updates (postgres_changes) für Lobbys und Match-Ereignisse, Presence |
| db | supabase/postgres | Postgres 17 mit Supabase-Rollen; Schema aus `../supabase/migrations` |
| studio, meta | supabase/studio, postgres-meta | optional (Profil `studio`) |

Die Datenbank liegt im Docker-Volume `db-data`. Sicherung: `docker compose exec db pg_dump -U postgres postgres > backup.sql`.

## Sicherheit

- `.env` enthält alle Geheimnisse (Postgres-Passwort, JWT-Secret, Service-Role-Key) – nie weitergeben, nicht committen.
- Der Anon-Key darf in die App; alle Daten sind durch Row Level Security geschützt (siehe Migration).
- `SERVICE_ROLE_KEY` umgeht RLS – nur für Admin-Skripte auf dem Server.
- Registrierung abschalten: `DISABLE_SIGNUP=true` (bestehende Konten funktionieren weiter).
