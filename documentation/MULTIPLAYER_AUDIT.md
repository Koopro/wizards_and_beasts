# Wizards & Beasts — Multiplayer / Server-Authority Audit

`dev`, 2026-09-29. Scope: every path by which a client can make the server do something. That covers the 42
client→server payloads (listed by their `playToServer` registrations) plus vanilla item use and menus, with the
wand release and session system re-audited and the systems added since then inspected.

**The rule applied:** the client may ask, the server decides. A finding is a real authority bug only when a crafted,
duplicated, late or malformed packet (or an ordinary lifecycle event such as death or relog) gets the player
something the server never granted.

## 1. How it was tested

| Condition | How |
|---|---|
| Dedicated server | `runGameTestServer` is a headless dedicated server. All 169 required scenarios pass on it. |
| Two or more players | Mock `ServerPlayer`s with real `Connection`s (`WizardTestSupport.placeMockPlayer`): Portkey group travel, deed cooldowns, allegiance duels, the race tests |
| Rapid input / duplicate interaction | Existing `WandCastRaceTests` and `WandCastLifecycleTests` (one release token, a duplicate release is refused); new bag and deed tests (the same action twice) |
| Death / respawn | Existing `AfterDeathInputTests` and the cast-session tests; new `authority_apparition_charge_dies_with_the_wizard` |
| Disconnect / reconnect | New `authority_deed_cooldown_survives_a_relog`: a logout event, removal, then the same profile placed again with its saved data loaded |
| Dimension change | Existing cast-session abort tests; the new Apparition lifecycle hook (reviewed, not scenario-tested) |
| Malformed payload | Review of every stream codec and handler: non-finite floats, out-of-range ordinals, unbounded strings, client-supplied positions |
| Delayed packet | Review. The cast release is only honoured against the server's own record that the hold ended (`vanillaReleaseObserved`). A late Apparition release from a dead sender is now dropped. |
| Chunk unload / entity removal | Review. Handlers resolve entities by id at handling time (broom, Imperio victim, Leviosa session, the Gringotts teller) and do nothing when the entity is gone. |

**Not exercised:** an integrated single-player server with a real client, and two real game clients. The mock
connections carry real packets through real handlers, but rendering and client prediction are not in the loop.

## 2. Verified systems (no change needed)

| System | Why it holds |
|---|---|
| **Wand cast session / release** (`CastReleaseGate`, `WandCastSessions`) | Re-audited, unchanged. There is one session per hold and one release token. A release is accepted only after the server itself observed vanilla's `releaseUsing`. A dead caster is refused. Changing spell mid-hold or dropping the wand ends the hold through the per-tick reconciliation. Death, respawn and dimension change abort the session. A clash hold casts nothing. Covered by `WandCastLifecycleTests`, `WandCastRaceTests` and `AfterDeathInputTests`, all passing. |
| Spell select / assign | Slot range and wand guards (`SpellNetworkGuards`). The spell must be implemented and known. Obscurial ability slots are refused. |
| Leviosa throw | Requires a live Leviosa beam session with something lifted, so a stray throw is harmless. |
| Imperio command / resist | The command needs `CASTER_TO_VICTIM` plus the victim's controller UUID and a live caster. Resist is limited to once per second, and only when the player is actually controlled. |
| Ability framework | `validateWheel` (the ability is granted), `validateTarget` (target kind and range), the framework cooldown, and an alive check on both activate and toggle. |
| Apparition travel / side-along | Starting goes through `evaluateStart` (module, training, licence when the Ministry is active, cooldown, ward). An anchor from another dimension is refused. Side-along accept requires a live offer. |
| Floo travel / registration / call | Module check, an in-call lock, a departure lock, a cooldown, no riding, a lit hearth nearby, and an address resolved from the visited or public list. Registration checks reach and ownership and bills the fee all-or-nothing. |
| Heritage select | Refused once locked. The heritage must be alpha-available, the lineage must belong to it, and a chosen condition must be one that lineage can carry. |
| Skill unlock, professions, vocation | `evaluateUnlock` / `evaluateSelect` (points, adjacency, standing gates, module) run before any mutation. |
| Form change / size override | Operator permission is required, and sizes are checked for finite values and clamped. |
| Module update | `AdminAccess.allows`, plus validated module, state and setting ids. |
| Map waypoints | Needs an open map session. Positions are clamped to the world border, labels sanitised and capped at 48 characters, and each player's waypoints are capped. |
| Mirror connect / close | The caller must hold the paired mirror and name the other holder. Sessions are dropped at logout. |
| Ollivander trial select / choose | Container id must match the open menu, slot must be 0–2, the resonance threshold is checked, and the menu closes after a choice (so a duplicate choose meets no menu). |
| Wandmaker's bench flexibility | Container id must match the open menu. |
| OWL profession choice | The exam must be taken, no profession already held, and the eligibility checks passed. |
| Broom impact report | The report must come from the broom the server believes the player is controlling, at most once per interval, with severity clamped (see §4 for the model's limit). |
| Debug inspect | Debug mode must be on for the player, and it is rate-limited. |
| Items added in the last passes | Time-Turner, Portkey, Resurrection Stone shades, magic-resistant hides and the practice budget are all decided on the server from vanilla's server-side item use and tick. None has a client→server payload. The Time-Turner trail is not saved and resets on death and relog, so it cannot reach back through time it was not carried. |

## 3. Vulnerabilities found and fixed

| # | Vulnerability | Class | Fix | Test |
|---|---|---|---|---|
| 1 | **Hermione's beaded bag duplicated items.** Contents were written back only on close, into whatever bag was in the hand, and the open bag's own hotbar slot was a normal slot. Take items out, pick the bag up to the cursor, and the menu closes without writing: the bag still lists the items. Dying with the bag open did the same. | duplicate an item | `HermionesBagMenu` remembers the opened stack and is valid only while that exact stack is in the hand. Its slot is pinned (no pickup, place, throw, hotbar swap or offhand swap). Every content change is written into the stack at once. | `authority_beaded_bag_cannot_duplicate` |
| 2 | **Vault reachable from anywhere.** `VaultActionC2SPayload` was honoured with no teller present, so it skipped the teller's Ministry-access licence check and the counter itself (deposit, withdraw, exchange, Dragot trades). | bypass a requirement | `GringottsCounter` records the teller that admitted the player. An action is honoured only while that teller is alive and within 8 blocks. | `authority_vault_only_at_the_tellers_counter` |
| 3 | **Apparition charge outlived death and dimension change.** It is held in `PlayerScopedState`, which is cleared at logout only and keyed by UUID. After a respawn, a release Apparated the new body to an anchor chosen before death. After a portal, an anchored destination from one dimension was applied in another. | action without valid state | `ApparitionChargeLifecycle` aborts the charge on death, respawn and dimension change. `release` drops a charge whose sender is dead. | `authority_apparition_charge_dies_with_the_wizard` |
| 4 | **Magical-deed cooldowns reset on relog.** They were in-memory, so casting the Patronus, relogging and casting again gave +3 alignment each time instead of once per 5 minutes. | bypass a cooldown; progression | Saved on a `DEED_COOLDOWNS` attachment (`copyOnDeath`) against absolute game time. Expired entries are pruned on write. | `authority_deed_cooldown_survives_a_relog` |
| 5 | **The OWL exam could be sat anywhere, with the OWLS module off.** The exam screen opens client-side from the desk, so the server never saw the desk. | bypass a requirement | Needs the `OWLS` module and an examination desk within 6 blocks. | `authority_owl_exam_needs_a_desk` |
| 6 | **Leviosa distance from a NaN.** A non-finite `distanceDelta` passed through `Mth.clamp` into the hold distance and then into the lifted entity's position. | malformed payload; desync | Non-finite deltas are rejected. | Review (a one-line guard) |
| 7 | **Broom steering.** Non-finite yaw or pitch was stored as the steering target. Any passenger, not only the pilot, could send steering input. | malformed payload; movement | Non-finite values keep the current heading. Only the controlling passenger's input is accepted. | Review |
| 8 | **Pocket configurator from anywhere.** A client-chosen `BlockPos` was looked up unchecked. That loads, or generates, any chunk in the world, and let an owner reconfigure the pocket from anywhere. | server action without valid state; load | Requires a loaded position within 8 blocks before any lookup. | Review |

**Mutation-checked:** fixes 1–5, reverted one at a time and restored afterwards. Each broke exactly its own test,
for the reason it exists.

## 4. Remaining risks (not fixed, with reasons)

| Risk | Why it stays |
|---|---|
| **Broom crash damage is client-reported.** A modified client can stop sending impact reports and never crash. | That is vanilla's vehicle model: the controlling client moves the vehicle, and a server-side collision simulation would be a rewrite of working flight. Movement itself still goes through vanilla's moved-too-quickly checks. Anti-cheat territory. |
| **Per-session anti-spam resets on relog:** the Floo arrival cooldown, the Butterbeer anti-spam window, Firewhisky round escalation, and the debug-panel rate limit. | Each still costs something real (Floo powder, a mug, a drink) or only affects the player. A relog is a visible, slow action. Worth moving to attachments if a server reports abuse. |
| **Heritage selection is accepted with the HERITAGE module off.** | Still one-time and locked. The heritage module ships PREVIEW. |
| **Side-along offer expiry compares against the invitee's `tickCount`,** which restarts at respawn. | An offer can linger past its time only for a respawned invitee, and it still needs the caster's live attempt. Low value. |
| **Riddle's diary writes are not rate-limited.** | The only effect of spamming is to the writer (corruption, possession). |
| **`CloakEffectsHandler`, `MirrorSessionManager` and `ImperioServerLogic` keep static maps** rather than `PlayerScopedState`. | Each clears on logout (verified), so none leaks. Moving them would be a refactor of working code. |
| **Module and item-use gates on client-opened screens** (OWL exam before this fix, the Pensieve list) trust that the screen was opened from the right place. | The OWL case was fixed because the exam writes lasting grades. The Pensieve only shows the player's own memories. |

## 5. Files changed

`currency/vault/GringottsCounter` (new), `entity/goblin/GoblinTellerEntity`, `network/currency/VaultActionC2SPayload`
(handler body now `perform`), `item/trinket/HermionesBagMenu`, `event/apparition/ApparitionChargeLifecycle` (new),
`apparition/charge/ApparitionChargeManager`, `standing/deed/DeedService`, `registry/ModAttachments`
(`DEED_COOLDOWNS`), `network/owl/RequestOWLExamPacket`, `network/spell/SpellLeviosaAdjustC2SPayload`,
`entity/broom/BroomEntity`, `network/broom/BroomInputC2SPayload`, `network/trunk/PocketConfigC2SPayload`,
`gametest/AuthorityTests` (new, 5 scenarios).
