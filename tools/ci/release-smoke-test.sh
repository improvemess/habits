#!/usr/bin/env bash
# Smoke-Test der Release-APK (R8/Minify), wie F-Droid sie baut (#108).
#
# Signiert die unsignierte Release-APK mit einem Wegwerf-Schlüssel, installiert
# sie, startet die App und lässt monkey zufällige Eingaben machen. Schlägt fehl,
# wenn die App abstürzt (Crash-Log oder monkey-Abbruch). ANRs werden ignoriert,
# da der Emulator in der CI dafür zu langsam und zu unzuverlässig ist.
#
# Aufruf: tools/ci/release-smoke-test.sh <unsigned-release.apk> <output-dir>

set -u
cd "$(dirname "$0")/../.." || exit 1

UNSIGNED_APK=${1:?Aufruf: $0 <unsigned-release.apk> <output-dir>}
OUT=${2:?Aufruf: $0 <unsigned-release.apk> <output-dir>}
ADB=${ADB:-adb}
EVENTS=${EVENTS:-3000}
SEED=${SEED:-108}
PACKAGE=$(sed -n -E 's/^ *applicationId = "([^"]+)".*/\1/p' uhabits-android/build.gradle.kts)

mkdir -p "$OUT"
BUILD_TOOLS=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
KEYSTORE="$OUT/smoke.keystore"
SIGNED_APK="$OUT/release-smoke.apk"

keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias smoke -keyalg RSA -keysize 2048 -validity 1 -dname "CN=smoke" > /dev/null 2>&1 || exit 1
"$BUILD_TOOLS/zipalign" -f -p 4 "$UNSIGNED_APK" "$OUT/aligned.apk" || exit 1
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android \
    --out "$SIGNED_APK" "$OUT/aligned.apk" || exit 1

$ADB install -r "$SIGNED_APK" || exit 1
$ADB logcat -c

status=0

# 1. Kaltstart: die App muss nach dem Start noch laufen.
$ADB shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 > /dev/null
sleep 5
if ! $ADB shell pidof "$PACKAGE" > /dev/null; then
    echo "::error::Release-APK läuft nach dem Start nicht mehr."
    status=1
fi

# 2. Zufällige Eingaben in der App (ohne Systemtasten, damit monkey die App nicht verlässt).
echo "::group::monkey ($EVENTS Ereignisse, Seed $SEED)"
$ADB shell monkey -p "$PACKAGE" -s "$SEED" --throttle 50 --pct-syskeys 0 \
    --pct-appswitch 0 --ignore-timeouts -v "$EVENTS" > "$OUT/monkey.txt" 2>&1
monkey_status=$?
tail -n 20 "$OUT/monkey.txt"
echo "::endgroup::"
if [ $monkey_status -ne 0 ] || grep -q "// CRASH" "$OUT/monkey.txt"; then
    echo "::error::monkey hat einen Absturz der Release-APK gemeldet (siehe monkey.txt)."
    status=1
fi

# 3. Crash-Puffer: jede FATAL EXCEPTION der App ist ein Fehler.
$ADB logcat -d -b crash > "$OUT/crash.txt"
$ADB logcat -d > "$OUT/logcat.txt"
if grep -q "Process: $PACKAGE" "$OUT/crash.txt"; then
    echo "::error::Release-APK ist abgestürzt:"
    grep -A 30 "Process: $PACKAGE" "$OUT/crash.txt" | head -60
    status=1
fi

[ $status -eq 0 ] && echo "Release-Smoke-Test bestanden."
exit $status
