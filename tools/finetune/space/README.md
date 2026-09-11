---
title: Scorelens Finetune
emoji: 🎯
colorFrom: blue
colorTo: green
sdk: gradio
app_file: app.py
pinned: false
---

Trainiert das Scorelens-Dart-Modell im Hintergrund (kostenloser CPU-Basic-Space). Konfiguration über die
Space-Variablen `DATASET_REPO`, `OUTPUT_REPO`, `EPOCHS`, `FREEZE`, `BATCH` und das Secret `HF_TOKEN`
(siehe `tools/finetune/README.md` im Scorelens-Repo). Nach dem Training pausiert sich der Space selbst.
