# 🎯 Scorelens – Der kostenlose Darts-Scorer für Dein Handy

**Scorelens** ist eine kostenlose Android-App zum Zählen von Darts-Spielen – aufgebaut wie Autodarts, aber ohne Kosten,
ohne Abo und ohne Pflicht-Konto. Dein Handy erkennt die Darts **automatisch mit der Kamera**, sagt die Scores an, führt
Statistik und spielt auf Wunsch gegen Bots, im Turnier oder online gegen Freunde.

Alles Wichtige läuft **offline und lokal auf Deinem Handy**: Spiele, Spieler und Statistiken bleiben auf dem Gerät, die
KI-Erkennung rechnet komplett auf dem Handy, es gibt keine Cloud-Auswertung Deiner Kamerabilder. Nur anonyme
Nutzungsdaten und Absturzberichte gehen an Google Firebase – abschaltbar unter *Einstellungen › Scorelens*.

Wenn Du möchtest, kannst Du **optional online spielen**: Lobbys, Gegnersuche, Freunde, Einladungen, Rangliste und
Zuschauen – genau wie bei Autodarts. Dafür braucht es ein Supabase-Backend, entweder ein kostenloses Projekt auf
supabase.com oder einen selbst gehosteten Server (Docker, läuft sogar auf einem Raspberry Pi 4).

| | |
|---|---|
| **Plattform** | Android 8.0 (API 26) oder neuer |
| **Version** | `1.0.0-beta.12` |
| **Kosten** | kostenlos, keine Werbung, kein Abo |
| **Konto** | nicht nötig (nur für den Online-Modus) |
| **Autoscoring** | Handykamera (Lens), Autodarts Board Manager oder zweites Scorelens-Handy |
| **Spielmodi** | 13 (X01, Cricket/Tactics, 7 Trainings- und 4 Party-Modi) |

---

## Inhalt

1. [Wer sollte Scorelens nutzen?](#wer-sollte-scorelens-nutzen)
2. [Funktionen im Überblick](#funktionen-im-überblick)
3. [Scorelens und Autodarts im Vergleich](#scorelens-und-autodarts-im-vergleich)
4. [Installation](#installation)
5. [Erste Schritte](#erste-schritte)
6. [Die Startseite](#die-startseite)
7. [Ein Spiel anlegen (Lobby)](#ein-spiel-anlegen-lobby)
8. [Spielmodi im Detail](#spielmodi-im-detail)
9. [Während des Matches](#während-des-matches)
10. [Lens – Autoscoring mit der Handykamera](#lens--autoscoring-mit-der-handykamera)
11. [Geräte: Board Manager und Remote Scoring](#geräte-board-manager-und-remote-scoring)
12. [Bots](#bots)
13. [Checkout-Guide](#checkout-guide)
14. [Statistiken](#statistiken)
15. [Turniere](#turniere)
16. [Online-Modus](#online-modus)
17. [Online-Server einrichten](#online-server-einrichten)
18. [Einstellungen erklärt](#einstellungen-erklärt)
19. [Datenschutz](#datenschutz)
20. [Fehlersuche](#fehlersuche)
21. [Häufige Fragen](#häufige-fragen)
22. [Für Entwickler](#für-entwickler)
23. [Lizenzen und Danksagungen](#lizenzen-und-danksagungen)

---

## Wer sollte Scorelens nutzen?

- **Darts-Spieler mit echtem Board**, die die Punkte automatisch zählen lassen möchten, ohne Kamera-Hardware zu kaufen –
  ein Handy und ein Stativ reichen.
- **Trainingsspieler**, die ihren Fortschritt verfolgen wollen: 3-Dart-Average, First-9, Checkout-Quote, MPR,
  Trefferbild, Verlauf der letzten Spiele und ein Trainingsvorschlag des Tages.
- **Vereine und Kneipenabende**, die ein Turnier (K.-o. oder Jeder gegen jeden) ohne Zettel und Kreide spielen wollen.
- **Online-Spieler**, die gegen Freunde oder Fremde spielen möchten – jeder am eigenen Board – ohne dafür zu zahlen.
- **Autodarts-Nutzer**, die zu einer kostenlosen Alternative wechseln wollen. Begriffe, Farben und Abläufe sind
  bewusst an Autodarts angelehnt; unter *Einstellungen › Umstieg von Autodarts* steht eine Gegenüberstellung.
- **Besitzer eines Autodarts-Boards**, die ihre vorhandene Hardware über den Board Manager im WLAN weiterverwenden
  möchten.

---

## Funktionen im Überblick

### 🎯 13 Spielmodi
| Kategorie | Modi |
|---|---|
| **Wettkampf** | X01 (121 bis 1001), Cricket / Tactics / Hidden Cricket |
| **Training** | Around the Clock, Round the World, Count Up, Random Checkout, Bob's 27, Segment Training, 121 |
| **Party** | Shanghai, Gotcha, Bermuda, Killer |

Legs und Sets, *First to* oder *Best of*, Double/Master In und Out, Bull 25/50 oder 50/50, Rundenlimit, Ausbullen
(Bull-off) oder zufälliger Startspieler – alle Details weiter unten unter [Spielmodi im Detail](#spielmodi-im-detail).

### 📷 Lens – automatische Erkennung mit der Kamera
- Board wird beim Kamerastart **automatisch per KI gefunden** – kein Antippen, kein Ausrichten von Hand.
- Darts werden erkannt, über mehrere Messungen bestätigt und sofort eingetragen; **Takeout** (Darts ziehen) und
  **Bouncer** werden erkannt.
- Kamera darf **schräg** stehen wie bei Autodarts (35–55°); die App rechnet das Bild für die KI in die Frontalansicht.
- Kleine Kamerabewegungen gleicht die App selbst aus; nach einem Positionswechsel wird neu kalibriert, ohne dass Darts
  auf dem Board verloren gehen.
- **Referee-Bild** zu jedem erkannten Dart: Kamera-Ausschnitt um die Spitze zur Kontrolle.
- Läuft vollständig auf dem Gerät (TensorFlow Lite, GPU wenn verfügbar), **ohne Limit** und ohne Konto.

### ⌨️ Manuelle Eingabe
- **Board antippen** – virtuelles Dartboard, Segment antippen, auch Bouncer/Miss
- **Gesamtscore** – nur die Punkte der Aufnahme eintippen
- **Dart für Dart** – jeden Dart einzeln über ein Zahlenfeld

### 🖥️ Geräte
- **Autodarts Board Manager** im WLAN (Port 3180) als Wurfquelle
- **Remote Scoring**: Spielansicht im Browser eines Tablets, PCs oder Fernsehers (Port 8765)
- **Zweitgerät koppeln**: ein zweites Handy übernimmt die Würfe des Board-Handys per QR-Code

### 🤖 Bots
11 Stufen von Average ~25 bis ~105, auch mehrere Bots in einem Spiel, realistische Streuung um das Ziel statt
zufälliger Punkte.

### 📊 Statistiken
Kennzahl je Modus mit Trend, Head-to-Head, Trefferbild (Heatmap), Verteilung der Aufnahmen, Checkout-Doubles,
Serien, Aktivität, Spielzeit und Leg-für-Leg-Verlauf jedes Matches.

### 🏆 Turniere
K.-o.-System mit Freilosen oder Jeder gegen jeden mit Tabelle – lokal am Handy oder online in einer Lobby.

### 🌐 Online (optional)
Lobbys mit 6-stelligem Code, öffentliche Lobby-Liste, *Gegner finden* nach Spielstärke, Live-Matches mit
Neusynchronisation, Zuschauen, Rangliste, Freunde per QR-Code oder Link, Einladungen mit Push-Mitteilung,
Cloud-Sicherung von Verlauf, Spielern und Einstellungen.

### 🔊 Caller und Sound
Sprachansage der Scores, Restpunkte, Bust und Game Shot über die Android-Sprachausgabe – auf Deutsch oder im
englischen PDC-Stil, Stimme wählbar, Ansage erst ab einem Mindestscore. Dazu Soundeffekte und Clips für 180 und 0.

---

## Scorelens und Autodarts im Vergleich

| Funktion | Autodarts | Scorelens |
|---|---|---|
| Autoscoring mit Handykamera | Lens (Limit ohne Abo) | Lens, **ohne Limit** |
| Eigene Kamera-Hardware | Autodarts-Board | Weiterverwendbar über **Board Manager** |
| X01, Cricket | ✔ | ✔ |
| Killer, 121 und weitere Modi | mit Abo | **kostenlos** |
| Bots | ✔ | ✔ (11 Stufen) |
| Remote Scoring im Browser | ✔ | ✔ |
| Online-Lobbys, Matchmaking | Autodarts-Server | eigener oder supabase.com-Server |
| Konto | Pflicht | nur für Online |
| Caller | Huw Ware | Android-Sprachausgabe (Deutsch/Englisch) |
| Kosten | Abo für volle Funktionen | kostenlos |

Scorelens ist ein unabhängiges Projekt und steht in keiner Verbindung zu Autodarts.

---

## Installation

### Variante 1: Beta über Firebase App Distribution
Beta-Tester werden in die Firebase-Gruppe `beta` aufgenommen und bekommen neue Versionen per E-Mail-Einladung bzw.
über die App *Firebase App Tester*. Beim ersten Mal muss die Installation aus unbekannten Quellen erlaubt werden.

### Variante 2: Selbst bauen mit Docker (am einfachsten)
Du brauchst nur Docker – JDK, Android SDK und Gradle stecken im Build-Image:

```bash
scripts/docker-build.sh            # Arbeitsbaum inkl. uncommitteter Änderungen
scripts/docker-build.sh --head     # exakt der letzte Commit
scripts/docker-build.sh --image    # nur das Builder-Image "scorelens-builder" bauen
```

Ergebnis:
- `out/app-debug.apk` – die App
- `out/app-debug.apk.sha256` – Prüfsumme
- `out/test-report/index.html` – Bericht der Unit-Tests

### Variante 3: Manuell (JDK 17 + Android SDK)
```bash
# Debug-Build
./gradlew :app:assembleDebug

# Unit-Tests
./gradlew :app:testDebugUnitTest

# Per USB installieren (USB-Debugging am Handy aktivieren)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Signierter Release-Build (braucht keystore.properties, sonst debug-signiert)
./gradlew :app:assembleRelease
```

> **Wichtig:** Das Projekt braucht **JDK 17**. Mit neueren JDKs schlägt der Build fehl. Der Pfad zum Android SDK
> steht in `local.properties` (`sdk.dir=…`).

### Variante 4: Cloud-Build mit Codemagic
`codemagic.yaml` enthält zwei Workflows: **Check** (jeder Push auf `main`: Tests + Debug-APK) und **Beta-Release**
(manuell: signierte APK an die Firebase-Gruppe `beta`). Einmalig in Codemagic einrichten:
1. Mit GitHub anmelden, App hinzufügen → Repo `Bavarianator/scorelens`, Konfiguration „codemagic.yaml“.
2. App → Environment variables, Gruppe **`firebase`**, alle als *Secret*:
   - `KEYSTORE_BASE64`: Ausgabe von `base64 -w0 release.jks`
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`: Werte aus `keystore.properties`
   - `GOOGLE_SERVICES_JSON`: Inhalt von `app/google-services.json`
   - `FIREBASE_SERVICE_ACCOUNT`: JSON-Schlüssel eines Service-Kontos mit der Rolle „Firebase App Distribution Admin“
     (Google Cloud Console → Projekt `autodart-5c115` → IAM → Service-Konten → Schlüssel erstellen)
3. Vor jeder Beta `versionCode` in `app/build.gradle.kts` erhöhen, pushen, dann „Start new build“ → Beta-Release.

Der Gratis-Tarif von Codemagic bietet 500 Build-Minuten im Monat auf `mac_mini_m2`.

### Berechtigungen der App
| Berechtigung | Wofür |
|---|---|
| Kamera | Lens (Autoscoring), QR-Codes scannen |
| Internet, Netzwerkstatus, WLAN-Status | Board Manager, Remote Scoring, Online-Modus, IP-Anzeige |
| Mitteilungen | Push bei Einladungen und Freundschaftsanfragen |
| Vibration | Rückmeldung, sobald Lens bereit ist |

Die Kamera ist optional (`required=false`) – die App läuft auch auf Geräten ohne Kamera, dann nur mit manueller Eingabe.

---

## Erste Schritte

1. **App installieren und öffnen.**
2. **Profil anlegen** – Dein Name und eine Farbe. Dieser Spieler ist Dein Profil; seine Statistiken stehen auf der
   Startseite.
3. **Konto (optional)** – mit Google oder GitHub anmelden, um online zu spielen und Deine Daten geräteübergreifend zu
   sichern. Oder „Später – erst mal lokal spielen“.
4. **Lens einrichten** (optional, empfohlen):
   - Handy aufs Stativ, ca. 1 m vom Board, schräg davor (siehe [Aufbau](#aufbau-und-positionierung))
   - *Lens*-Tab öffnen → **Kamera starten**
   - Die App sucht das Board selbst; die Status-Pill wird grün („Kalibriert“ → „Ready to play“), das Handy vibriert
5. **Spielen!**
   - Direkt nach der Kalibrierung bietet der Lens-Screen **„Sofort spielen“** mit Deinen letzten Einstellungen an.
   - Oder auf der Startseite **Neues Spiel** (Modus, Spieler, Bots wählen) bzw. die große Karte mit dem letzten Spiel.
6. **Nach dem Match**: Ergebnis mit Statistik, **Rematch** mit einem Tipp.

---

## Die Startseite

| Bereich | Inhalt |
|---|---|
| **Weiterspielen** | Große Karte mit Deinem letzten Modus und Deinen Spielern – ein Tipp startet sofort. Läuft ein Match noch, führt sie zurück ins Spiel. |
| **Neues Spiel** | Lobby: Modus, Spieler, Bots, Einstellungen |
| **Gegner finden** | Angemeldet: Online-Gegnersuche (501, First to 3). Ohne Konto: ein Bot passend zu Deinem X01-Average. |
| **Online spielen** | Online-Übersicht bzw. zurück in Deine offene Lobby |
| **Turnier** | Erscheint, solange ein Turnier läuft: offene Spiele oder Sieger |
| **Player Card** | Tipp auf Dein Profilbild: Level, X01-Kennzahlen, Form, Erfolge – wie die Player Card bei Autodarts |
| **Deine Form** | Average, Checkout-Quote und Serie aus den letzten 10 X01-Spielen |
| **Training des Tages** | Ein Trainingsmodus, abgeleitet aus Deinem Checkout und Average – ein Tipp startet ihn |
| **Letzte Ergebnisse** | Die drei letzten Matches, „Alle“ öffnet den Verlauf |
| **Mitteilungen** | Einladungen, Freundschaftsanfragen und Ergebnisse an einem Ort |

Unten führt die Navigationsleiste zu **Start**, **Lens**, **Statistik** und **Einstellungen**.

---

## Ein Spiel anlegen (Lobby)

Die Lobby („Create Game“) ist wie bei Autodarts aufgebaut:

### Spieler
- **Spieler hinzufügen** – aus Deiner Spielerliste oder neu anlegen (auch als Gast). Spieler haben Namen, Farbe und
  optional ein Profilbild.
- **Bot hinzufügen** – Stufe 1 bis 11, auch mehrere Bots (gleiche Stufe wird durchnummeriert).
- **QR-Code des Mitspielers scannen** – spielt jemand mit eigenem Scorelens-Konto an Deinem Board mit, zeigt er seinen
  QR-Code (*Freunde › Mein QR-Code*). Das Match landet dann **auch in seinem Verlauf und seiner Statistik**.
- **Shuffle** mischt die Reihenfolge.
- **Tipp auf einen Namen** öffnet die **Player Card**: Level, Average, Checkout-Quote, 180er, höchstes Finish, Form,
  Head-to-Head gegen Dein Profil und Erfolge.

### Modus und Einstellungen
Modus wählen („Anderen Modus wählen ›“), dann die Optionen des Modus – siehe [Spielmodi im Detail](#spielmodi-im-detail).
Zu jedem Modus steht eine kurze Regelbeschreibung direkt in der Lobby.

### Startspieler
| Option | Bedeutung |
|---|---|
| **Aus** | Spieler 1 beginnt |
| **Bull-off (Normal)** | Jeder wirft einen Dart aufs Bull, wer näher dran ist, beginnt |
| **Bull-off (Offiziell)** | Wie Normal, bei Gleichstand wird in umgekehrter Reihenfolge erneut geworfen |
| **Zufälliger Start** | Zufälliger Startspieler ohne Ausbullen |

Bei mehreren Legs wechselt der Anwurf von Leg zu Leg.

### Autoscoring
Wie in der Autodarts-Lobby: **Lens** (Kamera dieses Handys) oder **Board Manager** (Autodarts-Hardware im WLAN). Das
aktive Gerät hat einen Haken. Ohne Autoscoring wird manuell eingegeben.

### Weitere Aktionen
- **Start Game** – Match starten
- **Als Turnier starten** – aus den Spielern der Lobby ein Turnier bauen (siehe [Turniere](#turniere))

---

## Spielmodi im Detail

### 🎯 X01
Jeder startet mit dem Startwert, geworfene Punkte werden abgezogen. Wer zuerst **exakt auf 0** kommt, gewinnt das Leg.

| Option | Werte |
|---|---|
| Startwert | 121, 170, 301, **501**, 701, 901, 1001 |
| Handicap | eigener Startwert je Spieler (z. B. 401 gegen 501) – in den X01-Einstellungen der Lobby unter „Start <Name>“ |
| In-Modus | **Straight In**, Double In, Master In (Double oder Triple) |
| Out-Modus | Straight Out, **Double Out**, Master Out (Double oder Triple) |
| Bull-Modus | **25/50** (Single Bull 25, Bullseye 50) oder 50/50 |
| Match-Modus | **Legs** oder Sets (Legs pro Set) |
| Gewinnmodus | **First to** n oder Best of n |
| Max. Runden | 0 = unbegrenzt; sonst gewinnt nach dem Limit der niedrigste Rest das Leg |

- **Bust**: Unter 0, bei Double/Master Out auf 1, oder 0 mit ungültigem letzten Dart → die ganze Aufnahme zählt nicht,
  der Score springt auf den Stand vor der Aufnahme zurück.
- **Best of** mit gerader Anzahl kann unentschieden enden (Leg bzw. Set ohne Sieger).
- Anzeige wie bei Autodarts: großer Rest, Leg-Average / Match-Average, Darts im Leg, Legs orange und Sets blau.

**Statistik:** 3-Dart-Average, First-9-Average, Checkout-Quote (Checkouts je Dart auf ein mögliches Finish), höchstes
Finish, höchste Aufnahme, 60+/100+/140+/170+/180, Busts, bestes und schlechtestes Leg (Darts).

### 🎪 Cricket / Tactics
Zahlen dreimal treffen („schließen“), Single = 1 Mark, Double = 2, Triple = 3. Wer alle Zahlen geschlossen hat und
nach Punkten führt (bzw. bei Cut Throat die wenigsten hat), gewinnt.

| Option | Werte |
|---|---|
| Zahlen | **Cricket** (15–20 + Bull), Tactics (10–20 + Bull), **Hidden** (sieben zufällige Zahlen, verdeckt bis zum ersten Treffer) |
| Wertung | **Standard** (Punkte auf offene Zahlen für Dich), **Cut Throat** (Punkte gehen an die Gegner, wenigste gewinnen), **No Score** (nur Schließen zählt) |
| Max. Runden | 0 = unbegrenzt |

**Statistik:** MPR (Marks per Round), Marks gesamt und pro Spiel, Darts je Zahl.

### 🕐 Around the Clock
Die Zahlen der Reihe nach treffen, optional mit Bull am Ende. Wer zuerst durch ist, gewinnt.

| Option | Werte |
|---|---|
| Reihenfolge | 1 → 20, 20 → 1, zufällig |
| Trefferart | Beliebig, nur Single, nur Double, nur Triple |
| Treffer pro Zahl | 1–3 (Double/Triple zählt als ein Treffer) |
| Bull am Ende | an/aus |
| Max. Runden | 0 = unbegrenzt |

**Kennzahl:** Trefferquote.

### 🌍 Round the World
Runde n = Zahl n (aufwärts, abwärts oder zufällig, optional mit Bull). Single = 1 Punkt, Double = 2, Triple = 3.
Die meisten Punkte gewinnen. Einstellbar: Anzahl Runden (Zahlen 1–n), Reihenfolge, Bull.

### ➕ Count Up
Acht Runden (einstellbar), alle Punkte zählen. Die höchste Gesamtpunktzahl gewinnt.

### 🎲 Random Checkout
Pro Leg ein zufälliger Rest zwischen **Min. Checkout** und **Max. Checkout** (Standard 41–170, nur Werte, die mit
drei Darts machbar sind). Pro Leg 1, 2, 3, 6 oder 9 Aufnahmen, Out-Modus wie eingestellt. Bust = Aufnahme zählt nicht.
Wer die meisten Legs auscheckt, gewinnt. Der Checkout-Guide zeigt den Weg.

### 🎯 Bob's 27
Start mit 27 Punkten, Doubles 1 bis 20 (optional Bull). Pro Runde: jeder Treffer **+ Wert des Doubles**, kein Treffer
**− Wert des Doubles**. Bei 0 oder weniger scheidet man aus – außer „Negative Punkte erlauben“ ist an.

### 🎯 Segment Training
Eine Zahl (oder Bull) in der gewählten Trefferart üben.
- **Ende nach Darts**: nach n Darts, die meisten Treffer gewinnen.
- **Ende nach Treffern**: nach n Treffern, die wenigsten Darts gewinnen.

**Kennzahl:** Trefferquote, dazu „Meist trainiertes Segment“ in der Statistik.

### 💯 121
Wie bei Autodarts: Ziel **121** mit 9 (oder 6) Darts auschecken, Double Out.
- **Erfolg:** Ziel steigt um 1, 3 oder 5 (bis 170).
- **Misserfolg** je nach Einstellung:
  - **Soft** – Ziel − 1
  - **Hard Reset** – zurück auf 121
  - **Safehouse** – nie unter das zuletzt gesicherte Ziel; gesichert wird nach jedem n-ten Erfolg
- Sieg bei 170 oder mit dem höchsten Ziel nach allen Versuchen – aber nur oberhalb von 121.

### 🀄 Shanghai
Runde n = Zahl n (Standard 7 Runden, bis 20). Punkte nur auf die Zahl der Runde. **Single, Double und Triple in
einer Aufnahme = „Shanghai!“ und sofortiger Sieg**, sonst gewinnen die meisten Punkte.

### 😈 Gotcha
Von 0 **exakt** auf das Ziel (101 bis 701, Standard 301), Out-Modus wie eingestellt. Überwerfen = Bust. Wer genau auf
dem Score eines Gegners landet, schickt diesen **zurück auf 0**.

### 🔺 Bermuda
Ziele der Reihe nach: 12, 13, 14, **Double**, 15, 16, 17, **Triple**, 18, 19, 20, **Bullseye**. Treffer zählen Punkte,
**ohne Treffer in der Runde wird der Score halbiert.**

### 🔪 Killer
Jeder Spieler bekommt eine Zahl. Treffer auf die eigene Zahl füllen das Konto (Single 1, Double 2, Triple 3). Ab der
eingestellten Zahl an **Leben** ist man **Killer** und nimmt anderen mit deren Zahl Leben weg. Fällt ein Killer unter
die Schwelle, ist er kein Killer mehr; trifft ein Killer die eigene Zahl, verliert er Leben. Wer bei 0 noch einmal
getroffen wird, scheidet aus. Der letzte Spieler gewinnt. Trefferart einstellbar (z. B. nur Doubles).

---

## Während des Matches

### Aufbau des Match-Screens
- **Kopfzeile:** Set, Leg, Runde (bzw. Modus-spezifisch, z. B. „Versuch 3 / 10 · Dart 4 / 9“)
- **Spielerkarten:** aktiver Spieler hervorgehoben, großer Score, Legs/Sets, Averages, Darts
- **Aufnahme-Leiste:** drei Dart-Pills mit Segment und Zwischensumme
- **Checkout-Guide:** Vorschlag für den Rest, z. B. `T20  T20  D25`
- **Chalkboard:** Verlauf aller Aufnahmen wie auf der Kreidetafel
- **Untere Leiste wie bei Autodarts:** Eingabe-Umschalter, Undo und großer **Next**-Button

### Eingabe
| Methode | So geht's |
|---|---|
| **Kamera (Lens)** | Einfach werfen – Darts erscheinen automatisch, Takeout wird erkannt |
| **Board antippen** | Segment auf dem virtuellen Board, Bouncer/Miss als eigene Taste |
| **Gesamtscore** | Punkte der Aufnahme eintippen |
| **Dart für Dart** | Jeden Dart einzeln |
| **Board Manager** | Würfe kommen von der Autodarts-Hardware oder einem gekoppelten Board-Handy |

### Korrigieren
- **Dart-Pill antippen** → Segment auf dem virtuellen Board neu wählen („Quick Correction: ein Tap = neuer Dart“).
  Ein Tipp auf das Board übernimmt auch die **Position** (bleibt im Trefferbild), und bei einem Lens-Dart wird das
  Kamerabild mit dem korrigierten Label als `fix_*` in den Trainingsordner gelegt – auch ohne „Trainingsdaten sammeln“.
  So wird jede Fehlerkennung automatisch zum Feintuning-Beispiel.
- **Letzten Dart zurücknehmen** (Undo), auch über mehrere Aufnahmen hinweg.
- **Referee-Bild**: bei Lens-Darts den Kamera-Ausschnitt um die Spitze ansehen, um strittige Darts zu prüfen.

### Takeout
Sobald Darts gezogen werden, erscheint das **TAKEOUT**-Panel in Warnfarbe. Ist das Board wieder leer, geht es mit dem
nächsten Spieler weiter. Mit **Automatisch nächster Spieler** wechselt die App nach einer angefangenen Aufnahme selbst,
wenn eine einstellbare Zeit lang kein Dart kommt.

### Match-Einstellungen im Spiel
Über das Zahnrad im Match: Caller, jede Aufnahme bzw. jeden Dart ansagen, Soundeffekte, Chalkboard, Checkout-Guide,
Eingabe wechseln, Lens neu kalibrieren, Referenz neu aufnehmen.

### Darts Zoom und Caller-Einblendung
Mit Lens läuft das Kamerabild im Match mit. **Darts Zoom** zeigt die aktuelle Aufnahme groß, lesbar von der Abwurflinie.
Nach jeder Aufnahme wird der Score kurz groß eingeblendet (antippen zum Überspringen).

### Spiel beenden
„Spiel beenden“ verwirft ein laufendes lokales Spiel **ohne** Statistik-Eintrag. Ein Online-Match wird für alle
abgebrochen und nicht gewertet.

### Ergebnis
Sieger, Legs/Sets, Average, First-9, Checkout-Quote, höchster Score, höchstes Finish, bestes/schlechtestes Leg, Busts
bzw. die Kennzahlen des Modus. **Rematch** startet dasselbe Spiel neu, im Turnier führt „Zum Turnier“ zurück zum
Spielplan. **Wurfprotokoll und Teilen** öffnet das Match-Detail: jede Aufnahme Leg für Leg mit Rest, dazu ein
Teilen-Button, der das Ergebnis als Text an Messenger oder Zwischenablage gibt.

---

## Lens – Autoscoring mit der Handykamera

### Aufbau und Positionierung
| | Empfehlung |
|---|---|
| **Abstand** | ca. 1 m vom Board |
| **Winkel** | **schräg davor**, etwa 35–55° zur Boardfläche – nicht frontal, nicht von der Seite |
| **Bild** | ganzes Board **mit Zahlenring** im Bild |
| **Licht** | gleichmäßig, keine wandernden Schatten, kein Gegenlicht |
| **Halterung** | Stativ oder feste Halterung – das Handy darf während des Spiels nicht wackeln |

Die App gibt beim Einrichten Hinweise wie „Ganzes Board im Bild“, „Abstand passt (≈ 1 m)“, „Licht passt“ und zeigt den
Blickwinkel an. Der Winkel ist nur eine Empfehlung und blockiert nie: zu flach leidet die Genauigkeit, zu frontal
verdeckt ein Dart öfter seine eigene Spitze.

### Kalibrierung
1. *Lens*-Tab → **Kamera starten**, Board in den Kreis bringen.
2. Die App sucht das Board im ganzen Bild per KI – gesucht werden die Kalibrierpunkte am äußeren Doppelring
   (20/1, 6/10, 3/19, 11/14). Das dauert meist 1–2 Sekunden.
3. Die Punkte werden über Ring-Kanten und Draht-Übergänge verfeinert und über mehrere Durchläufe gemittelt (gegen
   Zittern).
4. Die Status-Pill wird grün („Kalibriert“ → „Ready to play“), das Handy vibriert. Fokus, Belichtung und Weißabgleich
   werden auf das Board festgelegt.

**Falls die Suche danebenliegt:**
- **Liegt das Gitter falsch herum:** im Bild auf die **20** tippen.
- **Punkte manuell ziehen** (unter *Erweitert*): die vier Punkte auf die Außenkante des Doppelrings an den Drähten
  20/1, 6/10, 3/19 und 11/14 ziehen.
- **Neu kalibrieren** sucht das Board frisch – das passiert bei jedem Kamerastart ohnehin automatisch.

Einmal kalibriert, startet Lens beim Match automatisch („Lens beim Match automatisch starten“).

### So erkennt Lens die Darts
Die Erkennung kombiniert zwei Stufen:

1. **Klassische Bildverarbeitung** (immer offline, ohne KI): Das Kamerabild wird auf ein 360×480-Graubild verkleinert
   und mit einem Referenzbild verglichen. Sobald sich eine Veränderung beruhigt hat, wird der Blob gesucht, über die
   Hauptachse die Spitze bestimmt (das schmale Ende) und per Homographie auf das Board abgebildet. Große Veränderungen
   gelten als Hand bzw. Takeout.
2. **KI** (YOLOv8n, TensorFlow Lite, GPU-Delegate wenn verfügbar): Der Board-Ausschnitt wird in voller
   Kameraauflösung **in die Frontalansicht entzerrt** (Board als Kreis, 20 oben) – so, wie das Modell trainiert wurde,
   egal wie schräg die Kamera steht. Das Modell findet Kalibrierpunkte und Dartspitzen.

Ein neuer Dart zählt erst, wenn seine Spitze **über mehrere Auswertungen an derselben Stelle** liegt (Median der
Messungen): 3 Messungen im Normalfall, 4 wenn nur die KI ihn gefunden hat, 5 wenn er näher als 1,5 mm an einem Draht
steckt. Bereits gezählte Darts werden verfolgt, sodass auch ein Dart direkt neben einem anderen (Gruppierung) als neu
erkannt wird. Landet ein weiterer Dart, während der vorige noch geprüft wird, wird der vorige mit dem bisherigen Stand
abgeschlossen – **es geht kein Dart verloren**. Ein Dart, der kurz da war und wieder weg ist, zählt als **Bouncer**
(0 Punkte).

**Kamera bewegt?** Die App erkennt das an verschobenen Kalibrierpunkten, kalibriert neu und ordnet die Darts auf dem
Board neu zu. Verdeckt ein Dart einen anderen: vorderen ziehen oder Handy leicht drehen – der fehlende Dart wird
nachgetragen.

### Erweiterte Lens-Einstellungen
| Einstellung | Wirkung |
|---|---|
| **Empfindlichkeit automatisch** | Schwellwerte passen sich dem Bildrauschen an; sonst Regler 0–100 (höher = kleinere Änderungen erkennen) |
| **Belichtung** | Belichtungskorrektur, falls das Board zu hell oder zu dunkel ist |
| **Frontkamera** | Frontkamera statt Hauptkamera |
| **Klassische Erkennung** | Nur Differenzbild, ohne KI |
| **Zuschnitt** | Kamerabild auf den Board-Ausschnitt umschalten (Symbol im Kamerabild) |
| **Referenzbild neu aufnehmen** | aktuelles Bild als leeres Board übernehmen |
| **Punkte manuell ziehen** | Kalibrierpunkte von Hand setzen |
| **Zurücksetzen** | Kalibrierung verwerfen |
| **Trainingsdaten sammeln** | speichert Bilder und Labels für das Feintuning des Modells (siehe [KI-Modell](#ki-modell-und-feintuning)) |

---

## Geräte: Board Manager und Remote Scoring

Erreichbar über *Einstellungen › Geräte verwalten* („Devices“). Wie bei Autodarts bleibt ein Gerät im
**Detection Mode** am Board, das Spiel läuft auf demselben oder einem zweiten Gerät.

### Lens (Kamera dieses Handys)
Status und Einstieg in den Detection Mode („Start Lens Detection Mode“).

### Board Manager
Verbindet Scorelens mit einem **Autodarts Board Manager** im WLAN (Standard-Port **3180**). Die Würfe kommen dann von
der vorhandenen Autodarts-Hardware; der Wechsel von *Throw* auf *Takeout* beendet die Aufnahme. Start, Stop, Reset und
Kalibrieren lassen sich aus der App steuern. Eintragen: IP-Adresse des Board Managers und Port.

### Remote Scoring (Browser)
Das Handy bleibt als Kamera am Board, die **Spielansicht läuft im Browser** eines Tablets, PCs oder Fernsehers im
selben WLAN:

- Remote Scoring einschalten, dann die angezeigte Adresse `http://<Handy-IP>:8765` im Browser öffnen
  (am besten die URL aus der App kopieren statt abtippen).
- Spielerkarten wie in der App, **Live-Bild der Lens** daneben.
- **Undo** und **Next** auch im Browser.
- Tastenkürzel: **U** (Undo), **Leertaste** (Next), **F** (Vollbild).
- **Streamen:** `http://<Handy-IP>:8765/overlay` als Browserquelle in OBS einfügen (Hintergrund ist transparent) – Spieler,
  Score, Legs/Sets, laufende Aufnahme und Banner liegen live über deinem Kamerabild. Für Twitch/YouTube reicht das,
  ein eigener Dienst ist nicht nötig.

### Zweitgerät koppeln
Ein zweites Handy kann das Spiel des Board-Handys anzeigen und bedienen:

1. Am Board-Handy Remote Scoring einschalten – die Remote-Karte zeigt einen QR-Code.
2. Am Zweitgerät: *Devices › Remote Scoring › „Als Zweitgerät koppeln“* → **QR-Code scannen** (oder IP-Adresse eintippen).
3. Optional **„Volle App: Würfe vom Board-Handy übernehmen“** – dann läuft das komplette Spiel inklusive Statistik auf
   dem Zweitgerät, das Board-Handy liefert nur die Würfe (wie ein Board Manager, mit Auftreffpunkten in Millimetern).

### Zweite Kamera
Zwei Handys sehen mehr als eines: Verdeckt ein Dart einen anderen, sieht ihn die zweite Kamera aus ihrem Winkel,
und knapp am Draht zählt der Mittelwert beider Blickwinkel. Außerdem sammeln sich die nötigen Messungen doppelt
so schnell, ein Dart wird also schneller bestätigt.

| Handy | Schritte |
|---|---|
| Kamera 2 | Lens starten (Detection Mode bleibt offen), *Devices › Remote Scoring* einschalten. Kein Spiel nötig. |
| Haupt-Handy | Lens starten, *Devices › „Als Zweitgerät koppeln“* → QR-Code der Kamera 2 scannen → **„Würfe übernehmen“**. Das Spiel läuft hier. |

Die Lens-Diagnosezeile des Haupt-Handys zeigt dann `2. Kamera`. Technisch fließen die rohen KI-Spitzen der zweiten
Kamera (in Board-Millimetern) als zusätzliche Messungen in die Bestätigung des Haupt-Handys ein; gezählt und
„Darts entnommen“ entscheidet nur das Haupt-Handy, es zählt also nichts doppelt. Aufstellung: die zweite Kamera
30–45° versetzt zur ersten, beide ruhig und sauber kalibriert. Fällt Kamera 2 aus, läuft das Haupt-Handy wie gewohnt weiter.

### HTTP-Schnittstelle des Board-Handys
Für Bastler – der eingebaute Server (ohne Abhängigkeiten) beantwortet:

| Pfad | Inhalt |
|---|---|
| `/` | Spielansicht für den Browser |
| `/state` | Spielstand als JSON |
| `/board.jpg` | aktuelles Lens-Bild als JPEG |
| `/overlay` | Scoreboard-Streifen mit transparentem Hintergrund für OBS (Browserquelle) oder den Fernseher; `?pos=top` für oben |
| `/api/state` | Board-Manager-Sicht der Lens (Throw / Takeout / Stopped, Segmente, `coords` mit `unit=mm`, dazu `tips`/`tipSeq`: rohe KI-Spitzen in mm für eine zweite Kamera) |
| `/api/<befehl>` | Board-Befehle wie beim Autodarts Board Manager |
| `/cmd?do=…` | Befehle der Spielansicht (Undo, Next) |

---

## Bots

Bots simulieren echte Würfe: Sie wählen ein Ziel (z. B. T20, das beste Checkout-Double oder die Zahl, die sie in Cricket
schließen müssen) und streuen normalverteilt um diesen Punkt. Das getroffene Segment ergibt sich aus der Board-Geometrie
– deshalb gibt es auch „Nachbar-Treffer“ wie 1 und 5 statt 20.

| Stufe | Streuung (σ) | ungefährer 3-Dart-Average |
|---|---|---|
| 1 | 62 mm | 25 |
| 2 | 50 mm | 32 |
| 3 | 42 mm | 40 |
| 4 | 36 mm | 47 |
| 5 | 30 mm | 55 |
| 6 | 26 mm | 62 |
| 7 | 22 mm | 70 |
| 8 | 18,5 mm | 78 |
| 9 | 15,5 mm | 86 |
| 10 | 12,5 mm | 95 |
| 11 | 9,5 mm | 105+ |

- **Bot-Wurfpause** (0,2–2 s) unter *Einstellungen › Match › Erweitert*.
- **Gegner finden** ohne Online-Konto wählt die Bot-Stufe, deren Average Deinem X01-Average am nächsten liegt.
- Bots bekommen eigene Statistiken (je Stufe).
- **Bot „Wie ich“:** startet mit der Streuung, die zu Deinem X01-Average passt, und passt sich nach jedem Leg an –
  gewinnt er, wird er 6 % genauer, verlierst Du nicht, wird er 6 % ungenauer. Der Stand bleibt über Spiele hinweg erhalten,
  so hast Du immer einen Gegner auf Augenhöhe, der mit Dir wächst.

---

## Checkout-Guide

Der Checkout-Guide zeigt für den aktuellen Rest und die verbleibenden Darts der Aufnahme den besten Weg, z. B.
`T20  T19  D12`. Die Suche:

- nimmt immer den **kürzesten** Weg (so wenige Darts wie möglich),
- bevorzugt beliebte Finish-Doubles in der Reihenfolge **D20, D16, D8, D4, D2, D1, D18, D12, D10 …**,
- bevorzugt hohe Triples als Setup und vermeidet Bull als Setup-Dart,
- beachtet den Out-Modus (Double Out und Master Out bis 170, Straight Out bis 180).

Die Bots nutzen dieselben Wege. Auch Random Checkout und 121 zeigen den Guide.

**Checkout-Tabelle:** Unter *Einstellungen › Match* und im Zahnrad des Matches gibt es die komplette Tabelle 2 bis 170
(bzw. 180 bei Straight Out) mit demselben Weg, den der Guide vorschlägt – Reste ohne 3-Dart-Finish sind grau.

---

## Statistiken

Der *Statistik*-Tab („Statistics“) zeigt pro Spieler:

### Filter
- **Spieler** – oben per Avatar wählen
- **Zeitraum** – Heute, 7 Tage, 30 Tage, Gesamt
- **Modus** – alle Modi oder einer

### Übersicht aller Modi
Aktivität der letzten 28 Tage und je Modus die Kennzahl; ein Tipp auf eine Zeile filtert auf den Modus.

### Kennzahl je Modus
| Modus | Kennzahl |
|---|---|
| X01, Gotcha | 3-Dart-Average |
| Cricket | MPR (Marks per Round) |
| Around the Clock, Segment Training | Trefferquote |
| Count Up, Shanghai, Bermuda, Bob's 27 | Punkte |
| Round the World | Treffer |
| Random Checkout | Legs ausgecheckt |
| 121 | höchstes erreichtes Ziel |
| Killer | Treffer auf Gegner |

Die große Kennzahl vergleicht die **letzten 10 Spiele mit den 10 davor** und zeigt den Verlauf der letzten 20 Spiele
(gestrichelt = Durchschnitt, grün = Bestwert). Averages und Quoten sind dartgewichtet.

### Details
- **X01:** Matches, Siegquote, Legs gewonnen/verloren, First-9-Average, Checkout-Quote, Checkouts, höchstes Finish,
  höchster Score, bestes/schlechtestes Leg, Busts, Darts geworfen
- **Aufnahmen:** Verteilung auf 0–39, 40–59, 60–99, 100–139, 140–179 und 180, höchste Aufnahme, Anteil über 60,
  typischer Bereich
- **Checkout-Doubles:** mit welchen Doubles Du Legs beendest
- **Cricket:** Marks gesamt/pro Spiel, beste MPR, Darts je Zahl („Meiste Darts auf …“)
- **Trefferbild:** farbige Dartscheibe (Rot = oft, Blau = selten); bei Lens und Board Manager auch die echten
  Auftreffpunkte
- **Head-to-Head:** Bilanz gegen jeden Gegner aus Zwei-Spieler-Matches – Siege, Legs, Averages, Checkout-Quote, 180er,
  höchstes Finish
- **Serien:** Siege bzw. Niederlagen in Folge, längste Siegesserie
- **Spielzeit** und Anzahl Spiele
- **Verlauf:** jedes Match; ein Tipp öffnet das **Match-Detail** mit Statistik-Tabelle und dem kompletten Wurfprotokoll
  (jede Aufnahme mit Darts, Summe und Rest, Leg für Leg) sowie Teilen-Button
- **Erfolge:** 16 Badges aus dem Verlauf (erstes 180, Ton-Finish, 170er, 15-Darter, 9-Darter, Siegesserie, Ø 50/70,
  Allrounder …) mit Fortschritt – auf der Player Card jedes Spielers

### Verlauf löschen und exportieren
- **Verlauf löschen** entfernt alle Matches (angemeldet auch die Cloud-Sicherung).
- **Spieler und Verlauf exportieren (JSON)** unter *Einstellungen › Profil* – ohne Einstellungen, weil diese Schlüssel
  enthalten können.

---

## Turniere

In der Lobby **„Als Turnier starten“** wählen. Spieler und Einstellungen der Lobby gelten für alle Spiele; jedes Spiel
ist ein normales Match und zählt in der Statistik.

| Modus | Ablauf |
|---|---|
| **K.-o.-System** | Turnierbaum mit Freilosen auf die nächste Zweierpotenz; Sieger rücken automatisch weiter (Achtelfinale, Viertelfinale, Halbfinale, Finale) |
| **Jeder gegen jeden** | Rundenverfahren, bei ungerader Spielerzahl je Runde ein Freilos; **Tabelle** nach Siegen, dann Leg-Differenz |

- Auf dem Turnier-Screen bei einem offenen Spiel **„Spielen“** tippen.
- Solange ein Turnier läuft, erscheint es als Karte auf der Startseite.
- **Turnier beenden** löscht den Spielplan; gespielte Matches bleiben in der Statistik.
- **Online-Turnier:** In einer Online-Lobby startet der Host das Turnier; jedes Spiel ist ein Online-Match der beiden
  Beteiligten, die anderen schauen zu. Der Spielplan aktualisiert sich live bei allen.

---

## Online-Modus

Der Online-Modus ist **optional**. Er braucht einen Supabase-Server (siehe
[Online-Server einrichten](#online-server-einrichten)) und ein Konto.

### Anmelden
- **Google** oder **GitHub** mit einem Tipp (Supabase OAuth mit PKCE, Rückkehr in die App über
  `scorelens://auth/callback`).
- Das Online-Profil hat Anzeigename, Farbe und Profilbild; die Online-Kennzahlen (Average, Matches, Siege) kommen aus
  abgeschlossenen Online-Matches.

### Lobbys
- **Lobby erstellen:** Modus und Einstellungen wie lokal, öffentlich (in der Liste sichtbar) oder privat, maximale
  Spielerzahl.
- **Mit Code beitreten:** 6-stelliger Code, teilen oder kopieren. Mit dem Code kann man jederzeit wieder beitreten.
- **Öffentliche Lobbys:** Liste offener Lobbys, aktualisiert sich automatisch.
- Der **Host** legt Modus und Einstellungen fest, kann Spieler entfernen und startet das Match, sobald alle
  **Bereit** sind. Verlässt der Host die Lobby, wird ein anderer Spieler Host; ist niemand mehr da, wird sie geschlossen.

### Gegner finden
Sucht eine passende offene Lobby nach Spielstärke (Online-Average) und tritt bei – gibt es keine, wird eine öffentliche
Lobby eröffnet, der andere beitreten können.

### Live-Matches
- **Jeder wirft an seinem eigenen Board** – mit Lens, Board Manager oder manuell. Die Scores laufen live auf allen
  Geräten.
- **Live-Ticker** mit den letzten Darts aller Spieler, inklusive Referee-Bild, wo vorhanden.
- **Anwesenheit:** Die App zeigt, wenn ein Gegner offline ist.
- **Undo** ist nur für das eigene letzte Ereignis erlaubt.
- **Netz kurz weg?** Die Verbindung baut sich selbst wieder auf, verpasste Würfe werden nachgeladen.

### Zuschauen und Rangliste
- Laufende öffentliche Matches anderer lassen sich **live mitverfolgen** (nur lesend).
- **Rangliste** nach Online-Average, mit Matches und Win Rate.

### Freunde und Einladungen
- **Mein QR-Code** zeigen oder **Freundes-Link teilen** – der Link öffnet die App direkt.
- Freunde per **QR-Code scannen** oder **per Name suchen**.
- Anfragen annehmen, ablehnen, zurückziehen; Freunde entfernen.
- Die Freundesliste zeigt, wer gerade online ist, die gemeinsame Bilanz und offene Lobbys.
- **Spielt gerade:** Läuft bei einem Freund ein Online-Match, steht in seiner Zeile „Spielt gerade · Zuschauen“ – ein Tipp, und du siehst live zu.
- **Einladen:** Freund in die eigene offene Lobby einladen (gibt es keine, wird eine private Lobby angelegt). Die
  Einladung kommt sofort als Karte – und bei geschlossener App als **Push-Mitteilung**.

### Cloud-Sicherung
Angemeldet werden **Match-Verlauf, Spielerliste und Einstellungen** mit dem Konto abgeglichen. Auf einem neuen Handy
reicht die Anmeldung, und Deine Statistik ist wieder da.

---

## Online-Server einrichten

Die App braucht nur zwei Werte: **Supabase-URL** und **Anon-Key** (bzw. Publishable Key). Eintragen unter
*Startseite › Online spielen › Zahnrad* oder *Einstellungen › Online-Konto*. Die App unterscheidet nicht zwischen
supabase.com und eigenem Server.

### Option A: supabase.com (kostenlos, ca. 10 Minuten)
1. Auf [supabase.com](https://supabase.com) ein Projekt anlegen (Free Tier reicht).
2. **Schema einspielen** – alle Dateien aus `supabase/migrations/` der Reihe nach im SQL-Editor ausführen, oder per CLI:
   ```bash
   supabase login
   supabase link --project-ref <ref>
   supabase db push
   ```
3. *Authentication › URL Configuration*: `scorelens://auth/callback` unter **Redirect URLs** eintragen.
4. *Authentication › Providers*: E-Mail aktiv lassen, optional Google / GitHub / Discord. Beim Anbieter als
   Redirect-URL `https://<projekt>.supabase.co/auth/v1/callback` hinterlegen.
5. *Project Settings › API*: **Project URL** und **anon public** Key in die App eintragen.

Details: [`supabase/README.md`](supabase/README.md)

### Option B: Selbst hosten (Docker)
Schlanker Stack aus den offiziellen Supabase-Images: Postgres, Auth (GoTrue), REST (PostgREST), Realtime und Caddy als
Gateway. Ein Raspberry Pi 4 oder kleiner VPS reicht (ca. 1,5 GB RAM).

```bash
cd selfhost
./setup.sh http://192.168.178.50:8000     # nur im WLAN
# oder: ./setup.sh https://darts.example.com   (Domain → automatisches HTTPS über Caddy)
docker compose up -d
```

`setup.sh` gibt Supabase-URL und Anon-Key aus – genau diese beiden Werte in der App eintragen.

| Aufgabe | Befehl |
|---|---|
| Schema nach App-Update aktualisieren | `./migrate.sh` |
| Datenbank-Oberfläche (Studio) | `docker compose --profile studio up -d` → http://127.0.0.1:3000 |
| Logs | `docker compose logs -f auth rest realtime gateway` |
| Backup | `docker compose exec db pg_dump -U postgres postgres > backup.sql` |
| Push-Relay mitstarten | `docker compose --profile push up -d --build` |
| Registrierung abschalten | `DISABLE_SIGNUP=true` in `.env` |
| Alles zurücksetzen (löscht die Datenbank!) | `docker compose down -v` und `.env` entfernen |

Details zu Erreichbarkeit, Reverse-Proxy, OAuth und Sicherheit: [`selfhost/README.md`](selfhost/README.md)

### Push-Mitteilungen (optional)
Damit Einladungen und Freundschaftsanfragen auch bei geschlossener App ankommen:
- **supabase.com:** Edge Function `supabase/functions/push` deployen und zwei Database-Webhooks anlegen
  (`invites` und `friendships`, Event `INSERT`).
- **Selfhost:** Node-Relay aus `push/` (beobachtet die Tabellen per Realtime und sendet über Firebase Cloud Messaging).

Beide brauchen einmalig einen Firebase-Service-Account. Details: [`push/README.md`](push/README.md)

### Server fest in die APK einbauen
Wer eine APK mit voreingestelltem Server bauen will, setzt in `gradle.properties` oder `~/.gradle/gradle.properties`:

```properties
scorelens.supabaseUrl=https://<projekt>.supabase.co
scorelens.supabaseAnonKey=<anon key>
```

---

## Einstellungen erklärt

### 👤 Profil
| Einstellung | Wirkung |
|---|---|
| Profil | Wessen Statistiken auf der Startseite stehen |
| Spieler verwalten | Spieler anlegen, bearbeiten (Name, Farbe, Profilbild), löschen |
| Spieler und Verlauf exportieren (JSON) | Datensicherung als Datei |

### 🌐 Online-Konto
Anmelden/Abmelden, Server eintragen.

### 🔊 Caller & Sound
| Einstellung | Wirkung |
|---|---|
| Caller (Sprachansage) | Scores, Restpunkte („du brauchst 40“ nur bei möglichem Checkout), Bust und Game Shot ansagen |
| Aufnahmen ansagen | erst ab einem Mindestscore ansagen (0 = alle, auch „Keine Punkte“) |
| Sprache und Stimme | Deutsch oder English (PDC-Stil), Offline-Stimme der Android-Sprachausgabe wählen, Hörprobe |
| Jeden Dart ansagen | zusätzlich jeden einzelnen Dart ansagen |
| Soundeffekte | Töne für Darts und Busts, Clips für 180 und 0 |

### 🎮 Match
| Einstellung | Wirkung |
|---|---|
| Checkout-Guide | Finish-Vorschlag einblenden |
| Automatisch nächster Spieler | nach 1–20 s ohne Dart zum nächsten Spieler (Aus = nur manuell/Takeout) |
| *Erweitert:* Chalkboard anzeigen | Kreidetafel mit dem Aufnahme-Verlauf |
| *Erweitert:* Bildschirm anlassen | Display bleibt während des Spiels an |
| *Erweitert:* Darts Zoom im Kamerabild | aktuelle Aufnahme groß über dem Kamerabild |
| *Erweitert:* Animationen und Match-Intro | Intro, Gewinn-, Bust- und Caller-Animationen |
| *Erweitert:* Bot-Wurfpause | Pause zwischen Bot-Würfen (0,2–2 s) |

### 🎥 Geräte
| Einstellung | Wirkung |
|---|---|
| Lens beim Match automatisch starten | Kamera startet im Match von selbst (erst nach einer Kalibrierung verfügbar) |
| Geräte verwalten | Lens, Board Manager, Remote Scoring, Zweitgerät |

### ℹ️ Scorelens
| Einstellung | Wirkung |
|---|---|
| Nutzungsdaten und Absturzberichte senden | Firebase Analytics und Crashlytics an/aus |
| Umstieg von Autodarts | Gegenüberstellung: wo finde ich was |

---

## Datenschutz

| Daten | Wo sie liegen |
|---|---|
| Spieler, Matches, Einstellungen, Turnier | lokal als JSON-Dateien im App-Speicher |
| Kamerabilder | nur im Arbeitsspeicher; die KI rechnet auf dem Gerät. Ausnahme: „Trainingsdaten sammeln“ speichert Bilder lokal im App-Ordner |
| Referee-Bilder im Online-Match | als kleines JPEG per Broadcast an die Mitspieler, **nicht** in der Datenbank |
| Online-Profil, Lobbys, Matches, Freunde | auf dem gewählten Supabase-Server, geschützt durch Row Level Security |
| Cloud-Sicherung (Verlauf, Spieler, Einstellungen) | nur wenn angemeldet, auf dem gewählten Server |
| Nutzungsdaten | anonym an Google Firebase: genutzte Bildschirme und Modi, Match-Start/-Ende, Lens-Kalibrierung, Online- und Turnier-Aktionen, Abstürze. **Keine Namen, Scores oder Kamerabilder.** Abschaltbar. |

---

## Fehlersuche

### Kamera
**„Kamera-Fehler“ oder schwarzes Bild**
- Kamera-Berechtigung erlauben (Android-Einstellungen › Apps › Scorelens › Berechtigungen)
- Andere Apps schließen, die die Kamera nutzen
- App neu starten

### Lens findet das Board nicht
- Ganzes Board **mit Zahlenring** ins Bild, nicht zu weit weg
- Licht prüfen – die App warnt bei zu dunklem oder zu hellem Bild; ggf. Belichtung unter *Erweitert* anpassen
- Winkel prüfen: nicht exakt frontal, nicht extrem seitlich
- Gitter falsch herum → im Bild auf die 20 tippen
- Notfalls **Punkte manuell ziehen**

### Lens erkennt Darts falsch oder gar nicht
- **Beleuchtung:** gleichmäßig, keine wandernden Schatten (z. B. von Personen oder Deckenventilator)
- **Kalibrierung erneuern:** Lens-Screen → „Neu kalibrieren“
- **Handy wackelt:** feste Halterung verwenden
- **Referenzbild neu aufnehmen**, wenn sich das Licht geändert hat
- **Empfindlichkeit:** automatisch lassen oder leicht anpassen
- **Verdeckte Darts:** vorderen Dart ziehen oder Handy leicht drehen
- Strittigen Dart über das **Referee-Bild** prüfen und per Dart-Pill korrigieren

### Remote Scoring: Seite lädt nicht
- Handy und Zweitgerät im **gleichen WLAN**? (Gäste-WLANs isolieren Geräte oft voneinander)
- Remote Scoring am Board-Handy eingeschaltet?
- URL aus der App kopieren statt abtippen, Port **8765**
- Firewall bzw. Router-Einstellung „Client-Isolation“ prüfen

### Board Manager verbindet nicht
- IP-Adresse und Port (**3180**) prüfen
- Board Manager im Browser unter `http://<ip>:3180` erreichbar?
- Handy und Board Manager im gleichen Netz?

### Online
**„Kein Server eingetragen“** – Supabase-URL und Anon-Key unter *Online spielen › Zahnrad* eintragen.

**Anmeldung mit Google/GitHub kehrt nicht in die App zurück** – `scorelens://auth/callback` muss in Supabase unter
*Redirect URLs* stehen (bei selfhost bereits eingetragen).

**Lobby oder Match hängt** – „Neu verbinden“ tippen. Die App lädt das Ereignisprotokoll neu und baut den Spielstand
wieder auf.

**Selfhost nicht erreichbar** – `docker compose logs -f auth rest realtime gateway`; bei eigenem Reverse-Proxy
WebSockets für `/realtime/v1/` durchreichen.

### Keine Sprachansage
- Caller eingeschaltet?
- In den Android-Einstellungen eine Sprachausgabe mit **offline** installierter deutscher bzw. englischer Stimme
  einrichten
- Medienlautstärke prüfen

### Build schlägt fehl
- **JDK 17** verwenden (`java -version`)
- `local.properties` mit `sdk.dir` vorhanden?
- Alternativ `scripts/docker-build.sh` nutzen

---

## Häufige Fragen

**Brauche ich ein Konto?**
Nein. Lokal läuft alles ohne Konto. Nur der Online-Modus braucht eins.

**Brauche ich Internet?**
Nein. Lens, alle Spielmodi, Bots, Turniere und Statistik funktionieren offline. Board Manager und Remote Scoring
brauchen nur das lokale WLAN.

**Welches Handy brauche ich?**
Android 8.0 oder neuer. Für Lens ist eine ordentliche Hauptkamera hilfreich; die KI nutzt die GPU, wenn das Gerät sie
unterstützt, und fällt sonst auf die CPU zurück.

**Kann ich mein Autodarts-Board weiterverwenden?**
Ja, über *Geräte › Board Manager*.

**Wie genau ist Lens?**
Das hängt stark von Licht, Winkel und Halterung ruhig ab. Jeder Dart wird mehrfach gemessen, nahe am Draht noch öfter.
Im Zweifel hilft das Referee-Bild und eine Korrektur per Tipp.

**Kann ich mit zwei Personen an einem Board spielen, und beide bekommen die Statistik?**
Ja. Der Mitspieler zeigt seinen QR-Code (*Freunde › Mein QR-Code*), Du scannst ihn in der Lobby – das Match landet auch
in seinem Verlauf.

**Gehen meine Daten beim Handywechsel verloren?**
Mit Konto nicht: Verlauf, Spieler und Einstellungen werden gesichert. Ohne Konto hilft der JSON-Export.

**Darf ich Scorelens kommerziell nutzen?**
Nein – das KI-Modell steht unter CC BY-NC 4.0 (nur nicht-kommerziell), siehe
[Lizenzen](#lizenzen-und-danksagungen).

---

## Für Entwickler

### Technik
| Bereich | Technik |
|---|---|
| Sprache | Kotlin, JVM-Target 17 |
| UI | Jetpack Compose, Material 3 |
| Kamera | CameraX 1.4 |
| KI | TensorFlow Lite 2.16 mit GPU-Delegate, YOLOv8n (800 px) |
| Netzwerk | OkHttp (REST, WebSocket), eigener Minimal-HTTP-Server |
| Serialisierung | kotlinx.serialization |
| Backend (optional) | Supabase: Auth/GoTrue, PostgREST, Realtime – **ohne SDK**, eigener schlanker Client |
| Firebase (optional) | Crashlytics, Analytics, Cloud Messaging – nur aktiv, wenn `app/google-services.json` existiert |
| QR-Codes | ZXing |
| Android | minSdk 26, targetSdk/compileSdk 36 |

### Projektstruktur
```
app/src/main/java/com/freedarts/scorer/
  MainActivity.kt             Einstieg, Deep Links (scorelens://auth, scorelens://friend)
  audio/                      Caller: Sprachansage (TTS) und Soundeffekte
  board/                      Client für den Autodarts Board Manager
  data/                       Repository: lokale Persistenz als JSON-Dateien
  engine/                     Spiellogik ohne Android-Abhängigkeiten
    DartGame.kt               Basis: Aufnahmen, Undo, Bull-off, Statistik
    GameFactory.kt            Modus → Spielklasse
    Board.kt                  Board-Geometrie (Segment aus mm-Koordinaten)
    Bot.kt                    Bot-Würfe mit Streuung
    Checkout.kt               Checkout-Guide
    Statistics.kt             Auswertungen über mehrere Spiele
    games/                    X01, Cricket, Übungs- und Party-Modi
  lens/                       Kamera-Erkennung
    LensController.kt         Kamera, Ablauf, Threads
    BoardFinder.kt            Board-Suche (Ringe, Ellipse, Sektormuster)
    Homography.kt             Kamerabild ↔ Board-Ebene (DLT)
    CalibrationTracker.kt     zeitlicher Median, Kamerabewegung
    DartDetector.kt           klassische Differenzbild-Erkennung
    BoardRectifier.kt         Entzerrung in die Frontalansicht für die KI
    YoloDartModel.kt          TFLite-Inferenz und Decoding
    TipTracker.kt             KI-Spitzen verfolgen und bestätigen
    TrainingCapture.kt        Trainingsdaten fürs Feintuning
  model/                      Datenklassen: Einstellungen, Spieler, Matches, Turnier
  online/                     Supabase-Client, Realtime, Online-Controller
  remote/                     HTTP-Server für Remote Scoring
  ui/                         AppViewModel, Screens, Komponenten, Theme
app/src/main/assets/          TFLite-Modell
app/src/main/res/raw/         Soundclips
app/src/test/                 Unit-Tests (Engine, Lens, Statistik, Turnier, Online)
supabase/                     Migrationen, Edge Functions (friend, push), config.toml
selfhost/                     Docker-Stack zum Selbsthosten
push/                         Push-Relay (Node) für Selfhost
tools/export_model.sh         Modell nach TFLite exportieren und prüfen
tools/finetune/               Datensatz bauen, Feintuning (HF, Kaggle, Colab, Modal), Benchmarks
design/                       Design-Entwürfe der Screens
scripts/docker-build.sh       Build komplett in Docker
codemagic.yaml                CI: Check und Beta-Release
```

### Architektur in Kürze
- **Engine:** Jeder Modus ist eine `DartGame`-Unterklasse. Die Engine bekommt Würfe (`Segment`) und liefert einen
  unveränderlichen `GameState` für die UI. Sie ist **deterministisch** (Seed), rein Kotlin und vollständig unit-testbar.
- **UI:** Ein `AppViewModel` hält den App-Zustand als `StateFlow`s; die Compose-Screens rendern nur.
- **Persistenz:** `Repository` speichert `players.json`, `settings.json`, Matches und Turnier atomar (Schreiben über
  `.tmp`); unlesbare Einzeleinträge verwerfen nicht die ganze Datei.
- **Lens:** Kamera-Frames werden auf dem Analyse-Thread verarbeitet, die KI-Inferenz läuft auf einem eigenen Thread,
  damit die Bewegungslogik keine Frames verpasst.
- **Online-Sync:** Alle Geräte bauen aus **Seed, Einstellungen und Spielerliste** dieselbe Engine und spielen das
  Ereignisprotokoll (`match_events`: `throw` / `next` / `undo`) in `seq`-Reihenfolge ein. Eigene Ereignisse werden
  sofort lokal angewendet und dann eingefügt; das Echo über Realtime bestätigt sie. Bei Lücken oder Konflikten wird das
  Protokoll neu geladen und die Engine neu aufgebaut.

### Datenmodell (Supabase)
| Tabelle / RPC | Zweck |
|---|---|
| `profiles` | Anzeigename, Farbe, Avatar, Online-Kennzahlen |
| `lobbies`, `lobby_players` | Lobby mit Code, Host, Einstellungen, Sichtbarkeit, Status, Turnier |
| `matches` | Seed, Einstellungen, Spielerreihenfolge, Ergebnis |
| `match_events` | Ereignisprotokoll mit laufender Nummer je Match |
| `saved_matches`, `user_data` | Cloud-Sicherung von Verlauf, Spielern und Einstellungen |
| `friendships`, `invites` | Freunde und Lobby-Einladungen |
| `push_tokens` | FCM-Tokens für Push-Mitteilungen |
| `leaderboard` (View) | Rangliste |
| RPCs | `create_lobby`, `join_lobby`, `leave_lobby`, `quick_match`, `start_match`, `finish_match`, `abort_match`, `friends` … |

Alle Tabellen mit Row Level Security.

### Tests
```bash
./gradlew :app:testDebugUnitTest
```
Abgedeckt sind u. a. Spielregeln aller Modi, X01-Busts und Legs/Sets, Checkout, Statistik, Turnierbaum, Board-Geometrie,
Board-Suche, Homographie, Entzerrung, Differenzbild-Erkennung, TipTracker und Freundes-Links.

### Build-Konfiguration
| Datei | Zweck | Im Git |
|---|---|---|
| `local.properties` | Pfad zum Android SDK | nein |
| `keystore.properties` + `release.jks` | Release-Signierung (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); fehlt sie, wird debug-signiert | nein |
| `app/google-services.json` | aktiviert Crashlytics, Analytics und Push; ohne die Datei baut das Projekt trotzdem | nein |
| `gradle.properties` | optional voreingestellter Supabase-Server | ja |

Release-Builds sind mit R8 verkleinert (`isMinifyEnabled`, `isShrinkResources`). Das TFLite-Modell bleibt unkomprimiert
(Memory-Mapping).

### KI-Modell und Feintuning
Basis ist das YOLOv8n aus [dart-sense](https://github.com/bnww/dart-sense) (Ben Willshaw), das eigens feinabgestimmt
wurde. Das Modell erkennt Kalibrierpunkte am Doppelring und Dartspitzen als Objekte (Box-Mitte = Punkt, wie bei
DeepDarts), Eingabegröße 800 px.

Ablauf in Kurzform:
1. **Daten sammeln** – in der App „Trainingsdaten sammeln“ einschalten (max. 3000 Bilder); korrigierte Darts landen
   immer als `fix_*` mit bestätigtem Label dort. Abholen mit
   `adb pull /sdcard/Android/data/com.freedarts.scorer/files/training`; dazu eigene Fotos und öffentliche Datensätze
   (DeepDarts).
2. **Datensatz bauen und prüfen** – `tools/finetune/prelabel.py` (Vorschlags-Labels, Review-Bilder, `finalize`).
3. **Trainieren** – gratis auf Kaggle/Colab (T4) oder bezahlt über Hugging Face Jobs bzw. Modal; alle Wege nutzen
   dieselben Trainingsskripte mit Checkpoints zum Fortsetzen.
4. **Bewerten** – neben mAP zählt die **Spitzen-Genauigkeit** (`tip_recall_10px`, ≈ 2 mm auf dem Board).
5. **Exportieren** – `tools/export_model.sh` exportiert nach TFLite, prüft die Kotlin-Decode-Logik gegen ultralytics und
   kopiert das Modell nach `app/src/main/assets/`.

Ausführlich: [`tools/finetune/README.md`](tools/finetune/README.md)

### Beitragen
- Code-Kommentare und Texte sind auf Deutsch.
- Spiellogik gehört in `engine/` und bekommt einen Unit-Test.
- Neue Datenbank-Änderungen als neue Migration in `supabase/migrations/` (Selfhost übernimmt sie über `./migrate.sh`).

---

## Lizenzen und Danksagungen

| Bestandteil | Lizenz / Quelle |
|---|---|
| App-Code | eigener Code |
| KI-Modell | Feintuning auf Basis von **dart-sense** von Ben Willshaw (YOLOv8n), **CC BY-NC 4.0** – nur nicht-kommerzielle Nutzung |
| Trainingsdaten | u. a. DeepDarts (McNally et al., 2021) |
| Schriften | Barlow Condensed, DM Sans – SIL Open Font License 1.1 |
| Icons | Lucide – ISC License |
| Soundclips (180, 0) | von myinstants.com, **keine freie Lizenz** – nur für den privaten Gebrauch |

Scorelens ist ein privates, nicht-kommerzielles Projekt und nicht mit Autodarts verbunden. „Autodarts“ ist eine Marke
des jeweiligen Inhabers.

---

## Support

- **Probleme melden:** Issue auf GitHub öffnen – bei Lens-Problemen bitte Handymodell, Abstand, Winkel und Licht
  beschreiben und nach Möglichkeit einen Screenshot des Lens-Screens anhängen.
- **Beta-Tester:** Abstürze werden automatisch über Crashlytics gemeldet (sofern nicht abgeschaltet).
- **Feedback** ist jederzeit willkommen!

---

**Viel Spaß beim Spielen! 🎯**
