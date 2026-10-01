# Configuration profiles, snapshots and history

Phase 10 of the Control Center (2026-10-01): **Profiles** (Profiles | Snapshots | History | Import) and
`/wandb admin profile …`. A profile is a named set of server setting values and module states. The shipped
presets, an administrator's saved profiles, snapshots and imported files are all the same thing — one document
format, one validator, one applier — and every value they touch goes through `AdminSettingService`, the same door
as a single edit in the panel.

**The rule:** nothing is changed until the whole profile has been validated, and then either every change lands
or none does.

---

## 1. Pieces

| Piece | Where | Responsibility |
|---|---|---|
| Document | `admin/profile/ProfileDocument` | schema version, mod version, `Meta` (id, name, description, author, created, kind), mode, settings, modules. Plain strings only |
| Codec | `ProfileCodec` | `parse(text)` never throws: size/entry/length caps, schema id, version window, field types → `Parsed(document, errors, warnings)`. `write` emits sorted, pretty JSON |
| Ids | `ProfileIds` | profile ids `[a-z0-9_]{1,48}`, names, import file names (`[A-Za-z0-9_.-]{1,64}.json`, no `..`) |
| Target | `ProfileTarget`, `LiveProfileTarget` | what the validator may ask about the server: every setting's facts, a typed check of a value, module states. Tests use a fake |
| Validator | `ProfileValidator` | document → `Plan(changes, errors, warnings, resetToDefault)`; `applicable()`, `needsRestart()`, `touchesWorldgen()` |
| Deprecations | `DeprecatedSettings` | renamed / removed setting ids, read on import. Empty today |
| Applier | `ProfileApplier` | the all-or-nothing apply (below) |
| Service | `ProfileService` | presets, stored profiles, capture, save as / duplicate / rename / delete, preview, apply, revert group, export, import |
| Storage | `AdminProfileData` | world SavedData `wizards_and_beasts_admin_profiles`: profiles (≤ 64) and snapshots (≤ 32), each stored **as its export text** |
| History | `admin/history/AdminHistoryData`, `AdminHistoryStore` | world SavedData `wizards_and_beasts_admin_history`, last 1000 records, written through from the in-memory history |
| Commands | `admin/command/AdminProfileCommands` | `/wandb admin profile …` |
| Network | `network/admin/AdminProfilePayloads` | list / preview / action requests and replies |
| Screen | `client/admin/screen/Profiles*Panel`, `ProfileText` | the Profiles section |

Every setting the registry can reach is covered, including provider settings (spells, brews, recipes, wands,
brooms, beam looks): `AdminSettingProvider.enumerate(server)` lists a provider's ids, and
`AdminSettingRegistry.everything(server)` is static settings plus every enumerated one.

## 2. Format (schema 1)

```json
{
  "schema": "wizards_and_beasts:admin_profile",
  "schema_version": 1,
  "mod_version": "0.4.0",
  "profile": { "id": "hardcore", "name": "Hardcore", "description": "…", "author": "…", "created": 0, "kind": "custom" },
  "mode": "replace",
  "settings": { "wizards_and_beasts:spell_damage_multiplier": "1.5" },
  "modules": { "ministry": "enabled" }
}
```

- **Values are the setting type's canonical text**, the same text `/wandb admin config set` takes and the history
  records. No Java object is serialised; a value is parsed and bounded by its setting's `SettingType` on the way in.
- **Sparse.** A captured profile names only values that differ from the default. `module_*` settings are written
  under `modules` by module id, not under `settings`.
- **Mode.** `replace` (default): every server setting the profile does not name goes back to its default — the
  profile *is* the configuration. `merge`: only the named values change.
- **Kind.** `preset` (shipped), `custom` (saved or imported), `snapshot`. A file's own kind is not trusted: a
  shipped file is always a preset, an import always custom.
- **Versions.** `schema_version` must be between `OLDEST_SUPPORTED_VERSION` and `CURRENT_VERSION` (both 1). A
  future version is refused as `incompatible_version`, never guessed at. `mod_version` is informational.
- **Caps.** 512 KB, 20 000 entries, keys ≤ 256 chars, values ≤ 2 048, names ≤ 48. Exceeding any is an error.

Bumping the schema: raise `CURRENT_VERSION`, teach `ProfileCodec.parse` the old shape (or raise
`OLDEST_SUPPORTED_VERSION` to drop it), add renamed ids to `DeprecatedSettings`.

## 3. Built-in presets

`src/main/resources/data/wizards_and_beasts/admin_profiles/*.json`: **Default** (everything at its default — the
way back), **Hogwarts RP**, **Sandbox**, **Hardcore**, **Developer**. They are ordinary profile documents in the
export format, read from the mod's resources, validated like any import, and applied through the same applier.
They contain values, not behaviour: applying Hardcore sets `spell_damage_multiplier` to 1.5 and so on, and every
one of those values can be changed afterwards like any other. There is no "hardcore mode" in code. A preset cannot
be renamed or deleted (`builtin`); Duplicate makes an editable copy.

Adding a preset: drop a file in that folder. The id is the file name. `ProfileCodecValidatorTest` parses and
validates every shipped preset against the real setting list.

## 4. Validation (`ProfileValidator`)

Run for preview, again on apply (the preview may be old), and on import. For every entry:

| Check | Result |
|---|---|
| deprecated id (renamed) | read under the new id, warning `deprecated_renamed` |
| deprecated id (removed) | skipped, warning `deprecated_removed` |
| unknown setting | skipped, warning `unknown_setting` |
| client-only setting | skipped, warning `client_setting` (display preferences stay with each player) |
| unknown module | skipped, warning `unknown_module` |
| module that cannot change (coming soon) | left as is, warning `module_locked` |
| value does not parse / wrong enum | **error** `invalid_value` |
| value outside the setting's bounds | **error** `out_of_range` |
| actor may not change the setting | **error** `unauthorized` |
| module states that break a REQUIRES dependency | **error** `dependency` (the same text the Modules page uses) |
| module states that weaken a PARTIAL dependency | warning `dependency_partial` |

Settings the profile does not name: in `replace` mode each one not at its default becomes a change back to
default (`resetToDefault` counts them). Each change carries its `ApplyMode` (RUNTIME / RELOAD / RESTART /
NEW_CHUNKS) and module, so the preview says **RESTART REQUIRED** and **NEW CHUNKS ONLY** before anything is applied.

Any error → the plan is not applicable, the preview lists every error, and apply is refused without touching
anything. Cross-setting rules (last selectable heritage, last creature variant, last wand pairing, brew shadowing,
spell prerequisites) are not re-implemented here: they are the settings' own validators, enforced by the service
during apply.

## 5. Applying (`ProfileApplier`)

1. **Stale check.** Every change's `from` must still be the live value; otherwise nothing is touched (`stale`).
2. **Order.** Module changes first, in `ModuleProfilePlanner`'s dependency-safe order, then the rest.
3. **Apply** inside `ModuleStateService.withoutDatapackReload` and `AdminSettingService.withGroup(group)`: every
   change through `changeAs(actor, id, value, PROFILE)` (pre-confirmed — the administrator confirmed the whole
   profile). A change refused with `CONFLICT` (a cross-setting rule that depends on order, such as opening one
   heritage before closing another) is retried in a later pass, up to 6 passes.
4. **Any change still refused → rollback.** Every applied change is set back in reverse order (kind `REVERT`), and
   the group's records are marked undone. The result lists every failure; a rollback failure is reported separately.
5. **One datapack reload** after the batch if a module changed (`ModuleStateService.reloadAfterBatch`), instead of
   one per module.

Every change of one apply shares a history group: `profile:<id>:<millis>` or `snapshot:<id>:<millis>`.

## 6. History

- `AdminChangeRecord` gained `group` (null outside a batch) and the kinds `PROFILE` and `REVERT`.
- `AdminHistoryStore` attaches on server start: the in-memory history is restored from `AdminHistoryData` and a
  listener writes every record (and every "undone" mark) through to it. The last 1000 records survive restarts.
- **Inspect:** History tab, or `/wandb admin config history`.
- **Revert one:** `AdminSettingService.revert(actor, sequence)` puts the record's old value back. Refused
  (`CONFLICT`, "changed since") when the setting no longer holds the record's new value, so a revert never undoes
  a later change. Recorded as `REVERT` and marks the record undone.
- **Revert a group:** `ProfileService.revertGroup` takes, per setting, the value before the group's first change
  and after its last, refuses if any setting moved since, and applies the reversal through the same all-or-nothing
  applier under a new group `revert:<group>:<millis>`.

## 7. Snapshots

A snapshot is a captured profile of kind `snapshot` (`replace` mode, every non-default server value). **Create
Snapshot** stores it (auto-named with the time if no label); **Restore** is an apply of that snapshot, with the
same preview and a confirmation dialog. Restoring is itself one history group, so a restore can be reverted.

## 8. Export and import

Files live in `<world>/wizards_and_beasts/admin_profiles/`. Export writes `<id>.json` in the format above.
Import reads a file name from that folder only: the name must match `ProfileIds.validFileName`, the resolved path
must stay inside the folder (`..`, separators and absolute paths are refused), and the size is checked before
reading. The text is parsed and validated as in §4; a usable file is kept as a custom profile (unique name and
id) with its warnings, and **is never applied by importing**. Malformed JSON, the wrong schema, an incompatible
version or invalid values refuse the file with every reason. Nothing in this path throws to the server: parse
errors, stack overflows from hostile nesting and I/O errors all become a result.

## 9. Commands

`/wandb admin profile` (requires admin; managing profiles needs the CONFIG capability; applying needs whatever
the profile's settings need):

- `list`
- `preview <id>`: "N setting(s) will change", each `from → to`, errors and warnings, restart/new-chunk flags
- `apply <id> confirm` (bare `apply <id>` only explains itself)
- `save <name>`, `snapshot [label]`
- `delete <id> confirm`
- `export <id>`, `import <file>`
- `revert <sequence>`, `revert_group <group>`

## 10. Control Center

**Profiles** section (❐), four tabs:

- **Profiles.** First entry *Current configuration* → Save as. A profile's page: Apply… / Export / Delete, a name
  field with Duplicate / Rename, then the server's preview: "12 setting(s) will change", RESTART REQUIRED / NEW
  CHUNKS ONLY, every error (red) and warning (gold), and each change as "Spell Damage Multiplier: 1.0 → 1.5"
  (⟳ restart, ◎ new chunks, ↻ reload). Apply is only enabled when the preview has no errors and changes something.
  Apply… opens a dialog with the count and the first changes: **[Cancel] [Apply…]**.
- **Snapshots.** *Current configuration* → Create snapshot; a snapshot's page previews restoring it; Restore…
  asks for confirmation.
- **History.** Newest first (✖ refused, ↶ reverted, ▣ part of a group). A change's page shows before/after, who,
  when, the group; Revert this change / Revert whole group, each confirmed.
- **Import.** *About importing* (the folder and what is checked), then each file with Import and the result.

The client decides nothing: every button is a request; apply, delete and reverts are sent with `confirmed` only
after the dialog, and the server refuses them otherwise. After any action that changed values the server resends
all setting values, and the client marks every per-page cache (spells, brews, wands, brooms, beams, creatures)
stale.

## 11. Tests

| Test | What it proves |
|---|---|
| `ProfileCodecValidatorTest` (JUnit, 11) | round trip; malformed / hostile / oversized input never throws; incompatible versions; unknown fields; ids and file names; valid plan; invalid values, out of range, unauthorised; unknown / client / deprecated / locked warnings; replace mode resets + restart flag; module dependency error and partial warning; every shipped preset parses and validates |
| GameTest `admin_profile_apply_preset_and_revert_group` | Hardcore applies as one PROFILE group with the previewed count; reverting the group restores every value and marks it undone |
| GameTest `admin_profile_partial_failure_rolls_back` | a batch with a change refused by a rule in every pass, one with an invalid value, and one with a stale `from` each leave every value unchanged; rolled-back records are not revertible |
| GameTest `admin_profile_import_validation` | malformed, future-version, invalid and out-of-range text refused; unknown setting kept as a warning; importing never applies; a malformed file refused; `../`, `..\` and sub-paths refused |
| GameTest `admin_profile_snapshot_restore` | a snapshot restores a changed value; a built-in preset cannot be deleted |
| GameTest `admin_profile_history_persists_and_reverts` | a change is in the world's history with actor, old and new value; a stale revert is refused; revert one applies and is persisted as undone |
| GameTest `admin_profile_flags_and_authority` | a worldgen module change is NEW_CHUNKS, not RESTART; every profile operation refuses an actor without authority; a profile with settings the actor may not change is not applicable |

## 12. Limits and notes

- Client-only settings are not part of a profile: they are each player's display preferences.
- Player data (heritages, money, licences, spells known) is never part of a profile; profiles configure the server.
- The history keeps the last 1000 records; a group reverted after its oldest records fell off reverts what is left.
- A profile that names a RESTART setting applies the stored value at once and says so; the value takes effect
  after the restart, as with a single edit.
