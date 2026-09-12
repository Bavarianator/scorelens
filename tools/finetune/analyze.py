"""Fehleranalyse eines Modells auf einem Val-Set: je Bild verpasste (FN) und falsche (FP) Dartspitzen (10 px von 800),
gruppiert nach Quelle (D1/D2), echt/verzerrt, Dartanzahl, Spitzenabstand, Helligkeit. Schreibt hard.json (harte Bilder)
und eine Zusammenfassung. Aufruf: yolo-env/bin/python tools/finetune/analyze.py DATASET_DIR weights.pt OUT_DIR"""
import json, sys
from collections import defaultdict
from pathlib import Path
import numpy as np
from PIL import Image
from ultralytics import YOLO

data_dir, weights, out = Path(sys.argv[1]).expanduser(), sys.argv[2], Path(sys.argv[3]).expanduser(); out.mkdir(parents=True, exist_ok=True)
import torch
DEV = 0 if torch.cuda.is_available() else "cpu"
m = YOLO(weights); tol = 10 / 800
rows = []
for img in sorted((data_dir / "images/val").glob("*")):
    lab = data_dir / "labels/val" / (img.stem + ".txt")
    pts = [l.split() for l in lab.read_text().splitlines() if l.strip()] if lab.exists() else []
    gt = [(float(p[1]), float(p[2])) for p in pts if p[0] == "4"]
    cal = [(int(p[0]), float(p[1]), float(p[2])) for p in pts if p[0] != "4"]
    r = m.predict(str(img), imgsz=800, conf=0.3, device=DEV, verbose=False)[0]
    pred = [(float(x), float(y), float(cf)) for c, (x, y, _, _), cf in zip(r.boxes.cls, r.boxes.xywhn, r.boxes.conf) if int(c) == 4]
    pcal = {int(c) for c in r.boxes.cls if int(c) != 4}
    used, fn = set(), 0
    for g in gt:
        cands = [(((p[0] - g[0]) ** 2 + (p[1] - g[1]) ** 2) ** 0.5, i) for i, p in enumerate(pred) if i not in used]
        best = min(cands) if cands else None
        if best is not None and best[0] <= tol: used.add(best[1])
        else: fn += 1
    fp = len(pred) - len(used)
    # Attribute
    mind = min((((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2) ** 0.5 for i, a in enumerate(gt) for b in gt[i + 1:]), default=1.0)
    im = Image.open(img).convert("L"); bright = float(np.asarray(im).mean())
    cx = np.mean([p[1] for p in cal]) if cal else 0.5; cy = np.mean([p[2] for p in cal]) if cal else 0.5
    edge = max((((g[0] - cx) ** 2 + (g[1] - cy) ** 2) ** 0.5 for g in gt), default=0.0)
    rows.append(dict(name=img.name, d2=img.name.startswith("dd_d2"), warped="_w" in img.stem, n=len(gt), fn=fn, fp=fp, mind=round(mind, 3),
                     bright=round(bright, 1), edge=round(edge, 3), cal_missing=sorted({c for c, _, _ in cal} - pcal)))

def group(key, fmt=lambda v: str(v)):
    g = defaultdict(lambda: [0, 0, 0, 0])
    for r in rows:
        k = fmt(key(r)); g[k][0] += 1; g[k][1] += r["n"]; g[k][2] += r["fn"]; g[k][3] += r["fp"]
    return {k: dict(images=v[0], tips=v[1], fn=v[2], fp=v[3], recall=round(1 - v[2] / max(1, v[1]), 3), fp_per_img=round(v[3] / max(1, v[0]), 3)) for k, v in sorted(g.items())}

summary = {
    "gesamt": group(lambda r: "alle"),
    "quelle": group(lambda r: ("d2" if r["d2"] else "d1") + ("_verzerrt" if r["warped"] else "_echt")),
    "darts_im_bild": group(lambda r: r["n"]),
    "spitzenabstand": group(lambda r: r["mind"], lambda v: "<0.03" if v < 0.03 else "<0.06" if v < 0.06 else ">=0.06"),
    "helligkeit": group(lambda r: r["bright"], lambda v: "<70" if v < 70 else "<110" if v < 110 else "<150" if v < 150 else ">=150"),
    "abstand_zentrum": group(lambda r: r["edge"], lambda v: "<0.2" if v < 0.2 else "<0.35" if v < 0.35 else ">=0.35"),
    "kalibrierpunkt_fehlt": group(lambda r: bool(r["cal_missing"])),
}
hard = [r for r in rows if r["fn"] or r["fp"]]
(out / "analysis.json").write_text(json.dumps(dict(summary=summary, hard=hard), indent=1))
print(json.dumps(summary, indent=1)); print(f"{len(hard)} harte Bilder von {len(rows)} → {out}/analysis.json")
