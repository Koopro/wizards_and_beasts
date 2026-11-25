package at.koopro.wizardsandbeasts.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every creature the mod registers ships the three files its GeckoLib renderer reads, and they agree.
 *
 * <p>The creature renderers ({@code TintedBeastRenderer}, {@code DragonRenderer} and the bespoke ones) build a
 * {@code DefaultedEntityGeoModel} from the creature id, so the model, the animations and the skin are found by
 * path alone: {@code geckolib/models/entity/<id>.geo.json}, {@code geckolib/animations/entity/<id>.animation.json}
 * and {@code textures/entity/<id>.png}. Nothing checks those paths at registration. A missing file shows up only
 * when the creature is first rendered (an invisible mob or the purple-black square), and a skin painted for a
 * different UV size than the model declares renders with scrambled faces and no error at all.
 *
 * <p>Coat variants under {@code textures/entity/<id>/} are drawn through the same model ({@code CoatedBeastGeoModel}),
 * so they must match the base skin's size. That a declared coat exists at all is {@code CreatureCoatsTest}'s job.
 *
 * <p>Pure file I/O, no Minecraft bootstrap: the roster is read from {@code ModCreatures.java} as the bond and
 * harvest data tests do.
 */
class CreatureAssetParityTest {

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "wizards_and_beasts");
    private static final Path GEO = ASSETS.resolve("geckolib/models/entity");
    private static final Path ANIMATIONS = ASSETS.resolve("geckolib/animations/entity");
    private static final Path SKINS = ASSETS.resolve("textures/entity");
    private static final Path CREATURES_JAVA = Path.of("src", "main", "java", "at", "koopro",
            "wizardsandbeasts", "registry", "ModCreatures.java");

    private static final Pattern SPEC = Pattern.compile("new Spec\\(\"([a-z0-9_]+)\"");
    private static final Pattern BESPOKE = Pattern.compile("BESPOKE_IDS\\s*=\\s*Set\\.of\\(([^)]*)\\)");
    private static final Pattern QUOTED = Pattern.compile("\"([a-z0-9_]+)\"");

    /** {@code ModCreatures.MANIFEST} plus {@code ModCreatures.BESPOKE_IDS}, in source order. */
    private static Set<String> roster() throws IOException {
        String source = Files.readString(CREATURES_JAVA);
        Set<String> ids = new LinkedHashSet<>();
        Matcher spec = SPEC.matcher(source);
        while (spec.find()) {
            ids.add(spec.group(1));
        }
        Matcher bespoke = BESPOKE.matcher(source);
        assertTrue(bespoke.find(), "ModCreatures.BESPOKE_IDS not found; update this test's pattern");
        Matcher quoted = QUOTED.matcher(bespoke.group(1));
        while (quoted.find()) {
            ids.add(quoted.group(1));
        }
        // The roster is ~100 creatures; a handful means the source pattern stopped matching, not that they left.
        assertTrue(ids.size() > 50, "read only " + ids.size() + " creature ids from ModCreatures.java");
        return ids;
    }

    @Test
    void everyCreatureShipsItsModelAnimationsAndSkin() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String id : roster()) {
            Path geo = GEO.resolve(id + ".geo.json");
            Path animations = ANIMATIONS.resolve(id + ".animation.json");
            Path skin = SKINS.resolve(id + ".png");
            if (!Files.exists(geo)) {
                problems.add(id + ": no model at " + geo);
            } else if (geometry(geo).getAsJsonArray("bones") == null
                    || geometry(geo).getAsJsonArray("bones").isEmpty()) {
                problems.add(id + ": model has no bones");
            }
            if (!Files.exists(animations)) {
                problems.add(id + ": no animations at " + animations);
            } else {
                JsonObject anims = JsonParser.parseString(Files.readString(animations)).getAsJsonObject()
                        .getAsJsonObject("animations");
                if (anims == null || anims.isEmpty()) {
                    problems.add(id + ": animation file holds no animations");
                }
            }
            if (!Files.exists(skin)) {
                problems.add(id + ": no skin at " + skin);
            }
        }
        assertTrue(problems.isEmpty(), "creatures that would render wrong or not at all:\n"
                + String.join("\n", problems));
    }

    @Test
    void everySkinFitsTheUvSizeItsModelDeclares() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String id : roster()) {
            Path geo = GEO.resolve(id + ".geo.json");
            Path skin = SKINS.resolve(id + ".png");
            if (!Files.exists(geo) || !Files.exists(skin)) {
                continue; // reported by the test above
            }
            JsonObject description = geometry(geo).getAsJsonObject("description");
            int declaredW = description.has("texture_width") ? description.get("texture_width").getAsInt() : 0;
            int declaredH = description.has("texture_height") ? description.get("texture_height").getAsInt() : 0;
            int[] size = size(skin);
            // A skin may be drawn at a whole multiple of the declared size (HD), never at another shape.
            boolean fits = declaredW > 0 && declaredH > 0
                    && size[0] % declaredW == 0 && size[1] % declaredH == 0
                    && size[0] / declaredW == size[1] / declaredH;
            if (!fits) {
                problems.add(id + ": model declares " + declaredW + "x" + declaredH + " UVs but the skin is "
                        + size[0] + "x" + size[1]);
            }
        }
        assertTrue(problems.isEmpty(), "skins painted for a different UV layout than their model:\n"
                + String.join("\n", problems));
    }

    @Test
    void everyCoatMatchesTheBaseSkinItReplaces() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String id : roster()) {
            Path skin = SKINS.resolve(id + ".png");
            Path coats = SKINS.resolve(id);
            if (!Files.exists(skin) || !Files.isDirectory(coats)) {
                continue;
            }
            int[] base = size(skin);
            List<Path> coatFiles;
            try (Stream<Path> files = Files.list(coats)) {
                coatFiles = files.filter(p -> p.toString().endsWith(".png")).sorted().toList();
            }
            for (Path coat : coatFiles) {
                int[] size = size(coat);
                if (size[0] != base[0] || size[1] != base[1]) {
                    problems.add(id + "/" + coat.getFileName() + ": " + size[0] + "x" + size[1]
                            + " but the base skin is " + base[0] + "x" + base[1]);
                }
            }
        }
        assertTrue(problems.isEmpty(), "coats drawn for a different UV size than the model they wear:\n"
                + String.join("\n", problems));
    }

    private static JsonObject geometry(Path geo) throws IOException {
        JsonArray geometries = JsonParser.parseString(Files.readString(geo)).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry");
        return geometries.get(0).getAsJsonObject();
    }

    /** Width and height from the PNG header, without decoding the pixels. */
    private static int[] size(Path png) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(png.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("not an image: " + png);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }
}
