# FreeDarts Relay (Cloudflare)

Online-Variante des Remote Scorings: Das Handy bleibt als Lens-Kamera am Board, die Spielansicht
läuft im Browser eines beliebigen Geräts – auch außerhalb des WLANs. Das Relay leitet nur weiter,
die Spiel-Engine bleibt auf dem Handy. Läuft komplett im Cloudflare-Free-Plan (Workers + Durable
Objects mit WebSocket-Hibernation), jeder kann es auf seinem eigenen Account betreiben.

## Deploy (einmalig, ca. 5 Minuten)

```bash
cd relay
npm install
npx wrangler login          # öffnet den Browser, Cloudflare-Konto (kostenlos)
npx wrangler deploy         # Ausgabe: https://freedarts-relay.<account>.workers.dev
```

Die ausgegebene URL in der App unter **Einstellungen › Online-Remote › Relay-URL** eintragen und den
Schalter aktivieren. Die App zeigt dann Link und QR-Code für Zuschauer (`https://…/b/<CODE>`).

## Lokal testen

```bash
npm run dev                 # http://localhost:8787
npm test                    # Smoke-Test gegen den laufenden Dev-Server (RELAY_URL überschreibbar)
```

## Funktionsweise

- `/ws/board/<code>?token=…` – WebSocket des Handys. Der erste Verbindungsaufbau belegt den Code mit dem
  Token; ein anderes Handy mit demselben Code bekommt 403 und würfelt in der App einen neuen Code.
- `/ws/view/<code>` – WebSocket eines Zuschauers. Erhält sofort den letzten Zustand und Online-Status,
  darf nur `undo`/`next` senden (max. 4 Befehle/s pro Verbindung).
- Ohne verbundenes Handy wird die Session nach 12 h freigegeben (Code, Token, Zustand gelöscht).
- Gespeichert wird nur der aktuelle Spielzustand (Spielernamen, Scores). Keine Historie, keine Konten.

## Sicherheit

- Wer den Link bzw. Code kennt, sieht das Spiel und kann Undo/Next drücken – wie im WLAN-Modus.
  Codes haben 6 Zeichen aus 32 Symbolen (ca. 1 Milliarde Kombinationen) und laufen ab.
- Das Token des Handys ist nur dem Handy bekannt; nur damit lässt sich der Zustand schreiben.
- TLS übernimmt Cloudflare.
