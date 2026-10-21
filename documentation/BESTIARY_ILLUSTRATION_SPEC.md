# Bestiary Illustration Specification

How every Field Journal plate is made, so a plate added next year sits beside one made today. Written 2026-10-07
with the journal. Code: `bestiary/journal/IllustrationStage`, `JournalArt`; generator: `tools/bestiary_journal_art.py`;
check: `JournalDataTest`.

## 1. Stages

A plate shows only what the player has earned. One stage per depth of knowledge; an undiscovered creature has no
plate — not even its outline.

| Stage | Depth | Reads as | Content |
|---|---|---|---|
| `silhouette` | Encountered | "a shape glimpsed" | the body as flat ink, edges bitten so it reads drawn, not rendered; no interior detail |
| `sketch` | Observed | "field sketch" | graphite contour and hatching only; no colour; shadowed areas hatched, lit areas left as paper |
| `portrait` | Studied | "portrait" | muted colour wash over the paper with an ink line; true colours of the creature, desaturated ~30% |
| `studied` | Known | "finished plate" | the portrait plus the naturalist's annotation: a scale bar with ticks under the body, three leader ticks to the margin, mounting corner marks |
| `signature` | Known, with a signature | caption under it | the creature smaller (⅔), beside the **motif** of its signature behaviour (§4) |

The journal falls back stage by stage: a missing `studied` shows the `portrait`, then the `sketch`… A studied page
with no painted plate at all shows the live creature model instead; below that, the pre-journal 32 px icon and
silhouette, drawn at the honesty of the stage (ink for a silhouette, graphite tint for a sketch, colour from studied).

## 2. Format

| Property | Value |
|---|---|
| Size | **96 × 96 px**, drawn 1:1 into the plate well |
| Mode | RGBA, **transparent background** — the journal's kraft inset is the paper |
| Path | `assets/wizards_and_beasts/textures/gui/bestiary/journal/<stage>/<entry id>.png` |
| Override | an entry's `journal.art` map names any other texture for a stage |
| Caption | `journal.signature_caption` → a lang key, shown under the signature plate |
| View | the creature's in-game 3/4 view (`beast_preview.py` yaw/pitch), 10 px margin |

## 3. Palette

The `field_notebook` GUI skin's own colours (`WizardsPalette.GuiSkin.FIELD_NOTEBOOK`), never the dark-HUD palette:

| Use | Colour |
|---|---|
| Paper (supplied by the GUI) | `#CDBC95` |
| Ink (silhouette, contour, annotation) | `#2B2B2B` |
| Pencil (sketch) | `#3E3C39` — darker than the skin's muted `#555350`, which sits under 3 : 1 on the kraft |
| Graphite (secondary marks) | `#555350` |
| Gold (coins, gilt) | `#C9973A` |
| Silver (moonlight, unicorn hair) | `#C4C9D2` |

Portrait colour: the creature's own skin, desaturated ~30%, a light paper-tone wash, quantised to ≤ 14 colours.

## 4. Signature motifs

A motif is the one image a reader would recognise the behaviour by, drawn small and to one side of the creature.

| Creature | Motif |
|---|---|
| Niffler | coins scattered on the ground under it |
| Bowtruckle | a padlock, and the twig reaching into it |
| Unicorn | a crescent moon, and silver hair strands left behind |
| Mooncalf | a full moon, and the dancing rings in the grass |
| Hippogriff | a small figure bowing low before it |

A new motif is a branch in `bestiary_journal_art.py`'s `signature()` and a row here.

## 5. Making plates

```
python tools/bestiary_journal_art.py                 # every creature in MOTIFS, skipping files it does not own
python tools/bestiary_journal_art.py --only unicorn  # one creature
python tools/bestiary_journal_art.py --force         # repaint even hand-made files (careful)
```

Generated files carry the `wizards_and_beasts/tools/bestiary_journal_art.py` PNG marker. A hand-painted plate saved
without that marker is left alone by the generator; it must still meet §2 (`JournalDataTest` checks size and alpha
for the showcase entries). Adding a creature: add it to `MOTIFS`, run the tool, give its entry a `signature_caption`.

## 6. Shipped

Five showcase entries carry the full set (25 plates): Niffler, Bowtruckle, Unicorn, Mooncalf, Hippogriff. Every
other creature uses the fallback chain of §1 until its plates are made.
