package at.koopro.wizardsandbeasts.owl.career.track;

import at.koopro.wizardsandbeasts.creature.AlphaRoster;
import at.koopro.wizardsandbeasts.owl.career.track.ContractDefinition.Activity;
import at.koopro.wizardsandbeasts.owl.career.track.ContractDefinition.Delivery;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped Magizoologist track, joined against the data it depends on.
 *
 * <p>Every check here is a silent failure in play: a contract naming a creature a default world never
 * spawns is simply never completed, a delivery of an item only a kill drops breaks the bestiary's own
 * "never by killing" rule, and a rank asking for more dangerous creatures than exist is a ceiling. None of
 * these would error at load.
 */
class CareerTrackDataTest {

    private static final Path DATA = Path.of("src/main/resources/data/wizards_and_beasts");
    private static final Path LANG = Path.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json");
    private static final String NS = "wizards_and_beasts";

    private static CareerTrack track;

    @BeforeAll
    static void load() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        track = CareerTrack.CODEC.parse(JsonOps.INSTANCE, read(DATA.resolve("career_tracks/magizoologist.json")))
                .getOrThrow();
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /** Alpha-roster creatures that also have a natural spawn: what a default world actually holds. */
    private static Set<String> reachable() {
        Set<String> out = new HashSet<>();
        Set<String> candidates = new HashSet<>(AlphaRoster.SHIPPED);
        candidates.addAll(AlphaRoster.PROGRESSION_CRITICAL);
        for (String id : candidates) {
            if (Files.exists(DATA.resolve("neoforge/biome_modifier/spawn_" + id + ".json"))) {
                out.add(id);
            }
        }
        return out;
    }

    private static List<ActivityFilter> filters() {
        List<ActivityFilter> out = new ArrayList<>();
        track.xpRules().forEach(rule -> out.add(rule.when()));
        for (ContractDefinition contract : track.contracts()) {
            if (contract.objective() instanceof Activity activity) {
                out.add(activity.filter());
            }
        }
        for (CareerTrack.Rank rank : track.ranks()) {
            for (CareerGoal goal : rank.requires()) {
                if (goal instanceof CareerGoal.ActivityTotal total) {
                    out.add(total.filter());
                }
            }
        }
        return out;
    }

    @Test
    void theTrackIsSound() {
        assertEquals(List.of(), track.validate());
        assertTrue(track.contracts().size() >= 10, "a board needs variety: " + track.contracts().size());
        for (CareerTrack.Rank rank : track.ranks()) {
            assertTrue(track.contracts().stream().anyMatch(c -> c.minRank().equals(rank.id())),
                    "rank " + rank.id() + " unlocks no contract");
        }
    }

    @Test
    void everyCreatureNamedSpawnsInADefaultWorld() {
        Set<String> reachable = reachable();
        for (ActivityFilter filter : filters()) {
            for (Identifier species : filter.species()) {
                assertEquals(NS, species.getNamespace(), species.toString());
                assertTrue(reachable.contains(species.getPath()),
                        species + " is named by the track but never spawns in a default world");
            }
        }
    }

    @Test
    void deliveriesAskOnlyForMaterialsGivenWithoutAKill() throws IOException {
        Set<String> given = new HashSet<>();
        for (String id : reachable()) {
            Path bond = DATA.resolve("creature_bonds/" + id + ".json");
            if (Files.exists(bond)) {
                JsonObject profile = read(bond);
                if (profile.has("gift")) {
                    given.add(profile.getAsJsonObject("gift").get("item").getAsString());
                }
            }
            Path creature = DATA.resolve("creatures/" + id + ".json");
            if (Files.exists(creature) && read(creature).has("abilities")) {
                for (JsonElement ability : read(creature).getAsJsonArray("abilities")) {
                    String type = ability.getAsJsonObject().get("type").getAsString();
                    if (type.equals("groomable") || type.equals("shed")) {
                        given.add(ability.getAsJsonObject().get("item").getAsString());
                    }
                }
            }
        }
        for (ContractDefinition contract : track.contracts()) {
            if (contract.objective() instanceof Delivery delivery) {
                assertTrue(given.contains(delivery.item().toString()),
                        contract.id() + " asks for " + delivery.item() + ", which no reachable creature gives freely: "
                                + given);
            }
        }
    }

    @Test
    void breedingWorkNamesOnlyCreaturesThatBreed() throws IOException {
        // An unfiltered breeding rule can only ever fire for a species that breeds, so it needs one to exist.
        boolean anyBreeder = false;
        for (String id : reachable()) {
            Path bond = DATA.resolve("creature_bonds/" + id + ".json");
            anyBreeder |= Files.exists(bond) && read(bond).has("breeding");
        }
        assertTrue(anyBreeder, "the track pays for breeding but no reachable creature breeds");
        for (ActivityFilter filter : filters()) {
            if (filter.activity() != ActivityType.CREATURE_BRED) {
                continue;
            }
            for (Identifier species : filter.species()) {
                JsonObject profile = read(DATA.resolve("creature_bonds/" + species.getPath() + ".json"));
                assertTrue(profile.has("breeding"), species + " has no breeding in its bond profile");
            }
        }
    }

    @Test
    void rankMilestonesAreReachable() throws IOException {
        Set<String> reachable = reachable();
        for (CareerTrack.Rank rank : track.ranks()) {
            for (CareerGoal goal : rank.requires()) {
                if (goal instanceof CareerGoal.BestiaryPages pages) {
                    int available = 0;
                    for (String id : reachable) {
                        Path entry = DATA.resolve("bestiary/entries/" + id + ".json");
                        if (!Files.exists(entry)) {
                            continue;
                        }
                        JsonObject json = read(entry);
                        int rating = json.has("mmRating") ? json.get("mmRating").getAsInt() : 0;
                        if (rating >= pages.minRating()) {
                            available++;
                        }
                    }
                    assertTrue(available >= pages.count(), rank.id() + " asks for " + pages.count()
                            + " pages but only " + available + " reachable creatures qualify");
                }
                if (goal instanceof CareerGoal.OwlGrade grade) {
                    assertTrue(grade.subject().examined(), rank.id() + " asks for an unexamined subject");
                }
                if (goal instanceof CareerGoal.NewtGrade grade) {
                    assertTrue(at.koopro.wizardsandbeasts.owl.OWLGradeRules.newtLadder(grade.subject()) != null,
                            rank.id() + " asks for a N.E.W.T. that is never sat");
                }
            }
        }
    }

    @Test
    void rankRewardsAreRealItems() {
        for (CareerTrack.Rank rank : track.ranks()) {
            for (ItemStack reward : rank.rewards()) {
                assertTrue(!reward.isEmpty(), rank.id() + " has an empty reward");
                assertEquals(NS, BuiltInRegistries.ITEM.getKey(reward.getItem()).getNamespace(),
                        rank.id() + " reward did not resolve to a mod item");
            }
        }
    }

    @Test
    void everyRankAndContractHasWords() throws IOException {
        Set<String> keys = read(LANG).keySet();
        String base = "career.wizards_and_beasts.magizoologist.";
        for (CareerTrack.Rank rank : track.ranks()) {
            assertTrue(keys.contains(base + "rank." + rank.id()), rank.id());
        }
        for (ContractDefinition contract : track.contracts()) {
            assertTrue(keys.contains(base + "contract." + contract.id()), contract.id());
            assertTrue(keys.contains(base + "contract." + contract.id() + ".brief"), contract.id() + " brief");
        }
        for (ActivityType type : ActivityType.values()) {
            assertTrue(keys.contains("career.wizards_and_beasts.activity." + type.getSerializedName()), type.name());
        }
    }
}
