package at.koopro.wizardsandbeasts.wand.making;

import at.koopro.wizardsandbeasts.creature.AlphaRoster;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every wand core can actually be had in survival -- not merely "appears in some loot table".
 *
 * <p>That weaker check is what let a third of the old per-pair recipes die: thunderbird, troll and wampus cat cores had loot
 * tables and harvest rules, on creatures that never spawn. A core counts as reachable here only if a trader sells it,
 * a chest loot table holds it, or a creature that really spawns (on the alpha roster or progression-critical, with a
 * spawn biome modifier) drops, sheds or gives it (2026-10-22).
 */
class WandCoreReachabilityTest {

    private static final String NS = "wizards_and_beasts:";
    private static final Path DATA = Path.of("src/main/resources/data/wizards_and_beasts");

    @Test
    void everyCoreHasALiveSource() throws IOException {
        // Every core pairs with every wood (WandmakingRules), so every defined core is one a maker may ask for.
        Set<String> cores = new TreeSet<>();
        try (Stream<Path> files = Files.list(DATA.resolve("wizards_and_beasts/wand_cores"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = f.getFileName().toString();
                cores.add(name.substring(0, name.length() - ".json".length()));
            }
        }
        assertEquals(10, cores.size(), "shipped cores: " + cores);

        Set<String> sold = soldItems();
        String chests = textOf(DATA.resolve("loot_table/chests"));
        Set<String> spawning = spawningCreatures();

        TreeMap<String, List<String>> routes = new TreeMap<>();
        for (String core : cores) {
            List<String> how = new ArrayList<>();
            if (sold.contains(NS + core)) {
                how.add("sold");
            }
            if (chests.contains("\"" + NS + core + "\"")) {
                how.add("chest");
            }
            for (String creature : spawning) {
                if (creatureGives(creature, core)) {
                    how.add("creature:" + creature);
                }
            }
            routes.put(core, how);
        }
        List<String> dead = routes.entrySet().stream().filter(e -> e.getValue().isEmpty()).map(e -> e.getKey()).toList();
        assertTrue(dead.isEmpty(), "wand cores with no live source: " + dead + " (all routes: " + routes + ")");
    }

    private static Set<String> soldItems() throws IOException {
        Set<String> out = new HashSet<>();
        try (Stream<Path> files = Files.list(DATA.resolve("locations"))) {
            for (Path f : files.toList()) {
                JsonObject market = read(f).getAsJsonObject().getAsJsonObject("market");
                if (market == null || !market.has("sells")) {
                    continue;
                }
                JsonArray sells = market.getAsJsonArray("sells");
                for (JsonElement s : sells) {
                    if (s.getAsJsonObject().has("item")) {
                        out.add(s.getAsJsonObject().get("item").getAsString());
                    }
                }
            }
        }
        return out;
    }

    /** Creatures that spawn under the default (alpha-only) setting and have a spawn biome modifier. */
    private static Set<String> spawningCreatures() throws IOException {
        String modifiers = textOf(DATA.resolve("neoforge/biome_modifier"));
        Set<String> out = new TreeSet<>();
        Set<String> allowed = new HashSet<>(AlphaRoster.SHIPPED);
        allowed.addAll(AlphaRoster.PROGRESSION_CRITICAL);
        for (String creature : allowed) {
            if (modifiers.contains("\"" + NS + creature + "\"")) {
                out.add(creature);
            }
        }
        return out;
    }

    /** Its loot table, its living abilities (shed, groom) or its bond gifts name the core. */
    private static boolean creatureGives(String creature, String core) throws IOException {
        for (Path p : List.of(DATA.resolve("loot_table/entities/" + creature + ".json"),
                DATA.resolve("creatures/" + creature + ".json"), DATA.resolve("creature_bonds/" + creature + ".json"))) {
            if (Files.exists(p) && Files.readString(p, StandardCharsets.UTF_8).contains(NS + core)) {
                return true;
            }
        }
        // The phoenix sheds in code (PhoenixEntity) as well as gifting through its bond profile, covered above.
        return false;
    }

    private static String textOf(Path dir) throws IOException {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                all.append(Files.readString(f, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return all.toString();
    }

    private static JsonElement read(Path f) throws IOException {
        return JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
    }
}
