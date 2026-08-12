# SPELL_CANON_AUDIT.md — Phase 0, read-only

**No code written. No file edited. No JSON created.**

| | |
|---|---|
| SHA | `d794677a` |
| Branch | `fix/chamber-of-secrets-module-gate` |
| Date | 2026-08-03 |

Pre-flight `git status --porcelain` (obscurus model work in progress, not mine, untouched):

```
 M src/main/resources/assets/wizards_and_beasts/geckolib/animations/entity/obscurus.animation.json
 M src/main/resources/assets/wizards_and_beasts/geckolib/models/entity/obscurus.geo.json
 M src/main/resources/assets/wizards_and_beasts/textures/entity/obscurus.png
 M src/main/resources/assets/wizards_and_beasts/textures/entity/obscurus_glowmask.png
 M tools/beast_skins.py
?? tools/obscurus_model.py
```

---

## ⛔ HALT — two Phase 0 stop-triggers fire

Both are structural. Both change the shape of Phase 1. Neither can be resolved by me.
**§0.3's canon delta table is deliberately NOT attempted** — see §4 for why that is the correct
order.

---

## §0.1 Inventory — actual counts

**33 spells, across two authoritative sources.**

| source | count | ids |
|---|---:|---|
| Datapack JSON `data/wizards_and_beasts/spells/*.json` | **27** | accio, aguamenti, alohomora, arresto_momentum, bombarda, capacious_extremis, claustra_reverto, colloportus, confringo, crucio, depulso, diffindo, episkey, expelliarmus, finite_incantatem, flipendo, frigora, glacius, incendio, levicorpus, liberacorpus, lumos, nox, reparo, riddikulus, stupefy, wingardium_leviosa |
| Bespoke Java `spell/impl/*.java` | **6** | avada_kedavra, expecto_patronum, imperio, obscurus_grasp, obscurus_surge, protego |

**The two sets are disjoint.** Every id appears in exactly one source; there is no id present in
both, and therefore no pair of definitions that disagree.

This matters because it is *not* the wand-wood failure pattern §1.6 warns about. Wand woods had
10 JSON definitions and 4 enum constants describing **the same concept**, with the runtime reading
the enum and silently dropping 6. Here the sources **partition** the spell set. Reporting this as
the same failure would have been wrong, and I checked specifically because the prompt invited that
conclusion.

It is still a structural problem, but a different one — see Stop-trigger 2.

---

## §0.2 `canonTier` reconciliation

### ⛔ STOP-TRIGGER 1 — `canonTier` already encodes `canonConfidence`

`SpellCanonTier` (`spell/def/SpellCanonTier.java:20`) declares:

| constant | serialized | javadoc meaning |
|---|---|---|
| `BOOKS` | `books` | Attested in the seven main novels |
| `COMPANION` | `companion` | Attested in a Scamander/Whisp companion book |
| `FILM` | `film` | Attested on screen but not on the page |
| `POTTERMORE` | `pottermore` | Attested in Pottermore / Wizarding World authorial writing |
| `EXPANDED` | `expanded` | Attested only in licensed expanded material such as the video games |
| `ORIGINAL` | `original` | No attestation in any canon source — original to this mod |

§2 of this prompt asks for a new field `canonConfidence` with the value set
`PRIMARY` / `COMPANION` / `FILM` / `POTTERMORE` / `EXTRAPOLATED`.

**These are the same concept.** Four of five proposed values already exist under the same names.
`PRIMARY` is `BOOKS`; `EXTRAPOLATED` is approximately `ORIGINAL`, and the existing set is *finer* —
it separates `EXPANDED` (licensed games) from `ORIGINAL` (invented here), a distinction the
proposed set collapses.

§0.2 says: *"if `canonTier` already encodes part of what this prompt asks for, stop and report. Do
not create a parallel metadata field alongside it."* It does. I have not written anything.

**Ruling needed:** extend `canonTier` as the home for canon-confidence, or introduce
`canonConfidence` and define the relationship between the two. I recommend the former and would
not add the second field without being told to.

### `canonTier` is currently dead

It is parsed by `SpellDefinition` (`:99`, `:292`, `:322`) and read by **no gameplay code**. Grep
for `getCanonTier` / `canonTier()` outside the definition record and the enum itself returns
nothing. It is `Optional`, deliberately, so absence means "untriaged" rather than a default —
that part is well designed.

This is the same class of finding as `DEFECT_REGISTER.md` S3 (parsed, never read). It is not an
argument against using it; it is an argument that Phase 1 should give it a reader.

---

## ⛔ STOP-TRIGGER 2 — canon metadata cannot reach the 6 Java spells (§1.6)

The 6 bespoke spells **do not use `SpellDefinition` at all.** Grep for `SpellDefinition` across
`spell/impl/*.java` returns nothing. Only `JsonSpell` (`spell/core/JsonSpell.java:26`) holds one.

So a codec-backed canon metadata block — which is what §2 specifies — is reachable by the 27
datapack spells and **structurally unreachable by the other 6**.

That is not an edge case. It lands squarely on this prompt's own mandate:

- **§3 correction class 5 requires every Unforgivable to be gated by `Module.DARK_ARTS`.** Of the
  three Unforgivables, **`crucio` is datapack, but `avada_kedavra` and `imperio` are Java.** Two
  thirds of the rule's subject matter cannot carry the data that expresses it.
- `protego` is Java, and §3 correction class 6 is *specifically* about blocking/immunity flags
  contradicting canon blockability — Protego versus Avada Kedavra is the canonical example, and
  **both sides of that relationship are Java-side.**
- `countersSpell` / `counteredBy` would resolve `Identifier`s across a set where 6 members cannot
  declare their half of the relationship.

**Ruling needed.** Three shapes, and I will not pick:

1. **Metadata on the `Spell` base class**, with `SpellDefinition` supplying it for JSON spells and
   the 6 Java classes declaring it in their constructors. Reaches all 33. Means canon metadata is
   not purely data-driven.
2. **Migrate the 6 to datapack first**, then metadata is uniform. That is the migration your notes
   already track as open ("Imperio + Avada JSON migration, 1 piece each") — but it is a separate
   prompt, and §6 puts it out of scope here.
3. **Metadata on `SpellDefinition` only**, accepting that 6 spells — including 2 Unforgivables and
   Protego — are exempt. This makes the Phase 4 test "every Unforgivable is gated" either
   unimplementable or vacuous.

Option 3 looks wrong to me and I want that on record, but the call is yours.

---

## §0.3 Canon delta table — NOT ATTEMPTED, deliberately

Not produced, for two reasons, in order of importance:

1. **It would be premature.** Both stop-triggers change where findings land and how they are
   expressed. §1.6 says halt *immediately*; producing 33 rows of analysis against a metadata shape
   that may not survive your ruling is wasted work that would then need redoing.

2. **Citation discipline needs budget I do not have left in this session.** §1.5 is explicit that a
   fabricated citation is a hard failure and that honest `UNVERIFIED` is correct behaviour. Doing
   33 spells × book-and-chapter properly is careful work. I would rather deliver zero rows than
   rows padded with plausible-looking chapter numbers I cannot stand behind — and with 33 spells
   the temptation to pattern-match a citation is exactly the failure mode §1.5 exists to prevent.

The inventory above is the part that is verifiable from the repo and is complete and checked. The
canon comparison is the part that requires sources, and it should start from a settled metadata
shape.

**Estimated finding volume:** unknown, and I decline to guess. §1.6 asks for a split proposal above
~60 findings; with 33 spells generating potentially multiple findings each, that threshold is
plausibly in reach, which is itself a reason to settle shape before volume.

---

## What I need from you

1. **`canonTier` or `canonConfidence`?** (Stop-trigger 1.) My recommendation: extend `canonTier`,
   and give it its first reader.
2. **Where does canon metadata live so it reaches all 33 spells?** (Stop-trigger 2.) Options above.
   Not option 3, in my view.
3. **Once ruled — split §0.3?** I would suggest the delta table run in its own pass, batched by
   source (27 datapack, then 6 Java), so citation work gets proper attention.

Nothing has been written to any source file, any JSON, or any lang file.

---

# Rulings received — 2026-08-03

**1. Metadata home: extend `canonTier`.** Ruled as recommended. No `canonConfidence` field will be
created. `canonTier` gets its first reader in Phase 1 and grows the remaining canon fields around
it.

**2 and 3 were returned to me.** Both are implementation and sequencing rather than canon, and §8
names *"where the metadata lives"* explicitly as an implementation-quality call to be made on repo
evidence. Recording the reasoning so it can be overruled on sight.

## Decision 2 — canon metadata lives on the `Spell` base class, via an overridable accessor

Not on `SpellDefinition` alone. That was option 3 in the audit above and it exempts 2 of the 3
Unforgivables plus Protego, which guts §3's correction classes 5 and 6.

Not by extending the `Spell` constructor either, which was the obvious reading of option 1. The
constructor is already 6 positional arguments:

```java
super("avada_kedavra", "Avada Kedavra", SpellCategory.DARK_ARTS, 1200, 999.0f, 0xFF00FF00);
super("protego",       "Protego",       SpellCategory.DEFENSE,    100,   0.0f, 0xFF4488FF);
```

Adding canon fields positionally would push that toward the same shape as the 18-argument
`PlayerAbilityData` constructor this repo has already been bitten by — where a transposed pair of
same-typed arguments compiles cleanly and is wrong at runtime. Two of the six existing arguments
are already `int, float` adjacent.

Instead:

- A `SpellCanonMeta` record holding `incantation`, `classification`, `canonTier`, `canonSource`,
  `countersSpell`, `counteredBy`, `targetRestriction`, `canonColor` — every field optional, with an
  `EMPTY` constant.
- `Spell#canonMeta()` returns `SpellCanonMeta.EMPTY` by default. No constructor changes, so all 33
  spells compile untouched.
- `JsonSpell` overrides it to project the values out of its `SpellDefinition`.
- Each of the 6 bespoke classes overrides it to declare its own.

This reaches all 33, keeps the datapack spells fully data-driven, changes no existing signature, and
makes "does this spell have canon metadata" a single call site the Phase 4 tests can iterate.

**Consequence to accept:** canon metadata is no longer purely datapack-authored. For the 6 Java
spells it is code. That is a real cost and the alternative — migrating those 6 to datapack — is
explicitly out of scope here (§6) and already tracked as open work.

## Decision 3 — §0.3 runs as its own pass, batched by source

27 datapack spells first, then the 6 Java. Two reasons:

- Phase 1's backfill *depends on* the delta table, so Phase 1 cannot complete before §0.3 does.
  Sequencing them into one pass would produce a half-backfilled metadata block.
- §1.5 citation work is the slow, careful part and the part where haste turns into fabrication.
  Batching keeps a bounded number of spells in view at once.

**Next action is §0.3, not Phase 1**, and it wants a session with real budget rather than the tail
of this one. Nothing further has been written.

---

# §0.3 — batch 1: repo-verifiable findings (no citation risk)

Started with the findings whose *canon* half is asserted by this prompt itself (§3 correction
classes 5 and 6), so the only thing needing proof is the **code** half — which is checkable in the
repo. Citation-bearing findings are deliberately not in this batch; see §0.3 above.

## F-1 — Unforgivables are gated at learn-time only, not at cast-time — **Tier A**

| | |
|---|---|
| spells | `avada_kedavra`, `imperio`, `crucio` (all three) |
| §3 class | 5 — "any Unforgivable not behind `Module.DARK_ARTS` is a bug" |
| citation | none required — the rule is asserted by this prompt |

`SpellLearningEligibility.java:37-38` refuses to let a `SpellCategory.DARK_ARTS` spell be **learned**
while `Module.DARK_ARTS` is disabled:

```java
if (spell.getCategory() == SpellCategory.DARK_ARTS
        && !ModuleManager.isEnabled(Module.DARK_ARTS)) {
```

That is the only module check on the path. `SpellCastGate` declares nine rejection reasons —
`NO_ACTIVE_SPELL`, `UNKNOWN_SPELL`, `SPELL_NOT_KNOWN`, `OBSCURIAL_ABILITY_INPUT`,
`REQUIREMENTS_UNMET`, `OBSCURIAL_DARK_ONLY`, `OBSCURIAL_DARK_RESTRICTED`, `ON_COOLDOWN`,
`GLOBAL_COOLDOWN` — and **none of them is a module gate.** `SpellCastService` and `SpellCastGate`
contain no `Module.DARK_ARTS` reference at all.

**Failure scenario:** a player learns Crucio while the module is enabled. An operator later disables
`Module.DARK_ARTS`. The player still knows the spell, so `SPELL_NOT_KNOWN` does not fire, and no
other gate applies — the Unforgivable remains castable with the module off.

The two `Module.DARK_ARTS` checks that *do* exist on the cast path
(`SpellCastTargetedHandler.java:119, :182`) are the inverse: they *enable* Imperio's mob-control
behaviour when the module is on. With the module off, Imperio still casts, it just stops controlling
— which is a partial no-op rather than a refusal.

**Not fixed here.** §3 permits this correction class, but adding a cast-time gate means a new
`SpellCastGate` constant and a rejection message, and this session has no budget left to do that and
verify it. Logged for the next pass.

## F-2 — Protego vs Avada Kedavra: needs a citation-bearing check — **deferred**

§3 class 6 covers blocking flags that contradict canon blockability, and Protego/Avada Kedavra is the
textbook case. Both are Java-side (`spell/impl/Protego.java`, `spell/impl/AvadaKedavra.java`), so
whatever the finding is, it lands in the six that cannot carry datapack metadata — Stop-trigger 2.

The code half needs reading `Protego`'s block logic properly; the canon half needs a real citation.
Neither is done. **Not a finding yet — an identified place to look.** Recorded so the next pass does
not have to rediscover it.

## Batch 1 status

2 items examined, 1 confirmed Tier A, 1 deferred. This is **not** the full §0.3 table — 33 spells
remain to be compared against sources, which is the citation-heavy work that needs its own session.
