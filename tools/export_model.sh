#!/usr/bin/env bash
# Exportiert das dart-sense-Modell (weights.pt) nach TFLite – mit der Trainingsgröße 800 px und FP16-Gewichten
# (halbe Dateigröße, Ein-/Ausgang bleiben float32) – und legt es in die App-Assets.
# Voraussetzungen: uv (https://docs.astral.sh/uv/), Internetzugang. Nutzung nur nicht-kommerziell (CC BY-NC 4.0).
set -euo pipefail
cd "$(dirname "$0")/.."
APP_DIR=$PWD
TOOLS=${FREEDARTS_TOOLS:-$HOME/freedarts-tools}
IMGSZ=${IMGSZ:-800}
mkdir -p "$TOOLS/dartsense"
[ -f "$TOOLS/dartsense/weights.pt" ] || curl -sL -o "$TOOLS/dartsense/weights.pt" https://raw.githubusercontent.com/bnww/dart-sense/main/weights.pt
[ -d "$TOOLS/yolo-env" ] || uv venv --python 3.11 "$TOOLS/yolo-env"
uv pip install --python "$TOOLS/yolo-env/bin/python" torch torchvision --index-url https://download.pytorch.org/whl/cpu
uv pip install --python "$TOOLS/yolo-env/bin/python" ultralytics "tensorflow-cpu>=2.16" onnx onnxruntime tf_keras ai-edge-litert onnx2tf onnx_graphsurgeon sng4onnx onnxslim
cd "$TOOLS/dartsense"
rm -rf weights_saved_model
IMGSZ=$IMGSZ "$TOOLS/yolo-env/bin/python" - <<'PY'
import os
from ultralytics import YOLO
import tensorflow as tf
size = int(os.environ["IMGSZ"])
YOLO("weights.pt").export(format="tflite", imgsz=size, nms=False)
conv = tf.lite.TFLiteConverter.from_saved_model("weights_saved_model")
conv.optimizations = [tf.lite.Optimize.DEFAULT]
conv.target_spec.supported_types = [tf.float16]
open(f"weights_{size}_float16.tflite", "wb").write(conv.convert())
PY
cp "weights_${IMGSZ}_float16.tflite" "$APP_DIR/app/src/main/assets/dartsense_yolov8n.tflite"
echo "Modell (${IMGSZ} px, FP16) kopiert nach app/src/main/assets/dartsense_yolov8n.tflite"
