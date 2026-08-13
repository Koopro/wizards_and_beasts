# AGENT PROMPT — Heritage Forms & Manifestation

**Status:** Phase 0 complete 2026-08-13. Brief is **partly stale** — see the
[Phase 0 Reconciliation Addendum](#phase-0-reconciliation-addendum-2026-08-13) at the bottom, which
supersedes several sections. Read the addendum before acting on anything above it.

**Path note:** the brief specified `docs/AGENT_PROMPT_HERITAGE_FORMS.md`. `/docs/` is gitignored
(`.gitignore:153`); the tracked doc home is `documentation/`, which is where `AUDIT_PUNCHLIST.md` and
`MIGRATION_DELTAS.md` already live. Filed here instead.

**Supersedes:** `AGENT_PROMPT_HERITAGE_MANIFESTATION.md` (Prompt A). That draft is void.

**Scope:** Third-person visual expression of heritage on the player, via three distinct mechanisms
behind one datapack registry.

---

## 0. Hard stack rules (non-negotiable)

- Minecraft **1.21.11**, NeoForge **21.11.x**, Java **21**, official Mojang mappings, GeckoLib
  **5.4.5**. Not 1.21.1 / 21.1.x. If an API you need exists only on 21.1.x, stop and report.
- `Identifier` everywhere — **never** `ResourceLocation`.
- `Identifier.fromNamespaceAndPath()` only — never `tryParse()`.
- `Identifier`-keyed `AttributeModifier` — never UUID-keyed.
- `NeoForge.EVENT_BUS` only.
- `DeferredRegister` for all registration. Registration is never conditional.
- `AttachmentType` + `Codec` for persistent state.
- `CustomPacketPayload` + `StreamCodec` + `RegisterPayloadHandlersEvent` for networking.
- JSpecify nullability on every public signature.
- **GeckoLib render safety:** `GeoRenderState` + `DataTicket` + `buildRenderTask`. **No entity access
  at render time, ever.**
- **GeckoLib coordinate convention:** `y_model = 24 - y_geo`, reflection `diag(1,-1,1)` — negates X
  and Z rotations, leaves Y unchanged. Distinct from vanilla's `scale(-1,-1,1)`. Do not mix them.
- **Mixin discipline:** prefer a NeoForge event. If you conclude a mixin is required, stop and report
  with justification naming the event you evaluated and why it was insufficient. If approved: no
  logic in the mixin body (pure delegation), `defaultRequire: 1`.
- **Repo wins.** Match existing patterns even where you'd have chosen differently. Deviation requires
  evidence (§9).

---

## 1. The three mechanisms

Each heritage uses exactly one (plus optional overlay). They are not intensities of a single system.

### Mechanism A — Proportion pass (player skin preserved)

**Applies to:** part-giant, part-goblin, vampire. *(Addendum §A4 revises this list.)*

Canon treats these as differently-proportioned humans, not different creatures — Hagrid and Madame
Maxime are large people, Flitwick is a small one. The player keeps their own skin and their own model.
Scale and per-bone offsets only.

- Implemented with the machinery `PlayerPoseLayer` already owns. Do not build a second one.
- **Visual scale only.** Do **not** touch hitbox, collision, reach, eye height, or any attribute.
  *(Addendum §A3 revises this — the constraint binds new code only.)*
- Vampire additionally takes a pallor tint via Mechanism C. Pallor only — **no fangs, no red eyes, no
  cape, no bat.** Canon on vampire appearance is Sanguini in *Half-Blood Prince*, roughly two lines,
  gaunt and pale. Record the under-sourced flag in the datapack entry and in `MIGRATION_DELTAS.md`.

### Mechanism B — Form replacement (new rig, renderer swap)

**Applies to:** werewolf (transformed), Veela (transformed). *(Addendum §A4 revises this list.)*

Full replacement of the player model while the trigger holds. The player's skin does not render —
correct here, because they are not a person at that moment.

- **Werewolf:** canon is emphatic that a transformed werewolf is not a true wolf — shorter snout,
  tufted tail, distinct silhouette (*Prisoner of Azkaban*, Lupin's transformation; Lupin's own DADA
  lesson on telling them apart). Build to that distinction, not to a wolf.
- **Veela:** *Goblet of Fire* ch. 8, the Quidditch World Cup — faces elongating into beaked bird
  heads, scaly wings bursting from the shoulders. That is the reference.
- **Armour is suppressed while a Mechanism B form is active.** Canon-defensible (transformation
  destroys clothing) and it avoids mapping vanilla armour onto non-human proportions. **Visual
  suppression only — armour stats are untouched.**
- **Held items are not rendered** in Mechanism B forms. Whether a transformed player *can* cast is a
  gameplay question, explicitly out of scope (§6), logged to the punchlist.

### Mechanism C — Overlay FX (composites over whatever is beneath)

**Applies to:** Obscurial (smoke), vampire (pallor), human lineages (exertion flare).

Renders on top of Mechanism A or B output. Ordering is a correctness requirement.

- **Obscurial:** an Obscurus geometry reportedly already ships. **Phase 0 must find it.** Reuse it.
  Do not author a second Obscurus. *(Addendum §A2 — found, but not player-attachable.)*
- **Human lineages — exertion flare.** Brief aura at the wand hand on a high-proficiency or high-cost
  cast. No bloodline mark, no sigil, no permanent tell — canon is explicit that wizards are visually
  indistinguishable from Muggles, and the Statute of Secrecy depends on it. This is
  **fan-extrapolation** and must be labelled as such in the datapack entry and in
  `MIGRATION_DELTAS.md`. It reads as "that wizard is powerful," never "that wizard is of house X."

### Cross-cutting

- **Third person only.** First-person is out of scope — it collides with the pending Animagus
  front-limb work.
- **Metamorphmagus is not in this prompt.** Voluntary and cosmetic-control shaped, not trigger-fired.
- **House-elf and centaur are not playable.** *(Addendum §A1 — VOID. They are playable, gated
  "coming soon" by `Heritage.alphaAvailable`.)*
- **Bat form belongs to the Animagus roster**, not to vampire. Out of scope here; logged only.

---

## 2. Phase 0 — mandatory read-only audit

**COMPLETE.** Findings in the addendum below.

---

## 3. Data model

New datapack registry `HeritageAppearance`, mirroring `BestiaryEntry`.

- Keyed by heritage `Identifier`, and by variant `Identifier` where variants diverge.
- Codec-driven, reload-listener backed.
- The mechanism is a **tagged union**, not a numeric tier: `proportion` / `form` / `overlay`. A
  heritage may carry a `proportion` or `form` entry plus an `overlay` entry.
- `proportion`: scale factor, per-bone offset map.
- `form`: model `Identifier`, texture `Identifier`, animation `Identifier`, trigger reference.
- `overlay`: texture or particle reference, tint, emissive flag, trigger reference (nullable).
- Every entry carries a **provenance field**: canon citation (book + chapter) or an explicit
  fan-extrapolation flag.
- A missing entry is valid and renders nothing. It must not throw and must not spam — at most one
  logged miss per identifier per session.

**No lore prose.** All display strings are placeholder lang keys. Provenance values are citations.

---

## 4. Placeholder rig contract

- Placeholder rigs are acceptable and expected. They are box-fidelity and will be replaced with
  hand-built art.
- Every placeholder geo carries a **machine-checkable marker** — a structured field, not a free-text
  `_comment`.
- Ship a **unit test that fails** if a marker is present on a rig whose cube-per-bone ratio indicates
  real art has been swapped in. The marker cannot silently outlive the placeholder.
- Ship an **asset-swap contract** per rig: required bone names, pivot conventions, animation names
  the renderer will request. Mirror the broom asset-swap contract already in the repo.
- Do not clear the existing free-text markers here. Out of scope, already on the punchlist.

---

## 5. Deliverables by phase

Superseded by the addendum's revised phase plan.

---

## 6. Out of scope — hard rejection

- **Sighting records, knowledge tracking, Ministry registry, concealment counterplay.**
- A "manifestation occurred" event with no consumer.
- Any new transformation trigger, timer, or state machine — including Veela rage.
- Hitbox, collision, reach, eye-height, or attribute changes.
- Armour **stat** changes; ability suppression while transformed; whether a transformed player can
  cast.
- Metamorphmagus. Vampire bat form.
- First-person rendering.
- Robe, cloak, or wearable cosmetics.
- Changes to heritage stats, `canUseWand`, the selection ceremony, `HeritageDossierRenderer`, or
  `HeritageSelectionScreen`.
- Clearing the existing placeholder markers.
- Authoring canon prose, heritage blurbs, or lang values beyond placeholder keys.
- Fixing anything discovered out of scope. Log it.

---

## 7. Stop-and-report triggers

- Any belief a mixin is required.
- Any conflict between this prompt and an existing repo pattern.
- Any finding that would require expanding §6.

---

## 8. Commits & logging

- **Explicit pathspec commits only.** Never bare `git commit`, never `git add -A` / `git add .`.
- One commit per logical unit. Conventional prefixes.
- The working tree carries uncommitted feature-sized changes. **Stage nothing you did not author
  here.**
- `documentation/AUDIT_PUNCHLIST.md` — every out-of-scope discovery, tagged BLOCKER / POLISH /
  NICE-TO-HAVE.
- `documentation/MIGRATION_DELTAS.md` — vampire under-sourced flag, human-flare fan-extrapolation
  label, every unwired form.

---

## 9. Upgrade License

Override implementation methodology where there is **evidence**. May **not** be used to expand scope,
change §1, alter the mechanism assignment, introduce a behavioural delta, or resolve a lore question.

---

## 10. In-game verification (mandatory, non-substitutable)

A clean build and green tests do not satisfy this. Launch and verify:

- [ ] Mechanism A renders in third person with the player's **own custom skin intact**.
- [ ] Mechanism B fully replaces the model when its trigger fires, and reverts cleanly.
- [ ] Armour and held items are hidden in Mechanism B, and return on revert.
- [ ] Mechanism C composites **over** both A and B.
- [ ] Vampire shows pallor plus proportion together.
- [ ] Nothing renders in first person.
- [ ] A **second player** sees all of the above correctly.
- [ ] A heritage with no entry renders cleanly — no exception, no log spam.
- [ ] `/reload` picks up an edited appearance JSON without restart.
- [ ] Module gate: `DISABLED` renders nothing; `PREVIEW` renders.

---
---

# Phase 0 Reconciliation Addendum (2026-08-13)

**The brief above is written as greenfield. It is not.** A full heritage form-replacement and
proportion system already ships and is live. This is a reconciliation job, not a build.

## A0. What already exists

| Brief calls it | Ships today as | Live? |
|---|---|---|
| Mechanism A (proportion) | `SizeProfile.modelScale` + `modelAspectX/Z` → `livingState.scale` in `FormRenderStateModifier`, aspect via `PoseStack` in `FormRenderHandler` | ✔ |
| Mechanism B (form replacement) | `LivingEntityRendererMixin` cancels `LivingEntityRenderer.submit` at HEAD for every non-`HUMANOID` form; `FormModelRenderer` dispatches on `ModelType` | ✔ |
| Mechanism C (overlay) | Obscurus smoke particles in `FormRenderHandler.spawnObscurialSmokeTrail`; `RenderFlag` enum (`GLOWING_EYES`, `PARTICLE_AURA`, `SHADOW_OVERLAY`, `TRANSLUCENT`, `WING_LAYER`, `TAIL_LAYER`) | partial |
| §3 registry | `FormRegistry` (22 forms) + `SizeProfileRegistry` (22 profiles) + `HeritageFormBridge` (heritage × variant × state → formId) — all **hardcoded static initialisers**, no codec, no reload | ✔ |

Key classes: `form/` (13), `client/form/` (11), `mixin/client/LivingEntityRendererMixin`.

## A1. Roster — brief was wrong

**10 heritages, 31 variants.** House-elf, centaur and merpeople **are** playable. They are gated
"coming soon" by `Heritage.alphaAvailable`, enforced in `HeritageSelectionScreen` (confirm button
inactive). Only `wizardkind`, `werewolf`, `obscurial` are alpha-available today.

**Ruling (Christian, 2026-08-13):** they stay. §1's "not playable" line is VOID.

**Consequence:** the appearance mapping covers all 10 heritages, not 7. "Part-goblin" does not exist —
`goblin` is a whole heritage with variants `common` / `warrior` / `rune`, no half-blood variant.
"Part-giant" = `GIANT_HALF` variant (`half_giant`).

## A2. Obscurus geometry — found, not reusable as-is

`assets/wizards_and_beasts/geckolib/models/entity/obscurus.geo.json` — GeckoLib, **27 bones / 65
cubes**, 128×128, generated by `tools/obscurus_model.py`. No placeholder marker. Consumed by the
datapack creature `data/wizards_and_beasts/creatures/obscurus.json` → `GenericBeastEntity`. **A mob.**

The player's dark form already uses `client/model/ObscurialDarkModel` — the same rig deliberately
rebuilt in vanilla `ModelPart`s, sharing the texture palette through the same generator. There is no
GeckoLib-on-player render path in this repo, and building one is not warranted. **The rebuild is the
reuse.** Brief §1's "do not author a second Obscurus" is already satisfied.

## A3. Behavioural deltas — RULING

`SizeSystemAPI.applyProfile` already applies, per form, `Attributes.SCALE`, `STEP_HEIGHT`,
`BLOCK_INTERACTION_RANGE`, `ENTITY_INTERACTION_RANGE`, `KNOCKBACK_RESISTANCE`, then
`refreshDimensions()`.

**Ruling:** shipped behaviour is left **untouched**. §1's "do not touch hitbox / attributes" binds
**new code only** — it is a prohibition on *introducing* deltas, not an order to remove existing ones.
Removing them would itself be a behavioural delta and is out of scope by §6.

**The mismatch the brief assumed is smaller than stated.** `SizeProfile.hitboxHeight` drives
`Attributes.SCALE` via `scaleAttributeValue()`, and every shipped profile keeps `hitboxHeight` and
`modelScale` consistent (`giant_half`: 2.88 = 1.8 × 1.6, `modelScale` 1.6). Vertical scale and hitbox
already agree.

**The real mismatch is horizontal.** `modelAspectX/Z` is visual-only and has no hitbox counterpart —
`hitboxWidth` is an independent field. Goblin: `hitboxWidth` 0.39 (= 0.6 × 0.65) but visual width
0.65 × 1.231 ≈ 0.80. The model is ~2× wider than its collision box. Logged BLOCKER.

## A4. Mechanism assignment — RULING

**Ruling:** ratify what ships, and declare it explicitly with provenance. The datapack entry
*documents and can override* the hardcoded default rather than fighting it.

| Heritage | Mechanism | Ships as | Note |
|---|---|---|---|
| `wizardkind` | C only (flare) | `human_default`, HUMANOID | flare **deferred**, see A5 |
| `werewolf` | A (human) → **B** (transformed) | `werewolf_human` HUMANOID 1.05× → `werewolf_wolf` CUSTOM_BIPED | trigger absent, see A6 |
| `obscurial` | A (human) → **B + C** | `obscurial_human` → `obscurial_dark` SHADOW + smoke | only fully-wired one |
| `goblin` | **B** | `goblin_default` SMALL_HUMANOID | **deviates from §1**, which said A. §1's premise was "part-goblin"; no such variant exists. A goblin player is a full goblin — a separate species — so B is correct |
| `house_elf` | **B** | `house_elf_default` SMALL_HUMANOID | separate species; B correct |
| `veela` | A (human) → **B** (transformed) | `veela_human` → `veela_harpy` CUSTOM_BIPED | trigger absent |
| `giant` | **A** | `giant_default` / `half_giant_default`, both HUMANOID | matches §1 exactly; already correct |
| `centaur` | **B** | `centaur_default` QUADRUPED | separate species; B correct |
| `vampire` | **A + C** | `vampire_human` HUMANOID + `GLOWING_EYES` | §1 wants **pallor**, not glowing eyes → C arm replaces the flag. `vampire_bat` stays registered but **unwired and flagged** → Animagus roster, `MIGRATION_DELTAS.md` |
| `merpeople` | A (land) → **B** (water) | `merfolk_land` → `merfolk_water` SWIMMING | trigger absent |

## A5. Exertion flare — DEFERRED, schema arm only

`CastContext` is server-side (`ServerPlayer`, `Proficiency`, `ModifierStack`). Client-side:

- **Self** proficiency: `ClientSpellDataState.get().getSpellProficiency(spellId)` → float 0..1.
- **Other players:** `SpellCastAnimationS2CPayload` → `ClientCastAnimationState`, keyed by entity id,
  carries `spellId, ticks, windupEnd, releaseEnd, holdPhase`. **No proficiency tier, no cost/power
  magnitude.**

Brief §D11: unreachable without new plumbing ⇒ **deferred, not invented.** The `overlay` union arm is
built and tested; no flare is wired. Adding the two fields to the payload is a one-line schema change
for a follow-up prompt. Logged.

## A6. Triggers — one of five exists

| Trigger | Status |
|---|---|
| Obscurial emotional break | **EXISTS** — `ObscurialHeritageHandler`: `EMOTIONAL_TRIGGER_HP_THRESHOLD 0.28f`, `EMOTIONAL_TRIGGER_MIN_STRESS 80.0f`, `AUTO_TRANSFORM_HP_THRESHOLD 0.40f`, forced-dark duration, daylight strain, damage stress spikes |
| Werewolf moon phase | **ABSENT** — zero hits for `getMoonPhase` / `moonPhase` / `isFullMoon` in the whole tree |
| Veela rage | **ABSENT** — `TransformationConfigRegistry` has `veela_human ↔ veela_harpy` entries, nothing drives them |
| Vampire bat | **ABSENT** (and out of scope) |
| Merfolk water | **ABSENT** |

Manual entry only: `/wandb` form commands (`WizFormCommands`, `WizSizeCommands`).

Per §6, **no new triggers.** Unwired forms are declared, left unwired, and logged — the broom
`brake`/`summon` precedent.

## A7. Sync scope — a correctness landmine

| State | Payload | Client store | Visible for other players? |
|---|---|---|---|
| `activeFormId`, `SizeProfile`, `RenderFlag`s | `FormSyncS2CPayload.syncToTracking` | `ClientFormDataState` — `Map<UUID, FormData>` | **YES** |
| heritage, variant, `transformationState` | `HeritageDataSyncS2CPayload.syncToPlayer` | `ClientHeritageDataState` — a **single** `PlayerHeritageData` instance | **NO — local player only** |

**Any new render code must key off `activeFormId`, never `transformationState`.** Keying off the
latter would work in single-player and silently fail for every remote player — exactly the case §10
demands be verified.

## A8. Module gate — does not exist

`grep Module.|ModuleManager` over `heritage/`, `form/`, `client/form/`, `client/heritage/`,
`event/heritage/` → **zero hits**. No `HERITAGE` constant in `Module` (34 constants). `PLAYER_ANIMATION`
gates the pose layer only.

Brief §10's module-gate checkbox is unverifiable without adding a constant. Adding one is new scope →
**gate the new appearance layer on `Module.PLAYER_ANIMATION`**, which is the module that already owns
"procedural visual expression of player state", and log the missing `HERITAGE` constant.

## A9. Armour / held-item suppression — already free

The mixin injects at HEAD of `LivingEntityRenderer.submit` and cancels — **before vanilla runs any
render layer.** Armour, held items and every other layer are already suppressed for non-`HUMANOID`
forms; armour *stats* are untouched. No new suppression point needed. Needs in-game confirmation only.

## A10. Placeholder markers

**170** free-text `"_comment": "PLACEHOLDER box rig"` markers, not the 93 the brief assumed. Out of
scope to clear (§6); the §4 machine-checkable marker + failing test applies to **new** rigs only.

## A11. Mixin discipline

Moot for the swap: the repo already carries three client mixins (`AvatarRendererMixin`,
`LivingEntityRendererMixin`, `PlayerModelMixin`) with `defaultRequire: 1` and pure-delegation bodies.
The form swap is mixin-based and stays that way. **No new mixin is required by this work.**

## A12. Architecture ruling — override layer, not replacement

**Ruling (Christian delegated, 2026-08-13):** `HeritageAppearance` is a datapack **override layer**
over the shipped hardcoded registries, not a migration of them.

Resolution order, per heritage/variant:

```
HeritageAppearance datapack entry   (codec-driven, reload-listener backed, may be absent)
        ↓ falls through to
HeritageFormBridge + FormRegistry + SizeProfileRegistry   (hardcoded, always present)
        ↓ falls through to
nothing rendered
```

Rationale: satisfies §3's "codec-driven, not hardcoded" for all new appearance data; keeps a working,
in-game-verified system intact; makes every §10 checkbox testable by editing one JSON; and makes the
mechanism assignment (A4) reversible without a recompile. Nothing is deleted.

## A13. Revised phase plan

- **Phase 1 — data layer.** `HeritageAppearance` tagged-union record + `Codec`
  (`proportion` / `form` / `overlay` arms) + provenance field; `SimpleJsonResourceReloadListener`
  mirroring `BestiaryEntryLoader`; volatile-swap registry mirroring `BestiaryEntryRegistry`; sync
  payload. Tests: codec round-trip per arm, missing entry returns empty and does not throw,
  one-log-per-identifier miss.
- **Phase 2 — resolution + Mechanism A.** Resolver implementing the A12 order. Wire the `proportion`
  arm through the existing `FormRenderStateModifier` / `PlayerPoseLayer` path. Player skin preserved.
  No attribute writes from new code.
- **Phase 3 — Mechanism C.** Overlay compositing over A output. Vampire pallor (replaces the
  `GLOWING_EYES` flag on `vampire_human`). Obscurus smoke declared through the registry rather than
  hardcoded in `FormRenderHandler`. Flare = schema arm only, unwired, logged.
- **Phase 4 — Mechanism B declaration.** Declare the existing form swaps through the registry.
  Confirm C composites over B. Confirm armour/held-item suppression. Unwired forms declared and
  logged. **No new rigs needed** — werewolf, veela, goblin, house-elf, centaur, merfolk, bat and
  shadow models all already exist; §4's placeholder contract applies only if a new rig is authored.
- **Phase 5 — in-game verification** per §10, including the two-player case.

## A14. Punchlist / delta entries owed

`documentation/AUDIT_PUNCHLIST.md`:
- **BLOCKER** — `modelAspectX/Z` visual width has no `hitboxWidth` counterpart; goblin model ~2×
  wider than its collision box (A3).
- **BLOCKER** — `transformationState` is not synced to remote clients; only `activeFormId` is (A7).
- **POLISH** — no `Module.HERITAGE` constant; heritage/form content is ungated (A8).
- **POLISH** — werewolf moon-phase, Veela rage, merfolk water triggers absent (A6).
- **NICE-TO-HAVE** — transformed-caster ability question (§6, unanswered).
- **NICE-TO-HAVE** — `vampire_bat` → Animagus roster (§1).
- **NICE-TO-HAVE** — 170 free-text placeholder markers (A10).

`documentation/MIGRATION_DELTAS.md`:
- Vampire appearance **under-sourced** — Sanguini, *Half-Blood Prince*, ~2 lines.
- Human exertion flare **fan-extrapolation**, and **deferred** for lack of wire data (A5).
- Every unwired form: `veela_harpy`, `merfolk_water`, `vampire_bat`.
- Goblin mechanism **deviates from brief §1** (B, not A) — rationale in A4.
