#!/usr/bin/env bash
# Baut einen Commit dieses Repos so, wie F-Droid ihn bauen würde (#110):
# im Docker-Image des F-Droid-Buildservers, mit fdroid lint, Scanner und
# fdroid build aus dem Quellcode auf GitHub.
#
# Aufruf: tools/fdroid/build.sh [--lint-only] [<commit>] [<ausgabe-ordner>]
#   <commit>          muss auf GitHub liegen (Standard: origin/dev)
#   <ausgabe-ordner>  Standard: build/fdroid
#
# Ergebnis: unsignierte APK com.improvemess.habits_<versionCode>.apk, Logs und
# build.log im Ausgabeordner. Dauert beim ersten Lauf ~15 min (Image ~1,5 GB,
# Gradle und Abhängigkeiten). Details: docs/FDROID.md

set -euo pipefail
cd "$(dirname "$0")/../.."

IMAGE=registry.gitlab.com/fdroid/fdroidserver:buildserver
# fdroidserver-Version, mit der das Rezept zuletzt geprüft wurde.
FDROIDSERVER_COMMIT=fa7b54e637963337877cd31645d364a467f490f7
CATEGORIES_URL=https://gitlab.com/fdroid/fdroiddata/-/raw/master/config/categories.yml

LINT_ONLY=0
if [ "${1:-}" = "--lint-only" ]; then
    LINT_ONLY=1
    shift
fi
REF=${1:-origin/dev}
OUT=$(realpath -m "${2:-build/fdroid}")

git fetch -q origin
COMMIT=$(git rev-parse "$REF^{commit}")
if ! git branch -r --contains "$COMMIT" | grep -q .; then
    echo "Commit $COMMIT liegt nicht auf GitHub; F-Droid klont von dort. Erst pushen." >&2
    exit 1
fi
GRADLE=$(git show "$COMMIT:uhabits-android/build.gradle.kts")
VERSION_NAME=$(sed -n -E 's/^ *versionName = "([^"]+)".*/\1/p' <<< "$GRADLE")
VERSION_CODE=$(sed -n -E 's/^ *versionCode = ([0-9]+).*/\1/p' <<< "$GRADLE")
echo "Baue $COMMIT (versionName $VERSION_NAME, versionCode $VERSION_CODE)"

# Arbeitsordner außerhalb des Repos: heruntergeladene Daten getrennt halten.
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/metadata" "$WORK/config" "$OUT"

sed -e "s/@VERSION_NAME@/$VERSION_NAME/g" -e "s/@VERSION_CODE@/$VERSION_CODE/g" \
    -e "s/@COMMIT@/$COMMIT/g" tools/fdroid/com.improvemess.habits.yml \
    > "$WORK/metadata/com.improvemess.habits.yml"

# Kategorien wie in fdroiddata; ohne Icon-Verweise, die lint sonst als Dateien sucht.
curl -fsSL "$CATEGORIES_URL" | sed '/^  icon:/d' > "$WORK/config/categories.yml"

git clone -q https://gitlab.com/fdroid/fdroidserver.git "$WORK/fdroidserver"
git -C "$WORK/fdroidserver" -c advice.detachedHead=false checkout -q "$FDROIDSERVER_COMMIT"

docker run --rm \
    -e FDROID_LINT_ONLY=$LINT_ONLY \
    -v "$WORK/fdroidserver:/fdroidserver:ro" \
    -v "$WORK:/work:ro" \
    -v "$OUT:/out" \
    -v "$PWD/tools/fdroid/in-container.sh:/in-container.sh:ro" \
    --entrypoint bash "$IMAGE" /in-container.sh 2>&1 | tee "$OUT/build.log"
status=${PIPESTATUS[0]}

if [ "$status" -eq 0 ]; then
    echo "F-Droid-Prüfung erfolgreich. Ausgabe: $OUT"
else
    echo "F-Droid-Prüfung fehlgeschlagen (Exit $status). Log: $OUT/build.log" >&2
fi
exit "$status"
