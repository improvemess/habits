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
EVENTS=${EVENTS:-4000}
SEEDS=${SEEDS:-"108 1080 10800"}
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

# Tippt auf die Mitte der ersten View, deren resource-id oder Text auf das
# Muster passt (uiautomator dump). Rückgabe 1, wenn keine gefunden wurde.
tap_view() {
    local pattern=$1 bounds
    $ADB shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || return 1
    bounds=$($ADB shell cat /sdcard/ui.xml | tr '>' '\n' |
        grep -E "(resource-id|text)=\"($pattern)\"" | head -1 |
        sed -n -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p')
    [ -n "$bounds" ] || return 1
    set -- $bounds
    $ADB shell input tap $((($1 + $3) / 2)) $((($2 + $4) / 2))
}

# 2. Die Einführung beim ersten Start überspringen, sonst landen fast alle
#    monkey-Eingaben dort statt in der eigentlichen App.
if tap_view "$PACKAGE:id/skip|SKIP|Skip"; then
    echo "Einführung übersprungen."
else
    echo "::warning::Skip-Schaltfläche der Einführung nicht gefunden."
fi
sleep 2

# 3. Zufällige Eingaben in der App (ohne Systemtasten, damit monkey die App
#    nicht verlässt), in mehreren Runden mit verschiedenen Seeds.
: > "$OUT/monkey.txt"
for seed in $SEEDS; do
    echo "::group::monkey ($EVENTS Ereignisse, Seed $seed)"
    $ADB shell monkey -p "$PACKAGE" -s "$seed" --throttle 50 --pct-syskeys 0 \
        --pct-appswitch 0 --ignore-timeouts -v "$EVENTS" > "$OUT/monkey-$seed.txt" 2>&1
    monkey_status=$?
    cat "$OUT/monkey-$seed.txt" >> "$OUT/monkey.txt"
    tail -n 5 "$OUT/monkey-$seed.txt"
    echo "::endgroup::"
    if [ $monkey_status -ne 0 ] || grep -q "// CRASH" "$OUT/monkey-$seed.txt"; then
        echo "::error::monkey hat mit Seed $seed einen Absturz der Release-APK gemeldet (siehe monkey-$seed.txt)."
        status=1
    fi
done

# 4. Crash-Puffer: jede FATAL EXCEPTION der App ist ein Fehler.
$ADB logcat -d -b crash > "$OUT/crash.txt"
$ADB logcat -d > "$OUT/logcat.txt"
if grep -q "Process: $PACKAGE" "$OUT/crash.txt"; then
    echo "::error::Release-APK ist abgestürzt:"
    grep -A 30 "Process: $PACKAGE" "$OUT/crash.txt" | head -60
    status=1
fi

# Welche Bildschirme erreicht wurden, damit die Abdeckung sichtbar bleibt
# (ActivityTaskManager loggt jeden Start mit "START u0 {... cmp=<paket>/<klasse>}").
activities() {
    grep -oE "START u0 .*cmp=$PACKAGE/[A-Za-z0-9_.]+" "$OUT/logcat.txt" |
        sed -E 's/.*cmp=[^/]+\///' | sort | uniq -c | sort -rn
}
echo "Erreichte Activities (Anzahl Starts):"
activities
{
    echo "### Release-Smoke-Test: erreichte Activities"
    activities | awk '{print "- `" $2 "` (" $1 "x)"}'
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

[ $status -eq 0 ] && echo "Release-Smoke-Test bestanden."
exit $status
