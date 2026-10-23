package at.koopro.wizardsandbeasts.bestiary.harvest;

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
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * User decision (2026-10-23): a creature's materials may be taken on a kill, but killing is never the better deal.
 *
 * <ul>
 *   <li>Wherever a material also comes without killing -- a bond gift, a shed sign, grooming -- the best a kill rule
 *       can yield per hour is below what that route yields per hour.</li>
 *   <li>A mod item in a creature's own loot table drops only to a player's kill, so a lava pit or a mob crusher
 *       harvests nothing a player could not.</li>
 * </ul>
 */
class KillVersusKeepTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");
    /** A niffler's hoard is coin it carried, not a part of the animal. */
    private static final Set<String> NOT_PARTS = Set.of("wizards_and_beasts:knut");

    @Test
    void killingNeverOutEarnsKeeping() throws IOException {
        Map<String, Double> keepPerHour = keepRates();
        List<String> worse = new ArrayList<>();
        for (JsonObject rule : jsons(DATA.resolve("bestiary").resolve("harvest"))) {
            String item = rule.get("item").getAsString();
            Double keep = keepPerHour.get(item);
            if (keep == null) {
                continue;
            }
            int max = rule.has("maxCount") ? rule.get("maxCount").getAsInt() : 1;
            double killPerHour = rule.get("chance").getAsDouble() * max * 3600.0
                    / Math.max(1, rule.get("cooldownSeconds").getAsInt());
            if (killPerHour >= keep) {
                worse.add(item + ": a kill yields " + killPerHour + "/h, keeping the creature " + keep + "/h");
            }
        }
        assertTrue(worse.isEmpty(), "killing out-earns keeping: " + worse);
    }

    @Test
    void creaturePartsDropOnlyToAPlayersKill() throws IOException {
        List<String> open = new ArrayList<>();
        try (Stream<Path> files = Files.list(DATA.resolve("loot_table").resolve("entities"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject table = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                JsonArray pools = table.has("pools") ? table.getAsJsonArray("pools") : new JsonArray();
                for (JsonElement poolElement : pools) {
                    JsonObject pool = poolElement.getAsJsonObject();
                    boolean playerKill = pool.has("conditions") && pool.getAsJsonArray("conditions").toString()
                            .contains("minecraft:killed_by_player");
                    for (JsonElement entry : pool.getAsJsonArray("entries")) {
                        JsonObject e = entry.getAsJsonObject();
                        String name = e.has("name") ? e.get("name").getAsString() : "";
                        if (name.startsWith("wizards_and_beasts:") && !NOT_PARTS.contains(name) && !playerKill) {
                            open.add(file.getFileName() + " -> " + name);
                        }
                    }
                }
            }
        }
        assertTrue(open.isEmpty(), "creature parts that drop without a player's kill: " + open);
    }

    /** Material -> the best items per hour a living creature gives of it, over gifts, sheds and grooming. */
    private static Map<String, Double> keepRates() throws IOException {
        Map<String, Double> rates = new HashMap<>();
        for (JsonObject bond : jsons(DATA.resolve("creature_bonds"))) {
            if (!bond.has("gift")) {
                continue;
            }
            JsonObject gift = bond.getAsJsonObject("gift");
            int min = gift.has("minCount") ? gift.get("minCount").getAsInt() : 1;
            double perHour = min * 3600.0 / gift.get("cooldownSeconds").getAsInt();
            rates.merge(gift.get("item").getAsString(), perHour, Math::max);
        }
        for (JsonObject creature : jsons(DATA.resolve("creatures"))) {
            if (!creature.has("abilities")) {
                continue;
            }
            for (JsonElement a : creature.getAsJsonArray("abilities")) {
                JsonObject ability = a.getAsJsonObject();
                String type = ability.get("type").getAsString();
                String ticksField = "shed".equals(type) ? "interval_ticks" : "groomable".equals(type) ? "cooldown_ticks" : null;
                if (ticksField == null) {
                    continue;
                }
                int ticks = ability.has(ticksField) ? ability.get(ticksField).getAsInt() : 24000;
                int count = ability.has("count") ? ability.get("count").getAsInt() : 1;
                rates.merge(ability.get("item").getAsString(), count * 72000.0 / ticks, Math::max);
            }
        }
        return rates;
    }

    private static List<JsonObject> jsons(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            List<JsonObject> out = new ArrayList<>();
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                out.add(JsonParser.parseString(Files.readString(p)).getAsJsonObject());
            }
            return out;
        }
    }
}
