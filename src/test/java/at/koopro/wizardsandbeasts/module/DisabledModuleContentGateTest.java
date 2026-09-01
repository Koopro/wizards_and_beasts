package at.koopro.wizardsandbeasts.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A module that ships off must not leave a half-finished loop reachable in survival.
 *
 * <p>Three separate things have to hold, and only the first was ever checked:
 * <ol>
 *   <li>worldgen is gated — {@code AzkabanStructureGateTest} and
 *       {@code ChamberOfSecretsStructureGateTest} cover that;</li>
 *   <li>no recipe produces an item the disabled module owns without carrying the
 *       {@code wizards_and_beasts:module_enabled} condition for it;</li>
 *   <li>the two structure modules own no items at all, so no crafting loop can point at them
 *       however the recipes are written.</li>
 * </ol>
 *
 * <p>Datapack-level and pure file I/O: no Minecraft bootstrap, and it reads exactly the files the
 * game reads. The creative-tab side is enforced in code by {@code ModuleContentIndex} and needs no
 * per-item test.
 */
class DisabledModuleContentGateTest {

    private static final Path RECIPES =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "recipe");
    /** Datagen output, committed: {@code module/<name>.json} per registry. */
    private static final Path ITEM_MODULE_TAGS =
            Path.of("src", "generated", "resources", "data", "wizards_and_beasts", "tags", "item", "module");
    private static final Path AUTHORED_ADVANCEMENTS =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "advancement");

    private static final String CONDITION = "wizards_and_beasts:module_enabled";

    /** The four the brief names, and the four that actually ship off. */
    private static Set<Module> disabledModules() {
        return Stream.of(Module.values())
                .filter(module -> ModuleDefaults.shipped(module) == ModuleState.DISABLED
                        || ModuleDefaults.shipped(module) == ModuleState.COMING_SOON)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** item id -> owning module, read from the committed datagen tags. */
    private static Map<String, Module> itemOwners() throws IOException {
        Map<String, Module> owners = new HashMap<>();
        if (!Files.isDirectory(ITEM_MODULE_TAGS)) {
            return owners;
        }
        try (Stream<Path> files = Files.list(ITEM_MODULE_TAGS)) {
            for (Path tag : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String slug = tag.getFileName().toString().replace(".json", "");
                Module module = Module.valueOf(slug.toUpperCase(Locale.ROOT));
                JsonObject json = JsonParser.parseString(Files.readString(tag)).getAsJsonObject();
                JsonArray values = json.getAsJsonArray("values");
                if (values == null) {
                    continue;
                }
                for (JsonElement value : values) {
                    String id = value.isJsonObject()
                            ? value.getAsJsonObject().get("id").getAsString()
                            : value.getAsString();
                    owners.put(id, module);
                }
            }
        }
        return owners;
    }

    private static String resultId(JsonObject recipe) {
        JsonElement result = recipe.get("result");
        if (result == null) {
            return null;
        }
        if (result.isJsonObject()) {
            JsonElement id = result.getAsJsonObject().get("id");
            return id == null ? null : id.getAsString();
        }
        return result.getAsString();
    }

    private static boolean gatedOn(JsonObject recipe, Module module) {
        JsonArray conditions = recipe.getAsJsonArray("neoforge:conditions");
        if (conditions == null) {
            return false;
        }
        for (JsonElement condition : conditions) {
            JsonObject object = condition.getAsJsonObject();
            JsonElement type = object.get("type");
            JsonElement named = object.get("module");
            if (type != null && CONDITION.equals(type.getAsString())
                    && named != null && module.name().equals(named.getAsString())) {
                return true;
            }
        }
        return false;
    }

    // -- the invariants --------------------------------------------------------------------------

    /**
     * The one that would strand a player: a craftable item whose module is off does nothing when
     * crafted, and the recipe book still teaches it.
     */
    @Test
    void noRecipeProducesDisabledModuleContentWithoutItsGate() throws IOException {
        Map<String, Module> owners = itemOwners();
        Set<Module> disabled = disabledModules();
        List<String> problems = new ArrayList<>();

        try (Stream<Path> files = Files.walk(RECIPES)) {
            for (Path recipePath : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject recipe = JsonParser.parseString(Files.readString(recipePath)).getAsJsonObject();
                String result = resultId(recipe);
                if (result == null) {
                    continue;
                }
                Module owner = owners.get(result);
                if (owner == null || !disabled.contains(owner)) {
                    continue;
                }
                if (!gatedOn(recipe, owner)) {
                    problems.add(recipePath.getFileName() + " makes " + result + " (module " + owner
                            + ", ships off) with no " + CONDITION + " condition");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * Azkaban and the Chamber are structure-only. They own no item, so no crafting loop can point at
     * them at all — which is why gating their worldgen is sufficient rather than merely necessary.
     */
    @Test
    void theTwoPlaceholderStructureModulesOwnNoItems() throws IOException {
        Map<String, Module> owners = itemOwners();
        for (Module module : List.of(Module.AZKABAN, Module.CHAMBER_OF_SECRETS)) {
            List<String> owned = owners.entrySet().stream()
                    .filter(entry -> entry.getValue() == module)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            assertTrue(owned.isEmpty(), module + " unexpectedly owns items: " + owned
                    + " — a craftable loop into a structure module that ships off");
        }
    }

    /** Hand-authored progression must not hand a player a goal their install cannot reach. */
    @Test
    void noAuthoredAdvancementRequiresDisabledModuleContent() throws IOException {
        Map<String, Module> owners = itemOwners();
        Set<Module> disabled = disabledModules();
        List<String> problems = new ArrayList<>();

        try (Stream<Path> files = Files.walk(AUTHORED_ADVANCEMENTS)) {
            for (Path advancement : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String body = Files.readString(advancement);
                for (Map.Entry<String, Module> entry : owners.entrySet()) {
                    if (disabled.contains(entry.getValue()) && body.contains("\"" + entry.getKey() + "\"")) {
                        problems.add(advancement.getFileName() + " references " + entry.getKey()
                                + " (module " + entry.getValue() + ", ships off)");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /**
     * The shipped states themselves, asserted so a change to {@code ModuleDefaults} has to be
     * deliberate. `DISABLED` keeps an operator able to switch them on; `COMING_SOON` would not.
     */
    @Test
    void theFourNamedModulesShipDisabledAndOperatorReachable() {
        for (Module module : List.of(Module.AZKABAN, Module.CHAMBER_OF_SECRETS,
                Module.MINISTRY, Module.DARK_ARTS)) {
            assertEquals(ModuleState.DISABLED, ModuleDefaults.shipped(module),
                    module + " must ship DISABLED — see KNOWN_ISSUES for why");
        }
    }
}
