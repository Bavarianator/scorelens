"""Harter Benchmark (bench_hard.py) auf Modal (T4, Cent-Beträge statt Kaggle-Kontingent).
Bilder und Gewichte kommen beim ersten Mal direkt von Kaggle (signierte URLs über die Kaggle-API) ins Volume scorelens-ft
(/vol/bench_hard, /vol/runs/<name>/best.pt) und bleiben dort für spätere Läufe.

  venv/bin/modal run tools/finetune/bench_hard_modal.py --models dd6          # nur dd6
  venv/bin/modal run tools/finetune/bench_hard_modal.py --models dd6,dd7,base # base liegt schon im Volume

Ergebnis: ~/freedarts-tools/finetune/bench_hard/<modell>.{json,md}
"""
import json
from pathlib import Path

import modal

here = Path(__file__).resolve().parent
tools = Path.home() / "freedarts-tools"
app = modal.App("scorelens-bench-hard")
vol = modal.Volume.from_name("scorelens-ft")
image = (
    modal.Image.debian_slim(python_version="3.11")
    .apt_install("libgl1", "libglib2.0-0")
    .pip_install("ultralytics>=8.3,<9", "opencv-python-headless")
    .add_local_file(tools / "finetune/benchmark.py", "/root/finetune/benchmark.py")
    .add_local_file(tools / "dartsense/get_scores.py", "/root/dartsense/get_scores.py")
    .add_local_file(here / "bench_hard.py", "/root/finetune/bench_hard.py")
)
# Kaggle-Kernel und Pfad, aus dem die Gewichte kommen (nur beim ersten Mal nötig)
KAGGLE = {"dd3": ("scorelens-finetune-dd3", "kg_dd3/best.pt"), "dd5": ("scorelens-finetune-dd5", "kg_dd5/best.pt"), "dd4": ("scorelens-finetune-dd4", "kg_dd4/best.pt"), "dd6": ("scorelens-build-data6", "kg_dd6/best.pt"), "dd7": ("scorelens-build-data7", "kg_dd7/best.pt")}


@app.function(image=image, gpu="T4", volumes={"/vol": vol}, timeout=30 * 60)
def bench(models: dict, zip_url: str):
    import sys, urllib.request, zipfile
    sys.path.insert(0, "/root/finetune")
    import benchmark, bench_hard
    if not Path("/vol/bench_hard/data.yaml").exists():
        urllib.request.urlretrieve(zip_url, "/tmp/bench_hard.zip")
        with zipfile.ZipFile("/tmp/bench_hard.zip") as zf: zf.extractall("/vol")
        vol.commit(); print("bench_hard ins Volume geladen", flush=True)
    paths = {}
    for name, url in models.items():
        dst = Path("/vol/runs") / name / "best.pt" if name != "base" else Path("/vol/base.pt")
        if url and not dst.exists():
            dst.parent.mkdir(parents=True, exist_ok=True); urllib.request.urlretrieve(url, dst); vol.commit(); print(f"{name} ins Volume geladen", flush=True)
        assert dst.exists(), f"{name}: keine Gewichte unter {dst} und keine URL"
        paths[name] = str(dst)
    preds, gts, meta = benchmark.run("/vol/bench_hard", paths, ["original"], 0.1, 0)
    return {n: bench_hard.criteria(preds, gts, "/vol/bench_hard", n) for n in paths}


def kaggle_urls(wanted):
    """Signierte Download-URLs aus den Kernel-Outputs (kernel → {dateipfad: url})."""
    from kaggle.api.kaggle_api_extended import KaggleApi
    from kagglesdk.kernels.types.kernels_api_service import ApiListKernelSessionOutputRequest
    api = KaggleApi(); api.authenticate(); out = {}
    with api.build_kaggle_client() as kg:
        for kernel in wanted:
            req = ApiListKernelSessionOutputRequest(); req.user_name = "mobayer"; req.kernel_slug = kernel; req.page_size = 100
            out[kernel] = {f.file_name: f.url for f in kg.kernels.kernels_api_client.list_kernel_session_output(req).files}
    return out


@app.local_entrypoint()
def main(models: str = "dd6", out: str = str(tools / "finetune/bench_hard")):
    names = models.split(",")
    urls = kaggle_urls({"scorelens-bench-hard"} | {KAGGLE[n][0] for n in names if n in KAGGLE})
    zip_url = urls["scorelens-bench-hard"]["bench_hard.zip"]
    req = {n: urls[KAGGLE[n][0]][KAGGLE[n][1]] if n in KAGGLE else "" for n in names}
    res = bench.remote(req, zip_url)
    o = Path(out); o.mkdir(parents=True, exist_ok=True)
    for n, r in res.items():
        (o / f"{n}.json").write_text(json.dumps(r, indent=1, ensure_ascii=False))
        (o / f"{n}.md").write_text(bench_hard_report(r) + "\n")
        print(bench_hard_report(r)); print()
    print(f"→ {o}/")


def bench_hard_report(r):
    import sys
    sys.path.insert(0, str(here)); sys.path.insert(0, str(tools / "finetune"))
    import bench_hard
    return bench_hard.report(r)
