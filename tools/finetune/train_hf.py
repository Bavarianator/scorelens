"""
Feintuning des dart-sense-YOLOv8n-Modells auf Hugging Face Jobs (CPU oder GPU).
Wird von finetune_hf.sh in den Datensatz-Repo hochgeladen und im Job per hf_hub_download gestartet.

Läuft in einem HF-Job, einem kostenlosen CPU-Space (tools/finetune/space/) oder in einem Kaggle/Colab-Notebook.

Umgebungsvariablen (vom Start-Skript gesetzt):
  DATASET_REPO   privates HF-Dataset mit images/, labels/, data.yaml (und dieser Datei)
  OUTPUT_REPO    privates HF-Model-Repo für last.pt / best.pt / Metriken
  BASE_WEIGHTS   Start-Gewichte: URL (Standard: dart-sense weights.pt) oder "hf:<repo>/<datei>"
  EPOCHS, BATCH, IMGSZ, FREEZE, PATIENCE, RESUME (1 = von last.pt im OUTPUT_REPO weitermachen), UPLOAD_EVERY
  FORCE=1        auch trainieren, wenn OUTPUT_REPO schon ein fertiges Ergebnis (metrics.json) enthält
  PAUSE_SPACE=1  nach dem Ende den eigenen Space (SPACE_ID) pausieren
  HF_TOKEN       als Secret
"""
import json, os, shutil, subprocess, sys, time, urllib.request
from pathlib import Path

try:
    from huggingface_hub import HfApi, hf_hub_download, snapshot_download
except ImportError:
    subprocess.run([sys.executable, "-m", "pip", "install", "-q", "-U", "huggingface_hub>=0.34"], check=True)
    from huggingface_hub import HfApi, hf_hub_download, snapshot_download  # noqa: E402

E = os.environ
DATASET_REPO, OUTPUT_REPO = E["DATASET_REPO"], E["OUTPUT_REPO"]
BASE = E.get("BASE_WEIGHTS", "https://raw.githubusercontent.com/bnww/dart-sense/main/weights.pt")
EPOCHS, BATCH, IMGSZ = int(E.get("EPOCHS", 30)), int(E.get("BATCH", 8)), int(E.get("IMGSZ", 800))
FREEZE, PATIENCE = int(E.get("FREEZE", 10)), int(E.get("PATIENCE", 10))
RESUME, UPLOAD_EVERY = E.get("RESUME", "1") == "1", int(E.get("UPLOAD_EVERY", 2))
CORES = int(E.get("CPU_CORES", os.cpu_count() or 2))
WORK = Path(E.get("WORK_DIR", "/tmp/ft")); WORK.mkdir(parents=True, exist_ok=True)
api = HfApi()
SPACE_ID = E.get("SPACE_ID")


def pause_space():
    if E.get("PAUSE_SPACE") == "1" and SPACE_ID:
        try:
            api.pause_space(SPACE_ID); print("Space pausiert:", SPACE_ID, flush=True)
        except Exception as ex:
            print("Space konnte nicht pausiert werden:", ex, flush=True)


# Schon fertig? (Space-Neustart nach abgeschlossenem Training)
if E.get("FORCE") != "1":
    try:
        if "metrics.json" in api.list_repo_files(OUTPUT_REPO):
            print("OUTPUT_REPO enthält bereits metrics.json – nichts zu tun (FORCE=1 erzwingt ein neues Training).", flush=True)
            pause_space(); sys.exit(0)
    except Exception:
        pass

# ---- Daten ----
data_dir = Path(snapshot_download(DATASET_REPO, repo_type="dataset", local_dir=WORK / "data"))
yaml_path = data_dir / "data.yaml"
yaml = yaml_path.read_text().replace("path: .", f"path: {data_dir}")
yaml_path.write_text(yaml)
n_train = len(list((data_dir / "images/train").glob("*")))
n_val = len(list((data_dir / "images/val").glob("*")))
print(f"Datensatz: {n_train} train / {n_val} val", flush=True)

# ---- Startgewichte / Resume ----
api.create_repo(OUTPUT_REPO, repo_type="model", private=True, exist_ok=True)
start = None; resume = False
if RESUME:
    try:
        start = hf_hub_download(OUTPUT_REPO, "last.pt", local_dir=WORK / "prev"); resume = True
        print("Setze last.pt aus", OUTPUT_REPO, "fort", flush=True)
    except Exception:
        pass
if start is None:
    if BASE.startswith("hf:"):
        repo, _, fname = BASE[3:].rpartition("/")
        start = hf_hub_download(repo, fname, local_dir=WORK / "base")
    else:
        start = str(WORK / "base.pt"); urllib.request.urlretrieve(BASE, start)

import torch  # noqa: E402
from ultralytics import YOLO  # noqa: E402
device = 0 if torch.cuda.is_available() else "cpu"
torch.set_num_threads(CORES)
print(f"Gerät: {device}, Threads: {CORES}, Start: {start}", flush=True)

model = YOLO(start)
last_upload = [0.0]


def on_fit_epoch_end(trainer):
    """Alle UPLOAD_EVERY Epochen last.pt sichern, damit ein Timeout nichts verliert."""
    ep = trainer.epoch + 1
    if ep % UPLOAD_EVERY: return
    last = Path(trainer.wdir) / "last.pt"
    if last.exists():
        api.upload_file(path_or_fileobj=str(last), path_in_repo="last.pt", repo_id=OUTPUT_REPO, commit_message=f"epoch {ep}")
        print(f"last.pt hochgeladen (Epoche {ep})", flush=True)


model.add_callback("on_fit_epoch_end", on_fit_epoch_end)

# Hyperparameter aus dart-sense (train_optimal.sh, per YOLO-Tuning gefunden); Feintuning mit kleinerer Lernrate.
hp = dict(lr0=0.0015, lrf=0.01, momentum=0.90098, weight_decay=0.00038, warmup_epochs=1.0, warmup_momentum=0.43,
          box=2.99452, cls=0.30763, dfl=1.53753, hsv_h=0.00695, hsv_s=0.45949, hsv_v=0.24372, degrees=15.58584,
          translate=0.10067, scale=0.2181, shear=0.0, perspective=0.0, flipud=0.0, fliplr=0.0, mosaic=0.6, mixup=0.0)
# fliplr=0: gespiegelte Boards vertauschen die Kalibrierklassen (11 ↔ 6) und wären falsch gelabelt.

t0 = time.time()
if resume:
    model.train(resume=True)
else:
    model.train(data=str(yaml_path), epochs=EPOCHS, imgsz=IMGSZ, batch=BATCH, device=device, workers=min(CORES, 4),
                freeze=FREEZE if FREEZE > 0 else None, patience=PATIENCE, cache="ram", plots=False, val=n_val > 0,
                project=str(WORK / "runs"), name="ft", exist_ok=True, pretrained=True, seed=0, deterministic=False, **hp)
print(f"Training fertig nach {(time.time() - t0) / 60:.1f} min", flush=True)

run = Path(model.trainer.save_dir)
best = run / "weights/best.pt" if (run / "weights/best.pt").exists() else run / "weights/last.pt"


def tip_metrics(m, data_dir: Path, imgsz: int, device, tol_px: float = 10.0):
    """Spitzen-Genauigkeit: GT-Dartpunkt gilt als getroffen, wenn eine Vorhersage (Klasse 4) näher als tol_px (von 800) liegt."""
    tol = tol_px / 800
    tp = fp = fn = 0
    for img in sorted((data_dir / "images/val").glob("*")):
        lab = data_dir / "labels/val" / (img.stem + ".txt")
        gt = [tuple(map(float, l.split()[1:3])) for l in lab.read_text().splitlines() if l.startswith("4 ")] if lab.exists() else []
        r = m.predict(str(img), imgsz=imgsz, conf=0.3, device=device, verbose=False)[0]
        pred = [(float(x), float(y)) for c, (x, y, _, _) in zip(r.boxes.cls, r.boxes.xywhn) if int(c) == 4]
        used = set()
        for g in gt:
            cands = [(((p[0] - g[0]) ** 2 + (p[1] - g[1]) ** 2) ** 0.5, i) for i, p in enumerate(pred) if i not in used]
            best_c = min(cands) if cands else None
            if best_c is not None and best_c[0] <= tol: tp += 1; used.add(best_c[1])
            else: fn += 1
        fp += len(pred) - len(used)
    return dict(tip_recall_10px=tp / max(1, tp + fn), tip_precision_10px=tp / max(1, tp + fp), tips_gt=tp + fn, tips_pred=tp + fp)


# ---- Bewertung: mAP + Spitzen-Genauigkeit (Dart-Punkt innerhalb 10 px von 800 = ~2 mm auf dem Board) ----
metrics = {"epochs": EPOCHS, "train_images": n_train, "val_images": n_val, "base": BASE, "freeze": FREEZE, "imgsz": IMGSZ}
if n_val > 0:
    m = YOLO(str(best))
    v = m.val(data=str(yaml_path), imgsz=IMGSZ, batch=BATCH, device=device, plots=False, verbose=False)
    metrics.update(mAP50=float(v.box.map50), mAP50_95=float(v.box.map), precision=float(v.box.mp), recall=float(v.box.mr))
    metrics.update(tip_metrics(m, data_dir, IMGSZ, device))
print(json.dumps(metrics, indent=1), flush=True)

# ---- Ergebnisse hochladen ----
out = WORK / "upload"; out.mkdir(exist_ok=True)
shutil.copy(best, out / "best.pt")
if (run / "weights/last.pt").exists(): shutil.copy(run / "weights/last.pt", out / "last.pt")
if (run / "results.csv").exists(): shutil.copy(run / "results.csv", out / "results.csv")
(out / "metrics.json").write_text(json.dumps(metrics, indent=1))
(out / "README.md").write_text(
    "---\nlicense: cc-by-nc-4.0\ntags: [yolov8, darts, scorelens]\n---\n"
    f"# Scorelens Dart-Modell (Feintuning)\n\nBasis: {BASE}\n\n```json\n{json.dumps(metrics, indent=1)}\n```\n"
    "Nur nicht-kommerziell (CC BY-NC 4.0, dart-sense / DeepDarts).\n")
api.upload_folder(folder_path=str(out), repo_id=OUTPUT_REPO, repo_type="model", commit_message="finetune result")
print("Hochgeladen nach", OUTPUT_REPO, flush=True)
pause_space()
