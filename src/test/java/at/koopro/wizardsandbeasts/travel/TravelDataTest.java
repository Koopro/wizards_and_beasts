package at.koopro.wizardsandbeasts.travel;

import at.koopro.wizardsandbeasts.broom.fitting.BroomFitting;
import at.koopro.wizardsandbeasts.broom.fitting.FittingSlot;
import at.koopro.wizardsandbeasts.broom.fitting.FittingStat;
import at.koopro.wizardsandbeasts.broom.fitting.BroomWear;
import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import at.koopro.wizardsandbeasts.owl.career.track.CareerTrack;
import at.koopro.wizardsandbeasts.owl.career.track.ContractDefinition;
import at.koopro.wizardsandbeasts.travel.profile.TravelProfile;
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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The travel data and words: the Quidditch track and the broom shop load, every new thing has a name in en_us, and
 * every fitting has an item, a recipe and an icon.
 */
class TravelDataTest {

    private static final Path DATA = Path.of("src/main/resources/data/wizards_and_beasts");
    private static final Path ASSETS = Path.of("src/main/resources/assets/wizards_and_beasts");
    private static Set<String> lang;

    @BeforeAll
    static void load() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        lang = read(ASSETS.resolve("lang/en_us.json")).keySet();
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void theQuidditchTrackLoadsAndIsWorded() throws IOException {
        CareerTrack track = CareerTrack.CODEC.parse(JsonOps.INSTANCE,
                read(DATA.resolve("career_tracks/quidditch_player.json"))).getOrThrow();
        for (CareerTrack.Rank rank : track.ranks()) {
            assertTrue(lang.contains("career.wizards_and_beasts.quidditch_player.rank." + rank.id()), rank.id());
        }
        for (ContractDefinition contract : track.contracts()) {
            String key = "career.wizards_and_beasts.quidditch_player.contract." + contract.id();
            assertTrue(lang.contains(key) && lang.contains(key + ".brief"), contract.id());
        }
    }

    @Test
    void everyTravelActivityIsWorded() {
        for (ActivityType type : List.of(ActivityType.COURIER_DELIVERED, ActivityType.COURIER_EXPRESS,
                ActivityType.TIME_TRIAL_FINISHED, ActivityType.TIME_TRIAL_PAR, ActivityType.AERIAL_SURVEY,
                ActivityType.TREASURE_SIGHTED)) {
            assertTrue(lang.contains("career.wizards_and_beasts.activity." + type.getSerializedName()), type.name());
        }
    }

    @Test
    void theBroomShopOffersTrialsCourierWorkAndFittings() throws IOException {
        LocationDefinition shop = LocationDefinition.CODEC.parse(JsonOps.INSTANCE,
                read(DATA.resolve("locations/broom_shop.json"))).getOrThrow();
        assertEquals(List.of(), shop.validate());
        Set<String> services = new java.util.HashSet<>();
        shop.services().forEach(offer -> services.add(offer.service().getPath()));
        assertTrue(services.containsAll(Set.of("time_trial", "courier_dispatch", "broom_servicing")), services.toString());
        String raw = Files.readString(DATA.resolve("locations/broom_shop.json"));
        for (BroomFitting fitting : BroomFitting.values()) {
            assertTrue(raw.contains("wizards_and_beasts:" + fitting.id()), fitting + " is neither sold nor a reward");
        }
        for (String service : List.of("time_trial", "courier_dispatch", "apparition_test")) {
            assertTrue(lang.contains("location.wizards_and_beasts.service." + service), service);
        }
    }

    @Test
    void everyFittingHasAnItemRecipeIconAndWords() {
        for (BroomFitting fitting : BroomFitting.values()) {
            assertTrue(Files.exists(DATA.resolve("recipe/" + fitting.id() + ".json")), fitting + " recipe");
            assertTrue(Files.exists(ASSETS.resolve("textures/item/" + fitting.id() + ".png")), fitting + " icon");
            assertTrue(lang.contains("item.wizards_and_beasts." + fitting.id()), fitting + " item name");
            assertTrue(lang.contains(fitting.translationKey()), fitting + " name");
        }
        for (FittingSlot slot : FittingSlot.values()) {
            assertTrue(lang.contains(slot.translationKey()), slot.name());
        }
        for (FittingStat stat : FittingStat.values()) {
            assertTrue(lang.contains(stat.translationKey()), stat.name());
        }
        for (BroomWear wear : BroomWear.values()) {
            assertTrue(lang.contains(wear.translationKey()), wear.name());
        }
        assertTrue(Files.exists(ASSETS.resolve("textures/item/courier_parcel.png")));
        assertTrue(lang.contains("item.wizards_and_beasts.courier_parcel"));
    }

    @Test
    void theTravelTabIsWorded() {
        assertTrue(lang.contains("gui.wizards_and_beasts.character_sheet.tab.travel"));
        for (TravelProfile.Section section : TravelProfile.Section.values()) {
            assertTrue(lang.contains(section.translationKey()), section.name());
        }
    }
}
