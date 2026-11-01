package at.koopro.wizardsandbeasts.settlement.npc;

import at.koopro.wizardsandbeasts.artefact.erised.Gazer;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.MagicalCondition;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityFilter;
import at.koopro.wizardsandbeasts.owl.career.track.CareerContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every role file parses, keeps a real place, says only what the lang file holds, and can always say something. */
class NpcRoleDataTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path ROLES = RESOURCES.resolve("data/wizards_and_beasts/npc_roles");
    private static final Path LOCATIONS = RESOURCES.resolve("data/wizards_and_beasts/locations");
    private static final Map<String, NpcRole> ALL = new HashMap<>();
    private static JsonObject lang;

    @BeforeAll
    static void load() throws IOException {
        lang = JsonParser.parseString(Files.readString(RESOURCES.resolve("assets/wizards_and_beasts/lang/en_us.json")))
                .getAsJsonObject();
        try (Stream<Path> files = Files.list(ROLES)) {
            for (Path file : files.toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(file));
                String id = file.getFileName().toString().replace(".json", "");
                ALL.put(id, NpcRole.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            }
        }
    }

    private static Gazer nobody() {
        return new Gazer(Heritage.WIZARDKIND, null, Set.of(), List.of(), 0, 0, null,
                career());
    }

    private static CareerContext career() {
        return new CareerContext() {
            @Override public OWLGrade grade(OWLSubject subject) { return OWLGrade.T; }
            @Override public int pages(DiscoveryTier minTier, int minRating) { return 0; }
            @Override public int researchCompleted() { return 0; }
            @Override public int activityTotal(ActivityFilter filter, boolean distinct) { return 0; }
            @Override public int contractsCompleted() { return 0; }
        };
    }

    @Test
    void theFirstSettlementIsStaffed() {
        assertEquals(Set.of("keeper", "wandmaker", "apothecary", "scholar", "teller"), ALL.keySet());
    }

    @Test
    void everyRoleKeepsARealLocationThatAdmitsIt() throws IOException {
        for (Map.Entry<String, NpcRole> entry : ALL.entrySet()) {
            Identifier location = entry.getValue().location();
            Path file = LOCATIONS.resolve(location.getPath() + ".json");
            assertTrue(Files.exists(file), entry.getKey() + " keeps " + location);
            JsonObject presence = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("presence");
            if (entry.getValue().entity().isEmpty()) {
                assertEquals("wizards_and_beasts:" + entry.getKey(), presence.get("host_role").getAsString(), entry.getKey());
                Set<String> hosts = new HashSet<>();
                presence.getAsJsonArray("hosts").forEach(host -> hosts.add(host.getAsString()));
                assertTrue(hosts.contains("wizards_and_beasts:settlement_npc"), entry.getKey());
            }
        }
    }

    @Test
    void everyLineIsInTheLangFile() {
        for (Map.Entry<String, NpcRole> entry : ALL.entrySet()) {
            NpcRole role = entry.getValue();
            assertFalse(role.names().isEmpty(), entry.getKey() + " has no name");
            role.names().forEach(key -> assertTrue(lang.has(key), key));
            role.greetings().forEach(line -> assertTrue(lang.has(line.text()), line.text()));
            for (NpcRole.Topic topic : role.topics()) {
                assertTrue(lang.has(topic.label()), topic.label());
                assertTrue(lang.has(topic.reply()), topic.reply());
            }
        }
    }

    @Test
    void anyoneAtAllGetsAGreetingFromEveryRoleWithWords() {
        for (Map.Entry<String, NpcRole> entry : ALL.entrySet()) {
            if (!entry.getValue().greetings().isEmpty()) {
                for (long seed = 0; seed < 20; seed++) {
                    assertNotNull(NpcTalk.greet(entry.getValue(), nobody(), seed), entry.getKey());
                }
            }
        }
    }

    @Test
    void noRoleOffersMoreTopicsThanTheBoardShowsToAnyone() {
        for (NpcRole role : ALL.values()) {
            assertTrue(NpcTalk.topics(role, nobody()).size() <= NpcTalk.MAX_TOPICS);
        }
    }

    @Test
    void aHeritageLineGoesOnlyToThatHeritage() {
        NpcRole keeper = ALL.get("keeper");
        NpcRole.Line veela = keeper.greetings().stream().filter(l -> l.text().endsWith("veela")).findFirst().orElseThrow();
        assertFalse(veela.heardBy(nobody()));
        Gazer one = new Gazer(Heritage.VEELA, null, Set.of(), List.of(), 0, 0, null,
                career());
        assertTrue(veela.heardBy(one));
    }

    @Test
    void aConditionTopicOnlyOpensForThoseWhoCarryIt() {
        NpcRole apothecary = ALL.get("apothecary");
        assertTrue(NpcTalk.topics(apothecary, nobody()).stream().noneMatch(t -> t.id().equals("wolfsbane")));
        Gazer bitten = new Gazer(Heritage.WIZARDKIND, null, Set.of(MagicalCondition.LYCANTHROPY), List.of(), 0, 0, null,
                career());
        assertTrue(NpcTalk.topics(apothecary, bitten).stream().anyMatch(t -> t.id().equals("wolfsbane")));
    }

    @Test
    void shopRulesNameRealGoods() throws IOException {
        for (NpcRole role : ALL.values()) {
            if (role.shop().isEmpty()) {
                continue;
            }
            JsonObject location = JsonParser.parseString(Files.readString(LOCATIONS.resolve(role.location().getPath() + ".json")))
                    .getAsJsonObject();
            Set<String> sells = new HashSet<>();
            location.getAsJsonObject("market").getAsJsonArray("sells")
                    .forEach(good -> sells.add(good.getAsJsonObject().get("id").getAsString()));
            for (String good : role.shop().get().goods().keySet()) {
                assertTrue(sells.contains(good), good + " is not sold at " + role.location());
            }
        }
    }

    @Test
    void noSpellIsForSaleInAHamlet() throws IOException {
        for (String place : List.of("hamlet", "reading_room", "apothecary")) {
            String file = Files.readString(LOCATIONS.resolve(place + ".json"));
            assertFalse(file.contains("standard_book_of_spells"), place);
            assertFalse(file.contains("torn_spell_page"), place);
        }
    }
}
