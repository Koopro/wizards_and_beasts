# Wizards & Beasts

NeoForge mod for Minecraft 1.21.x (`at.koopro.wizardsandbeasts`). Spells, skill trees, wands, brooms, and wizarding content.

## Alpha Release

- Current release channel: `0.1.0-alpha.1`
- For external alpha testing instructions, see `ALPHA_TESTING.md`.
- Known active issues and caveats are tracked in `KNOWN_ISSUES.md`.
- Build-by-build notes are tracked in `CHANGELOG.md`.
- Planned post-alpha features are tracked in `KNOWN_ISSUES.md` under "Coming Soon".
- Multi-feature alpha smoke scenarios (heritage, vault, skills, spells, forms) are summarized under **Systems checklist** in `ALPHA_TESTING.md`.

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
- Before tagging alpha builds, follow the release gate checklist in `ALPHA_TESTING.md` (same CI order).
