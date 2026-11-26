#!/bin/bash
# Decides which jobs of the "Quality Gates" workflow a change needs, from the
# files changed between two commits. Used by the "Changes" job in CI; also
# works locally, e.g. `tools/ci/changes.sh origin/dev HEAD`.
#
# Usage: tools/ci/changes.sh <base> <head>
#        tools/ci/changes.sh --all
#
# Prints one flag per line (key=true|false), ready for $GITHUB_OUTPUT:
#   ktlint        code style of all modules
#   core          build and tests of uhabits-core
#   android       unit tests and lint of uhabits-android
#   assemble      debug APKs (needed by the instrumented tests)
#   instrumented  instrumented tests on the emulator
#   release       release build (R8) and smoke test on the emulator
#
# The rules list what may be SKIPPED: a path that no rule knows turns on every
# job, so a new directory can never bypass the checks unnoticed. Without a
# usable base (--all, a new branch, a commit missing after a force push) every
# job runs as well.

set -u
cd "$(dirname "$0")/../.." || exit 1

KEYS="ktlint core android assemble instrumented release"
declare -A need
for key in $KEYS; do need[$key]=false; done

all() {
    for key in $KEYS; do need[$key]=true; done
}

print() {
    for key in $KEYS; do echo "$key=${need[$key]}"; done
}

if [[ $# -eq 1 && $1 == --all ]]; then
    echo "Running every job (requested)." >&2
    all
    print
    exit 0
fi

if [[ $# -ne 2 ]]; then
    echo "Usage: $0 <base> <head> | --all" >&2
    exit 2
fi
BASE=$1
HEAD=$2

if [[ $BASE =~ ^0+$ ]] || ! git cat-file -e "$BASE^{commit}" 2>/dev/null \
    || ! git cat-file -e "$HEAD^{commit}" 2>/dev/null; then
    echo "No usable base ($BASE), running every job." >&2
    all
    print
    exit 0
fi

# Three dots: changes on HEAD since it branched off BASE, as GitHub shows them.
if ! FILES=$(git diff --name-only "$BASE...$HEAD"); then
    echo "git diff failed, running every job." >&2
    all
    print
    exit 0
fi

while IFS= read -r file; do
    [[ -z $file ]] && continue
    case $file in
        # Documentation and project files that no build reads.
        docs/* | screenshots/* | .github/ISSUE_TEMPLATE/* | \
            LICENSE.txt | translators-*.csv | .gitignore)
            ;;
        # Release tooling: only used by the release workflow on main.
        tools/release/* | .releaserc.json | .github/workflows/release.yml | \
            .github/workflows/pr-title.yml)
            ;;
        tools/ci/run-instrumented-tests.sh | tools/parseInstrument.py)
            need[assemble]=true
            need[instrumented]=true
            ;;
        tools/ci/release-smoke-test.sh)
            need[release]=true
            ;;
        tools/ci/changes.sh)
            # The filter itself: check it on everything.
            echo "Every job needed for: $file" >&2
            all
            break
            ;;
        uhabits-android/*)
            need[ktlint]=true
            need[android]=true
            need[assemble]=true
            need[instrumented]=true
            need[release]=true
            ;;
        *)
            # Markdown in the top level (README, CHANGELOG, CLAUDE.md, ...).
            # Markdown inside the modules may be packaged, so it counts as code.
            if [[ $file != */* && $file == *.md ]]; then
                continue
            fi
            # uhabits-core (the app depends on it), build configuration,
            # the Quality Gates workflow and anything unknown.
            echo "Every job needed for: $file" >&2
            all
            break
            ;;
    esac
done <<<"$FILES"

print
