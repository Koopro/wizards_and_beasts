# The Transformed Player Contract

What a player wearing a body that is not their own can and cannot do — and where each rule is enforced.

Code: `at.koopro.wizardsandbeasts.form.constraint` (what a form takes away),
`at.koopro.wizardsandbeasts.form.sense` (what it gives back).

---

## 1. The contract

| | Animagus (beast) | Werewolf, medicated | Werewolf, feral | Obscurial (dark form) |
|---|---|---|---|---|
| Constraint set | `BEAST_HANDS` | `BEAST_HANDS` | `FERAL` | `BEAST_HANDS` − `NO_SPELLCASTING` |
| Use an item / start using one | ✗ | ✗ | ✗ | ✗ |
| Interact with a block | ✗ | ✗ | ✗ | ✗ |
| Break a block | ✗ | ✗ | ✗ | ✗ |
| Interact with an entity | ✗ | ✗ | ✗ | ✗ |
| Open or hold a container (incl. own inventory) | ✗ | ✗ | ✗ | ✗ |
| Drop items | ✗ | ✗ | ✗ | ✗ |
| Cast a spell / assign / select | ✗ | ✗ | ✗ | **✓ (curated)** |
| **Change back voluntarily** | **✓** | **✓** | ✗ | ✓ |
| Melee | ✓ | ✓ | ✓ | ✓ |
| Steer their own movement | ✓ | ✓ | ✗ (driven) | ✓ |
| Chat, pause menu, death screen | ✓ | ✓ | ✓ | ✓ |
| Take damage, die, respawn | ✓ | ✓ | ✓ | ✓ |

**The Obscurial column is the exception that proves the rule.** Every other transformed state is mute —
a cat and a wolf hold no wand. Dark form is the opposite case: casting is what it is *for*, curated by
`ObscurialRules.isSpellAllowedInDarkForm` / `isDarkFormOnlySpell` and enforced by its own gates in
`SpellCastGate`. Handing it the blanket ban would have silently deleted `ObscurusGrasp`, `ObscurusSurge`
and every Dark Arts cast the form exists to enable.

**The Animagus column never changes.** A transformation a wizard chose is one they can end; `BEAST_HANDS`
omits `NO_VOLUNTARY_EXIT` and `FormConstraintSetTest.beastHands_neverTrapsAnAnimagus` fails if that is
ever "tidied" away.

**Melee is deliberately unconstrained.** A beast's answer to everything is its teeth. It is also not
implementable as a constraint: NeoForge fires `AttackEntityEvent` from *inside* `Player.attack`, so
cancelling it would disarm the werewolf controller's own bite along with the player's.

**Movement is not a constraint.** Being unable to steer is something *done to* a player, needs a tick and
a controller rather than an event cancel, and only the werewolf has it. It lives in `FeralController`.

## 2. Where each rule is enforced

| Constraint | Enforced by |
|---|---|
| `NO_ITEM_USE` | `FormConstraintEvents` — `RightClickItem` + `LivingEntityUseItemEvent.Start` + a per-tick `stopUsingItem` |
| `NO_BLOCK_INTERACT` | `FormConstraintEvents` — `RightClickBlock` |
| `NO_BLOCK_BREAK` | `FormConstraintEvents` — `LeftClickBlock` |
| `NO_ENTITY_INTERACT` | `FormConstraintEvents` — `EntityInteract`, `EntityInteractSpecific` |
| `NO_INVENTORY` | `FormConstraintEvents` — per-tick `closeContainer()` (`PlayerContainerEvent.Open` is not cancellable) |
| `NO_ITEM_DROP` | `FormConstraintEvents` — `ItemTossEvent`, stack restored *before* the cancel |
| `NO_SPELLCASTING` | `SpellNetworkGuards.canUseWand` — the one guard every wand packet passes |
| `NO_VOLUNTARY_EXIT` | the service offering the exit (`WerewolfTransformService.requestVoluntaryRevert`) |

A system does not write handlers. It registers a `FormConstraintSource` and returns a set.

## 3. Why the client cannot bypass it

Every source takes a `ServerPlayer` and reads server-authoritative state — an attachment the client
cannot write, or a form id the server assigned. `FormConstraints.denies(Object, …)` returns `false` for
anything that is not a `ServerPlayer`, so a client-side copy of an interaction never decides anything.

Client-side restrictions exist (refusing to open a screen; dropping movement input while feral) and are
**cosmetic smoothing only** — each is backed by a server rule that holds regardless:

| Client does | Server does anyway |
|---|---|
| refuses to open a container screen | closes any open container every tick |
| zeroes movement input while feral | drives velocity, forces rotation, teleports back on sustained drift |
| — | cancels every interaction packet |
| — | refuses every wand packet at the network guard |

A crafted interact packet, cast packet, container click or movement packet from a modified client changes
nothing.

## 4. Composition

Sources are unioned, not prioritised: a player under two systems gets the **strictest** reading of both,
and no source needs to know another exists. An Animagus who is also a werewolf caught by a full moon is
feral, because `BEAST_HANDS ∪ FERAL = FERAL`.

## 5. Senses

The mirror image: what a form gives.

| Sense | Effect | Granted to |
|---|---|---|
| `NIGHT_EYES` | vanilla Night Vision, ambient, no HUD icon | any form declaring `NIGHT_VISION`; werewolf wolf form |
| `SCENT_TRACK` | living entities outlined through blocks within 16 blocks, viewer only | any form declaring `SCENT_TRACK`; werewolf wolf form |
| `KEEN_SIGHT` | *declared, not implemented* — pure client render tuning, no server hook | — |

Two implementation notes worth keeping:

**Night vision must not be re-applied on a short timer.** Vanilla strobes the screen once an instance has
under 200 ticks left, so the Animagus passive service's 40-tick refresh made cat form flicker permanently
in the dark. `FormSenseService` uses a 400-tick instance refreshed every 100, so it never drops below 300.
Removal is conditional on the remaining duration looking like ours, so a player who drank a real Potion of
Night Vision keeps it.

**Scent is one rule, not two.** The outline is drawn purely in client render state (no packet), so the
client must know whether the viewer has the sense. Rather than re-deriving "am I a transformed cat or an
unmedicated wolf" on both sides, the server grants a marker effect (`wizards_and_beasts:scent_tracking`)
and the client asks only whether it is present. Mob effects already sync; this costs nothing new on the
wire. Same trick `Wrackspurt` uses.

## 6. Lifecycle

| Event | What happens |
|---|---|
| Death | Animagus: flag cleared **and form reset to default**. Werewolf: `forceReset`, controller released. |
| Respawn | form re-applied (`FormLifecycleHandler`); werewolf attributes re-applied |
| Dimension change | form re-applied and re-announced to the new tracking set |
| Logout mid-transform | `TransitionManager.onPlayerLoggedOut` ends the transition **and restores prior invulnerability** |
| Login | form re-applied, everyone's forms synced to the joiner, werewolf attributes re-applied |
| Heritage module off | werewolf control released; the shape is left alone |

## 7. Known gaps

### `AnimagusFormDefinition` was inert, and is now only partly live

Every field of the datapack type had **zero readers** in the source tree: `hitbox()`, `attributes()`,
`flight()`, `sounds()`, `animationMap()`, `animations()`. The file was loaded, validated against its own
invariants, synced to every client, and consulted by nothing. What players actually got came from a
hardcoded `switch` on form id in `AnimagusAbilityService` — keyed on the *other* vocabulary.

| Field / capability | State |
|---|---|
| `attributes` | **live** — applied by `AnimagusCapabilityService`, minus scale/step height (see below) |
| `CLIMB` | **live** — spider-rule wall climbing |
| `SAFE_LANDING` | **live** — falls out of `attributes` carrying `safe_fall_distance`, as its own javadoc always said it should |
| `NIGHT_VISION`, `SCENT_TRACK` | **live** — via `FormSense` |
| `hitbox` | inert — `SizeProfileRegistry` owns geometry instead, and the two disagree |
| `flight`, `AnimagusFlight`, `AnimagusHitbox` | inert — zero readers |
| `sounds`, `animationMap`, `animations` | inert — the renderer does not look animations up by role, despite the codec validating that it does |
| `LOW_PROFILE`, `GAP_SQUEEZE`, `KEEN_SIGHT` | not implemented — and only declared on `rat`/`falcon`, neither of which is selectable, so implementing them today would be dead code |

**Scale and step height are deliberately skipped** when applying the attribute block: `SizeProfileRegistry`
already applies them per `animagus_*` form, and stacking a second modifier would scale the player twice.
The two sources disagree — `cat.json` says scale 0.55 where the profile says 1.0; `dog.json`'s hitbox is
0.8 × 0.9 where the profile says 0.60 × 0.85. Unifying them means making the size profile derive from the
definition, which is a change to the form system rather than to the Animagus one.

### The two Animagus vocabularies still do not overlap

Selectable: `animagus_cat`, `animagus_dog`, `animagus_stag`, `animagus_hawk`, `animagus_hare`,
`animagus_beetle`. Shipped definitions: `cat`, `dog`, `falcon`, `rat`. Only **cat and dog** resolve, so
only those two get anything data-driven; `stag`/`hawk`/`hare`/`beetle` fall back entirely to the hardcoded
switch, and `falcon`/`rat` are unreachable content.

Closing it needs rigs, textures and animation files that do not exist. What changed is that it is no
longer **silent**: `AnimagusFormLoader` logs both halves at `WARN` on every reload.
