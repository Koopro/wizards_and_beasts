# Asset Status

> **STATUS: Stale per `ALPHA_IMPROVEMENT_AUDIT.md` §14.1 ("Dated May 19, materially wrong about current coverage").** For current asset/textures coverage, see `ALPHA_IMPROVEMENT_AUDIT.md` §13.

## Sweets / Foods / Drinks

Legend: `Complete` = runtime-ready, `Partial` = missing one or more major pieces.

| Item | Type | Registry | Creative Tab | Lang Name+Desc | Model/Item Def | Recipe | Status | Notes |
|---|---|---|---|---|---|---|---|---|
| `brew` | drink | Yes | Yes | Yes | Yes | N/A (data-driven brew output) | Complete | Uses shared brew system + `BrewItem` |
| `butterbeer` | drink | Yes | Yes | Yes | Yes | No | Complete | Uses quick consumable behavior |
| `pumpkin_juice` | drink | Yes | Yes | Yes | Yes | No | Complete | Uses quick consumable behavior |
| `chocolate_frog` | sweet | Yes | Yes | Yes | Yes | No | Complete | Custom random card reward behavior |
| `bertie_botts_every_flavour_beans` | sweet | Yes | Yes | Yes | Yes | No | Complete | Custom random effect behavior |
| `droobles_best_blowing_gum` | sweet | Yes | Yes | Yes | Yes | No | Complete | Uses quick consumable behavior |
| `firewhisky` | drink | Yes | Yes | Yes | Yes | No | Complete | Uses quick consumable behavior |
| `gillyweed` | consumable herb | Yes | Yes | Yes | Yes | No | Complete | Uses quick consumable behavior |
| `dirigible_plum` | food | Yes | Yes | Yes | Yes | No | Complete | Uses quick consumable behavior |
| `dittany` | consumable herb | Yes | Yes | Yes | Yes | No | Complete | Custom healing behavior |
| `bezoar` | consumable antidote | Yes | Yes | Yes | Yes | No | Complete | Custom antidote behavior |
| `treacle_tart` | sweet | Yes | Yes | Yes | Yes | Yes | Complete | |
| `pumpkin_pasty` | food | Yes | Yes | Yes | Yes | Yes | Complete | |
| `fizzing_whizzbee` | sweet | Yes | Yes | Yes | Yes | Yes | Complete | |
| `peppermint_toad` | sweet | Yes | Yes | Yes | Yes | Yes | Complete | |

### Consumable Effects Policy

- Drinks use `DRINK` animation with longer consume windows.
- Sweets/snacks use `EAT` animation with short consume windows.
- Curatives (`dittany`, `bezoar`) have cooldown guardrails to avoid chain-spam.
- Shared quick-consumables consume on finish (timed), not instantly on click.

### Balance

| Item | Consume | Animation | Nutrition / Heal | Main Effects | Cooldown |
|---|---:|---|---|---|---:|
| `brew` | 32t | Drink | data-driven | brew effects scaled by `BrewPotency` | 20t |
| `butterbeer` | 28t | Drink | +2, sat 0.3 | Regeneration I (160t) | 10t |
| `pumpkin_juice` | 28t | Drink | +4, sat 0.6 | none | 0 |
| `chocolate_frog` | 18t | Eat | +3, sat 0.45 | grants random famous wizard card | 10t |
| `bertie_botts_every_flavour_beans` | 16t | Eat | +1, sat 0.2 | 50% good: Saturation + Speed; 50% bad: Nausea + Poison | 8t |
| `droobles_best_blowing_gum` | 16t | Eat | +1, sat 0.1 | Slow Falling (400t) | 8t |
| `firewhisky` | 30t | Drink | +1, sat 0.05 | Strength I (160t) + Blindness (100t) | 20t |
| `gillyweed` | 20t | Eat | +1, sat 0.1 | Water Breathing (800t) | 15t |
| `dirigible_plum` | 16t | Eat | +2, sat 0.25 | Levitation (40t) | 12t |
| `dittany` | 24t | Drink | heal 6.0 | direct heal | 100t |
| `bezoar` | 28t | Drink | n/a | removes all active effects | 200t |
| `treacle_tart` | 24t | Eat | +7, sat 0.9 | none | 0 |
| `pumpkin_pasty` | 20t | Eat | +5, sat 0.6 | none | 0 |
| `fizzing_whizzbee` | 16t | Eat | +2, sat 0.3 | Jump Boost I (240t) | 10t |
| `peppermint_toad` | 16t | Eat | +2, sat 0.3 | Speed I (200t) | 10t |

---

## Creative Tab

### Previously missing (resolved)

- [x] `debug_wand`
- [x] `morph_wand`
- [x] `brew`
- [x] `invisibility_cloak`
- [x] `deathly_hallow_cloak`
- [x] `treacle_tart`
- [x] `pumpkin_pasty`
- [x] `fizzing_whizzbee`
- [x] `peppermint_toad`

### Blocks intentionally excluded from creative tab

- [ ] `mandrake_crop`
- [ ] `deluminator_light`
- [ ] `unlit_torch`
- [ ] `unlit_wall_torch`
- [ ] `unlit_lantern`
- [ ] `unlit_glowstone`

---

## Textures / Models

Asset paths: `src/main/resources/assets/wizards_and_beasts` and `src/generated/resources/assets/wizards_and_beasts`

### Blocks

- [ ] Generated block models still reference texture ids that do not have local PNG coverage parity.

### Items — resolved

- [x] `invisibility_cloak` texture present
- [x] `broom` item model present
- [x] `goblin_teller_spawn_egg` item model present
- [x] `niffler_spawn_egg` item model present
- [x] `deathly_hallow_cloak`, `deluminator`, `wand`, `galleon`, `knut`, `sickle` — local textures present

### Sweets / food / drink — interim vanilla fallback models

- [x] `brew` — vanilla potion texture fallback
- [x] All drinks/foods: `butterbeer`, `pumpkin_juice`, `chocolate_frog`, `bertie_botts_every_flavour_beans`, `droobles_best_blowing_gum`, `firewhisky`, `gillyweed`, `dirigible_plum`, `treacle_tart`, `pumpkin_pasty`, `fizzing_whizzbee`, `peppermint_toad`
- [ ] Replace vanilla fallback textures with bespoke art under `textures/item/*.png`.

### Notes

- Most generated item models point to `wizards_and_beasts:item/*` texture paths, but most corresponding PNG files are not present.
- A parity test validates generated item definitions resolve to real model files.
- `runData` is blocked by missing blockstate definitions for: `deluminator_light`, `unlit_torch`, `unlit_wall_torch`, `unlit_lantern`, `unlit_glowstone`.
