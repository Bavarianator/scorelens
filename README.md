# Scorelens

(vormals FreeDarts) Kostenloser Darts-Scorer für Android (Kotlin, Jetpack Compose). Kein Konto, kein Abo, keine Cloud.
Alle Daten bleiben auf dem Gerät.

## Funktionen

- **Spielmodi** (wie Winmau Autodarts, inkl. der dort kostenpflichtigen): X01 (121–1001, Straight/Double/Master
  In & Out, Legs/Sets, Bull-Modus, Max-Runden), Cricket (Standard, Cut Throat, Tactics), Around the Clock,
  Round the World, Count Up, Random Checkout, Bob's 27, Segment Training, 121, Shanghai, Gotcha, Bermuda, Killer.
- **Eingabe**: virtuelles Dartboard (antippen), Gesamtscore über Nummernblock, Dart für Dart.
- **Lens (Kamera-Autoscoring)**: Handy auf ein Stativ ca. 1 m seitlich vor das Board. Vier Kalibrierpunkte am
  äußeren Doppelring (20/1, 6/10, 3/19, 11/14) ergeben eine Homographie Bild → Board. Die Erkennung arbeitet mit
  Differenzbild gegen ein Referenzbild, Stabilitätsprüfung, größtem Blob und Spitzenerkennung (schmales Ende der
  Hauptachse); Hand im Bild = Takeout, leeres Board = neue Aufnahme. Ohne Match-Limit, komplett offline.
- **KI-Erkennung**: liegt `app/src/main/assets/dartsense_yolov8n.tflite` vor (Export aus
  [dart-sense](https://github.com/bnww/dart-sense), YOLOv8n, CC BY-NC 4.0), erkennt ein neuronales Netz Dartspitzen und die
  vier Kalibrierpunkte direkt auf dem Gerät. Die klassische Erkennung übernimmt Bewegungs-/Takeout-Logik und dient als Rückfall.
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
  gilt für die laufende und die zuletzt abgeschlossene Aufnahme, das Spiel wird neu abgespielt.
- **Kamerabewegung**: Wird das Handy im Match bewegt, kalibriert Lens still nach und ordnet die Darts auf dem Board
  neu zu; neu sichtbare (vorher verdeckte) Darts werden nachgetragen.
- **Beschleunigung**: TensorFlow Lite mit GPU-Delegate (Rückfall CPU/XNNPACK); Objektivverzerrung wird, wenn der
  Kamera-HAL es unterstützt, korrigiert.
- **Board Manager**: optional Anbindung an einen Autodarts Board Manager im WLAN (`http://<ip>:3180/api/state`);
  Start/Stop/Reset/Kalibrieren aus der App.
- **Bots**: elf Stufen (Ø ca. 25 bis 105), simulierte Streuung auf echter Board-Geometrie.
- **Match-Ansicht**: Spielerkarten mit Rest, Average, Legs/Sets; Chalkboard; Checkout-Guide mit
  Segment-Hervorhebung; Undo; „Weiter“; Game-Shot-Banner; Caller per Sprachausgabe (TTS).
- **Ergebnis & Statistik**: 3-Dart-Average, First-9, Checkout-Quote, höchstes Finish, 60+/100+/140+/170+/180,
  Spielzeit, Rematch; Verlauf pro Spieler.

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

### Docker (optional)

```bash
scripts/docker-build.sh     # baut Image mit SDK, führt Tests aus und erzeugt die Debug-APK
```

## Struktur

```
app/src/main/java/com/freedarts/scorer/
  model/     Segment, Player, GameSettings, MatchRecord, AppSettings
  engine/    Board-Geometrie, Checkout-Rechner, Bot, DartGame-Basis, games/ (alle Modi)
  data/      Repository (JSON-Dateien im App-Speicher)
  board/     BoardManagerClient (Autodarts Board Manager, Port 3180)
  lens/      LensController (CameraX, Ablauf), FrameConverter (YUV → Bild), BoardFinder + CalibrationTracker
             (Kalibrierung), DartDetector (Differenzbild), YoloDartModel + TipTracker (KI-Spitzen), Homography
  remote/    RemoteServer (Mini-HTTP-Server für Remote Scoring)
  audio/     Caller (TextToSpeech, Töne)
  ui/        AppViewModel (Navigation, Match-Logik), screens/, components/, theme/
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
