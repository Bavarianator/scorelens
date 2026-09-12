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
import modal

app = modal.App("scorelens-finetune")
vol = modal.Volume.from_name("scorelens-ft", create_if_missing=True)
image = (
    modal.Image.debian_slim(python_version="3.11")
    .apt_install("libgl1", "libglib2.0-0")
    .pip_install("ultralytics>=8.3,<9", "opencv-python-headless")
)


def tip_metrics(m, data_dir, imgsz, tol_px=10.0):
    """Spitzen-Genauigkeit wie in train_hf.py: GT-Dart (Klasse 4) getroffen, wenn Vorhersage näher als tol_px (von 800)."""
    from pathlib import Path
    tol = tol_px / 800
    tp = fp = fn = 0
    for img in sorted(Path(data_dir, "images/val").glob("*")):
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
    from ultralytics import YOLO
    m = YOLO(weights)
    v = m.val(data=yaml_path, imgsz=imgsz, batch=batch, device=0, plots=False, verbose=False)
    out = dict(mAP50=float(v.box.map50), mAP50_95=float(v.box.map), precision=float(v.box.mp), recall=float(v.box.mr))
    out.update(tip_metrics(m, data_dir, imgsz))
    return out


@app.function(image=image, gpu="T4", volumes={"/vol": vol}, timeout=80 * 60)  # Kostendeckel: ~1 $ Guthaben, T4 ≈ 0,60 $/h
def train(name: str, epochs: int, batch: int, imgsz: int, freeze: int, patience: int):
    import json, shutil, time
    from pathlib import Path
    from ultralytics import YOLO

    data_dir = Path("/vol/data")
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
    metrics = dict(name=name, epochs=epochs, imgsz=imgsz, freeze=freeze, train_images=n_train, val_images=n_val, baseline=baseline, result=result,
                   better=result["tip_recall_10px"] >= baseline["tip_recall_10px"] and result["tip_precision_10px"] >= baseline["tip_precision_10px"])
    print(json.dumps(metrics, indent=1), flush=True)
    shutil.copy(best, out / "best.pt")
    if (run / "weights/last.pt").exists(): shutil.copy(run / "weights/last.pt", out / "last.pt")
    if (run / "results.csv").exists(): shutil.copy(run / "results.csv", out / "results.csv")
    (out / "metrics.json").write_text(json.dumps(metrics, indent=1))
    vol.commit()
    return metrics


web_image = modal.Image.debian_slim(python_version="3.11").pip_install("fastapi[standard]")


@app.function(image=web_image, volumes={"/vol": vol})
@modal.fastapi_endpoint()
def progress(name: str = "dd1"):
    """Kleine Fortschrittsseite: Balken, Epoche, Kennzahlen aus results.csv, am Ende metrics.json."""
    import csv, json, time
    from pathlib import Path
    from fastapi.responses import HTMLResponse
    vol.reload()
    run = Path("/vol/runs") / name
    p = json.loads((run / "progress.json").read_text()) if (run / "progress.json").exists() else None
    rows = list(csv.DictReader((run / "results.csv").open())) if (run / "results.csv").exists() else []
    done = json.loads((run / "metrics.json").read_text()) if (run / "metrics.json").exists() else None
    if not p: return HTMLResponse(f"<h2>Lauf „{name}“: noch nicht gestartet</h2>")
    pct = int(100 * p["epoch"] / p["epochs"])
    mins = (p.get("now", time.time()) - p["started"]) / 60
    eta = (mins / p["epoch"] * (p["epochs"] - p["epoch"])) if p["epoch"] else 0
    head = "".join(f"<th>{k.strip()}</th>" for k in rows[0].keys()) if rows else ""
    body = "".join("<tr>" + "".join(f"<td>{float(v):.4g}</td>" if v.strip().replace('.', '', 1).replace('-', '', 1).isdigit() else f"<td>{v}</td>" for v in r.values()) + "</tr>" for r in rows[-8:])
    b = p["baseline"]
    result = ""
    if done:
        r = done["result"]
        result = f"<h3>{'✅ besser als Baseline' if done['better'] else '❌ nicht besser'}</h3><p>tip_recall {b['tip_recall_10px']:.3f} → {r['tip_recall_10px']:.3f} · tip_precision {b['tip_precision_10px']:.3f} → {r['tip_precision_10px']:.3f} · mAP50 {b['mAP50']:.3f} → {r['mAP50']:.3f}</p>"
    return HTMLResponse(f"""<!doctype html><meta http-equiv="refresh" content="30"><meta name="viewport" content="width=device-width">
<body style="font-family:system-ui;background:#0b1220;color:#eee;padding:16px;max-width:900px;margin:auto">
<h2>Scorelens Feintuning „{name}“</h2>
<div style="background:#2a3040;border-radius:8px;height:18px"><div style="width:{pct}%;background:#2b6bff;height:18px;border-radius:8px"></div></div>
<p>Epoche {p['epoch']} / {p['epochs']} · {mins:.0f} min gelaufen · noch etwa {eta:.0f} min · Baseline tip_recall {b['tip_recall_10px']:.3f}, tip_precision {b['tip_precision_10px']:.3f}, mAP50 {b['mAP50']:.3f}</p>
{result}
<div style="overflow-x:auto"><table style="font-size:12px;border-collapse:collapse"><tr>{head}</tr>{body}</table></div>
<p style="color:#9aa3b5">Aktualisiert sich alle 30 s.</p></body>""")


@app.local_entrypoint()
def main(name: str = "dd1", epochs: int = 30, batch: int = 16, imgsz: int = 800, freeze: int = 10, patience: int = 10):
    m = train.remote(name, epochs, batch, imgsz, freeze, patience)
    print("better:", m["better"], "| baseline tip_recall %.3f → %.3f, precision %.3f → %.3f" % (
        m["baseline"]["tip_recall_10px"], m["result"]["tip_recall_10px"], m["baseline"]["tip_precision_10px"], m["result"]["tip_precision_10px"]))
