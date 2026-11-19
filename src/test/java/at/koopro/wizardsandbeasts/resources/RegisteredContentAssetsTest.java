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

    /**
     * Every model a blockstate or item definition of ours names exists, and so does every texture (and parent) such a
     * model names in our namespace. A blockstate that points at a missing model renders as the purple cube just as
     * surely as a missing blockstate, and nothing on the server notices.
     */
    @Test
    void everyModelOurAssetsNameExistsWithItsTextures() throws IOException {
        List<String> problems = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String dir : List.of("blockstates", "items")) {
            for (Path root : ASSET_ROOTS) {
                Path folder = root.resolve(dir);
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                try (var files = Files.list(folder)) {
                    for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                        try (Reader reader = Files.newBufferedReader(file)) {
                            List<String> models = new ArrayList<>();
                            collectModels(JsonParser.parseReader(reader), models);
                            for (String model : models) {
                                checkModel(model, dir + "/" + file.getFileName(), seen, problems);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "asset references that resolve to nothing: " + problems);
    }

    private static void collectModels(com.google.gson.JsonElement json, List<String> out) {
        if (json.isJsonObject()) {
            for (var entry : json.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()) {
                    out.add(entry.getValue().getAsString());
                } else {
                    collectModels(entry.getValue(), out);
                }
            }
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(e -> collectModels(e, out));
        }
    }

    private static void checkModel(String model, String from, java.util.Set<String> seen, List<String> problems)
            throws IOException {
        if (!model.startsWith(NS + ":") || !seen.add(model)) {
            return;
        }
        String path = model.substring(NS.length() + 1);
        Path file = ASSET_ROOTS.stream().map(r -> r.resolve("models").resolve(path + ".json"))
                .filter(Files::exists).findFirst().orElse(null);
        if (file == null) {
            // GeckoLib-drawn and builtin item models carry no JSON of their own on purpose.
            if (!ASSET_ROOTS.stream().anyMatch(r -> Files.exists(r.resolve("geckolib")))
                    || !path.startsWith("item/")) {
                problems.add(from + " -> missing model " + model);
            }
            return;
        }
        com.google.gson.JsonObject json;
        try (Reader reader = Files.newBufferedReader(file)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (json.has("parent")) {
            checkModel(json.get("parent").getAsString(), model, seen, problems);
        }
        if (json.has("textures")) {
            for (var entry : json.getAsJsonObject("textures").entrySet()) {
                String texture = entry.getValue().getAsString();
                if (texture.startsWith(NS + ":")) {
                    String tex = texture.substring(NS.length() + 1);
                    if (ASSET_ROOTS.stream().noneMatch(r -> Files.exists(r.resolve("textures").resolve(tex + ".png")))) {
                        problems.add(model + " -> missing texture " + texture);
                    }
                }
            }
        }
    }
}
