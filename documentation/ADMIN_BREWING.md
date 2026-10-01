# Brewing and potion administration

Phase 5 of the Control Center, built on `ADMIN_FRAMEWORK.md` and following the patterns of `ADMIN_MAGIC.md`. It adds
the **Brewing** section:

- a browser over every brew the server has;
- per-brew enable and effect editing, with a reusable effect editor;
- per-recipe values;
- a client-side preview;
- the global brewing rules.

It adds no second brewing path. The cauldron, the drink, silver refining, the debug inspector and the client sync
all keep reading the same two registries. What changes is what is registered in them.

---

## 1. What already existed

| Part | Where |
|---|---|
| Brews | `data/*/brews/*.json` → `BrewDefinition` → `Brew`, registered in `Brews` by `BrewReloadListener`. |
| Brew behaviour | A list of components (`BrewEffect`: `apply_effects`, `heal_and_cure`, `felix_felicis`, `polyjuice_disguise`, `truth_serum`, `flourish`, …), each with a phase (`on_drink` / `on_brew_complete`). A legacy `effects` list is wrapped in one `apply_effects` at load. |
| Mob effects | `ApplyEffects.EffectSpec(id, duration, amplifier, ambient)`. Applied on drink, with duration scaled by the brewer's skill (`BrewPotency`). |
| Recipes | `data/*/brewing_recipes/*.json` → `BrewingRecipe` (ingredients, `CauldronTier`, heat time, output brew, failure chance, optional timed catalyst), registered in `BrewingRecipes`. `findMatch` returns the first recipe that matches. |
| Cauldron | `CauldronBlockEntity`:<br>• `startBrewing`: recipe match → brew;<br>• the tick: heat required, spoils after 200 cold ticks, counts down;<br>• `rollFailure`: rolled once at completion, `BrewFailure`;<br>• `timedAdd`: catalyst window, contamination 0.25 per item. |
| Drink | `BrewItem.onConsumed` runs the brew's components. The stack carries only a brew id. |
| Sync | `BrewDataSyncPayload` sends the live registries to every client on join and on reload. |
| Config / module | None for brewing. Brewing had no gate. |

## 2. How admin hooks in: an effective-registry overlay

```
reload listener → authored brews/recipes → BrewTuning.acceptAuthored…()
                                                   │
                         BrewTuningData (world) ───┤ overrides
                                                   ▼
                    Brews / BrewingRecipes  ←  authored + override   (re-registered)
                                                   │
         cauldron · drink · silver · inspector · BrewDataSyncPayload (unchanged consumers)
```

- **Reload.** The reload listeners register the authored brews and recipes exactly as before, then hand them to
  `BrewTuning`. `BrewTuning` re-registers each one with its override applied.
- **Changes.** `BrewTuningService` stores an admin change in world data, republishes, and re-sends the existing
  brew sync, so clients (tooltips, handbook) see the tuned values.
- **No overrides.** With no overrides, the registries hold exactly what the datapacks define.
- **World lifecycle.** A world closing drops its overrides.

## 3. What is editable

**Per brew** (`brew/<ns>/<id>/<property>`):

| Setting | Type | Enforced at |
|---|---|---|
| `enabled` | on/off | `startBrewing` refuses with `BREW_DISABLED`, consuming nothing. `BrewItem` refuses the drink: the bottle is kept and no effect is applied. |
| `effects` | effect list | The brew's single `apply_effects` component is replaced by the enabled rows. The legacy list is rebuilt to match, so the client sync carries the same effects. |

**Per recipe** (`brew_recipe/<ns>/<id>/<property>`):

| Setting | Type |
|---|---|
| `heat_time` | ticks, 20–72000 |
| `failure_chance` | 0–1 |
| `cauldron_tier` | pewter / brass / copper |
| `ingredient.<item ns>.<item path>` | count, 1–64. There is one per ingredient the recipe has. |

**Global** (Brewing → Rules, new keys in `Config`):

| Setting | What it does | Default |
|---|---|---|
| `brewing_enabled` ⚠(off) | No new brews start (`BREWING_DISABLED`). Brews on the heat finish; bottles can still be drunk. | on |
| `brew_speed_multiplier` ⚠(>2×) | Divides the heat time when a brew starts. | 1 |
| `brew_failure_multiplier` | Scales the recipe's own failure chance at the roll. Penalties and skill still apply. | 1 |
| `brew_require_heat_source` ⚠(off) | The "required equipment" rule: the heat check at start and on every tick. | on |
| `brew_contamination_penalty` | The "ingredient restriction" rule: failure added per wrong item in a working pot. | 0.25 |

### The effect list

One setting holds the whole list in a text form (`BrewEffectText`):

```
minecraft:speed 1200 0; -minecraft:luck 600 1; wizards_and_beasts:wolfsbane 3600 0 ambient
```

- **Format.** Each entry is `[-]<effect> <duration ticks> <amplifier> [ambient]`. A leading `-` keeps the row but
  switches it off; this is the editor's Enabled toggle.
- **One value.** The list goes through authorisation, validation, confirmation, history and undo once, and a change
  applies atomically.
- **Parsing is strict and happens on the server.** An unknown effect id, a duration outside 1–72000, an amplifier
  outside 0–9, a duplicate effect, a malformed entry or more than 12 entries makes the whole value
  `INVALID_VALUE`, and nothing changes.
- **Rules.**
  - A brew whose only behaviour is its effect list must keep at least one enabled effect (`CONFLICT`).
  - Adding an effect the brew never had, raising an amplifier, or more than doubling a duration is dangerous and
    needs confirmation.
- **Where add/remove is safe.** Only brews whose drink-time effects are exactly one `apply_effects` component; 12
  of the 14 shipped brews qualify. Felix Felicis and Polyjuice are built from bespoke components. They show their
  component list read-only, and no `effects` setting exists for them.

### Recipes stay valid

- The ingredients themselves (which items) and the output are not overridable, only how many of each. A recipe can
  be made cheaper or dearer but never turned into a different recipe.
- Bounds keep every count at least 1, and heat times and chances in range.
- A tier or count change that would make one recipe always match before another is refused as `CONFLICT`
  (`recipe_shadowed`). `findMatch` takes the first match, so the later recipe could never be brewed.
  - The check compares shadowing before and after the change, so a pre-existing overlap never blocks unrelated
    edits.
  - No shipped recipe pair can shadow today (no pair shares an ingredient subset); the check guards datapacks and
    future edits.

### Shown, not invented

- **Difficulty** is a label derived from the fields that make a recipe hard: cauldron tier, failure chance, and
  whether it has a catalyst. The data has no difficulty field.
- **Category.** Brews carry no category, so the browser's "equipment" filter uses the recipe's cauldron tier, plus
  "silver-based" and "no recipe".
- **Brewing time** is the recipe heat time. There is no separate brewing-time value.
- **Duration and amplifier** are per effect, inside the effect list. A brew has no global duration or amplifier.
- **Not exposed,** because the mechanics live in code and adding a setting would be a new mechanic:
  - catalyst windows;
  - the skill reduction of the failure chance;
  - the spoil timer.

## 4. The browser, editor and preview

- **Filters.** Search; effect (the effects the listed brews actually apply); equipment; difficulty; enabled.
- **Row badges.** ✕ means disabled; • means an override is in force.
- **Page.**
  - Header: bottle icon, name, difficulty, tier, heat time and failure chance.
  - The enabled row.
  - The effect editor.
  - Preview.
  - Recipe facts: ingredients, effective heat time and failure after the global rules, catalyst.
  - The recipe's rows, each ingredient row labelled with its item.
  - Components.
- **Effect editor** (`EffectListEditor`, reusable for any `BrewEffectText` setting). Each row has an effect
  selector (the client's mob-effect registry), duration, amplifier, enabled and remove. "+ Add effect" picks an
  effect not already in the list. Every edit writes the whole list as the setting's draft, and Apply sends it
  through the normal change path. The server re-parses it, so the editor is a convenience, not a trust boundary.
- **Preview.** Drawn on the admin's client from the draft rows and never applied to anyone: the real bottle stack
  (icon and its own tooltip), then each enabled effect with its mob-effect sprite, level, and duration at base
  potency.

## 5. Server authority

- **Every change is an `AdminChangeSettingC2SPayload`,** checked by `AdminSettingService`:
  - the reader's capability;
  - the id exists (an unknown brew, recipe, property or ingredient is `UNKNOWN_SETTING`);
  - parse and bounds;
  - validators;
  - confirmation for dangerous changes.
- **Reads.** The list and detail payloads are answered only for administrators with the content capability.
- **Players cannot spoof potion values.**
  - A bottle carries only a brew id, and the drink reads the server's registry.
  - A forged bottle naming no real brew does nothing.
  - The brew sync only travels server → client.

## 6. How a new brew or recipe becomes editable

Add it as the game already requires, with a `brews/<id>.json` or `brewing_recipes/<id>.json` in any datapack.
Nothing else is needed:

- it appears in the browser;
- `brew/<ns>/<id>/enabled` exists;
- `brew/<ns>/<id>/effects` exists if its drink-time effects are one `apply_effects` component (or a legacy
  `effects` list);
- its recipe's heat time, failure chance, tier and each ingredient count are editable;
- the preview draws its bottle and effects.

The texts are shared per property (`admin.wizards_and_beasts.brew_property.*`,
`admin.wizards_and_beasts.brew_recipe_property.*`), so no lang keys are needed.

**Adding a component type** (`BrewEffect`) needs no admin change. It shows in the component list, and it stays
read-only unless it is `apply_effects`.

## 7. Commands

```
/wandb admin brew list [filter]
/wandb admin brew info <brew>                                 (prints every editable id for this brew)
/wandb admin config set wizards_and_beasts:brew/wizards_and_beasts/amortentia/enabled false
/wandb admin config set wizards_and_beasts:brew/wizards_and_beasts/pepperup_potion/effects minecraft:speed 1200 1; minecraft:fire_resistance 600 0
/wandb admin config set wizards_and_beasts:brew_recipe/wizards_and_beasts/felix_felicis/ingredient.minecraft.gold_ingot 3
/wandb admin config set wizards_and_beasts:brew_speed_multiplier 2      (asks /wandb admin config confirm)
```

## 8. Tests

- **JUnit**
  - `BrewTuningTest`:
    - effect text parse and format, and every invalid form;
    - override replaces the one effect list and keeps the other components;
    - with no overrides the registry holds the authored brew;
    - enabled;
    - two effect lists means not editable;
    - recipe overrides keep ingredients and output;
    - shadow detection, including cauldron tier;
    - codec round trips.
  - `AdminBrewPayloadCodecTest`: list and detail round trip; setting ids and their lang keys, including the
    ingredient dot rule.
  - `AdminCatalogLangTest`, `CommandTreeShapeTest`.
- **GameTest** (`AdminBrewTests`, 5 scenarios)
  - A disabled brew:
    - is refused at a real cauldron with nothing consumed;
    - does nothing when drunk, and the bottle is kept.
  - With brewing off, every start is refused; re-enabled, the brew starts.
  - Invalid effect lists, the empty list on an effect-only brew, and non-editable brews are all refused, and the
    list is unchanged.
  - An edited effect list:
    - needs confirmation;
    - is what a drink applies, and disabled rows do not apply;
    - a forged bottle applies nothing;
    - reset restores the datapack's list.
  - Recipes:
    - out-of-range or unknown values are refused;
    - a changed recipe keeps its shape, still brews, and uses its new heat time;
    - every recipe keeps its ingredients and output.
  - A non-admin cannot change or read brews.
- **Client capture** (`WB_ADMIN_CAPTURE_PLAN=brew`, 8 shots):
  - page with editor and preview;
  - an added effect held for confirmation by the server;
  - recipe rows;
  - a component-built brew (Felix);
  - filter;
  - rules.
