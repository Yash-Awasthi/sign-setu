#!/usr/bin/env bash
# Runs one shard of the instrumented suite on an emulator the caller has already started.
#
# ci.yml used to hold this loop inline. The android-emulator-runner action turns its `script`
# input into a list of single-line commands, so a `for ... do` / `done` pair can never parse
# there ("Syntax error: end of file unexpected"). All shell structure lives here instead.
#
# Usage: bash tools/ci/run_instrumented.sh <shard>
# Env:   DEVICE_CLASSES  the classes to run, set by the instrumented job in ci.yml
#        TOTAL_SHARDS    must match the job's matrix (default 4)
#        DRY_RUN=1       print the commands and exit without touching adb
set -euo pipefail

shard="${1:?usage: run_instrumented.sh <shard>}"
total="${TOTAL_SHARDS:-4}"
classes="${DEVICE_CLASSES:?DEVICE_CLASSES is set by the instrumented job in ci.yml}"
report_dir="app/build/reports/androidTests"
verify_dir="out/verify"

if [ "${DRY_RUN:-0}" = "1" ]; then
  for c in $classes; do
    echo "gradlew :app:connectedStandardDebugAndroidTest class=app.signsetu.$c numShards=$total shardIndex=$shard"
  done
  if [ "$shard" = "0" ]; then
    echo "shard 0 would also: install both apks, run VerifierFixtureDeviceTest through am instrument,"
    echo "exec-out the report and passphrase, then run tools/security/verify_seal.py with its pins"
  fi
  echo "dry run ok: $(echo $classes | wc -w) classes, shard $shard of $total"
  exit 0
fi

# One class per gradle run: given a comma list, the connected task ran only the first class.
for c in $classes; do
  ./gradlew --console=plain :app:connectedStandardDebugAndroidTest     -Pandroid.testInstrumentationRunnerArguments.class=app.signsetu."$c"     -Pandroid.testInstrumentationRunnerArguments.numShards="$total"     -Pandroid.testInstrumentationRunnerArguments.shardIndex="$shard"     || { mkdir -p "$report_dir"; adb logcat -d > "$report_dir/logcat.txt"; exit 1; }
done

# The same exported report, checked off the phone: the app's seal must satisfy verify_seal.py.
# The test runs through the instrumentation runner directly, not the connected task, because the
# connected task uninstalls the app and the report with it.
if [ "$shard" = "0" ]; then
  adb install -r -t app/build/outputs/apk/standard/debug/app-standard-debug.apk
  adb install -r -t app/build/outputs/apk/androidTest/standard/debug/app-standard-debug-androidTest.apk
  adb shell am instrument -w -e class app.signsetu.VerifierFixtureDeviceTest \
    in.signsetu.test/androidx.test.runner.AndroidJUnitRunner \
    || { mkdir -p "$report_dir"; adb logcat -d > "$report_dir/logcat.txt"; exit 1; }
  mkdir -p "$verify_dir"
  adb exec-out run-as in.signsetu cat files/verify/report.pdf > "$verify_dir/report.pdf"
  adb exec-out run-as in.signsetu cat files/verify/passphrase.txt > "$verify_dir/passphrase.txt"
  pip install cryptography==50.0.2 pypdf==6.21.4
  python tools/security/verify_seal.py --pdf "$verify_dir/report.pdf" --passphrase "$(cat "$verify_dir/passphrase.txt")"
fi
