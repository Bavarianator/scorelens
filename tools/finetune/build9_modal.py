"""data9 + dd9 auf Modal (T4, Gratis-Guthaben 30 $/Monat, kein Kaggle-Kontingent nötig): riesiger Datensatz, aufbauend
auf dd6 (dd8 ist auf Kaggle zweimal ausgefallen, deshalb direkt von dd6 aus). Alles in einem Prozess (kein Zip-Hin-und-
Her wie bei Kaggle, also auch keine der beiden "Ordner nach dem Zippen gelöscht"-Fallen von dd7/dd8 möglich).

data9 = KOMPLETTES data6 (77.884 Bilder, unverändert – nie verdünnen, nur ergänzen)
      + ALLE Roboflow train+valid (22.780 Bilder, Original UND je eine Schrägsicht+Verderbung – der Test-Split bleibt
        für bench_rf unberührt)
      + 3.000 davon extra-hart (von dd6 selbst gemint: höchste Fehlerquote bzw. laut Ground Truth is_hard), mit
        stärkerer Schrägsicht (55–70°) + Verderbung
Val: unverändert data6s Val, damit alle bisherigen Zahlen vergleichbar bleiben.

  venv/bin/modal run tools/finetune/build9_modal.py

Ergebnis: ~/freedarts-tools/finetune/dd9/{best.pt,metrics.json,results.csv}, dazu Volume /vol/runs/dd9/.
"""
import json
from pathlib import Path

import modal

here = Path(__file__).resolve().parent
tools = Path.home() / "freedarts-tools"
app = modal.App("scorelens-dd9")
vol = modal.Volume.from_name("scorelens-ft")
image = (
    modal.Image.debian_slim(python_version="3.11")
    .apt_install("libgl1", "libglib2.0-0")
    .pip_install("ultralytics>=8.3,<9", "opencv-python-headless", "requests")
    .add_local_file(here / "dataset.py", "/root/finetune/dataset.py")
)
KAGGLE = {"dd6": ("scorelens-build-data6", "kg_dd6/best.pt")}
ROBOFLOW_KEY = "uZOBo8dtYnbzcaPPRsuG"


def kaggle_urls(wanted):
    from kaggle.api.kaggle_api_extended import KaggleApi
    from kagglesdk.kernels.types.kernels_api_service import ApiListKernelSessionOutputRequest
    api = KaggleApi(); api.authenticate(); out = {}
    with api.build_kaggle_client() as kg:
        for kernel in wanted:
            req = ApiListKernelSessionOutputRequest(); req.user_name = "mobayer"; req.kernel_slug = kernel; req.page_size = 100
            out[kernel] = {f.file_name: f.url for f in kg.kernels.kernels_api_client.list_kernel_session_output(req).files}
    return out


@app.function(image=image, gpu="T4", volumes={"/vol": vol}, timeout=6 * 3600)
def build_and_train(data6_url: str, dd6_url: str, time_h: float):
    import random, shutil, sys, urllib.request, zipfile
    import cv2, requests, torch
    sys.path.insert(0, "/root/finetune")
    from dataset import NAMES, is_hard, read_pts, warp_image, degrade_image, write_pts
    from ultralytics import YOLO
    assert torch.cuda.is_available()
    print("GPU:", torch.cuda.get_device_name(0), flush=True)
    rng = random.Random(9)
    T = Path("/tmp/build"); T.mkdir(parents=True, exist_ok=True)
    out = T / "data9"
    for sp in ("train", "val"):
        (out / "images" / sp).mkdir(parents=True, exist_ok=True); (out / "labels" / sp).mkdir(parents=True, exist_ok=True)

    # 1) dd6-Gewichte ins Volume (liegen normalerweise schon dort aus früheren Läufen)
    dd6_path = Path("/vol/runs/dd6/best.pt")
    if not dd6_path.exists():
        dd6_path.parent.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve(dd6_url, dd6_path); vol.commit()
        print("dd6 ins Volume geladen", flush=True)

    # 2) komplettes data6 unverändert (aus dem Kaggle-Build-Kernel) – dd8 ist ausgefallen, wir bauen direkt auf dd6 auf
    # Robust laden: urlretrieve bricht bei 11 GB gern ohne Wiederholung ab, deshalb gestreamt + mit Retries.
    print("lade data6 (ca. 11 GB) …", flush=True)
    for attempt in range(5):
        try:
            with requests.get(data6_url, stream=True, timeout=120) as resp:
                resp.raise_for_status()
                with open(T / "data6.zip", "wb") as f:
                    for chunk in resp.iter_content(1 << 20):
                        if chunk: f.write(chunk)
            break
        except Exception as e:
            print(f"Download-Fehler (Versuch {attempt + 1}/5): {e}", flush=True); (T / "data6.zip").unlink(missing_ok=True)
    else:
        raise SystemExit("data6.zip-Download dauerhaft fehlgeschlagen")
    print("data6.zip:", (T / "data6.zip").stat().st_size // 2**20, "MB geladen", flush=True)
    with zipfile.ZipFile(T / "data6.zip") as zf: zf.extractall(T)
    (T / "data6.zip").unlink()
    shutil.rmtree(out); shutil.move(str(T / "data6"), str(out))
    n_old = sum(1 for _ in (out / "images/train").glob("*")); n_val = sum(1 for _ in (out / "images/val").glob("*"))
    print(f"data6 übernommen: {n_old} train / {n_val} val", flush=True)

    # 3) Roboflow komplett train+valid laden (test bleibt für bench_rf unberührt)
    r = requests.get(f"https://api.roboflow.com/dartsync/darts-bjj98-minfw/1/yolov8?api_key={ROBOFLOW_KEY}", timeout=60).json()
    link = r["export"]["link"]; print("Roboflow-Export:", r["export"], flush=True)
    with requests.get(link, stream=True, timeout=300) as resp:
        resp.raise_for_status()
        with open(T / "rf.zip", "wb") as f:
            for chunk in resp.iter_content(1 << 20): f.write(chunk)
    rf_dir = T / "roboflow"; rf_dir.mkdir(exist_ok=True)
    with zipfile.ZipFile(T / "rf.zip") as zf: zf.extractall(rf_dir)
    (T / "rf.zip").unlink()

    pool = []
    for split in ("train", "valid"):
        for img in (rf_dir / split / "images").glob("*"):
            lab = rf_dir / split / "labels" / (img.stem + ".txt")
            if lab.exists(): pool.append((img, lab))
    print(f"Roboflow train+valid: {len(pool)} Bilder", flush=True)
    counts = [0] * 7
    for _, lab in pool[:3000]:
        for line in lab.read_text().splitlines():
            if line.strip(): counts[int(line.split()[0])] += 1
    assert counts[4] > 1.5 * max(counts[:4]), f"Klassenzuordnung falsch: {counts}"
    print("Klassenzuordnung ok", flush=True)

    # 4) breite Ergänzung: jedes Roboflow-Bild Original + eine Schrägsicht+Verderbung
    n_rf_orig = n_rf_warp = 0
    for i, (img, lab) in enumerate(pool):
        if i % 3000 == 0: print(f"  Roboflow breit {i}/{len(pool)} …", flush=True)
        pts = read_pts(lab); base = cv2.imread(str(img))
        if base is None: continue
        stem = f"rf9_{img.stem}"
        shutil.copy(img, out / "images/train" / f"{stem}{img.suffix}"); write_pts(out / "labels/train" / f"{stem}.txt", pts); n_rf_orig += 1
        wimg, wpts = warp_image(base, pts, rng, yaw=(30, 55))
        if wimg is None: continue
        dimg = degrade_image(wimg, rng)
        cv2.imwrite(str(out / "images/train" / f"{stem}_W.jpg"), dimg, [cv2.IMWRITE_JPEG_QUALITY, 85])
        write_pts(out / "labels/train" / f"{stem}_W.txt", wpts); n_rf_warp += 1
    print(f"Roboflow breit übernommen: {n_rf_orig} Original + {n_rf_warp} gehärtet", flush=True)

    # 5) gezielt: dd6 über ALLE Roboflow-Bilder laufen lassen, die 3000 schwersten extra-hart machen
    dd6 = YOLO(str(dd6_path))
    scored = []
    for i, (img, lab) in enumerate(pool):
        if i % 3000 == 0: print(f"  Mining {i}/{len(pool)} …", flush=True)
        pts = read_pts(lab); gt = [(x, y) for c, x, y in pts if c == 4]
        if not gt: continue
        res = dd6.predict(str(img), imgsz=800, conf=0.3, device=0, verbose=False)[0]
        pred = [(float(x), float(y)) for c, (x, y, _, _) in zip(res.boxes.cls, res.boxes.xywhn) if int(c) == 4]
        used, fn = set(), 0
        for g in gt:
            cands = [(((p[0]-g[0])**2+(p[1]-g[1])**2)**0.5, i2) for i2, p in enumerate(pred) if i2 not in used]
            best = min(cands) if cands else None
            if best is not None and best[0] <= 10/800: used.add(best[1])
            else: fn += 1
        fp = len(pred) - len(used)
        score = fn + fp + (2 if is_hard(pts) else 0)
        if score: scored.append((score, img, lab, pts))
    del dd6
    scored.sort(key=lambda t: -t[0])
    mined = scored[:3000]
    print(f"gemint: {len(mined)} von {len(pool)} geprüft", flush=True)
    n_extra = 0
    for _, img, lab, pts in mined:
        base = cv2.imread(str(img))
        if base is None: continue
        wimg, wpts = warp_image(base, pts, rng, yaw=(55, 70))
        if wimg is None: continue
        dimg = degrade_image(wimg, rng)
        stem = f"rf9x_{img.stem}"
        cv2.imwrite(str(out / "images/train" / f"{stem}.jpg"), dimg, [cv2.IMWRITE_JPEG_QUALITY, 85])
        write_pts(out / "labels/train" / f"{stem}.txt", wpts); n_extra += 1
    print(f"extra-hart übernommen: {n_extra}", flush=True)
    shutil.rmtree(rf_dir, ignore_errors=True)

    (out / "data.yaml").write_text("path: .\ntrain: images/train\nval: images/val\n" + NAMES)
    n_train = sum(1 for _ in (out / "images/train").glob("*"))
    print(f"data9 fertig: {n_train} train ({n_old} data6 + {n_rf_orig+n_rf_warp} Roboflow breit + {n_extra} extra-hart) / {n_val} val", flush=True)

    # ---- Training: Warmstart aus dd6 (dd8 auf Kaggle ausgefallen) ----
    work = Path("/vol/runs/dd9"); work.mkdir(parents=True, exist_ok=True)
    D2_WEIGHT = 2
    if D2_WEIGHT > 1:
        for img in list((out / "images/train").glob("dd_d2*")):
            lab = out / "labels/train" / (img.stem + ".txt")
            for k in range(1, D2_WEIGHT):
                shutil.copy(img, img.with_name(f"{img.stem}_x{k}{img.suffix}"))
                if lab.exists(): shutil.copy(lab, lab.with_name(f"{img.stem}_x{k}.txt"))
    n_train_final = sum(1 for _ in (out / "images/train").glob("*"))

    def tip_metrics(m, prefix="", tol_px=10.0):
        tol = tol_px / 800; tp = fp = fn = 0
        for img in sorted((out / "images/val").glob(prefix + "*")):
            lab = out / "labels/val" / (img.stem + ".txt")
            gt = [tuple(map(float, l.split()[1:3])) for l in lab.read_text().splitlines() if l.startswith("4 ")] if lab.exists() else []
            r = m.predict(str(img), imgsz=800, conf=0.3, device=0, verbose=False)[0]
            pred = [(float(x), float(y)) for c, (x, y, _, _) in zip(r.boxes.cls, r.boxes.xywhn) if int(c) == 4]
            used = set()
            for g in gt:
                cands = [(((p[0]-g[0])**2+(p[1]-g[1])**2)**0.5, i) for i, p in enumerate(pred) if i not in used]
                best = min(cands) if cands else None
                if best is not None and best[0] <= tol: tp += 1; used.add(best[1])
                else: fn += 1
            fp += len(pred) - len(used)
        return dict(tip_recall_10px=tp/max(1,tp+fn), tip_precision_10px=tp/max(1,tp+fp), tips_gt=tp+fn, tips_pred=tp+fp)

    def evaluate(weights):
        m = YOLO(weights)
        v = m.val(data=str(out / "data.yaml"), imgsz=800, batch=16, device=0, plots=False, verbose=False)
        o = dict(mAP50=float(v.box.map50), mAP50_95=float(v.box.map), precision=float(v.box.mp), recall=float(v.box.mr))
        o.update(tip_metrics(m)); o.update({k+"_d2": v for k, v in tip_metrics(m, "dd_d2").items()})
        return o

    baseline = evaluate(str(dd6_path)); print("Baseline (dd6):", json.dumps(baseline), flush=True)
    model = YOLO(str(dd6_path))
    hp = dict(lr0=0.0003, lrf=0.01, cos_lr=True, momentum=0.90098, weight_decay=0.00038, warmup_epochs=1.0, warmup_momentum=0.43,
              box=2.99452, cls=0.30763, dfl=1.53753, hsv_h=0.05, hsv_s=0.6, hsv_v=0.24372, degrees=15.58584,
              translate=0.10067, scale=0.2181, shear=0.0, perspective=0.0, flipud=0.0, fliplr=0.0, mosaic=0.6, mixup=0.0)
    model.train(data=str(out / "data.yaml"), epochs=30, time=time_h, imgsz=800, batch=16, device=0, workers=4, freeze=5,
                patience=10, cache="ram", plots=False, project="/tmp/runs", name="ft", exist_ok=True, pretrained=True, seed=0, **hp)
    run = Path(model.trainer.save_dir)
    best = run / "weights/best.pt" if (run / "weights/best.pt").exists() else run / "weights/last.pt"
    result = evaluate(str(best))
    metrics = dict(name="dd9", data="data9", base="dd6", train_images=n_train_final, val_images=n_val, baseline=baseline, result=result,
                   better=result["tip_recall_10px"] >= baseline["tip_recall_10px"] and result["tip_precision_10px"] >= baseline["tip_precision_10px"])
    print(json.dumps(metrics, indent=1), flush=True)
    shutil.copy(best, work / "best.pt")
    if (run / "results.csv").exists(): shutil.copy(run / "results.csv", work / "results.csv")
    (work / "metrics.json").write_text(json.dumps(metrics, indent=1))
    vol.commit()
    return metrics


@app.local_entrypoint()
def main(time_h: float = 5.0, out: str = str(tools / "finetune/dd9")):
    urls = kaggle_urls({"scorelens-build-data6"})
    data6_url = urls["scorelens-build-data6"]["data6.zip"]
    dd6_url = urls["scorelens-build-data6"]["kg_dd6/best.pt"]
    metrics = build_and_train.remote(data6_url, dd6_url, time_h)
    o = Path(out); o.mkdir(parents=True, exist_ok=True)
    (o / "metrics.json").write_text(json.dumps(metrics, indent=1))
    print(json.dumps(metrics, indent=1))
    print(f"→ {o}/ (best.pt/results.csv per `modal volume get scorelens-ft runs/dd9 {o}`)")
