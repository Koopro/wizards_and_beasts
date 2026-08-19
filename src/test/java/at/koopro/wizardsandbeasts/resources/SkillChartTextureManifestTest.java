package at.koopro.wizardsandbeasts.resources;

import at.koopro.wizardsandbeasts.client.skill.gui.SkillTreeChartTextures;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every texture the skill chart names must exist on disk.
 *
 * <p>A bad texture id is silent: the game renders the missing-texture magenta and logs nothing the
 * player or a test would notice, so a typo, a dropped {@code textures/} prefix or a sprite the
 * generator stopped emitting survives all the way to a screenshot. This walks the ids instead of
 * trusting them — the chart's own sprite set by reflection, and the {@code star_chart} skin pieces
 * the screen blits through {@code McStylePanel} by name.
 */
class SkillChartTextureManifestTest {

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets");

    /** The skin elements {@code SkillTreeScreen} and {@code SkillTreeRenderHelper} actually draw. */
    private static final List<String> STAR_CHART_ELEMENTS = List.of(
            "panel", "panel_inset", "divider", "seal", "button", "button_hover", "button_off");

    @Test
    void chartSpriteConstants_resolveToFilesOnDisk() throws IllegalAccessException {
        Set<Identifier> ids = new LinkedHashSet<>();
        for (Field field : SkillTreeChartTextures.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == Identifier.class) {
                field.setAccessible(true);
                ids.add((Identifier) field.get(null));
            }
        }
        // The per-size and per-region sprites live in private maps, so they are reached through the
        // accessors rather than the field scan.
        for (Skill.Size size : Skill.Size.values()) {
            ids.add(SkillTreeChartTextures.core(size));
            ids.add(SkillTreeChartTextures.ring(size));
            ids.add(SkillTreeChartTextures.flare(size));
            ids.add(SkillTreeChartTextures.locked(size));
        }
        for (SkillTreeId tree : SkillTreeId.values()) {
            ids.add(SkillTreeChartTextures.regionGlyph(tree));
        }

        assertFalse(ids.isEmpty(), "reflection found no Identifier constants on SkillTreeChartTextures");
        assertTrue(missing(ids).isEmpty(), "skill chart references missing textures:\n"
                + String.join("\n", missing(ids)));
    }

    @Test
    void starChartSkinElements_existForEveryPieceTheScreenDraws() {
        List<String> missing = new ArrayList<>();
        for (String element : STAR_CHART_ELEMENTS) {
            Path path = ASSETS.resolve("wizards_and_beasts")
                    .resolve("textures").resolve("gui").resolve("sprites")
                    .resolve("star_chart").resolve(element + ".png");
            if (!Files.exists(path)) {
                missing.add(path.toString());
            }
        }
        assertTrue(missing.isEmpty(), "star_chart skin is missing pieces the skill chart draws:\n"
                + String.join("\n", missing));
    }

    private static List<String> missing(Set<Identifier> ids) {
        List<String> missing = new ArrayList<>();
        for (Identifier id : ids) {
            Path path = ASSETS.resolve(id.getNamespace()).resolve(id.getPath());
            if (!Files.exists(path)) {
                missing.add(id + " -> " + path);
            }
        }
        return missing;
    }
}
