package at.koopro.wizardsandbeasts.resources;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GUI skin sprites resolve, and their nine-slice metadata is the shape vanilla accepts.
 *
 * <p>Atlas sprites fail quietly. {@code GuiGraphics#blitSprite} takes a sprite <em>name</em>, not
 * a texture path, and an unknown name resolves to the missing-texture sprite rather than throwing
 * — a typo in a directory renders as magenta checks and nothing in the log says why. The mapping
 * is also indirect: {@code atlases/gui.json} declares source {@code gui/sprites} with an empty
 * prefix, so {@code wizards_and_beasts:field_notebook/panel} comes from
 * {@code textures/gui/sprites/field_notebook/panel.png}. This test is the assertion that the two
 * halves of that mapping still agree.
 *
 * <p>It runs off the filesystem rather than off {@code GuiSkin} on purpose: the enum is client-only
 * and loading it here would drag a {@code Dist.CLIENT} class into the test JVM.
 */
class GuiSkinSpriteParityTest {

    private static final Path SPRITES =
            Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "textures", "gui", "sprites");
    private static final Path ATLAS =
            Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "atlases", "gui.json");

    /** Mirrors {@code WizardsPalette.GuiSkin}'s directory names. */
    private static final List<String> SKINS =
            List.of("field_notebook", "ministry", "star_chart", "goblin_ledger", "workbench");

    /** Mirrors the elements {@code McStylePanel}'s skinned methods ask for. */
    private static final List<String> ELEMENTS =
            List.of("panel", "panel_inset", "divider", "scrollbar_track", "scrollbar_thumb", "seal");

    /** The two that nine-slice. The rest stretch along a uniform axis and need no metadata. */
    private static final List<String> NINE_SLICED = List.of("panel", "panel_inset");

    @Test
    void everySkinShipsEveryElement() {
        List<String> missing = new ArrayList<>();
        for (String skin : SKINS) {
            for (String element : ELEMENTS) {
                Path png = SPRITES.resolve(skin).resolve(element + ".png");
                if (!Files.exists(png)) {
                    missing.add("wizards_and_beasts:" + skin + "/" + element + " -> " + png);
                }
            }
        }
        assertTrue(missing.isEmpty(), "GuiSkin sprites with no file behind them:\n" + String.join("\n", missing));
    }

    @Test
    void stateBadgeExists() {
        assertTrue(Files.exists(SPRITES.resolve("state").resolve("badge.png")),
                "WizardsPalette.STATE_BADGE has no file behind it");
    }

    @Test
    void nineSlicedSpritesCarryValidMetadata() throws IOException {
        List<String> bad = new ArrayList<>();
        for (String skin : SKINS) {
            for (String element : NINE_SLICED) {
                Path meta = SPRITES.resolve(skin).resolve(element + ".png.mcmeta");
                if (!Files.exists(meta)) {
                    bad.add(meta + " -> missing; the sprite will stretch its border instead of cutting it");
                    continue;
                }
                String json = Files.readString(meta).replaceAll("\\s+", "");
                if (!json.contains("\"type\":\"nine_slice\"")) {
                    bad.add(meta + " -> not declared nine_slice");
                }
                // Vanilla's codec rejects a border that leaves no centre slice:
                // left + right >= width. Ours is 8 on 32, so 16 < 32 holds.
                if (!json.contains("\"width\":32") || !json.contains("\"height\":32")
                        || !json.contains("\"border\":8")) {
                    bad.add(meta + " -> not 32x32 cut on 8, which is what the sprites are drawn at");
                }
            }
        }
        assertTrue(bad.isEmpty(), "Nine-slice metadata problems:\n" + String.join("\n", bad));
    }

    @Test
    void atlasSourcesTheSpriteDirectory() throws IOException {
        String json = Files.readString(ATLAS).replaceAll("\\s+", "");
        assertTrue(json.contains("\"source\":\"gui/sprites\""),
                "atlases/gui.json must source gui/sprites, or no skin sprite is stitched at all");
        assertTrue(json.contains("\"prefix\":\"\""),
                "the prefix must be empty, or sprite names gain a segment and every blitSprite call misses");
    }

    @Test
    void noStraySpriteIsShippedUnreferenced() throws IOException {
        List<String> stray = new ArrayList<>();
        try (var walk = Files.walk(SPRITES)) {
            walk.filter(p -> p.toString().endsWith(".png")).forEach(png -> {
                Path rel = SPRITES.relativize(png);
                String dir = rel.getParent() == null ? "" : rel.getParent().toString().replace('\\', '/');
                String element = rel.getFileName().toString().replace(".png", "");
                boolean known = ("state".equals(dir) && "badge".equals(element))
                        || (SKINS.contains(dir) && ELEMENTS.contains(element));
                if (!known) {
                    stray.add(rel.toString());
                }
            });
        }
        assertTrue(stray.isEmpty(),
                "Sprites on the atlas that nothing draws (atlas space is not free):\n" + String.join("\n", stray));
    }
}
