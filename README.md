# Scorelens

(vormals FreeDarts) Kostenloser Darts-Scorer für Android (Kotlin, Jetpack Compose). Kein Abo. Lokal ohne Konto – alle
Daten bleiben auf dem Gerät. Optional ein **Online-Modus wie bei Autodarts** (Lobbys, Gegner finden, Live-Matches)
über einen frei wählbaren Supabase-Server: kostenloses Projekt auf supabase.com oder selbst gehostet per Docker.

## Funktionen

- **Spielmodi** (wie Winmau Autodarts, inkl. der dort kostenpflichtigen, mit den Regeln und Einstellungen aus der
  Autodarts-Dokumentation): X01 (121–1001, Straight/Double/Master In & Out, Legs/Sets, Bull-Modus, Max-Runden),
  Cricket (Zahlen 15–20, Tactics 10–20 oder Hidden mit sieben verdeckten Zufallszahlen; Wertung Standard, Cut Throat,
  No Score), Around the Clock (1→20, 20→1 oder zufällig, 1–3 Treffer pro Zahl, Trefferart), Round the World
  (S/D/T = 1/2/3 Punkte), Count Up, Random Checkout (Out-Modus, 1–9 Aufnahmen pro Leg), Bob's 27 (Aus bei ≤ 0 oder
  negativ erlaubt), Segment Training (Trefferart, Ende nach Treffern oder Darts), 121 (9 oder 6 Darts, Soft / Hard
  Reset / Safehouse, Schritt 1/3/5, Ziel 170), Shanghai, Gotcha (Out-Modus, Ziel bis 701), Bermuda (letzte Runde
  Bullseye), Killer (Treffer-Konto wie bei Autodarts: eigene Zahl füllt Leben, Killer nehmen Leben, Trefferart).
- **Eingabe**: virtuelles Dartboard (antippen), Gesamtscore über Nummernblock, Dart für Dart.
- **Lens (Kamera-Autoscoring)**: Handy auf ein Stativ, ca. 1 m vom Bull und deutlich seitlich versetzt – Blick
  etwa 45° schräg auf die Scheibe, wie Autodarts es für seine Kameras vorgibt (35–55° zur Boardfläche; die Lens-App
  meldet „View more from the front“ / „View from the side“). Die App misst den Blickwinkel über das Achsenverhältnis
  der Board-Ellipse (auch bei KI-Kalibrierung aus der Homographie): unter ≈ 30° „zu schräg“, über ≈ 64° „zu frontal“
  (der Dart verdeckt sonst seine Spitze), Ideal 35–55°. Vier Kalibrierpunkte am
  äußeren Doppelring (20/1, 6/10, 3/19, 11/14) ergeben eine Homographie Bild → Board. Die Erkennung arbeitet mit
  Differenzbild gegen ein Referenzbild, Stabilitätsprüfung, größtem Blob und Spitzenerkennung (schmales Ende der
  Hauptachse); Hand im Bild = Takeout, leeres Board = neue Aufnahme. Ohne Match-Limit, komplett offline.
- **KI-Erkennung**: liegt `app/src/main/assets/dartsense_yolov8n.tflite` vor (Export aus
  [dart-sense](https://github.com/bnww/dart-sense), YOLOv8n, CC BY-NC 4.0), erkennt ein neuronales Netz Dartspitzen und die
  vier Kalibrierpunkte direkt auf dem Gerät. Die klassische Erkennung übernimmt Bewegungs-/Takeout-Logik und dient als Rückfall.
  Weil das Modell überwiegend mit Frontalaufnahmen trainiert wurde, bekommt es nicht den rohen Kamera-Ausschnitt,
  sondern den über die Kalibrier-Homographie **entzerrten** Board-Ausschnitt (Board als Kreis, 20 oben; `BoardRectifier`,
  `FrameConverter.warp`). Eine schräg stehende Kamera sieht für das Modell damit aus wie eine frontale; die Spitzen
  liegen in der Boardebene und werden exakt zurückgerechnet. Trainingsdaten und Referee-Bilder sind dieselben
  entzerrten Bilder.
  Das Modell läuft mit seiner Trainingsgröße 800×800 px (nicht 640) als float32-TFLite (12 MB; INT8 würde die Erkennung
  kleiner Spitzen verschlechtern). Die Eingabegröße wird zur Laufzeit aus dem Modell gelesen; das Bild wird wie beim
  Training per Letterbox mit bilinearer Skalierung bzw. Flächenmittelung eingepasst. Export und Verifikation gegen
  ultralytics: `tools/export_model.sh` (benötigt uv, Python 3.11, ultralytics, ai-edge-litert; ca. 5 min CPU).
- **Erkennungs-Pipeline (Autodarts-Niveau angestrebt)**: Kamera 1280×960; Bewegungs- und Stabilitätslogik auf einem
  360×480-Graubild; KI-Spitzenerkennung auf dem hochaufgelösten Board-Ausschnitt, auf einem eigenen Thread (die
  Bewegungslogik verpasst keine Frames). Jeder Dart wird über mindestens drei Auswertungen gemessen und der **Median**
  verwendet (einzelne Fehlmessungen fallen heraus); nahe an einem Draht (< 1,5 mm) fünf, bei einem KI-Fund ohne
  Bewegungsereignis vier. Gezählte Darts werden **verfolgt**: neue Spitzen werden per Nächster-Nachbar-Zuordnung von den
  bekannten getrennt, sodass auch eng gruppierte Darts (T20-Gruppe, wenige Millimeter Abstand) einzeln zählen. Landet
  Dart 2, bevor Dart 1 fertig bestätigt ist, wird Dart 1 mit dem bisherigen Stand abgeschlossen. Kalibrierung aus sechs
  KI-Keypoints, verfeinert über Ring-Kanten/Drähte (Sub-Millimeter), zeitlicher Median; Fokus/Belichtung/Weißabgleich
  nach der Kalibrierung gesperrt; gemittelte Referenzbilder; Takeout klassisch und KI-bestätigt; Drift-Korrektur.
  Das Live-Bild wird auf die Scheibe zugeschnitten (Match-Ansicht, Lens-Screen umschaltbar).
- **Detection Mode** wie bei Autodarts: Vollbild mit Live-Bild, Status-Pill „Detecting“, drei Dart-Symbolen und Tipps;
  grüner Rahmen plus Vibration, sobald das Board erkannt und die Referenz gesetzt ist; Positionierungs-Meldungen
  („Das ganze Board muss sichtbar sein“, „Näher“, „Mehr von vorn“, „Mehr von der Seite“).
- **Remote Scoring**: Das Handy bleibt als Kamera am Board, die Spielansicht läuft im Browser eines zweiten Geräts
  (`http://<Handy-IP>:8765`, Undo/Next aus dem Browser). Eingebauter Mini-HTTP-Server, nur im lokalen Netz.
- **Dart-Korrektur im Match**: Dart-Slot antippen, richtiges Segment auf dem virtuellen Board wählen oder „Bouncer“;
  gilt für die laufende und die zuletzt abgeschlossene Aufnahme, das Spiel wird neu abgespielt. Kam der Dart von Lens,
  zeigt der Dialog das **Referee-Bild**: den Kamera-Ausschnitt der erkannten Spitze mit Markierung (offline-Ersatz für
  den AI Referee von Autodarts).
- **Kamerabewegung**: Wird das Handy im Match bewegt, kalibriert Lens still nach und ordnet die Darts auf dem Board
  neu zu; neu sichtbare (vorher verdeckte) Darts werden nachgetragen.
- **Beschleunigung**: TensorFlow Lite mit GPU-Delegate (Rückfall CPU/XNNPACK); Objektivverzerrung wird, wenn der
  Kamera-HAL es unterstützt, korrigiert.
- **Online-Modus (Supabase)**: Konto per E-Mail, Supabase OAuth (Google, GitHub, Discord) oder als Gast; Profil mit
  Online-Average; Lobbys wie bei Autodarts (6-stelliger Code, öffentlich/privat, 2–6 Spieler, Host wählt Modus und
  Einstellungen), „Gegner finden“ (Matchmaking nach Average) und Live-Matches: jeder wirft an seinem eigenen Board
  (Lens, Board Manager oder manuell), alle Geräte spielen dasselbe Ereignisprotokoll in die Spiel-Engine ein
  (Realtime, Undo nur für das eigene letzte Ereignis, automatische Neusynchronisation). Backend wahlweise
  **supabase.com** (`supabase/`) oder **selbst gehostet per Docker** (`selfhost/`, Postgres + Auth + REST + Realtime
  + Caddy) – die App braucht nur URL und Anon-Key.
- **Board Manager**: optional Anbindung an einen Autodarts Board Manager im WLAN (`http://<ip>:3180/api/state`);
  Start/Stop/Reset/Kalibrieren aus der App.
- **Bots**: elf Stufen (Ø ca. 25 bis 105), simulierte Streuung auf echter Board-Geometrie.
- **Match-Ansicht**: Spielerkarten mit Rest, Average, Legs/Sets; Chalkboard; Checkout-Guide mit
  Segment-Hervorhebung; Undo; „Weiter“; Game-Shot-Banner; Caller per Sprachausgabe (TTS).
- **Ergebnis & Statistik**: 3-Dart-Average, First-9, Checkout-Quote, höchstes Finish, 60+/100+/140+/170+/180,
  Spielzeit, Rematch; Verlauf pro Spieler; **Trefferbild** (Segmente nach Häufigkeit eingefärbt, Auftreffpunkte aus
  Lens) und **Head-to-Head** (Bilanz, Legs und Averages gegen jeden Gegner).

## Oberfläche

Angelehnt an die Winmau-Autodarts-App: Dashboard mit Profilkarte (3 Dart Avg, Win Rate, Checkout %), Play Now,
Create-Game-Lobby (Players, Add Player/Add Bot, Shuffle, Modus-Karte mit How to play und Edit settings,
Autoscoring-Schalter), Select Game Mode (Kategorien X01 / Practice / Party), Match-Ansicht mit Spielerkarten,
Dart-Slots, Board/Kamera und Bottom-Bar (Board / Score / Darts / Undo / Next), Statistik mit Overview und Match History.

## Build

Voraussetzungen: JDK 17, Android SDK (Platform 36, Build-Tools 36). `local.properties` zeigt auf das SDK.

```bash
./gradlew :app:assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # Engine-Tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Docker (empfohlen, wenn kein JDK 17 / SDK installiert ist)

Der Build läuft komplett im Container (JDK 17, Android SDK 36, Gradle-Wrapper und alle Abhängigkeiten liegen im
Image); auf dem Rechner wird nur Docker gebraucht. Unit-Tests laufen mit, ein Testfehler bricht den Build ab.

```bash
scripts/docker-build.sh          # Arbeitsbaum → out/app-debug.apk, out/app-debug.apk.sha256, out/test-report/
scripts/docker-build.sh --head   # exakt der letzte Commit (git archive), unabhängig vom Arbeitsbaum
scripts/docker-build.sh --image  # Builder-Image "scorelens-builder" für interaktive Nutzung:
docker run --rm -it -v "$PWD":/src scorelens-builder bash
```

Versionen (Build-Args im `Dockerfile`): Command-line Tools 15859902, Platform 36, Build-Tools 36.0.0.
Der erste Lauf lädt SDK und Abhängigkeiten (ca. 1,5 GB); danach werden nur geänderte Stufen neu gebaut.

### Beta-Release (signierte APK, Firebase)

Die Release-APK wird mit `release.jks` signiert; die Zugangsdaten liegen in `keystore.properties` in der
Projektwurzel (beide nicht im Git – **Keystore sichern**, ohne ihn können Tester spätere Updates nicht
über die installierte Version einspielen). Fehlt die Datei, wird debug-signiert.

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

Firebase (Crashlytics für Absturzberichte, App Distribution für die Verteilung an Tester) ist vorbereitet und
aktiviert sich, sobald `app/google-services.json` vorhanden ist:

1. [Firebase-Konsole](https://console.firebase.google.com): Projekt anlegen, Android-App mit Paketname
   `com.freedarts.scorer` hinzufügen, `google-services.json` herunterladen und nach `app/` legen.
2. In der Konsole Crashlytics und App Distribution aktivieren; unter App Distribution eine Tester-Gruppe
   `beta` mit den E-Mail-Adressen anlegen.
3. Einmalig `npm i -g firebase-tools && firebase login`, danach pro Beta-Version (App-ID = `mobilesdk_app_id`
   aus `google-services.json`):

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleRelease
firebase appdistribution:distribute app/build/outputs/apk/release/app-release.apk \
  --app 1:XXXX:android:XXXX --groups beta --release-notes "Beta 1"
```

Tester bekommen eine E-Mail mit Installationslink und werden bei neuen Versionen benachrichtigt.
Vor jeder Version `versionCode` in `app/build.gradle.kts` erhöhen.

## Online-Modus einrichten

Die App braucht nur zwei Angaben (Startseite › **Online spielen** › Zahnrad oder Einstellungen › Online-Modus):
die Supabase-URL und den Anon-Key. Zwei Wege:

- **supabase.com** (kostenlos): Projekt anlegen, `supabase/migrations/*.sql` im SQL-Editor ausführen (oder
  `supabase db push`), `scorelens://auth/callback` als Redirect-URL eintragen, gewünschte Login-Anbieter aktivieren.
  Details: `supabase/README.md`.
- **Selbst hosten (Docker)**: `cd selfhost && ./setup.sh https://darts.example.com && docker compose up -d` – das
  Skript erzeugt alle Geheimnisse und gibt URL und Anon-Key aus. Läuft auch nur im WLAN (`./setup.sh http://<IP>:8000`).
  Details: `selfhost/README.md`.

Ablauf im Spiel: Host erstellt eine Lobby (oder „Gegner finden“), andere treten per Code oder aus der Liste bei,
der Host startet. Jeder Spieler sieht das Match live, wirft in seiner Aufnahme an seinem eigenen Board; Lens und
Board Manager funktionieren wie offline. Ergebnisse landen in der lokalen Statistik (dem Profil-Spieler zugeordnet)
und im Online-Profil.

## Struktur

```
app/src/main/java/com/freedarts/scorer/
  model/     Segment, Player, GameSettings, MatchRecord, AppSettings
  engine/    Board-Geometrie, Checkout-Rechner, Bot, DartGame-Basis, games/ (alle Modi)
  data/      Repository (JSON-Dateien im App-Speicher)
  board/     BoardManagerClient (Autodarts Board Manager, Port 3180)
  lens/      LensController (CameraX, Ablauf), FrameConverter (YUV → Bild), BoardFinder + CalibrationTracker
             (Kalibrierung), DartDetector (Differenzbild), YoloDartModel + TipTracker (KI-Spitzen), Homography
  remote/    RemoteServer (Mini-HTTP-Server für Remote Scoring), CloudRelayClient (Online-Remote)
  online/    SupabaseApi (Auth + REST), RealtimeClient (Phoenix-WebSocket), OnlineController (Konto, Lobbys, Match-Sync)
  audio/     Caller (TextToSpeech, Töne)
  ui/        AppViewModel (Navigation, Match-Logik), screens/, components/, theme/
supabase/    Datenbankschema (migrations/) und CLI-Konfiguration für supabase.com
selfhost/    Docker-Compose-Stack zum Selbsthosten (setup.sh, migrate.sh, Caddyfile)
relay/       Cloudflare-Relay für das Online-Remote-Scoring (Zuschauer-Link)
```

## Design

Das Design (Canvas unter `design/`, Artboards als `.dc.html`) folgt dem Stil der Autodarts-App: Navy `#0B1220` mit
diagonalen Flächen, Karten `#171C27` (Radius 16), Primär-Blau `#2B6BFF`, Überschriften in Barlow Condensed, Text in
DM Sans, schräge Namens-Ribbons mit Level-Badge, Kategorie-Badges (X01 grün, Cricket blau, Practice orange, Party
gelbgrün), Live-Match mit Magenta-Spielerkarte, Aufnahme-Leiste und Overlays im Kamerabild (Detecting, Darts-Zähler,
Checkout, Caller). Umgesetzt in `ui/theme/Theme.kt`, `ui/components/AdWidgets.kt` und den Screens.

## Match-Komfort

Farbcode wie bei Autodarts (Grün = Finish, Orange = Checkout-Vorschlag, gedämpftes Rot = Bust; Sets blau, Legs orange),
Quick-Correction-Grid (ein Tap pro Dart, Zahl lange drücken = Double) auch als Dart-für-Dart-Eingabe, Darts Zoom über
dem Kamerabild, vollflächiges Takeout-Panel mit Reset, Automatic Next Player (Verzögerung in den Einstellungen),
Match-Intro und Animationen für Caller, 180 und Game Shot (abschaltbar).

## Feintuning des KI-Modells

Die Lens kann Trainingsdaten sammeln (Lens-Screen → „Trainingsdaten sammeln“: Board-Ausschnitt + Label bei jedem
erkannten Dart). `tools/finetune/` baut daraus (plus optional DeepDarts) einen YOLO-Datensatz, trainiert das
dart-sense-Modell auf einem kostenlosen HF-CPU-Space, per Notebook auf einer Gratis-GPU (Kaggle/Colab) oder als
bezahlten HF-Job – jeweils mit Checkpoint-Upload und Resume –,
bewertet die Spitzen-Genauigkeit und exportiert das Ergebnis per `tools/export_model.sh` in die App.
Details: `tools/finetune/README.md`.

## Spiellogik wie bei Autodarts

- **Aufnahme-Sperre:** Bei Autoscoring (Lens, Board Manager) ist die Aufnahme nach dem dritten Dart, einem Bust oder
  einem Checkout gesperrt; weitere erkannte Darts werden ignoriert, erst der Takeout (oder „Next“) gibt den nächsten
  Spieler frei. Manuelle Eingabe und Bots wechseln sofort. Die Sperre steckt als `hold` im Wurf-Ereignis, Undo und
  Korrektur spielen sie identisch nach.
- **Startspieler:** Bull-off (Aus / Normal / Offiziell: bei Gleichstand wird in umgekehrter Reihenfolge nachgeworfen;
  mit Lens zählt der gemessene Abstand zum Bull, sonst der Ring) oder zufälliger Startspieler. Runden zählen ab dem
  Startspieler.
- **First to / Best of** für Legs und Sets, inklusive Unentschieden bei gerader Best-of-Zahl.
- **Wurfprotokoll:** Jeder Dart wird mit Set, Leg, Runde, Segment, Bust-Markierung und (bei Lens) Auftreffpunkt in
  Board-Millimetern im Match gespeichert. Daraus: Leg-für-Leg-Verlauf in der Match History, Trefferbild, bestes und
  schlechtestes Leg, höchster Score, Busts.
- **Statistik:** Zeitraum-Filter (heute, 7 Tage, 30 Tage, gesamt), alle Modi wählbar; Cricket-MPR und Trefferquoten
  (Around the Clock, Segment Training) werden persistiert; Legs gewonnen zählt über alle Sets.

## Umstieg von Autodarts

Avatar-Menü mit **Devices** (Lens: „Start Lens Detection Mode“, Board Manager, Remote Scoring), Onboarding beim ersten
Start (Profil anlegen, Devices, Play Now), „Gegner finden“ als Bot auf dem eigenen Niveau und eine Hilfeseite
„Umstieg von Autodarts“, die Begriffe und Wege gegenüberstellt.

## Lizenzen / Danksagung

- Schriften Barlow Condensed und DM Sans (`app/src/main/res/font`), SIL Open Font License 1.1.

- KI-Modell aus [dart-sense](https://github.com/bnww/dart-sense) (Ben Willshaw), Lizenz **CC BY-NC 4.0**: Nutzung nur
  nicht-kommerziell, mit Namensnennung. Damit ist FreeDarts inklusive Modell ausschließlich für den privaten,
  nicht-kommerziellen Gebrauch bestimmt.
- Grundlagen der Keypoint-Idee: McNally et al., *DeepDarts* (CVSports 2021).
- Klassische Board-Erkennung, Spiel-Engine und Oberfläche sind eigener Code dieses Projekts.
