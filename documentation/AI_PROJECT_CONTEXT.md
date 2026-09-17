# Wizards & Beasts — AI Project Context

> **Purpose:** Persistent context for AI agents working on Wizards & Beasts.
>
> **Primary rule:** Before answering a technical, gameplay, lore, architecture, debugging, UI, asset, or design question about this mod, inspect the current `dev` branch and the relevant source/data/docs. Do not rely on memory, an old conversation, or an old documentation snapshot when the repository can answer the question.
>
> **Repository:** `Koopro/wizards_and_beasts`
> **Integration branch:** `dev`
> **Mod id:** `wizards_and_beasts`
> **Java package:** `at.koopro.wizardsandbeasts`
> **Minecraft:** `1.21.11`
> **NeoForge:** `21.11.42+`
> **Java:** `21`
> **Current version:** `0.1.0-alpha.1`
> **License:** All Rights Reserved
>
> This file is an **AI operating context**, not a replacement for source code. The repository is the authority for implementation.

---

## 1. Mandatory repository-first workflow

Every new AI session involving Wizards & Beasts should begin with this sequence unless the user explicitly says not to inspect the repository.

### Step 1 — Identify the current branch and commit

Use the GitHub repository and inspect `dev` first.

Record internally:

- current `dev` commit SHA
- latest commit message
- whether the requested feature appears to have changed recently

The branch can move at any time. Never assume the commit recorded in this document is still current.

### Step 2 — Read the project entry points

At minimum inspect:

1. `README.md`
2. `documentation/CURRENT_STATE.md`
3. `documentation/DEVELOPER_REFERENCE.md`
4. `documentation/KNOWN_ISSUES.md`
5. `documentation/CHANGELOG.md`

Then inspect the source files relevant to the task.

### Step 3 — Treat source as authoritative

The authority order is:

1. Current implementation in `src/main/java`
2. Current hand-authored data in `src/main/resources`
3. Current generated resources in `src/generated/resources`
4. Current tests in `src/test`
5. Current documentation in `documentation/`
6. Historical documentation under `documentation/history/`
7. Previous chat context / AI memory

When two sources disagree, explicitly resolve the disagreement from current code/data/tests before making a claim.

### Step 4 — Search before asking the user

Before asking questions such as “does the mod already have X?”, “where is X implemented?”, “what system controls X?”, or “is X data-driven?”, search the repository first.

Only ask the user when the repository and available project sources genuinely cannot determine the answer or the decision is a product/design choice that the code cannot resolve.

### Step 5 — Inspect tests before changing architecture

Before proposing a large refactor, find the tests protecting the affected system. Tests often encode intentional invariants that are not obvious from the implementation alone.

Look for:

- unit tests in `src/test`
- GameTests in `gametest/**`
- CI workflow expectations
- named regression tests referred to by documentation

### Step 6 — Check recent changes when a system looks suspicious

For bugs or systems that appear inconsistent, inspect recent commits affecting the relevant files. This avoids recommending a fix for something that was already fixed on `dev`.

---

## 2. What the project is

Wizards & Beasts is a Wizarding World-inspired RPG layer for Minecraft rather than a simple spell-addition mod.

Its core gameplay pillars are:

- wand ownership, allegiance, customization and progression
- data-driven and Java-defined spellcasting
- heritage and character identity systems
- player stats and skill-web progression
- vocations and player abilities
- brooms, Floo and Apparition travel
- brewing and herbology
- magical creatures and the Bestiary
- Gringotts/economy systems
- magical artefacts and utility items
- wizarding-world locations and modules
- law/Ministry concepts
- forms such as Animagus and Obscurial states
- world generation and wizarding materials

The intended fantasy is approximately:

> Choose who you are, acquire a wand that chooses/bonds with you, discover magic rather than simply purchasing a spell list, develop proficiency and specialization, and interact with a broader wizarding world.

The mod is alpha software. Unfinished, preview, gated and deliberately absent content exists by design. Never interpret every registered object as fully playable content.

---

## 3. Important gameplay facts currently documented on `dev`

The following are high-value context points that an agent should know before discussing the mod.

### Wands

Wands are a signature system, not cosmetic sticks. Their state includes concepts such as:

- wand wood
- wand core
- flexibility
- length
- master/bond state
- allegiance/history
- integrity
- corruption
- resonance/customization
- derived casting modifiers

Wood and core definitions are data-driven. Do not reintroduce hardcoded switches when extending these systems.

The current implementation intentionally separates the wand's contribution from a spell's final effective result. A tooltip can display wand-derived stats, but cannot truthfully claim the final cast damage without spell, proficiency and caster context.

Wand allegiance is server-authoritative and is expected to have meaningful gameplay consequences. Using a wand that is not yours is intentionally penalized.

### Spells

Spells are a hybrid system:

- Java spells exist for bespoke behavior.
- JSON/datapack spells handle data-driven content.
- Spell loading/reloading is part of the architecture.
- Known spells, loadouts, cooldowns, cast counts and proficiency belong to player spell state.
- Client input must not be trusted for game-changing execution.

Do not assume every spell is implemented the same way. Inspect `spell/**`, especially:

- `spell/core`
- `spell/data`
- `spell/cast`
- `spell/impl`
- `spell/effect`
- `spell/proficiency`
- matching network handlers

### Player stats

Player stats are not merely UI. They feed gameplay systems including casting/training. Current names include:

- POWER
- PRECISION
- REFLEXES
- WILLPOWER

Additional derived gameplay values exist; inspect the current stats implementation before naming or calculating them.

### Skill web

The skill web is a progression system with data-driven nodes and explicit tests protecting important design invariants.

Do not assume an old node count from documentation is still current. The web has been actively redesigned. Check the current JSON and relevant loader/tests before discussing node counts, costs, filler, unlocks, or balance.

Important design principle: skill nodes should represent actual wizarding choices and pathways rather than simply becoming a long chain of repeated generic stat increases.

### Heritage / conditions

Heritage is a core identity system, but its exact current roster and semantics have changed during development. In particular, older documents may describe werewolf or Obscurial concepts as heritages even when the current implementation treats them differently.

Therefore:

- never quote a heritage roster from memory
- inspect `heritage/**`
- inspect `PlayerHeritageData` and relevant conditions/attachments
- inspect current data definitions
- inspect the onboarding UI and tests

### Creatures

Creatures are data-driven where practical, with generic runtime behavior and bespoke entity classes for special cases.

A creature can have separate concepts for:

- registry/entity type
- creature definition
- rig/model
- animation
- skin/texture
- AI/ability kit
- loot
- Bestiary entry
- natural spawning
- alpha-readiness

Do not infer that “registered” means “alpha-ready,” “naturally spawning,” or “fully finished.” Check the current alpha roster/gates.

### Bestiary

The Bestiary is an actual discovery system. Entries can unlock through discovery and contain lore/data.

Do not assume that every Bestiary entry has equivalent art, mechanics, loot integration, or spawn behavior.

### Modules

Major systems are gated with module states:

- `ENABLED`
- `PREVIEW`
- `DISABLED`
- `COMING_SOON`

A module can be registered while access is gated. Therefore “the registry contains it” does not mean “a normal player can use it.”

Always inspect `ModuleDefaults`, `ModuleManager`, `ModuleContentIndex`, and current documentation when discussing feature availability.

Current documented examples include disabled/off systems such as Azkaban, Chamber of Secrets, Ministry and Dark Arts. Their exact state can change, so verify the current source before reporting availability.

---

## 4. Architecture rules that agents must preserve

### Server authority

For any action that changes gameplay state:

> Client requests → server validates → server mutates → server synchronizes the result.

Do not move validation to the client merely because it makes UI or input code simpler.

### Data-driven content

Prefer JSON/datapack definitions when a system is content-heavy or expected to be iterated by designers.

Examples:

- spells where appropriate
- creatures
- brews
- skills
- vocations
- wand woods
- wand cores
- other tunable game definitions

Do not replace a data-driven system with a large hardcoded switch unless there is a clear architectural reason.

### Player state

Persistent player/entity state generally uses NeoForge attachments.

Per-item-stack state generally uses data components.

Do not put stack-specific state into player attachments or player identity into a generic item NBT blob without first checking the established pattern.

### Client separation

Client-only code belongs under `client/**` or the established client-specific architecture.

Common/server code must not acquire client-only imports.

### Mixins

Prefer NeoForge events, attachments and supported APIs first.

Use a mixin only when the required vanilla behavior cannot be achieved cleanly through the supported API/event layer.

A mixin that is not listed in `wizards_and_beasts.mixins.json` does not load.

### Package structure

The project intentionally uses feature-rooted packages such as:

- `spell/**`
- `wand/**`
- `heritage/**`
- `creature/**`
- `broom/**`
- `floo/**`
- `brew/**`
- `skill/**`

Do not introduce a generic `common/` package simply to reorganize unrelated code.

### Registries

The project uses grouped DeferredRegisters and helpers rather than a single giant registration class.

When adding content, follow the existing registry pattern and inspect nearby registrations before creating a new abstraction.

---

## 5. Important repository map

### Production code

```text
src/main/java/at/koopro/wizardsandbeasts/
```

Feature roots include:

```text
ability/
apparition/
azkaban/
bestiary/
brew/
broom/
client/
command/
creature/
currency/
deluminator/
diary/
effect/
entity/
floo/
form/
heritage/
item/
legilimency/
map/
memory/
module/
network/
owl/
particle/
registry/
skill/
spell/
stats/
sync/
trunk/
util/
wand/
world/
```

### Resources

```text
src/main/resources/
```

Hand-authored assets and data live here.

### Generated resources

```text
src/generated/resources/
```

Generated content is committed. Treat generated data carefully and verify whether a file is generated or hand-authored before editing it.

### Tests

```text
src/test/
```

Game tests and related in-world test infrastructure live under the production/test layout described by the repo. Search for `@GameTest`, `gametest`, and named regression tests when relevant.

### Documentation

```text
documentation/
```

Root-level Markdown files other than the README are intentionally not the normal documentation location. Put persistent project docs under `documentation/`.

---

## 6. Development commands and hazards

The current README documents these important Gradle tasks:

```text
./gradlew build
./gradlew test
./gradlew runClient
./gradlew runClient2
./gradlew runServer
./gradlew runGameTestServer
./gradlew runData
```

CI currently runs the major checks including:

1. `test`
2. `runData`
3. `runGameTestServer`
4. `build`

### Critical datagen warning

Do **not** casually run `runData` in the working tree to inspect something.

The current repo documents that datagen can overwrite committed art with placeholder textures/models when source/generated paths overlap.

If datagen must be tested locally, use a throwaway worktree and inspect the diff before copying anything back.

Never blindly run `git add -A` after datagen.

---

## 7. CI / quality expectations

When proposing or implementing a code change, agents should think in terms of:

- compile correctness
- unit/regression tests
- GameTests for actual gameplay interactions
- datapack/data validation
- client/server separation
- network trust boundaries
- persistence/migration safety
- reload behavior
- resource parity
- lore correctness
- player-visible feedback

A feature is not “done” merely because it compiles.

When practical, add a regression test for a newly discovered invariant so the same bug cannot quietly return.

---

## 8. Lore and Wizarding World rules

The mod aims to feel internally consistent with the Wizarding World rather than merely applying Harry Potter names to generic RPG mechanics.

When evaluating lore accuracy:

1. Determine what the repository currently claims as canon/lore intent.
2. Cross-check external canon sources when the task requires exact canon accuracy.
3. Distinguish established canon from mod-original mechanics.
4. Do not silently invent canon facts.
5. If adapting canon to Minecraft gameplay, explain the adaptation rather than pretending it is canon.

Useful areas to verify carefully include:

- wand woods and cores
- creature behavior and traits
- spell effects and limitations
- heritage/identity terminology
- Ministry/law concepts
- magical artefacts
- travel rules
- potions
- wizarding economy
- school/examination concepts

---

## 9. UI / UX rules

Wizards & Beasts has an intentional wizarding-world visual direction. Existing UI work includes character/skill/bestiary/map and other custom screens.

Before redesigning a screen:

- inspect the existing screen implementation
- inspect the GUI design documentation
- inspect neighboring UI components for shared vocabulary
- keep Minecraft readability and interaction conventions
- do not sacrifice functional clarity for decoration
- distinguish information architecture problems from texture/art problems

The desired style is wizarding-world parchment/book/archival rather than a generic fantasy RPG HUD, while still reading as Minecraft.

---

## 10. Asset / texture / model rules

Agents may need to modify or generate assets, but should first inspect the existing pipeline and asset conventions.

Important principles:

- preserve NeoForge resource paths
- preserve namespace `wizards_and_beasts`
- match existing texture/model naming conventions
- check animation/rig references before replacing models
- check glowmask/emissive conventions where applicable
- do not create a texture merely to satisfy a missing-file warning without checking whether the file is generated or intentionally absent
- verify model, animation, skin and definition parity when working on creatures

When a tool/generator exists under `tools/`, inspect it before replacing its output manually.

---

## 11. Known development history that may matter

The mod has undergone several significant architectural corrections. Agents should check current code instead of assuming older audit findings remain true.

Examples of previously identified problems that were subsequently corrected include:

- wand wood datapack integration
- wand core datapack integration and fallback removal
- Thestral core ID parity
- visible wand-derived cast statistics
- skill-web filler/pathway restructuring
- creature placeholder rigs
- creature asset parity and glowmask issues
- cast/release validation and session tracking
- Protego ward-beam behavior

A past audit finding is historical unless revalidated against current `dev`.

---

## 12. Recent high-value implementation context

The `dev` branch's September 17, 2026 head includes a merge described as bringing the Protego ward-beam fixes onto `dev`. The commit message reports verification of:

- 1,995 unit tests
- 118 in-world scenarios
- green build

These numbers are a **commit-time snapshot**, not permanent project totals. Always re-check current CI/source before quoting them as current.

The same principle applies to counts in every audit or developer reference: creature counts, skill-node counts, Java-file counts, data-file counts, etc. They are snapshots unless regenerated.

---

## 13. How an AI should reason about a task

For a new task, silently classify it into one or more areas:

```text
CODE
DATA
NETWORK
PERSISTENCE
GAMEPLAY
BALANCE
LORE
UI
UX
TEXTURE
MODEL
ANIMATION
WORLDGEN
TESTING
BUILD/CI
DOCUMENTATION
```

Then inspect the corresponding implementation and data.

### Example: “Improve wand cores”

Do not answer from a generic RPG perspective.

Inspect:

```text
wand/**
spell/**
registry/**
network/**
documentation/DEVELOPER_REFERENCE.md
data/... wand definitions
src/test/... wand tests
```

Then determine what is currently implemented, what is data-driven, what is tested, what is visible to the player, and what is still intentionally unwired.

### Example: “Make the Demiguise more lore accurate”

Inspect:

```text
creature/**
bestiary/**
resources/... demiguise data
model/animation/texture files
relevant AI/ability code
```

Then distinguish canon behavior from Minecraft adaptation and propose concrete changes across AI, visuals, loot, Bestiary, spawning, and player interaction as applicable.

### Example: “Fix this bug”

Start with:

1. exact current implementation
2. state ownership
3. client → server path
4. persistence
5. existing regression tests
6. recent commits touching the code

Then reproduce the logic mentally or with tests before changing it.

---

## 14. Questions an AI should stop asking the user unnecessarily

Do not ask questions that repository inspection can answer, such as:

- “What Minecraft version are you using?”
- “Are you using NeoForge?”
- “What is the mod ID?”
- “Where are your spell definitions?”
- “Do you already have a skill system?”
- “Do creatures use GeckoLib?”
- “Do you have a second client?”
- “Where is the character sheet?”
- “Do you have tests?”
- “Is there already a module system?”
- “How are wands stored?”

Check the repo first.

Likewise, do not ask the user to paste a file that is publicly accessible in the connected GitHub repository.

Ask only when:

- the answer is genuinely absent from the repository/project sources
- there are multiple valid product/design choices and the user must choose
- credentials, private files, or inaccessible external sources are required
- the requested behavior is intentionally unspecified and cannot be inferred safely

---

## 15. What agents should report back

When finishing a repository-based task, prefer this structure:

### What I found
Concrete current-state facts from `dev`.

### What is already implemented
Avoid proposing duplicate systems.

### What is missing / incorrect
Separate actual defects from intentional alpha limitations.

### What I changed / recommend
Give specific files/classes/data paths where possible.

### Verification
State what was tested, what was not tested, and whether current CI or local tests were checked.

### Open decision
Only list genuine user/design choices that the repository cannot resolve.

---

## 16. Anti-hallucination rules

Never:

- invent classes, packets, registries, data files, tests or commands
- claim a feature is implemented without finding evidence
- claim a feature is missing solely because it is absent from a documentation snapshot
- quote stale counts as current counts
- assume a historical audit remains valid
- assume a registered asset is playable
- assume a module being registered means it is enabled
- assume a Java spell is the only implementation path
- assume a JSON definition is actually read without tracing its loader
- recommend a duplicate architecture without searching for an existing abstraction

When uncertain, say exactly what was verified and what remains uncertain.

---

## 17. Canon / external research rule

The repository is the authority for **what the mod currently does**.

External sources are required for questions about **what official Wizarding World canon says**, especially when exact lore accuracy matters.

For canon research:

- prefer primary/official sources where available
- use reputable reference sources for details not available from primary sources
- distinguish book/film/game canon when relevant
- distinguish canon from fandom interpretation
- do not invent citations

---

## 18. Agent context checklist

Before producing a substantive answer about Wizards & Beasts, verify internally:

```text
[ ] Current dev branch identified
[ ] Current commit checked
[ ] README checked
[ ] Relevant documentation checked
[ ] Relevant source package searched
[ ] Relevant resource/data files searched
[ ] Relevant tests searched
[ ] Recent changes checked when the issue could already have been fixed
[ ] Module gate checked if feature availability is involved
[ ] Client/server ownership checked if networking is involved
[ ] Persistence/data-component/attachment ownership checked if state is involved
[ ] Canon sources checked if lore accuracy is involved
[ ] Historical audit findings treated as historical until revalidated
```

---

## 19. Single most important instruction

> **Do not make the user re-explain Wizards & Beasts. Inspect the current `dev` branch first, use the repository as the implementation source of truth, and only ask for information that the repository genuinely cannot provide.**

This document itself may become stale. That is intentional: it is a workflow contract, not a frozen copy of the codebase. The first action in a future AI session should still be to inspect the current `dev` branch.
