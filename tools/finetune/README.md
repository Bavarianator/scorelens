# Feintuning des Dart-Modells (Hugging Face Jobs)

Das KI-Modell der App ist das YOLOv8n aus [dart-sense](https://github.com/bnww/dart-sense) (7 Klassen:
Kalibrierpunkte 20, 3, 11, 6, 9, 15 und Dartspitze; 800 px; CC BY-NC 4.0). Es wurde auf ~24 000 Bildern trainiert,
überwiegend frontal bzw. leicht schräg. Die App entzerrt den Board-Ausschnitt deshalb vor der Inferenz in die
Frontalansicht (`BoardRectifier`); was dann noch fehlt, sind Bilder mit den eigenen Darts, Flights, Board und Licht –
genau die liefert die App selbst. Der Ablauf:

## 1. Daten sammeln

* **App:** Lens-Screen → „Trainingsdaten sammeln“ einschalten. Bei jedem erkannten Dart wird der Board-Ausschnitt,
  den das Modell gesehen hat, als JPEG plus YOLO-Label (Vorschlag des aktuellen Modells) abgelegt. Seit der
  Entzerrung ist das der **in die Frontalansicht gerechnete** Ausschnitt (Board als Kreis, 20 oben), auch wenn das Handy
  schräg steht – genau das Bild, das das Modell im Betrieb sieht. Abholen:
  `adb pull /sdcard/Android/data/com.freedarts.scorer/files/training ~/darts-training`
* **Eigene Fotos** (Handy-Kamera): Ordner mit JPG/PNG – möglichst frontal aufgenommen, damit sie zu den entzerrten
  App-Bildern passen; schräge Fotos bringen dem Modell für den Betrieb in der App nichts mehr.
* **Fertiger Datensatz vom Hub:** [vandana27/dart_tip_board](https://huggingface.co/datasets/vandana27/dart_tip_board)
  ist ein Roboflow-Export von DeepDarts D1 (CC BY 4.0): 4 574 Bilder 800×800 (je Foto drei augmentierte Versionen),
  YOLO-Labels mit 0 = Spitze, 1–4 = Kalibrierpunkte oben/unten/links/rechts. Frontalansicht, also kein Ersatz für
  eigene Seitenbilder, aber ein sauberer Grundstock und ein Test der Pipeline:
  ```bash
  hf download vandana27/dart_tip_board --type dataset --local-dir ~/freedarts-tools/datasets/dart_tip_board
  cd ~/freedarts-tools/datasets/dart_tip_board && unzip -q "DeepDarts YOLOv8.v1i.yolov8.zip" -d unz
  ```
  [bhabha-kapil/Dartboard-Detection-Dataset](https://huggingface.co/datasets/bhabha-kapil/Dartboard-Detection-Dataset)
  enthält alle 16 050 DeepDarts-Bilder (auch D2 mit Seitenansichten), aber ohne Labels.
* **Optional DeepDarts komplett** (McNally 2021, [IEEE DataPort](https://ieee-dataport.org/open-access/deepdarts-dataset),
  kostenloses Konto nötig): `cropped_images.zip` (800 px) und `labels.pkl`, enthält auch D2 (Seitenansichten).

## 2. Datensatz bauen (PC, Python mit ultralytics – z. B. `~/freedarts-tools/yolo-env/bin/python`)

```bash
PY=~/freedarts-tools/yolo-env/bin/python
$PY tools/finetune/prelabel.py add-app      --src ~/darts-training --dst ~/darts-ds --review
$PY tools/finetune/prelabel.py add          --src ~/fotos --dst ~/darts-ds --prefix cam --review
$PY tools/finetune/prelabel.py add-yolo     --src ~/freedarts-tools/datasets/dart_tip_board/unz --dst ~/darts-ds --limit 1000 --review
$PY tools/finetune/prelabel.py add-deepdarts --pkl labels.pkl --images cropped_images/800 --dst ~/darts-ds --limit 3000 --review
$PY tools/finetune/prelabel.py finalize     --dst ~/darts-ds
```

`~/darts-ds/review/` zeigt die Labels als Kreise (grün = Kalibrierpunkt, rot = Spitze). Falsche Labels mit
[labelImg](https://github.com/HumanSignal/labelImg) korrigieren (Klassen wie `data.yaml`, Box 0,025) oder das Bild
löschen. Ein paar hundert eigene, geprüfte Bilder reichen für einen spürbaren Effekt; die DeepDarts-Bilder verhindern,
dass das Modell die Frontalansicht verlernt.

## 3. Training – kostenlos oder bezahlt

Alle Wege nutzen dasselbe `train_hf.py`: Datensatz aus dem privaten HF-Dataset laden, Feintuning mit den
dart-sense-Hyperparametern, alle zwei Epochen `last.pt` ins Modell-Repo sichern (Resume nach Abbruch), am Ende
`best.pt`, `results.csv`, `metrics.json` hochladen.

### A. CPU-Space (seit 2026 nur noch mit PRO)

```bash
tools/finetune/finetune_hf.sh ~/darts-ds --space --epochs 30
hf spaces logs <user>/scorelens-finetune-ft1 --follow
tools/finetune/finetune_hf.sh --fetch            # wenn das Log "Hochgeladen nach …" zeigt
```

Der Space (CPU Basic, 2 vCPU, 16 GB) trainiert im Hintergrund einer kleinen Gradio-Seite, die das Log zeigt.
**Achtung:** Hugging Face lehnt das Anlegen eines Gradio-/Docker-Spaces auf einem Free-Konto inzwischen mit
„402 Payment Required – hosting Gradio and Docker Spaces on free cpu-basic requires a PRO subscription“ ab
(Stand 11.09.2026); nur statische Spaces ohne Rechenleistung sind gratis. Das Skript lädt Datensatz und Skript
trotzdem hoch, sodass Weg B oder C direkt weitergehen kann. Freie Spaces haben keinen dauerhaften Speicher; deshalb der
`last.pt`-Upload und das automatische Fortsetzen. Nach dem Training pausiert sich der Space selbst.

### B. Gratis-GPU mit Kaggle oder Colab (empfohlen, Minuten statt Stunden)

`tools/finetune/finetune_colab.ipynb` in [Kaggle](https://www.kaggle.com/code) (30 GPU-Stunden pro Woche, „Save & Run
All“ läuft bis 12 h ohne offenes Fenster) oder [Colab](https://colab.research.google.com) (T4, Sitzung muss offen
bleiben) hochladen, `HF_TOKEN` als Secret hinterlegen, Repo-Namen eintragen, ausführen. Der Datensatz muss vorher auf
dem Hub liegen – dafür reicht `finetune_hf.sh ~/darts-ds --space` (Upload passiert vor dem Space-Start; den Space
danach pausieren, wenn die GPU trainieren soll) oder von Hand:

```bash
hf repos create <user>/scorelens-darts-ft1 --type dataset --private
hf upload <user>/scorelens-darts-ft1 ~/darts-ds . --type dataset --include "images/*" --include "labels/*" --include "data.yaml"
hf upload <user>/scorelens-darts-ft1 tools/finetune/train_hf.py train_hf.py --type dataset
```

### C. Lokal (kostenlos, ohne PRO)

Gleiche Leistung wie ein CPU-Basic-Space. Datensatz muss auf dem Hub liegen (macht `--space` vor dem Fehler bereits):

```bash
mkdir -p ~/freedarts-tools/finetune/ft1 && cd ~/freedarts-tools/finetune/ft1
HF_TOKEN="$(hf auth token)" DATASET_REPO=<user>/scorelens-darts-ft1 OUTPUT_REPO=<user>/scorelens-dart-model-ft1 \
  EPOCHS=15 FREEZE=10 BATCH=8 WORK_DIR=$PWD/work \
  setsid nohup nice -n 15 ~/freedarts-tools/yolo-env/bin/python -u ~/autodart/tools/finetune/train_hf.py > train.log 2>&1 &
tail -f train.log
```

Checkpoints und Ergebnis landen wie sonst im Modell-Repo, `--fetch` funktioniert danach genauso; ein Abbruch wird beim
nächsten Start bei `last.pt` fortgesetzt.

### D. Bezahlte HF-Jobs (Guthaben nötig)

```bash
tools/finetune/finetune_hf.sh ~/darts-ds --flavor t4-small --timeout 2h --wait
```

| Variante | Preis | ~Dauer für 500 Bilder × 30 Epochen |
|---|---|---|
| Space `cpu-basic` | nur mit PRO (9 $/Monat) | 8–12 h |
| Lokal (2 Kerne) | kostenlos | 8–12 h |
| Kaggle / Colab T4 | kostenlos | 10–20 min |
| Job `cpu-basic` | 0,01 $/h | 8–12 h (≈ 0,10 $) |
| Job `t4-small` | 0,40 $/h | 10–20 min (≈ 0,10 $) |

`--freeze 10` friert den Backbone ein (schnell, wenig Vergessen); `--freeze 0` trainiert alles, sinnvoll ab
einigen tausend Bildern. Die Hyperparameter stammen aus dem YOLO-Tuning von dart-sense, nur die Lernrate ist für
Feintuning gesenkt und horizontales Spiegeln ist aus (würde 11 und 6 vertauschen).

## 4. Bewerten und in die App

`metrics.json` enthält neben mAP die **Spitzen-Genauigkeit**: Anteil der Dartspitzen im Validierungs-Set, die das
Modell innerhalb von 10 px (von 800, ≈ 2 mm auf dem Board) trifft (`tip_recall_10px`, `tip_precision_10px`).
Nur wenn diese Werte auf den eigenen Bildern besser sind als beim Basismodell, lohnt der Export:
`--fetch` ruft `tools/export_model.sh` mit `WEIGHTS=…/best.pt` auf, prüft die Kotlin-Decode-Logik gegen
ultralytics und kopiert das TFLite nach `app/src/main/assets/`. Danach App neu bauen.

Basisvergleich: `tools/finetune/finetune_hf.sh ~/darts-ds --epochs 0` ist nicht möglich; stattdessen dieselben
Metriken für `weights.pt` lokal berechnen, indem `train_hf.py` mit `EPOCHS=1 FREEZE=22` gestartet wird
(praktisch keine Änderung der Gewichte).
