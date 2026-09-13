# Scorelens – Der kostenlose Darts-Scorer für Dein Handy

**Scorelens** ist eine kostenlose Android-App zum Zählen von Darts-Spielen – wie die teure Autodarts-App, aber ohne Kosten und ohne nerviges Abonnement. Alles funktioniert offline und komplett lokal auf Deinem Handy. Deine Daten bleiben privat und werden nirgendwohin hochgeladen.

Wenn Du möchtest, kannst Du optional online spielen, Gegner suchen und Dich mit Freunden messen – genau wie bei Autodarts. Du brauchst dafür nur einen kostenlosen Supabase-Account (oder wir stellen einen selbst gehosteten Server zur Verfügung).

## Wer sollte Scorelens nutzen?

- **Darts-Spieler**, die ein echtes Dartboard haben und die Punkte automatisch zählen lassen möchten
- **Trainingsspieler**, die ihre Statistiken festhalten wollen (3er-Average, Checkout-Quote, Trefferbild)
- **Online-Spieler**, die gegen andere spielen möchten, ohne bei Autodarts zu zahlen
- **Autodarts-Nutzer**, die zu einer kostenlosen Alternative wechseln möchten

## Was kann Scorelens?

### 🎯 Spielmodi (13 verschiedene)
Die App kennt die gleichen Spielarten wie Autodarts:
- **X01** (500, 301, 501, 701, 1001 Punkte) – der Standard
- **Cricket** – Zahlen 15–20 und Bull treffen
- **Around the Clock** – alle Zahlen 1–20 nacheinander
- **Round the World** – Single/Double/Triple sammeln
- **Killer, Shanghai, Count Up** und mehr

### 📷 Automatisches Erkennen (Lens)
Das Besondere: Dein Handy erkennt die Darts **automatisch mit der Kamera**. So funktioniert es:
1. Handy auf ein Stativ etwa 1 Meter vom Board entfernt, schräg davor (wie bei Autodarts)
2. Kamera starten – die App findet das Board per KI von selbst (1–2 Sekunden), kein Antippen nötig
3. Während des Spiels: Die App erkennt geworfene Darts automatisch und zeigt sie sofort an

Die Erkennung funktioniert mit zwei Methoden:
- **Klassische Methode**: Unterschied zwischen zwei Bildern (ohne KI, immer offline)
- **KI-Methode**: Eigens trainiertes Neuronales Netz findet Board und Dartspitzen noch genauer, läuft komplett auf dem Handy, keine Cloud

Falls die automatische Suche mal danebenliegt: mit dem Finger antippen, wo der obere Board-Rand ist, oder die 4 Punkte unter „Erweitert“ manuell ziehen.

### ⌨️ Manuelle Eingabe
Falls die Kamera nicht optimal ist:
- **Board antippen**: Virtuelles Dartboard auf dem Handy
- **Score eingeben**: Nur die Gesamtpunkte der Runde tippen
- **Dart für Dart**: Jeden Dart einzeln eingeben

### 🖥️ Remote Scoring
Das Handy bleibt als Kamera am Board, aber die Punkteübersicht läuft im Browser eines anderen Geräts (Tablet, PC):
- URL: `http://<Handy-IP>:8765` im WLAN öffnen
- Undo und Next können auch im Browser geklickt werden
- Live Lens-Bild neben den Spielerkarten sichtbar
- Tastenkürzel: U (Undo), Leertaste (Next), F (Vollbild)

### 📊 Statistiken nach jedem Spiel
Nach einem Match siehst Du:
- **3er-Average** – wie viele Punkte pro 3 Darts durchschnittlich
- **Checkout-Quote** – wie oft hast Du das Spiel beendet
- **Trefferbild** – farbige Dartscheibe zeigt, welche Felder Du getroffen hast
- **Head-to-Head** – Statistik gegen jeden einzelnen Gegner
- **Spielverlauf** – wie verlief das Match Punkt für Punkt

### 🤖 Gegen den Computer spielen
11 verschiedene Schwierigkeitsstufen – vom Anfänger (Average ~25) bis zur Maschine (105+).

### 🎮 Online spielen (optional)
- Lobbys mit Freunden (6-stelliger Code)
- Gegner-Suche nach Spielstärke (Matchmaking)
- Live-Matches – jeder spielt an seinem eigenen Board
- Automatische Neusynchronisation, falls das Netz kurz weg ist

## Installation

### Einfach für die meisten: Docker
Falls Du Docker installiert hast, reicht ein Befehl:
```bash
scripts/docker-build.sh
# APK liegt dann in: out/app-debug.apk
```

### Manuell (mit JDK 17 + Android SDK)
```bash
# Build
./gradlew :app:assembleDebug

# APK installieren
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Erste Schritte

1. **App installieren** und öffnen
2. **Profil anlegen** – Dein Name und eine Farbe
3. **Devices konfigurieren** (Einstellungen → Geräte):
   - **Lens aktivieren** – Handy als Kamera
   - Oder: **Board Manager** – wenn Du einen Autodarts Board Manager hast
   - Oder: **Remote Scoring** – um auf einem anderen Gerät zu spielen
4. **Neues Spiel starten**:
   - Oben: "Play Now" (schnell)
   - Oder: "Create Game" (Spieler auswählen, Modus, Einstellungen)
5. **Kalibrieren** (wenn Lens aktiviert):
   - Lens-Screen öffnen, Kamera starten
   - App findet das Board automatisch per KI – kein Antippen nötig
   - Status-Pill wird grün ("Kalibriert" → kurz danach "Ready to play")
6. **Spielen**! Direkt nach der Kalibrierung bietet der Lens-Screen „Sofort spielen“ mit Deinen letzten Einstellungen an

## Online-Modus einrichten (optional)

Du brauchst nur eine Supabase-URL und einen API-Schlüssel. Zwei Optionen:

### Option A: supabase.com (kostenlos, schnell)
1. Auf [supabase.com](https://supabase.com) ein Projekt anlegen (dauert 1 Minute)
2. In der App: Einstellungen → Online-Konto
3. URL und Key aus Supabase einfügen
4. Fertig! 🎉

Nur einmalig: In Supabase auch die Datenbank-Migrations aus `supabase/migrations/` ausführen.

### Option B: Selbst hosten (Docker)
Wenn Du einen Server hast oder eine Private Cloud willst:
```bash
cd selfhost
./setup.sh https://darts.example.com
docker compose up -d
```

Dann die Ausgabe des Scripts in die App eintragen.

## Einstellungen erklärt

### 👤 Profil
Wessen Statistiken auf der Startseite angezeigt werden

### 🔊 Caller & Sound
- **Caller (Sprachansage)**: App sagt Deine Scores an
- **Soundeffekte**: kleine Töne für Darts und Busts

### 🎮 Match-Anzeige
- **Chalkboard anzeigen**: Schreib-Tafel im Match
- **Checkout-Guide**: Hilft bei den letzten Punkten
- **Bildschirm an lassen**: Display bleibt während des Spiels an
- **Darts Zoom**: Zoom auf den Kamerabereich beim Werfen
- **Animationen**: Game-Shot-Animation und Match-Intro

### 🤖 Bot
Wie schnell der Computer zwischen den Würfen wartet (in Sekunden)

### 🎮 Geräte
- **Lens**: Kamera an/aus
- **Board Manager**: Autodarts-Hardware verbinden
- **Remote Scoring**: Browser-Ansicht auf anderem Gerät

## Wie funktioniert die Lens-Kalibrierung?

1. **Positionierung**:
   - Handy auf Stativ, ca. 1 Meter vom Board entfernt
   - **Schräg davor** – etwa 35–55° zur Boardfläche (nicht von vorn, nicht von der Seite!), die App zeigt den Winkel nur als Empfehlung an
   - Ganzes Board mit Zahlenring im Bild, gleichmäßiges Licht

2. **Automatische Suche**:
   - Kamera starten – die App sucht das Board per KI im ganzen Bild (die vier Punkte 20/1, 6/10, 3/19, 11/14 am äußeren Double-Ring)
   - Sobald ein stabiler Fund vorliegt: Status-Pill wird grün, kurz darauf "Ready to play"
   - Fällt die Suche schwer (schlechtes Licht, ungewöhnlicher Winkel): kurz auf den oberen Board-Rand tippen als Hinweis, oder die 4 Punkte unter „Erweitert“ → „Punkte manuell ziehen“ selbst setzen

3. **Danach**:
   - App erkennt jetzt automatisch Darts auf Deiner Scheibe
   - Kleine Kamerabewegungen gleicht die App selbst aus
   - Nach einem Positionswechsel oder am nächsten Spieltag: Button „Neu kalibrieren“ sucht das Board frisch (passiert bei jedem Kamerastart ohnehin automatisch)

## Fehlersuche

### "Kamera-Fehler"
- Kamera-Berechtigung in den Einstellungen geben
- App neu starten

### Lens erkennt Darts nicht richtig
- **Beleuchtung prüfen**: Board sollte gut ausgeleuchtet sein
- **Kalibrierung neu machen**: Lens-Screen → neu Kalibrieren
- **Handy-Position**: Nicht zu frontal, nicht zu schräg

### Remote Scoring: Seite wird nicht geladen
- Handy und Gerät im gleichen WLAN?
- Firewall-Regeln prüfen (Port 8765)
- URL aus den Einstellungen kopieren (nicht selbst eingeben)

## Spielmodi im Detail

### 🎯 X01
- Start: 301, 501, 701, 1001 (wählbar)
- Ziel: Exakt 0 erreichen
- Varianten:
  - **In-Modus**: Mit Single/Double/Master starten
  - **Out-Modus**: Mit Single/Double/Master beenden
  - **Bull-Modus**: Bull zählt als 50

### 🎪 Cricket
- Ziel: Zahlen 15–20 und Bull drei Mal treffen
- Wer zuerst alle trifft UND führt: Sieg
- Varianten: Standard (Punkte sammeln), Cut Throat (Gegner bekommen Punkte), No Score

### 🚀 Around the Clock
- Nacheinander 1, 2, 3, ... 20 treffen
- Single/Double/Triple möglich oder Mixed
- Schnellster gewinnt

### 🎯 Weitere Modi
**Round the World**: Single (1 Punkt), Double (2), Triple (3) sammeln
**Killer**: Jeder bekommt eine Zahl, die nur der Gegner treffen darf – wer 3× getroffen wird, ist raus
**Shanghai**: Alle Zahlen 1–20 in 20 Runden treffen, Triple zählt am meisten
**Segment Training**: Eine bestimmte Zahl üben, Statistiken tracken

## Technische Details (für Entwickler)

### Struktur
```
app/                          App-Quelle (Kotlin + Jetpack Compose)
  engine/                     Spiellogik für alle Modi
  lens/                       Kamera-Erkennung & KI
  ui/                         Bildschirme & Design
  remote/                     HTTP-Server für Remote Scoring
  online/                     Supabase-Integration
supabase/                     Datenbank-Migrationen
selfhost/                     Docker-Setup zum Selbsthosten
tools/finetune/              KI-Modell feineinstellen
```

### Abhängigkeiten
- **Jetpack Compose**: UI
- **TensorFlow Lite**: KI-Erkennung (optional)
- **CameraX**: Kamerazugriff
- **Supabase**: Backend für Online-Modus

### Performance
- Kamera-Analyse auf 360×480 Graubild (schnell)
- KI läuft auf eigenem Thread (ruckelfrei)
- Jeder Dart wird 3–5× gemessen, Median wird verwendet (genauer)

## Support & Lizenz

**Probleme melden**: Öffne ein Issue auf GitHub

**Lizenzen**:
- App: Eigener Code
- Schriften: SIL Open Font License 1.1
- KI-Modell: eigenes Feintuning auf Basis von dart-sense, CC BY-NC 4.0 – nur privat nutzen, nicht kommerziell
- Flutter Icons: ISC License

---

**Viel Spaß beim Spielen! 🎯**

Wenn Du Fragen hast, öffne ein Issue oder schreib eine Nachricht. Feedback ist willkommen!
