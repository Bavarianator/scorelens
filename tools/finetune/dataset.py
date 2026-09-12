"""
Datensatzbau für das Feintuning, ohne Modal-Abhängigkeit (läuft lokal, auf Modal und als Kaggle-Kernel).

- warp_image:     synthetische Schrägsicht (Board-Ebene 25–50° gedreht), Labels per Homographie
- degrade_image:  "reales Leben": dunkler Raum, Lampenlicht, Bewegungsunschärfe, Rauschen, JPEG, Schatten, Vignette
- is_hard:        Schwachstellen aus der Fehleranalyse (3 Darts, enge Spitzen, Randtreffer)
- convert_deepdarts: labels.pkl + Bilder → YOLO-Labels (7 Klassen wie dart-sense), 9/15 per Modell
- augment_dataset: kopieren + Schrägsichten + harte Fälle vermehren + Verderbung
- build_hard_val:  hartes Benchmark-Set aus einem Val-Split (D2, starke Schräge, verdorben)
"""
import hashlib, json, random, re, shutil
from pathlib import Path

NAMES = "names:\n  0: '20'\n  1: '3'\n  2: '11'\n  3: '6'\n  4: 'dart'\n  5: '9'\n  6: '15'\n"


def split_of(name, val_frac=0.1):
    return "val" if int(hashlib.md5(name.encode()).hexdigest(), 16) % 1000 < val_frac * 1000 else "train"


def read_pts(lab):
    return [(int(l.split()[0]), float(l.split()[1]), float(l.split()[2])) for l in Path(lab).read_text().splitlines() if l.strip()] if Path(lab).exists() else []


def write_pts(lab, pts):
    Path(lab).write_text("".join(f"{c} {x:.6f} {y:.6f} 0.025 0.025\n" for c, x, y in pts))


def warp_image(src, pts, rng, yaw=(25, 50)):
    """Board-Ebene um ihr Zentrum drehen (Yaw seitlich, Pitch ±12°, Roll ±5°) und mit f = Bildbreite neu projizieren;
    danach so skalieren, dass alle Punkte mit Rand im Bild bleiben. Gibt (Bild BGR, Punkte) oder (None, None)."""
    import cv2, numpy as np
    img = src if not isinstance(src, (str, Path)) else cv2.imread(str(src)); h, w = img.shape[:2]
    a = np.radians(rng.uniform(*yaw) * rng.choice((-1, 1))); p = np.radians(rng.uniform(-12, 12)); r = np.radians(rng.uniform(-5, 5))
    f = float(w); cx, cy = w / 2, h / 2
    Ry = np.array([[np.cos(a), 0, np.sin(a)], [0, 1, 0], [-np.sin(a), 0, np.cos(a)]])
    Rx = np.array([[1, 0, 0], [0, np.cos(p), -np.sin(p)], [0, np.sin(p), np.cos(p)]])
    Rz = np.array([[np.cos(r), -np.sin(r), 0], [np.sin(r), np.cos(r), 0], [0, 0, 1]])
    R = Rz @ Rx @ Ry

    def project(uv):
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
    return cv2.warpPerspective(img, H, (w, h), borderMode=cv2.BORDER_REPLICATE), [(c, float(q[0] / w), float(q[1] / h)) for (c, _, _), q in zip(pts, Q)]


def degrade_image(img, rng):
    """Photometrische Verderbung wie bei Handyfotos im Wohnzimmer: 2–4 zufällige Effekte. Labels bleiben gültig."""
    import cv2, numpy as np
    out = img.astype(np.float32)
    h, w = out.shape[:2]
    effects = rng.sample(["dark", "warm", "blur", "noise", "jpeg", "shadow", "vignette", "contrast"], k=rng.randint(2, 4))
    if "dark" in effects: out *= rng.uniform(0.35, 0.75)
    if "contrast" in effects: out = (out - 128) * rng.uniform(0.6, 1.3) + 128
    if "warm" in effects:  # Glühlampe / Kaltlicht: Kanäle verschieben (BGR)
        t = rng.uniform(-1, 1); out[..., 0] *= 1 - 0.18 * t; out[..., 2] *= 1 + 0.18 * t
    if "shadow" in effects:  # weicher Schattenverlauf über eine Bildhälfte (Spieler/Lampe)
        yy, xx = np.mgrid[0:h, 0:w]; ang = rng.uniform(0, 2 * np.pi)
        ramp = (np.cos(ang) * (xx - w / 2) + np.sin(ang) * (yy - h / 2)) / w
        out *= (1 - rng.uniform(0.3, 0.6) * np.clip(ramp + 0.5, 0, 1))[..., None]
    if "vignette" in effects:
        yy, xx = np.mgrid[0:h, 0:w]; d = np.sqrt((xx - w / 2) ** 2 + (yy - h / 2) ** 2) / (w / 2)
        out *= (1 - rng.uniform(0.25, 0.5) * np.clip(d - 0.5, 0, 1))[..., None]
    out = np.clip(out, 0, 255).astype(np.uint8)
    if "blur" in effects:
        if rng.random() < 0.5: out = cv2.GaussianBlur(out, (0, 0), rng.uniform(0.8, 2.2))
        else:  # Bewegungsunschärfe
            k = rng.randint(5, 13); ker = np.zeros((k, k), np.float32); ker[k // 2, :] = 1 / k
            M = cv2.getRotationMatrix2D((k / 2, k / 2), rng.uniform(0, 180), 1); ker = cv2.warpAffine(ker, M, (k, k)); ker /= ker.sum() or 1
            out = cv2.filter2D(out, -1, ker)
    if "noise" in effects:
        out = np.clip(out.astype(np.float32) + np.random.default_rng(rng.randint(0, 1 << 30)).normal(0, rng.uniform(4, 14), out.shape), 0, 255).astype(np.uint8)
    if "jpeg" in effects:
        ok, buf = cv2.imencode(".jpg", out, [cv2.IMWRITE_JPEG_QUALITY, rng.randint(35, 65)]); out = cv2.imdecode(buf, cv2.IMREAD_COLOR)
    return out


def is_hard(pts):
    """Schwachstellen aus der Fehleranalyse: drei Darts, Spitzen < 0,06 auseinander oder Dart > 0,35 vom Board-Zentrum."""
    darts = [(x, y) for c, x, y in pts if c == 4]
    cal = [(x, y) for c, x, y in pts if c < 4]
    if len(darts) >= 3: return True
    if any(((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2) ** 0.5 < 0.06 for i, a in enumerate(darts) for b in darts[i + 1:]): return True
    cx = sum(x for x, _ in cal) / len(cal) if cal else 0.5; cy = sum(y for _, y in cal) / len(cal) if cal else 0.5
    return any(((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 > 0.35 for x, y in darts)


def convert_deepdarts(pkl, images_root, out, model=None, device=0, folders=None, val_frac=0.1, log=print):
    """labels.pkl (img_folder, img_name, xy: 4 Kalibrierpunkte 20/3/11/6, dann Darts) → out/images|labels/{train,val}.
    9/15 ergänzt das Modell (Klassen 5/6, conf ≥ 0,5)."""
    import pandas as pd
    df = pd.read_pickle(pkl)
    if folders is not None: df = df[df.img_folder.isin(folders)]
    out = Path(out)
    for sp in ("train", "val"): (out / "images" / sp).mkdir(parents=True, exist_ok=True); (out / "labels" / sp).mkdir(parents=True, exist_ok=True)
    counts = {"train": 0, "val": 0, "missing": 0, "d2": 0}
    for i, row in enumerate(df.itertuples()):
        src = Path(images_root) / row.img_folder / row.img_name
        if not src.exists(): counts["missing"] += 1; continue
        pts = [(min(j, 4), float(p[0]), float(p[1])) for j, p in enumerate(row.xy) if not (float(p[0]) <= 0 and float(p[1]) <= 0)]
        if model is not None:
            r = model.predict(str(src), imgsz=800, conf=0.5, device=device, verbose=False)[0]
            pts += [(int(c), float(x), float(y)) for c, (x, y, _, _) in zip(r.boxes.cls, r.boxes.xywhn) if int(c) in (5, 6)]
        name = f"dd_{row.img_folder}_{Path(row.img_name).stem}"
        sp = split_of(name, val_frac)
        shutil.copy(src, out / "images" / sp / (name + src.suffix.lower())); write_pts(out / "labels" / sp / (name + ".txt"), pts)
        counts[sp] += 1; counts["d2"] += row.img_folder.startswith("d2")
        if i % 1000 == 0: log(f"{i}/{len(df)} …")
    (out / "data.yaml").write_text("path: .\ntrain: images/train\nval: images/val\n" + NAMES)
    (out / "counts.json").write_text(json.dumps(counts))
    return counts


def augment_dataset(src_dir, out, warps_d1=1, warps_d2=3, hard_warps=0, hard_dup=0, hard_list=None, degrade_frac=0.0, seed=1, log=print):
    """Kopiert src_dir nach out und hängt an: Schrägsichten (D2-Bilder warps_d2-mal, sonst warps_d1-mal), für harte Bilder
    (is_hard oder Mining-Liste; gemined = zusätzlich 2 Kopien + 2 Varianten) hard_warps Varianten und hard_dup Kopien,
    und für degrade_frac der geschriebenen Trainingsbilder eine verdorbene Fassung. Val bleibt unverändert."""
    import cv2
    src_dir, out = Path(src_dir), Path(out)
    rng = random.Random(seed)
    if out.exists(): shutil.rmtree(out)
    mined = {re.sub(r"_w\d+$", "", Path(r["name"]).stem) for r in json.loads(Path(hard_list).read_text())["hard"]} if hard_list else set()
    counts = {"train": 0, "val": 0, "warped": 0, "d2": 0, "hard": 0, "mined": 0, "degraded": 0}
    (out / "review").mkdir(parents=True)

    def emit(sp, stem, img, pts):
        cv2.imwrite(str(out / "images" / sp / (stem + ".jpg")), img, [cv2.IMWRITE_JPEG_QUALITY, 88]); write_pts(out / "labels" / sp / (stem + ".txt"), pts)
        if sp == "train" and degrade_frac and rng.random() < degrade_frac:
            cv2.imwrite(str(out / "images" / sp / (stem + "_d.jpg")), degrade_image(img, rng), [cv2.IMWRITE_JPEG_QUALITY, 88]); write_pts(out / "labels" / sp / (stem + "_d.txt"), pts)
            counts["degraded"] += 1
            if counts["degraded"] <= 6: shutil.copy(out / "images" / sp / (stem + "_d.jpg"), out / "review" / (stem + "_d.jpg"))

    for sp in ("train", "val"):
        (out / "images" / sp).mkdir(parents=True); (out / "labels" / sp).mkdir(parents=True)
        for img in sorted((src_dir / "images" / sp).glob("*")):
            pts = read_pts(src_dir / "labels" / sp / (img.stem + ".txt"))
            shutil.copy(img, out / "images" / sp / img.name); write_pts(out / "labels" / sp / (img.stem + ".txt"), pts)
            counts[sp] += 1; counts["d2"] += img.name.startswith("dd_d2")
            if sp != "train": continue
            base = cv2.imread(str(img))
            if degrade_frac and rng.random() < degrade_frac:
                emit(sp, img.stem + "_d", degrade_image(base, rng), pts); counts["degraded"] += 1
            mined_hit = img.stem in mined; hard = mined_hit or is_hard(pts)
            if hard:
                counts["hard"] += 1; counts["mined"] += mined_hit
                for k in range(hard_dup + (2 if mined_hit else 0)):
                    shutil.copy(img, out / "images" / sp / f"{img.stem}_h{k}{img.suffix}"); write_pts(out / "labels" / sp / f"{img.stem}_h{k}.txt", pts)
            n_w = (warps_d2 if img.name.startswith("dd_d2") else warps_d1) + (hard_warps if hard else 0) + (2 if mined_hit else 0)
            for k in range(n_w):
                wimg, wpts = warp_image(base, pts, rng)
                if wimg is None: continue
                emit(sp, f"{img.stem}_w{k}", wimg, wpts); counts["warped"] += 1
                if counts["warped"] <= 4: shutil.copy(out / "images" / sp / f"{img.stem}_w{k}.jpg", out / "review" / f"{img.stem}_w{k}.jpg")
            if (counts["train"] + counts["val"]) % 1000 == 0: log(counts)
    shutil.copy(src_dir / "data.yaml", out / "data.yaml")
    (out / "counts.json").write_text(json.dumps(counts))
    log(f"fertig: {counts}")
    return counts


def build_hard_val(src_dir, out, seed=7, log=print):
    """Hartes Benchmark-Set aus einem Val-Split: alle D2-Originale, dazu je Val-Bild eine starke Schrägsicht (45–60°)
    und eine verdorbene Fassung (Original oder Schrägsicht). Dieselben Labels-Regeln, eigene data.yaml (val = alles)."""
    import cv2
    src_dir, out = Path(src_dir), Path(out)
    rng = random.Random(seed)
    if out.exists(): shutil.rmtree(out)
    (out / "images/val").mkdir(parents=True); (out / "labels/val").mkdir(parents=True)
    n = {"d2": 0, "warp": 0, "degraded": 0}
    for img in sorted((src_dir / "images/val").glob("*")):
        pts = read_pts(src_dir / "labels/val" / (img.stem + ".txt")); base = cv2.imread(str(img))
        if img.name.startswith("dd_d2"):
            shutil.copy(img, out / "images/val" / img.name); write_pts(out / "labels/val" / (img.stem + ".txt"), pts); n["d2"] += 1
        wimg, wpts = warp_image(base, pts, rng, yaw=(45, 60))
        if wimg is not None:
            cv2.imwrite(str(out / "images/val" / f"{img.stem}_W.jpg"), wimg, [cv2.IMWRITE_JPEG_QUALITY, 90]); write_pts(out / "labels/val" / f"{img.stem}_W.txt", wpts); n["warp"] += 1
        dimg, dpts = (wimg, wpts) if wimg is not None and rng.random() < 0.5 else (base, pts)
        cv2.imwrite(str(out / "images/val" / f"{img.stem}_D.jpg"), degrade_image(dimg, rng), [cv2.IMWRITE_JPEG_QUALITY, 88]); write_pts(out / "labels/val" / f"{img.stem}_D.txt", dpts); n["degraded"] += 1
    (out / "data.yaml").write_text("path: .\ntrain: images/val\nval: images/val\n" + NAMES)
    (out / "counts.json").write_text(json.dumps(n))
    log(f"val_hard: {n}")
    return n


if __name__ == "__main__":  # Selbsttest: Warp und Verderbung erhalten die Punktzahl, Bild bleibt 800×800
    import numpy as np
    img = np.full((800, 800, 3), 120, np.uint8); pts = [(0, .5, .1), (1, .5, .9), (2, .1, .5), (3, .9, .5), (4, .55, .5)]
    w, q = warp_image(img, pts, random.Random(0)); assert w.shape == (800, 800, 3) and len(q) == 5 and all(0 <= x <= 1 and 0 <= y <= 1 for _, x, y in q)
    d = degrade_image(img, random.Random(0)); assert d.shape == (800, 800, 3) and d.dtype == np.uint8
    assert is_hard([(4, .5, .5), (4, .52, .5)]) and not is_hard([(4, .5, .5)] + [(c, .5, .5) for c in range(4)])
    print("dataset.py ok")
