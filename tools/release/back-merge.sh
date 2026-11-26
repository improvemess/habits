#!/usr/bin/env bash
# Führt nach einem Release den Stand von main nach dev zurück. Wird vom
# Release-Workflow aufgerufen und per Deploy-Key gepusht (darf die Ruleset auf
# dev umgehen).
#
# Warum: semantic-release committet versionCode/versionName und CHANGELOG.md
# als "chore(release): x.y.z [skip ci]" auf main. Ohne Rückführung liefe dev
# auseinander, und der nächste Release-PR dev → main hätte Konflikte. Danach
# gilt wieder: dev enthält main vollständig.
#
# Es entsteht immer ein Merge-Commit "chore(release): merge main into dev",
# auch wenn dev nur vorgespult werden könnte, und zwar ohne Skip-CI-Marker
# (#124): Für einen Commit mit Marker laufen keine pull_request-Workflows.
# Wäre er der Kopf von dev, bekäme ein Release-PR dev → main nie den
# Pflicht-Check "Quality gate" und bliebe blockiert. So laufen die Quality
# Gates einmal auf dev; ein Release lösen sie dort nicht aus.
#
# Bei einem Konflikt bricht das Skript ab, ohne etwas zu pushen. Konflikte
# entstehen nur, wenn auf dev dieselben Zeilen geändert wurden (versionCode,
# versionName, Anfang von CHANGELOG.md). Dann von Hand auflösen:
#   git fetch origin && git switch -C dev origin/dev && git merge origin/main
#   (Konflikt lösen, committen), Ruleset "dev" kurz deaktivieren, git push origin dev,
#   Ruleset wieder aktivieren. Ein Squash-PR reicht nicht: Er verliert die
#   Verbindung zu main, der Konflikt käme beim nächsten Release wieder.
#
# Aufruf: tools/release/back-merge.sh [<quelle> <ziel>]   (Standard: main dev)
set -euo pipefail

SOURCE="${1:-main}"
TARGET="${2:-dev}"
REMOTE="${REMOTE:-origin}"
ATTEMPTS=3

for ((attempt = 1; attempt <= ATTEMPTS; attempt++)); do
    git fetch --no-tags "$REMOTE" \
        "+refs/heads/${SOURCE}:refs/remotes/${REMOTE}/${SOURCE}" \
        "+refs/heads/${TARGET}:refs/remotes/${REMOTE}/${TARGET}"

    if git merge-base --is-ancestor "${REMOTE}/${SOURCE}" "${REMOTE}/${TARGET}"; then
        echo "${TARGET} enthält ${SOURCE} bereits, nichts zu tun."
        exit 0
    fi

    git checkout --quiet -B "$TARGET" "${REMOTE}/${TARGET}"
    if ! git merge --no-ff --no-edit -m "chore(release): merge ${SOURCE} into ${TARGET}" "${REMOTE}/${SOURCE}"; then
        git merge --abort || true
        echo "::error::Konflikt beim Zurückführen von ${SOURCE} nach ${TARGET}. Anleitung im Kopf von $0." >&2
        exit 1
    fi

    # Schlägt fehl, wenn dev in der Zwischenzeit weitergelaufen ist: neu versuchen.
    if git push "$REMOTE" "HEAD:refs/heads/${TARGET}"; then
        echo "${SOURCE} nach ${TARGET} zurückgeführt ($(git rev-parse --short HEAD))."
        exit 0
    fi
    echo "::warning::Push nach ${TARGET} abgelehnt (Versuch ${attempt}/${ATTEMPTS}), neuer Versuch."
done

echo "::error::${SOURCE} konnte nicht nach ${TARGET} gepusht werden." >&2
exit 1
