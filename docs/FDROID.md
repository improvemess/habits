# F-Droid

The app is being prepared for F-Droid (project "F-Droid-Veröffentlichung", issues
#104–#118). This page describes how F-Droid builds it and how to check that
locally before a release or a merge request to `fdroiddata`.

## How F-Droid builds the app

F-Droid does not use our APKs. Its build server clones this repository at the
commit named in the recipe, runs a scanner over the source and builds the
`release` variant with Gradle (`assembleRelease`, unsigned, with R8). F-Droid
signs the result with its own key, unless the build is reproducible (#111).

The recipe is [`tools/fdroid/com.improvemess.habits.yml`](../tools/fdroid/com.improvemess.habits.yml),
a template for `metadata/com.improvemess.habits.yml` in `fdroiddata`. Version
and commit are filled in by the build script.

Things the recipe has to handle:

* **JDK.** The build server image (Debian 13) only has JDK 21; Debian 13 has no
  JDK 17. The project asks for a JDK 17 toolchain (`jvmToolchain(17)`), so the
  recipe patches it to 21 in a `prebuild` step, as the upstream recipe
  (`org.isoron.uhabits`) does. Java and Kotlin still target Java 17.
* **Categories.** `Habit Tracker` and `Sports & Health`, as upstream.
* **Updates.** `UpdateCheckMode: Tags ^v[0-9.]+$` with `AutoUpdateMode: Version`:
  F-Droid picks up every release tag (`v2.28.0`, ...) and builds it.

Scanner results: no errors. It warns about the fonts
`uhabits-core/assets/main/fonts/NotoSans-*.ttf` ("possibly code"), a known
false positive for font files. The repositories in `settings.gradle.kts` are
accepted (jitpack only for AppIntro, #109), and no proprietary dependency is
used.

## Checking a commit locally

```bash
tools/fdroid/build.sh                         # origin/dev
tools/fdroid/build.sh <commit-or-branch>      # any commit that is on GitHub
tools/fdroid/build.sh --lint-only <commit>    # recipe only, takes a minute
```

The script needs Docker and network access. It fills in the recipe, downloads
the categories of `fdroiddata` and a pinned `fdroidserver`, and runs the same
steps as the `fdroid build` job of `fdroiddata`'s CI in the build server image
(`registry.gitlab.com/fdroid/fdroidserver:buildserver`):

1. `fdroid lint`: checks the recipe.
2. `fdroid rewritemeta`: the recipe must already be in the canonical form.
3. `fdroid build --on-server`: scanner and Gradle build.

The unsigned APK (`com.improvemess.habits_<versionCode>.apk`), the logs and
`build.log` end up in `build/fdroid/`. The first run takes about 15 minutes
(image of about 1.5 GB, Gradle and all dependencies).

The commit must be on GitHub, because F-Droid clones from there, not from the
local working tree.

## Before a merge request to fdroiddata (#117)

* The release tag exists and `tools/fdroid/build.sh v<version>` succeeds.
* The recipe in `fdroiddata` matches the template, with the tag's commit.
* Anti-features are decided (#112).
