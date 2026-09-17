package at.koopro.wizardsandbeasts.creature;

import at.koopro.wizardsandbeasts.creature.ability.CreatureAbility;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Killing a magical creature is not how a wizard comes by what it is prized for, where canon says so. Unicorn hair is
 * shed or given; a phoenix is reborn rather than killed; a demiguise's hair is found or given; mooncalf dung is left by a
 * dancing herd or a kept one; a werewolf is a person, and leaves at dawn.
 */
class CreatureLootLoreTest {

    private static final Path DATA = Path.of("src", "main", "resources", "data", "wizards_and_beasts");

    /** The creatures that yield nothing to a killer. */
    private static final Set<String> NOTHING_FROM_A_BODY = Set.of("unicorn", "phoenix", "demiguise", "mooncalf", "werewolf");

    /** The materials no creature's loot table may hand out. */
    private static final Set<String> NEVER_FROM_A_KILL = Set.of(
            "wizards_and_beasts:unicorn_hair", "wizards_and_beasts:phoenix_feather", "wizards_and_beasts:demiguise_hair");

    @Test
    void theseCreaturesYieldNothingToAKiller() throws IOException {
        for (String id : NOTHING_FROM_A_BODY) {
            String table = Files.readString(DATA.resolve(Path.of("loot_table", "entities", id + ".json")));
            assertTrue(JsonParser.parseString(table).getAsJsonObject().getAsJsonArray("pools").isEmpty(),
                    id + " drops something when killed");
        }
    }

    @Test
    void noLootTableHandsOutAMaterialThatMustBeGivenOrFound() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> tables = Files.walk(DATA.resolve("loot_table"))) {
            for (Path table : tables.filter(p -> p.toString().endsWith(".json")).toList()) {
                String text = Files.readString(table);
                for (String material : NEVER_FROM_A_KILL) {
                    if (text.contains('"' + material + '"')) {
                        offenders.add(table + " gives " + material);
                    }
                }
            }
        }
        assertEquals(List.of(), offenders);
    }

    @Test
    void theLivingSourcesParse() throws IOException {
        for (String id : List.of("unicorn", "demiguise", "werewolf")) {
            String json = Files.readString(DATA.resolve(Path.of("creatures", id + ".json")));
            CreatureDefinition definition = CreatureDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                    .getOrThrow(message -> new AssertionError(id + ": " + message));
            List<CreatureAbility.Type> types = definition.abilities().stream().map(CreatureAbility::type).toList();
            switch (id) {
                case "unicorn" -> assertTrue(types.containsAll(List.of(CreatureAbility.Type.WARY,
                        CreatureAbility.Type.GROOMABLE, CreatureAbility.Type.SHED, CreatureAbility.Type.SLAYER_CURSE)), id);
                case "demiguise" -> assertTrue(types.containsAll(List.of(CreatureAbility.Type.WATCHED_INVISIBILITY,
                        CreatureAbility.Type.FORESIGHT, CreatureAbility.Type.GROOMABLE, CreatureAbility.Type.SHED)), id);
                case "werewolf" -> assertTrue(types.containsAll(List.of(CreatureAbility.Type.MOON_BOUND,
                        CreatureAbility.Type.LYCANTHROPIC_BITE)), id);
                default -> throw new AssertionError(id);
            }
            assertTrue(!definition.traits().contains(Trait.FEARFUL) || !id.equals("unicorn"),
                    "a fearful unicorn flees everyone, including the people it should let near");
        }
    }
}
