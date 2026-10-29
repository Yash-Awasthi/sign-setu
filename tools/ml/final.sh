#!/bin/sh
# The final model in one command.
#   sh final.sh rehearse   W recipe on cache_v4b as row WR: seeds, soup, pick, calibration, staging and gate only
#   sh final.sh seeds [CACHE]  seeds onward on a cache already cut (SEEDS=n, default 5; the soup ships)
#   sh final.sh final [CACHE]  re-split what is landmarked now into cache_v5 (or CACHE) (INCLUDE test held out), then the same as row WF
# Installing into the app, the Kotlin test and the APK stay manual, after the staged gate passes.
set -e
export PYTHONIOENCODING=utf-8 PYTHONUTF8=1  # labels include Devanagari; the Windows code page cannot write them
MODE=$1
L=/c/work/ml-data/logs
MP=/c/work/venvs/signsetu-mp/Scripts/python.exe
TP=/c/work/venvs/test2isl/Scripts/python.exe
cd "$(dirname "$0")"
if [ "$MODE" = final ]; then
  ROW=${ROW:-WF}; CACHE=${2:-C:/work/ml-data/cache_v5}
  # Landmarks keep running; this run takes what is landmarked now. Faces and clusters share the CPU meanwhile.
  echo 8 > /c/work/ml-data/landmark_workers.txt
  $MP -s q3.py catalog > $L/final_catalog.log 2>&1
  $MP -s q3.py spans > $L/final_spans.log 2>&1
  $MP -s q3.py faces --workers 16 > $L/final_faces.log 2>&1
  $MP -s q3.py clusters > $L/final_clusters.log 2>&1
  echo 14 > /c/work/ml-data/landmark_workers.txt
  $TP -s splits.py --hold-include-test --grow-held --out $CACHE > $L/final_splits.log 2>&1
  grep -av Warning $L/final_splits.log | tail -n 30
  # The final's seeds take the GPU: gap-filler rows stop (they resume from checkpoints) and pretraining pauses.
  touch /c/work/ml-data/runs/pause_pretrain
  # The filler chain first, so it cannot start its next pair, then its rows.
  powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { \$_.ProcessId -ne \$PID -and \$_.CommandLine -match '(gpu_pairs|i12)\.sh' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force -ErrorAction SilentlyContinue }"
  powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { \$_.ProcessId -ne \$PID -and \$_.CommandLine -match 'python.*exp\.py (OPT|I1[0-9])' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force -ErrorAction SilentlyContinue }"
elif [ "$MODE" = seeds ]; then
  # The cache is already cut: seeds onward only.
  ROW=${ROW:-WF}; CACHE=${2:-C:/work/ml-data/cache_v5}
else
  ROW=WR; CACHE=C:/work/ml-data/cache_v4b
fi
# SEEDS (default 5) seeds, two at a time, then their uniform soup. The soup ships: the best single seed on the same
# val proved val-lucky (final1: seed 1 beat seed 4 by 15 clips to 1).
SEEDS=${SEEDS:-5}
seeds=$(seq 1 $SEEDS)
# Earlier runs of this row are moved aside, never deleted: a gate may have used them.
OLD=/c/work/ml-data/runs/_old/${ROW}_$(date +%m%d_%H%M)
for d in /c/work/ml-data/runs/${ROW}_s*; do [ -e "$d" ] && mkdir -p $OLD && mv "$d" $OLD/; done
set -- $seeds
while [ $# -gt 0 ]; do
  $TP -s exp.py $ROW --seed $1 --cache $CACHE > $L/${ROW}_s$1.log 2>&1 &
  [ $# -gt 1 ] && $TP -s exp.py $ROW --seed $2 --cache $CACHE > $L/${ROW}_s$2.log 2>&1 &
  wait
  shift; [ $# -gt 0 ] && shift
done
$TP -s soup.py $ROW $seeds
$TP -s exp.py $ROW --seed 99 --eval --cache $CACHE > $L/${ROW}_s99.log 2>&1
for s in $seeds 99; do grep -h "ROW" $L/${ROW}_s$s.log || true; done
# Seed spread, for the record.
$TP -s union.py $(for s in $seeds; do [ $s != 1 ] && printf "%s " ${ROW}_s$s; done) ${ROW}_s99 --ref ${ROW}_s1 --cache $CACHE > $L/${ROW}_union.log 2>&1
cat $L/${ROW}_union.log
PICK=${ROW}_s99
echo "pick $PICK"
$TP -s ship_w.py --row $ROW --run $PICK --cache $CACHE --stage $ROW > $L/${ROW}_ship.log 2>&1
grep -a "temperature\|gate" $L/${ROW}_ship.log
