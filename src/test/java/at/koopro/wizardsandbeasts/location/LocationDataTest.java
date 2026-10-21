package at.koopro.wizardsandbeasts.location;

import at.koopro.wizardsandbeasts.owl.career.track.ActivityFilter;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import at.koopro.wizardsandbeasts.owl.career.track.CareerGoal;
import at.koopro.wizardsandbeasts.owl.career.track.CareerTrack;
import at.koopro.wizardsandbeasts.owl.career.track.ContractDefinition;
import at.koopro.wizardsandbeasts.treasure.CurseKind;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped location and the curse-breaker track, joined against what they name. Each check is a failure that
 * would be silent in play: a contract naming a treasure site that does not exist is never completed, a tier asking
 * for more distinct curses than there are is a ceiling, a lang key missing shows the raw key on the board.
 */
class LocationDataTest {

    private static final Path DATA = Path.of("src/main/resources/data/wizards_and_beasts");
    private static final Path LANG = Path.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json");
    private static final String NS = "wizards_and_beasts";

    private static final Map<String, LocationDefinition> LOCATIONS = new LinkedHashMap<>();
    private static CareerTrack curseBreaker;
    private static Set<String> lang;

    @BeforeAll
    static void load() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (Stream<Path> files = Files.list(DATA.resolve("locations"))) {
            for (Path file : files.sorted().toList()) {
                String id = file.getFileName().toString().replace(".json", "");
                LOCATIONS.put(id, LocationDefinition.CODEC.parse(JsonOps.INSTANCE, read(file)).getOrThrow());
            }
        }
        curseBreaker = CareerTrack.CODEC.parse(JsonOps.INSTANCE, read(DATA.resolve("career_tracks/curse_breaker.json")))
                .getOrThrow();
        lang = read(LANG).keySet();
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Set<String> treasureSites() throws IOException {
        Set<String> sites = new HashSet<>();
        try (Stream<Path> files = Files.list(DATA.resolve("treasure_sites"))) {
            files.forEach(f -> sites.add(NS + ":" + f.getFileName().toString().replace(".json", "")));
        }
        return sites;
    }

    @Test
    void shippedLocationsLoadAndValidate() {
        assertTrue(LOCATIONS.containsKey("gringotts"), "gringotts");
        assertTrue(LOCATIONS.containsKey("ollivanders"), "ollivanders");
        LOCATIONS.forEach((id, def) -> assertEquals(List.of(), def.validate(), id));
        assertEquals(List.of(), curseBreaker.validate());
    }

    @Test
    void everyServiceIsRegistered() {
        LOCATIONS.forEach((id, def) -> def.services().forEach(offer ->
                assertNotNull(LocationServices.get(offer.service()), id + " offers " + offer.service())));
    }

    @Test
    void everyLineHasItsWords() {
        LOCATIONS.forEach((id, def) -> {
            String base = "location." + NS + "." + id;
            assertTrue(lang.contains(base + ".name"), base + ".name");
            for (CareerTrack.Rank tier : def.standing().ranks()) {
                assertTrue(lang.contains(base + ".tier." + tier.id()), base + ".tier." + tier.id());
            }
            for (ContractDefinition contract : def.standing().contracts()) {
                assertTrue(lang.contains(base + ".contract." + contract.id()), contract.id());
                assertTrue(lang.contains(base + ".contract." + contract.id() + ".brief"), contract.id() + ".brief");
            }
            for (ServiceOffer offer : def.services()) {
                String key = "location." + offer.service().getNamespace() + ".service." + offer.service().getPath();
                assertTrue(lang.contains(key), key);
            }
        });
        for (CareerTrack.Rank rank : curseBreaker.ranks()) {
            assertTrue(lang.contains("career." + NS + ".curse_breaker.rank." + rank.id()), rank.id());
        }
        for (ContractDefinition contract : curseBreaker.contracts()) {
            assertTrue(lang.contains("career." + NS + ".curse_breaker.contract." + contract.id() + ".brief"),
                    contract.id());
        }
        for (ActivityType type : List.of(ActivityType.CURSE_BROKEN, ActivityType.TREASURE_RETURNED)) {
            assertTrue(lang.contains("career." + NS + ".activity." + type.getSerializedName()), type.name());
        }
    }

    /** Every subject a filter names is something that can actually be the subject of that activity. */
    @Test
    void filtersNameRealSitesAndCurses() throws IOException {
        Set<String> sites = treasureSites();
        Set<String> curses = new HashSet<>();
        for (CurseKind kind : CurseKind.values()) {
            curses.add(kind.id().toString());
            assertTrue(lang.contains("subject." + NS + "." + kind.getSerializedName()), kind.name());
        }
        for (String site : sites) {
            assertTrue(lang.contains("subject." + site.replace(':', '.')), site);
        }
        Stream.concat(LOCATIONS.values().stream().flatMap(def -> allFilters(def.standing()).stream()),
                allFilters(curseBreaker).stream()).forEach(filter -> {
            for (Identifier subject : filter.species()) {
                if (filter.activity() == ActivityType.TREASURE_RETURNED) {
                    assertTrue(sites.contains(subject.toString()), "no treasure site " + subject);
                } else if (filter.activity() == ActivityType.CURSE_BROKEN) {
                    assertTrue(curses.contains(subject.toString()), "no curse " + subject);
                }
            }
        });
    }

    @Test
    void deliveriesNameRealItems() {
        Stream.concat(LOCATIONS.values().stream().flatMap(def -> def.standing().contracts().stream()),
                curseBreaker.contracts().stream()).forEach(contract -> {
            if (contract.objective() instanceof ContractDefinition.Delivery delivery) {
                assertTrue(BuiltInRegistries.ITEM.containsKey(delivery.item()), contract.id() + ": " + delivery.item());
            }
        });
    }

    /**
     * The composed reachability check (lessons: "registered is not reachable"): a tier asking for N distinct
     * subjects must have at least N to choose from, and every tier's contracts-completed goal must be reachable
     * from the contracts the tiers below it are offered.
     */
    @Test
    void everyTierIsReachable() throws IOException {
        int sites = treasureSites().size();
        int curses = CurseKind.values().length;
        List<CareerTrack> tracks = Stream.concat(LOCATIONS.values().stream().map(LocationDefinition::standing),
                Stream.of(curseBreaker)).toList();
        for (CareerTrack track : tracks) {
            for (int r = 1; r < track.ranks().size(); r++) {
                CareerTrack.Rank rank = track.ranks().get(r);
                int offeredBelow = 0;
                for (ContractDefinition contract : track.contracts()) {
                    if (track.rankIndex(contract.minRank()) < r) {
                        offeredBelow++;
                    }
                }
                assertTrue(offeredBelow > 0, rank.id() + ": no contract is offered on the way to it");
                for (CareerGoal goal : rank.requires()) {
                    if (goal instanceof CareerGoal.ActivityTotal total && total.distinct()) {
                        int pool = total.filter().activity() == ActivityType.TREASURE_RETURNED ? sites : curses;
                        assertTrue(total.count() <= pool, rank.id() + " asks for " + total.count() + " of " + pool);
                    }
                }
            }
        }
    }

    @Test
    void gringottsIsKeptByTellersAndGatedByItsModule() {
        LocationDefinition gringotts = LOCATIONS.get("gringotts");
        assertTrue(gringotts.presence().hosts().contains(Identifier.fromNamespaceAndPath(NS, "goblin_teller")));
        assertEquals("GRINGOTTS", gringotts.module().name());
        assertFalse(gringotts.activityTypes().isEmpty());
        // Ollivanders reads no activity: its standing is paid only by its contracts, so it keeps no counters.
        assertTrue(LOCATIONS.get("ollivanders").activityTypes().isEmpty());
        assertTrue(LOCATIONS.get("ollivanders").presence().hostProfession().isPresent(),
                "Ollivanders must not be kept by every villager");
    }

    private static List<ActivityFilter> allFilters(CareerTrack track) {
        List<ActivityFilter> out = new java.util.ArrayList<>();
        track.xpRules().forEach(rule -> out.add(rule.when()));
        for (ContractDefinition contract : track.contracts()) {
            if (contract.objective() instanceof ContractDefinition.Activity activity) {
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
}
