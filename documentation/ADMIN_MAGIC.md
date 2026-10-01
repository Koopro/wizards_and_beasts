# Magic and spell administration

Phase 2 of the Control Center, built on the framework in `ADMIN_FRAMEWORK.md`. It adds the Magic and Dark
Arts sections: global spell rules, a spell browser, per-spell overrides, and server-validated test casts.

It adds no second configuration framework, permission system or spell model. Every per-spell value is an
ordinary admin setting. It goes through `AdminSettingService` (authorisation, parsing, bounds, rules,
confirmation, history, undo, broadcast) and changes one accessor the existing cast pipeline already reads.

---

## 1. What the spell architecture already had, and where admin hooks in

| Concern | Where it lives | How admin reaches it |
|---|---|---|
| Spell definitions | ~145 datapack `JsonSpell`s (`SpellDefinition`, reloadable, synced by `SpellDefinitionsSyncS2CPayload`) and 7 Java spells (`Spells`) | read-only facts on the detail page; never mutated |
| Cooldown | every consumer reads `Spell.getBaseCooldownTicks()` (cast, obscurial abilities, HUD, spell menu, wheel, commands) | the getter consults `SpellTuning` |
| Damage | every consumer reads `Spell.getBaseDamage()` (12 sites) | the getter consults `SpellTuning` |
| Range | every consumer reads `spell.getProperties().getRange()` (7 sites) | `getProperties()` returns a range-adjusted copy, cached per tuning version |
| Prerequisite | `Spell.getRequirement()` — the cast gate (when `enforceSpellRequirements`) **and** learning | the getter consults `SpellTuning` |
| Learning skill | `SpellLearningEligibility` → `getRequiredSkillId()` | now reads `Spell.getEffectiveRequiredSkillId()` |
| Cast authority | `SpellCastService.completeWandCastRelease` → pure `SpellCastGate.evaluate`; held beams re-check every tick through `releaseWouldBeRefused` → `evaluateGate`; Obscurial abilities through `ObscurialServerLogic` | new gate `SPELL_DISABLED`; same check on the ability path |
| Classification | `SpellCategory` (4, gameplay), `SpellLawRegistry` legal class (datapack: UNRESTRICTED / RESTRICTED / DARK / UNFORGIVABLE) | `SpellAvailability.isDark` / `isUnforgivable`; the browser filters by the categories the spells actually have |
| Block damage | `SpellHelper.createExplosion` (4 callers), `SpellEffectComponent.Explosion`, `SpellHelper` fire placement | one `spellBlockDamage` check at each choke point |

The release/session machinery (`WandCastSessions`, `CastReleaseGate`, release tokens, the ordering of
vanilla's release packet) is **unchanged**. Admin state is one more input to the existing pure gate, not a
new path around it. The whole existing cast/session game-test suite passes unchanged.

**Things that do not exist, so are not faked:**

| Asked for | Why it isn't here |
|---|---|
| spell resource/mana cost | no cost system exists; the detail page says "Cost: none" |
| "spell experimentation" | no such system |
| per-spell heritage allow-lists | the only heritage rule is Obscurial-only abilities, shown as a fact |
| friendly-fire rules | spells follow vanilla PvP (`pvp` server property) and Protego's `isAlliedTo` ward exemption; a spell-specific rule would need a damage-pipeline refactor |
| per-spell wand conditions | a bonded wand is required and wand affinity scales by family (shown as a fact) |

## 2. Layers

```
spell/tuning/            (common, pure)
  SpellOverride            per-spell optional overrides: enabled, cooldownTicks, damage, range, requirement, requiredSkill
  SpellTuningSnapshot      overrides + Globals (damage/cooldown/range multipliers, allowUnforgivables, blockDamage)
  SpellTuning              the live snapshot: local layer (server owns) + remote layer (client of a remote server)
  SpellRequirementText     text form of SpellRequirement: none | id | id@proficiency | a+b
  SpellAvailability        castAllowed / isDark / isUnforgivable (reads the datapack spell law)
  SpellTuningData          world SavedData holding the overrides
  SpellTuningService       the only writer: store → publish → SpellTuningSyncS2CPayload to every client

admin/spell/             (server logic)
  SpellProperty            ENABLED, COOLDOWN_TICKS, DAMAGE, RANGE, PREREQUISITE, REQUIRED_SKILL
  SpellSettingIds          wizards_and_beasts:spell/<ns>/<path>/<property>
  SpellSettingProvider     resolves those ids into AdminSettings, live, per lookup
  SpellAdminService        browser rows, detail facts, batch resets
  SpellTestService         validated test casts

admin/config/catalog/ModuleStateSettings   the Dark Arts module as a setting, bound to ModuleStateService
admin/command/AdminSpellCommands           /wandb admin spell …
network/admin/AdminSpellPayloads           list / detail / reset / test requests and replies
network/spell/SpellTuningSyncS2CPayload    every client's copy of the tuning state
client/admin/screen/MagicPanel             tabs: Rules (SettingsPanel) | Spells (SpellBrowserPanel)
client/admin/screen/DarkArtsPanel          dark rules + dark spell overview
client/admin/SpellPreviewClient            local sound + particles preview
SpellDiamondOverlay.renderPreview          the real HUD renderer, drawing a preview
```

### Why a provider, not registration

Spells arrive with datapacks and change on `/reload`. Registering 150 × 6 settings at startup would freeze a
list that a reload invalidates. `AdminSettingRegistry.addProvider` lets `SpellSettingProvider` answer
"is `spell/…/cooldown_ticks` a setting, and what is it right now?" on every lookup, using the live spell
registry.

A provider-built setting is an ordinary `AdminSetting`: the same service, change/reset payloads, history,
undo, observers and commands handle it. Nothing in the admin core knows spells exist.

### Why `SpellTuning` has two layers

The server owns the state: world overrides plus globals pushed from `Config.onLoad`.

- **Remote server.** The client must show the server's numbers, not its own config file's. The HUD cooldown
  sweep, the spell menu and the wheel all read `getBaseCooldownTicks()`, so the synced snapshot becomes the
  remote layer and wins while it is set.
- **Own integrated server.** The client ignores the sync (`SpellTuningClient`). The local layer is already
  the server's, and adopting a copy one packet behind would hand the server thread stale values in the same
  JVM.

The remote layer is cleared on logout; the local overrides are cleared when a world closes.

## 3. Server validation

A per-spell change is `AdminSettingService.change(actor, spell/<ns>/<path>/<prop>, text, confirmed)`. In order:

1. **Actor.** Must be an admin at all (`AdminContext`, reusing `AdminAccess`); a non-admin gets
   UNAUTHORIZED even for ids that don't exist, so it can't probe.
2. **Setting exists.** The id must parse (`SpellSettingIds`); the spell must exist (bare Java ids resolve via
   `SpellSettingIds.resolveSpell`); the property must apply to that spell. There is no damage setting for a
   spell that deals none, and no range setting for a projectile. Otherwise: UNKNOWN_SETTING.
3. **Capability.** The actor must hold `admin.content`.
4. **Store available.** A server must be running.
5. **Type and bounds.**

   | Property | Allowed values |
   |---|---|
   | cooldown | 0–72000 ticks |
   | damage | 0–1000 |
   | range | 0.5–128 |
   | prerequisite | must parse as requirement text |
   | skill | lowercase id characters, ≤128 |

6. **Rules** (CONFLICT with a detail key):
   - every prerequisite spell must exist;
   - following prerequisites (including other overrides) must never lead back to the spell. A cycle would
     make it unlearnable for everyone.
   - a required skill must be a real skill node.
7. **Confirmation** (next section).
8. **Store and sync.** The value is stored in `SpellTuningData`, published to `SpellTuning`, synced to every
   client, and recorded in history; other admins are notified.

Setting the authored value **removes** the override instead of pinning a copy. A later datapack edit to that
value is not masked.

## 4. Dangerous changes need confirmation, and the server enforces it

`AdminSetting.DangerRule` decides per change, from the previous and candidate values. The service answers an
unconfirmed dangerous change with `CONFIRMATION_REQUIRED` plus the warning key: **nothing is stored and
nothing is recorded**.

- **Panel.** Drafts are sent unconfirmed. The server holds the dangerous ones back, and the screen shows a
  dialog with each warning (`ClientAdminState.takeConfirmRequests`). On Apply it resends them with
  `confirmed`. The client never decides what counts as dangerous.
- **Commands.** The held change is parked per actor in `AdminConfirmations` for 60 seconds.
  `/wandb admin config confirm` replays it with confirmation. A command's value argument is greedy, so it
  cannot take a `confirm` suffix.
- **Explicit steps count as confirmation:** undo, section reset, `reset_all confirm`, and the reset-spell or
  reset-category dialogs.

Dangerous changes in this phase:

| Setting | Asks when |
|---|---|
| `allow_unforgivable_curses` | turned **on** |
| a single spell's `enabled` | an Unforgivable is switched back **on** |
| `module_dark_arts` | opened from DISABLED |
| `enforce_spell_requirements` | turned **off** (disabling prerequisites) |
| `spell_damage_multiplier`, `spell_range_multiplier` | above 2× default |
| `spell_cooldown_multiplier` | below ½ default |
| per-spell `damage`, `range` | above 2× the authored value |
| per-spell `cooldown_ticks` | below ½ the authored value |
| per-spell `prerequisite` | set to `none` when one was authored |
| per-spell `required_skill` | cleared when one was authored |
| spell power bounds | always (unchanged from Phase 1) |

## 5. Global rules (Magic → Rules, Dark Arts)

New `Config` keys, surfaced by `ConfigSettingCatalog` and pushed into `SpellTuning.Globals` by `Config.onLoad`:

| Key | Default | Effect |
|---|---|---|
| `spellDamageMultiplier` | 1.0 (0–10) | scales every base damage; the spell-power soft cap still bounds bonuses |
| `spellCooldownMultiplier` | 1.0 (0.1–10) | scales every base cooldown; the cooldown floor still bounds bonuses |
| `spellRangeMultiplier` | 1.0 (0.25–4) | scales targeted, cone and beam range |
| `allowUnforgivableCurses` | true | false refuses every spell the spell law classes UNFORGIVABLE, at the cast |
| `spellBlockDamage` | true | false: spell explosions use `ExplosionInteraction.NONE`; spells place no fire |

`enforceSpellRequirements` moved from Game Rules to Magic → Rules. The Dark Arts module is exposed as
`module_dark_arts`, bound to `ModuleStateService.setState` (a view, not a copy; COMING_SOON stays locked).

**Disabling is not forgetting.** A disabled or refused spell can still be learned, and players keep what they
know. Re-enabling restores exactly what they had.

## 6. How to …

### Add a spell to the browser

Register it the normal way: a datapack JSON, or `Spells.register`, or `RegisterSpellsEvent`. The browser lists
`Spells.all()` from the server, so it appears after the next `/reload` or restart. Its category, cast type,
legal class, colour, icon (`WandHudSprites.spellIcon`) and facts all come from its own definition.

To give its detail page a description, add `admin.wizards_and_beasts.spell_fact.*` facts in
`SpellAdminService.facts`. There is no per-spell prose field in the mod.

### Register a new editable spell property

1. Add the field to `SpellOverride`: record component, `with…`, `isEmpty`, codec.
2. Add a flag and read/write to `SpellTuningSyncS2CPayload`.
3. Add the accessor in `SpellTuning`, and make the **one** pipeline accessor read it. If no single accessor
   exists, stop: that is a refactor, not a setting.
4. Add a constant in `SpellProperty`, a case in `SpellSettingProvider.build` (type, bounds, validators, danger
   rule), and applicability in `SpellSettingProvider.applicable`.
5. Add the lang keys `admin.wizards_and_beasts.spell_property.<id>`, `.desc` and `.warning`.

The panel row, the command (`/wandb admin spell set <spell> <id> <value>`), history, undo, reset-spell and
reset-category then work without further code.

### Add a global spell rule

Add a key in `Config.java` and a `ConfigSettingCatalog` entry (MAGIC or DARK_ARTS, with a `Danger`). If
gameplay reads it where `Config` must not be touched, push it through `SpellTuning.Globals` from `Config.onLoad`.

## 7. Testing a spell

**Test** (panel) or `/wandb admin spell test <spell> self|looked_at|nearest_dummy|player <name>`
→ `SpellTestService.test`.

- **The client names a kind of target, never an entity.** The server resolves it:
  - `LOOKED_AT`: its own ray from the admin's eyes, 32 blocks, with line of sight;
  - `NEAREST_DUMMY`: the nearest `DuellingDummyEntity` within 24 blocks;
  - `PLAYER`: an online player by UUID, in the same dimension, within 32 blocks. This needs `admin.players`.

  An unknown mode decodes to `SELF`.
- **Self-cast spells** need `SELF`; everything else needs a real target. Held beams are refused, because they
  need a wand held every tick.
- **The cast is real.** The admin is turned to face the target, then the spell runs through
  `SpellCastService.contextFor` → `SpellExecutor.executeGeneric`: wand, allegiance, proficiency and modifiers
  all apply.
- **Skipped, like `/wandb magic spell cast`:** knowing the spell, its requirement, its cooldown and its
  enabled switch. An admin may test before enabling. No cooldown is stamped.

**Preview** (panel only, `SpellPreviewClient`) is local: the spell's cast sound, plus its family's tinted
particles laid out by cast type (streak, beam, cone, burst). Nothing is sent. Preview and Test briefly
**peek**: the Control Center hides for 2–2.5 s so the world is visible, and any click or key returns early.

**HUD preview** draws `SpellDiamondOverlay.renderPreview`, the real HUD renderer's sprites, slot geometry and
cooldown sweep, with the selected spell armed.

## 8. Commands

`/wandb admin spell`:

- `list [dark|unforgivable|disabled|changed|<category>|<text>]`
- `info <spell>`
- `enable <spell>` / `disable <spell>`
- `set <spell> <property> <value…>`
- `reset <spell> [property]`
- `reset_category <category>`
- `test <spell> self|looked_at|nearest_dummy|player <p>`

`/wandb admin config confirm` applies a held dangerous change.

All go through the same services as the panel. The existing `/wandb magic spell …` tree (learn, forget,
cast, cooldowns) edits players' spell knowledge, not server state, and is unchanged.

## 9. Tests

| Test | What it proves |
|---|---|
| `SpellTuningTest` (JUnit) | untouched tuning returns authored values exactly; overrides; multipliers; remote layer wins and clears; requirement text round-trip/normalisation/rejection; setting ids |
| `SpellCastGateTest` | `SPELL_DISABLED` outranks every caster-side gate, is outranked by `SPELL_NOT_IMPLEMENTED`; the 11-argument constructor means enabled |
| `AdminConfirmationTest` | unconfirmed dangerous change held and unrecorded; confirmed applies; only the dangerous direction asks; validation before the question; confirmation never bypasses authority; resets can be dangerous; command confirmations per actor, once, expiring; provider settings share service, history and undo |
| `AdminSpellPayloadCodecTest` | tuning sync round-trips every field; non-finite multipliers neutralised; spell payloads round-trip; unknown target mode decodes to SELF |
| GameTest `admin_magic_disabled_spell_cannot_cast` | release gate and per-tick beam re-check refuse it; no count, no cooldown, `spell_disabled` recorded; re-enabled, the real cast succeeds |
| GameTest `admin_magic_unforgivables_rule` | the rule refuses exactly the Unforgivables; re-allowing, and re-enabling a single Unforgivable, need confirmation |
| GameTest `admin_magic_values_are_validated` | out-of-range, malformed, inapplicable, unknown spell, unknown prerequisite, self-cycle, two-spell cycle, unknown skill are refused and leave no override |
| GameTest `admin_magic_unauthorised_cannot_touch_spells` | non-admin cannot change, list, see a page, or test-cast |
| GameTest `admin_magic_prerequisite_override_is_enforced` | an overridden prerequisite gates the cast and learning; learning it opens the cast; removing one asks |
| GameTest `admin_magic_tuning_syncs_to_clients` | the cast accessor reads the new cooldown at once; every client gets a sync; slashing below half asks; reset removes the override |
| GameTest `admin_magic_test_cast_targets_are_server_chosen` | no dummy: refused; dummy in range: cast, no cooldown; projectile at self, held beam, absent player all refused |

`BroomRideTests.letGoBroomSettles` was made to force and wait for its chunk (the repo's `forceChunks` +
`checkChunksTick` idiom). It had passed only while a neighbouring scenario's player happened to keep its
chunk ticking; the new scenarios changed the batch layout and exposed it.

## 10. Limitations and next steps

- Values that other clients read (HUD cooldown, spell menu) follow the sync. Only clients actually connected
  receive it, which is all of them.
- `SpellDefinition` values that are not read through a single accessor are shown but not editable:
  knockback, effect duration, projectile speed, explosion power, effects. Making them editable is a
  pipeline refactor per value.
- The live preview is local particles and sound. It does not render the real projectile or beam entity, and
  the cast animation is not previewed. It is the entry point for the Beam/Visual Editor.
- The browser's description is composed from real values; there is no spell prose in the mod.
- History is still in-memory (Phase 1). Spell overrides themselves persist in the world.
