"""
Feintuning des dart-sense-YOLOv8n-Modells als Kaggle-Kernel (Gratis-GPU, 30 h/Woche). Gleiche Logik wie train_modal.py:
Baseline messen, trainieren, Spitzen-Metriken (gesamt und nur D2), Vergleich. Ergebnis in /kaggle/working/.

Kaggle-Dataset (privat): scorelens-data2 = data2 aus dem Modal-Volume (images/, labels/, data.yaml) + base.pt.
Start: kaggle kernels push -p tools/finetune/kaggle · Status: kaggle kernels status <user>/scorelens-finetune ·
Ergebnis: kaggle kernels output <user>/scorelens-finetune -p ~/freedarts-tools/finetune/kaggle
"""
import json, os, shutil, subprocess, sys, time
from pathlib import Path

EPOCHS, BATCH, IMGSZ = int(os.environ.get("EPOCHS", 30)), int(os.environ.get("BATCH", 16)), int(os.environ.get("IMGSZ", 800))
FREEZE, PATIENCE, NAME = int(os.environ.get("FREEZE", 0)), int(os.environ.get("PATIENCE", 8)), os.environ.get("NAME", "kg1")
# Feintuning-Regler: kleinere Lernrate + Cosinus-Abklingen bei freiem Backbone; D2 (schräg) doppelt im Training
LR0, COS_LR, D2_WEIGHT = float(os.environ.get("LR0", 0.0015)), os.environ.get("COS_LR", "0") == "1", int(os.environ.get("D2_WEIGHT", 1))
subprocess.run([sys.executable, "-m", "pip", "install", "-q", "ultralytics>=8.3,<9"], check=True)
import torch  # noqa: E402
assert torch.cuda.is_available(), "Keine CUDA-GPU – Kernel mit machine_shape NvidiaTeslaT4 pushen (tools/finetune/kaggle/push.sh)"
print("GPU:", torch.cuda.get_device_name(0), flush=True)
from ultralytics import YOLO  # noqa: E402

work = Path("/kaggle/working"); out = work / NAME; out.mkdir(parents=True, exist_ok=True)
# Daten kommen als Zip von Hugging Face (Kaggle-Dataset-Upload hängt von hier aus); DATA = Zipname ohne .zip, z. B. data2
DATA, REPO = os.environ.get("DATA", "data2"), os.environ.get("DATASET_REPO", "Bayernator/scorelens-data2")
subprocess.run([sys.executable, "-m", "pip", "install", "-q", "huggingface_hub"], check=True)
from huggingface_hub import hf_hub_download  # noqa: E402
import zipfile  # noqa: E402
import glob  # noqa: E402
found = glob.glob(f"/kaggle/input/**/{DATA}/data.yaml", recursive=True)  # Datensatz aus einem Build-Kernel (kernel_sources)
if found:
    data_dir = Path(found[0]).parent; print("Datensatz aus Kernel-Output:", data_dir, flush=True)
else:
    z = hf_hub_download(REPO, DATA + ".zip", repo_type="dataset", local_dir="/kaggle/tmp")
    with zipfile.ZipFile(z) as zf: zf.extractall("/kaggle/tmp")
    data_dir = Path("/kaggle/tmp") / DATA
hard_dir = next((Path(p).parent for p in glob.glob("/kaggle/input/**/val_hard/data.yaml", recursive=True)), None)
BASE_RUN = os.environ.get("BASE_RUN", "")  # z. B. kg_dd4: Warmstart aus dem Output des Kernels in kernel_sources; Baseline = dieses Modell
base = glob.glob(f"/kaggle/input/**/{BASE_RUN}/best.pt", recursive=True)[0] if BASE_RUN else hf_hub_download(REPO, "base.pt", repo_type="dataset", local_dir="/kaggle/tmp")
print("Startgewichte:", base, flush=True)
yaml_path = work / "data.yaml"
yaml_path.write_text((data_dir / "data.yaml").read_text().replace("path: .", f"path: {data_dir}"))
if D2_WEIGHT > 1:  # seltene, relevante Bilder öfter zeigen: D2-Originale und ihre Schrägsichten (D2_WEIGHT-1)-mal kopieren
    for img in list((data_dir / "images/train").glob("dd_d2*")):
        lab = data_dir / "labels/train" / (img.stem + ".txt")
        for k in range(1, D2_WEIGHT):
            shutil.copy(img, img.with_name(f"{img.stem}_x{k}{img.suffix}"))
            if lab.exists(): shutil.copy(lab, lab.with_name(f"{img.stem}_x{k}.txt"))
n_train = len(list((data_dir / "images/train").glob("*"))); n_val = len(list((data_dir / "images/val").glob("*")))
print(f"Datensatz: {n_train} train / {n_val} val", flush=True)


def tip_metrics(m, prefix="", tol_px=10.0, d=None):
    d = d or data_dir
    tol = tol_px / 800; tp = fp = fn = 0
    for img in sorted((d / "images/val").glob(prefix + "*")):
        lab = d / "labels/val" / (img.stem + ".txt")
        gt = [tuple(map(float, l.split()[1:3])) for l in lab.read_text().splitlines() if l.startswith("4 ")] if lab.exists() else []
        r = m.predict(str(img), imgsz=IMGSZ, conf=0.3, device=0, verbose=False)[0]
        pred = [(float(x), float(y)) for c, (x, y, _, _) in zip(r.boxes.cls, r.boxes.xywhn) if int(c) == 4]
        used = set()
        for g in gt:
            cands = [(((p[0] - g[0]) ** 2 + (p[1] - g[1]) ** 2) ** 0.5, i) for i, p in enumerate(pred) if i not in used]
            best = min(cands) if cands else None
            if best is not None and best[0] <= tol: tp += 1; used.add(best[1])
            else: fn += 1
        fp += len(pred) - len(used)
    return dict(tip_recall_10px=tp / max(1, tp + fn), tip_precision_10px=tp / max(1, tp + fp), tips_gt=tp + fn, tips_pred=tp + fp)


def evaluate(weights):
    m = YOLO(weights)
    v = m.val(data=str(yaml_path), imgsz=IMGSZ, batch=BATCH, device=0, plots=False, verbose=False)
    o = dict(mAP50=float(v.box.map50), mAP50_95=float(v.box.map), precision=float(v.box.mp), recall=float(v.box.mr))
    o.update(tip_metrics(m))
    if any((data_dir / "images/val").glob("dd_d2*")): o.update({k + "_d2": v for k, v in tip_metrics(m, "dd_d2").items()})
    if hard_dir is not None:  # hartes Benchmark-Set: starke Schräge + Verderbung, zusätzlich mit 5 px Toleranz
        o.update({k + "_hard": v for k, v in tip_metrics(m, d=hard_dir).items()})
        o.update({k + "_hard5px": v for k, v in tip_metrics(m, tol_px=5.0, d=hard_dir).items()})
    return o


baseline = evaluate(base); print("Baseline:", json.dumps(baseline), flush=True)
HSV_H, HSV_S = float(os.environ.get("HSV_H", 0.00695)), float(os.environ.get("HSV_S", 0.45949))  # Farbrobustheit: 0,05 / 0,6 gegen Auswendiglernen der Ringfarben
hp = dict(lr0=LR0, lrf=0.01, cos_lr=COS_LR, momentum=0.90098, weight_decay=0.00038, warmup_epochs=1.0, warmup_momentum=0.43,
          box=2.99452, cls=0.30763, dfl=1.53753, hsv_h=HSV_H, hsv_s=HSV_S, hsv_v=0.24372, degrees=15.58584,
          translate=0.10067, scale=0.2181, shear=0.0, perspective=0.0, flipud=0.0, fliplr=0.0, mosaic=0.6, mixup=0.0)
t0 = time.time()
model = YOLO(base)
model.train(data=str(yaml_path), epochs=EPOCHS, imgsz=IMGSZ, batch=BATCH, device=0, workers=4, freeze=FREEZE if FREEZE > 0 else None,
            patience=PATIENCE, cache="ram", plots=False, project=str(work / "runs"), name="ft", exist_ok=True, pretrained=True, seed=0, **hp)
print(f"Training fertig nach {(time.time() - t0) / 60:.1f} min", flush=True)
run = Path(model.trainer.save_dir)
best = run / "weights/best.pt" if (run / "weights/best.pt").exists() else run / "weights/last.pt"
result = evaluate(str(best))
metrics = dict(name=NAME, data=DATA, base=BASE_RUN or "dart-sense", epochs=EPOCHS, imgsz=IMGSZ, freeze=FREEZE, lr0=LR0, cos_lr=COS_LR, d2_weight=D2_WEIGHT, train_images=n_train, val_images=n_val, baseline=baseline, result=result,
               better=result["tip_recall_10px"] >= baseline["tip_recall_10px"] and result["tip_precision_10px"] >= baseline["tip_precision_10px"])
print(json.dumps(metrics, indent=1), flush=True)
shutil.copy(best, out / "best.pt")
if (run / "results.csv").exists(): shutil.copy(run / "results.csv", out / "results.csv")
(out / "metrics.json").write_text(json.dumps(metrics, indent=1))
shutil.rmtree(work / "runs", ignore_errors=True)  # Kaggle-Output klein halten
