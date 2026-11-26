#!/usr/bin/env bash
# Wird von semantic-release (@semantic-release/exec, Phase "prepare") aufgerufen:
# setzt versionName/versionCode in uhabits-android/build.gradle.kts und baut
# die signierte Release-APK (R8), die mit ihrem Mapping am GitHub-Release hängt.
#
# Aufruf: tools/release/prepare.sh <version>   (z. B. 2.4.0)
set -euo pipefail

VERSION="${1:?Aufruf: $0 <version>}"
GRADLE_FILE="uhabits-android/build.gradle.kts"

if [[ ! "$VERSION" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "::error::Unerwartetes Versionsformat: $VERSION" >&2
    exit 1
fi
MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[3]}"

# Schema wie upstream (2.3.2 -> 20302). Muss streng steigen, sonst lässt sich
# die APK nicht über eine ältere Installation aktualisieren.
if ((MINOR > 99 || PATCH > 99)); then
    echo "::error::Minor/Patch > 99 passen nicht ins versionCode-Schema ($VERSION)" >&2
    exit 1
fi
VERSION_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))

sed -i -E \
    -e "s/^( *versionCode = )[0-9]+/\1${VERSION_CODE}/" \
    -e "s/^( *versionName = )\"[^\"]*\"/\1\"${VERSION}\"/" \
    "$GRADLE_FILE"

grep -q "versionCode = ${VERSION_CODE}$" "$GRADLE_FILE"
grep -q "versionName = \"${VERSION}\"$" "$GRADLE_FILE"
echo "Version gesetzt: ${VERSION} (versionCode ${VERSION_CODE})"

# Release-Build (R8) mit dem Release-Schlüssel aus LOOP_KEY_* (#127). Ohne
# diese Variablen baute Gradle eine unsignierte APK (so baut F-Droid); für ein
# GitHub-Release ist das ein Fehler.
for var in LOOP_KEY_STORE LOOP_KEY_ALIAS LOOP_KEY_PASSWORD LOOP_STORE_PASSWORD; do
    if [[ -z "${!var:-}" ]]; then
        echo "::error::$var ist nicht gesetzt – die Release-APK wäre unsigniert." >&2
        exit 1
    fi
done
if [[ ! -s "$LOOP_KEY_STORE" ]]; then
    echo "::error::Keystore $LOOP_KEY_STORE fehlt oder ist leer." >&2
    exit 1
fi
./gradlew --stacktrace :uhabits-android:assembleRelease

mkdir -p dist
# Name nach Paket-ID und Version, z. B. com.improvemess.habits.3.0.0.apk
APP_ID=$(sed -n -E 's/^ *applicationId = "([^"]+)".*/\1/p' "$GRADLE_FILE")
if [[ -z "$APP_ID" ]]; then
    echo "::error::applicationId nicht in $GRADLE_FILE gefunden" >&2
    exit 1
fi
APK="dist/${APP_ID}.${VERSION}.apk"
cp uhabits-android/build/outputs/apk/release/uhabits-android-release.apk "$APK"

# Signatur prüfen: Die APK muss mit genau dem Release-Zertifikat signiert sein,
# dessen Fingerabdruck im Repo steht. Ein falsches Secret fiele sonst erst auf,
# wenn sich ein Update nicht installieren lässt.
EXPECTED=$(tr -d ' \n:' < tools/release/release-cert.sha256 | tr 'A-F' 'a-f')
APKSIGNER=$(ls -d "${ANDROID_HOME:?ANDROID_HOME fehlt}"/build-tools/* | sort -V | tail -1)/apksigner
CERTS=$("$APKSIGNER" verify --print-certs "$APK")
ACTUAL=$(grep -m1 'certificate SHA-256 digest:' <<< "$CERTS" | sed -E 's/.*digest: *//' | tr -d ' :\r' | tr 'A-F' 'a-f' || true)
if [[ -z "$ACTUAL" || "$ACTUAL" != "$EXPECTED" ]]; then
    echo "::error::APK ist nicht mit dem Release-Zertifikat signiert (erwartet $EXPECTED, gefunden ${ACTUAL:-keins})." >&2
    grep -i 'certificate' <<< "$CERTS" >&2 || echo "$CERTS" >&2
    exit 1
fi
echo "Signatur: $ACTUAL"

# R8-Mapping, um Stacktraces aus Fehlerberichten zurückzuübersetzen.
MAPPING="dist/${APP_ID}.${VERSION}-mapping.txt"
cp uhabits-android/build/outputs/mapping/release/mapping.txt "$MAPPING"
echo "APK: $APK"
echo "Mapping: $MAPPING"
