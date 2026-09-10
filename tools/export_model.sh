#!/usr/bin/env bash
# Exportiert das dart-sense-Modell (weights.pt) nach TFLite und legt es in die App-Assets.
# Voraussetzungen: uv (https://docs.astral.sh/uv/), Internetzugang. Nutzung nur nicht-kommerziell (CC BY-NC 4.0).
set -euo pipefail
cd "$(dirname "$0")/.."
TOOLS=${FREEDARTS_TOOLS:-$HOME/freedarts-tools}
mkdir -p "$TOOLS/dartsense"
[ -f "$TOOLS/dartsense/weights.pt" ] || curl -sL -o "$TOOLS/dartsense/weights.pt" https://raw.githubusercontent.com/bnww/dart-sense/main/weights.pt
[ -d "$TOOLS/yolo-env" ] || uv venv --python 3.11 "$TOOLS/yolo-env"
uv pip install --python "$TOOLS/yolo-env/bin/python" torch torchvision --index-url https://download.pytorch.org/whl/cpu
uv pip install --python "$TOOLS/yolo-env/bin/python" ultralytics "tensorflow-cpu>=2.16" onnx onnxruntime tf_keras ai-edge-litert onnx2tf onnx_graphsurgeon sng4onnx onnxslim
cd "$TOOLS/dartsense"
"$TOOLS/yolo-env/bin/python" - <<'PY'
from ultralytics import YOLO
YOLO("weights.pt").export(format="litert", imgsz=640, nms=False)
PY
SRC=$(ls -t "$TOOLS"/dartsense/weights_saved_model/*float32*.tflite | head -1)
cp "$SRC" "$OLDPWD/app/src/main/assets/dartsense_yolov8n.tflite"
echo "Modell kopiert nach app/src/main/assets/dartsense_yolov8n.tflite"
