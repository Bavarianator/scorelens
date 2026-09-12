#!/usr/bin/env bash
# Trainingskernel auf Kaggle starten, GPU (T4) explizit. Regler als NAME=WERT-Paare, z. B.:
#   tools/finetune/kaggle/push.sh dd6 DATA=data6 BASE_RUN=kg_dd4 EPOCHS=40 LR0=0.0003 COS_LR=1 D2_WEIGHT=2 FREEZE=5 HSV_H=0.05 HSV_S=0.6 \
#       SOURCES=mobayer/scorelens-build-data6,mobayer/scorelens-finetune-dd4
# Baut ein Kernel-Verzeichnis im Scratch, setzt die Defaults im Skript und pusht mit --accelerator NvidiaTeslaT4.
set -euo pipefail
cd "$(dirname "$0")/../../.."
run=$1; shift
dir=$(mktemp -d); cp tools/finetune/kaggle/train_kaggle.py "$dir/"
sources='[]'
for kv in "$@"; do
  k=${kv%%=*}; v=${kv#*=}
  if [ "$k" = SOURCES ]; then sources=$(printf '%s' "$v" | python3 -c 'import sys,json; print(json.dumps([s for s in sys.stdin.read().split(",") if s]))'); continue; fi
  # Default des Reglers im Skript ersetzen: os.environ.get("K", alt) → os.environ.get("K", neu); Strings behalten Anführungszeichen
  python3 - "$dir/train_kaggle.py" "$k" "$v" <<'PY'
import re, sys
p, k, v = sys.argv[1:]; s = open(p).read()
m = re.search(r'os\.environ\.get\("%s", ([^)]*)\)' % k, s); assert m, f"Regler {k} unbekannt"
new = f'"{v}"' if m.group(1).startswith('"') else v
open(p, "w").write(s.replace(m.group(0), f'os.environ.get("{k}", {new})'))
PY
done
python3 - "$dir/train_kaggle.py" "$run" <<'PY'
import re, sys
p, run = sys.argv[1:]; s = open(p).read()
s = re.sub(r'os\.environ\.get\("NAME", "[^"]*"\)', f'os.environ.get("NAME", "kg_{run}")', s); open(p, "w").write(s)
PY
cat > "$dir/kernel-metadata.json" <<JSON
{"id": "mobayer/scorelens-finetune-$run", "title": "Scorelens Finetune $run", "code_file": "train_kaggle.py", "language": "python",
 "kernel_type": "script", "is_private": true, "enable_gpu": true, "enable_internet": true, "machine_shape": "NvidiaTeslaT4",
 "dataset_sources": [], "competition_sources": [], "kernel_sources": $sources, "model_sources": []}
JSON
grep -oE 'os\.environ\.get\("[A-Z0-9_]+", [^)]*\)' "$dir/train_kaggle.py" | tr '\n' ' '; echo
venv/bin/kaggle kernels push -p "$dir" --accelerator NvidiaTeslaT4 2>&1 | grep -v warn | tail -1
