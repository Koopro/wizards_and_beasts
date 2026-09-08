# Alpha Release Gate — 0.1.0-alpha.2

Reconstructs the gate half of the stripped `DEVELOPER_REFERENCE.md` §21. The by-hand scenario
list lives in [`ALPHA_SMOKE.md`](ALPHA_SMOKE.md); this document is the **decision procedure**:
work top to bottom and it ends in "tag" or "fix X first".

Everything in §0 was executed on 2026-09-07 against this repository. Results are real, not
predicted. Where a check could not be run, it says so.

---

## 0. Verdict for the current tree — **DO NOT TAG**

Four CI gates pass. Five defects block, of which two are cheap. The blockers are listed in
§7 with file:line.

| Gate | Command | Result | Detail |
|---|---|---|---|
| Unit tests | `./gradlew test` | **PASS** | 202 suites, **1641 tests, 0 failures, 0 errors, 0 skipped** |
| Datagen | `./gradlew runData` | **PASS** | `written: 0`; `src/main/resources` and `src/generated/resources` byte-identical before and after |
| Game tests | `./gradlew runGameTestServer` | **PASS** | `All 11 required tests passed :)` in 6.448 s |
| Build | `./gradlew build` | **PASS** | jar produced |
| Dedicated server | `./gradlew runServer` | **PASS** (at `47bc7344`) | `Done (0.352s)`, RCON up, module state seeded and persisted |

**Blocking:**

- **B-1 — the working tree does not compile.** A concurrent session is writing
  `heritage/vampire/*`; `ModAttachments` lacks `BLOOD_DRAINED_UNTIL` and `VAMPIRE_BLOOD`.
  3 `cannot find symbol` errors. Nothing can be tagged from this tree until it compiles.
- **B-2 — the config fix `KNOWN_ISSUES.md` §8a.1 claims is applied is not in `HEAD`.**
  At `47bc7344`, `Config.java:260` still uses plain `.defineList("adminUuids", …)`;
  `defineListAllowEmpty` and the whole `werewolf*` block exist **only in the uncommitted tree**.
  Tagging `HEAD` ships the unfixed config and a doc that lies about it.
- **B-3 — `ministry_license_scroll` recipe never loads.** Reproduces on a clean `HEAD` boot; it
  is the *only* ERROR in an otherwise clean dedicated-server start.
- **B-4 — the JEI plugin throws on every module-state sync**, taking the
  `wizards_and_beasts:module_state_sync` payload handler down with it.
- **B-5 — 644 uncommitted files.** A tag must come from a committed, reproducible tree.

**Cheap and worth doing in the same pass:** F-1 (missing lang key), F-3 (bench serialization
spam), F-4 (silent brooms).

---

## 1. Pre-build hygiene

- [ ] **Working tree clean.** `git status --porcelain` returns nothing.
      *Today: 644 entries.* A dirty tree cannot be re-derived from the tag.
- [ ] **No other session is editing.** Check file mtimes under `src/main/java` against the clock.
      This gate was written while another session wrote `heritage/vampire/*` — the tree changed
      three times mid-run.
- [ ] **Java 21 is what Gradle actually uses.** This machine's default is **Corretto 8**:

      ```
      $ java -version
      openjdk version "1.8.0_502"     # WRONG
      ```

      Set it for the shell that runs the gates:

      ```bash
      export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot"
      export PATH="$JAVA_HOME/bin:$PATH"
      java -version     # must say 21.0.x
      ```

      `java.toolchain.languageVersion = 21` in `build.gradle` covers compilation, but the
      `runX` tasks and any manual `java` invocation do not.
- [ ] **Generated resources are in sync.** Run `runData`, then confirm the tree did not move:

      ```bash
      ./gradlew --no-daemon runData
      git status --porcelain src/generated/resources
      ```

      A clean run logs `written: 0`. *Today: clean — the ~200 staged deletions under
      `models/item/` are correct; datagen genuinely no longer emits them.*
- [ ] **`runData` did not stub over real art.** It writes placeholder PNGs into
      `src/main/resources`. Never `git add -A` after it. Back up first:

      ```bash
      cp -r src/main/resources /tmp/resbackup && ./gradlew runData
      diff -rq /tmp/resbackup src/main/resources    # must be empty
      ```

      *Today: 269 untracked PNGs were at risk; the diff came back empty, so nothing was clobbered.*
- [ ] **No stale dev processes.** `Get-Process java` — kill leftovers before any `runServer`.
      See §5 for why this matters more than it looks.
- [ ] **Version bumped.** `gradle.properties` → `mod_version`.
- [ ] **`KNOWN_ISSUES.md` matches reality**, in both directions. B-2 is a case of the document
      describing a fix that is not in the commit being tagged.

---

## 2. Module default states for the alpha

Authority is [`ModuleDefaults.java`](../src/main/java/at/koopro/wizardsandbeasts/module/ModuleDefaults.java).
Verify against a **fresh world** — module state is stored per world and seeded once:

```
/wandb admin module list
```

Expect the header `4 disabled · 16 enabled · 9 preview`. *Verified 2026-09-07 — exact match.*

| State | Modules |
|---|---|
| **ENABLED** (16) | `wands`, `wands_and_spells`, `skill_trees`, `broom_flight`, `pocket_dimensions`, `floo_network`, `character_sheet`, `structures`, `handbook`, `gringotts`, `wandwood`, `magizoology`, `wizarding_food`, `artefacts`, `furnishings`, `scholarship` |
| **PREVIEW** (9) | `proficiency`, `player_abilities`, `creatures`, `bestiary`, `player_animation`, `owls`, `player_stats`, `heritage`, `apparition` |
| **DISABLED** (4) | `dark_arts`, `azkaban`, `chamber_of_secrets`, `ministry` |
| **COMING_SOON** (0) | nothing ships in this state |

`ModuleManager` gates **access, never registration** — a `/give` still hands you an item from an
off module. `ModuleContentIndex` **fails open**: untagged content stays reachable.

### Verified module-system behaviour

| Check | Result |
|---|---|
| Fresh world seeds from config defaults | `[Modules] Seeded a new world's module state from config defaults (29 modules)` |
| `module set` applies and reports | `Module Ministry of Magic → enabled` |
| State survives a restart | flipped `ministry`+`creatures`, restarted: `3 disabled · 18 enabled · 8 preview`, no re-seed |
| `COMING_SOON` refused to operators | *"That module is marked coming soon — a roadmap marker, not a switch."* |
| Unknown module / unknown state | `Unknown module: not_a_module` / `Unknown state: not_a_state (disabled, enabled, preview, coming_soon).` |

**One defect here — F-1.** `/wandb admin module list` prints the raw key for one module:

```
module.wizards_and_beasts.heritage.name (heritage) · preview
```

`module.wizards_and_beasts.heritage.name` exists in no lang file. It is the **only** one of the
29 missing — every other module resolves. One line in
`src/main/resources/assets/wizards_and_beasts/lang/en_us.json`.

---

## 3. Critical-path smoke scenarios

Run against a **fresh singleplayer Survival world** unless a step says otherwise. `[A]` = covered
by an automated gate and needs no hand pass. `[M]` = manual, needs a client.

### Data-load census — do this first `[A]`

Boot a dedicated server and read the load lines. Any count that moves without an intended change
means content was lost. Baseline at `47bc7344`:

| Loader | Count |
|---|---|
| Recipes | 1898 |
| JSON spells | 155 (0 failed) |
| Brews | 14 (0 failed) |
| Brewing recipes | 12 (0 failed) |
| Bestiary entries | 107 (107 tied to an entity) |
| Creature definitions | 96 |
| Skill nodes | 168 across 3 webs, **0 dropped edges, 0 unreachable nodes** |
| Pocket templates | 3 |
| Ollivander pool | 12 |
| Harvest rules | 5 |
| Magical deeds | 5 |
| Map discovery | 14 structure + 5 landmark rules |

**Gate: exactly one ERROR is expected today (B-3). Zero is the target. Grep `WARN` too** — the
loot-modifier failures that once shipped were WARN-level, not ERROR.

### S1 — Heritage selection + form change `[A]` + `[M]`

Automated: `heritage_commit_builds_a_whole_character`,
`heritage_change_rerolls_power_and_keeps_training`, `heritage_clear_takes_everything_back_off`
(all pass). They cover the commit routine, the POWER re-roll onto the new band, training
survival, and that `clear` takes the body, both attribute modifiers and the roll back off.

Manual, because the ceremony is a screen:
1. Join fresh. The selection screen opens and cannot be dismissed.
2. Cycle `◀ ▶`. **Exactly three are selectable** — Wizardkind, Werewolf, Obscurial. The other
   seven show and refuse.
3. Confirm Wizardkind. Screen closes, size profile applies.
4. `/wandb player heritage set <player> giant full_giant`, then `/wandb player sheet`.
   The body, POWER band and ability grants must all change together — those were three
   different subsets before the commit routine was unified.

> **Caveat on the automated coverage.** `PowerBandTable` knows **only the five Wizardkind
> lineages**; `full_giant` logs `unknown variant 'full_giant', defaulting to (20, 70) band`.
> The game test asserts against that fallback, so it does not prove a real Giant band exists.
> Not a regression — a coverage gap. See F-5.

### S2 — Wand acquisition + first cast `[A]` + `[M]`

Automated: `cast_and_release_casts_exactly_once`, `release_without_cast_does_nothing`,
`duplicate_release_is_refused`, `dead_caster_cannot_release` (all pass).
`WandBondReachabilityTest` pins the bond inequality — the bug where *every* wand refused
*every* player was a weighted sum that could not clear its own threshold.

Manual:
1. Craft wandmaker's bench + wand blank + a core. Use the bench, pick wood and core.
2. Tooltip shows wood / core / length / flexibility, and the wand is **tinted by its wood**.
3. Hold it. A bond result arrives as one toast, once, at the unbound→bound transition.
4. **A total refusal rate across several wood/core pairs is a regression, not bad luck.**
5. `/wandb magic spell learn lumos`, hold `X`, sweep, release, right-click.
6. Three different refusals must produce **three different messages**: on cooldown / not
   learned / no wand in hand. A generic "can't cast" for all three is a failure.

### S3 — Skill tree unlock + proficiency gain `[M]`

1. `K` opens the web on the star-chart background. Loader must report
   **0 dropped edges, 0 unreachable nodes**.
2. Unlock a reachable node → **toast**, not chat (chat draws *under* an open screen).
3. Try a locked node → refusal toast naming the missing requirement.
4. `/wandb player skill points`, then `/wandb player skill respec` — ungated on purpose, refunds
   `pointCost × level` and strips the ability grants those nodes sourced. A player must not be
   able to soft-lock themselves out of the web.
5. Cast a learned spell ~20 times; `/wandb magic proficiency` shows the float curve moving.

> `PROFICIENCY` stays `PREVIEW`: the formula is single-owned and test-locked
> (`SpellPowerTest`), the **numbers** are not balanced. Two proficiency systems still coexist
> and disagree about "mastered".

### S4 — Broom flight `[M]`

Do this on **two** brooms — the starter `broom` and a `firebolt_supreme` — because impact and
camera maths are per-broom and the bug they replaced only showed as a *difference* between them.

1. Use on ground → broom entity spawns, you mount.
2. W drives, S reverses, jump climbs, sneak descends, sprint boosts.
3. **Release everything mid-air.** It must *settle downward*, capped short of free-fall.
   Hovering forever is the old behaviour and a regression.
4. **Gentle landing costs nothing** — check durability before and after; unchanged is the pass.
5. **Fly flat out into a cliff on both brooms.** Both must register a real crash. The starter
   broom used to be unable to register an impact at any speed.
6. **Firebolt Supreme at ~⅔ throttle into a wall → survive.** It used to be fatal.
7. Dismount hovering → set down on the ground below, no momentum kept.
8. Fly into water, then lava → flight ends, rider set at nearest clear footing, broom drops as
   an item, action-bar line says so.
9. `broomFovEffect=0` and `broomWindVolume=0` must **fully** stop both — this is the supported
   motion-sickness path.

> **F-4: expect silence.** 15 broom sound events reference vanilla `.ogg` paths that do not
> exist in 1.21.11 — mount, dismount, both crash tiers, all four boosts, all wind tiers and
> polish. Step 2–8 are currently mute. See §7.

### S5 — Apparition, short and long `[A]`(logic) + `[M]`(feel)

Two tiers, from [`ApparitionTier.java`](../src/main/java/at/koopro/wizardsandbeasts/apparition/ApparitionTier.java):

| Tier | Charge | Cooldown | Range | Aborts on damage |
|---|---|---|---|---|
| `BLINK` | 10 t | 40 t | 12 base + 18 across the proficiency curve | no |
| `ANCHORED` | 70 t | 1200 t | any distance, same dimension | **yes** |

1. `/wandb magic apparate licence <player>` (or accept the Trace fine — see S7).
2. **Short:** hold the ability key ~10 ticks, release in the Deliberation window → crack, arrive
   within range. Miss the window → splinch.
3. `/wandb magic apparate mark <name>` at A, travel to B, apparate back — **long/anchored**.
4. **Take a hit during an anchored wind-up.** Outcome must floor to at least `MAJOR`; carrying a
   passenger floors to `CATASTROPHIC`, which does not arrive at all.
5. `/wandb world ward add …` then apparate into it → refused.
6. **Mounted:** apparating while riding silently snaps you back. Confirm it refuses instead.
7. Listen for the crack **variant** and check the radius scales with volume — the resolution
   queue that drives both once had zero consumers, so both were inaudible while looking wired.

### S6 — Creature interaction / bestiary `[A]` + `[M]`

Automated (verified live, 2026-09-07): the roster reports **96 registered, 96 definitions
loaded, 12 on the alpha roster**, every row `[ok]`. Summoned and queried:

| Creature | Health | Reading |
|---|---|---|
| `hippogriff` (alpha) | 36.0 | custom attributes applied |
| `hungarian_horntail` (alpha) | 136.0 | breed kit applied |
| `niffler` (alpha) | 10.0 | — |
| `kneazle` (placeholder) | 6.0 | spawns and lives; box rig |

Manual:
1. `/wandb beast creature summon hippogriff` → hand-built rig, AI past wander-and-look, loot on death.
2. `/wandb beast creature summon kneazle` → 6–9 flat-shaded boxes, drops nothing. **Documented, not a failure.**
3. Wander without summoning → only alpha-roster creatures spawn naturally, plus the Unicorn,
   which is exempt because it is the only wild source of a wand core.
4. Creative tab shows **no** placeholder spawn eggs.
5. Bestiary: entries unlock on sight at 12 blocks, scanned once a second.
   Tiers `UNDISCOVERED → SIGHTED → ENCOUNTERED → STUDIED → MASTERED`.
6. **Expect the art to be thin** — 32px procedural portraits, no per-entry illustration.

### S7 — Ministry Trace / notoriety `[M]`

`MINISTRY` ships **DISABLED**, so this scenario must first turn it on. That is the point: with
it off, illegal magic is legal and `TraceService.report` returns `CLEAR` without touching state.

1. `/wandb admin module list` → `ministry · disabled`. Commit an offence → **nothing recorded**.
2. `/wandb admin module set ministry enabled`.
3. `/wandb ministry offence <player> unlicensed_apparition` → notoriety rises, record filed,
   fine assessed. Filed first, billed second.
4. `/wandb ministry record <player>` → status, rank, offences, outstanding fine.
5. Repeat the same offence → both the heat *and* the fine escalate in step (a first offence is
   unscaled in both).
6. `/wandb ministry fine pay` — Gringotts settles from the vault. **Heat must not cool while a
   fine is outstanding.**
7. `/wandb ministry wanted`, then `/wandb ministry notoriety clear <player>`.
8. Lie low → notoriety decays at 0.05/second.
9. Set it back to `disabled` before shipping.

> Offences are `arrestable` XOR `fineable`. Live report sites: unlicensed Apparition,
> unregistered Animagus, forged documents, Ministry trespass, devalued coin, and Unforgivables
> via `SpellCastService`. **`DARK_ARTS` is DISABLED**, so the Unforgivable route may be
> unreachable in a default install — audit items `AUD-F-001/002` are open against exactly that.

### S8 — Gringotts currency `[M]`

1. `/wandb player vault` → balance. Character sheet's Carried Coin panel agrees.
2. `/wandb player vault deposit galleons 3`, `withdraw knuts 100`.
3. **Rates are canon: 29 Knuts = 1 Sickle, 17 Sickles = 1 Galleon, 493 Knuts = 1 Galleon.**
   Craft both directions and confirm totals are preserved.
4. Every coin's tooltip states the **whole** scale, not just its own step.
5. Buy a lesson from the spell teacher — **58 Knuts, on by default**. It is the only coin sink;
   with it off the economy is decoration.
6. Niffler prioritises canonical coins over `dragot`, highest denomination first.
7. Confirm **no** emerald exchange path exists. Deliberate.

### S9 — Character sheet / stats `[M]`

1. Inventory → Character Sheet tab button with an icon, never a magenta square.
2. No missing-texture checkerboard, at GUI scale **2 and 3**.
3. **Amber corner mark** on any tab fed by a `PREVIEW` module (Attributes, while `PLAYER_STATS`
   is `PREVIEW`). `/wandb admin module set player_stats enabled` → mark goes away.
4. Every tab selects at both scales — hit areas must track the drawn tabs.
5. `/wandb player stats get <player> power`, `set`, `reroll_power`.
   **`reroll_power` must not reset PRECISION / REFLEXES / WILLPOWER or the four training
   accumulators** — it did, and that is what `withHeritageRoll` fixed.
6. Speed rows print percentages against a 0.1 base — the Centaur must read **+30%**, not "+3%".
7. Throw off an Imperius with `PLAYER_STATS` **off**, then **on**. Module-off must use the
   **top** of the resist range as fallback; it used to pin at the 0.40 floor, making the curse
   ~2.5× harder to resist with stats disabled.

### S10 — Armour equip `[M]` — **gated on the armour pass landing**

Do not run until the in-flight armour work is merged; `ArmorGeoAssetTest` and
`ArmorRendererBindingTest` are green but only bind assets.

1. `/give @s wizards_and_beasts:student_robe_chest` (also `_legs`, `_boots`, `wizard_hat`,
   `auror_robe_*`, `death_eater_robe_*`, `death_eater_mask`).
2. Equip each slot → the GeckoLib rig draws, and **is not a featureless slab**. A detail cube
   inside `chest + inflate` renders as nothing; the head box swallows anything above y24.
3. Hood toggle keybind → `HoodToggleC2SPayload` round-trips and other players see it.
4. Armour values reach `Attributes.ARMOR` — hang a robe on a duelling dummy and read the
   damage difference. That is what the dummy is for.
5. Unequip → every modifier comes back off.

### S11 — Multiplayer lifecycle `[M]` — required for the RC gate

Two clients, one dedicated server:
1. Both join → heritage select, spell assign/select/cast, skill unlock, form transition.
2. Reconnect after a dimension change; die and respawn.
3. `PLAYER_STATS` and `HERITAGE_DATA` are `copyOnDeath` and re-sync on login, respawn and
   dimension change. Transient attribute modifiers re-apply on the same three events.
4. **Join server A, reach a high heritage sync version, then join a freshly started server B.**
   B's `AtomicInteger` starts at zero per process; a client that fails to reset `lastSyncVersion`
   rejects **every** heritage sync for the whole session — no cap tick, no HUD entry, wrong
   body, nothing logged.
5. Rapid channel release under latency — the known desync edge.

---

## 4. Systems that must stay PREVIEW or DISABLED

| Module | State | Why it cannot be `ENABLED` |
|---|---|---|
| `chamber_of_secrets` | **DISABLED** | `structure/chamber_of_secrets/chamber.nbt` is a **1.3 KB placeholder** with no reachable content. `ChamberOfSecretsStructure` gates generation on the module, so a default install places nothing. *Verified: `locate` returns "Could not find a structure … nearby".* |
| `azkaban` | **DISABLED** | `structure/azkaban.nbt` is a **225-byte placeholder**. No Dementor spawn table, no loot. Turning it on generates an empty crag. *Verified: same refusal (registry id is `azkaban_fortress`, not `azkaban`).* |
| `ministry` | **DISABLED** | Never enabled in any shipped build. Preserved off so existing worlds do not suddenly gain a police force. |
| `dark_arts` | **DISABLED** | Gating layer only; content falls back to whatever module owns it. Open audit items `AUD-F-001/002`. |
| `heritage` | **PREVIEW** | **3 of 10** heritages are selectable. The transformation triggers for the other seven do not exist. |
| `creatures` | **PREVIEW** | **12 of 96** are alpha-ready; 83 are on placeholder box rigs. |
| `bestiary` | **PREVIEW** | 107 entries with full lore, but only 32px procedural portraits and no detail-pane illustration. `EncounterTrigger.ITEM_USE` has no caller. |
| `owls` | **PREVIEW** | **5 of 12 O.W.L. subjects can only ever grade Troll** — Arithmancy, Divination, History of Magic, Astronomy, Muggle Studies each read a counter nothing increments. |
| `player_abilities` | **PREVIEW** | **4 selectable Animagus forms have no datapack definition** (`stag`, `hawk`, `hare`, `beetle`) and 2 definitions are unreachable (`falcon`, `rat`). The server logs both on every boot. Stag — the iconic one — gets no capabilities, attributes or senses. |
| `apparition` | **PREVIEW** | `AbilityInput.chargeTicks` is client-side and unrevalidated. |
| `proficiency` | **PREVIEW** | Two proficiency systems coexist and disagree about "mastered". |
| `player_stats` | **PREVIEW** | Derived values and HUD provisional. |
| `player_animation` | **PREVIEW** | One proving clip; poses read wrong when swimming, riding, on elytra. |

Both structure modules are `DISABLED` rather than `COMING_SOON` on purpose — `COMING_SOON`
would put them out of an operator's reach.

---

## 5. Running a headless smoke pass

Three traps, all hit while producing this document.

**`runServer` "failing" is almost never NeoForge.** `DEVELOPER_REFERENCE.md` §18/§21.1 blame a
bootstrap issue for `FatalStartupException: Couldn't find Minecraft server thread`. That message
is a *symptom*. The two real causes seen here:

```
java.io.IOException: The process cannot access the file because another process
  has locked a portion of the file            → stale process holding run/world/session.lock
**** FAILED TO BIND TO PORT!  Address already in use  → stale process on 25565
```

Both were leftover dev JVMs, some days old. Kill them, or use a separate `level-name` and port.
**Recommend correcting those two doc sections.**

**The dev server pauses when empty, so entities never tick.**

```
[Server thread/INFO]: Server empty for 60 seconds, pausing
```

Every `/data get` then returns "No entity was found" even though `/summon` reported success.
Put this in `run/server.properties` for any headless pass:

```properties
pause-when-empty-seconds=0
```

**RCON has no position, so bare `@e` selectors miss.** Anchor every selector:

```
execute in minecraft:overworld positioned 0.0 -59.0 0.0 run data get entity @e[type=…,distance=..64,limit=1] Health
```

Enable RCON in `run/server.properties` (`enable-rcon=true`, a port, a password) and drive the
server over it — the whole of §3's server-side half runs this way without a client.

---

## 6. Missing tests

Ordered by the regression each would have caught. The first four are the ones this pass
actually needed.

### Unit tests — cheap, no world needed

| # | Test | Catches |
|---|---|---|
| U-1 | `ModuleLangParityTest` — every `Module` value has `module.<modid>.<id>.name` **and** every `ModuleState` has its `.state.` key | **F-1 today.** One assertion over `Module.values()`; `LangParityTest` exists but does not cover this key family. |
| U-2 | `RecipeResultStackSizeTest` — every shipped recipe's `result.count` ≤ that item's `maxStackSize` | **B-3 today**, and every future repeat. Pure JSON + registry read. |
| U-3 | `SoundEventAssetTest` — every entry in `sounds.json` resolves to a file that exists, vanilla paths included | **F-4 today** — 15 dead vanilla `.ogg` references, all broom. |
| U-4 | `PowerBandCoverageTest` — every `HeritageVariant` has a `PowerBandTable` band | **F-5.** `full_giant` silently falls back to `(20,70)`, which the heritage game test then asserts against. |
| U-5 | `ConfigSpecEmptyListTest` — no `ModConfigSpec` list uses plain `defineList` with an empty default | **B-2.** `defineList` pins `ListValueSpec.NON_EMPTY`, so an empty default fails its own size check on every load. |
| U-6 | `AnimagusFormDataParityTest` — `AnimagusForms.IDS` ⟷ `data/…/animagus_forms/` both ways | The 4-missing / 2-unreachable split the server already logs but nothing fails on. |
| U-7 | `BlockEntityCodecRoundTripTest` for `WandmakersBenchBlockEntity` | **F-3** — empty slots encoded as air against a codec that rejects air. |
| U-8 | `OwlSubjectReachabilityTest` — every O.W.L. subject has at least one counter with a live mutator | The 5 permanently-Troll subjects. Applies the lessons-file rule: *compute the best case and compare it to the threshold.* |

### Game tests — need a live world

| # | Test | Catches |
|---|---|---|
| G-1 | `jei_hiding_survives_an_empty_list` | **B-4.** Assert `applyHiding` is safe when either list is empty. |
| G-2 | `module_state_survives_a_restart` | Done by hand today. `ModuleStateData` is a `SavedData`; a re-seed on reload would silently reset every operator's choices. |
| G-3 | `broom_mounts_flies_and_lands` — mount, apply input, assert displacement, land gently, assert **durability unchanged**; then fly into a wall and assert an impact | The whole of S4. The per-broom crash divisor bug was invisible to unit tests. |
| G-4 | `apparition_blink_arrives_and_anchored_floors_on_damage` | The damage floor and the mounted-snap-back case. |
| G-5 | `trace_records_only_while_ministry_is_on` | The module gate on `TraceService.report` — the exact shape of B-4's failure, one layer up. |
| G-6 | `vault_deposit_withdraw_round_trips` + denomination conversion | The `NonNullList.toArray` returns a copy trap that would have made every potion free lives in this family. |
| G-7 | `spell_cast_refusals_are_distinct` — assert three refusals produce three different keys | S2 step 6. |
| G-8 | `disabled_module_recipe_is_unreachable` in-world (the existing test is static) | Content leaking out of a disabled module. |

### Fix the harness gap first

**`8a.4` — a game-test player cannot be killed.** Both `Entity.kill(ServerLevel)` and
`hurt(genericKill(), MAX_VALUE)` leave a test `ServerPlayer` at full health.
`dead_caster_cannot_release` works around it by emptying health directly, so
`WandCastSessions.abort` on death — **and anything else that depends on a player dying** — has
no automated coverage at all. Nineteen handlers subscribe to `LivingIncomingDamageEvent`;
bisect them. This blocks G-3, G-4 and G-6 from testing their death paths.

---

## 7. Defect register for this gate

| ID | Sev | Where | What |
|---|---|---|---|
| **B-1** | blocker | `heritage/vampire/*` | Tree does not compile: `ModAttachments.BLOOD_DRAINED_UNTIL`, `.VAMPIRE_BLOOD` missing. Concurrent in-flight work. |
| **B-2** | blocker | `Config.java:260` | `defineList("adminUuids", …)` — the `defineListAllowEmpty` fix `KNOWN_ISSUES.md` §8a.1 calls applied is **not in `HEAD`**, only uncommitted. `adminUuids`' own comment tells operators to clear the list, which plain `defineList` makes impossible. |
| **B-3** | blocker | `data/…/recipe/ministry_license_scroll.json` | `"count": 2` against `stacksTo(1)` (`TrinketItemRegistry.java:33`). `Couldn't parse data file … Item stack with stack size of 2 was larger than maximum: 1`. The scroll is uncraftable and every data load logs an ERROR. Fix: `"count": 1`. |
| **B-4** | blocker | `WizardsAndBeastsJeiPlugin.java:123-124` | `addIngredientsAtRuntime` / `removeIngredientsAtRuntime` are called unguarded; JEI's `ErrorUtil.checkNotEmpty` throws on an empty list. The throw escapes `onRuntimeAvailable` **and** the `ClientModuleState.whenApplied` callback, so `Failed to process a synchronized task of the payload: wizards_and_beasts:module_state_sync`. `hideRecipes` on line 126 never runs, so recipe hiding for disabled modules silently does not apply. Fix: skip each call when its list is empty. |
| **B-5** | blocker | working tree | 644 uncommitted files. |
| **F-1** | cheap | `lang/en_us.json` | `module.wizards_and_beasts.heritage.name` missing — the only one of 29. Renders as a raw key in `/wandb admin module list`. |
| **F-2** | open | `run/config/…-common.toml` | Correction loop, `KNOWN_ISSUES.md` §8a.1. **2489** corrections across one 44-minute session — far worse than the "9 to 11 per 20-second run" recorded. Five `.bak` slots exhaust in seconds, then rotation fails. **New evidence:** a *freshly generated* config on a clean `HEAD` boot logged **0** corrections, and the file it writes (13473 B, 82 keys) contains no `werewolf*` block — because `HEAD`'s `Config.java` has no such keys. The oscillation therefore tracks a `run/config` file written by a build whose `Config.java` differs from the one running. Deleting `run/config/wizards_and_beasts-common*.toml*` clears it. Root cause still not fully pinned. |
| **F-3** | medium | `WandmakersBenchBlockEntity` | `Failed to encode value '0 minecraft:air' to field 'slot_0'…` on every save with an empty slot. Encode empty slots as absent, not as air. |
| **F-4** | medium | `sounds.json` | 15 broom sound events point at vanilla `.ogg` files that do not exist in 1.21.11. Mount, dismount, both crash tiers, four boosts, wind tiers and polish are **silent**. |
| **F-5** | medium | `PowerBandTable` | Bands exist for the 5 Wizardkind lineages only. Every other variant logs `unknown variant … defaulting to (20, 70)`, and `HeritageCommitTests` asserts against that fallback. |
| **F-6** | low | `occamy_eggshell` | `Rejecting block model … contains sprites from outside of supported atlas: items.png`. |
| **F-7** | doc | `DEVELOPER_REFERENCE.md` §18, §21.1 | The `runServer` `FatalStartupException` claim is wrong — see §5. |
| **F-8** | doc | `ALPHA_SMOKE.md` | *"the repository has no `@GameTest` classes yet"* is stale: 11 scenarios across 4 classes pass. |

---

## 8. Release Notes template

```markdown
# Wizards & Beasts 0.1.0-alpha.N

**Minecraft** 1.21.11 · **NeoForge** 21.11.42 · **Java** 21
Alpha. Back up your worlds — alpha builds may contain compatibility-breaking changes.

## Highlights
- <one line per player-visible change>

## Added
## Changed
## Fixed
## Known issues
See [KNOWN_ISSUES.md](documentation/KNOWN_ISSUES.md). Carried into this build:
- <copy the still-open rows from the defect register>

## Module states in this build
`ENABLED` (16) · `PREVIEW` (9) · `DISABLED` (4) — full table in KNOWN_ISSUES.md §1–§3.
Operators change these with `/wandb admin module set <module> <state>`.
Module state is stored **per world** and seeded once, so editing the config afterwards does
not reach worlds that already exist.

## Verification
| Gate | Result |
|---|---|
| `./gradlew test` | N suites / N tests, 0 failures |
| `./gradlew runData` | clean, `written: 0` |
| `./gradlew runGameTestServer` | N/N scenarios passed |
| `./gradlew build` | jar produced |
| Dedicated server boot | clean, N ERROR / N WARN |
| Two-client smoke | pass / fail + what |

## Reporting a bug
Include: mod version, MC/NeoForge/Java versions, `/wandb admin module list` output,
whether the world was fresh or migrated, the exact reject code
(`/wandb debug spell rejects summary`), repro steps, `latest.log`, crash report.
```

---

## 9. The tag itself

Only after §1–§3 are green and §7 has no open blocker.

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.7.6-hotspot"
./gradlew --no-daemon test runData runGameTestServer build

git status --porcelain          # must be empty
git tag -a v0.1.0-alpha.N -m "Wizards & Beasts 0.1.0-alpha.N"
git push origin v0.1.0-alpha.N
```

**RC policy, unchanged:** if any gate fails, do not tag until `KNOWN_ISSUES.md` records the
environment, the reproduction and the logs. CI names that file in its own failure notice.

> This tree is dirty and mid-edit by another session; commit with an explicit pathspec
> (`git commit -m "…" -- <paths>`) so a bare `git add` cannot sweep in unrelated staged work.
