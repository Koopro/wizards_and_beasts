# Heritage and player-rules administration

Phase 3 of the Control Center, built on `ADMIN_FRAMEWORK.md` and following the patterns of `ADMIN_MAGIC.md`.
It adds the **Heritages** section: a heritage browser with per-heritage rules and a preview, the global
heritage/progression rules, an onboarding preview, and a player inspector that can assign a heritage or send
one player back to onboarding.

It adds no second heritage model, stat store or permission system:

- Heritage rules are ordinary admin settings. They go through `AdminSettingService` for authorisation,
  validation, confirmation, history, undo and broadcast.
- Player changes go through the routine the onboarding gate already uses (`HeritageAPI.commit` / `clear`),
  now wrapped once in `HeritageAssignment`.
- Every number shown is read from the store the game reads.

---

## 1. What already existed, and where admin hooks in

| System | Where | Admin hook |
|---|---|---|
| Heritages and lineages | `Heritage`, `HeritageVariant` (enums, both sides) | The browser lists `Heritage.values()`. Rule ids are derived from the enum. |
| "May a new character pick this?" | Was the compile-time `Heritage.isAlphaAvailable()` | Now `HeritageRules.selectable(h)`: the override if set, else the shipped flag. |
| Onboarding gate | `HeritageOnboarding` (login prompt, `Module.HERITAGE`) → `HeritageSelectionScreen` → `HeritageSelectC2SPayload` | The packet re-checks the server's rules. The screen reads the synced rules. A preview mode is added. |
| Commit / reset | `HeritageAPI.commit`, `clear(openSelector)` | `HeritageAssignment.select / assign / resetOnboarding` (profession points and the right event, written once). |
| Stats | `PLAYER_STATS` via `PlayerStatsAPI`; POWER band from `PowerBandTable` | The inspector reads them; the preview shows the band. |
| Derived attributes | Vanilla/mod `AttributeInstance`s (heritage adds one `ADD_VALUE` modifier each to health, speed and armour in `HeritageAPI.applyStats`) | Read live, never stored. |
| Two-form change | `HeritageTransformService` (Veela, Vampire, Merpeople) | `heritage/<id>/transformation` gates **entering** only. |
| Werewolf, vampire rules | `WerewolfConfig`, `VampireBloodConfig` (keys in `Config.SPEC`) | Catalog entries, filed under Heritages. |
| Heritage system, progression | `Module.HERITAGE`, `Module.PLAYER_STATS` | Module settings (`ModuleStateSettings`). |

## 2. Layers

```
heritage/rules/HeritageRule            optional selectable / transformation override
heritage/rules/HeritageRules           pure; local layer (server) + remote layer (synced, remote servers only)
heritage/rules/HeritageRulesData       world SavedData (per world, like spell tuning)
heritage/rules/HeritageRulesService    only writer: store → publish → broadcast → settle transformed players
network/heritage/HeritageRulesSyncS2CPayload + client/heritage/HeritageRulesClient

heritage/HeritageAssignment            select (gate) / assign (admin) / resetOnboarding (admin)

admin/heritage/HeritageRuleSettings    one AdminSetting per heritage × applicable property
admin/heritage/HeritageAdminService    players / inspect / assign / resetOnboarding (PLAYERS capability)
admin/command/AdminHeritageCommands    /wandb admin heritage …
network/admin/AdminHeritagePayloads + AdminHeritageNetworkService

client/admin/screen/HeritagePanel       tabs: Heritages | Rules | Players
client/admin/screen/HeritageBrowserPanel, HeritagePlayersPanel
client/admin/preview/HeritagePreview    measured, stateless heritage preview (dossier, traits, stats, derived)
```

`HeritageRules` has two layers for the same reason as `SpellTuning`. The onboarding screen on a client connected
to a dedicated server must show that server's rules, not the shipped flags. A client on its own integrated
server never adopts the synced copy, because its local layer already is the server's.

## 3. Rules

### Per heritage (Heritages tab, on each heritage's page)

| Setting id | Default | Enforced at | Danger |
|---|---|---|---|
| `heritage/<id>/selectable` | `Heritage.isAlphaAvailable()` | `HeritageSelectC2SPayload.apply` (server), the selection screen (display) | Opening a heritage that does not ship as finished asks for confirmation. |
| `heritage/<id>/transformation` (only for heritages in `HeritageTransformService.SERVED`) | `true` | `HeritageTransformService.changeTo` (entering only) and `grantsFor` (wheel button) | none |

Rules and guarantees:

- **At least one heritage stays selectable.** Closing the last one is refused as `CONFLICT`
  (`conflict.last_selectable_heritage`). The onboarding screen swallows ESC, so with nothing selectable no new
  player could ever pass it.
- **Closing a heritage affects new characters only.** Players who already have it keep it. An administrator can
  still assign a closed heritage (see §5); the reply says it is closed.
- **Closing a transformation never traps anyone.** The way out stays open. Players currently transformed are
  brought back through the ordinary change, and every affected player's ability wheel is re-granted at once.
- **Storage.** An override equal to the shipped value is removed rather than stored, so a later release that
  finishes a heritage is not masked.

### Global (Rules tab)

| Setting | Source | Danger |
|---|---|---|
| `module_heritage` | `Module.HERITAGE`: onboarding prompt and every heritage mechanic | Opening **and** closing |
| `module_player_stats` | `Module.PLAYER_STATS`: stat training, cast modifiers and study. The POWER roll is always recorded. | Opening **and** closing |
| `werewolf_forced_transform` | `werewolfEnableForcedTransform` | none |
| `werewolf_loss_of_control` | `werewolfLossOfControl` | none |
| `werewolf_bite_infects` | `werewolfBiteInfects` | Turning on |
| `werewolf_wolfsbane_suppresses_transform` | `werewolfWolfsbaneSuppressesTransform` | none |
| `werewolf_pack_betrayal` (PvP) | `werewolfPackBetrayal` | Turning on |
| `vampire_feed_on_players` (PvP) | `vampireBloodAllowFeedingOnPlayers` | Turning on |

### Asked for but not built, because no mechanic exists

Each of these would be a new game mechanic, not an admin view of an existing one:

- **"Playable" separate from "selectable".** Nothing in the mod can take a heritage away from the players who
  already have it, and stopping a heritage's mechanics per heritage has no gate. `module_heritage` is the
  existing all-heritages switch.
- **Per-heritage PvP restrictions.** The only heritage PvP rules that exist are the two above.
- **Per-heritage ability toggles.** Heritage abilities are status grants (`PlayerStatusAbilityGrantSource`) owned
  by their systems: Veela allure, the form toggles, and the werewolf and Obscurial logic. Only the
  transformation has a single gate, so it is the only one exposed.
- **Per-heritage progression.** Progression is `Module.PLAYER_STATS`; the POWER band is `PowerBandTable` (code).
- **Rarity.** The mod has none. The only chance is the 1.5% prodigy roll, which is shown with the POWER band.
- **Lineage-level selectability.** This would need `HeritageRule` keyed by lineage and a check beside the heritage
  check in `HeritageSelectC2SPayload.apply`. It is small, but not requested as a mechanic yet.

## 4. The browser and preview

**Heritages tab.** The list is `Heritage.values()`. Each heritage has a crest in its own colour (the mod ships no
heritage icons) and a ✕ when it is closed. A heritage's page shows:

- **Header.** The name, a status line (Selectable / Closed by an administrator / Coming soon, magic source, size,
  no-wand, transforms) and the description.
- **Rules.** The rule rows are ordinary setting rows. Edits are drafts until Apply, and dangerous edits come back
  from the server for confirmation.
- **Preview.** A lineage selector and a **Preview onboarding** button. The button opens
  `HeritageSelectionScreen.preview(returnTo)`, which is the real onboarding screen under the current rules. It
  sends nothing, confirming just closes it, and ESC returns to the Control Center.
- **Lineages.** The heritage's lineages, marked when they transform.
- **`HeritagePreview`.** The onboarding dossier and traits (`HeritageDossierRenderer`, shared with the gate), the
  five stats and the derived values:
  - **POWER:** the band it is rolled in and its growth cap, from `PowerBandTable`.
  - **Precision, Willpower, Reflexes:** trained from 0.
  - **Knowledge:** derived.
  - **Derived values:** the lineage's modifiers to max health, armour and speed (exactly what `applyStats` adds),
    and "not affected by heritage" for Wand Affinity, Dark Corruption and Beast Resistance.

  Derived lines are marked ◇ with a note that they are computed, not stored.

`HeritagePreview` is a measured, stateless renderer (`measure` then `render`), so a creature or form preview can
follow the same shape later.

## 5. Players tab (the testing tool)

Everything here needs the **players** capability. A reader without it sees a notice, and the server refuses or
drops the requests anyway.

- **Online players.** Each shows its heritage crest.
- **Inspect.** Selecting a player shows three groups of facts, read on the server when asked:
  - **Heritage:** heritage, lineage, condition, onboarding complete/pending, shape, and whether the heritage is open.
  - **Stats (stored):** the five stats from `PlayerStatsAPI`, with POWER's band and cap and whether it was a
    prodigy roll.
  - **Derived values:** `AttributeInstance.getValue()` and the base value, live. An attribute players do not
    carry (Wand Affinity, Beast Resistance: nothing writes them yet) says "not tracked" instead of inventing 0.
- **Assign.** Choose a heritage and lineage, then confirm in a dialog. `HeritageAssignment.assign`:
  - The profession restarts. The body, blood pool and ability grants are recomputed.
  - A different lineage re-rolls POWER; the same lineage keeps it.
  - Trained stats and skills are kept.
  - It posts `PlayerHeritageChangedEvent`, never `Selected`, so no starting skill points are paid again.
- **Reset onboarding.** Confirm in a dialog. `HeritageAssignment.resetOnboarding` takes the heritage off
  completely (body, POWER roll, heritage grants) and reopens the onboarding screen on that player's client.
  Trained stats and skills are kept. Choosing again pays the starting skill points again, as the old
  `/wandb player heritage reset` always did.

**No bulk operation exists.** Every destructive request names exactly one online player by UUID and must arrive
with `confirmed = true`. The server refuses an unconfirmed request (`heritage_action.confirm_required`), so a
client that skips the dialog changes nothing. Each action is logged with the acting administrator.

## 6. How to add a heritage without touching the admin UI

1. Add the constant to `Heritage` and its lineages to `HeritageVariant`, as for the game itself. That includes
   the display names and the `type.wizards_and_beasts.<id>.desc` key the onboarding screen already needs.
2. Nothing else. The following follow automatically:
   - **Browser:** lists it (`Heritage.values()`).
   - **Rules:** `HeritageRuleSettings.contribute` registers `heritage/<id>/selectable`, defaulting to its
     `alphaAvailable` flag.
   - **Transformation rule:** added only if you put the heritage in `HeritageTransformService.SERVED`.
   - **Preview:** reads its dossier, traits (from lineage tags), POWER band and modifiers.
   - **Players tab:** offers it in the assign selector.
   - **Commands:** suggest it.

   The per-property texts are shared (`admin.wizards_and_beasts.heritage_property.<property>`), so no new admin
   lang keys are needed.

**To add a new per-heritage rule:**

- It must gate an existing single decision point, as the selection packet and `changeTo` do.
- Then add:
  - a field to `HeritageRule`, its flag in `HeritageRulesSyncS2CPayload` and a reader in `HeritageRules`;
  - a `Property` constant, with a case in `HeritageRuleSettings.applicable`/`build`;
  - the check at the decision point;
  - three lang keys (`heritage_property.<p>`, `.desc`, and `.warning` if it is dangerous).

  `AdminCatalogLangTest` fails until the keys exist.
- If no single decision point exists, it is a refactor first, not a setting.

## 7. Commands

Rules are settings:

```
/wandb admin config set wizards_and_beasts:heritage/goblin/selectable true      (asks /wandb admin config confirm)
/wandb admin config reset wizards_and_beasts:heritage/veela/transformation
```

The player tools, with the same service and checks as the panel:

```
/wandb admin heritage players
/wandb admin heritage inspect <player>
/wandb admin heritage assign <player> <heritage> <lineage> [confirm]
/wandb admin heritage reset_onboarding <player> [confirm]
```

Without `confirm`, a destructive verb explains what it would do and prints the confirming command. The existing
`/wandb player heritage set|reset` commands remain and now run `HeritageAssignment` too.

## 8. Tests

- **JUnit**
  - `HeritageRulesTest`: shipped defaults, overrides in both directions, refusal reasons, remote layer.
  - `AdminHeritagePayloadCodecTest`: rules sync, player payloads, derived ids, lang-key derivation (and spell
    keys unchanged).
  - `AdminCatalogLangTest`: heritage property, module and conflict texts.
  - `CommandTreeShapeTest`: `admin heritage` paths.
- **GameTest** (`AdminHeritageTests`, 6 scenarios)
  - A closed heritage is refused at the server gate whatever the client sends, and reopening restores
    onboarding. A locked character cannot re-choose. Opening an unfinished heritage needs confirmation.
  - Closing the last selectable heritage is a conflict.
  - A non-admin cannot change a rule, receive the player list or an inspection, or assign or reset, even with
    `confirmed` set. An administrator without the players capability cannot either. Unconfirmed and mismatched
    requests change nothing.
  - Assign and reset keep trained stats and do not pay the starting skill points again.
  - The inspector's max health is the player's live attribute value, and an untracked attribute is reported as such.
  - A closed transformation removes the form button and the way in, and the rule reaches the client in a sync.
