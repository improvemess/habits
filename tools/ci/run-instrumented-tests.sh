#!/bin/bash
# Runs the medium and large instrumented tests on a booted emulator, like
# `build.sh android-tests`, but against prebuilt APKs and with a retry for
# flaky tests. Used by the "Android (instrumented tests)" job in CI; also
# works locally with any emulator that adb can see.
#
# Usage: tools/ci/run-instrumented-tests.sh <app.apk> <androidTest.apk> <output-dir>
#
# Environment:
#   ADB       adb command (default: adb)
#   ATTEMPTS  runs per test size; failed tests are retried once by default
#
# Exit code 0 if every test passed in some attempt. Tests that needed a retry
# are listed as flaky (and reported as GitHub warnings in CI).

set -u
cd "$(dirname "$0")/../.." || exit 1

APP_APK=$1
TEST_APK=$2
OUT=$3
ADB=${ADB:-adb}
ATTEMPTS=${ATTEMPTS:-2}
PACKAGE=org.isoron.uhabits
RUNNER=$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner

mkdir -p "$OUT"

# Same device preparation as build.sh (animations are disabled by the caller).
$ADB root > /dev/null 2>&1 && sleep 5 && $ADB wait-for-device
$ADB shell 'echo android-test > /sys/power/wake_lock' || true

$ADB install -r -t "$APP_APK" || exit 1
$ADB install -r -t "$TEST_APK" || exit 1

status=0
for size in medium large; do
    failed=""
    first_failures=""
    passed=1
    for ((attempt = 1; attempt <= ATTEMPTS; attempt++)); do
        log="$OUT/instrument-$size-$attempt.txt"
        echo "::group::$size tests, attempt $attempt ${failed:+(retrying failed tests only)}"
        # shellcheck disable=SC2086 # $failed is "-e class a,b,c" or empty
        $ADB shell am instrument -r -e size "$size" $failed -w "$RUNNER" | ts "%.s" > "$log"
        failed=$(python3 tools/parseInstrument.py "$log")
        passed=$?
        echo "::endgroup::"
        [ "$attempt" -eq 1 ] && first_failures=${failed#-e class }
        if [ $passed -eq 0 ]; then
            if [ "$attempt" -gt 1 ]; then
                echo "::warning::Flaky $size tests, passed only on attempt $attempt: $first_failures"
            fi
            echo "$size: passed (attempt $attempt)"
            break
        fi
        echo "$size: failed on attempt $attempt: ${failed#-e class }"
    done
    if [ $passed -ne 0 ]; then
        echo "::error::Some $size instrumented tests failed: ${failed#-e class }"
        status=1
    fi
done

if [ $status -ne 0 ]; then
    $ADB logcat -d > "$OUT/logcat.txt"
    $ADB pull "/sdcard/Android/data/$PACKAGE/files/test-screenshots" "$OUT/test-screenshots" > /dev/null 2>&1 || true
fi
exit $status
