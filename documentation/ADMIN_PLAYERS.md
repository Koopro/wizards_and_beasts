# Player administration

Phase 11 of the Control Center (2026-10-01): **Players** (☺), and `/wandb admin players …`. This is about acting
on one player's character. It is not server configuration (that is Profiles and the settings sections), and it
keeps no player state of its own: every value shown is read from the system that owns it when it is asked for,
and every change goes through that system's own API.

**The rule:** each player mutation is checked on the server for authority, target and operation, a destructive one
needs confirmation, it runs server-side through the owning system, a failure part-way through is rolled back, and
the attempt is logged.

---

## 1. Audit: where player state lives

| State | Owner (read and written through) | Before this phase |
|---|---|---|
| Heritage, lineage, condition, onboarding | `HERITAGE_DATA` via `HeritageAPI`; changes via `HeritageAssignment` | `HeritageAdminService` (inspect / assign / reset; PLAYERS; confirmed), Heritages → Players tab, `/wandb admin heritage` |
| Stats, Power band, training progress | `PLAYER_STATS` (`PlayerStatsData`, `PlayerStatsAPI`) | `/wandb player stats …` |
| Skill points and nodes | `SKILL_DATA` (`PlayerSkillData`); awards through `SkillSystemAPI.awardPoints` (capped per audience) | `/wandb player skill points add/set, reset, respec` |
| Vanilla XP | vanilla | no longer pays skill points (`PROGRESSION_MAP.md`): the web's "XP" is skill points |
| Spells known, proficiency, practice, loadout | `SPELL_DATA` (`PlayerSpellData`); eligibility in `SpellLearningService` / `SpellLearningEligibility` | `/wandb magic spell learn/forget/reset/learn_all`, `/wandb magic proficiency …` |
| Wand ownership | on the stack (`WandAllegiance`); `WandAllegianceService.stateFor` | debug modules |
| Effects | the player's own `MobEffectInstance`s | vanilla `/effect` |
| Ministry: notoriety, wanted level, offences, sentence, fugitive, fine, age, wand confiscation | `MINISTRY_RECORD` (`MinistryRecords.mutate` syncs), `MinistryFines`, `MinistryTrace`; case dossiers in `MinistryCaseData` | `/wandb ministry record/wanted/pardon/fine waive/notoriety …` |
| Money | `VAULT_DATA` (`PlayerVaultData`) | `/wandb player vault [deposit|withdraw|clear]` (ADMIN) |
| Debug | `DebugModeService`, spell data counters | `/wandb debug player` |

Nothing above was duplicated. The two operator-command quirks found are documented, not changed: the operator
`skill reset` does not take back web-taught spells (the player `respec` does; the panel uses the respec path), and
`spell learn` steps past the learning rules (the panel offers both, separately).

## 2. Pieces

| Piece | Where | What it does |
|---|---|---|
| Capability | `AdminCapability.MONEY` | creating or destroying money; split from `PLAYERS` so a role system can withhold it. Positions are shown only with `WORLD` |
| Section | `AdminCategory.PLAYERS` (cap PLAYERS) | sidebar entry under Dashboard |
| Actions | `admin/player/PlayerAdminAction` | the closed list of what may be done; each names its capability and whether it is destructive |
| Service | `admin/player/PlayerAdminService` | `search`, `facet` (Overview, Heritage, Skills, Spells, Effects, Ministry, Economy, Debug), `perform` |
| Log | `admin/player/PlayerActionLog` | world SavedData `wizards_and_beasts_admin_player_log`, last 2000 entries |
| Network | `network/admin/AdminPlayerPayloads` | search / facet / action / log requests and replies |
| Client | `client/admin/ClientAdminPlayerState`, `screen/PlayersPanel`, `screen/PlayerFacetPanel` | nine tabs over one shared player selection |
| Commands | `admin/command/AdminPlayerCommands` | `/wandb admin players list [query] | inspect <player> <facet> | log [player]` (read-only) |

## 3. Actions

| Action | Capability | Confirm | Runs through |
|---|---|---|---|
| Assign heritage (`heritage/lineage`) | PLAYERS | yes | `HeritageAdminService.assign` → `HeritageAssignment.assign` |
| Reset onboarding | PLAYERS | yes | `HeritageAdminService.resetOnboarding` |
| Unlock spell | PLAYERS | no | `SpellLearningService.validateLearnAttempt`; refused with the rule's reason when not eligible |
| Grant spell (past the rules) | PLAYERS | yes | `PlayerSpellData.learnSpell` (as `/wandb magic spell learn`) |
| Revoke spell | PLAYERS | yes | `PlayerSpellData.forgetSpell` (also empties loadout slots; practice kept) |
| Reset spell progress | PLAYERS | yes | `PlayerSpellData.resetProgression` (new: practice, proficiency, casts, hits; spells stay known) |
| Add skill points (1–60) | PLAYERS | no | `SkillSystemAPI.awardPoints` (the audience cap applies; "at the cap" is reported) |
| Reset skills | PLAYERS | yes | the respec path without its fee: `resetAll` + `revokeWebTaughtSpells` + reconcile |
| Remove one effect | PLAYERS | no | `removeEffect` |
| Clear effects | PLAYERS | yes | `removeAllEffects` |
| Preview effect | PLAYERS | no | applies the effect for 5 s **to the administrator**, never to the target; instant effects refused |
| Pardon | PLAYERS | yes | `PlayerMinistryRecord.pardoned` (notoriety, wanted, sentence, fugitive, fine; offences stay) |
| Waive fine | PLAYERS | yes | `MinistryFines.waive` |
| Deposit / withdraw money (`galleons|sickles|knuts`, 1–10 000) | **MONEY** | yes | `PlayerVaultData` deposit / exact withdraw (never partial) |

There is deliberately no "give effect to player", no "set balance", no "set stat" and no bulk action: an action names
exactly one online player.

## 4. The pipeline (`PlayerAdminService.perform`)

1. **Permission.** `actor.canModify(action.capability())`. Refused → `unauthorized`, server log WARN only.
2. **Target.** The UUID must be an online player (for a preview: the administrator). Refused → `no_player`.
3. **Operation.** The argument is checked for the action: known spell, known effect, coin name, amount bounds.
   Refused → `invalid_argument`.
4. **Confirmation.** A destructive action without `confirmed` → `confirm_required`.
5. **Snapshot** of what an action here can write: spell data, skill data, vault (as NBT), stats, Ministry record,
   active effects.
6. **Execute** through the owning API, then sync to the player's client.
7. **Rollback.** Any exception restores the snapshot and resyncs → `failed`.
8. **Log** the attempt in `PlayerActionLog` (player, action, argument, admin, time, `ok` / `refused:<code>` /
   `failed:<code>`, what changed) and answer with the outcome and the log sequence.

Heritage assignment is the heritage system's own routine and is not inside the snapshot: it completes or reports
why not. Unauthorised attempts are not written to the world's log so that nobody without authority can push real
entries out of it; they are in the server log with the sender's name and UUID.

## 5. Security

- Read requests (search, facet, log) from a sender without PLAYERS are dropped with a WARN and **no reply**.
- An action from a sender without the capability is refused and answered, so a demoted administrator sees why.
- Packets carry a UUID, an action enum (an unknown ordinal fails decoding), an argument cut to 128 characters and a
  number; the server decides everything else. No packet carries player state.
- Positions appear only for viewers with WORLD; money changes need MONEY. Today `AdminPolicy` grants every
  capability to every administrator; a role system replaces `capabilitiesOf` and these splits take effect.

## 6. UI

Players section, tabs **Overview | Heritage | Skills | Spells | Effects | Ministry | Economy | Debug | Log**. The left
list is the online players (search by name or UUID; ✦ has effects, ⚠ is of interest to the Ministry). Each tab shows
its facts, then its actions, then its list (a list can be every spell known). Lists with an action carry a
button per line (Revoke, Remove). Choosers (spell to unlock, effect to preview, lineage to assign) have a filter field
that narrows the selector in place. Destructive buttons end in "…" and open a dialog naming the action, its subject
and the player. The last outcome on the selected player is shown at the top of every tab.

## 7. Tests

| Test | Proves |
|---|---|
| `PlayerAdminContractTest` (JUnit, 6) | money actions need MONEY and nothing else does; destructive flags; action/facet/search round-trips; an invented action ordinal is refused; overlong arguments are cut; every action, confirmation, facet and outcome has lang |
| GameTest `admin_player_unauthorised_is_refused` | a non-admin's search/facet/log get no reply; its money and reset actions are refused and change nothing and are not logged; a config admin reads nothing; a players admin without MONEY cannot create money |
| GameTest `admin_player_invalid_targets_and_arguments` | an offline UUID, an unknown spell, a malformed effect id, an amount above the cap, an unknown coin, zero points are refused and logged; a console preview lands on nobody |
| GameTest `admin_player_destructive_needs_confirmation` | five destructive actions are held unconfirmed with nothing changed; then grant, revoke, clear, withdraw (and a refused over-withdraw), pardon and skill points apply |
| GameTest `admin_player_actions_are_logged` | held and applied attempts are logged newest first with admin, player, action, argument, time, result and what changed |
| GameTest `admin_player_failure_rolls_back` | with an injected failure after the mutation, a deposit, a grant and a clear leave vault, spells and effects as they were and are logged `failed` |
| GameTest `admin_player_facets_read_live_state` | every facet reads; the vault and effects facets reflect live state; instant effects are not offered; positions only with WORLD |
