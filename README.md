# Wizards & Beasts

NeoForge mod for Minecraft 1.21.x (`at.koopro.wizardsandbeasts`). Spells, skill trees, wands, brooms, and wizarding content.

## Alpha Release

- Current release channel: `0.1.0-alpha.1`
- Build-by-build notes: `CHANGELOG.md`
- Active issues, release gates, and alpha smoke scenarios: `DEVELOPER_REFERENCE.md` §21 (Alpha Release Gates & Smoke Checks)
- For the authoritative code/systems reference (package layout, registries, networking, key classes): see `DEVELOPER_REFERENCE.md`. Older design docs (`ARCHITECTURE.md`, `DESIGN_NOTES.md`, `ASSET_STATUS.md`) are archived in `docs/history/` and superseded by `DEVELOPER_REFERENCE.md`.
- Combined build/work history: `WORKLOG.md` (audit + deltas per build pass), `CREATURES.md` (creature build + abilities), `SPELLS.md` (spell migration foundation + deltas).

## Requirements

- **Java 21** (the build uses `java.toolchain.languageVersion = 21`). To pin a JDK locally without changing the repo, set `org.gradle.java.home` in your user Gradle properties (e.g. `~/.gradle/gradle.properties` on Unix or `%USERPROFILE%\.gradle\gradle.properties` on Windows).

## Build

```bash
./gradlew build
./gradlew test
```

## CI (canonical gate)

GitHub Actions CI runs on push and pull request:

- `./gradlew test`
- `./gradlew runData`
- `./gradlew runGameTestServer`
- `./gradlew build`

Build artifacts are uploaded from `build/libs/*.jar`.

Canonical workflow file path is `.github/workflows/ci.yml` (forward-slash path). Keep CI edits in that location only.

## Data generation

Generated assets and data live under `src/generated/resources` and are **committed** to the repository. After changing datagen code (e.g. `ModModelProvider`, `ModLanguageProvider`, loot/table providers), run the **data** run configuration from your IDE or the equivalent Gradle task, then commit the updated generated files.

If the same resource path exists in both `src/main/resources` and `src/generated/resources`, the build merges them with `DuplicatesStrategy.EXCLUDE`—prefer **datagen output** as the source of truth for those paths to avoid stale hand-edited JSON.

## Local Hygiene

- Local/generated runtime outputs (`build/`, `bin/`) are not committed.
- Before tagging alpha builds, follow the release gate checklist in `DEVELOPER_REFERENCE.md` §21.8 (same CI order).
