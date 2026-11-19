package at.koopro.wizardsandbeasts.creature.variant;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coats declared in creature JSON ({@link Coats}): the codec, the variant list the roll and the admin rules read,
 * and that every declared coat ships its texture (and its glow mask where the base glows). A coat whose file is
 * missing renders as the purple-and-black missing texture, and nothing else would notice.
 */
class CreatureCoatsTest {

    private static final Path DEFS = Path.of("src/main/resources/data/wizards_and_beasts/creatures");
    private static final Path TEX = Path.of("src/main/resources/assets/wizards_and_beasts/textures/entity");

    @Test
    void theCodecReadsCoatsAndTheBaseComesFirst() {
        JsonElement json = JsonParser.parseString("""
                {"base_weight": 98, "variants": [{"id": "smoke", "weight": 2, "origin": "design", "note": "n"}]}""");
        Coats coats = Coats.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        List<Coats.DataCoat> variants = coats.asVariants("kneazle");
        assertEquals(2, variants.size());
        assertEquals("base", variants.get(0).variantId());
        assertNull(variants.get(0).variantTexture(), "the base coat is the creature's own texture");
        assertEquals(98, variants.get(0).authoredWeight());
        assertEquals("smoke", variants.get(1).variantId());
        assertEquals(1, variants.get(1).ordinal());
        assertEquals("kneazle/smoke", variants.get(1).variantTexture());
        assertEquals(2, variants.get(1).authoredWeight());
    }

    @Test
    void noCoatsMeansNoVariants() {
        assertTrue(Coats.NONE.asVariants("unicorn").isEmpty());
    }

    @Test
    void everyDeclaredCoatShipsItsTextureAndGlow() throws IOException {
        List<String> problems = new ArrayList<>();
        int declared = 0;
        try (Stream<Path> files = Files.list(DEFS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject def = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!def.has("coats")) {
                    continue;
                }
                String id = file.getFileName().toString().replace(".json", "");
                Coats coats = Coats.CODEC.parse(JsonOps.INSTANCE, def.get("coats")).getOrThrow();
                Set<String> origins = Set.of("canon", "design");
                boolean glows = Files.exists(TEX.resolve(id + "_glowmask.png"));
                BufferedImage base = ImageIO.read(TEX.resolve(id + ".png").toFile());
                for (Coats.Coat coat : coats.variants()) {
                    declared++;
                    Path png = TEX.resolve(id).resolve(coat.id() + ".png");
                    if (!Files.exists(png)) {
                        problems.add(id + ": coat " + coat.id() + " has no " + png);
                        continue;
                    }
                    BufferedImage img = ImageIO.read(png.toFile());
                    if (img.getWidth() != base.getWidth() || img.getHeight() != base.getHeight()) {
                        problems.add(id + ": coat " + coat.id() + " is not the base sheet's size (UVs would not line up)");
                    }
                    if (glows && !Files.exists(TEX.resolve(id).resolve(coat.id() + "_glowmask.png"))) {
                        problems.add(id + ": coat " + coat.id() + " lacks the glow mask the base has");
                    }
                    if (!origins.contains(coat.origin())) {
                        problems.add(id + ": coat " + coat.id() + " origin '" + coat.origin() + "' is not canon or design");
                    }
                    if (coat.weight() <= 0) {
                        problems.add(id + ": coat " + coat.id() + " can never be rolled (weight " + coat.weight() + ")");
                    }
                }
            }
        }
        assertTrue(declared > 0, "no creature declares coats");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
