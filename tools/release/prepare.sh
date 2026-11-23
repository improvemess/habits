#!/usr/bin/env bash
# Wird von semantic-release (@semantic-release/exec, Phase "prepare") aufgerufen:
# setzt versionName/versionCode in uhabits-android/build.gradle.kts und baut
# die APK, die anschließend am GitHub-Release hängt.
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

# Debug-Build mit dem festen Schlüssel aus DEBUG_KEYSTORE, damit sich die
# Release-APK über bisherige CI-Builds installieren lässt.
if [[ -z "${DEBUG_KEYSTORE:-}" ]]; then
    echo "::error::DEBUG_KEYSTORE ist nicht gesetzt – die APK hätte eine zufällige Signatur." >&2
    exit 1
fi
./gradlew --stacktrace :uhabits-android:assembleDebug

mkdir -p dist
cp uhabits-android/build/outputs/apk/debug/uhabits-android-debug.apk "dist/loop-${VERSION}.apk"
echo "APK: dist/loop-${VERSION}.apk"
