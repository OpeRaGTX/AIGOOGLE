#!/usr/bin/env bash
# text2motion.sh "<description>" <seconds> <out.bvh> [model.glb out.blend] — draft motion from text with MoMask
# (HumanML3D model, 20 fps, 2–9.8 s). Writes <out.bvh> (MoMask skeleton, foot IK) + <out.npy> (22 joint positions).
# With model.glb + out.blend: imports the model, builds the control rig and keys the draft on it (motion2rig.py),
# ready to refine by hand. T2M_FACING=front keeps the body facing forward (drops the draft's turning).
# MoMask is MIT; its HumanML3D/AMASS-trained weights are for research/non-commercial use.
set -e
A="$(cd "$(dirname "$0")" && pwd)"; M="$A/vendor/momask-codes"; PY="$A/vendor/momask-venv/Scripts/python.exe"
[ -x "$PY" ] || PY="$A/vendor/momask-venv/bin/python"
[ -x "$PY" ] || { echo "MoMask is not installed: bash $A/vendor/install_momask.sh"; exit 1; }
[ $# -ge 3 ] || { echo 'usage: text2motion.sh "<description>" <seconds> <out.bvh> [model.glb out.blend]'; exit 1; }
frames=$(awk -v s="$2" 'BEGIN { f = int(s * 20 / 4 + 0.5) * 4; if (f < 40) f = 40; if (f > 196) f = 196; print f }')
tag="t2m_$$"
(cd "$M" && PYTHONIOENCODING=utf-8 "$PY" "$A/t2m_gen.py" --gpu_id 0 --ext "$tag" --text_prompt "$1" \
   --motion_length "$frames" --repeat_times 1 2>&1 | grep -E "Sample|Error|error" || true)
src="$M/generation/$tag"
[ -f "$src/joints/0/sample0_repeat0_len${frames}_ik.npy" ] || { echo "generation failed"; exit 1; }
mkdir -p "$(dirname "$3")"
cp "$src/animations/0/sample0_repeat0_len${frames}_ik.bvh" "$3"
cp "$src/joints/0/sample0_repeat0_len${frames}_ik.npy" "${3%.bvh}.npy"
rm -rf "$src"
echo "draft: $3 + ${3%.bvh}.npy ($frames frames @ 20 fps)"
if [ -n "$5" ]; then
  . "$A/../env.sh"
  w() { (cd "$(dirname "$1")" && echo "$(pwd -W 2>/dev/null || pwd)/$(basename "$1")"); }
  "$BLENDER" -b --factory-startup -P "$A/motion2rig.py" -- "$(w "$4")" "$(w "${3%.bvh}.npy")" "$(w "$5")" ${T2M_FACING:+facing=$T2M_FACING} 2>&1 | grep -E "draft on rig|Error|Traceback"
fi
