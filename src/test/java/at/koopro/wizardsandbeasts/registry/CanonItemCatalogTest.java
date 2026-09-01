package at.koopro.wizardsandbeasts.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That every catalogued canon stub has the three things it is supposed to have.
 *
 * <p>The registry class, the sprites and the lang keys are all emitted together by
 * {@code tools/canon_items.py}, so they agree the moment they are generated. They do not stay
 * agreed: any of the three can be hand-edited afterwards, and each failure is silent in a
 * different way — a missing sprite is a magenta checkerboard, a missing name key is a raw
 * translation string in the tooltip, and a stub left out of the registry simply does not exist
 * while its sprite and its name sit in the jar unused.
 *
 * <p>Ids are read off the {@link DeferredItem} holders rather than by calling {@code get()},
 * which would need a bound registry and a running game.
 */
class CanonItemCatalogTest {

    private static final Path SPRITES =
            Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "textures", "item");
    private static final Path LANG =
            Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang", "en_us.json");

    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        lang = JsonParser.parseString(Files.readString(LANG)).getAsJsonObject();
    }

    private static List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (DeferredItem<?> stub : CanonItemRegistry.ALL) {
            ids.add(stub.getId().getPath());
        }
        return ids;
    }

    @Test
    void theCatalogueIsNotEmpty() {
        assertFalse(CanonItemRegistry.ALL.isEmpty(), "no canon stubs registered");
    }

    @Test
    void everyStubIsInTheModsOwnNamespace() {
        for (DeferredItem<?> stub : CanonItemRegistry.ALL) {
            Identifier id = stub.getId();
            assertEquals("wizards_and_beasts", id.getNamespace(), id + " is not ours");
        }
    }

    @Test
    void noIdIsRegisteredTwice() {
        List<String> ids = ids();
        Set<String> unique = new LinkedHashSet<>(ids);
        assertEquals(ids.size(), unique.size(),
                "duplicate id in CanonItemRegistry.ALL — a second registration of the same path "
                        + "throws at mod load, long before anything renders");
    }

    @Test
    void everyStubShipsASprite() {
        List<String> missing = new ArrayList<>();
        for (String id : ids()) {
            if (!Files.exists(SPRITES.resolve(id + ".png"))) {
                missing.add(id);
            }
        }
        assertTrue(missing.isEmpty(), "canon stubs with no item sprite: " + missing);
    }

    @Test
    void everyStubIsNamed() {
        List<String> missing = new ArrayList<>();
        for (String id : ids()) {
            if (!lang.has("item.wizards_and_beasts." + id)) {
                missing.add(id);
            }
        }
        assertTrue(missing.isEmpty(), "canon stubs with no display name: " + missing);
    }

    @Test
    void everyStubCitesItsCanonSource() {
        // The .desc key is the tooltip line, and for these it carries the canon citation. It is
        // the only place the source travels with the item, so an entry without one is a stub
        // nobody can trace back to a book.
        List<String> missing = new ArrayList<>();
        for (String id : ids()) {
            if (!lang.has("item.wizards_and_beasts." + id + ".desc")) {
                missing.add(id);
            }
        }
        assertTrue(missing.isEmpty(), "canon stubs with no source line: " + missing);
    }

    @Test
    void noStubCollidesWithAnItemThatAlreadyHasBehaviour() {
        // A stub is a reservation, not a replacement. If one of these ids is later given real
        // behaviour it moves to a proper registry, and leaving the stub behind would register
        // the path twice.
        Set<String> stubs = new LinkedHashSet<>(ids());
        for (String shipped : List.of("marauders_map", "pensieve", "time_turner", "remembrall",
                "foe_glass", "hand_of_glory", "philosophers_stone", "resurrection_stone",
                "invisibility_cloak", "floo_powder", "sneakoscope", "two_way_mirror")) {
            assertFalse(stubs.contains(shipped),
                    shipped + " already ships with behaviour — it must not also be a stub");
        }
    }
}
