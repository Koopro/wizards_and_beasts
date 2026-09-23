# Wizards & Beasts — Entity Design Bible

**Design pass for the 96 data-driven creatures**, plus a separate section for the 24 bespoke
entities. Written against the repository at `947c9526` on `dev-2026-09-17-rework`, which is treated
as authoritative — several claims in `ENTITY_AUDIT.md` were already corrected by the foundation
implementation and are corrected again here where they still mislead.

**This document changes no code, data, model, texture or animation.** It exists so that a later
implementation agent can take one creature and build it without inventing its identity.

## How to use this document

Read the five **Language** sections first. They carry most of the design: a creature entry says only
what is *different* about that creature, and leans on the conventions here for everything else. Then
read your creature's **Family** section, which decides what it shares with its neighbours. Only then
read the creature entry.

If a creature entry seems thin, that is usually correct — it means the family conventions already
describe it and the entry only needs to record its deltas.

### Verified corrections to the audit

The brief warns not to trust the earlier audit. Confirmed against the repository today:

- **Hurt animation is wired.** `GenericBeastEntity.hurtServer` fires `CLIPS_HIT` (`hit`, `flinch`).
  Missing clips are an art gap, not a code gap.
- **The death hook exists** as of the foundation commit, gated by `triggerFirstDeclared`. No rig
  declares a death clip yet.
- **Dementor already flies** — `FlyingMoveControl` + `BeastNavigation.flying`, no `ON_GROUND`
  placement anywhere. It is not a walking creature and never was.
- **Phoenix and Bowtruckle are bespoke** (`GeoEntityBase`), have no `CreatureDefinition`, and
  therefore **no profile in this document reaches them**. They are in the Bespoke section.
- **`IdleProfile.forBodyPlan` covers 10 of 12 body plans.** `WORM_LARVA` and `SESSILE` deliberately
  have no default, because a Flobberworm and a Horklump do not idle — they persist.

---

# Design Principles

**1. Identity before decoration.** A player should name the creature from its silhouette in motion,
before reading a label and before seeing its colour. Every design below is checked against that.

**2. The lore already decided.** Each creature has an authored `shortLore` line, and it is usually
the whole brief. *"A silent blue bird that screams its whole life back at death"* fully specifies the
Jobberknoll's sound design: no ambient at all, one death cry. Where lore and instinct disagree, lore
wins.

**3. Silence, stillness and absence are designs.** Not every creature vocalises, idles or reacts. A
Lethifold that rustles is a worse Lethifold. The `SoundProfile` and `IdleProfile` defaults are
"nothing" precisely so that nothing stays a legitimate answer.

**4. Differentiate by motion first, colour last.** Seventeen creatures share `WINGED_QUADRUPED` and
five share a hitbox tier. Palette cannot carry that load. Posture, gait rhythm, idle vocabulary and
attack timing can.

**5. Reuse the ability system; design the choice around it.** Thirty-three creatures have `enrage`
and eighteen have `status_on_hit`. This document never proposes a second poison system — it says
*when* the creature chooses to poison.

**6. Rhythm is identity.** Twenty-nine hostile creatures share one approach-and-bite beat. The
`CombatProfile` exists to break that, and the interesting field is almost always `windupTicks` and
`preferredRange`, not damage.

**7. Do not invent canon.** Where a design is the mod's interpretation, it is marked **[design]**.
Where it follows the books, **[canon]**. The Bestiary's own `basis` field already makes this
distinction per entry and this document does not contradict it.

---

# Visual Language

## Silhouette rules

A creature is recognisable when **one shape dominates**. Each body plan gets one dominant read, and
individual creatures vary it rather than replacing it:

| Body plan | Dominant read | Varied by |
|---|---|---|
| QUADRUPED | four-legged mass with a distinct head carriage | head shape, back line, horn/tail furniture |
| WINGED_QUADRUPED | wing-to-body ratio | neck length, wing span, spine profile, tail |
| BIPED_HUMANOID | upright stance and arm length | posture (stooped vs erect), head-to-body ratio |
| BLOB_SPHERE | a single rounded mass | surface (fur, slime, smoke), edge quality |
| AQUATIC | continuous streamlined line | fin placement, tail type, limb presence |
| ARTHROPOD_MULTILEG | leg count and span | body segmentation, carapace, claw furniture |
| SERPENTINE | a long unbroken curve | head crest, girth taper, number of heads |
| INSECTOID_FLYER | wing blur and tiny mass | wing shape, body-to-wing ratio |
| AVIAN | beak and wing silhouette | tail plume, leg length, crest |
| LARGE_HUMANOID | bulk above the player's eyeline | shoulder line, head sink, arm mass |
| WORM_LARVA | a single soft tube | nothing — it is the design |
| SESSILE | rooted, wider at the base | bristle/frond profile |

## Surface vocabulary

Six surfaces, each with a pixel treatment that should stay consistent across the roster so materials
read the same way on every creature:

- **Fur** — soft dithered edges, two-tone with a warmer underside.
- **Feather** — banded, directional, hard edge at the wing trailing edge.
- **Scale** — tight repeating pattern, specular highlight along the spine ridge.
- **Hide/skin** — low contrast, wrinkle lines at joints only.
- **Bark/plant** — vertical grain, irregular silhouette edge.
- **Magical material** (smoke, flame, light) — low internal contrast, silhouette carried by the
  emissive mask rather than the base texture.

## Colour

Describe palettes, do not prescribe hex. The project has **no palette data file for creatures**, so
hex values here would be inventions. Each entry names a palette *intent* — "cold slate with a single
warm accent" — and leaves the value to the artist.

One roster-wide rule: **one accent colour per creature, used only where it means something** (the
Clabbert's pustule, the Fwooper's plumage, the Runespoor's heads). Creatures whose whole body is
accent-coloured stop reading as individuals.

## Emissive

**Do not give every magical creature glowing eyes.** The mod already has the right rule and 50 of 110
rigs use it: a glowmask is attached purely by file presence via `GeoRendererHelper.applyGlowIfPresent`.

Emissive is earned by exactly three things:
1. The creature is a light source in lore (`bioluminescence` ability — 11 creatures).
2. A single organ signals state (Clabbert's pustule, Billywig's sting).
3. The creature is made of light or fire (Phoenix, Ashwinder trail).

Everything else: no glowmask.

---

# Behaviour Language

## Idle vocabulary

Use `IdleProfile`. Actions are one-shot clips through the `triggerDeclared` gate, so naming a clip a
rig lacks is inert, not broken. The vocabulary below is what the body-plan defaults already ship plus
the additions this document proposes; **anything not in this list needs a new clip name agreed before
use**.

Shipped defaults: `preen ruffle stretch hover flit shake graze sniff scratch coil taste_air groom
skitter peek grunt drift roll squirm settle`.

Proposed additions used by entries below: `dig inspect bask rear listen fan_wings burrow bob`.

Each creature gets **primary / secondary / rare**:
- **Primary** — the creature's signature, fires most often.
- **Secondary** — supports the read, adds variety.
- **Rare** — a moment of character; should surprise a player who has watched for a while.

`minDelay`/`maxDelay` carry personality too: a skittish creature idles often and briefly (90/240), a
heavy one rarely and slowly (300/700).

## Reaction vocabulary

Use `CreatureReaction`. **Most stimuli have no event raising them yet** — this is intended design
documentation, not a request to wire events. Record only reactions with a real rationale.

Roster-wide conventions:
- `FEARFUL` creatures (12) already panic on damage through vanilla goals. Do not duplicate that with a
  `DAMAGE → FLEE` reaction; it is already true.
- `FIRE → FLEE` is the default for anything wooden, furred or plant-bodied, and must be **omitted**
  for the 15 `FIRE_IMMUNE` creatures — a Salamander fleeing fire is a bug in the design.
- `LIGHT → FLEE` belongs only to creatures lore places in darkness.
- `CREATURE_DEATH → FLEE` belongs to herd and prey animals; `→ BECOME_ALERT` to pack predators.

## Combat identity

Use `CombatProfile`. The default is today's shared rhythm, so **only declare a profile when the
creature's fight should feel different**. The interesting fields:

| Style | Feel | Typical settings |
|---|---|---|
| `BRUTE` | walks in, hits | default; the honest choice for simple animals |
| `AMBUSHER` | waits, commits, recovers | high `windupTicks`, high `recoveryTicks` |
| `HARRIER` | darts in and out | `preferredRange` 3–6, fast `approachSpeed` |
| `SKIRMISHER` | keeps away, ability does the work | `preferredRange` 6–12 |
| `PACK_HUNTER` | fights beside its own | pair with the `PACK` trait |

---

# Animation Language

Clips are marked **essential / recommended / optional**:

- **Essential** — the creature is broken or unreadable without it. Always: `idle`, one locomotion
  clip. For anything that fights: an attack clip.
- **Recommended** — the creature reads as finished with it. Usually `hurt`/`flinch`, a voice clip,
  and a death clip.
- **Optional** — identity polish: secondary idle, alert, flee, special.

**Naming must match what the entity already looks for**, or the clip never fires:
`attack|strike|bite|lunge` for attacks, `hit|flinch` for hurt, `death|die|collapse` for death,
`groan|hiss|howl|call|song` for ambient voice. A clip named anything else needs a code change.

**Current state: 84 of 110 rigs have exactly four clips** (idle + locomotion + attack + voice). The
production plan below treats that four-clip set as the floor, not the target.

---

# Sound Language

Use `SoundProfile`. Ids resolve through `BuiltInRegistries.SOUND_EVENT`, so vanilla sounds are
available immediately and a bespoke sound can replace one later without a data-shape change.

**Three tiers, and most creatures should sit in tier 2:**

1. **Silent by design** — no fields at all. For creatures whose silence is characterisation: the
   Lethifold, the Demiguise, the Jobberknoll's *ambient* (it has only a death cry), the Obscurus.
   Each entry below that chooses silence says why.
2. **Vanilla-sourced** — point at an existing vanilla sound that fits. This is the right default for
   most of the 96 and costs nothing. Six creatures already demonstrate it.
3. **Bespoke** — a recorded sound, justified only where no vanilla sound carries the identity: the
   Fwooper's maddening song, the Jobberknoll's death cry, the Mandrake scream (already exists), the
   Thunderbird's storm.

**Do not give every creature generic animal noises.** A Horklump is a fungus. A Boggart's sound
should be borrowed from whatever it has become, not owned by the Boggart.

Where a creature's rig already has a voice clip (69 of them do), the sound and the clip should fire
together — `playAmbientSound` already triggers `CLIPS_AMBIENT`, so a matching `ambient` sound
completes a beat that currently plays silently.

---

# Creature Families

Twelve body plans. For each: what stays shared, what must differ, and whether rigs, UVs, animations
and behaviour profiles can be reused. **No recommendation here replaces working architecture** — the
generic entity classes, the clip gate, the navigation split and the ability system all stay.

---

## WINGED_QUADRUPED — 17 creatures

`abraxan aethonan granian griffin hippogriff manticore snallygaster` +
the 10 dragons (`antipodean_opaleye chinese_fireball common_welsh_green hebridean_black
hungarian_horntail norwegian_ridgeback peruvian_vipertooth romanian_longhorn swedish_short_snout
ukrainian_ironbelly`)

The roster's biggest identity problem: seventeen creatures, most at the same 2.7 × 2.9 hitbox, all
reading as "large flying thing".

**Shared infrastructure.** `GenericFlyingBeastEntity`, flying navigation, `FlyingMoveControl`, the
`fly` clip, the dragon block for the ten dragons. All correct; keep.

**The split that matters — three sub-families, not one:**

| Sub-family | Members | Silhouette rule | Gait | Combat |
|---|---|---|---|---|
| **Winged horses** | abraxan, aethonan, granian | equine body, feathered wings folded *along* the flank, long neck, flowing tail | graceful, wings mostly still in cruise | non-combatant or defensive only |
| **Composite beasts** | griffin, hippogriff, manticore, snallygaster | two animals joined at a visible seam — bird front / cat rear, the join is the read | alert, head-led, sharp direction changes | `AMBUSHER` or `HARRIER`; dives |
| **Dragons** | the ten breeds | heavy wedge head, long neck *and* long tail balancing, wings larger than body | heavy, deliberate, ground-shaking landing | `SKIRMISHER` — breath at range, bite when cornered |

**Dragon differentiation** is the hardest case: ten creatures with one body plan and one rig family.
They must differ by **head furniture and proportion**, never by colour alone:

- `hungarian_horntail` — spiked tail is the silhouette; longest tail, heaviest build **[canon]**
- `ukrainian_ironbelly` — largest mass, shortest relative wings, low slung **[canon]**
- `antipodean_opaleye` — slenderest, largest eyes, no pupils; most upright neck **[canon]**
- `swedish_short_snout` — short blunt muzzle, the only snub profile **[canon]**
- `chinese_fireball` — fringed face, snub-nosed, broad skull **[canon]**
- `peruvian_vipertooth` — smallest dragon, compact, quick **[canon]**
- `norwegian_ridgeback` — dorsal ridge ridgeline down the spine **[canon]**
- `romanian_longhorn` — two long golden horns, forward-pointing **[canon]**
- `hebridean_black` — bat-like wings, ridged spine, arrow-tipped tail **[canon]**
- `common_welsh_green` — smallest profile, most "ordinary" dragon; the baseline others read against **[canon]**

**Animation family:** one dragon animation set, retimed per breed — the Vipertooth's cycle 20%
faster, the Ironbelly's 30% slower. Retiming is cheap and does most of the differentiation work.
**Winged horses need their own set** (no breath clip, gentler wing rhythm). Composites can adapt the
dragon set but need a distinct `call`.

**Model family:** dragons can share a rig with swapped head/tail/wing furniture. Winged horses cannot
— an equine barrel and a dragon torso are different shapes. Composites need their own.

**Texture family:** UV structure shares within each sub-family, not across.

---

## QUADRUPED — 23 creatures

`centaur chimaera crup dugbog erumpent graphorn hodag jarvey knarl kneazle matagot moke murtlap
nogtail nundu qilin reem salamander sphinx tebo unicorn wampus_cat zouwu`

**Note a classification oddity to record, not fix:** `centaur` and `sphinx` are filed as QUADRUPED.
Both are canonically intelligent Beings with humanoid or human-headed upper bodies. Their rigs need a
humanoid torso on a quadruped base — closer to the composite sub-family above than to a Crup.
**Open design decision**, listed at the end.

**Four sub-families:**

| Sub-family | Members | Read |
|---|---|---|
| **Small companions** | crup, kneazle, puffskein-adjacent, moke, knarl | cat/dog scale, domestic posture, expressive tail and ears |
| **Big cats** | nundu, wampus_cat, zouwu, tebo, matagot | heavy shoulder, low head carriage, prowling gait |
| **Horned brutes** | erumpent, graphorn, reem, qilin | horn furniture is the silhouette; mass forward |
| **Intelligent / other** | centaur, sphinx, unicorn, salamander, murtlap, dugbog, hodag, jarvey, nogtail | each its own; see entries |

**Shared:** `GenericGroundBeastEntity`, stroll goal, ground navigation. Keep.

**Must differ:** head carriage (high = alert prey, low = predator), tail behaviour, and idle
vocabulary. `graze`+`sniff` for prey, `stretch`+`scratch` for cats, `rear`+`listen` for horned
brutes.

**Animation family:** one quadruped walk cycle serves the whole family with per-creature retiming and
head-height offsets. Big cats need a distinct prowl. Horned brutes need a `charge` that reads as
committed weight.

---

## BIPED_HUMANOID — 13 creatures

`clabbert demiguise erkling ghoul gnome imp leprechaun maledictus porlock pukwudgie red_cap
rougarou werewolf`

Widest behavioural spread in the roster: a Leprechaun, a Werewolf and a Ghoul share a body plan and
should share almost nothing else.

**Posture is the differentiator**, and it should be visible standing still:

- **Erect and small** — gnome, leprechaun, imp, red_cap: upright, hands active, quick
- **Stooped** — ghoul, erkling, pukwudgie: hunched, arms long, head forward
- **Clinging** — clabbert, porlock: not upright at all; braced against a surface
- **Bestial** — werewolf, rougarou: digitigrade, shoulders above head
- **Concealing** — demiguise, maledictus: posture is a wrap; silhouette hides its own limbs

**Shared:** ground navigation, stroll, the humanoid rig skeleton.
**Must differ:** limb length ratio and stance. These are one rig with very different proportions —
cheap to differentiate, and currently under-used.

**Animation family:** one humanoid base; the bestial pair need their own quadrupedal-lean run.

---

## BLOB_SPHERE — 9 creatures

`boggart bundimun lethifold lobalug obscurus pogrebin puffskein pygmy_puff toad`

The family where **surface carries the entire identity**, because the silhouette is a sphere.

- `puffskein` / `pygmy_puff` — long soft fur, edge broken by strands; the pair differ by size and
  palette only, which is correct **[canon]** — a Pygmy Puff *is* a bred-down Puffskein
- `bundimun` — fur over many eyes, greenish, asymmetrical
- `lethifold` — not a sphere at all in motion: a flat drifting cloak. Needs its own silhouette rule
- `obscurus` — smoke; no fixed edge, carried by emissive
- `boggart` — shape is whatever it has become; the base form should be deliberately unresolved
- `lobalug` — aquatic, a stalk with a venom sac
- `pogrebin` — a rock with arms; reads as a boulder until it moves
- `toad` — plain amphibian, and should stay plain

**Shared:** the simplest rig in the roster.
**Must differ:** surface treatment and idle. `squirm`/`settle` suits the furred ones; the Lethifold
and Obscurus need `drift`; the Pogrebin needs to be *still* and then suddenly not.

---

## AQUATIC — 8 creatures

`grindylow hippocampus kappa kelpie merperson plimpy ramora shrake`

**Shared:** `GenericAquaticBeastEntity` with `WaterBoundPathNavigation` and
`SmoothSwimmingMoveControl` — already correct, keep.

**Must differ:** body line and limb presence.
- Streamlined, no limbs: ramora, shrake
- Limbed swimmers: grindylow (long fingers), kappa (shell, beak), plimpy (long legs)
- Horse-line: hippocampus, kelpie — the kelpie must read as *wrong*, a horse where a horse should not be
- Humanoid: merperson — and note it doubles as a player heritage form

**Animation family:** one swim cycle; the limbed ones need a distinct paddle.

---

## ARTHROPOD_MULTILEG — 6 creatures

`acromantula blast_ended_skrewt chizpurfle fire_crab mackled_malaclaw quintaped`

**Leg count is the read** and it is genuinely distinct per creature: Acromantula eight, Quintaped
five **[canon]**, crab-forms with claws, Skrewt with neither.

**Shared:** multi-leg walk cycle, ground navigation.
**Must differ:** body segmentation and leg span. The Quintaped's five legs are its entire identity
and must be visible at a glance.

---

## SERPENTINE — 5 creatures

`ashwinder basilisk horned_serpent occamy sea_serpent`

**Shared:** serpentine rig with a long spine chain.
**Must differ:** head furniture, girth and whether the body leaves the ground.
- `basilisk` — the giant; see Special Attention
- `horned_serpent` — horn on the brow, jewel **[canon]**
- `occamy` — plumed and winged, not a true serpent; already has `occamy_choranaptyxis` sizing
- `ashwinder` — thin, ember-trailing, temporary
- `sea_serpent` — aquatic humps

**Note:** all five are `Locomotion.GROUND` including `sea_serpent`. Recorded as an open decision.

---

## INSECTOID_FLYER — 5 creatures

`billywig doxy fairy glumbumble swooping_evil`

**Shared:** `GenericFlyingBeastEntity`, `hover`/`flit` idle.
**Must differ:** wing shape and flight rhythm. The Billywig's spin **[canon]**, the Doxy's four
limbs and dark colouring, the Fairy's humanoid form, the Swooping Evil's cloak-like fold.

**The Fairy is already correct** — `glow_self`, whole-body glowmask, documented in `fairy_model.py`.
Do not "fix" it.

---

## AVIAN — 5 creatures

`diricawl fwooper golden_snidget jobberknoll thunderbird`

**Must differ sharply** despite a shared plan:
- `diricawl` — flightless, dodo-like, vanishes **[canon]**
- `fwooper` — brilliant plumage, maddening song **[canon]**
- `golden_snidget` — tiny, spherical, jewel-eyed, impossibly fast **[canon]**
- `jobberknoll` — small, blue, silent until death **[canon]**
- `thunderbird` — multiple wings, storm-bringing, the largest **[canon]**

Three of the five have an identity that is **entirely sound-based or movement-based**, not visual.
Design accordingly.

---

## LARGE_HUMANOID — 3 creatures

`giant troll yeti`

**Shared:** humanoid rig at large scale, slow gait.
**Must differ:** the giant reads as a person too big; the troll as dim and top-heavy; the yeti as
white and cold-adapted. Head sink into shoulders increases: giant < troll < yeti.

---

## WORM_LARVA — 1 · SESSILE — 1

`flobberworm`, `horklump`

Both deliberately minimal. The Flobberworm "does little but eat lettuce" and the Horklump is a rooted
fungus. **Neither has an idle default and neither should get one.** No combat, no reactions, no
sound. They exist as harvestable set dressing and that is the correct design.

---

# Data-Driven Creatures

96 entries. Each records only what is **specific** to that creature — its family section above
carries the shared conventions. Fields: Identity, Visual, Movement, Idle, Sound, Reactions, Combat,
Ecology, Player, Spells, Clips, Notes.

**Clip legend:** `E` essential, `R` recommended, `O` optional. Clips already on disk are marked ✓.
**Lore legend:** **[canon]** from the books, **[design]** this mod's interpretation.

---

## Family: WINGED_QUADRUPED (17)

### Abraxan `abraxan` · NEUTRAL · XXXX · PACK
- **Identity** Giant palomino draught horse with wings — mass first, flight second **[canon]**.
- **Visual** Heavy barrel, thick feathered fetlocks, broad wings that look barely adequate. Pale gold coat, white mane. No emissive.
- **Movement** Powerful and unhurried; takes off reluctantly with a running start. Landing is heavy.
- **Idle** primary `graze` · secondary `shake` · rare `rear` · 300/700 (slow, placid)
- **Sound** Tier 2 — vanilla horse ambient/hurt/death, pitched down. Its `call` clip ✓ is already there and silent.
- **Reactions** `CREATURE_DEATH → BECOME_ALERT` r16 (herd). No fire reaction beyond default.
- **Combat** Non-aggressor. `BRUTE` default only when provoked; `enrage` already covers escalation.
- **Ecology** Herd herbivore, draught animal. Social, non-territorial.
- **Player** Neutral, bondable — a working animal.
- **Spells** None specific. Do not add.
- **Clips** E `idle`, `fly`; R `walk`, `hurt`, `death`, `call`✓; O `graze`, `rear`
- **Notes** Differentiate from Aethonan/Granian by *mass and slowness*, not colour.

### Aethonan `aethonan` · NEUTRAL · XX · PACK
- **Identity** The ordinary winged horse — chestnut, common, the baseline the other three read against **[canon]**.
- **Visual** Standard riding-horse proportions, chestnut, moderate wings.
- **Movement** Balanced, comfortable, the most "normal" gait in the family.
- **Idle** primary `graze` · secondary `sniff` · rare `shake` · 240/600
- **Sound** Tier 2 — vanilla horse, unmodified.
- **Reactions** `CREATURE_DEATH → BECOME_ALERT` r16.
- **Combat** Non-aggressor. Default.
- **Ecology** Herd herbivore, the most numerous winged horse in Britain **[canon]**.
- **Player** Neutral, bondable, rideable-feeling.
- **Spells** None.
- **Clips** E `idle`, `fly`; R `walk`, `hurt`, `death`, `call`✓
- **Notes** Deliberately unremarkable. Its job is to make Abraxan and Granian legible by contrast.

### Granian `granian` · NEUTRAL · XXXX
- **Identity** The fast one — grey-green, lean, built like a racehorse **[canon]**.
- **Visual** Slender, long-legged, narrow chest, long swept-back wings. Grey-green coat.
- **Movement** Quick, nervous, light-footed. Takes off in two strides.
- **Idle** primary `shake` · secondary `sniff` · rare `rear` · 140/380 (restless)
- **Sound** Tier 2 — vanilla horse, pitched up.
- **Reactions** `PLAYER → BECOME_ALERT` r12; `CREATURE_DEATH → FLEE` r16.
- **Combat** Flees rather than fights. No profile needed.
- **Ecology** Herd, skittish, open country.
- **Player** Avoidant-neutral; hardest of the winged horses to approach.
- **Spells** None.
- **Clips** E `idle`, `fly`; R `walk`, `hurt`, `call`✓; O `rear`
- **Notes** Speed must read in the *animation timing*, not a speed stat alone.

### Griffin `griffin` · NEUTRAL · XXXX · KNOCKBACK · dive_bomb
- **Identity** Eagle front, lion rear, and the seam between them is the silhouette **[canon]**.
- **Visual** Feathered head/chest/forelimbs, furred hindquarters and tail — the transition should be abrupt and visible. Gold and tawny. Fierce eyes, no glow.
- **Movement** Alert and raptorial; head leads every turn. Perches rather than stands.
- **Idle** primary `preen` · secondary `listen` · rare `fan_wings` · 200/500
- **Sound** Tier 2 — an eagle screech for ambient; heavy wingbeat for attack.
- **Reactions** `PLAYER → BECOME_ALERT` r14 (guardian).
- **Combat** `HARRIER`, `preferredRange` 5, fast approach. `dive_bomb` already exists — design the *choice*: dives when the target is in the open and it has height, otherwise closes on the ground.
- **Ecology** Solitary apex flyer, territorial over high ground **[canon]**.
- **Player** Territorial, not hostile-on-sight.
- **Spells** None specific.
- **Clips** E `idle`, `fly`, `strike`✓; R `walk`, `hurt`, `death`, `call`✓; O `preen`, `dive`
- **Notes** Share the composite rig with Hippogriff; differ by tail (lion vs feathered) and mass.

### Hippogriff `hippogriff` · NEUTRAL · XXX · CHARGE+KNOCKBACK · dive_bomb
- **Identity** Eagle head and forelegs, horse body — and *proud*. Bow first **[canon]**.
- **Visual** Eagle head, horse hindquarters, feathered wings, steel-grey to roan. Eye contact matters; the head should read as attentive.
- **Movement** Stately, deliberate. Carries its head high. Bows.
- **Idle** primary `listen` · secondary `preen` · rare `bow` · 220/540
- **Sound** Tier 2 — a clipped eagle call, less shrill than the Griffin's.
- **Reactions** `PLAYER → BECOME_ALERT` r12 — the approach ritual is its whole character.
- **Combat** `BRUTE` with a high `windupTicks` (~15): it warns before it strikes. Attacks only the disrespectful.
- **Ecology** Semi-social, proud, forest and hill.
- **Player** **The roster's best bonding candidate** — existing `BondableBeast` applies; the bow should gate it.
- **Spells** None. The interaction is behavioural, not magical.
- **Clips** E `idle`, `fly`, `attack`✓; R `walk`, `hurt`, `death`, `call`; O `bow` ← identity-critical
- **Notes** `bow` is the single highest-value optional clip in the roster.

### Manticore `manticore` · HOSTILE · XXXXX · POISON+FIRE_IMMUNE
- **Identity** Lion body, human head, scorpion sting — three species arguing **[canon]**.
- **Visual** Heavy lion body, disturbingly human face, segmented tail ending in a sting. Dark red-brown. **No glowing eyes** — the human face is the horror.
- **Movement** Prowling, with the tail moving independently and constantly.
- **Idle** primary `taste_air` (tail) · secondary `stretch` · rare `groom` · 200/480
- **Sound** Tier 3 bespoke — it should *almost* speak. Nothing vanilla carries this.
- **Reactions** None declared; it initiates.
- **Combat** `AMBUSHER`, `windupTicks` 18, `recoveryTicks` 25. `status_on_hit` is the sting — design: the tail strikes on the *recovery*, so the player who steps in after the swing is the one who gets poisoned.
- **Ecology** Apex solitary predator.
- **Player** Hostile on sight.
- **Spells** `spell_resist` already present — dark and protective magic both land at reduced effect.
- **Clips** E `idle`, `fly`, `strike`✓; R `walk`, `hurt`, `death`, `hiss`✓; O `sting`
- **Notes** The human face must survive the low pixel budget; reserve texture density for it.

### Snallygaster `snallygaster` · NEUTRAL · XXXX · FIRE_ATTACK+CHARGE
- **Identity** Half-bird half-reptile, mistaken for a dragon and offended by it **[canon]**.
- **Visual** Beaked but toothed, scaled body with feathered wing leading edges — deliberately unresolved between bird and reptile. Metallic grey-green.
- **Movement** Awkward on the ground, decisive in the air.
- **Idle** primary `stretch` · secondary `listen` · rare `fan_wings` · 220/520
- **Sound** Tier 2 — a mechanical, whistling screech.
- **Reactions** `PLAYER → INVESTIGATE` r16 — it approaches, which is what makes it dangerous.
- **Combat** `HARRIER` with `ranged_hex` already present; `preferredRange` 6.
- **Ecology** Solitary, North American **[canon]**.
- **Player** Curious-dangerous.
- **Spells** None.
- **Clips** E `idle`, `fly`, `strike`✓; R `walk`, `hurt`, `death`, `hiss`✓
- **Notes** Should read as *wrong* beside a dragon, not as a lesser one.

### The ten dragons
`antipodean_opaleye chinese_fireball common_welsh_green hebridean_black hungarian_horntail
norwegian_ridgeback peruvian_vipertooth romanian_longhorn swedish_short_snout ukrainian_ironbelly`

All: HOSTILE or NEUTRAL, XXXXX, `FIRE_IMMUNE`+`FIRE_ATTACK`+`KNOCKBACK`, ability `enrage`, `dragon`
block. **All ten currently declare zero clips** — they animate through the dragon path.

- **Shared identity** Wedge head, long neck and tail in balance, wings larger than body, heavy landing.
- **Shared movement** Deliberate. Weight telegraphed through the wing-beat before takeoff.
- **Shared idle** primary `stretch` · secondary `shake` · rare `fan_wings` · 300/700
- **Shared sound** Tier 2 initially (vanilla ender dragon growl, pitched per breed); Tier 3 for breath.
- **Shared reactions** None — a dragon reacts to nothing smaller than itself. Deliberate.
- **Shared combat** `SKIRMISHER`, `preferredRange` 8–12, breath at range, bite when closed. `windupTicks` 20+ before breath — the telegraph is what makes a dragon fight survivable and readable.
- **Shared ecology** Apex, solitary, territorial. No social behaviour.
- **Shared player** Hostile; never bondable **[canon]** — dragons are not tamed.
- **Shared spells** `FIRE_IMMUNE` must suppress any fire-based spell reaction. Spell resistance is thematically right but is **not** currently on the dragons — recorded as an open decision, not assumed.
- **Shared clips** E `idle`, `fly`, `breath`, `bite`; R `walk`, `hurt`, `death`, `roar`; O `land`, `takeoff`
- **Per-breed deltas** — the only differentiation that matters, all **[canon]**:

| Breed | Silhouette delta | Timing delta | Flame |
|---|---|---|---|
| `hungarian_horntail` | longest spiked tail, heaviest | slowest windup, longest reach | yellow, longest |
| `ukrainian_ironbelly` | largest mass, shortest wings | slowest everything | ordinary |
| `antipodean_opaleye` | slenderest, pupil-less eyes, upright neck | quickest of the large | vivid scarlet |
| `swedish_short_snout` | snub muzzle, only blunt profile | mid | brilliant blue |
| `chinese_fireball` | face fringe, broad skull | quick head turns | mushroom-shaped |
| `peruvian_vipertooth` | smallest, most compact | fastest cycles (+20%) | short, venomous bite instead |
| `norwegian_ridgeback` | dorsal ridge down spine | mid | ordinary |
| `romanian_longhorn` | two forward golden horns | mid, charge-heavy | ordinary |
| `hebridean_black` | bat wings, arrow tail | sharp, angular | ordinary |
| `common_welsh_green` | smallest silhouette, plainest | baseline | and it **sings** — unique voice clip **[canon]** |

- **Notes** One rig, ten furniture swaps, ten retimings. The Welsh Green's song is the cheapest
  distinctive feature in the whole dragon set and should not be cut.

---

## Family: QUADRUPED (23)

### Unicorn `unicorn` · PASSIVE · XXXX · REGEN · heal_aura, wary, groomable, shed, slayer_curse
- **Identity** Pure white horned horse; the horn and the *purity gate* are the identity **[canon]**.
- **Visual** Fine-boned horse, single spiral horn, cloven hooves, tail like spun glass. White with the faintest warm shadow. **Emissive: no** — purity reads through value, not glow.
- **Movement** Weightless, precise, never hurried. Flees without panic.
- **Idle** primary `graze` · secondary `listen` · rare `rear` · 260/620
- **Sound** Tier 2, very sparse — a soft equine breath only. **No death sound**: killing a unicorn should be silent and awful.
- **Reactions** `PLAYER → FLEE` r18 *conditional on corruption* — already implemented in `WildlifeRules` (purity limit 25, taint 50). Do not duplicate.
- **Combat** Non-combatant. Never fights.
- **Ecology** Solitary-to-small-group, deep forest, the roster's moral litmus test.
- **Player** Avoidant; `groomable`/`shed` make it bondable only for the uncorrupted.
- **Spells** Dark magic should repel it further; `slayer_curse` already penalises killing it. **This is already the mod's best spell-creature interaction — extend nothing.**
- **Clips** E `idle`, `walk`; R `hurt`✓(`flinch`), `call`✓, `death`; O `rear`, `graze`
- **Notes** Identity is already mechanically complete. Work here is art and sound only.

### Nundu `nundu` · HOSTILE · XXXXX · POISON · nundu_pestilence, enrage, dread_aura
- **Identity** A gigantic leopard whose *breath* is the weapon **[canon]** — the most dangerous beast in the world.
- **Visual** Huge, low-slung leopard; heavy shoulders, enormous head, spotted. Breath should be visible as a faint haze, **not** a glow.
- **Movement** Silent prowl — the horror is that something that big makes no noise.
- **Idle** primary `stretch` · secondary `groom` · rare `taste_air` · 240/560
- **Sound** Tier 2, low and infrequent. **Quiet is the point**; a roaring Nundu is a lesser Nundu.
- **Reactions** None — it does not react, it decides.
- **Combat** `AMBUSHER`, `windupTicks` 20, `preferredRange` 0. `nundu_pestilence` is the identity — design: pestilence on the *approach*, bite as the finisher, so the player is already sick before contact.
- **Ecology** Apex solitary, East African **[canon]**. No social behaviour.
- **Player** Hostile. Should feel unsurvivable at low level.
- **Spells** `dread_aura` present. Protective magic should matter here more than damage.
- **Clips** E `idle`, `walk`, `bite`✓; R `prowl`, `hurt`, `death`, `howl`✓; O `breath`
- **Notes** The design brief for the whole roster: *lethal, quiet, slow*.

### Wampus Cat `wampus_cat` · HOSTILE · XXXXX · leap, status_on_hit, enrage, frenzy, damage_reduction
- **Identity** Great magical panther, six-legged in some tellings **[canon]**, hypnotic-eyed.
- **Visual** Panther build, unusually long limbs, yellow eyes. Consider the six-legged read as a **[design]** choice to separate it from Nundu — record it, do not assume it.
- **Movement** Fast, bounding, aggressive.
- **Idle** primary `groom` · secondary `stretch` · rare `listen` · 180/420
- **Sound** Tier 2 — big-cat snarl.
- **Reactions** `DAMAGE → ATTACK` (already true via retaliation; declare only if `frenzy` should trigger).
- **Combat** `HARRIER` — `leap` in, strike, reposition. Contrast with Nundu's ambush deliberately.
- **Ecology** Apex, solitary, North American.
- **Player** Hostile.
- **Spells** Legilimency-adjacent hypnotic gaze would be lore-right but needs a new mechanic — **open decision**, not implemented.
- **Clips** E `idle`, `walk`, `lunge`✓; R `run`, `hurt`, `death`, `hiss`✓
- **Notes** Must not feel like a reskinned Nundu: faster, louder, closer.

### Zouwu `zouwu` · NEUTRAL · XXX · leap, blink_away, enrage
- **Identity** Elephant-sized five-coloured cat of impossible speed **[canon]**.
- **Visual** Enormous cat with a vast plumed tail, five-colour banding. The tail is half the silhouette.
- **Movement** Explosive; covers ground in bursts. `blink_away` reads as teleport-speed.
- **Idle** primary `stretch` · secondary `shake` (tail) · rare `groom` · 200/500
- **Sound** Tier 2 — deep, resonant, more elephant than cat.
- **Reactions** `PLAYER → INVESTIGATE` r14 — curious, not hostile.
- **Combat** `HARRIER`, very fast approach; only fights when cornered.
- **Ecology** Solitary, Chinese **[canon]**; playful rather than predatory.
- **Player** Curious, bondable — a Zouwu is tameable in lore.
- **Spells** None.
- **Clips** E `idle`, `walk`, `lunge`✓; R `run`, `hurt`, `death`, `call`✓
- **Notes** The tail carries the identity — animate it independently of the body.

### Matagot `matagot` · NEUTRAL · XXX · damage_reduction, duplication
- **Identity** A cat-spirit that **splits into copies when struck** **[canon]**; haunts wizarding buildings.
- **Visual** Lean black cat, slightly too long, edges not quite solid. Eyes catch light without glowing.
- **Movement** Stalking, unhurried, appears where it was not.
- **Idle** primary `listen` · secondary `groom` · rare `settle` · 220/540
- **Sound** Tier 1 **silent** — a spirit-cat should make no noise. Its `hiss`✓ clip plays without sound deliberately.
- **Reactions** `PLAYER → BECOME_ALERT` r10 — it watches.
- **Combat** `SKIRMISHER`. `duplication` is the identity: design — it splits on taking damage, never by choice, so hitting it is the mistake.
- **Ecology** Guardian of magical buildings; spawn already gated on wizarding stonework.
- **Player** Neutral-territorial.
- **Spells** None.
- **Clips** E `idle`, `walk`, `strike`✓; R `hurt`, `death`; O `split`
- **Notes** Silence is the design. Do not add a cat meow.

### Kneazle `kneazle` · NEUTRAL · XXX · leap, status_on_hit, danger_sense
- **Identity** A cat that **judges you** — lion-like tail, oversized ears, unerring character sense **[canon]**.
- **Visual** Cat with flecked fur, outsized ears, plumed lion tail. The ears and tail are the read.
- **Movement** Precise, feline, deliberate pauses.
- **Idle** primary `groom` · secondary `listen` · rare `stretch` · 180/460
- **Sound** Tier 2 — vanilla cat, sparingly.
- **Reactions** `PLAYER → BECOME_ALERT` r10; `danger_sense` already models the judgement.
- **Combat** Defensive only; `BRUTE` default.
- **Ecology** Semi-domestic, solitary, bondable.
- **Player** **Bondable** — the roster's best small companion alongside the Crup.
- **Spells** None.
- **Clips** E `idle`, `walk`, `lunge`✓; R `hurt`, `death`, `hiss`✓; O `groom`
- **Notes** Its judgement of the player should eventually read off `MagicalStanding` — **open decision**.

### Crup `crup` · NEUTRAL · XXX · PACK · leap
- **Identity** A Jack Russell with a **forked tail**, devoted to wizards and murderous to Muggles **[canon]**.
- **Visual** Small terrier, unmistakable forked tail — the single diagnostic feature.
- **Movement** Bouncy, eager, quick direction changes.
- **Idle** primary `sniff` · secondary `shake` · rare `scratch` · 120/300 (busy)
- **Sound** Tier 2 — vanilla wolf/dog, pitched up. **Already declared** in the foundation demo.
- **Reactions** `PLAYER → INVESTIGATE` r12 (for wizards).
- **Combat** `PACK_HUNTER` — the `PACK` trait is already there.
- **Ecology** Pack, domestic, bondable.
- **Player** **Bondable**; hostile to non-magical players would be lore-right but the mod has no Muggle player state — **not implemented**.
- **Clips** E `idle`, `walk`, `bite`✓; R `run`, `hurt`, `death`, `call`✓; O `scratch`
- **Notes** Forked tail must survive at small pixel scale.

### Jarvey `jarvey` · NEUTRAL · XXX · THIEF · blink_away, jarvey_jinx
- **Identity** An oversized ferret that **swears at you** **[canon]**.
- **Visual** Long-bodied ferret, disproportionately large mouth. Brown, unremarkable — the comedy is in the behaviour.
- **Movement** Low, fast, weaving.
- **Idle** primary `sniff` · secondary `skitter` · rare `bark` · 120/280
- **Sound** Tier 3 bespoke — **short rude bursts**. This is the creature where sound *is* the identity and nothing vanilla works.
- **Reactions** `PLAYER → INVESTIGATE` r12 (then insults).
- **Combat** Avoids; `blink_away` is its exit.
- **Ecology** Burrowing pest, solitary.
- **Player** Neutral-comic.
- **Spells** None.
- **Clips** E `idle`, `walk`, `bite`✓; R `hurt`, `call`✓; O `bark`
- **Notes** Highest-value bespoke sound in the roster after the Fwooper.

### Salamander `salamander` · PASSIVE · XXX · FIRE_IMMUNE+FIRE_ATTACK · fire_affinity
- **Identity** A lizard **born of flame** that dies without it **[canon]**.
- **Visual** Small bright lizard, white-to-scarlet by flame temperature. **Emissive: yes** — this is one of the three earned cases.
- **Movement** Quick, skittering, drawn to fire.
- **Idle** primary `bask` · secondary `skitter` · rare `taste_air` · 160/400
- **Sound** Tier 2 — fire crackle rather than an animal noise.
- **Reactions** **`FIRE → INVESTIGATE`** r12 — the inverse of the roster default, and the whole point.
- **Combat** Non-combatant.
- **Ecology** Lives in fires; dies when its flame goes out **[canon]**.
- **Player** Neutral; harvestable.
- **Spells** **Incendio should attract it; Aguamenti should harm it.** One of the strongest spell interactions available.
- **Clips** E `idle`, `walk`; R `hurt`✓(`flinch`), `death`, `hiss`✓; O `bask`
- **Notes** `FIRE_IMMUNE` must suppress the default fire-flee. Model case for the reaction framework.

### Graphorn `graphorn` · HOSTILE · XXXX · CHARGE+KNOCKBACK · enrage, thorns, spell_resist, damage_reduction
- **Identity** Humpbacked, two-horned, **spell-resistant** brute **[canon]**.
- **Visual** Greyish-purple hump over the shoulders, two enormous forward horns, four-thumbed feet.
- **Movement** Heavy, committed. Turns badly — charging is its whole locomotion.
- **Idle** primary `rear` · secondary `sniff` · rare `grunt` · 280/640
- **Sound** Tier 2 — low bellow.
- **Reactions** None; territorial by default.
- **Combat** `BRUTE` with `windupTicks` 22 and a long `recoveryTicks` — a charge you can sidestep. `damage_reduction` + `spell_resist` already present.
- **Ecology** Near-extinct **[canon]**, solitary, mountainous.
- **Player** Territorial-hostile.
- **Spells** **`spell_resist` is the identity** — spells should visibly fizzle. Present already.
- **Clips** E `idle`, `walk`, `attack`✓; R `charge`, `hurt`, `death`, `groan`✓
- **Notes** The mod's clearest "magic is not the answer" creature.

### Erumpent `erumpent` · NEUTRAL · XXXX · CHARGE+KNOCKBACK+EXPLODE_ON_DEATH · explosive_horn
- **Identity** A rhino whose horn fluid makes **everything it pierces explode** **[canon]**.
- **Visual** Massive grey rhino, thick hide, one huge horn, long rope-like tail.
- **Movement** Ponderous until provoked, then straight-line and unstoppable.
- **Idle** primary `graze` · secondary `sniff` · rare `rear` · 300/700
- **Sound** Tier 2 — deep snorts.
- **Reactions** None declared; it charges what provokes it.
- **Combat** `BRUTE`, huge windup, no retreat. `explosive_horn` already exists.
- **Ecology** Solitary, African plains; not aggressive unprovoked **[canon]**.
- **Player** Neutral-dangerous.
- **Spells** None specific.
- **Clips** E `idle`, `walk`, `attack`✓; R `charge`, `hurt`, `death`, `groan`✓
- **Notes** Differentiate from Graphorn by *one* horn and a smoother back line.

### Reem `reem` · NEUTRAL · XXXX · CHARGE+KNOCKBACK · enrage, heal_aura
- **Identity** Giant ox whose **golden blood grants strength** **[canon]**.
- **Visual** Vast bovine, golden-brown, heavy dewlap.
- **Movement** Slow, herd-paced.
- **Idle** primary `graze` · secondary `shake` · rare `grunt` · 320/720
- **Sound** Tier 2 — vanilla cow, pitched far down.
- **Reactions** `CREATURE_DEATH → BECOME_ALERT` r18.
- **Combat** Defensive only.
- **Ecology** Herd, extremely rare **[canon]**.
- **Player** Neutral; the blood is the reason to find one.
- **Clips** E `idle`, `walk`, `attack`✓; R `hurt`, `death`, `groan`✓
- **Notes** Contrast with Erumpent: herd vs solitary, placid vs explosive.

### Qilin `qilin` · PASSIVE · X · FEARFUL+REGEN · heal_aura, blink_away
- **Identity** Gentle, pure, **sees into the soul** **[canon]**.
- **Visual** Deer-like with scales, single soft horn, fine limbs. Pale iridescence. **Emissive: subtle** — earned by lore.
- **Movement** Delicate, weightless, shy.
- **Idle** primary `listen` · secondary `graze` · rare `bow` · 240/600
- **Sound** Tier 1 near-silent — a single soft note at most.
- **Reactions** `PLAYER → FLEE` r20 unless the player is uncorrupted — mirrors the Unicorn rule, already available via `WildlifeRules`.
- **Combat** Never.
- **Ecology** Solitary, vanishingly rare.
- **Player** Avoidant; the ultimate purity test.
- **Spells** Dark magic repels; same channel as the Unicorn.
- **Clips** E `idle`, `walk`; R `hurt`✓, `call`✓; O `bow`
- **Notes** Deliberately parallel to the Unicorn — the pair should feel like two answers to one idea.

### Sphinx `sphinx` · NEUTRAL · XXXX · sphinx_riddle, dread_aura, heal_aura, status_on_hit
- **Identity** Lion body, **human head**, asks riddles, kills the wrong answer **[canon]**.
- **Visual** Lion body, human face and headdress. Face must read as *calm*, not monstrous.
- **Movement** Still. A Sphinx that paces is wrong — it waits.
- **Idle** primary `listen` · secondary `settle` · rare `stretch` · 400/900 (very still)
- **Sound** Tier 3 bespoke — speech-adjacent. `sphinx_riddle` already exists.
- **Reactions** `PLAYER → BECOME_ALERT` r12 — it notices and waits.
- **Combat** Only on a failed riddle. `AMBUSHER` with enormous windup.
- **Ecology** Solitary guardian; guards, never hunts.
- **Player** **Intelligent** — the only QUADRUPED that should be negotiated with.
- **Spells** None; the riddle is the interaction.
- **Clips** E `idle`, `strike`✓; R `walk`, `hurt`, `death`, `call`✓; O `speak`
- **Notes** Filed as QUADRUPED but behaviourally an intelligent Being — see Open Decisions.

### Centaur `centaur` · NEUTRAL · XXXX · PACK · ranged_hex
- **Identity** Half-human half-horse **stargazer**; proud, separate, armed with a bow **[canon]**.
- **Visual** Human torso on a horse body, seam at the waist. Bow and quiver are part of the silhouette.
- **Movement** Upright, dignified; never grazes like an animal.
- **Idle** primary `listen` · secondary `watch_sky` · rare `stretch` · 260/620
- **Sound** Tier 1 mostly silent, Tier 3 for speech. A centaur does not vocalise like a beast.
- **Reactions** `PLAYER → BECOME_ALERT` r20 — they see you long before you see them.
- **Combat** `SKIRMISHER`, `preferredRange` 10–14 — `ranged_hex` is the bow. Never closes willingly.
- **Ecology** Herd/tribe, territorial over forest, refuses human authority **[canon]**.
- **Player** **Intelligent**, not bondable, hostile to trespass.
- **Spells** None — they reject wand-magic **[canon]**.
- **Clips** E `idle`, `walk`, `attack`✓; R `run`, `hurt`, `death`, `call`✓; O `draw_bow`, `watch_sky`
- **Notes** Also a player heritage; keep the NPC and the player form visually consistent.

### Chimaera `chimaera` · HOSTILE · XXXXX · FIRE_BREATH+CHARGE · enrage, status_on_hit
- **Identity** Lion head, goat body, dragon tail — three creatures, one animal **[canon]**.
- **Visual** The three-part join must be blatant. Lion foreparts, goat barrel, scaled tail.
- **Movement** Disjointed — the parts should look like they disagree.
- **Idle** primary `taste_air` · secondary `shake` · rare `grunt` · 200/480
- **Sound** Tier 2 layered — a lion roar with a goat bleat under it. Unsettling by mismatch.
- **Reactions** None; hostile on sight.
- **Combat** `BRUTE` with fire breath; `FIRE_BREATH` trait already drives `BreatheFireGoal`.
- **Ecology** Apex solitary, Greek **[canon]**.
- **Player** Hostile.
- **Clips** E `idle`, `walk`, `bite`✓, `breath`; R `hurt`, `death`, `howl`✓
- **Notes** Do not smooth the joins. The wrongness is the design.

### Hodag `hodag` · NEUTRAL · XXXX · leap, enrage, life_leech
- **Identity** Horned frog-faced beast that **drinks dreams** **[design]**-leaning; American folklore.
- **Visual** Squat, horned, wide frog mouth, spined back.
- **Movement** Low, hopping-adjacent.
- **Idle** primary `sniff` · secondary `settle` · rare `taste_air` · 200/480
- **Sound** Tier 2 — croaking growl.
- **Reactions** `DARKNESS → BECOME_ALERT` r12 — it feeds on sleepers **[design]**.
- **Combat** `AMBUSHER`; `life_leech` present.
- **Ecology** Nocturnal, solitary.
- **Player** Neutral-dangerous at night.
- **Clips** E `idle`, `walk`, `bite`✓; R `hurt`, `death`, `groan`✓
- **Notes** Dream-drinking is a mod interpretation — mark it as such in the Bestiary.

### Dugbog `dugbog` · HOSTILE · XXX · AMPHIBIOUS · camouflage, leap, water_affinity, status_on_hit
- **Identity** Looks like a **dead log** until it moves **[canon]**.
- **Visual** Mottled brown, flat, log-proportioned; legs hidden until it lunges.
- **Movement** Motionless, then a single explosive lunge.
- **Idle** primary `settle` (stillness *is* the idle) · secondary `taste_air` · rare `skitter` · 400/900
- **Sound** Tier 1 silent while hidden; wet lunge sound only.
- **Reactions** `PLAYER → IGNORE` until in range — the ambush requires it.
- **Combat** `AMBUSHER`, `windupTicks` 5 (fast commit), long `recoveryTicks`. `camouflage` already present.
- **Ecology** Marsh ambusher, solitary.
- **Player** Hostile when stepped on.
- **Clips** E `idle`, `bite`✓; R `walk`, `hurt`, `death`, `hiss`✓; O `lunge`
- **Notes** The best ambush case in the roster; `camouflage` + long idle delay does most of the work.

### Knarl `knarl` · PASSIVE · XXX · FEARFUL · thorns
- **Identity** A hedgehog that **treats kindness as a trap** **[canon]** — the identity is entirely behavioural.
- **Visual** Hedgehog; visually indistinguishable from one on purpose **[canon]**.
- **Movement** Trundling, then bristling.
- **Idle** primary `sniff` · secondary `settle` · rare `bristle` · 160/400
- **Sound** Tier 2 — snuffling.
- **Reactions** **`PLAYER → BECOME_ALERT`** r6 — offering food should provoke, not tame. The signature interaction.
- **Combat** `thorns` only; never initiates.
- **Ecology** Solitary garden creature.
- **Player** Defensive; the anti-taming creature.
- **Clips** E `idle`, `walk`; R `hurt`✓, `call`✓; O `bristle`
- **Notes** Its whole design is that it *looks* like a mundane animal. Do not make it magical-looking.

### Moke `moke` · PASSIVE · XXX · FEARFUL · camouflage
- **Identity** Silver-green lizard that **shrinks at will** **[canon]**.
- **Visual** Small silver-green lizard, smooth.
- **Movement** Darting, then gone.
- **Idle** primary `taste_air` · secondary `skitter` · rare `settle` · 140/360
- **Sound** Tier 1 silent — a lizard this small makes no noise.
- **Reactions** `PLAYER → FLEE` r10 (shrink-flee).
- **Combat** None.
- **Ecology** Solitary, hides in rock.
- **Player** Avoidant; skin is the harvest **[canon]**.
- **Clips** E `idle`, `walk`; R `hurt`✓, `hiss`✓; O `shrink`
- **Notes** `camouflage` already present; shrinking could reuse `Attributes.SCALE` — **open decision**.

### Murtlap `murtlap` · NEUTRAL · XXX · AMPHIBIOUS · heal_aura, thorns
- **Identity** Rat with a **sea-anemone growth** on its back **[canon]**; the growth is the harvest.
- **Visual** Rat body, pink fleshy anemone crest. The crest is the entire read.
- **Movement** Scurrying, coastal.
- **Idle** primary `sniff` · secondary `groom` · rare `settle` · 160/400
- **Sound** Tier 2 — rodent squeaks.
- **Reactions** `PLAYER → FLEE` r8.
- **Combat** `thorns` only.
- **Ecology** Coastal, small groups.
- **Player** Neutral; murtlap essence is a real mod item.
- **Clips** E `idle`, `walk`, `bite`✓; R `hurt`✓, `death`
- **Notes** Crest should animate slightly independently — cheap, high identity value.

### Nogtail `nogtail` · NEUTRAL · XXX · blink_away, status_on_hit
- **Identity** A **demon piglet** that blights a farm from within **[canon]**; must be chased off by a pure white dog.
- **Visual** Runt piglet with long legs and a stub tail, black eyes.
- **Movement** Furtive, hides among livestock.
- **Idle** primary `sniff` · secondary `skitter` · rare `settle` · 160/400
- **Sound** Tier 2 — vanilla pig, wrong somehow (pitched low).
- **Reactions** `PLAYER → FLEE` r10.
- **Combat** Avoids entirely; `blink_away`.
- **Ecology** Parasitic on farms.
- **Player** Pest.
- **Clips** E `idle`, `walk`, `bite`✓; R `hurt`, `groan`✓
- **Notes** The white-dog counter is a strong lore hook — **open decision**, needs a Crup interaction.

### Tebo `tebo` · NEUTRAL · XXXX · FEARFUL · camouflage, enrage
- **Identity** Ash-grey warthog that **turns invisible** **[canon]**.
- **Visual** Warthog, ash-grey, tusked.
- **Movement** Nervous, then vanishes.
- **Idle** primary `sniff` · secondary `graze` · rare `shake` · 180/440
- **Sound** Tier 2 — vanilla hoglin/pig, low.
- **Reactions** `PLAYER → FLEE` r14 with `camouflage`.
- **Combat** Defensive only; `enrage` when cornered.
- **Ecology** Solitary, Congo/Zaire **[canon]**.
- **Player** Avoidant; hide is valuable.
- **Clips** E `idle`, `walk`, `attack`✓; R `hurt`, `death`, `groan`✓
- **Notes** Differentiate from Demiguise: Tebo's invisibility is *panic*, Demiguise's is *foresight*.

---

## Family: BIPED_HUMANOID (13)

### Demiguise `demiguise` · PASSIVE · XXXX · evasion, watched_invisibility, foresight, groomable, shed
- **Identity** Peaceful ape that is **invisible unless watched**, and sees the near future **[canon]**.
- **Visual** Compact ape body, arms ~1.8× leg length, long silver hair mantle. Rig already verified correct in the entity audit. Large soulful eyes. **No emissive.**
- **Movement** Slow, deliberate, never startled — it already knew.
- **Idle** primary `groom` · secondary `listen` · rare `peek` · 300/700 (calm)
- **Sound** Tier 1 **silent** — a creature that hides by prediction does not announce itself.
- **Reactions** `PLAYER → IGNORE` — its `watched_invisibility` already handles the player entirely.
- **Combat** Never fights.
- **Ecology** Solitary, deep jungle; the wild source of Demiguise hair.
- **Player** Avoidant-bondable via `groomable`/`shed` — hair is *given*, not taken.
- **Spells** Revelio-family should defeat its invisibility — strong lore rationale, **open decision**.
- **Clips** E `idle`, `walk`; R `hurt`✓, `death`, `call`✓; O `groom`, `climb`
- **Notes** Art is already good. Work is sound (none), idle, and the Revelio interaction.

### Werewolf `werewolf` · HOSTILE · XXXXX · PACK · moon_bound, lycanthropic_bite, frenzy, pack_tactics
- **Identity** A **cursed human**, not a wolf — the tragedy is the point **[canon]**.
- **Visual** Digitigrade, gaunt, short-snouted, tufted tail. Should read as *wrong wolf*, not big wolf.
- **Movement** Loping, unstable, four-limbed at speed and two at rest.
- **Idle** primary `listen` · secondary `shake` · rare `howl` · 140/340
- **Sound** Tier 2/3 — vanilla wolf is too clean; a bespoke ragged howl is warranted.
- **Reactions** `CREATURE_DEATH → BECOME_ALERT` r20 (pack).
- **Combat** `PACK_HUNTER`, fast approach, `frenzy` at low health. All abilities present.
- **Ecology** Only under the full moon — `moon_bound` and the spawn gate already enforce this.
- **Player** Hostile; `lycanthropic_bite` ties into the heritage condition system.
- **Spells** Already integrated with lycanthropy/Wolfsbane. Extend nothing.
- **Clips** E `idle`, `walk`, `run`, `attack`✓; R `hurt`✓, `death`, `howl`✓
- **Notes** Mechanically the most complete creature in the roster. Work is art and sound only.

### Rougarou `rougarou` · HOSTILE · XXXX · PACK · status_on_hit, camouflage
- **Identity** Dog-headed swamp stalker; the Werewolf's **regional cousin** **[canon]**.
- **Visual** Taller, leaner, more upright than the Werewolf; longer muzzle, swamp-matted fur.
- **Movement** Upright stalking rather than loping — the key separation from the Werewolf.
- **Idle** primary `listen` · secondary `sniff` · rare `howl` · 160/380
- **Sound** Tier 2 — lower, wetter than the Werewolf.
- **Reactions** `LIGHT → FLEE` r10 **[design]** — swamp-stalker avoids lanterns.
- **Combat** `AMBUSHER` — deliberately different from the Werewolf's pack rush.
- **Ecology** Solitary-ish despite `PACK`, Louisiana swamp.
- **Player** Hostile at night.
- **Clips** E `idle`, `walk`, `attack`✓; R `hurt`, `death`, `howl`✓
- **Notes** Must not be a Werewolf reskin — upright vs loping is the whole differentiation.

### Ghoul `ghoul` · NEUTRAL · XX · FEARFUL · (no abilities)
- **Identity** Slimy buck-toothed **nuisance** in the attic. Harmless, loud, pitiable **[canon]**.
- **Visual** Stooped, warty, buck teeth, drooping arms. Deliberately pathetic.
- **Movement** Shambling, clumsy, bumps into things.
- **Idle** primary `scratch` · secondary `peek` · rare `grunt` · 120/300 (busy and annoying)
- **Sound** Tier 2 — **banging and moaning**; the sound is its whole purpose in lore.
- **Reactions** `PLAYER → FLEE` r8 then return — it is startled, not dangerous.
- **Combat** None. One of the few HOSTILE-adjacent creatures that should never actually fight.
- **Ecology** Attic/cellar dweller, solitary, harmless.
- **Player** Neutral-comic.
- **Clips** E `idle`, `walk`; R `hurt`, `death`, `groan`✓; O `bang`
- **Notes** Zero abilities is correct. Do not add any.

### Gnome `gnome` · PASSIVE · XX · FEARFUL · blink_away
- **Identity** Leathery **potato-headed** garden pest that must be thrown out **[canon]**.
- **Visual** Small, knobbly, oversized bald head, tiny limbs. Comic proportions.
- **Movement** Waddling, fast when fleeing.
- **Idle** primary `dig` · secondary `sniff` · rare `peek` · 120/280
- **Sound** Tier 2 — high indignant squawks.
- **Reactions** `PLAYER → FLEE` r8 to burrow.
- **Combat** Bites when grabbed; otherwise none.
- **Ecology** Burrow colonies in gardens — **social**, should appear in small groups.
- **Player** Pest; de-gnoming is the interaction **[canon]**.
- **Clips** E `idle`, `walk`; R `hurt`✓, `call`✓; O `dig`, `thrown`
- **Notes** `dig` is identity-critical here; it is how gnomes enter and leave.

### Leprechaun `leprechaun` · NEUTRAL · XXX · THIEF · blink_away, bioluminescence
- **Identity** Green sprite that conjures **gold that vanishes** **[canon]**.
- **Visual** Small, green-clad, sharp-featured. Gold effects around the hands.
- **Movement** Quick, capering, never still.
- **Idle** primary `peek` · secondary `scratch` · rare `caper` · 100/260 (very busy)
- **Sound** Tier 2 — chattering laughter.
- **Reactions** `PLAYER → INVESTIGATE` r12 — it approaches to trick.
- **Combat** Never; `blink_away`.
- **Ecology** Social, Irish, tree-dwelling **[canon]**.
- **Player** Neutral-mischievous; ties to the existing currency system (vanishing gold).
- **Spells** None.
- **Clips** E `idle`, `walk`; R `hurt`✓, `call`✓; O `caper`, `conjure`
- **Notes** `bioluminescence` present — use it for the **gold**, not the body.

### Imp `imp` · NEUTRAL · XX · KNOCKBACK · status_on_hit, blink_away
- **Identity** Small riverbank **slapstick** prankster — trips people **[canon]**.
- **Visual** Tiny, dark, hairless, grinning.
- **Movement** Darting, low, comedic.
- **Idle** primary `peek` · secondary `skitter` · rare `caper` · 100/240
- **Sound** Tier 2 — giggling.
- **Reactions** `PLAYER → INVESTIGATE` r10.
- **Combat** Knockback prank only; never lethal.
- **Ecology** Riverbanks, small groups.
- **Player** Neutral-comic; differentiate from Leprechaun by *pure malice without gold*.
- **Clips** E `idle`, `walk`; R `hurt`✓, `call`✓; O `trip`
- **Notes** Imp/Leprechaun/Gnome are three comic smalls — separate by posture and by what they *want*.

### Erkling `erkling` · HOSTILE · XXXX · PACK · dread_aura, status_on_hit
- **Identity** Elfish creature that **lures children with song** **[canon]** — genuinely sinister.
- **Visual** Elfin, pointed features, three feet tall, unsettlingly cheerful face.
- **Movement** Light, dancing, beckoning.
- **Idle** primary `listen` · secondary `caper` · rare `sing` · 180/420
- **Sound** Tier 3 bespoke — **a luring song**. The identity is auditory; nothing vanilla works.
- **Reactions** `PLAYER → INVESTIGATE` r16 — it comes to you singing.
- **Combat** `PACK_HUNTER`; `dread_aura` present.
- **Ecology** German forests, packs **[canon]**.
- **Player** Hostile disguised as friendly — the best bait-and-switch in the roster.
- **Clips** E `idle`, `walk`, `attack`✓; R `hurt`, `death`, `call`✓; O `sing`, `beckon`
- **Notes** The song must play *before* hostility is apparent.

### Red Cap `red_cap` · HOSTILE · XXX · status_on_hit, enrage
- **Identity** Squat goblin-horror that lurks where **blood has been spilled** **[canon]**.
- **Visual** Dwarfish, wiry, oversized hands, cap stained dark red. The cap is the read.
- **Movement** Scuttling, aggressive, low.
- **Idle** primary `scratch` · secondary `peek` · rare `grunt` · 140/340
- **Sound** Tier 2 — guttural.
- **Reactions** `CREATURE_DEATH → INVESTIGATE` r16 — it is drawn to death. **The single most lore-apt reaction in the roster.**
- **Combat** `BRUTE`, fast, relentless.
- **Ecology** Battlefields, dungeons; solitary **[canon]**.
- **Player** Hostile on sight.
- **Clips** E `idle`, `walk`, `attack`✓; R `hurt`, `death`, `groan`✓
- **Notes** `CREATURE_DEATH → INVESTIGATE` should be the framework's first wired stimulus.

### Pukwudgie `pukwudgie` · HOSTILE · XXX · blink_away, heal_aura, ranged_hex
- **Identity** Grey-skinned **trickster** with poisoned arrows; proud, easily offended **[canon]**.
- **Visual** Two to three feet, large ears and nose, grey skin, bow.
- **Movement** Quick, evasive, keeps distance.
- **Idle** primary `listen` · secondary `peek` · rare `scratch` · 160/380
- **Sound** Tier 2 — sharp chattering.
- **Reactions** `PLAYER → BECOME_ALERT` r14.
- **Combat** `SKIRMISHER`, `preferredRange` 8–10 — `ranged_hex` is the bow. Never melees willingly.
- **Ecology** North American, solitary-to-small-group.
- **Player** Hostile-but-negotiable in lore; mod treats as hostile.
- **Clips** E `idle`, `walk`, `attack`✓; R `hurt`, `death`, `call`✓; O `draw_bow`
- **Notes** With the Centaur, one of two bow-users — share the draw animation.

### Clabbert `clabbert` · NEUTRAL · XX · bioluminescence, blink_away, danger_sense
- **Identity** Tree-dweller whose **forehead pustule glows to warn of danger** **[canon]**.
- **Visual** Smooth mottled green, webbed hands and feet, short horns, single large pustule on the brow.
- **Movement** Clinging and climbing, rarely on the ground.
- **Idle** primary `peek` · secondary `listen` · rare `scratch` · 180/440
- **Sound** Tier 1 near-silent.
- **Reactions** `PLAYER → BECOME_ALERT` r16 — and the pustule lights. `danger_sense` already models it.
- **Combat** None; `blink_away`.
- **Ecology** Arboreal, small groups, used as a burglar alarm by wizards **[canon]**.
- **Player** Neutral-useful.
- **Spells** None.
- **Clips** E `idle`, `walk`; R `hurt`✓, `call`✓; O `climb`, `alert`
- **Notes** **Emissive: the pustule only** — the model case for organ-level glowmasks.

### Porlock `porlock` · PASSIVE · XX · FEARFUL · blink_away, heal_aura
- **Identity** Shaggy hoof-footed **guardian of horses** **[canon]**; hides in straw.
- **Visual** Small, covered in rough hair, cloven hooves, large nose, hidden eyes.
- **Movement** Shy, keeps to cover, moves between hiding places.
- **Idle** primary `hide` · secondary `peek` · rare `groom` · 200/480
- **Sound** Tier 1 silent.
- **Reactions** `PLAYER → FLEE` r10.
- **Combat** Never.
- **Ecology** Lives with horses; **should follow equines** — a real social behaviour with in-mod targets (Abraxan, Granian, Aethonan).
- **Player** Avoidant.
- **Clips** E `idle`, `walk`; R `hurt`✓, `call`✓; O `hide`
- **Notes** The horse-guarding behaviour is the only genuinely inter-species relationship in the roster.

### Maledictus `maledictus` · NEUTRAL · XXX · POISON · constrict, enrage
- **Identity** A **woman cursed to become a beast, permanently** **[canon]**. Tragedy, not monster.
- **Visual** Human-serpent transitional form; the face should remain readable as human.
- **Movement** Sinuous, uncomfortable, caught between two gaits.
- **Idle** primary `listen` · secondary `coil` · rare `settle` · 260/620
- **Sound** Tier 1 near-silent, Tier 3 for a single human-adjacent sound.
- **Reactions** `PLAYER → IGNORE` — she is not hunting.
- **Combat** Defensive only, despite HOSTILE-adjacent abilities.
- **Ecology** Solitary, unique.
- **Player** **Intelligent** — should never feel like vermin.
- **Spells** Transformation magic is thematically central — **open decision**, no mechanic proposed.
- **Clips** E `idle`, `walk`, `strike`✓; R `hurt`, `death`, `hiss`✓; O `transform`
- **Notes** Handle with care: this is a cursed person, and the Bestiary text should say so.

---

## Family: BLOB_SPHERE (9)

### Puffskein `puffskein` · PASSIVE · XX · bioluminescence
- **Identity** A **contented ball of appetite** with a long tongue **[canon]**.
- **Visual** Cream custard-coloured sphere of long soft fur; no visible limbs or face until the tongue emerges.
- **Movement** Rolls and bounces; never walks.
- **Idle** primary `settle` · secondary `squirm` · rare `tongue` · 200/500
- **Sound** Tier 2 — a low contented hum. It already has a `song`✓ clip.
- **Reactions** `PLAYER → INVESTIGATE` r8 — it likes everyone.
- **Combat** None, ever.
- **Ecology** Domestic pet, social, harmless.
- **Player** **Bondable** — the friendliest creature in the roster.
- **Clips** E `idle`, `walk`(roll); R `hurt`✓, `song`✓; O `tongue`
- **Notes** `bioluminescence` is questionable here — Puffskeins do not glow in canon. **Open decision: consider removing.**

### Pygmy Puff `pygmy_puff` · PASSIVE · X · bioluminescence
- **Identity** A **bred-down, brightly dyed Puffskein** **[canon]**. Sharing the Puffskein's design is correct.
- **Visual** Identical to Puffskein but half the size and in pink/purple.
- **Movement** As Puffskein, faster and lighter.
- **Idle** As Puffskein, 160/400.
- **Sound** As Puffskein, pitched up.
- **Reactions** As Puffskein.
- **Combat** None.
- **Ecology** Pet only, sold at Weasleys' **[canon]**.
- **Player** Bondable.
- **Clips** Share the Puffskein rig entirely. E `idle`; R `song`✓, `hurt`✓
- **Notes** **The one legitimate case of colour-only differentiation in the roster**, because lore says so.

### Boggart `boggart` · NEUTRAL · XXX · boggart_dread
- **Identity** Becomes **whatever you fear** **[canon]**. It has no true form.
- **Visual** Base state should be deliberately **unresolved** — a dark shifting mass, never settled.
- **Movement** Uncertain until it takes a shape.
- **Idle** primary `squirm` · secondary `settle` · rare (none) · 240/600
- **Sound** **Borrowed** — a Boggart's sound should be the sound of whatever it has become, never its own. Silent in base form.
- **Reactions** `PLAYER → SPECIAL` r10 — the transformation is the reaction.
- **Combat** `boggart_dread` already exists; no melee rhythm.
- **Ecology** Dark enclosed spaces, solitary.
- **Player** Hostile-fearful; Riddikulus is the counter and already exists.
- **Spells** **Riddikulus** — already implemented via `boggart_banish`. Model spell interaction.
- **Clips** E `idle`; R `flinch`✓, `call`✓; O `shift`
- **Notes** Resist the urge to give it a face.

### Lethifold `lethifold` · HOSTILE · XXXXX · DEATH_GAZE · lethifold_smother
- **Identity** A **living black shroud** that smothers sleepers **[canon]**.
- **Visual** Flat, edge-rippling black sheet. **Not a sphere** despite the body plan — this needs its own silhouette handling.
- **Movement** **Gliding, low to the ground**, no limbs. Should not walk.
- **Idle** primary `drift` · secondary (none) · rare (none) · 400/900
- **Sound** Tier 1 **absolutely silent**. Its lore is that you never hear it coming. A sound would ruin it.
- **Reactions** `LIGHT → FLEE` r14 — strong lore rationale; it hunts in darkness.
- **Combat** `AMBUSHER`; `lethifold_smother` already present. Approaches only the stationary.
- **Ecology** Tropical, nocturnal, solitary **[canon]**.
- **Player** Lethal; Patronus is the only defence **[canon]**.
- **Spells** **Patronus should repel it** — the existing Patronus system already has `isPatronusDarkAligned`. Strong candidate, **open decision**.
- **Clips** E `idle`(drift), `attack`✓; R `flinch`✓; O `engulf`
- **Notes** Body plan is wrong for it; record as an open decision rather than changing the classification.

### Obscurus `obscurus` · HOSTILE · XXXXX · FLYING · dread_aura, ranged_hex, enrage, tint
- **Identity** A **parasitic dark force** born of repressed magic **[canon]** — not an animal at all.
- **Visual** Roiling black smoke with a hot interior. No fixed silhouette. **Emissive: yes**, earned — it is made of magic.
- **Movement** Flying, erratic, violent.
- **Idle** primary `drift` · secondary (none) · rare (none)
- **Sound** Tier 3 — a scream that is not quite a voice. Currently **no clips at all**.
- **Reactions** None — it is not a creature that notices.
- **Combat** `SKIRMISHER`; `ranged_hex` present.
- **Ecology** Not an ecology. Appears where a child's magic was suppressed.
- **Player** Lethal.
- **Spells** Deeply tied to the existing Obscurial heritage-condition system. **Do not extend.**
- **Clips** E `idle`; R `attack`; O `form`, `disperse` — **it has zero clips today**
- **Notes** Overlaps the bespoke `obscurus` player form. Keep the NPC and the form visually identical.

### Bundimun `bundimun` · PASSIVE · XXX · FEARFUL · block_decay
- **Identity** Creeping pest whose **secretions rot foundations** **[canon]**.
- **Visual** Green fungal mass on **many small eyes** and spindly legs. The eyes are the read.
- **Movement** Creeping, spreading, never fast.
- **Idle** primary `squirm` · secondary `settle` · rare (none) · 300/700
- **Sound** Tier 1 silent.
- **Reactions** `LIGHT → FLEE` r8 **[design]** — it lives under floorboards.
- **Combat** None; `block_decay` is passive damage to the world.
- **Ecology** Infests buildings; **colonial** — should appear in clusters.
- **Player** Pest; Scourgify is the canon counter **[canon]**.
- **Spells** **Scourgify should kill it** — strong, cheap, lore-exact. **Open decision.**
- **Clips** E `idle`; R `flinch`✓; O `spread`
- **Notes** Many eyes on a green mass — cheap and unmistakable.

### Pogrebin `pogrebin` · HOSTILE · XXX · dread_aura, camouflage
- **Identity** Looks like a **rock** until it moves; feeds on despair **[canon]**.
- **Visual** Grey hairy boulder with a disproportionately large head and stubby arms.
- **Movement** **Stillness, then following** — it trails the player and induces futility.
- **Idle** primary `settle` (stillness is the disguise) · secondary (none) · rare `squirm` · 500/1000
- **Sound** Tier 1 silent while disguised.
- **Reactions** `PLAYER → INVESTIGATE` r20 — it *follows*, which is the whole creature.
- **Combat** `AMBUSHER` after prolonged following; `dread_aura` present.
- **Ecology** Russian **[canon]**, solitary stalker.
- **Player** Hostile-creepy.
- **Clips** E `idle`, `walk`, `attack`✓; R `hurt`, `groan`✓; O `crouch`
- **Notes** The follow-then-strike loop is unusual enough to be worth the `INVESTIGATE` wiring.

### Lobalug `lobalug` · PASSIVE · XXX · AQUATIC · thorns
- **Identity** A **venom-sac stalk** of the North Sea, harmless unless provoked **[canon]**.
- **Visual** Simple stalk with a pulsing sac; jellyfish-adjacent.
- **Movement** Drifting with the current.
- **Idle** primary `drift` · secondary `squirm` · rare (none) · 300/700
- **Sound** Tier 1 silent — underwater and brainless.
- **Reactions** None.
- **Combat** `thorns` only.
- **Ecology** Seabed, harvested by merpeople as a weapon **[canon]**.
- **Player** Neutral; harvest target.
- **Clips** E `idle`; R `flinch`✓
- **Notes** Filed BLOB_SPHERE but locomotion AQUATIC — correct and consistent.

### Toad `toad` · PASSIVE · unrated · AMPHIBIOUS
- **Identity** **Deliberately mundane** — except that a Basilisk hatches under one **[canon]**.
- **Visual** An ordinary toad. Resist all embellishment.
- **Movement** Hopping.
- **Idle** primary `settle` · secondary `squirm` · rare `call` · 200/500
- **Sound** Tier 2 — vanilla frog.
- **Reactions** `PLAYER → FLEE` r6.
- **Combat** None.
- **Ecology** Ponds; familiar animal; the Basilisk breeding link is its real significance.
- **Player** Neutral; keepable as a familiar.
- **Clips** E `idle`, `walk`(hop); R `call`✓, `flinch`✓
- **Notes** Its mundanity is load-bearing. Do not make it magical.

---

## Family: AQUATIC (8)

### Merperson `merperson` · NEUTRAL · XXXX · water_affinity, ranged_hex
- **Identity** Underwater **people** — Sirens, selkies, grey merfolk **[canon]**. Beings, not beasts.
- **Visual** Humanoid torso, fish tail; grey-green skin and long dark-green hair for the lake variety, fairer for Sirens. Spear.
- **Movement** Graceful, strong-tailed, territorial patrol.
- **Idle** primary `drift` · secondary `listen` · rare `song` · 240/600
- **Sound** Tier 3 — **Mermish is harsh above water and melodious below** **[canon]**. The single best sound-design hook in the roster.
- **Reactions** `PLAYER → BECOME_ALERT` r16 — they watch trespassers.
- **Combat** `SKIRMISHER` with the spear; `ranged_hex` present. Defends territory, does not hunt.
- **Ecology** **Social tribes** with a chief and law **[canon]**; the most social creature here.
- **Player** **Intelligent**, negotiable; also a player heritage.
- **Spells** Bubble-Head/Gillyweed context already exists in the mod.
- **Clips** E `idle`, `swim`, `attack`✓; R `hurt`, `death`, `song`✓; O `spear_thrust`
- **Notes** Keep visually consistent with the merperson player form.

### Kelpie `kelpie` · NEUTRAL · XXXX · lure_disguise, constrict, water_affinity, status_on_hit
- **Identity** A water demon that **takes horse shape to drown riders** **[canon]**.
- **Visual** A horse made of water and reeds; mane should read as weed. Must look *almost* right.
- **Movement** Invitingly calm on the surface, violent once mounted.
- **Idle** primary `drift` · secondary `listen` · rare `shake` · 240/600
- **Sound** Tier 2 — a horse call that is subtly wrong (reverbed, watery). Already declared in the foundation demo.
- **Reactions** `PLAYER → INVESTIGATE` r16 — it *approaches invitingly*. The lure is the whole creature.
- **Combat** `AMBUSHER`; `lure_disguise` and `constrict` already present.
- **Ecology** Solitary, British lakes **[canon]**.
- **Player** Hostile-deceptive; Placement Charm on the bridle is the canon counter.
- **Clips** E `idle`, `swim`, `attack`✓; R `hurt`, `death`, `call`✓; O `lure`, `drag`
- **Notes** Design tension: it must be attractive before it is frightening.

### Grindylow `grindylow` · NEUTRAL · XX · AMPHIBIOUS · water_affinity, constrict
- **Identity** Horned water demon with **long brittle fingers** **[canon]** — the fingers are the read and the weakness.
- **Visual** Pale sickly green, small horns, disproportionately long fingers.
- **Movement** Grasping, scuttling on the bottom, not a strong swimmer.
- **Idle** primary `drift` · secondary `squirm` · rare `roll` · 180/440
- **Sound** Tier 2 — already declared (guardian sounds).
- **Reactions** `PLAYER → ATTACK` r8 — short-range aggression only.
- **Combat** `HARRIER`, `preferredRange` 2. `constrict` present; the grip breaks easily **[canon]**.
- **Ecology** Lake weed beds, small groups.
- **Player** Hostile-nuisance.
- **Clips** E `idle`, `swim`, `attack`✓; R `hurt`✓, `death`; O `grab`
- **Notes** Brittle fingers should be mechanically visible — the grip should break, not hold.

### Kappa `kappa` · HOSTILE · XXXX · AMPHIBIOUS+POISON · life_leech, water_affinity, status_on_hit
- **Identity** Monkey-like water demon with a **water-filled hollow in its head** **[canon]**; bowing makes it spill and weaken.
- **Visual** Scaly monkey body, turtle shell, webbed hands, a visible depression on the crown.
- **Movement** Crouched, amphibious, grabbing.
- **Idle** primary `settle` · secondary `groom` · rare `bow` · 200/480
- **Sound** Tier 2 — wet chittering.
- **Reactions** `PLAYER → ATTACK` r10.
- **Combat** `AMBUSHER` at water's edge; `life_leech` is the blood-drinking.
- **Ecology** Japanese rivers **[canon]**, solitary.
- **Player** Hostile; **bowing is the canon counter** — a superb interaction, **open decision**.
- **Clips** E `idle`, `swim`, `attack`✓; R `hurt`✓, `death`; O `bow`, `spill`
- **Notes** The bow-counter parallels the Hippogriff's bow — one shared `bow` clip serves both.

### Hippocampus `hippocampus` · PASSIVE · XXX · water_affinity
- **Identity** **Horse before, fish behind** **[canon]**. Gentle.
- **Visual** Horse head and forelegs, scaled fish rear and tail fin. Clean seam.
- **Movement** Graceful undulation; never walks.
- **Idle** primary `drift` · secondary `roll` · rare `call` · 260/620
- **Sound** Tier 2 — muted equine call.
- **Reactions** `PLAYER → FLEE` r12.
- **Combat** None.
- **Ecology** Mediterranean **[canon]**, small groups.
- **Player** Avoidant-bondable.
- **Clips** E `idle`, `swim`; R `hurt`, `call`✓
- **Notes** Contrast with Kelpie deliberately: the Hippocampus is genuinely gentle, the Kelpie only looks it.

### Plimpy `plimpy` · NEUTRAL · XXX · water_affinity, bioluminescence
- **Identity** Round spherical fish with **two long rubbery legs** **[canon]**; merpeople tie its legs in a knot.
- **Visual** Spherical, mottled, two absurdly long legs.
- **Movement** Awkward paddling; the legs trail.
- **Idle** primary `drift` · secondary `roll` · rare `squirm` · 200/480
- **Sound** Tier 1 silent.
- **Reactions** `PLAYER → FLEE` r8.
- **Combat** Nuisance nibbling only.
- **Ecology** Lake-bottom, groups.
- **Player** Pest.
- **Clips** E `idle`, `swim`; R `flinch`✓, `call`✓
- **Notes** The legs are the joke and the identity — animate them loosely.

### Ramora `ramora` · PASSIVE · XX · water_affinity, anchor
- **Identity** Silver fish that **anchors ships** **[canon]**; a guardian.
- **Visual** Plain silver fish; its power is invisible.
- **Movement** Slow, steady, station-keeping.
- **Idle** primary `drift` · secondary (none) · rare (none) · 300/700
- **Sound** Tier 1 silent.
- **Reactions** None.
- **Combat** None; `anchor` is its whole ability.
- **Ecology** Indian Ocean **[canon]**, guards mariners.
- **Player** Neutral-protective.
- **Clips** E `idle`, `swim`; R `flinch`✓
- **Notes** Minimal by design; do not elaborate.

### Shrake `shrake` · NEUTRAL · XXX · water_affinity, thorns
- **Identity** **Entirely covered in spines**; shreds fishing nets **[canon]**.
- **Visual** Fish whose silhouette is broken everywhere by spines.
- **Movement** Aggressive patrol.
- **Idle** primary `drift` · secondary `roll` · rare (none) · 220/520
- **Sound** Tier 1 silent.
- **Reactions** `PLAYER → ATTACK` r6 if approached.
- **Combat** `thorns`; defensive.
- **Ecology** Atlantic, shoals.
- **Player** Nuisance.
- **Clips** E `idle`, `swim`, `attack`✓; R `flinch`✓
- **Notes** Spine silhouette is the entire differentiation from Ramora.

---

## Family: ARTHROPOD_MULTILEG (6)

### Acromantula `acromantula` · HOSTILE · XXXXX · POISON+PACK · web_snare, leap, pack_tactics
- **Identity** Monstrous colony spider that **speaks** **[canon]**.
- **Visual** Eight legs, thick black hair, eight eyes, pincers. Leg span is the read.
- **Movement** Skittering bursts; pauses with legs raised.
- **Idle** primary `groom` · secondary `skitter` · rare `listen` · 160/400
- **Sound** Tier 2 — already declared (vanilla spider set). Tier 3 for speech later.
- **Reactions** `CREATURE_DEATH → BECOME_ALERT` r20 (colony).
- **Combat** **Already has a declared `AMBUSHER` profile** in the foundation demo — windup 10, recovery 20, retreat 0.2. `web_snare` + `leap` present.
- **Ecology** **Colonial** — the strongest group-behaviour case in the roster.
- **Player** Lethal; negotiable only if you are Hagrid **[canon]**.
- **Clips** E `idle`, `walk`, `bite`✓; R `hurt`, `death`, `hiss`✓; O `rear`, `web`
- **Notes** Reference implementation for the combat profile system.

### Quintaped `quintaped` · HOSTILE · XXXXX · POISON+CHARGE+KNOCKBACK · leap, enrage, pack_tactics, spell_resist
- **Identity** **Five** thick legs, blood-red hair, eats humans **[canon]**. The leg count is the identity.
- **Visual** Five heavy hairy legs, low body, no visible head.
- **Movement** Lurching, asymmetric — five legs cannot move symmetrically, and that should be visible.
- **Idle** primary `skitter` · secondary `settle` · rare `groom` · 180/440
- **Sound** Tier 2 — heavy scuttling.
- **Reactions** None; hostile.
- **Combat** `BRUTE` with `CHARGE`; `spell_resist` present.
- **Ecology** Isle of Drear only **[canon]**, packs.
- **Player** Lethal.
- **Clips** E `idle`, `walk`, `attack`✓; R `hurt`, `death`, `groan`✓
- **Notes** **The asymmetric five-leg gait is the single highest-value animation in this family.**

### Blast-Ended Skrewt `blast_ended_skrewt` · NEUTRAL · XXXXX · FIRE_ATTACK+EXPLODE · ranged_hex, thorns
- **Identity** Hagrid's **hybrid mistake** — propels itself with its own explosions **[canon]**.
- **Visual** Shell-less lobster-scorpion, pale and slimy, stinger and sucker, no obvious head. Deliberately ugly.
- **Movement** Jerky, propelled by blasts — it does not walk so much as detonate forward.
- **Idle** primary `skitter` · secondary `settle` · rare `blast` · 140/340
- **Sound** Tier 2 — wet clicking plus a bang.
- **Reactions** None.
- **Combat** `HARRIER` driven by its own recoil; `ranged_hex` is the blast.
- **Ecology** Does not belong in any ecology **[canon]** — that is the joke.
- **Player** Hostile-absurd.
- **Clips** E `idle`, `walk`, `strike`✓; R `hurt`, `death`, `hiss`✓; O `blast`
- **Notes** Blast-propelled locomotion is the identity; a normal walk cycle wastes it.

### Fire Crab `fire_crab` · NEUTRAL · XXX · FIRE_IMMUNE+FIRE_ATTACK · fire_affinity, flame_burst
- **Identity** A **jewelled tortoise-crab** that shoots flame from its rear **[canon]**.
- **Visual** Gem-encrusted shell — the jewels are the read and the reason it is hunted.
- **Movement** Slow, armoured, deliberate.
- **Idle** primary `settle` · secondary `skitter` · rare `bask` · 240/600
- **Sound** Tier 2 — shell scraping.
- **Reactions** **`FIRE → IGNORE`** (explicit, because it is `FIRE_IMMUNE`).
- **Combat** Defensive; `flame_burst` on being struck from behind.
- **Ecology** Fiji beaches **[canon]**, protected species.
- **Player** Neutral; shell is valuable.
- **Clips** E `idle`, `walk`, `attack`✓; R `hurt`, `death`, `hiss`✓; O `burst`
- **Notes** Jewels are a good earned-emissive case — faint, not glowing.

### Mackled Malaclaw `mackled_malaclaw` · NEUTRAL · XXX · status_on_hit
- **Identity** Land lobster whose bite **causes bad luck** **[canon]**.
- **Visual** Greenish-grey with dark spots, lobster form, spiny.
- **Movement** Sidling, defensive.
- **Idle** primary `skitter` · secondary `groom` · rare `settle` · 200/480
- **Sound** Tier 1 near-silent — clicking only.
- **Reactions** `PLAYER → FLEE` r8.
- **Combat** Defensive bite; `status_on_hit` is the bad luck.
- **Ecology** Rocky European coasts **[canon]**.
- **Player** Neutral; the bite is a real consequence (anti-Felix Felicis).
- **Clips** E `idle`, `walk`, `bite`✓; R `flinch`✓
- **Notes** The bad-luck effect should read as a *curse*, not damage.

### Chizpurfle `chizpurfle` · NEUTRAL · XX · PACK · life_leech, status_on_hit
- **Identity** Tiny fanged parasite that **gnaws magic itself** — attacks wands **[canon]**.
- **Visual** Crab-like, under a millimetre in lore; here the smallest creature in the roster. Long fangs.
- **Movement** Swarming, fast, in numbers.
- **Idle** primary `skitter` · secondary `groom` · rare (none) · 120/280
- **Sound** Tier 1 silent individually; a swarm hum if grouped.
- **Reactions** None.
- **Combat** Swarm nuisance; `life_leech` present.
- **Ecology** **Infests magical items and creatures** — parasitic, colonial.
- **Player** Pest; should threaten *equipment*, not health.
- **Spells** **Wand-gnawing is a genuine hook into the existing wand integrity system** — strong rationale, **open decision**.
- **Clips** E `idle`, `walk`; R `bite`✓, `flinch`✓
- **Notes** The only creature whose threat is to items rather than the player.

---

## Family: SERPENTINE (5)

### Basilisk `basilisk` · HOSTILE · XXXXX · POISON+PETRIFY+DEATH_GAZE+LETHAL_GAZE+COCKCROW_WEAKNESS · constrict · **scale 1.4**
- **Identity** The **King of Serpents** — direct gaze kills, reflected gaze petrifies, a rooster's crow is fatal to it **[canon]**.
- **Visual** Vast green serpent, yellow eyes, crest on the male, fangs. **The eyes must be the focal point and must NOT be emissive** — a glow would soften the horror. Scale texture density should be highest at the head.
- **Movement** **Slow, immense, continuous.** A Basilisk never darts. Body drags; head leads at player height.
- **Idle** primary `coil` · secondary `taste_air` · rare `settle` · 400/900 (very still — it waits)
- **Sound** Tier 2/3 — a dry scrape of scale on stone plus a deep hiss. **No roar.** It already has `hiss`✓.
- **Reactions** **`LIGHT → BECOME_ALERT`** r20 **[design]**; a rooster crow stimulus would be canon-exact but needs an event — **open decision**.
- **Combat** `AMBUSHER`, very high `windupTicks` (~25), long `recoveryTicks`, `preferredRange` 0. The gaze traits already do the lethal work; the melee is the follow-up, not the threat.
- **Ecology** Apex, solitary, subterranean. Hatched from a chicken egg under a toad **[canon]** — links to the Toad entry.
- **Player** Lethal. Mirrors, spiders and roosters are the canon counters.
- **Spells** Gaze traits already implemented. **Do not add spell counters** — the counters are physical (mirror, rooster), which is more interesting.
- **Clips** E `idle`, `walk`(slither), `strike`✓; R `hurt`, `death`, `hiss`✓, `gaze`✓; O `rear`, `coil`
- **Notes** **Scale 1.4 is a documented design decision, not canon.** Rig is 8.5 blocks at 1.0 → ~12 at 1.4. The square hitbox cannot represent a serpent at any scale; a multi-part entity is the only true fix and is out of scope. **Collision at 3.78 × 4.06 is untested in world.**

### Occamy `occamy` · HOSTILE · XXXX · CHARGE+KNOCKBACK · occamy_choranaptyxis, constrict, enrage, tint
- **Identity** **Plumed serpent-bird** that grows or shrinks to fill available space **[canon]**.
- **Visual** Feathered serpentine body, two wings, beaked head, blue-silver plumage.
- **Movement** Sinuous flight and coiling; fiercely protective of eggs.
- **Idle** primary `coil` · secondary `preen` · rare `fan_wings` · 200/480
- **Sound** Tier 2 — a bird-snake hiss-screech hybrid.
- **Reactions** `PLAYER → ATTACK` r10 when near its nest **[canon]** — it is aggressive only in defence.
- **Combat** `HARRIER`; `constrict` present.
- **Ecology** India/Far East **[canon]**, nests, silver eggshells are the harvest.
- **Player** Hostile-defensive.
- **Clips** E `idle`, `fly`, `attack`; R `hurt`, `death`, `hiss`; O `grow`, `shrink` — **it has zero clips today**
- **Notes** `occamy_choranaptyxis` already drives `Attributes.SCALE`; verified working in the entity audit. Its **zero declared clips** are the gap.

### Horned Serpent `horned_serpent` · NEUTRAL · XXXXX · AQUATIC · bioluminescence
- **Identity** River serpent with a **luminous jewel in its brow** **[canon]**; a Horned Serpent horn is an Ilvermorny wand core.
- **Visual** Long river serpent, single brow horn, jewel. **Emissive: the jewel only** — earned.
- **Movement** Slow aquatic undulation.
- **Idle** primary `drift` · secondary `taste_air` · rare `coil` · 280/660
- **Sound** Tier 1 near-silent; a low hiss at most. Already has `hiss`✓.
- **Reactions** `PLAYER → BECOME_ALERT` r16 — it is intelligent and wary.
- **Combat** Defensive only despite XXXXX rating.
- **Ecology** North American rivers **[canon]**, solitary.
- **Player** Neutral-intelligent; the horn is a wand core in the mod's wandlore.
- **Clips** E `idle`, `swim`; R `bite`✓, `hurt`, `hiss`✓
- **Notes** Locomotion is AQUATIC — correct. Second organ-level glowmask case after the Clabbert.

### Sea Serpent `sea_serpent` · NEUTRAL · XXX · AQUATIC+AMPHIBIOUS · water_affinity, constrict
- **Identity** Vast **horse-headed** serpent of the deep **[canon]**; feared but harmless.
- **Visual** Horse-like head, humped body that breaks the surface in arcs.
- **Movement** Undulating, the humps are the read.
- **Idle** primary `drift` · secondary `coil` · rare (none) · 300/700
- **Sound** Tier 1 near-silent.
- **Reactions** None — it ignores people **[canon]**.
- **Combat** Never initiates.
- **Ecology** Atlantic/Mediterranean/Pacific **[canon]**, solitary.
- **Player** Neutral; frightening and harmless, which is the design.
- **Clips** E `idle`, `swim`; R `bite`✓, `hiss`✓
- **Notes** Should be *impressive* and never a threat — an unusual and valuable role.

### Ashwinder `ashwinder` · NEUTRAL · XXX · FIRE_IMMUNE+FIRE_ATTACK · ember_trail, fire_affinity, tint
- **Identity** A **thin grey serpent born of a dying magical fire**, lives only an hour, lays burning eggs **[canon]**.
- **Visual** Pale grey-ash, thin, with glowing red eyes and a trailing ember. **Emissive: yes** — it is made of fire.
- **Movement** Quick, slithering away to hide and lay.
- **Idle** primary `taste_air` · secondary `coil` · rare (none) · 140/340 (it has no time)
- **Sound** Tier 2 — fire crackle, like the Salamander.
- **Reactions** **`FIRE → INVESTIGATE`** — born of it; and `WATER` should be lethal.
- **Combat** Avoids; the eggs are the threat, not the snake.
- **Ecology** **Temporary** — exists only briefly after a fire. Unique lifecycle in the roster.
- **Player** Neutral-urgent: you must find the eggs before the house burns **[canon]**.
- **Spells** **Aguamenti/Frigora should destroy it; the eggs need freezing** — one of the best spell hooks available, and the mod already has `frigora`.
- **Clips** E `idle`, `walk`(slither); R `strike`✓, `hiss`✓, `death`; O `lay_egg`
- **Notes** `ember_trail` already exists. The hour-long lifespan is a real design opportunity.

---

## Family: INSECTOID_FLYER (5)

### Fairy `fairy` · PASSIVE · XX · FEARFUL · bioluminescence, tint
- **Identity** Vain, **faintly glowing** winged sprite of human shape **[canon]**. Decorative and stupid.
- **Visual** Tiny humanoid, insect wings, whole body faintly luminous. **`glow_self: true` and a full-body glowmask — already implemented and verified correct. Do not change it.**
- **Movement** Flitting, preening, showing off.
- **Idle** primary `preen` · secondary `flit` · rare `pose` · 140/340
- **Sound** Tier 1 near-silent — a high buzz at most.
- **Reactions** `PLAYER → FLEE` r8.
- **Combat** None.
- **Ecology** Woodland, groups; used as decoration by wizards **[canon]**.
- **Player** Neutral; ornamental.
- **Clips** E `idle`, `fly`; R `flinch`✓, `song`✓; O `preen`
- **Notes** **This creature is finished.** The audit wrongly flagged its glowmask as a bug; it is documented intent in `fairy_model.py`.

### Billywig `billywig` · NEUTRAL · XXX · status_on_hit, bioluminescence
- **Identity** Sapphire-blue Australian insect whose sting causes **giddiness and levitation** **[canon]**; its wings spin on top of its head.
- **Visual** Vivid sapphire, rotating wings mounted **on the crown**, long sting beneath.
- **Movement** **Spinning flight** — fast, erratic, hard to follow. The spin is the identity.
- **Idle** primary `hover` · secondary `flit` · rare (none) · 120/280
- **Sound** Tier 2 — a high rotor buzz.
- **Reactions** `PLAYER → FLEE` r8.
- **Combat** Stings only when caught; `status_on_hit` is the levitation.
- **Ecology** Australia **[canon]**, small swarms.
- **Player** Neutral; sting is an ingredient.
- **Clips** E `idle`, `fly`(spin); R `flinch`✓, `call`✓
- **Notes** The rotating-wing animation is the whole creature — worth doing properly.

### Doxy `doxy` · NEUTRAL · XXX · POISON · blink_away, evasion
- **Identity** The **Biting Fairy** — black, four-limbed, double-fanged, infests curtains **[canon]**.
- **Visual** Dark, hairy, **four arms and four legs** (the differentiator from the Fairy), fine iridescent wings, visible fangs.
- **Movement** Darting, swarming, aggressive.
- **Idle** primary `flit` · secondary `groom` · rare (none) · 100/240
- **Sound** Tier 2 — angry buzzing.
- **Reactions** `PLAYER → ATTACK` r6 — it bites first.
- **Combat** `HARRIER` in swarms.
- **Ecology** **Infests houses in nests** — colonial. Doxycide is the counter **[canon]**.
- **Player** Pest-hostile.
- **Clips** E `idle`, `fly`; R `bite`✓, `flinch`✓
- **Notes** Fairy vs Doxy is the cleanest "same plan, opposite creature" pair: light/dark, vain/vicious, two limbs/eight.

### Glumbumble `glumbumble` · PASSIVE · XXX · dread_aura, bioluminescence, spore_cloud
- **Identity** Grey furry flyer producing **melancholy-inducing nectar** **[canon]**.
- **Visual** Fat, grey, densely furred, small wings for its body.
- **Movement** Slow, droning, heavy for a flyer.
- **Idle** primary `hover` · secondary `groom` · rare (none) · 200/480
- **Sound** Tier 2 — a low mournful drone. Its `groan`✓ clip suits it exactly.
- **Reactions** None.
- **Combat** None; `dread_aura` is passive melancholy.
- **Ecology** Nests in dark places, Northern Europe **[canon]**.
- **Player** Neutral; treacle is the antidote **[canon]**.
- **Clips** E `idle`, `fly`; R `flinch`✓, `groan`✓
- **Notes** `bioluminescence` is questionable — Glumbumbles do not glow. **Open decision: consider removing.**

### Swooping Evil `swooping_evil` · HOSTILE · XXXX · FIRE_IMMUNE · leap, life_leech, status_on_hit, evasion
- **Identity** A winged predator that **folds into a spiked cocoon**; its venom erases bad memories **[canon]**.
- **Visual** Blue-green butterfly-reptile, spiked shell when folded — **two distinct silhouettes**.
- **Movement** Folded rest, explosive unfold, swooping dives.
- **Idle** primary `settle` (folded) · secondary `hover` · rare `unfold` · 220/520
- **Sound** Tier 2 — a snapping unfurl plus wing rush.
- **Reactions** None.
- **Combat** `HARRIER` — dives, `life_leech`, withdraws. `evasion` present.
- **Ecology** Solitary predator.
- **Player** Hostile; also usable as a weapon **[canon]**.
- **Clips** E `idle`(folded), `fly`, `lunge`✓; R `hurt`, `death`, `hiss`✓; O `unfold`, `fold`
- **Notes** **The fold/unfold pair is essential, not optional** — without it this is a generic flyer.

---

## Family: AVIAN (5)

### Thunderbird `thunderbird` · NEUTRAL · XXXX · thunderbird_storm, dive_bomb, enrage, danger_sense
- **Identity** Giant **multi-winged** bird that creates storms as it flies and senses danger **[canon]**.
- **Visual** Eagle-like, **three pairs of wings**, gold-and-brown iridescent plumage. The wing count is the read.
- **Movement** Majestic, storm-wreathed, high altitude.
- **Idle** primary `preen` · secondary `fan_wings` · rare `call` · 260/620
- **Sound** Tier 3 — **thunder is its voice**. Bespoke, and the strongest audio identity in the roster.
- **Reactions** `PLAYER → BECOME_ALERT` r24 — `danger_sense` already models the far-sensing.
- **Combat** `SKIRMISHER`; `dive_bomb` and `thunderbird_storm` present. Storm at range, dive to finish.
- **Ecology** Arizona **[canon]**, solitary, apex flyer.
- **Player** Neutral-awed; bondable in lore (Frank).
- **Spells** None.
- **Clips** E `idle`, `fly`, `strike`✓; R `hurt`, `death`, `call`✓; O `storm`, `dive`
- **Notes** Three wing-pairs must be animated in phase offsets, or it reads as a mess.

### Fwooper `fwooper` · NEUTRAL · XXX · fwooper_song, bioluminescence
- **Identity** Gaudy bird whose **song drives listeners mad** **[canon]**; sold with a Silencing Charm.
- **Visual** Brilliant orange/pink/lime/yellow plumage — **the one creature where colour IS the identity** and it should be loud.
- **Movement** Hopping, strutting, showy.
- **Idle** primary `preen` · secondary `ruffle` · rare `song` · 160/400
- **Sound** Tier 3 bespoke — **the song is the creature**. Must become unpleasant with exposure.
- **Reactions** None.
- **Combat** None.
- **Ecology** African **[canon]**, solitary.
- **Player** Neutral-dangerous-to-sanity; `fwooper_song` already exists.
- **Spells** **Silencio should suppress the song** — exact canon, strong hook, **open decision**.
- **Clips** E `idle`, `fly`; R `flinch`✓, `song`✓; O `strut`
- **Notes** `bioluminescence` is wrong here — Fwoopers are bright, not glowing. **Open decision: consider removing.**

### Jobberknoll `jobberknoll` · PASSIVE · XX · FEARFUL · death_cry, bioluminescence
- **Identity** **Silent its entire life**, then screams every sound it ever heard, backwards, as it dies **[canon]**.
- **Visual** Small, blue, speckled. Unremarkable by design.
- **Movement** Quick, nervous, small-bird flitting.
- **Idle** primary `flit` · secondary `preen` · rare (none) · 140/340
- **Sound** **Tier 1 for ambient — explicitly, absolutely silent.** Tier 3 for **death only**: a long backwards scream. `death_cry` already exists.
- **Reactions** `PLAYER → FLEE` r10.
- **Combat** Never.
- **Ecology** Northern Europe **[canon]**, solitary; feathers used in Truth Serums.
- **Player** Avoidant; killing one is memorable and should feel bad.
- **Clips** E `idle`, `fly`; R `flinch`✓; **O `death` ← identity-critical**
- **Notes** **The roster's purest sound-design case**: one creature, one sound, at one moment. `bioluminescence` is wrong — **open decision: consider removing.**

### Golden Snidget `golden_snidget` · PASSIVE · XXXX · FEARFUL · evasion
- **Identity** Tiny, spherical, **fully rotational wings**, ruby eyes, legendary speed — the Snitch's origin **[canon]**. Protected species.
- **Visual** Round golden body, thin beak, jewel-red eyes, blurred wings.
- **Movement** **Erratic, instantaneous direction changes.** Speed is the entire identity.
- **Idle** primary `hover` · secondary `flit` · rare (none) · 100/220 (never still)
- **Sound** Tier 1 near-silent — a faint whir.
- **Reactions** `PLAYER → FLEE` r20 — flees from very far.
- **Combat** Never.
- **Ecology** Protected, near-extinct **[canon]**. Spawn already narrowed to flower forest/meadow at weight 1.
- **Player** Avoidant; catching one should be an achievement, not a kill.
- **Spells** None — a spell-caught Snidget betrays the point.
- **Clips** E `idle`, `fly`; R `flinch`✓, `song`✓
- **Notes** **Do not turn it into a generic flying mob.** Its rarity is now ecological; its speed must be in the flight code, not a stat.

### Diricawl `diricawl` · PASSIVE · XX · FEARFUL · blink_away
- **Identity** The **Dodo** — flightless, plump, and it **vanishes** rather than fleeing **[canon]**.
- **Visual** Plump flightless bird, small wings, heavy beak. Comic.
- **Movement** Waddling, then gone.
- **Idle** primary `preen` · secondary `sniff` · rare (none) · 180/440
- **Sound** Tier 2 — low clucking.
- **Reactions** `PLAYER → FLEE` r10 — but as a **teleport**, via `blink_away`.
- **Combat** Never.
- **Ecology** Mauritius **[canon]**; Muggles think it extinct, which is the joke.
- **Player** Neutral-comic.
- **Clips** E `idle`, `walk`; R `flinch`✓, `call`✓; O `vanish`
- **Notes** Locomotion is GROUND despite AVIAN — **correct**, it is flightless. Do not "fix" it.

---

## Family: LARGE_HUMANOID (3)

### Giant `giant` · HOSTILE · XXXX · KNOCKBACK+PACK · enrage, ranged_hex, damage_reduction, spell_resist
- **Identity** A **person too big** — warlike, tribal, nearly extinct **[canon]**.
- **Visual** Human proportions scaled up, crude clothing, boulder. Head sits on the shoulders normally.
- **Movement** Slow, heavy, ground-shaking.
- **Idle** primary `grunt` · secondary `stretch` · rare `scratch` · 320/720
- **Sound** Tier 2 — deep grunting speech-adjacent.
- **Reactions** `CREATURE_DEATH → BECOME_ALERT` r24 (tribal).
- **Combat** `BRUTE`, huge windup, `ranged_hex` is the thrown boulder. `spell_resist` present **[canon]** — magic works poorly on giants.
- **Ecology** Tribal, mountainous, dwindling **[canon]**.
- **Player** Hostile; also a player heritage.
- **Clips** E `idle`, `walk`, `attack`✓; R `hurt`, `death`, `groan`✓; O `throw`
- **Notes** Keep consistent with the giant player heritage form.

### Troll `troll` · HOSTILE · XXXX · KNOCKBACK+CHARGE · enrage, damage_reduction, spell_resist
- **Identity** **Dim-witted**, immensely strong, foul-smelling **[canon]**.
- **Visual** Top-heavy, tiny head sunk between huge shoulders, long arms, club. Head-to-body ratio is the differentiator from the Giant.
- **Movement** Lumbering, unbalanced, telegraphed.
- **Idle** primary `grunt` · secondary `scratch` · rare `settle` · 300/700
- **Sound** Tier 2 — grunts and snorts, no speech.
- **Reactions** None — too stupid to notice.
- **Combat** `BRUTE`, the **longest windup in the roster** and a long recovery. A troll swing should be dodgeable by anyone paying attention.
- **Ecology** Mountain/forest/river varieties **[canon]**, solitary or small bands.
- **Player** Hostile-but-beatable; the classic first boss.
- **Clips** E `idle`, `walk`, `attack`✓; R `hurt`, `death`, `groan`✓; O `club_raise`
- **Notes** The windup/recovery pair is the design. Everything else is secondary.

### Yeti `yeti` · HOSTILE · XXXX · KNOCKBACK · spore_cloud, camouflage
- **Identity** The **Abominable Snowman** — white, cold-adapted, terrified of fire **[canon]**.
- **Visual** Tall, shaggy white, head sunk deepest of the three, long arms.
- **Movement** Heavy but faster than the troll; at home in snow.
- **Idle** primary `stretch` · secondary `shake` (snow off) · rare `grunt` · 280/660
- **Sound** Tier 2 — howling, wind-adjacent. Already has `howl`✓.
- **Reactions** **`FIRE → FLEE`** r14 — exact canon, and the single clearest reaction case in the roster.
- **Combat** `BRUTE`; `camouflage` in snow.
- **Ecology** Tibet **[canon]**, solitary, alpine.
- **Player** Hostile; **fire is the counter** — Incendio should genuinely rout it.
- **Spells** **Incendio/fire-based spells cause flight, not damage.** Best spell-reaction candidate in the roster after the Salamander.
- **Clips** E `idle`, `walk`, `attack`✓; R `hurt`, `death`, `howl`✓
- **Notes** `FIRE → FLEE` here and `FIRE → INVESTIGATE` on the Salamander are the two reactions that should be wired first.

---

## Family: WORM_LARVA (1) · SESSILE (1)

### Flobberworm `flobberworm` · PASSIVE · X
- **Identity** A brown worm that **does nothing** **[canon]**. Its dullness is documented and deliberate.
- **Visual** Ten inches of thick brown tube. No face, no limbs.
- **Movement** Barely moves.
- **Idle** **None.** `IdleProfile` has no `WORM_LARVA` default and should not gain one.
- **Sound** **Tier 1 silent.** Obviously.
- **Reactions** None.
- **Combat** None.
- **Ecology** Damp ditches; eats lettuce; produces mucus **[canon]**. Harvestable set-dressing.
- **Player** Neutral; a Care of Magical Creatures joke.
- **Clips** E `idle`; R `flinch`✓
- **Notes** **Do not improve this creature.** Its emptiness is the characterisation.

### Horklump `horklump` · PASSIVE · X · SESSILE
- **Identity** A pink bristly **fungus-beast rooted in the earth** **[canon]**.
- **Visual** Pink fleshy mushroom with black bristles; sinewy roots below.
- **Movement** **None.** It is rooted — the only SESSILE creature.
- **Idle** **None**, correctly. No default exists for SESSILE.
- **Sound** **Tier 1 silent.**
- **Reactions** None.
- **Combat** None.
- **Ecology** Spreads underground, feeds on earthworms **[canon]**; gnome food.
- **Player** Harvest target only.
- **Clips** E `idle`; R `flinch`✓
- **Notes** Verify at implementation time that SESSILE does not receive a stroll goal — it maps through the ground entity class and should not wander.

---

# Bespoke Entities

**The 24 bespoke entities must not be forced into `CreatureDefinition`.** They have no definition
file, so **no profile in this document reaches them**. Each is assessed for what it actually needs.

Integrating them would mean either giving them definition files (losing their bespoke logic) or
lifting the profile accessors onto a shared interface (a real architectural change). **That is
recorded as an open design decision, not implemented.**

## Phoenix `phoenix` — `PhoenixEntity extends GeoEntityBase`
- **Identity** Swan-sized scarlet bird with a gold tail, gentle, immortal by rebirth, tears heal, can carry immense weights **[canon]**.
- **Current state** Bespoke class, `BondableBeast`, 5 goals (`AvoidEntityGoal`, `FollowBondedOwnerGoal`, `LookAtPlayerGoal`, `RandomLookAroundGoal`, `WaterAvoidingRandomFlyingGoal`). **Rig declares only `idle` and `fly`.** Rebirth is implemented.
- **Needs** **Animation** (primary): `preen`, `spread_wings`, `settle`, `rebirth`. **Behaviour**: the audit was right that its idle is generic — but the fix is *not* the new `IdleProfile`, which cannot reach it. It needs either bespoke idle goals (as Mooncalf and Thestral have) or the interface lift. **Sound**: a single pure note; Tier 3, high value. **Art**: existing rig is adequate.
- **Design** Calm and majestic. **Do not over-animate.** A Phoenix at rest should be almost still, with slow wing-settling and occasional preening. Its power is implied, never performed.
- **Integration** Would benefit most of the 24 from the profile system. **Open decision.**

## Bowtruckle `bowtruckle` — bespoke, 10 goals
- **Identity** Tiny stick-insect tree guardian; hand-shaped, lock-picking fingers, fiercely protective of its wand-wood tree **[canon]**.
- **Current state** The **richest bespoke AI** in the roster: `TemptGoal`, `MoveTowardsRestrictionGoal` (its tree), panic, melee, target selection. Verified.
- **Needs** **Behaviour**: climbing, `hide`, `peek`. The audit flagged climbing; on inspection **no climbing mechanism exists anywhere in the mod**, and **only the Bowtruckle needs it**. Per the brief's own rule, that does **not** justify a shared climbing system.
- **Design** If climbing is built, build it *for the Bowtruckle only*, as a bespoke goal that moves it up a tree's Y axis and anchors it. Alternatively `MoveTowardsRestrictionGoal` plus a high anchor point approximates tree-dwelling for far less work — **recommended first attempt**.
- **Clips needed** `climb`, `hide`, `peek`.

## Dementor `dementor` — `DementorEntity extends Monster`
- **Identity** Soulless hooded thing that drains happiness; blind, senses emotion **[canon]**.
- **Current state** **Already correct.** `FlyingMoveControl(10, true)`, `BeastNavigation.flying`, no `ON_GROUND` placement, 1024 HP, 0 attack damage, LOS-free emotion sensing, Patronus masking, crop withering, client-side memory voices, kiss goal with windup/resolve clips.
- **Needs** **Nothing structural.** The audit's claim was wrong. Remaining polish: the cloak needs independent cloth motion, and the drift could be slower and more horizontal.
- **Design** Do not add lore mechanics here. Locomotion and presentation only, exactly as the brief says.

## Chocolate Frog `chocolate_frog`
- **Identity** A confection that hops once and escapes **[canon]**.
- **Current state** **Has a texture, no rig, no animation.** The only genuine "Missing" in the roster.
- **Needs** **Art**: a frog rig built with `tools/rigkit.py`. Requirements: recognisable frog silhouette, chocolate-glossy surface, correct small scale (~0.4 blocks), proper box UVs. **Animation**: `idle` (breathing), `hop`, and that is nearly all — it exists to escape once.
- **Design** **Do not reuse a generic quadruped rig.** A frog's crouch and hop are its identity. No hurt or death clip needed — it is food.

## Niffler `niffler` / Baby Niffler `baby_niffler`
- **Identity** Black fluffy treasure-thief with a pouch **[canon]**.
- **Current state** **The only entity in the mod with sounds** — nine of them. Digging and carrying implemented. The baby renders from the adult rig.
- **Needs** Nothing urgent. It is the reference standard the rest of the roster should reach.

## Thestral, Mooncalf, Augurey, Hidebehind, Runespoor, Streeler, Cornish Pixie, Goblin Teller
- **Thestral** — `ThestralGrazeGoal` exists; skeletal winged horse, visible only to those who have seen death. **The visibility mechanic does not exist** (`hasSeenDeath` appears nowhere). Recorded, not built.
- **Mooncalf** — `GatherToDanceGoal` and `StayInBurrowGoal`; the **best idle behaviour in the mod** and the model for the whole `IdleProfile` system.
- **Augurey** — thin green-black bird whose cry was thought to foretell death **[canon]**; needs a bespoke cry.
- **Hidebehind** — its whole identity is never being seen; verify the existing implementation before designing.
- **Runespoor** — three-headed **[canon]**; each head should animate independently. High-value, unique.
- **Streeler, Cornish Pixie** — colour-changing snail and blue mischief-maker; both adequately implemented.
- **Goblin Teller** — an NPC, not fauna. Correctly has no Bestiary entry. Needs dialogue, not creature work.

## Non-creature bespoke entities
`broom`, `spell_projectile`, `beast_hex_projectile`, `wizarding_thrown`, `beam`, `spell_clash`,
`patronus`, `protego_shield`, `form_mannequin`, `duelling_dummy`, `dementor` (above).

None need creature design. The `patronus` and `protego_shield` are magical manifestations with their
own visual systems already built and verified working.

---

# Shared Behaviour Opportunities

Only where **multiple creatures genuinely need it**:

| Opportunity | Creatures | Why shared |
|---|---|---|
| **`FIRE` reaction polarity** | 15 `FIRE_IMMUNE` + Yeti + Salamander + Ashwinder | One rule, opposite signs. Yeti flees, Salamander investigates, dragons ignore. Wiring the FIRE stimulus once serves all 18. |
| **`CREATURE_DEATH` alert** | 10 `PACK` + herd animals (abraxan, aethonan, reem) + red_cap | One event, two responses (alert for packs, flee for prey). Red Cap's `INVESTIGATE` is the outlier that proves the framework. |
| **Purity gating** | unicorn, qilin | Already exists in `WildlifeRules`. Extend to the Qilin rather than rebuild. |
| **`bow` interaction** | hippogriff, kappa | Both have a canon bow-to-appease mechanic. One clip, one interaction path. |
| **Ranged bow users** | centaur, pukwudgie | Share `draw_bow` animation and `SKIRMISHER` profile. |
| **Colonial/swarm presence** | acromantula, doxy, chizpurfle, bundimun, gnome | All should appear in groups. A shared group-spawn concept, **not** a new AI system. |

**Not recommended:** a general climbing system (one creature), a general "intelligent being" dialogue
system (out of scope), any second reaction/ability/navigation layer.

---

# Shared Animation Opportunities

| Family | Shareable | Must be unique |
|---|---|---|
| Dragons (10) | One rig, one animation set, **retimed per breed** (±20–30%) | head/tail/wing furniture; Welsh Green's song |
| Winged horses (3) | One equine-winged set | gait timing only |
| Composites (4) | Adapt the dragon set | each needs a distinct `call` |
| Big cats (5) | One prowl + one lunge | Zouwu's tail, Nundu's silence |
| Small quadrupeds (5) | One walk/trot | Crup's forked tail, Knarl's bristle |
| Humanoid smalls (4) | One base humanoid | posture and limb ratio |
| Blob (6 of 9) | One squash-and-settle | Lethifold and Obscurus need `drift`, not squash |
| Aquatic (8) | One swim undulation | limbed paddle variant for 3 |
| Arthropod (6) | One multi-leg cycle | **Quintaped's asymmetric five-leg gait** |

**Highest-value single animations in the roster**, in order:
1. `death` — **no rig has one**; the hook is already wired and waiting.
2. Quintaped five-leg gait — nothing else looks like it.
3. Hippogriff `bow` — gates a bonding interaction.
4. Swooping Evil `fold`/`unfold` — two silhouettes from one creature.
5. Billywig spin-flight — the whole creature.
6. Runespoor three independent heads (bespoke).

---

# Art Production Plan

Work is classified, **not ranked**.

## Foundation
Already built and committed: `CreatureBehaviour`, `SoundProfile`, `IdleProfile`, `CreatureReaction`,
`CombatProfile`, `scale`, `CreatureIdleGoal`, death hook. **Nothing further is required before
creature work can start.**

## Identity-critical
Without these the creature is not itself.
- Chocolate Frog rig (the only missing model in the roster)
- Quintaped five-leg gait · Swooping Evil fold/unfold · Billywig spin
- Dragon head/tail/wing furniture for all ten breeds
- Sphinx human face · Manticore human face
- Clabbert pustule glowmask · Horned Serpent jewel glowmask
- Jobberknoll death cry · Fwooper song · Jarvey speech · Erkling lure song (four bespoke sounds)
- Hippogriff `bow`

## Behaviour-critical
- Wire `FIRE` stimulus (serves 18 creatures)
- Wire `CREATURE_DEATH` stimulus (serves ~14)
- Combat profiles for the 29 hostiles — **Troll, Nundu, Dugbog, Basilisk, Acromantula first**, as they demonstrate all five styles
- Idle clips per body plan: `graze`, `preen`, `groom`, `coil`, `settle` cover most of the roster

## Presentation
- Vanilla sound profiles for the ~90 silent creatures (cheap, immediate, high impact)
- `death` clips across all 110 rigs
- `hurt`/`flinch` for the 74 rigs lacking one
- Secondary idle clips

## Optional polish
- Retimed dragon variants
- Independent tail animation (Zouwu, Manticore, Crup)
- Murtlap crest motion
- Cloak cloth on the Dementor

---

# Open Design Decisions

Recorded rather than decided, because each is a judgement the project owner should make:

1. **Should bespoke entities gain profile support?** Lifting the accessors onto an interface would
   let the Phoenix, Bowtruckle and Augurey use `IdleProfile` and `SoundProfile`. It is an
   architectural change and the brief forbids forcing it.
2. **Centaur and Sphinx are filed `QUADRUPED`** but are intelligent Beings with humanoid upper
   bodies. Reclassification would be a silent change to creature classification — not done.
3. **Lethifold is `BLOB_SPHERE`** but is a flat drifting sheet. Same concern.
4. **`sea_serpent` and `basilisk` are `Locomotion.GROUND`**; the sea serpent is aquatic in lore.
5. **Four creatures carry `bioluminescence` with no lore basis**: puffskein, glumbumble, fwooper,
   jobberknoll. Consider removing rather than designing glow for them.
6. **Basilisk scale 1.4 is untested in world** — corridor collision at 3.78 × 4.06 needs a playtest.
7. **Spell-interaction candidates with strong rationale, none implemented**: Scourgify kills
   Bundimun; Silencio suppresses Fwooper; Revelio defeats Demiguise invisibility; Aguamenti/Frigora
   destroys Ashwinder eggs; Patronus repels Lethifold; Chizpurfle damages wand integrity.
8. **Thestral death-visibility** does not exist (`hasSeenDeath` appears nowhere). Canon-central.
9. **Dragons have no `spell_resist`** despite being the most magic-resistant creatures in canon.
10. **Moke shrinking** could reuse `Attributes.SCALE` rather than `camouflage`.
11. **Kneazle judgement** could read `MagicalStanding` rather than being generic `danger_sense`.
12. **Nogtail's white-dog counter** would need a Crup interaction — a genuine inter-creature mechanic.

---

# Coverage

All **96** data-driven creatures have an entry: WINGED_QUADRUPED 17, QUADRUPED 23, BIPED_HUMANOID 13,
BLOB_SPHERE 9, AQUATIC 8, ARTHROPOD_MULTILEG 6, SERPENTINE 5, INSECTOID_FLYER 5, AVIAN 5,
LARGE_HUMANOID 3, WORM_LARVA 1, SESSILE 1.

The 24 bespoke entities are covered in their own section and are **explicitly not** given
`CreatureDefinition` profiles.

**Source data:** `build/entity-audit/design-input.json` — every creature's body plan, locomotion,
temperament, traits, abilities, declared clips, scale, Ministry rating, study methods, materials and
authored lore line, extracted from the repository at `947c9526`.
