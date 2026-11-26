#!/usr/bin/env bash
# Läuft im Image registry.gitlab.com/fdroid/fdroidserver:buildserver und baut die
# App so, wie es der Job "fdroid build" in fdroiddata/.gitlab-ci.yml tut
# (vereinfacht, ohne fetchsrclibs und Veröffentlichung). Aufgerufen von
# tools/fdroid/build.sh, nicht direkt.
#
# Mounts: /fdroidserver (Checkout), /work (metadata/, config/), /out (Ergebnisse).
# Umgebung: FDROID_LINT_ONLY=1 prüft nur das Rezept (lint, rewritemeta).

set -u
APPID=com.improvemess.habits
export ANDROID_HOME=/opt/android-sdk
# shellcheck disable=SC1091
source /etc/profile.d/bsenv.sh 2> /dev/null || true
home_vagrant=${home_vagrant:-/home/vagrant}

apt-get -qq update
apt-get install -qy --no-install-recommends fdroidserver apksigner git sudo \
    openjdk-21-jdk-headless > /dev/null

mkdir -p "$home_vagrant"/{metadata,build,tmp,unsigned,logs,.gradle}
cp /work/metadata/$APPID.yml "$home_vagrant/metadata/"
cp -r /work/config "$home_vagrant/"
touch "$home_vagrant/config.yml"
chown -R vagrant "$home_vagrant"
cd "$home_vagrant" || exit 1

fdroid() {
    sudo --preserve-env --user vagrant env \
        PATH="/fdroidserver:$(sudo --user vagrant -i printenv PATH)" \
        PYTHONPATH=/fdroidserver:/fdroidserver/examples PYTHONUNBUFFERED=true \
        HOME="$home_vagrant" ANDROID_HOME="$ANDROID_HOME" \
        GRADLE_USER_HOME="$home_vagrant/.gradle" fdroid "$@"
}

status=0
echo "::group::fdroid lint"
fdroid lint -v $APPID || status=1
echo "::endgroup::"

# rewritemeta bringt das Rezept in die Form, die fdroiddata erwartet. Weicht sie
# ab, muss tools/fdroid/com.improvemess.habits.yml angepasst werden.
cp metadata/$APPID.yml /tmp/recipe.yml
fdroid rewritemeta $APPID
if ! diff -u /tmp/recipe.yml metadata/$APPID.yml; then
    echo "::error::Rezept ist nicht in der Form von fdroid rewritemeta (siehe Diff)."
    status=1
fi

if [ "${FDROID_LINT_ONLY:-0}" != "1" ]; then
    sdkmanager "platform-tools" "build-tools;31.0.0" > /dev/null
    git -C "$home_vagrant/gradlew-fdroid" pull -q || true
    echo "::group::fdroid build"
    (unset CI; fdroid build --verbose --test --refresh-scanner --on-server --no-tarball $APPID) || status=1
    echo "::endgroup::"
    cp -r "$home_vagrant"/tmp/*.apk "$home_vagrant"/logs /out/ 2> /dev/null
    chmod -R a+rwX /out
fi

exit $status
