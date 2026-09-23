package at.koopro.wizardsandbeasts.item;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every item the game hands to GeckoLib finds a model to draw.
 *
 * <p>An item definition that dispatches to {@code geckolib:geckolib} with no geo file behind it
 * renders as nothing in hand — no checkerboard, no log line a player would see — and
 * {@code AnimatedItem} makes that one line of Java away: implement the interface, run datagen, and
 * the definition exists whether or not {@code tools/item_geo.py} was ever run for the item.
 *
 * <p>Read off the item definitions rather than the registry, because those are what the game loads
 * and because a unit test has no bound registry to ask.
 */
class AnimatedItemAssetsTest {

    private static final Path ASSETS = Path.of("src", "main", "resources", "assets", "wizards_and_beasts");
    private static final List<Path> DEFINITIONS = List.of(
            ASSETS.resolve("items"),
            Path.of("src", "generated", "resources", "assets", "wizards_and_beasts", "items"));

    private static List<String> geckolibItems() throws IOException {
        List<String> ids = new ArrayList<>();
        for (Path dir : DEFINITIONS) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    if (Files.readString(file).contains("\"geckolib:geckolib\"")) {
                        String name = file.getFileName().toString();
                        ids.add(name.substring(0, name.length() - ".json".length()));
                    }
                }
            }
        }
        return ids;
    }

    @Test
    void someItemsAreDrawnByGeckolib() throws IOException {
        assertFalse(geckolibItems().isEmpty(), "no item definition dispatches to geckolib");
    }

    @Test
    void everyGeckolibItemHasItsModelAnimationAndTexture() throws IOException {
        for (String id : geckolibItems()) {
            assertTrue(Files.exists(ASSETS.resolve("geckolib/models/item/" + id + ".geo.json")),
                    id + " is drawn by GeckoLib but has no geckolib/models/item/" + id + ".geo.json");
            // AnimatedItem reads its texture from textures/item/model/; the older bespoke renderers
            // (wand, coins, deluminator, map) use GeckoLib's default textures/item/<id>.png.
            boolean animated = Files.exists(ASSETS.resolve("textures/item/model/" + id + ".png"));
            assertTrue(animated || Files.exists(ASSETS.resolve("textures/item/" + id + ".png")),
                    id + " has a model but no texture");
            if (animated) {
                assertTrue(Files.exists(ASSETS.resolve("geckolib/animations/item/" + id + ".animation.json")),
                        id + " loops 'idle' but has no geckolib/animations/item/" + id + ".animation.json");
            }
        }
    }
}
