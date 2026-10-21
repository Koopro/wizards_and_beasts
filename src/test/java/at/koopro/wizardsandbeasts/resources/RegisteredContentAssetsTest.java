package at.koopro.wizardsandbeasts.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Everything the mod registers has the assets the client needs to show it.
 *
 * <p>Asset gaps are silent on the server and only show up in someone's inventory: an item with no
 * {@code items/<id>.json} definition is the purple-black cube, a block with no blockstate renders as the
 * missing model, and a name with no lang entry is a raw {@code item.wizards_and_beasts.foo} string.
 * Hand-written and generated assets are read together because both ship.
 */
class RegisteredContentAssetsTest {

    private static final String NS = "wizards_and_beasts";
    private static final List<Path> ASSET_ROOTS = List.of(
            Path.of("src", "main", "resources", "assets", NS),
            Path.of("src", "generated", "resources", "assets", NS));

    private static final JsonObject LANG = new JsonObject();

    @BeforeAll
    static void readLang() throws IOException {
        for (Path root : ASSET_ROOTS) {
            Path file = root.resolve("lang").resolve("en_us.json");
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonParser.parseReader(reader).getAsJsonObject().asMap().forEach(LANG::add);
                }
            }
        }
    }

    private static boolean asset(String dir, String path) {
        return ASSET_ROOTS.stream().anyMatch(r -> Files.exists(r.resolve(dir).resolve(path + ".json")));
    }

    private static List<Identifier> ours(Registry<?> registry) {
        List<Identifier> out = new ArrayList<>();
        for (Identifier id : registry.keySet()) {
            if (id.getNamespace().equals(NS)) {
                out.add(id);
            }
        }
        // An empty list would make every check below vacuous (registries not bound in this harness).
        assertFalse(out.isEmpty(), "no " + NS + " entries in " + registry.key());
        return out;
    }

    @Test
    void everyItemHasADefinitionAndAName() {
        List<String> problems = new ArrayList<>();
        for (Identifier id : ours(BuiltInRegistries.ITEM)) {
            if (!asset("items", id.getPath())) {
                problems.add("no items/" + id.getPath() + ".json");
            }
            if (!LANG.has("item." + NS + "." + id.getPath().replace('/', '.'))
                    && !LANG.has("block." + NS + "." + id.getPath().replace('/', '.'))) {
                problems.add("no lang name for " + id);
            }
        }
        assertTrue(problems.isEmpty(), "items missing client assets: " + problems);
    }

    @Test
    void everyBlockHasABlockstate() {
        List<String> problems = new ArrayList<>();
        for (Identifier id : ours(BuiltInRegistries.BLOCK)) {
            if (!asset("blockstates", id.getPath())) {
                problems.add(id.toString());
            }
        }
        assertTrue(problems.isEmpty(), "blocks with no blockstate (render as the missing model): " + problems);
    }

    @Test
    void everyEntityTypeHasAName() {
        List<String> problems = new ArrayList<>();
        for (Identifier id : ours(BuiltInRegistries.ENTITY_TYPE)) {
            if (!LANG.has("entity." + NS + "." + id.getPath())) {
                problems.add(id.toString());
            }
        }
        assertTrue(problems.isEmpty(), "entity types with no lang name (raw key in death messages): " + problems);
    }
}
