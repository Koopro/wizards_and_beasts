package at.koopro.wizardsandbeasts.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every item, block, entity and tag that a shipped recipe, loot table or tag file names has to exist.
 *
 * <p>None of these fail loudly. A recipe whose ingredient id does not resolve is dropped by the recipe
 * manager with one log line and the item becomes uncraftable; a loot entry for a missing item drops nothing;
 * a tag value that is not registered is skipped (or, for a non-optional entry, fails the whole tag load).
 * The hand-written data and the datagen output are checked together because both ship in the jar.
 */
class DataReferenceIntegrityTest {

    private static final String NS = "wizards_and_beasts";
    private static final List<Path> DATA_ROOTS = List.of(
            Path.of("src", "main", "resources", "data"),
            Path.of("src", "generated", "resources", "data"));

    private static final Pattern IDENTIFIER = Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9_./-]+");
    /**
     * Recipe fields whose string value is a label, a shape or a datapack key (wood, core, brew), not an
     * item id. The brew key is checked against {@code brews/} by the brew tests.
     */
    private static final Set<String> NON_ID_KEYS = Set.of("type", "category", "group", "pattern",
            "neoforge:ingredient_type", "wood_key", "core_key", "wizards_and_beasts:brew_id",
            // a tipped arrow's potion_contents names a potion, not an item (pukwudgie_venom_arrows)
            "potion");

    private static List<Path> files(String subdir) throws IOException {
        List<Path> out = new ArrayList<>();
        for (Path root : DATA_ROOTS) {
            Path dir = root.resolve(subdir);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(p -> p.toString().endsWith(".json")).forEach(out::add);
            }
        }
        return out;
    }

    private static JsonElement parse(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader);
        }
    }

    private static boolean dataFileExists(String kind, Identifier id) {
        if (!id.getNamespace().equals(NS)) {
            return true;
        }
        for (Path root : DATA_ROOTS) {
            if (Files.exists(root.resolve(NS).resolve(kind).resolve(id.getPath() + ".json"))) {
                return true;
            }
        }
        return false;
    }

    private static void collectStrings(String key, JsonElement e, Map<String, List<String>> out) {
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            out.computeIfAbsent(key, k -> new ArrayList<>()).add(e.getAsString());
        } else if (e.isJsonArray()) {
            for (JsonElement child : e.getAsJsonArray()) {
                collectStrings(key, child, out);
            }
        } else if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> child : e.getAsJsonObject().entrySet()) {
                collectStrings(child.getKey(), child.getValue(), out);
            }
        }
    }

    @Test
    void everyRecipeIngredientAndResultResolves() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path file : files("wizards_and_beasts/recipe")) {
            Map<String, List<String>> strings = new java.util.LinkedHashMap<>();
            collectStrings("", parse(file), strings);
            for (Map.Entry<String, List<String>> entry : strings.entrySet()) {
                if (NON_ID_KEYS.contains(entry.getKey())) {
                    continue;
                }
                for (String value : entry.getValue()) {
                    if (!IDENTIFIER.matcher(value).matches()) {
                        continue;
                    }
                    if (value.startsWith("#")) {
                        Identifier tag = Identifier.parse(value.substring(1));
                        if (!dataFileExists("tags/item", tag)) {
                            missing.add(file.getFileName() + ": item tag " + value);
                        }
                    } else if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse(value))) {
                        missing.add(file.getFileName() + ": item " + value);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "recipes naming things that are not registered items: " + missing);
    }

    private static void checkLootEntries(Path file, JsonElement e, List<String> missing) {
        if (e.isJsonArray()) {
            for (JsonElement child : e.getAsJsonArray()) {
                checkLootEntries(file, child, missing);
            }
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            String type = o.has("type") && o.get("type").isJsonPrimitive() ? o.get("type").getAsString() : "";
            if (o.has("name") && o.get("name").isJsonPrimitive()) {
                String name = o.get("name").getAsString();
                if (type.equals("minecraft:item") && !BuiltInRegistries.ITEM.containsKey(Identifier.parse(name))) {
                    missing.add(file.getFileName() + ": loot item " + name);
                } else if (type.equals("minecraft:loot_table")
                        && !dataFileExists("loot_table", Identifier.parse(name))) {
                    missing.add(file.getFileName() + ": nested loot table " + name);
                }
            }
            for (JsonElement child : o.asMap().values()) {
                checkLootEntries(file, child, missing);
            }
        }
    }

    @Test
    void everyLootEntryResolves() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Path file : files("wizards_and_beasts/loot_table")) {
            checkLootEntries(file, parse(file), missing);
        }
        assertTrue(missing.isEmpty(), "loot tables naming things that do not exist: " + missing);
    }

    private static void checkTags(String kind, Registry<?> registry, List<String> missing) throws IOException {
        List<Path> tagFiles = new ArrayList<>(files("wizards_and_beasts/tags/" + kind));
        // Our own blocks and items are also added to vanilla tags (mineable/axe, ...).
        tagFiles.addAll(files("minecraft/tags/" + kind));
        for (Path file : tagFiles) {
            JsonObject tag = parse(file).getAsJsonObject();
            JsonArray values = tag.has("values") ? tag.getAsJsonArray("values") : new JsonArray();
            for (JsonElement value : values) {
                String id;
                boolean required = true;
                if (value.isJsonObject()) {
                    id = value.getAsJsonObject().get("id").getAsString();
                    required = !value.getAsJsonObject().has("required")
                            || value.getAsJsonObject().get("required").getAsBoolean();
                } else {
                    id = value.getAsString();
                }
                if (!required) {
                    continue;
                }
                if (id.startsWith("#")) {
                    if (!dataFileExists("tags/" + kind, Identifier.parse(id.substring(1)))) {
                        missing.add(kind + "/" + file.getFileName() + ": tag " + id);
                    }
                } else if (!registry.containsKey(Identifier.parse(id))) {
                    missing.add(kind + "/" + file.getFileName() + ": " + id);
                }
            }
        }
    }

    @Test
    void everyRequiredTagValueResolves() throws IOException {
        List<String> missing = new ArrayList<>();
        checkTags("item", BuiltInRegistries.ITEM, missing);
        checkTags("block", BuiltInRegistries.BLOCK, missing);
        checkTags("entity_type", BuiltInRegistries.ENTITY_TYPE, missing);
        assertTrue(missing.isEmpty(), "tags with required values that are not registered: " + missing);
    }
}
