# Spell Corpus Roster — Full Canon Registration

**Status:** rev 1, design doc. Status column populated by the Phase 0 audit below.
**Scope:** every canon spell with a known incantation, registered as a datapack `SpellDefinition`.
Implemented spells keep their current behaviour; everything else ships `COMING_SOON`.

---

## 0. Phase 0 audit — findings from the live registry

Run 2026-08-19 against the working tree. §4 of the draft required the status column come from
the live registry rather than be assumed, so everything below is derived, not carried over.

### 0.1 What is registered today

**33 spells: 27 datapack JSON + 6 bespoke Java.** The draft's "~33" is exact.

| | |
|---|---|
| JSON (`data/wizards_and_beasts/spells/`) | 27 |
| Bespoke Java (`spell/impl/`) | 6 — `avada_kedavra`, `expecto_patronum`, `imperio`, `obscurus_grasp`, `obscurus_surge`, `protego` |
| Of the roster's 154 entries, already present | **28** |
| Registered but **absent from the roster** | **5** |

### 0.2 Five live registrations the roster does not account for

| id | canon tier | note |
|---|---|---|
| `capacious_extremis` | pottermore | Roster §8 excludes "Extension Charm" as a system, but it ships **as a spell** today. |
| `claustra_reverto` | original | Mod-original. No canon incantation; tier `original`. |
| `frigora` | original | Mod-original. No canon incantation; tier `original`. |
| `obscurus_grasp` | n/a (Java) | Obscurial ability registered through `Spells`. Arguably correctly out of the roster. |
| `obscurus_surge` | n/a (Java) | Obscurial ability registered through `Spells`. Arguably correctly out of the roster. |

The roster as drafted would orphan the first three. `claustra_reverto` and `frigora` are the
sharper problem: they are tier `original`, and the draft's tier scheme has no slot for it.

### 0.3 `SpellCanonTier` already exists — do not invent T1..T5

`spell/def/SpellCanonTier.java` ships six constants. The draft's five-tier scheme maps onto it
one-for-one but **omits `ORIGINAL`**, which two live spells use.

| Draft | Live constant | Live count |
|---|---|---|
| `T1_BOOKS` | `BOOKS` | 19 |
| `T2_COMPANION` | `COMPANION` | 0 |
| `T3_POTTERMORE` | `POTTERMORE` | 1 |
| `T4_FILM` | `FILM` | 2 |
| `T5_EXPANDED` | `EXPANDED` | 3 |
| — | **`ORIGINAL`** | 2 |

Every one of the 27 JSON spells already carries `canonTier`; none is unset. The field is live,
not new. Use the existing serialized names (`books`, `film`, …), not `T1_BOOKS`.

### 0.4 Q6 answered — 18 families map onto 4 categories

`SpellCategory` has exactly four constants: `COMBAT`, `UTILITY`, `DEFENSE`, `DARK_ARTS`.
The 18 families in §5 are organisational only and have no registry counterpart. Live spread:

| category | live spells |
|---|---|
| `utility` | 15 |
| `combat` | 9 |
| `defense` | 2 |
| `dark_arts` | 1 |

Authoring ~120 new definitions against four categories will make `utility` a dumping ground —
it is already 15 of 27. Worth a ruling before authoring, not after.

### 0.5 Q5's premise verified, with a correction

**16** skill nodes end in `_unlock`, not ~17. Their effects are only `spell_cooldown_reduction`
(11) and `spell_damage_bonus` (5). **None grants a spell.** The names lie exactly as the draft says.

### 0.6 What D1 and §2 need that does not exist yet

- **No per-spell state field.** `SpellDefinition` has no `COMING_SOON`, `enabled` or equivalent.
  D1 needs a new field plus enforcement at the cast gate, the spellbook and the teacher.
- **No relation field.** `RANK_OF` / `NARROW_OF` / `COUNTER_OF` do not exist.
- **But the RANK_OF *gate* already does.** `SpellRequirementDef` carries `prerequisiteId` and
  `minProficiency` — precisely the mechanism D2 describes. So `RANK_OF` needs a label at most,
  and may be derivable from the requirement it already implies. `NARROW_OF` gates nothing by
  definition and `COUNTER_OF` is metadata, so all three may be presentation rather than schema.

---

## 1. Resolved design decisions

| # | Decision | Ruling |
|---|---|---|
| D1 | How does an unimplemented spell exist? | Per-spell `COMING_SOON`. Visible in the spellbook, greyed, locked, not castable, not purchasable. |
| D2 | How do rank forms work? | Separate `SpellDefinition`s; the stronger declares a requirement on the base at a proficiency threshold. |
| D3 | No-incantation entries | Out of the spell registry. They are systems, not spells. |

## 2. Relation model

| Relation | Meaning | Gate | Example |
|---|---|---|---|
| `RANK_OF` | Same effect, greater magnitude | Parent at proficiency threshold | `lumos_maxima` → `lumos` |
| `NARROW_OF` | Same effect, restricted target class | None; learned independently | `oculus_reparo` → `reparo` |
| `COUNTER_OF` | Ends or reverses a specific spell | Requires the spell it counters | `nox` → `lumos` |
| `BASE` | No relation | — | — |

## 3. Canon tier

See §0.3 — use the live `SpellCanonTier` constants.

## 4. Implementation status

`IMPLEMENTED` = registered today. `COMING_SOON` = in this roster, not yet registered.
No entry is marked `PARTIAL`: the audit found no half-registered spell.

---

## 5. The roster

### 5.1 Light & Signal

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `lumos` | Lumos | Wandlight | T1 | BASE | **IMPLEMENTED** |
| `lumos_maxima` | Lumos Maxima | Brilliant burst of light | T4 | RANK_OF `lumos` | COMING_SOON |
| `nox` | Nox | Extinguishes wandlight | T1 | COUNTER_OF `lumos` | **IMPLEMENTED** |
| `fumos` | Fumos | Smokescreen | T5 | BASE | COMING_SOON |
| `nebulus` | Nebulus | Creates fog | T5 | BASE | COMING_SOON |
| `periculum` | Periculum | Red distress flare | T5 | BASE | COMING_SOON |
| `verdimillious` | Verdimillious | Green sparks; reveals hidden magic | T5 | BASE | COMING_SOON |
| `vermillious` | Vermillious | Red sparks | T5 | BASE | COMING_SOON |
| `baubillious` | Baubillious | Bolt of light from the tip | T5 | BASE | COMING_SOON |
| `flagrate` | Flagrate | Fiery writing in the air | T1 | BASE | COMING_SOON |

### 5.2 Fire & Explosion

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `incendio` | Incendio | Produces flame | T1 | BASE | **IMPLEMENTED** |
| `confringo` | Confringo | Blasting Curse - target bursts into flame | T1 | BASE | **IMPLEMENTED** |
| `expulso` | Expulso | Explodes with pressure | T1 | BASE | COMING_SOON |
| `bombarda` | Bombarda | Focused explosion | T4 | BASE | **IMPLEMENTED** |
| `bombarda_maxima` | Bombarda Maxima | Wall-levelling explosion | T4 | RANK_OF `bombarda` | COMING_SOON |
| `partis_temporus` | Partis Temporus | Parts a barrier of fire | T4 | BASE | COMING_SOON |
| `protego_diabolica` | Protego Diabolica | Black-flame protective circle | T4 | BASE | COMING_SOON |
| `glacius` | Glacius | Freezes the target | T5 | BASE | **IMPLEMENTED** |

### 5.3 Force & Impact

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `flipendo` | Flipendo | Knockback Jinx | T5 | BASE | **IMPLEMENTED** |
| `depulso` | Depulso | Banishing Charm | T5 | COUNTER_OF `accio` | **IMPLEMENTED** |
| `everte_statum` | Everte Statum | Throws opponent backward | T4 | BASE | COMING_SOON |
| `alarte_ascendare` | Alarte Ascendare | Launches target upward | T4 | BASE | COMING_SOON |
| `ascendio` | Ascendio | Propels the caster upward | T4 | BASE | COMING_SOON |
| `reducto` | Reducto | Blasts solid objects apart | T1 | BASE | COMING_SOON |
| `deprimo` | Deprimo | Blasts a hole downward | T1 | BASE | COMING_SOON |
| `defodio` | Defodio | Gouges through stone and earth | T1 | BASE | COMING_SOON |
| `waddiwasi` | Waddiwasi | Fires a lodged object | T1 | BASE | COMING_SOON |
| `oppugno` | Oppugno | Sets conjurations on a target | T1 | BASE | COMING_SOON |
| `carpe_retractum` | Carpe Retractum | Grappling rope pulls object or caster | T5 | BASE | COMING_SOON |

### 5.4 Levitation & Movement

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `wingardium_leviosa` | Wingardium Leviosa | Levitates an object | T1 | BASE | **IMPLEMENTED** |
| `levioso` | Levioso | Levitates a creature | T5 | NARROW_OF `wingardium_leviosa` | COMING_SOON |
| `locomotor` | Locomotor [object] | Moves a named object | T1 | BASE | COMING_SOON |
| `mobilicorpus` | Mobilicorpus | Moves an unconscious body | T1 | NARROW_OF `locomotor` | COMING_SOON |
| `mobiliarbus` | Mobiliarbus | Moves trees | T1 | NARROW_OF `locomotor` | COMING_SOON |
| `piertotum_locomotor` | Piertotum Locomotor | Animates statues and armour | T1 | RANK_OF `locomotor` | COMING_SOON |
| `arresto_momentum` | Arresto Momentum | Slows or halts motion | T4 | BASE | **IMPLEMENTED** |
| `descendo` | Descendo | Moves an object downward | T1 | BASE | COMING_SOON |
| `levicorpus` | Levicorpus | Hoists target by the ankle | T1 | BASE | **IMPLEMENTED** |
| `liberacorpus` | Liberacorpus | Releases the target | T1 | COUNTER_OF `levicorpus` | **IMPLEMENTED** |
| `accio` | Accio | Summons an object | T1 | BASE | **IMPLEMENTED** |
| `glisseo` | Glisseo | Stairs become a slide | T1 | BASE | COMING_SOON |

### 5.5 Binding & Restraint

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `petrificus_totalus` | Petrificus Totalus | Full Body-Bind | T1 | BASE | COMING_SOON |
| `incarcerous` | Incarcerous | Conjures binding ropes | T1 | BASE | COMING_SOON |
| `locomotor_mortis` | Locomotor Mortis | Leg-Locker Curse | T1 | BASE | COMING_SOON |
| `impedimenta` | Impedimenta | Slows or halts an advance | T1 | BASE | COMING_SOON |
| `immobulus` | Immobulus | Freezes objects and creatures | T4 | BASE | COMING_SOON |
| `brachiabindo` | Brachiabindo | Binds the body | T4 | BASE | COMING_SOON |
| `colloshoo` | Colloshoo | Sticks shoes to the floor | T3 | BASE | COMING_SOON |
| `epoximise` | Epoximise | Bonds objects together | T3 | BASE | COMING_SOON |
| `ebublio` | Ebublio | Traps target in a bubble | T5 | BASE | COMING_SOON |
| `locomotor_wibbly` | Locomotor Wibbly | Jelly-Legs Jinx | T3 | BASE | COMING_SOON |

### 5.6 Silence & Speech

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `silencio` | Silencio | Silences the target | T1 | BASE | COMING_SOON |
| `langlock` | Langlock | Glues the tongue to the palate | T1 | BASE | COMING_SOON |
| `mimblewimble` | Mimblewimble | Tongue-Tying Curse | T1 | BASE | COMING_SOON |
| `muffliato` | Muffliato | Fills nearby ears with buzzing | T1 | BASE | COMING_SOON |
| `sonorus` | Sonorus | Amplifies the voice | T1 | BASE | COMING_SOON |
| `quietus` | Quietus | Restores normal volume | T1 | COUNTER_OF `sonorus` | COMING_SOON |
| `cantis` | Cantis | Forces the target to sing | T3 | BASE | COMING_SOON |
| `steleus` | Steleus | Sneezing fit | T3 | BASE | COMING_SOON |

### 5.7 Shields & Wards

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `protego` | Protego | Shield Charm | T1 | BASE | **IMPLEMENTED** |
| `protego_totalum` | Protego Totalum | Area shield over a location | T1 | RANK_OF `protego` | COMING_SOON |
| `protego_horribilis` | Protego Horribilis | Wards specifically against dark magic | T1 | RANK_OF `protego` | COMING_SOON |
| `protego_maxima` | Protego Maxima | Castle-scale shield | T4 | RANK_OF `protego_totalum` | COMING_SOON |
| `fianto_duri` | Fianto Duri | Hardens a shield against impact | T4 | BASE | COMING_SOON |
| `repello_inimicum` | Repello Inimicum | Disintegrates enemies at the boundary | T4 | BASE | COMING_SOON |
| `salvio_hexia` | Salvio Hexia | Deflects incoming hexes | T1 | BASE | COMING_SOON |
| `cave_inimicum` | Cave Inimicum | Warns of approaching enemies | T1 | BASE | COMING_SOON |
| `repello_muggletum` | Repello Muggletum | Repels Muggles | T1 | BASE | COMING_SOON |
| `impervius` | Impervius | Repels water and substances | T1 | BASE | COMING_SOON |
| `spongify` | Spongify | Softens a surface | T5 | BASE | COMING_SOON |
| `molliare` | Molliare | Cushioning Charm | T3 | BASE | COMING_SOON |
| `meteolojinx_recanto` | Meteolojinx Recanto | Ends weather jinxes | T1 | BASE | COMING_SOON |

### 5.8 Revelation & Concealment

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `revelio` | Revelio | Reveals concealed things | T5 | BASE | COMING_SOON |
| `homenum_revelio` | Homenum Revelio | Reveals human presence | T1 | NARROW_OF `revelio` | COMING_SOON |
| `specialis_revelio` | Specialis Revelio | Reveals enchantments and ingredients | T1 | NARROW_OF `revelio` | COMING_SOON |
| `aparecium` | Aparecium | Reveals invisible ink | T1 | NARROW_OF `revelio` | COMING_SOON |
| `appare_vestigium` | Appare Vestigium | Reveals traces of past magic | T4 | RANK_OF `revelio` | COMING_SOON |
| `avenseguim` | Avenseguim | A feather tracks its owner | T4 | BASE | COMING_SOON |
| `prior_incantato` | Prior Incantato | Replays the last spell the wand cast | T1 | BASE | COMING_SOON |
| `deletrius` | Deletrius | Dismisses the echo | T1 | COUNTER_OF `prior_incantato` | COMING_SOON |
| `obscuro` | Obscuro | Blindfolds the target | T1 | BASE | COMING_SOON |
| `point_me` | Point Me | Wand acts as a compass | T1 | BASE | COMING_SOON |

### 5.9 Repair & Cleaning

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `reparo` | Reparo | Mends a broken object | T1 | BASE | **IMPLEMENTED** |
| `brackium_emendo` | Brackium Emendo | Mends broken bones - botches on a failed roll | T4 | NARROW_OF `reparo` | COMING_SOON |
| `reverte` | Reverte | Returns an object to its former state | T4 | BASE | COMING_SOON |
| `reparifarge` | Reparifarge | Undoes a transfiguration | T5 | COUNTER_OF transfiguration | COMING_SOON |
| `scourgify` | Scourgify | Cleans thoroughly | T1 | BASE | COMING_SOON |
| `tergeo` | Tergeo | Siphons off mess | T1 | BASE | COMING_SOON |
| `erecto` | Erecto | Erects a tent or structure | T1 | BASE | COMING_SOON |
| `duro` | Duro | Turns the target to stone | T1 | BASE | COMING_SOON |

### 5.10 Healing

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `episkey` | Episkey | Heals minor injuries | T1 | BASE | **IMPLEMENTED** |
| `vulnera_sanentur` | Vulnera Sanentur | Closes deep curse wounds | T1 | RANK_OF `episkey` | COMING_SOON |
| `ferula` | Ferula | Conjures bandages and a splint | T1 | BASE | COMING_SOON |
| `anapneo` | Anapneo | Clears a blocked airway | T1 | BASE | COMING_SOON |
| `rennervate` | Rennervate | Revives the stunned | T1 | COUNTER_OF `stupefy` | COMING_SOON |
| `reparifors` | Reparifors | Cures minor magical ailments | T5 | BASE | COMING_SOON |

### 5.11 Conjuration & Creature Transfiguration

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `avis` | Avis | Conjures a flock of birds | T1 | BASE | COMING_SOON |
| `serpensortia` | Serpensortia | Conjures a snake | T1 | BASE | COMING_SOON |
| `vipera_evanesca` | Vipera Evanesca | Vanishes a conjured snake | T4 | COUNTER_OF `serpensortia` | COMING_SOON |
| `orchideous` | Orchideous | Flowers from the wand | T1 | BASE | COMING_SOON |
| `herbifors` | Herbifors | Flowers sprout from the target | T3 | BASE | COMING_SOON |
| `herbivicus` | Herbivicus | Accelerates plant growth | T5 | BASE | COMING_SOON |
| `lapifors` | Lapifors | Target becomes a rabbit | T5 | BASE | COMING_SOON |
| `ducklifors` | Ducklifors | Target becomes a duck | T5 | BASE | COMING_SOON |
| `pullus` | Pullus | Target becomes a chicken | T5 | BASE | COMING_SOON |
| `entomorphis` | Entomorphis | Target becomes insectoid | T3 | BASE | COMING_SOON |
| `flintifors` | Flintifors | Object becomes a matchbox | T3 | BASE | COMING_SOON |
| `incarcifors` | Incarcifors | Target becomes ropes | T3 | BASE | COMING_SOON |

### 5.12 Body Alteration & Jinxes

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `engorgio` | Engorgio | Enlarges | T1 | BASE | COMING_SOON |
| `reducio` | Reducio | Shrinks | T1 | COUNTER_OF `engorgio` | COMING_SOON |
| `diminuendo` | Diminuendo | Shrinks the target sharply | T5 | RANK_OF `reducio` | COMING_SOON |
| `engorgio_skullus` | Engorgio Skullus | Enlarges the head | T3 | NARROW_OF `engorgio` | COMING_SOON |
| `redactum_skullus` | Redactum Skullus | Shrinks the head | T3 | NARROW_OF `reducio` | COMING_SOON |
| `mutatio_skullus` | Mutatio Skullus | Alters the head | T3 | BASE | COMING_SOON |
| `tentaclifors` | Tentaclifors | Head becomes a tentacle | T3 | BASE | COMING_SOON |
| `melofors` | Melofors | Encases the head in a pumpkin | T3 | BASE | COMING_SOON |
| `densaugeo` | Densaugeo | Enlarges the front teeth | T1 | BASE | COMING_SOON |
| `anteoculatia` | Anteoculatia | Grows antlers | T3 | BASE | COMING_SOON |
| `calvorio` | Calvorio | Causes hair loss | T3 | BASE | COMING_SOON |
| `furnunculus` | Furnunculus | Covers the target in boils | T1 | BASE | COMING_SOON |
| `mucus_ad_nauseam` | Mucus ad Nauseam | Streaming, unstoppable nose | T3 | BASE | COMING_SOON |
| `slugulus_eructo` | Slugulus Eructo | Target vomits slugs | T3 | BASE | COMING_SOON |
| `rictusempra` | Rictusempra | Tickling Charm | T1 | BASE | COMING_SOON |
| `titillando` | Titillando | Tickling hex | T5 | BASE | COMING_SOON |
| `tarantallegra` | Tarantallegra | Forces the legs to dance | T1 | BASE | COMING_SOON |
| `colovaria` | Colovaria | Changes colour | T3 | BASE | COMING_SOON |

### 5.13 Duplication & Vanishing

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `geminio` | Geminio | Duplicates an object | T1 | BASE | COMING_SOON |
| `evanesco` | Evanesco | Vanishes an object entirely | T1 | BASE | COMING_SOON |

### 5.14 Mind

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `legilimens` | Legilimens | Enters the mind of the target | T1 | BASE | COMING_SOON |
| `obliviate` | Obliviate | Erases memory | T1 | BASE | COMING_SOON |
| `confundo` | Confundo | Confuses the target | T1 | BASE | COMING_SOON |
| `riddikulus` | Riddikulus | Forces a boggart into absurdity | T1 | BASE | **IMPLEMENTED** |
| `surgito` | Surgito | Lifts a love enchantment | T4 | BASE | COMING_SOON |

### 5.15 Dark Arts

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `avada_kedavra` | Avada Kedavra | Killing Curse | T1 | BASE | **IMPLEMENTED** |
| `crucio` | Crucio | Cruciatus Curse | T1 | BASE | **IMPLEMENTED** |
| `imperio` | Imperio | Imperius Curse | T1 | BASE | **IMPLEMENTED** |
| `sectumsempra` | Sectumsempra | Deep slashing wounds | T1 | RANK_OF `diffindo` | COMING_SOON |
| `morsmordre` | Morsmordre | Conjures the Dark Mark | T1 | BASE | COMING_SOON |

### 5.16 Access & Passage

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `alohomora` | Alohomora | Unlocks | T1 | BASE | **IMPLEMENTED** |
| `aberto` | Aberto | Forces a locked door | T4 | RANK_OF `alohomora` | COMING_SOON |
| `colloportus` | Colloportus | Seals a door | T1 | COUNTER_OF `alohomora` | **IMPLEMENTED** |
| `dissendium` | Dissendium | Opens the one-eyed witch passage | T1 | BASE | COMING_SOON |
| `portus` | Portus | Creates a Portkey | T1 | BASE | COMING_SOON |
| `finestra` | Finestra | Softens or shatters glass | T4 | BASE | COMING_SOON |

### 5.17 Duelling Core & Counters

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `expelliarmus` | Expelliarmus | Disarms | T1 | BASE | **IMPLEMENTED** |
| `stupefy` | Stupefy | Stuns | T1 | BASE | **IMPLEMENTED** |
| `diffindo` | Diffindo | Severing Charm | T1 | BASE | **IMPLEMENTED** |
| `relashio` | Relashio | Forces a release | T1 | BASE | COMING_SOON |
| `finite_incantatem` | Finite Incantatem | Ends spell effects in the area | T1 | BASE | **IMPLEMENTED** |
| `expecto_patronum` | Expecto Patronum | Conjures a Patronus | T1 | BASE | **IMPLEMENTED** |
| `ventus` | Ventus | Jet of wind | T4 | BASE | COMING_SOON |

### 5.18 Household & Utility

| id | Incantation | Effect | Tier | Relation | Status |
|---|---|---|---|---|---|
| `aguamenti` | Aguamenti | Jet of clean water | T1 | BASE | **IMPLEMENTED** |
| `pack` | Pack | Packs luggage | T1 | BASE | COMING_SOON |
| `peskipiksi_pesternomi` | Peskipiksi Pesternomi | Does nothing. Always fizzles. | T1 | BASE | COMING_SOON |

---

## 6. Open decisions

Q1 (T5 gating), Q2 (rank threshold), Q4 (teacher dialogue) and Q5 (acquisition paths) are
unchanged and still need rulings. Q3 is resolved in the draft. Q6 is answered in §0.4.

Two further rulings the audit surfaced:

- **Q7 — where do `claustra_reverto` and `frigora` go?** Both are tier `original` and both are
  live. The roster has no `ORIGINAL` slot. Keep them as an explicit original-content section,
  or retire them?
- **Q8 — `capacious_extremis` is registered as a spell but §8 excludes the Extension Charm as a
  system.** One of the two has to give.

## 7. Counts

| Bucket | Count |
|---|---|
| Roster entries | 154 |
| Already implemented | 28 |
| To author as COMING_SOON | 126 |
| Live registrations outside the roster | 5 |
| Lang keys implied (name + description) | ~252 |

*Roster content is Christian's draft rev 1. Everything in §0, the Status column and the counts
are derived from the live registry on 2026-08-19; no status was carried over from the draft.*
