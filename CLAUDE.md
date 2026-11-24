# CLAUDE.md

Projektgedächtnis für Claude Code in diesem Fork (`oliverlorenz/uhabits`). Hier steht, was uns wichtig ist. Bitte bei jeder Änderung beachten und bei neuen Erkenntnissen ergänzen.

## Immer beachten: Export & Import

Bei **jeder** Änderung am Datenmodell oder an Daten, die Nutzer:innen speichern (neue Felder, neue Tabellen, neue Einstellungen, neue Habit-Eigenschaften wie z. B. Pausen), prüfen wir:

1. **Export:** Werden die Änderungen sinnvoll im Export mitgegeben?
   - Loop-Datenbank-Backup (SQLite, `ExportDBTask`) enthält neue Spalten/Tabellen automatisch – Migration und Schema prüfen.
   - CSV-Export (`HabitsCSVExporter`) – prüfen, ob die neuen Daten dort sinnvoll abgebildet werden oder bewusst nicht (dann begründen).
2. **Import:** Können die exportierten Daten auch wieder importiert werden (Round-Trip)?
   - `LoopDBImporter` (und ggf. `GenericImporter`, `HabitBullCSVImporter`, `RewireDBImporter`, `TickmateDBImporter`) anpassen, wenn neue Felder dazukommen.
   - Ältere Exporte ohne die neuen Felder müssen weiterhin importierbar sein (Abwärtskompatibilität, sinnvolle Defaults).
3. **Tests:** Round-Trip-Test (Export → Import → Daten identisch) für neue Felder ergänzen oder erweitern.
4. In der PR-Beschreibung kurz festhalten: „Export: ja/nein/n. a. – Import: ja/nein/n. a." mit Begründung.

## Workflow (Fork)

- Änderungen nur im Fork `oliverlorenz/uhabits`, **nie** upstream (iSoron/uhabits).
- Nie direkt auf `dev` committen: Feature-Branch von `dev`, aussagekräftige Commits, Pull Request im Fork.
- `gh pr create` immer mit `--repo oliverlorenz/uhabits --base dev`.

## Immer beachten: README mit Unterschieden zum Fork-Ursprung

Bei **jeder** Änderung (Feature, Fix, Änderung am Verhalten, an Berechtigungen, Abhängigkeiten oder am Build/Release) ergänzen wir im selben PR den Abschnitt „Differences from upstream“ in `README.md`:

- Neuer Punkt oder bestehenden Punkt erweitern, mit PR-Nummer, kurz und aus Nutzersicht (Englisch, wie die README).
- Wichtige Eigenheiten nennen: Standardwerte (z. B. „off by default“), neue Berechtigungen, was bewusst nicht enthalten ist.
- Reine interne Refactorings ohne sichtbare Wirkung brauchen keinen Eintrag.
- In der PR-Beschreibung festhalten: „README: ergänzt / n. a.“.

## Qualitätssicherung vor jedem Push

- `./gradlew ktlintCheck` (alle Module) und die betroffenen Tests ausführen; CI „Quality Gates" schlägt bei ktlint-Verstößen fehl (z. B. Import-Reihenfolge).
- Bekannte, bereits vorhandene Testfehler lokal unter Windows (nicht von uns): `StringsTest.testFormat`, `HabitsCSVExporterTest.testExportCSV`, `HabitListTest.testWriteCSV`. Vor Annahme einer Regression die CI prüfen.

## Lokale Build-Umgebung (Windows)

- Gradle braucht JDK 17: `JAVA_HOME="/c/Program Files/Microsoft/jdk-17.0.21.102-hotspot"` pro Befehl setzen.
- Android SDK: `C:\Android\Sdk` (`local.properties` enthält `sdk.dir`, gitignored).
- Nützliche Tasks: `:uhabits-core:jvmTest`, `ktlintCheck`, `:uhabits-android:assembleDebug`, `:uhabits-android:testDebugUnitTest`.
- Kein Python verfügbar; Edit-Tool oder sed/awk nutzen. Kein Emulator: Instrumented Tests werden nur kompiliert.

## Projektstruktur (Kurzüberblick)

- `uhabits-core`: Kotlin Multiplatform, Fachlogik, Modelle, Import/Export (`core/io`).
- `uhabits-android`: Android-App (UI, DB-Zugriff, `tasks/ExportDBTask`, Sharing, Homeassistant, Widgets).
