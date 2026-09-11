# Scorelens Push-Relay

Schickt eine Mitteilung aufs Handy, wenn die App zu ist: Lobby-Einladung oder Freundschaftsanfrage. Beobachtet
`invites` und `friendships` per Realtime (service_role) und sendet über Firebase Cloud Messaging an die Tokens aus
`push_tokens` (Migration `20260913100000_push.sql`, App meldet ihr Token bei jedem Login).

Braucht einmalig den **Firebase-Service-Account**: Firebase-Konsole → Projekteinstellungen → Dienstkonten →
„Neuen privaten Schlüssel generieren“ → als `firebase-service-account.json` ablegen (steht in `.gitignore`).

```sh
# gegen das supabase.com-Projekt (irgendein Rechner, der dauerhaft läuft)
docker build -t scorelens-push . && docker run -d --restart unless-stopped --name scorelens-push \
  -e SUPABASE_URL=https://<ref>.supabase.co -e SERVICE_ROLE_KEY=<service_role-Key aus dem Dashboard> \
  -e GOOGLE_APPLICATION_CREDENTIALS=/app/firebase-service-account.json \
  -v $PWD/firebase-service-account.json:/app/firebase-service-account.json:ro scorelens-push

# im selfhost-Stack: firebase-service-account.json nach selfhost/ legen, dann
cd ../selfhost && docker compose --profile push up -d --build
```

Test: in Supabase Studio eine Zeile in `invites` einfügen (oder in der App einen Freund einladen, dessen Handy die
App geschlossen hat) → Log `→ <user>: 1 gesendet`, Mitteilung erscheint, Tippen öffnet die App mit der Einladungskarte.
