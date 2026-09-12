"""Mehrere Gewichte auf demselben Val-Set vergleichen (CPU reicht): mAP + Spitzen-Metriken gesamt und nur D2.
Aufruf: yolo-env/bin/python tools/finetune/eval_local.py DATASET_DIR name=pfad.pt [name2=pfad2.pt ...]"""
import json, sys
from pathlib import Path
from ultralytics import YOLO

data_dir = Path(sys.argv[1]).expanduser()
yaml_path = Path("/tmp/eval_data.yaml"); yaml_path.write_text((data_dir / "data.yaml").read_text().replace("path: .", f"path: {data_dir}"))


def tip_metrics(m, prefix="", tol_px=10.0):
    tol = tol_px / 800; tp = fp = fn = 0
    for img in sorted((data_dir / "images/val").glob(prefix + "*")):
        lab = data_dir / "labels/val" / (img.stem + ".txt")
        gt = [tuple(map(float, l.split()[1:3])) for l in lab.read_text().splitlines() if l.startswith("4 ")] if lab.exists() else []
        r = m.predict(str(img), imgsz=800, conf=0.3, device="cpu", verbose=False)[0]
        pred = [(float(x), float(y)) for c, (x, y, _, _) in zip(r.boxes.cls, r.boxes.xywhn) if int(c) == 4]
        used = set()
        for g in gt:
            cands = [(((p[0] - g[0]) ** 2 + (p[1] - g[1]) ** 2) ** 0.5, i) for i, p in enumerate(pred) if i not in used]
            best = min(cands) if cands else None
            if best is not None and best[0] <= tol: tp += 1; used.add(best[1])
            else: fn += 1
        fp += len(pred) - len(used)
    return dict(tip_recall_10px=tp / max(1, tp + fn), tip_precision_10px=tp / max(1, tp + fp))


out = {}
for arg in sys.argv[2:]:
    name, w = arg.split("=", 1)
    m = YOLO(w)
    v = m.val(data=str(yaml_path), imgsz=800, batch=8, device="cpu", plots=False, verbose=False)
    o = dict(mAP50=float(v.box.map50), mAP50_95=float(v.box.map)); o.update(tip_metrics(m)); o.update({k + "_d2": v for k, v in tip_metrics(m, "dd_d2").items()})
    out[name] = o; print(name, json.dumps(o), flush=True)
print(json.dumps(out, indent=1))
