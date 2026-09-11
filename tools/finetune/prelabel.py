#!/usr/bin/env python
# /// script
# requires-python = ">=3.10"
# dependencies = ["ultralytics>=8.3,<9", "pillow", "numpy", "pandas"]
# ///
"""
Datensatz für das Feintuning des dart-sense-Modells zusammenstellen (YOLO-Format, 7 Klassen wie in weights.pt:
0=20, 1=3, 2=11, 3=6, 4=dart, 5=9, 6=15; Boxen 0,025 wie bei dart-sense/DeepDarts).

Aufrufe (Python mit ultralytics, z. B. ~/freedarts-tools/yolo-env/bin/python):

  prelabel.py add           --src FOTOS --dst DATASET [--weights W] [--prefix cam1] [--review]
      Eigene Fotos/Frames: Vor-Labels mit dem aktuellen Modell, optional Review-Bilder (DATASET/review/*.jpg).
  prelabel.py add-app       --src training --dst DATASET [--review]
      Von der App gesammelte Bilder + Labels (adb pull …/files/training) übernehmen.
  prelabel.py add-deepdarts --pkl labels.pkl --images cropped_images/800 --dst DATASET [--weights W] [--limit N]
      DeepDarts-Datensatz (McNally 2021, IEEE DataPort) konvertieren; Klassen 9/15 werden per Modell ergänzt.
  prelabel.py add-yolo      --src ROBOFLOW_DIR --dst DATASET --map "1:0,2:1,3:2,4:3,0:4" [--weights W] [--limit N]
      Fertiger YOLO-Datensatz (z. B. Roboflow-Export mit train/valid/test) mit Klassen-Umbenennung; fehlende Klassen
      9/15 ergänzt das Modell. Beispiel: HF-Dataset vandana27/dart_tip_board (DeepDarts D1, CC BY 4.0).
  prelabel.py finalize      --dst DATASET [--val 0.1]
      data.yaml schreiben, Zählung ausgeben.

Bilder landen deterministisch (Hash des Namens) in train oder val. Labels von `add`/`add-deepdarts` sind
Vorschläge: Review-Bilder prüfen und fehlerhafte Labels mit labelImg/LabelStudio korrigieren oder das Bild löschen.
"""
import argparse, hashlib, os, shutil, sys
from pathlib import Path

NAMES = {0: "20", 1: "3", 2: "11", 3: "6", 4: "dart", 5: "9", 6: "15"}
BOX = 0.025
CAL_CONF, DART_CONF = 0.5, 0.3
DEFAULT_WEIGHTS = os.path.expanduser("~/freedarts-tools/dartsense/weights.pt")
IMG_EXT = {".jpg", ".jpeg", ".png"}


def split_of(name: str, val_frac: float) -> str:
    h = int(hashlib.md5(name.encode()).hexdigest(), 16) % 1000
    return "val" if h < val_frac * 1000 else "train"


def dirs(dst: Path, split: str):
    im, lb = dst / "images" / split, dst / "labels" / split
    im.mkdir(parents=True, exist_ok=True); lb.mkdir(parents=True, exist_ok=True)
    return im, lb


def write_label(path: Path, points):
    """points: Liste (cls, x, y) mit x/y normiert 0..1."""
    with open(path, "w") as f:
        for cls, x, y in points:
            f.write(f"{cls} {x:.6f} {y:.6f} {BOX} {BOX}\n")


def read_label(path: Path):
    pts = []
    for line in path.read_text().splitlines():
        p = line.split()
        if len(p) >= 3: pts.append((int(p[0]), float(p[1]), float(p[2])))
    return pts


def load_model(weights):
    from ultralytics import YOLO
    return YOLO(weights)


def predict_points(model, image_path: Path, imgsz=800):
    from PIL import Image
    im = Image.open(image_path).convert("RGB")
    w, h = im.size
    res = model.predict(im, imgsz=imgsz, conf=min(CAL_CONF, DART_CONF), verbose=False)[0]
    pts = []
    for cls, conf, (x, y, _, _) in zip(res.boxes.cls, res.boxes.conf, res.boxes.xywh):
        cls, conf = int(cls), float(conf)
        if (cls == 4 and conf >= DART_CONF) or (cls != 4 and conf >= CAL_CONF):
            pts.append((cls, float(x) / w, float(y) / h))
    return pts


def review_image(src: Path, pts, out: Path):
    from PIL import Image, ImageDraw
    im = Image.open(src).convert("RGB"); w, h = im.size; d = ImageDraw.Draw(im)
    for cls, x, y in pts:
        cx, cy, r = x * w, y * h, max(4, w // 150)
        col = (255, 40, 40) if cls == 4 else (40, 220, 80)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=col, width=3)
        d.text((cx + r + 2, cy - r), NAMES.get(cls, str(cls)), fill=col)
    out.parent.mkdir(parents=True, exist_ok=True); im.save(out, quality=85)


def cmd_add(a):
    model = load_model(a.weights); dst = Path(a.dst); n = 0
    for src in sorted(Path(a.src).rglob("*")):
        if src.suffix.lower() not in IMG_EXT: continue
        name = f"{a.prefix}_{src.stem}" if a.prefix else src.stem
        im, lb = dirs(dst, split_of(name, a.val))
        pts = predict_points(model, src)
        shutil.copy(src, im / (name + src.suffix.lower())); write_label(lb / (name + ".txt"), pts)
        if a.review: review_image(src, pts, dst / "review" / (name + ".jpg"))
        n += 1
        if n % 25 == 0: print(f"{n} Bilder …", flush=True)
    print(f"{n} Bilder mit Vor-Labels übernommen → {dst}")


def cmd_add_app(a):
    src = Path(a.src); dst = Path(a.dst); n = 0
    for img in sorted((src / "images").glob("*")):
        if img.suffix.lower() not in IMG_EXT: continue
        lab = src / "labels" / (img.stem + ".txt")
        if not lab.exists(): continue
        im, lb = dirs(dst, split_of(img.stem, a.val))
        shutil.copy(img, im / img.name); shutil.copy(lab, lb / lab.name)
        if a.review: review_image(img, read_label(lab), dst / "review" / (img.stem + ".jpg"))
        n += 1
    print(f"{n} App-Bilder übernommen → {dst}")


def cmd_add_deepdarts(a):
    """labels.pkl: DataFrame mit img_folder, img_name, xy (n×2, normiert auf das zugeschnittene Bild).
    Reihenfolge der Kalibrierpunkte bei DeepDarts: oben (20), unten (3), links (11), rechts (6); danach Darts."""
    import pandas as pd
    df = pd.read_pickle(a.pkl); dst = Path(a.dst); root = Path(a.images)
    model = load_model(a.weights) if a.weights else None
    n = 0
    for _, row in df.iterrows():
        if a.limit and n >= a.limit: break
        src = root / row["img_folder"] / row["img_name"]
        if not src.exists(): continue
        xy = row["xy"]
        pts = []
        for i, p in enumerate(xy):
            x, y = float(p[0]), float(p[1])
            if x <= 0 and y <= 0: continue  # nicht sichtbar
            pts.append((i if i < 4 else 4, x, y))
        if model is not None:  # 9/15 ergänzen, die DeepDarts nicht kennt
            pts += [p for p in predict_points(model, src) if p[0] in (5, 6)]
        name = f"dd_{row['img_folder']}_{src.stem}"
        im, lb = dirs(dst, split_of(name, a.val))
        shutil.copy(src, im / (name + src.suffix.lower())); write_label(lb / (name + ".txt"), pts)
        if a.review and n < 40: review_image(src, pts, dst / "review" / (name + ".jpg"))
        n += 1
        if n % 200 == 0: print(f"{n} Bilder …", flush=True)
    print(f"{n} DeepDarts-Bilder übernommen → {dst}. Bitte review/ prüfen (Punktreihenfolge!).")


def cmd_add_yolo(a):
    """YOLO-Ordner mit train/valid/test (Roboflow) übernehmen; --map alt:neu,…; valid+test → val.
    --limit begrenzt die Trainingsbilder (zufällig, aber reproduzierbar), --per-source 1 nimmt je Quellbild
    (Name vor '.rf.' bzw. '_JPG') nur eine augmentierte Version."""
    import random
    cmap = {int(k): int(v) for k, v in (kv.split(":") for kv in a.map.split(","))}
    model = load_model(a.weights) if a.weights else None
    src = Path(a.src); dst = Path(a.dst); rnd = random.Random(0); total = 0
    for sub, split in (("train", "train"), ("valid", "val"), ("test", "val")):
        imgs = sorted(p for p in (src / sub / "images").glob("*") if p.suffix.lower() in IMG_EXT) if (src / sub / "images").exists() else []
        if a.per_source:
            seen, keep = set(), []
            rnd.shuffle(imgs)
            for p in imgs:
                key = p.name.split(".rf.")[0].split("_JPG")[0].split("_jpg")[0]
                if key in seen: continue
                seen.add(key); keep.append(p)
            imgs = keep
        if split == "train" and a.limit and len(imgs) > a.limit: rnd.shuffle(imgs); imgs = imgs[:a.limit]
        if split == "val" and a.limit and len(imgs) > max(30, a.limit // 4): rnd.shuffle(imgs); imgs = imgs[:max(30, a.limit // 4)]
        im, lb = dirs(dst, split); n = 0
        for p in imgs:
            lab = src / sub / "labels" / (p.stem + ".txt")
            if not lab.exists(): continue
            pts = [(cmap.get(c, c), x, y) for c, x, y in read_label(lab)]
            have = {c for c, _, _ in pts}
            if model is not None:
                pts += [q for q in predict_points(model, p) if q[0] in (5, 6) or (q[0] < 4 and q[0] not in have)]
            name = f"{a.prefix}_{p.stem}" if a.prefix else p.stem
            shutil.copy(p, im / (name + p.suffix.lower())); write_label(lb / (name + ".txt"), pts)
            if a.review and n < 30: review_image(p, pts, dst / "review" / (name + ".jpg"))
            n += 1
            if n % 100 == 0: print(f"{sub}: {n} …", flush=True)
        print(f"{sub} → {split}: {n} Bilder"); total += n
    print(f"{total} Bilder übernommen → {dst}")


def cmd_finalize(a):
    dst = Path(a.dst).resolve()
    counts = {}
    for split in ("train", "val"):
        im = dst / "images" / split
        counts[split] = len([p for p in im.glob("*") if p.suffix.lower() in IMG_EXT]) if im.exists() else 0
    if counts["train"] == 0: sys.exit("Keine Trainingsbilder in " + str(dst / "images/train"))
    if counts["val"] == 0: print("WARNUNG: keine Validierungsbilder – --val erhöhen oder mehr Bilder sammeln")
    yaml = "path: .\ntrain: images/train\nval: images/val\nnames:\n" + "".join(f"  {k}: '{v}'\n" for k, v in NAMES.items())
    (dst / "data.yaml").write_text(yaml)
    print(f"data.yaml geschrieben: train={counts['train']} val={counts['val']}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    def common(p):
        p.add_argument("--dst", required=True); p.add_argument("--val", type=float, default=0.1); p.add_argument("--review", action="store_true")
    p = sub.add_parser("add"); common(p); p.add_argument("--src", required=True); p.add_argument("--weights", default=DEFAULT_WEIGHTS); p.add_argument("--prefix", default="")
    p = sub.add_parser("add-app"); common(p); p.add_argument("--src", required=True)
    p = sub.add_parser("add-deepdarts"); common(p); p.add_argument("--pkl", required=True); p.add_argument("--images", required=True)
    p.add_argument("--weights", default=DEFAULT_WEIGHTS); p.add_argument("--limit", type=int, default=0)
    p = sub.add_parser("add-yolo"); common(p); p.add_argument("--src", required=True); p.add_argument("--map", default="1:0,2:1,3:2,4:3,0:4")
    p.add_argument("--weights", default=DEFAULT_WEIGHTS); p.add_argument("--limit", type=int, default=0); p.add_argument("--per-source", type=int, default=1); p.add_argument("--prefix", default="dd")
    p = sub.add_parser("finalize"); p.add_argument("--dst", required=True); p.add_argument("--val", type=float, default=0.1)
    a = ap.parse_args()
    {"add": cmd_add, "add-app": cmd_add_app, "add-deepdarts": cmd_add_deepdarts, "add-yolo": cmd_add_yolo, "finalize": cmd_finalize}[a.cmd](a)


if __name__ == "__main__":
    main()
