# Neo

NeoForge mod for Minecraft 1.21.x (`at.koopro.neo`). Spells, skill trees, wands, brooms, and wizarding content.

## Requirements

- **Java 21** (the build uses `java.toolchain.languageVersion = 21`). To pin a JDK locally without changing the repo, set `org.gradle.java.home` in your user Gradle properties (e.g. `~/.gradle/gradle.properties` on Unix or `%USERPROFILE%\.gradle\gradle.properties` on Windows).

## Build

```bash
./gradlew build
./gradlew test
```

## Data generation

Generated assets and data live under `src/generated/resources` and are **committed** to the repository. After changing datagen code (e.g. `ModModelProvider`, `ModLanguageProvider`, loot/table providers), run the **data** run configuration from your IDE or the equivalent Gradle task, then commit the updated generated files.

If the same resource path exists in both `src/main/resources` and `src/generated/resources`, the build merges them with `DuplicatesStrategy.EXCLUDE`—prefer **datagen output** as the source of truth for those paths to avoid stale hand-edited JSON.
