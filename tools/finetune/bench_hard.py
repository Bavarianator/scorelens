"""Harter Benchmark mit mehr Kriterien auf denselben 615 Bildern (bench_hard aus dataset.build_bench_hard):
eine Inferenz je Modell mit conf 0,1, daraus ohne Neu-Inferenz
- Spitzen-Recall/Precision über Toleranz (5/10/15 px) × Konfidenz (0,15–0,5)
- Kalibrierpunkte: Anteil Bilder mit allen 4 Punkten, Trefferquote je Punkt
- Score-Pipeline wie in der App (benchmark.py: Kalibrierung → Homografie → Score) bei Kalibrier-Schwelle 0,6 und 0,85
- Fehleraufschlüsselung wie analyze.py (Darts je Bild, Spitzenabstand, Helligkeit, Randnähe, D1/D2)
- Laufzeit je Bild
Aufruf über bench_hard_modal.py (Modal T4) oder direkt: criteria(preds, gts, data_dir, modell) mit preds aus benchmark.run.
"""
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
from PIL import Image

import benchmark

TOLS = (5, 10, 15)
CONFS = (0.15, 0.25, 0.3, 0.4, 0.5)
APP_CONF, APP_TOL = 0.3, 10


def match(gt, pred, tol_px):
    """Greedy-Zuordnung wahre Spitze → nächste Vorhersage innerhalb tol_px (bei 800 px). Gibt Anzahl Treffer."""
    used, tp = set(), 0
    for g in gt:
        cands = [(((p[0] - g[0]) ** 2 + (p[1] - g[1]) ** 2) ** 0.5, i) for i, p in enumerate(pred) if i not in used]
        best = min(cands) if cands else None
        if best is not None and best[0] <= tol_px / 800: tp += 1; used.add(best[1])
    return tp


def criteria(preds, gts, data_dir, modell):
    data_dir = Path(data_dir)
    P = [p for p in preds if p["modell"] == modell]
    agg = {c: {t: [0, 0, 0] for t in TOLS} for c in CONFS}  # tp, fn, fp
    rows, cal_hits, cal_all4 = [], Counter(), 0
    for p in P:
        stem = Path(p["bild"]).stem
        pts = benchmark.read_labels(data_dir / "labels/val" / (stem + ".txt"))
        gt = [(x, y) for c, x, y in pts if c == 4]; cal = {c: (x, y) for c, x, y in pts if c < 4}
        darts = [(x, y, cf) for c, x, y, cf in p["rows"] if c == 4]
        for conf in CONFS:
            pred = [(x, y) for x, y, cf in darts if cf >= conf]
            for t in TOLS:
                tp = match(gt, pred, t); a = agg[conf][t]; a[0] += tp; a[1] += len(gt) - tp; a[2] += len(pred) - tp
        # Kalibrierpunkte (App-Schwelle): je Klasse die sicherste Vorhersage, Treffer wenn < 10 px
        pcal = {}
        for c, x, y, cf in p["rows"]:
            if c < 4 and cf >= APP_CONF and c not in pcal: pcal[c] = (x, y)
        hit = {c for c, g in cal.items() if c in pcal and ((pcal[c][0] - g[0]) ** 2 + (pcal[c][1] - g[1]) ** 2) ** 0.5 <= APP_TOL / 800}
        for c in hit: cal_hits[c] += 1
        cal_all4 += len(hit) == 4
        # Attribute für die Aufschlüsselung (bei App-Schwelle, 10 px)
        pred = [(x, y) for x, y, cf in darts if cf >= APP_CONF]; tp = match(gt, pred, APP_TOL)
        mind = min((((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2) ** 0.5 for i, a in enumerate(gt) for b in gt[i + 1:]), default=1.0)
        bright = float(np.asarray(Image.open(data_dir / "images/val" / p["bild"]).convert("L")).mean())
        cx = np.mean([g[0] for g in cal.values()]) if cal else 0.5; cy = np.mean([g[1] for g in cal.values()]) if cal else 0.5
        edge = max((((g[0] - cx) ** 2 + (g[1] - cy) ** 2) ** 0.5 for g in gt), default=0.0)
        rows.append(dict(d2=p["bild"].startswith("dd_d2"), n=len(gt), fn=len(gt) - tp, fp=len(pred) - tp, mind=mind, bright=bright, edge=edge, cal4=len(hit) == 4))

    def group(key, fmt=lambda v: str(v)):
        g = defaultdict(lambda: [0, 0, 0, 0])
        for r in rows:
            k = fmt(key(r)); g[k][0] += 1; g[k][1] += r["n"]; g[k][2] += r["fn"]; g[k][3] += r["fp"]
        return {k: dict(bilder=v[0], spitzen=v[1], recall=round(1 - v[2] / max(1, v[1]), 3), fp_je_bild=round(v[3] / max(1, v[0]), 3)) for k, v in sorted(g.items())}

    spitzen = {f"conf{c}": {f"{t}px": dict(recall=round(a[0] / max(1, a[0] + a[1]), 3), precision=round(a[0] / max(1, a[0] + a[2]), 3), tp=a[0], fn=a[1], fp=a[2])
                            for t, a in ts.items()} for c, ts in agg.items()}
    # Score-Pipeline wie in der App: Darts ab conf 0,3, Kalibrier-Schwelle variabel
    P03 = [dict(p, rows=[r for r in p["rows"] if r[3] >= APP_CONF]) for p in P]
    score = {}
    for cal_conf in (0.6, 0.85):
        ev = benchmark.evaluate(P03, gts, cal_conf)
        errs = [r["fehler"] for r in ev if r["fehler"] is not None]
        score[f"cal{cal_conf}"] = dict(kalibriert=round(benchmark.mean([r["kalibriert"] for r in ev]), 3), score_exakt=round(benchmark.mean([r["score_exakt"] for r in ev]), 3),
                                       darts_exakt=round(benchmark.mean([r["darts_exakt"] for r in ev]), 3), dart_treffer=round(benchmark.mean([r["dart_treffer"] for r in ev]), 3),
                                       fehler_mittel=round(benchmark.mean(errs), 2) if errs else None, fehlerarten=dict(Counter(r["fehlerart"] for r in ev)))
    ys = sorted(p["ms_yolo"] for p in P)
    laufzeit = dict(ms_mittel=round(benchmark.mean(ys), 1), ms_median=round(ys[len(ys) // 2], 1), ms_p95=round(ys[int(0.95 * (len(ys) - 1))], 1),
                    ms_inferenz=round(benchmark.mean([p["ms_inf"] for p in P]), 1))
    n = len(rows)
    return dict(modell=modell, bilder=n, spitzen=spitzen,
                kalibrierung=dict(alle4=round(cal_all4 / max(1, n), 3), je_punkt={benchmark.scorer.class_names[c]: round(cal_hits[c] / max(1, n), 3) for c in range(4)}),
                score=score,
                aufschluesselung=dict(quelle=group(lambda r: "D2 seitlich" if r["d2"] else "D1 frontal"), darts_im_bild=group(lambda r: r["n"]),
                                      spitzenabstand=group(lambda r: r["mind"], lambda v: "<24px" if v < 0.03 else "<48px" if v < 0.06 else ">=48px"),
                                      helligkeit=group(lambda r: r["bright"], lambda v: "<70" if v < 70 else "<110" if v < 110 else ">=110"),
                                      abstand_zentrum=group(lambda r: r["edge"], lambda v: "<0.2" if v < 0.2 else "<0.35" if v < 0.35 else ">=0.35"),
                                      kalibriert=group(lambda r: "alle 4 Punkte" if r["cal4"] else "Punkt fehlt")),
                laufzeit=laufzeit)


def report(res):
    pct = lambda x: f"{100 * x:.1f} %"
    L = [f"# Harter Benchmark · {res['modell']} · {res['bilder']} Bilder", ""]
    L += ["## Spitzen: Recall / Precision nach Konfidenz × Toleranz", "", "| conf | " + " | ".join(f"{t} px" for t in TOLS) + " |", "|---|" + "--:|" * len(TOLS)]
    for c in CONFS:
        L.append(f"| {c} | " + " | ".join(f"{pct(res['spitzen'][f'conf{c}'][f'{t}px']['recall'])} / {pct(res['spitzen'][f'conf{c}'][f'{t}px']['precision'])}" for t in TOLS) + " |")
    k = res["kalibrierung"]
    L += ["", "## Kalibrierpunkte (conf 0,3, 10 px)", "", f"Alle 4 gefunden: **{pct(k['alle4'])}** · je Punkt: " + ", ".join(f"{n} {pct(v)}" for n, v in k["je_punkt"].items())]
    L += ["", "## Score-Pipeline wie in der App", "", "| Kalibrier-Schwelle | kalibriert | Score exakt | alle Darts exakt | Dart-Treffer | Fehler Ø | Fehlerarten |", "|---|--:|--:|--:|--:|--:|---|"]
    for cc, s in res["score"].items():
        L.append(f"| {cc[3:]} | {pct(s['kalibriert'])} | {pct(s['score_exakt'])} | {pct(s['darts_exakt'])} | {pct(s['dart_treffer'])} | {s['fehler_mittel']} | " + ", ".join(f"{a} {b}" for a, b in sorted(s["fehlerarten"].items(), key=lambda t: -t[1])) + " |")
    L += ["", "## Aufschlüsselung (conf 0,3, 10 px)", ""]
    for name, g in res["aufschluesselung"].items():
        L += [f"**{name}**: " + " · ".join(f"{k}: {pct(v['recall'])} Recall, {v['fp_je_bild']} FP/Bild ({v['bilder']})" for k, v in g.items())]
    t = res["laufzeit"]
    L += ["", f"## Laufzeit: Ø {t['ms_mittel']} ms/Bild, Median {t['ms_median']}, p95 {t['ms_p95']} (Inferenz {t['ms_inferenz']} ms)"]
    return "\n".join(L)


if __name__ == "__main__":  # Selbsttest der Zuordnung
    assert match([(0.5, 0.5), (0.6, 0.6)], [(0.5, 0.5 + 4 / 800), (0.9, 0.9)], 10) == 1
    assert match([(0.5, 0.5)], [(0.5, 0.5 + 12 / 800)], 10) == 0 and match([(0.5, 0.5)], [(0.5, 0.5 + 12 / 800)], 15) == 1
    print("bench_hard.py ok")
