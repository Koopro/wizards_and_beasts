package at.koopro.wizardsandbeasts.entity.niffler;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Niffler's tags say what it wants, how much, and what it may dig — and none of that includes a player's build. */
class NifflerTreasureTagsTest {

    private static final Path TAGS = Path.of("src/main/resources/data/wizards_and_beasts/tags");

    private static Set<String> values(String path) throws IOException {
        Set<String> out = new HashSet<>();
        try (Reader reader = Files.newBufferedReader(TAGS.resolve(path))) {
            for (JsonElement e : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("values")) {
                out.add(e.isJsonObject() ? e.getAsJsonObject().get("id").getAsString() : e.getAsString());
            }
        }
        return out;
    }

    @Test
    void goldOutranksGemsWhichOutrankTheRest() throws IOException {
        Set<String> high = values("item/niffler_treasure_high.json");
        Set<String> medium = values("item/niffler_treasure_medium.json");
        assertTrue(high.contains("minecraft:gold_ingot") && high.contains("wizards_and_beasts:galleon"));
        assertTrue(medium.contains("minecraft:diamond") && medium.contains("wizards_and_beasts:sickle"));
        assertFalse(high.contains("minecraft:diamond"), "diamonds are not gold");
        Set<String> overlap = new HashSet<>(high);
        overlap.retainAll(medium);
        assertTrue(overlap.isEmpty(), "ranked twice: " + overlap);
    }

    @Test
    void everythingRankedIsSomethingItWants() throws IOException {
        Set<String> shiny = values("item/niffler_shiny.json");
        Set<String> coins = values("item/coins.json");
        for (String tier : new String[]{"item/niffler_treasure_high.json", "item/niffler_treasure_medium.json"}) {
            for (String id : values(tier)) {
                assertTrue(shiny.contains(id) || (shiny.contains("#wizards_and_beasts:coins") && coins.contains(id)),
                        id + " is ranked but a Niffler would never pick it up");
            }
        }
    }

    @Test
    void itDigsNaturalDepositsAndLooseGroundNeverBuilds() throws IOException {
        Set<String> ore = values("block/niffler_shiny_blocks.json");
        Set<String> soft = values("block/niffler_diggable.json");
        for (String build : new String[]{"minecraft:gold_block", "minecraft:raw_gold_block", "minecraft:diamond_block",
                "minecraft:emerald_block"}) {
            assertFalse(ore.contains(build), "a Niffler would mine a player's " + build);
        }
        for (String hard : new String[]{"minecraft:stone", "minecraft:bedrock", "minecraft:deepslate",
                "minecraft:cobblestone", "minecraft:obsidian"}) {
            assertFalse(soft.contains(hard) || ore.contains(hard), "a Niffler would dig through " + hard);
        }
        assertTrue(ore.contains("#minecraft:gold_ores"), "gold ore is what Nifflers are kept to find");
    }
}
