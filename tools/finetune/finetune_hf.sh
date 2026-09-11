#!/usr/bin/env bash
# Feintuning des Dart-Modells auf Hugging Face Jobs.
#
#   tools/finetune/finetune_hf.sh DATASET_DIR [Optionen]
#     --flavor cpu-basic|cpu-upgrade|t4-small|…   (Standard: cpu-basic, $0.01/h; t4-small ≈ 30× schneller für $0.40/h)
#     --epochs N (30)  --freeze N (10 = Backbone eingefroren, 0 = alles trainieren)  --batch N (8)
#     --timeout 12h    --name ft1   --no-resume   --wait (bis zum Ende warten, best.pt holen und TFLite exportieren)
#     --space          statt eines Jobs einen CPU-Basic-Space anlegen, der im Hintergrund trainiert
#                      (seit 2026 nur mit PRO-Abo; Fortschritt: hf spaces logs <user>/scorelens-finetune-ft1 --follow)
#   Nur Ergebnis holen/exportieren:  tools/finetune/finetune_hf.sh --fetch [--name ft1]
#
# Voraussetzungen: `hf auth login` (Write-Token), DATASET_DIR aus prelabel.py finalize.
# Jobs sind pay-as-you-go (Guthaben nötig); --space braucht PRO. Kostenlos: lokal (README Weg C) oder Gratis-GPU per
# tools/finetune/finetune_colab.ipynb (Kaggle/Colab). Datensatz-Upload passiert in jedem Fall zuerst.
set -euo pipefail
cd "$(dirname "$0")/../.."
TOOLS=${FREEDARTS_TOOLS:-$HOME/freedarts-tools}
FLAVOR=cpu-basic; EPOCHS=30; FREEZE=10; BATCH=8; TIMEOUT=12h; NAME=ft1; RESUME=1; WAIT=0; FETCH=0; SPACE=0; DATASET_DIR=""
while [ $# -gt 0 ]; do
  case "$1" in
    --flavor) FLAVOR=$2; shift 2;; --epochs) EPOCHS=$2; shift 2;; --freeze) FREEZE=$2; shift 2;; --batch) BATCH=$2; shift 2;;
    --timeout) TIMEOUT=$2; shift 2;; --name) NAME=$2; shift 2;; --no-resume) RESUME=0; shift;; --wait) WAIT=1; shift;; --fetch) FETCH=1; shift;;
    --space) SPACE=1; shift;;
    -*) echo "Unbekannte Option $1"; exit 1;; *) DATASET_DIR=$1; shift;;
  esac
done
HF_USER=${HF_USER:-$(hf auth whoami --format json | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("user") or d.get("name"))')}
DATASET_REPO=${DATASET_REPO:-$HF_USER/scorelens-darts-$NAME}
OUTPUT_REPO=${OUTPUT_REPO:-$HF_USER/scorelens-dart-model-$NAME}
SPACE_REPO=${SPACE_REPO:-$HF_USER/scorelens-finetune-$NAME}

fetch_and_export() {
  mkdir -p "$TOOLS/finetune/$NAME"
  hf download "$OUTPUT_REPO" best.pt metrics.json --local-dir "$TOOLS/finetune/$NAME" >/dev/null
  echo "Metriken:"; cat "$TOOLS/finetune/$NAME/metrics.json"; echo
  WEIGHTS="$TOOLS/finetune/$NAME/best.pt" tools/export_model.sh
}

if [ "$FETCH" = 1 ]; then fetch_and_export; exit 0; fi
[ -n "$DATASET_DIR" ] && [ -f "$DATASET_DIR/data.yaml" ] || { echo "DATASET_DIR mit data.yaml angeben (prelabel.py finalize)"; exit 1; }

case "$FLAVOR" in cpu*) IMAGE=ultralytics/ultralytics:latest-cpu;; *) IMAGE=ultralytics/ultralytics:latest;; esac

echo "Datensatz → $DATASET_REPO (privat)"
hf repos create "$DATASET_REPO" --type dataset --private --exist-ok >/dev/null
hf upload "$DATASET_REPO" "$DATASET_DIR" . --type dataset --include "images/*" --include "labels/*" --include "data.yaml" --commit-message "dataset" >/dev/null
hf upload "$DATASET_REPO" tools/finetune/train_hf.py train_hf.py --type dataset --commit-message "train script" >/dev/null
hf repos create "$OUTPUT_REPO" --type model --private --exist-ok >/dev/null

if [ "$SPACE" = 1 ]; then
  echo "Space → https://huggingface.co/spaces/$SPACE_REPO (CPU Basic)"
  if ! hf repos create "$SPACE_REPO" --type space --sdk gradio --private --exist-ok >/dev/null; then
    echo "Space konnte nicht angelegt werden (Gradio-Spaces auf CPU Basic brauchen ein PRO-Abo)."
    echo "Datensatz liegt in $DATASET_REPO. Kostenlos weiter: lokal (tools/finetune/README.md, Weg C) oder Kaggle/Colab (finetune_colab.ipynb)."
    exit 1
  fi
  hf spaces secrets add "$SPACE_REPO" --secrets HF_TOKEN >/dev/null
  hf spaces variables add "$SPACE_REPO" -e DATASET_REPO="$DATASET_REPO" -e OUTPUT_REPO="$OUTPUT_REPO" -e EPOCHS="$EPOCHS" \
    -e FREEZE="$FREEZE" -e BATCH="$BATCH" -e RESUME="$RESUME" -e PAUSE_SPACE=1 >/dev/null
  hf upload "$SPACE_REPO" tools/finetune/train_hf.py train_hf.py --type space --commit-message "train script" >/dev/null
  hf upload "$SPACE_REPO" tools/finetune/space . --type space --commit-message "space app" >/dev/null
  hf spaces restart "$SPACE_REPO" >/dev/null 2>&1 || true
  echo "Der Space baut jetzt (einige Minuten) und trainiert dann im Hintergrund."
  echo "Log:    hf spaces logs $SPACE_REPO --follow     (Build: --build)"
  echo "Danach: tools/finetune/finetune_hf.sh --fetch --name $NAME"
  echo "Der Space pausiert sich nach dem Training selbst; ein Neustart trainiert nicht erneut (FORCE=1 als Variable erzwingt es)."
  exit 0
fi

BOOT='from huggingface_hub import hf_hub_download as d; import os; exec(open(d(os.environ["DATASET_REPO"], "train_hf.py", repo_type="dataset")).read())'
echo "Job: $FLAVOR, $EPOCHS Epochen, freeze=$FREEZE, Timeout $TIMEOUT → $OUTPUT_REPO"
JOB_JSON=$(hf jobs run --detach --flavor "$FLAVOR" --timeout "$TIMEOUT" --name "scorelens-$NAME" \
  --secrets HF_TOKEN \
  -e DATASET_REPO="$DATASET_REPO" -e OUTPUT_REPO="$OUTPUT_REPO" -e EPOCHS="$EPOCHS" -e FREEZE="$FREEZE" -e BATCH="$BATCH" -e RESUME="$RESUME" \
  "$IMAGE" bash -c "pip install -q -U huggingface_hub && python -c '$BOOT'")
echo "$JOB_JSON"
JOB_ID=$(echo "$JOB_JSON" | grep -oE '[0-9a-f]{24}' | head -1 || true)
echo "Logs:   hf jobs logs --follow $JOB_ID"
echo "Status: hf jobs inspect $JOB_ID"
if [ "$WAIT" = 1 ] && [ -n "$JOB_ID" ]; then
  hf jobs wait "$JOB_ID" --timeout "$TIMEOUT" && fetch_and_export
fi
