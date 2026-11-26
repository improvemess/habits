# CLAUDE.md

Project memory for Claude Code in this fork (`improvemess/habits`). It lists what matters to us. Follow it for every change and extend it when you learn something new.

## Always: export & import

For **every** change to the data model or to data that users store (new fields, new tables, new settings, new habit properties such as pauses), we check:

1. **Export:** are the changes included in the export where it makes sense?
   - The Loop database backup (SQLite, `ExportDBTask`) contains new columns/tables automatically – check migration and schema.
   - CSV export (`HabitsCSVExporter`) – check whether the new data is represented there sensibly, or deliberately not (then give a reason).
2. **Import:** can the exported data be imported again (round trip)?
   - Adjust `LoopDBImporter` (and, if needed, `GenericImporter`, `HabitBullCSVImporter`, `RewireDBImporter`, `TickmateDBImporter`) when new fields are added.
   - Older exports without the new fields must stay importable (backward compatibility, sensible defaults).
3. **Tests:** add or extend a round-trip test (export → import → identical data) for new fields.
4. State it briefly in the PR description: "Export: yes/no/n/a – Import: yes/no/n/a" with a reason.

## Always: public texts in English

Everything that is publicly visible is written **in English**:

- Issues: title, body and comments (also when editing old issues).
- Pull requests: title, body, review comments.
- Commit messages: the PR title becomes the squash commit, and semantic-release turns it into the release notes and `CHANGELOG.md`. A German title therefore ends up in the release notes.
- Release notes and changelog, README, `docs/`, code comments, store texts and this file.

Exceptions: translated app strings (`values-de` and similar) and localized store texts (`de-DE`). Talk to the user in the language they use.

## Workflow (fork)

- Changes only in the fork `improvemess/habits`, **never** upstream (iSoron/uhabits).
- Never commit directly to `dev`: feature branch off `dev`, meaningful commits, pull request in the fork.
- Always run `gh pr create` with `--repo improvemess/habits --base dev`.
- Feature PRs are merged into `dev` by **squash**. The PR title (Conventional Commit) decides the next version; the PR body becomes the commit message.
- **Never write a skip CI marker (`[skip ci]` and similar) literally in PR bodies**: the squash commit would carry it, no checks would run for it, and a release PR with this commit on top would stay blocked.
- A merge into `dev` does **not** create a release. Releases only come from a release PR `dev → main` titled `chore(release): dev → main`, merged as a **merge commit** (never squash). Afterwards the release workflow merges `main` back into `dev` automatically. Only open or merge release PRs when explicitly asked to. Details: `docs/RELEASE.md`.

## Always: README with the differences from upstream

For **every** change (feature, fix, change of behavior, permissions, dependencies or build/release) we update the "Differences from upstream" section in `README.md` in the same PR:

- Add a new item or extend an existing one, with the PR number, short and from the user's point of view.
- Mention important details: defaults (e.g. "off by default"), new permissions, what is deliberately not included.
- Purely internal refactorings without a visible effect need no entry.
- State it in the PR description: "README: updated / n/a".

## Quality checks before every push

- Run `./gradlew ktlintCheck` (all modules) and the affected tests; the CI "Quality Gates" fail on ktlint violations (e.g. import order).
- Known, pre-existing local test failures on Windows (not caused by us): `StringsTest.testFormat`, `HabitsCSVExporterTest.testExportCSV`, `HabitListTest.testWriteCSV`. Check CI before assuming a regression.
- **Instrumented tests do not run locally** (no emulator); they are only compiled here. For changes to UI flows (dialogs, activities, navigation, back behavior), search `uhabits-android/src/androidTest` for affected steps before pushing (`CommonSteps.createHabit`, `HabitsTest` and others), update them and add an acceptance test for the new behavior. Results of `registerForActivityResult` arrive before `onStart`, i.e. after `onSaveInstanceState`: in the callback use `dismissAllowingStateLoss()` instead of `dismiss()`, and no fragment transactions with `commit()`.
- **Check CI after every push** (`gh pr checks <nr> --repo improvemess/habits`, on failures `gh run view <id> --log-failed`) and fix failures before reporting the PR as done.

## Local build environment (Windows)

- Gradle needs JDK 17: set `JAVA_HOME="/c/Program Files/Microsoft/jdk-17.0.21.102-hotspot"` per command.
- Android SDK: `C:\Android\Sdk` (`local.properties` contains `sdk.dir`, gitignored).
- Useful tasks: `:uhabits-core:jvmTest`, `ktlintCheck`, `:uhabits-android:assembleDebug`, `:uhabits-android:testDebugUnitTest`.
- No Python available; use the Edit tool or sed/awk. No emulator: instrumented tests are only compiled.

## Project structure (overview)

- `uhabits-core`: Kotlin Multiplatform, domain logic, models, import/export (`core/io`).
- `uhabits-android`: Android app (UI, DB access, `tasks/ExportDBTask`, sharing, Home Assistant, widgets).
