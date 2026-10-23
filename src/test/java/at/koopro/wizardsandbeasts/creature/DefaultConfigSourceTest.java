package at.koopro.wizardsandbeasts.creature;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every creature material can be had <em>at default settings</em>.
 *
 * <p>{@link CreatureMaterialObtainabilityTest} asks whether a source exists anywhere. That passed while eleven
 * materials were unobtainable in a fresh world (Phase 6 audit, 2026-10-23): their only source was the loot table of
 * a creature that never spawns, or a trader behind a module that ships off. This test counts a source only if it
 * works with nothing switched on:
 *
 * <ul>
 *   <li>a living creature -- on the alpha roster (or exempted as progression-critical) <em>and</em> given a spawn
 *       rule -- through its loot table, harvest rules, shedding, grooming or bond gift;</li>
 *   <li>a shop whose location's module ships enabled;</li>
 *   <li>a chest loot table, a global loot modifier or a crafting recipe's result.</li>
 * </ul>
 */
class DefaultConfigSourceTest {

    private static final String NS = "wizards_and_beasts:";
    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    private static final Path JAVA = Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts");

    /**
     * Materials that are off along with everything they are for. The basilisk lives in the Chamber of Secrets and
     * its fang is a Dark Arts artefact (and what impregnates the Sword of Gryffindor); both modules ship off, so at
     * default settings there is nothing to want a fang for.
     */
    private static final Set<String> GATED_WITH_ITS_USE = Set.of("basilisk_fang");

    @Test
    void everyCreatureMaterialCanBeHadAtDefaultSettings() throws IOException {
        Set<String> living = livingCreatures();
        assertFalse(living.isEmpty(), "found no creature that spawns at default settings");
        String sources = defaultSources(living);
        List<String> missing = new ArrayList<>();
        for (String material : CreatureMaterialObtainabilityTest.CREATURE_MATERIALS) {
            if (!GATED_WITH_ITS_USE.contains(material) && !sources.contains('"' + NS + material + '"')) {
                missing.add(material);
            }
        }
        assertEquals(List.of(), missing, "no source at default settings (only a creature that never spawns, or a "
                + "shop behind a module that ships off): " + missing);
    }

    /** On the roster (or progression-critical) and given a spawn rule. */
    private static Set<String> livingCreatures() throws IOException {
        String roster = Files.readString(JAVA.resolve(Path.of("creature", "AlphaRoster.java")));
        Set<String> allowed = new HashSet<>();
        for (String field : List.of("SHIPPED", "PROGRESSION_CRITICAL")) {
            int start = roster.indexOf(field + " = Set.of(");
            String block = roster.substring(start, roster.indexOf(");", start));
            Matcher id = Pattern.compile("\"([a-z_]+)\"").matcher(block.replaceAll("//[^\n]*", ""));
            while (id.find()) {
                allowed.add(id.group(1));
            }
        }
        Set<String> living = new HashSet<>();
        for (String creature : allowed) {
            if (Files.exists(DATA.resolve(Path.of("neoforge", "biome_modifier", "spawn_" + creature + ".json")))) {
                living.add(creature);
            }
        }
        return living;
    }

    private static Set<String> enabledModules() throws IOException {
        String defaults = Files.readString(JAVA.resolve(Path.of("module", "ModuleDefaults.java")));
        Matcher put = Pattern.compile("SHIPPED\\.put\\(Module\\.(\\w+),\\s*ModuleState\\.(\\w+)\\)").matcher(defaults);
        Set<String> on = new HashSet<>();
        while (put.find()) {
            if (!"DISABLED".equals(put.group(2))) {
                on.add(put.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return on;
    }

    private static String defaultSources(Set<String> living) throws IOException {
        StringBuilder all = new StringBuilder();
        for (String creature : living) {
            for (Path file : List.of(
                    DATA.resolve(Path.of("loot_table", "entities", creature + ".json")),
                    DATA.resolve(Path.of("creatures", creature + ".json")),
                    DATA.resolve(Path.of("creature_bonds", creature + ".json")))) {
                if (Files.exists(file)) {
                    all.append(Files.readString(file));
                }
            }
        }
        for (JsonObject rule : jsons(DATA.resolve(Path.of("bestiary", "harvest")))) {
            if (living.contains(rule.get("entry").getAsString().replace(NS, ""))) {
                all.append('"').append(rule.get("item").getAsString()).append('"');
            }
        }
        Set<String> on = enabledModules();
        for (JsonObject location : jsons(DATA.resolve("locations"))) {
            if (location.has("module") && !on.contains(location.get("module").getAsString())) {
                continue;
            }
            if (location.has("market") && location.getAsJsonObject("market").has("sells")) {
                for (JsonElement sell : location.getAsJsonObject("market").getAsJsonArray("sells")) {
                    all.append('"').append(sell.getAsJsonObject().get("item").getAsString()).append('"');
                }
            }
        }
        for (Path dir : List.of(DATA.resolve(Path.of("loot_table", "chests")), DATA.resolve("loot_modifiers"))) {
            for (JsonObject json : jsons(dir)) {
                all.append(json);
            }
        }
        for (JsonObject recipe : jsons(DATA.resolve("recipe"))) {
            if (recipe.has("result")) {
                all.append(recipe.get("result"));
            }
        }
        return all.toString();
    }

    private static List<JsonObject> jsons(Path dir) throws IOException {
        List<JsonObject> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                out.add(JsonParser.parseString(Files.readString(p)).getAsJsonObject());
            }
        }
        return out;
    }
}
