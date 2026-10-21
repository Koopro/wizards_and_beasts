package at.koopro.wizardsandbeasts.creature.companion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped companion data, joined against itself and the bond layer. Every failure here is silent in play:
 * an unknown trait is never rolled, a SEARCH command with no search ability does nothing, a companion file with
 * no bond profile is a creature that can never be bonded to in the first place.
 */
class CompanionDataTest {

    private static final Path DATA = Path.of("src/main/resources/data/wizards_and_beasts");
    private static final Path LANG = Path.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json");

    private static final Map<String, CompanionProfile> PROFILES = new HashMap<>();
    private static final Map<String, PersonalityTrait> TRAITS = new HashMap<>();
    private static Set<String> lang;

    @BeforeAll
    static void load() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (Stream<Path> files = Files.list(DATA.resolve("companion_traits"))) {
            for (Path file : files.toList()) {
                TRAITS.put(name(file), PersonalityTrait.CODEC.parse(JsonOps.INSTANCE, read(file)).getOrThrow());
            }
        }
        try (Stream<Path> files = Files.list(DATA.resolve("companions"))) {
            for (Path file : files.toList()) {
                PROFILES.put(name(file), CompanionProfile.CODEC.parse(JsonOps.INSTANCE, read(file)).getOrThrow());
            }
        }
        lang = read(LANG).keySet();
    }

    private static String name(Path file) {
        String f = file.getFileName().toString();
        return f.substring(0, f.length() - ".json".length());
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void theRequestedRosterShips() {
        for (String species : Set.of("niffler", "bowtruckle", "hippogriff", "thestral", "phoenix", "demiguise", "unicorn")) {
            assertTrue(PROFILES.containsKey(species), species + " has no companion profile");
        }
        assertTrue(TRAITS.keySet().containsAll(Set.of("brave", "skittish", "greedy", "playful", "loyal", "aggressive",
                "territorial", "curious")));
    }

    @Test
    void everyCompanionCanBeBondedTo() {
        PROFILES.keySet().forEach(species -> assertTrue(Files.exists(DATA.resolve("creature_bonds/" + species + ".json")),
                species + " is a companion with no bond profile, so nothing could ever bond to it"));
    }

    @Test
    void everyPoolNamesRealTraitsAndNoSpeciesHasThemAll() {
        PROFILES.forEach((species, profile) -> {
            assertTrue(profile.traitPool().size() >= profile.traitsPerCreature(), species + " pool is too small");
            assertTrue(profile.traitPool().size() < TRAITS.size(), species + " can roll every trait");
            profile.traitPool().forEach(t -> assertTrue(TRAITS.containsKey(t.trait()), species + " names unknown trait " + t.trait()));
        });
    }

    @Test
    void commandsHaveTheAbilitiesAndBodiesTheyNeed() throws IOException {
        for (Map.Entry<String, CompanionProfile> e : PROFILES.entrySet()) {
            CompanionProfile profile = e.getValue();
            String species = e.getKey();
            for (CompanionCommand command : new CompanionCommand[]{CompanionCommand.SEARCH, CompanionCommand.ASSIST}) {
                boolean has = profile.abilities().stream().anyMatch(a -> a.command() == command);
                assertTrue(!profile.understands(command) || has, species + " takes " + command + " with nothing to do it with");
                assertTrue(profile.understands(command) || !has, species + " has a " + command + " ability it is never told to use");
            }
            boolean combat = profile.commands().stream().anyMatch(CompanionCommand::isCombat);
            assertTrue(!combat || profile.canFight(), species + " takes combat orders but cannot fight");
            Path creature = DATA.resolve("creatures/" + species + ".json");
            if (profile.canFight() && Files.exists(creature)) {
                JsonObject json = read(creature);
                assertTrue(json.has("attackDamage") && json.get("attackDamage").getAsDouble() > 0,
                        species + " may fight but its creature definition deals no damage");
            }
            assertTrue(profile.understands(CompanionCommand.FOLLOW) && profile.understands(CompanionCommand.STAY),
                    species + " cannot even be told to follow or stay");
        }
    }

    @Test
    void everyNameHasWords() {
        TRAITS.keySet().forEach(t -> assertTrue(lang.contains(PersonalityTrait.key(t)), t));
        for (CompanionCommand command : CompanionCommand.values()) {
            assertTrue(lang.contains(command.key()), command.name());
        }
        for (CompanionStage stage : CompanionStage.values()) {
            assertTrue(lang.contains(stage.key()), stage.name());
        }
        PROFILES.forEach((species, profile) -> profile.abilities().forEach(a ->
                assertTrue(lang.contains(a.key()), species + ": " + a.key())));
    }

    @Test
    void breedingHabitatOnlyWhereTheBondBreeds() throws IOException {
        for (Map.Entry<String, CompanionProfile> e : PROFILES.entrySet()) {
            if (e.getValue().breeding().isPresent()) {
                assertTrue(read(DATA.resolve("creature_bonds/" + e.getKey() + ".json")).has("breeding"),
                        e.getKey() + " has companion breeding rules but its bond profile never breeds");
            }
        }
        assertFalse(PROFILES.isEmpty());
    }
}
