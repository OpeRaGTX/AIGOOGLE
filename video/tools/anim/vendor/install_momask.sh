#!/usr/bin/env bash
# install_momask.sh — MoMask (text-to-motion, MIT) into tools/anim/vendor/: repo, Python 3.10 venv (uv), HumanML3D weights.
set -e
V="$(cd "$(dirname "$0")" && pwd)"; cd "$V"
[ -d momask-codes ] || git clone -q --depth 1 https://github.com/EricGuo5513/momask-codes.git
[ -x momask-venv/Scripts/python.exe ] || uv venv -q -p "$(uv python find 3.10)" momask-venv
PY="$V/momask-venv/Scripts/python.exe"
# chumpy (SMPL viewer only) fails to build and is not needed for generation
grep -v "^chumpy" momask-codes/requirements.txt | sed -e "s/^matplotlib==.*/matplotlib>=3.5,<3.8/" -e "s/^numpy==.*/numpy==1.23.5/" -e "s/^scipy.*/scipy<1.10/" -e "s/^scikit-learn.*/scikit-learn<1.2/" > momask-reqs.txt  # 3.1.3 has no py3.10 wheel
uv pip install -q -p "$PY" --index-strategy unsafe-best-match -r momask-reqs.txt
uv pip install -q -p "$PY" -U gdown   # old gdown fails on Drive quota pages
"$PY" -c "import torch; print('torch', torch.__version__, 'cuda', torch.cuda.is_available(), torch.cuda.get_device_name(0) if torch.cuda.is_available() else '')"
cd momask-codes
if [ ! -d checkpoints/t2m/length_estimator ]; then
  mkdir -p checkpoints/t2m && cd checkpoints/t2m
  "$V/momask-venv/Scripts/gdown.exe" 1vXS7SHJBgWPt59wupQ5UUzhFObrnGkQ0 -O humanml3d_models.zip
  "$PY" -m zipfile -e humanml3d_models.zip . && rm humanml3d_models.zip
  cd ../..
fi
ls checkpoints/t2m
echo MOMASK_OK
