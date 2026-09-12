"""
Feintuning des dart-sense-YOLOv8n-Modells auf Modal (T4, Gratis-Guthaben). Datensatz und Startgewichte liegen im
Modal-Volume "scorelens-ft" (einmalig hochladen, siehe README), Ergebnisse landen unter runs/<name>/ im selben Volume.

  modal volume create scorelens-ft
  modal volume put scorelens-ft ~/freedarts-tools/datasets/deepdarts-ds data      # images/, labels/, data.yaml
  modal volume put scorelens-ft ~/freedarts-tools/dartsense/weights.pt base.pt
  modal run tools/finetune/train_modal.py --name dd1 --epochs 30
  modal volume get scorelens-ft runs/dd1 ~/freedarts-tools/finetune/

metrics.json enthält Baseline (Startgewichte) und Ergebnis; exportieren nur, wenn tip_recall/precision besser sind.
"""
import os
from pathlib import Path as _P

import modal

VOL = _P(os.environ.get("SCORELENS_VOL", "/vol"))  # lokal: SCORELENS_VOL=~/freedarts-tools/vol und build_dataset.local(...)

app = modal.App("scorelens-finetune")
vol = modal.Volume.from_name("scorelens-ft", create_if_missing=True)
image = (
    modal.Image.debian_slim(python_version="3.11")
    .apt_install("libgl1", "libglib2.0-0")
    .pip_install("ultralytics>=8.3,<9", "opencv-python-headless")
)


def tip_metrics(m, data_dir, imgsz, tol_px=10.0, prefix=""):
    """Spitzen-Genauigkeit wie in train_hf.py: GT-Dart (Klasse 4) getroffen, wenn Vorhersage näher als tol_px (von 800).
    prefix filtert die Val-Bilder (z. B. "dd_d2" = DeepDarts D2, seitliche Kamera)."""
    from pathlib import Path
    tol = tol_px / 800
    tp = fp = fn = 0
    for img in sorted(Path(data_dir, "images/val").glob(prefix + "*")):
        lab = Path(data_dir, "labels/val", img.stem + ".txt")
        gt = [tuple(map(float, l.split()[1:3])) for l in lab.read_text().splitlines() if l.startswith("4 ")] if lab.exists() else []
        r = m.predict(str(img), imgsz=imgsz, conf=0.3, device=0, verbose=False)[0]
        pred = [(float(x), float(y)) for c, (x, y, _, _) in zip(r.boxes.cls, r.boxes.xywhn) if int(c) == 4]
        used = set()
        for g in gt:
            cands = [(((p[0] - g[0]) ** 2 + (p[1] - g[1]) ** 2) ** 0.5, i) for i, p in enumerate(pred) if i not in used]
            best = min(cands) if cands else None
            if best is not None and best[0] <= tol: tp += 1; used.add(best[1])
            else: fn += 1
        fp += len(pred) - len(used)
    return dict(tip_recall_10px=tp / max(1, tp + fn), tip_precision_10px=tp / max(1, tp + fp), tips_gt=tp + fn, tips_pred=tp + fp)


def evaluate(weights, yaml_path, data_dir, imgsz, batch):
    from pathlib import Path
    from ultralytics import YOLO
    m = YOLO(weights)
    v = m.val(data=yaml_path, imgsz=imgsz, batch=batch, device=0, plots=False, verbose=False)
    out = dict(mAP50=float(v.box.map50), mAP50_95=float(v.box.map), precision=float(v.box.mp), recall=float(v.box.mr))
    out.update(tip_metrics(m, data_dir, imgsz))
    if any(Path(data_dir, "images/val").glob("dd_d2*")):
        out.update({k + "_d2": v for k, v in tip_metrics(m, data_dir, imgsz, prefix="dd_d2").items()})
    return out


@app.function(image=image, gpu="T4", volumes={"/vol": vol}, timeout=80 * 60)  # Kostendeckel: ~1 $ Guthaben, T4 ≈ 0,60 $/h
def train(name: str, epochs: int, batch: int, imgsz: int, freeze: int, patience: int, data: str = "data"):
    import json, shutil, time
    from pathlib import Path
    from ultralytics import YOLO

    data_dir = Path("/vol") / data
    work = Path("/tmp/ft"); work.mkdir(exist_ok=True)
    yaml_path = work / "data.yaml"
    yaml_path.write_text((data_dir / "data.yaml").read_text().replace("path: .", f"path: {data_dir}"))
    n_train = len(list((data_dir / "images/train").glob("*"))); n_val = len(list((data_dir / "images/val").glob("*")))
    print(f"Datensatz: {n_train} train / {n_val} val", flush=True)
    base = "/vol/base.pt"
    out = Path("/vol/runs") / name; out.mkdir(parents=True, exist_ok=True)

    baseline = evaluate(base, str(yaml_path), data_dir, imgsz, batch)
    print("Baseline:", json.dumps(baseline), flush=True)

    # Hyperparameter aus dart-sense (train_optimal.sh); fliplr=0, weil gespiegelte Boards die Kalibrierklassen vertauschen
    hp = dict(lr0=0.0015, lrf=0.01, momentum=0.90098, weight_decay=0.00038, warmup_epochs=1.0, warmup_momentum=0.43,
              box=2.99452, cls=0.30763, dfl=1.53753, hsv_h=0.00695, hsv_s=0.45949, hsv_v=0.24372, degrees=15.58584,
              translate=0.10067, scale=0.2181, shear=0.0, perspective=0.0, flipud=0.0, fliplr=0.0, mosaic=0.6, mixup=0.0)
    t0 = time.time()
    model = YOLO(base)
    (out / "progress.json").write_text(json.dumps(dict(epochs=epochs, epoch=0, started=t0, baseline=baseline)))

    def on_fit_epoch_end(trainer):
        """Nach jeder Epoche results.csv + Stand ins Volume, damit die Fortschrittsseite live mitliest."""
        csv = Path(trainer.save_dir) / "results.csv"
        if csv.exists(): shutil.copy(csv, out / "results.csv")
        (out / "progress.json").write_text(json.dumps(dict(epochs=epochs, epoch=trainer.epoch + 1, started=t0, now=time.time(), baseline=baseline)))
        vol.commit()

    model.add_callback("on_fit_epoch_end", on_fit_epoch_end)
    model.train(data=str(yaml_path), epochs=epochs, imgsz=imgsz, batch=batch, device=0, workers=4,
                freeze=freeze if freeze > 0 else None, patience=patience, cache="ram", plots=False,
                project=str(work / "runs"), name="ft", exist_ok=True, pretrained=True, seed=0, deterministic=False, **hp)
    print(f"Training fertig nach {(time.time() - t0) / 60:.1f} min", flush=True)

    run = Path(model.trainer.save_dir)
    best = run / "weights/best.pt" if (run / "weights/best.pt").exists() else run / "weights/last.pt"
    result = evaluate(str(best), str(yaml_path), data_dir, imgsz, batch)
    metrics = dict(name=name, data=data, epochs=epochs, imgsz=imgsz, freeze=freeze, train_images=n_train, val_images=n_val, baseline=baseline, result=result,
                   better=result["tip_recall_10px"] >= baseline["tip_recall_10px"] and result["tip_precision_10px"] >= baseline["tip_precision_10px"])
    print(json.dumps(metrics, indent=1), flush=True)
    shutil.copy(best, out / "best.pt")
    if (run / "weights/last.pt").exists(): shutil.copy(run / "weights/last.pt", out / "last.pt")
    if (run / "results.csv").exists(): shutil.copy(run / "results.csv", out / "results.csv")
    (out / "metrics.json").write_text(json.dumps(metrics, indent=1))
    vol.commit()
    return metrics


def warp_image(src, pts, rng):
    """Synthetische Schrägsicht: die Board-Ebene wird um ihr Zentrum gedreht (Yaw 25–50° seitlich, Pitch ±12°, Roll ±5°)
    und mit Brennweite f = Bildbreite neu projiziert – das ergibt echte Verkürzung (Ellipse) wie eine seitlich stehende
    Kamera. Danach so skaliert/verschoben, dass alle Punkte mit Rand im 800×800-Bild bleiben. Labels wandern mit."""
    import cv2, numpy as np
    img = cv2.imread(str(src)); h, w = img.shape[:2]
    yaw = np.radians(rng.uniform(25, 50) * rng.choice((-1, 1))); pitch = np.radians(rng.uniform(-12, 12)); roll = np.radians(rng.uniform(-5, 5))
    f = float(w); cx, cy = w / 2, h / 2
    Ry = np.array([[np.cos(yaw), 0, np.sin(yaw)], [0, 1, 0], [-np.sin(yaw), 0, np.cos(yaw)]])
    Rx = np.array([[1, 0, 0], [0, np.cos(pitch), -np.sin(pitch)], [0, np.sin(pitch), np.cos(pitch)]])
    Rz = np.array([[np.cos(roll), -np.sin(roll), 0], [np.sin(roll), np.cos(roll), 0], [0, 0, 1]])
    R = Rz @ Rx @ Ry

    def project(uv):  # Bildpunkt → Punkt auf der Ebene (Z = f vor der Kamera) → um das Zentrum gedreht → zurückprojiziert
        X = np.stack([uv[:, 0] - cx, uv[:, 1] - cy, np.zeros(len(uv))], 1) @ R.T + np.array([0, 0, f])
        return np.stack([f * X[:, 0] / X[:, 2] + cx, f * X[:, 1] / X[:, 2] + cy], 1)

    corners = np.array([[0, 0], [w, 0], [w, h], [0, h]], dtype=np.float32)
    H = cv2.getPerspectiveTransform(corners, project(corners).astype(np.float32))
    P = np.array([[x * w, y * h] for _, x, y in pts], dtype=np.float64).reshape(-1, 1, 2)
    Q = cv2.perspectiveTransform(P, H).reshape(-1, 2)
    cal = Q[[i for i, (c, _, _) in enumerate(pts) if c < 4]] if any(c < 4 for c, _, _ in pts) else Q
    lo, hi = cal.min(0), cal.max(0); span = (hi - lo).max() or 1.0
    sc = 0.8 * w / span; A = np.array([[sc, 0, w / 2 - sc * (lo[0] + hi[0]) / 2], [0, sc, h / 2 - sc * (lo[1] + hi[1]) / 2], [0, 0, 1]])
    H = A @ H
    Q = cv2.perspectiveTransform(P, H).reshape(-1, 2)
    if (Q < 0).any() or (Q[:, 0] > w).any() or (Q[:, 1] > h).any(): return None, None
    out_img = cv2.warpPerspective(img, H, (w, h), borderMode=cv2.BORDER_REPLICATE)
    return out_img, [(c, float(q[0] / w), float(q[1] / h)) for (c, _, _), q in zip(pts, Q)]


def is_hard(pts):
    """Schwachstellen aus der Fehleranalyse (dd3): drei Darts, eng stehende Spitzen (< 0,06 normiert) oder Darts weit
    außen (> 0,35 vom Board-Zentrum, geschätzt aus den Kalibrierpunkten)."""
    darts = [(x, y) for c, x, y in pts if c == 4]
    cal = [(x, y) for c, x, y in pts if c < 4]
    if len(darts) >= 3: return True
    if any(((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2) ** 0.5 < 0.06 for i, a in enumerate(darts) for b in darts[i + 1:]): return True
    cx = sum(x for x, _ in cal) / len(cal) if cal else 0.5; cy = sum(y for _, y in cal) / len(cal) if cal else 0.5
    return any(((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 > 0.35 for x, y in darts)


def augment_dataset(src_dir, out, warps_d1, warps_d2, hard_warps=0, hard_dup=0):
    """Datensatz kopieren und je Bild Schrägsicht-Varianten anhängen (D2-Bilder warps_d2-mal, sonst warps_d1-mal).
    Harte Trainingsbilder (is_hard) bekommen zusätzlich hard_warps Varianten und hard_dup Kopien; Val bleibt unverändert."""
    import cv2, json, random, shutil
    from pathlib import Path
    from PIL import Image, ImageDraw
    rng = random.Random(1)
    if out.exists(): shutil.rmtree(out)
    counts = {"train": 0, "val": 0, "warped": 0, "d2": 0, "hard": 0}
    (out / "review").mkdir(parents=True)
    for sp in ("train", "val"):
        (out / "images" / sp).mkdir(parents=True); (out / "labels" / sp).mkdir(parents=True)
        for img in sorted((src_dir / "images" / sp).glob("*")):
            lab = src_dir / "labels" / sp / (img.stem + ".txt")
            shutil.copy(img, out / "images" / sp / img.name)
            if lab.exists(): shutil.copy(lab, out / "labels" / sp / lab.name)
            counts[sp] += 1; counts["d2"] += img.name.startswith("dd_d2")
            pts = [(int(l.split()[0]), float(l.split()[1]), float(l.split()[2])) for l in lab.read_text().splitlines() if l.strip()] if lab.exists() else []
            hard = sp == "train" and is_hard(pts)
            if hard:
                counts["hard"] += 1
                for k in range(hard_dup):
                    shutil.copy(img, out / "images" / sp / f"{img.stem}_h{k}{img.suffix}")
                    if lab.exists(): shutil.copy(lab, out / "labels" / sp / f"{img.stem}_h{k}.txt")
            for k in range((warps_d2 if img.name.startswith("dd_d2") else warps_d1) + (hard_warps if hard else 0)):
                wimg, wpts = warp_image(img, pts, rng)
                if wimg is None: continue
                wname = f"{img.stem}_w{k}"
                cv2.imwrite(str(out / "images" / sp / (wname + ".jpg")), wimg, [cv2.IMWRITE_JPEG_QUALITY, 88])
                (out / "labels" / sp / (wname + ".txt")).write_text("".join(f"{c} {x:.6f} {y:.6f} 0.025 0.025\n" for c, x, y in wpts))
                counts["warped"] += 1
                if counts["warped"] <= 6:
                    im = Image.fromarray(cv2.cvtColor(wimg, cv2.COLOR_BGR2RGB)); d = ImageDraw.Draw(im); w, h = im.size
                    for c, x, y in wpts: d.ellipse([x * w - 6, y * h - 6, x * w + 6, y * h + 6], outline=("red" if c == 4 else "lime"), width=3); d.text((x * w + 8, y * h - 8), str(c), fill="yellow")
                    im.save(out / "review" / (wname + ".jpg"), quality=80)
            if (counts["train"] + counts["val"]) % 500 == 0: print(counts, flush=True)
    shutil.copy(src_dir / "data.yaml", out / "data.yaml")
    (out / "counts.json").write_text(json.dumps(counts))
    if "SCORELENS_VOL" not in os.environ: vol.commit()
    print("fertig:", counts, flush=True)
    return counts


build_image = image.pip_install("huggingface_hub", "pandas", "pillow", "numpy")


@app.function(image=build_image, gpu="T4", volumes={"/vol": vol}, timeout=90 * 60)
def build_dataset(out_name: str = "data2", d1_limit: int = 5000, val_frac: float = 0.1, warps_d1: int = 0, warps_d2: int = 0, source: str = "hf", hard_warps: int = 0, hard_dup: int = 0):
    """DeepDarts (McNally 2021) aus labels.pkl (GitHub) + Bildern (HF bhabha-kapil/Dartboard-Detection-Dataset, 800 px):
    alle D2-Bilder (seitliche Kamera) plus ganze D1-Ordner bis d1_limit. Klassen wie dart-sense (20,3,11,6,dart,9,15),
    9/15 ergänzt das Basismodell. Split deterministisch per Hash wie in prelabel.py."""
    import hashlib, json, random, shutil, urllib.request
    from pathlib import Path
    import pandas as pd
    from PIL import Image, ImageDraw
    from huggingface_hub import snapshot_download
    from ultralytics import YOLO

    if source != "hf":  # vorhandenen YOLO-Datensatz (z. B. data2) kopieren und um Schrägsichten ergänzen – kein Download, kein Vorlabel
        return augment_dataset(VOL / source, VOL / out_name, warps_d1, warps_d2, hard_warps, hard_dup)
    pkl = VOL / "labels.pkl"
    if not pkl.exists(): urllib.request.urlretrieve("https://raw.githubusercontent.com/wmcnally/deep-darts/master/dataset/labels.pkl", pkl)
    df = pd.read_pickle(pkl)
    folders = df.img_folder.value_counts()
    d1 = [f for f in folders.index if f.startswith("d1")]; random.Random(0).shuffle(d1)
    chosen = [f for f in folders.index if f.startswith("d2")]
    n = 0
    for f in d1:
        if n >= d1_limit: break
        chosen.append(f); n += int(folders[f])
    df = df[df.img_folder.isin(chosen)]
    print(f"{len(df)} Bilder aus {len(chosen)} Ordnern (D2: {int(df.img_folder.str.startswith('d2').sum())})", flush=True)
    root = Path(snapshot_download("bhabha-kapil/Dartboard-Detection-Dataset", repo_type="dataset", local_dir=str(VOL / "hf"),
                                  allow_patterns=[f"images/{f}/*" for f in chosen])) / "images"
    import torch
    dev = 0 if torch.cuda.is_available() else "cpu"
    model = YOLO(str(VOL / "base.pt"))
    out = VOL / out_name
    if out.exists(): shutil.rmtree(out)
    for sp in ("train", "val"): (out / "images" / sp).mkdir(parents=True); (out / "labels" / sp).mkdir(parents=True)
    (out / "review").mkdir()
    counts = {"train": 0, "val": 0, "missing": 0, "d2": 0, "warped": 0}
    rows = list(df.itertuples())
    import cv2
    rng = random.Random(1)

    def warp(src, pts, k): return warp_image(src, pts, rng)
    for i, row in enumerate(rows):
        src = root / row.img_folder / row.img_name
        if not src.exists(): counts["missing"] += 1; continue
        pts = [(min(j, 4), float(p[0]), float(p[1])) for j, p in enumerate(row.xy) if not (float(p[0]) <= 0 and float(p[1]) <= 0)]
        r = model.predict(str(src), imgsz=800, conf=0.5, device=dev, verbose=False)[0]
        pts += [(int(c), float(x), float(y)) for c, (x, y, _, _) in zip(r.boxes.cls, r.boxes.xywhn) if int(c) in (5, 6)]
        name = f"dd_{row.img_folder}_{Path(row.img_name).stem}"
        sp = "val" if int(hashlib.md5(name.encode()).hexdigest(), 16) % 1000 < val_frac * 1000 else "train"
        shutil.copy(src, out / "images" / sp / (name + src.suffix.lower()))
        (out / "labels" / sp / (name + ".txt")).write_text("".join(f"{c} {x:.6f} {y:.6f} 0.025 0.025\n" for c, x, y in pts))
        counts[sp] += 1; counts["d2"] += row.img_folder.startswith("d2")
        for k in range(warps_d2 if row.img_folder.startswith("d2") else warps_d1):
            wimg, wpts = warp(src, pts, k)
            if wimg is None: continue
            wname = f"{name}_w{k}"
            cv2.imwrite(str(out / "images" / sp / (wname + ".jpg")), wimg, [cv2.IMWRITE_JPEG_QUALITY, 88])
            (out / "labels" / sp / (wname + ".txt")).write_text("".join(f"{c} {x:.6f} {y:.6f} 0.025 0.025\n" for c, x, y in wpts))
            counts["warped"] += 1
            if counts["warped"] <= 4:
                im = Image.fromarray(cv2.cvtColor(wimg, cv2.COLOR_BGR2RGB)); d = ImageDraw.Draw(im); w, h = im.size
                for c, x, y in wpts: d.ellipse([x * w - 6, y * h - 6, x * w + 6, y * h + 6], outline=("red" if c == 4 else "lime"), width=3); d.text((x * w + 8, y * h - 8), str(c), fill="yellow")
                im.save(out / "review" / (wname + ".jpg"), quality=80)
        if counts["d2"] <= 4 and row.img_folder.startswith("d2") or i < 2:
            im = Image.open(src).convert("RGB"); d = ImageDraw.Draw(im); w, h = im.size
            for c, x, y in pts: d.ellipse([x * w - 6, y * h - 6, x * w + 6, y * h + 6], outline=("red" if c == 4 else "lime"), width=3); d.text((x * w + 8, y * h - 8), str(c), fill="yellow")
            im.save(out / "review" / (name + ".jpg"), quality=80)
        if i % 500 == 0: print(f"{i}/{len(rows)} …", flush=True)
    (out / "data.yaml").write_text("path: .\ntrain: images/train\nval: images/val\nnames:\n  0: '20'\n  1: '3'\n  2: '11'\n  3: '6'\n  4: 'dart'\n  5: '9'\n  6: '15'\n")
    (out / "counts.json").write_text(json.dumps(counts))
    if "SCORELENS_VOL" not in os.environ: vol.commit()
    print("fertig:", counts, flush=True)
    return counts


web_image = modal.Image.debian_slim(python_version="3.11").pip_install("fastapi[standard]")


@app.function(image=web_image, volumes={"/vol": vol})
@modal.fastapi_endpoint()
def review(name: str = "dd2", file: str = ""):
    """Review-Bild (Labels eingezeichnet) aus dem Volume ausliefern."""
    from pathlib import Path
    from fastapi.responses import Response
    p = Path("/vol") / name / "review" / Path(file).name
    return Response(p.read_bytes(), media_type="image/jpeg") if p.exists() else Response("nicht gefunden", status_code=404)


@app.function(image=web_image, volumes={"/vol": vol})
@modal.fastapi_endpoint()
def progress(name: str = ""):
    """Fortschrittsseite: alle Läufe, Balken, Verlaufsgrafik, Baseline-Vergleich, Datensatz und Review-Bilder."""
    import csv, json, time
    from pathlib import Path
    from fastapi.responses import HTMLResponse
    vol.reload()
    runs = sorted(p.name for p in Path("/vol/runs").glob("*")) if Path("/vol/runs").exists() else []
    name = name or (runs[-1] if runs else "dd1")
    run = Path("/vol/runs") / name
    p = json.loads((run / "progress.json").read_text()) if (run / "progress.json").exists() else None
    rows = list(csv.DictReader((run / "results.csv").open())) if (run / "results.csv").exists() else []
    rows = [{k.strip(): v.strip() for k, v in r.items()} for r in rows]
    done = json.loads((run / "metrics.json").read_text()) if (run / "metrics.json").exists() else None
    css = "font-family:system-ui;background:#0b1220;color:#eee;padding:16px;max-width:960px;margin:auto"
    card = "background:#171c27;border-radius:14px;padding:14px;margin:10px 0"
    nav = " · ".join(f'<a style="color:{"#fff" if r == name else "#9aa3b5"}" href="?name={r}">{r}</a>' for r in runs) or "keine"

    def fmt(v): return f"{v:.3f}" if isinstance(v, float) else str(v)

    def chart(keys, colors, title):
        """SVG-Linien über die Epochen, jede Reihe auf ihr Min/Max normiert (Vergleich der Form, nicht der Skala)."""
        if len(rows) < 2: return ""
        w, h = 900, 160
        out = []
        for k, c in zip(keys, colors):
            ys = [float(r[k]) for r in rows if k in r]
            lo, hi = min(ys), max(ys); rng = (hi - lo) or 1
            pts = " ".join(f"{i / (len(ys) - 1) * (w - 20) + 10:.0f},{h - 10 - (y - lo) / rng * (h - 30):.0f}" for i, y in enumerate(ys))
            out.append(f'<polyline fill="none" stroke="{c}" stroke-width="2" points="{pts}"/><text x="10" y="{14 + 14 * keys.index(k)}" fill="{c}" font-size="11">{k} (zuletzt {ys[-1]:.4g}, best {max(ys) if "mAP" in k or "precision" in k or "recall" in k else min(ys):.4g})</text>')
        return f'<div style="{card}"><b>{title}</b><svg viewBox="0 0 {w} {h}" style="width:100%;height:auto;background:#0d1119;border-radius:8px;margin-top:6px">{"".join(out)}</svg></div>'

    body = f'<h2>Scorelens Feintuning</h2><p>Läufe: {nav}</p>'
    if not p:
        body += f'<div style="{card}">Lauf „{name}“: noch nicht gestartet.</div>'
    else:
        pct = int(100 * p["epoch"] / p["epochs"]); mins = (p.get("now", time.time()) - p["started"]) / 60
        eta = (mins / p["epoch"] * (p["epochs"] - p["epoch"])) if p["epoch"] else 0
        state = "fertig" if done else ("läuft" if p.get("now", 0) > time.time() - 600 else "abgebrochen?")
        body += f'<div style="{card}"><b>{name}</b> · {state} · Epoche {p["epoch"]} / {p["epochs"]} · {mins:.0f} min' + (f' · noch etwa {eta:.0f} min' if not done else "") + \
                f'<div style="background:#2a3040;border-radius:8px;height:16px;margin-top:8px"><div style="width:{pct}%;background:{"#7cf06b" if done else "#2b6bff"};height:16px;border-radius:8px"></div></div></div>'
        b = p["baseline"]; r = done["result"] if done else None
        keys = [k for k in b.keys() if isinstance(b[k], float)]
        trs = "".join(f'<tr><td>{k}</td><td>{fmt(b[k])}</td><td style="color:{"#7cf06b" if r and r[k] >= b[k] else "#e5484d"}">{fmt(r[k]) if r else "…"}</td></tr>' for k in keys)
        verdict = "" if not done else f'<p style="font-size:18px">{"✅ besser als Baseline" if done["better"] else "❌ nicht besser als Baseline"} (Spitzen-Recall und -Precision auf 10 px)</p>'
        body += f'<div style="{card}"><b>Baseline vs. Ergebnis</b>{verdict}<table style="border-collapse:collapse;margin-top:6px"><tr><th align="left">Kennzahl</th><th>Baseline</th><th>Feintuning</th></tr>{trs}</table>' \
                f'<p style="color:#9aa3b5">_d2 = nur DeepDarts D2 (seitliche Kamera, wie das Handy am Board). Zähler tips_gt/tips_pred = Anzahl Dartspitzen.</p></div>'
        body += chart(["metrics/mAP50(B)", "metrics/precision(B)", "metrics/recall(B)"], ["#7cf06b", "#4c8dff", "#ffc107"], "Validierung je Epoche")
        body += chart(["train/box_loss", "val/box_loss", "train/cls_loss", "val/cls_loss"], ["#4c8dff", "#7cf06b", "#ffc107", "#e5484d"], "Verluste je Epoche (fallend = gut)")
    data = Path("/vol") / (done or {}).get("data", "data2") if done else Path("/vol/data2")
    for d in ([data] if data.exists() else []) + [Path("/vol/data")]:
        c = json.loads((d / "counts.json").read_text()) if (d / "counts.json").exists() else None
        n_tr = len(list((d / "images/train").glob("*"))) if (d / "images/train").exists() else 0
        n_va = len(list((d / "images/val").glob("*"))) if (d / "images/val").exists() else 0
        imgs = sorted(x.name for x in (d / "review").glob("*.jpg")) if (d / "review").exists() else []
        rev = review.get_web_url()  # eigener Endpoint = eigene URL auf Modal
        gal = "".join(f'<a href="{rev}?name={d.name}&file={f}" target="_blank"><img src="{rev}?name={d.name}&file={f}" style="width:180px;border-radius:8px;margin:4px" title="{f}"></a>' for f in imgs[:12])
        body += f'<div style="{card}"><b>Datensatz {d.name}</b> · {n_tr} train / {n_va} val' + (f' · D2: {c["d2"]} · fehlend: {c["missing"]}' if c else "") + \
                (f'<div style="margin-top:8px">{gal}</div><p style="color:#9aa3b5">Grün = Kalibrierpunkte 20/3/11/6 und 9/15, Rot = Dartspitzen. Reihenfolge prüfen: 0 oben, 1 unten, 2 links, 3 rechts.</p>' if gal else "") + '</div>'
    refresh = '<meta http-equiv="refresh" content="5">' if p and not done else ""
    return HTMLResponse(f'<!doctype html>{refresh}<meta name="viewport" content="width=device-width"><body style="{css}">{body}<p style="color:#9aa3b5">Modal-Volume scorelens-ft · {"aktualisiert alle 5 s" if refresh else "statisch"}</p></body>')


@app.local_entrypoint()
def main(name: str = "dd1", epochs: int = 30, batch: int = 16, imgsz: int = 800, freeze: int = 10, patience: int = 10, data: str = "data", build: bool = False, d1_limit: int = 5000, warps_d1: int = 0, warps_d2: int = 0, source: str = "hf"):
    if build:
        print(build_dataset.remote(data, d1_limit, 0.1, warps_d1, warps_d2, source)); return
    m = train.remote(name, epochs, batch, imgsz, freeze, patience, data)
    print("better:", m["better"], "| baseline tip_recall %.3f → %.3f, precision %.3f → %.3f" % (
        m["baseline"]["tip_recall_10px"], m["result"]["tip_recall_10px"], m["baseline"]["tip_precision_10px"], m["result"]["tip_precision_10px"]))
