#!/usr/bin/env bash
# Exportiert das dart-sense-Modell (weights.pt) nach TFLite in der Trainingsgröße 800 px (float32, ca. 12 MB)
# und legt es in die App-Assets. Der Weg über den LiteRT-Torch-Konverter (format=litert) ist der einzige, der
# hier zuverlässig läuft; der onnx2tf-Weg (format=tflite) scheitert an protobuf-Konflikten.
# Danach wird die Kotlin-Decode-Logik (Letterbox, argmax, NMS) gegen ultralytics geprüft; nur bei Übereinstimmung
# wird das Modell kopiert. Voraussetzungen: uv (https://docs.astral.sh/uv/), Internetzugang, ca. 5 min CPU.
# Nutzung nur nicht-kommerziell (CC BY-NC 4.0).
set -euo pipefail
cd "$(dirname "$0")/.."
APP_DIR=$PWD
TOOLS=${FREEDARTS_TOOLS:-$HOME/freedarts-tools}
IMGSZ=${IMGSZ:-800}
mkdir -p "$TOOLS/dartsense"
[ -f "$TOOLS/dartsense/weights.pt" ] || curl -sL -o "$TOOLS/dartsense/weights.pt" https://raw.githubusercontent.com/bnww/dart-sense/main/weights.pt
[ -f "$TOOLS/dartsense/sample.jpg" ] || curl -sL -o "$TOOLS/dartsense/sample.jpg" https://raw.githubusercontent.com/bnww/dart-sense/main/images/d2_02_03_2021_2_DSC_0059.JPG
[ -d "$TOOLS/yolo-env" ] || uv venv --python 3.11 "$TOOLS/yolo-env"
uv pip install --python "$TOOLS/yolo-env/bin/python" torch torchvision --index-url https://download.pytorch.org/whl/cpu
uv pip install --python "$TOOLS/yolo-env/bin/python" ultralytics ai-edge-litert litert-torch pillow numpy
cd "$TOOLS/dartsense"
IMGSZ=$IMGSZ APP_ASSET="$APP_DIR/app/src/main/assets/dartsense_yolov8n.tflite" "$TOOLS/yolo-env/bin/python" - <<'PY'
import os, shutil, sys, numpy as np
from ultralytics import YOLO
from PIL import Image
from ai_edge_litert.interpreter import Interpreter
IMG = int(os.environ["IMGSZ"]); asset = os.environ["APP_ASSET"]
model = YOLO("weights.pt")
tfl = model.export(format="litert", imgsz=IMG, nms=False)
# Prüfbild: linkes oberes Viertel der Beispielgrafik (echtes Foto), quadratisch → kein Letterbox-Unterschied
im = Image.open("sample.jpg").convert("RGB"); W, H = im.size
img = im.crop((0, 0, W // 2, H // 2)).resize((IMG, IMG))
res = model.predict(img, imgsz=IMG, conf=0.25, verbose=False)[0]
ref = sorted((int(c), round(float(x), 1), round(float(y), 1)) for c, (x, y, _, _) in zip(res.boxes.cls, res.boxes.xywh))
it = Interpreter(model_path=tfl, num_threads=4); it.allocate_tensors()
inp = it.get_input_details()[0]; outd = it.get_output_details()[0]
x = np.array(img).astype(np.float32)[None] / 255.0
if list(inp["shape"])[1] == 3: x = np.ascontiguousarray(np.transpose(x, (0, 3, 1, 2)))
it.set_tensor(inp["index"], x); it.invoke()
o = it.get_tensor(outd["index"])[0]
if o.shape[0] > o.shape[1]: o = o.T
f = IMG if o[:4].max() <= 1.5 else 1.0
boxes = sorted(((float(o[4 + int(np.argmax(o[4:, i])), i]), int(np.argmax(o[4:, i])), o[0, i] * f, o[1, i] * f, o[2, i] * f, o[3, i] * f)
                for i in range(o.shape[1]) if float(o[4:, i].max()) >= 0.25), reverse=True)
def iou(a, b):
    ax1, ay1, ax2, ay2 = a[2] - a[4] / 2, a[3] - a[5] / 2, a[2] + a[4] / 2, a[3] + a[5] / 2
    bx1, by1, bx2, by2 = b[2] - b[4] / 2, b[3] - b[5] / 2, b[2] + b[4] / 2, b[3] + b[5] / 2
    iw, ih = max(0, min(ax2, bx2) - max(ax1, bx1)), max(0, min(ay2, by2) - max(ay1, by1))
    inter = iw * ih; union = a[4] * a[5] + b[4] * b[5] - inter
    return inter / union if union > 0 else 0
kept = []
for b in boxes:
    if not any(k[1] == b[1] and iou(k, b) > 0.5 for k in kept): kept.append(b)
mine = sorted((b[1], round(float(b[2]), 1), round(float(b[3]), 1)) for b in kept)
print("ultralytics:", ref); print("tflite     :", mine)
ok = len(mine) == len(ref) and all(a[0] == b[0] and abs(a[1] - b[1]) < 3 and abs(a[2] - b[2]) < 3 for a, b in zip(mine, ref))
if not ok: sys.exit("MISMATCH – Modell nicht kopiert")
shutil.copy(tfl, asset); print("Modell (%d px) kopiert nach %s (%d Bytes)" % (IMG, asset, os.path.getsize(asset)))
PY
