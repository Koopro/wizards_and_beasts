package at.koopro.wizardsandbeasts.creature.wildlife;

import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules.Fear;
import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules.MerfolkSong;
import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules.Omen;
import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules.Crowding;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each species' signature rules, pinned without a world. */
class SignatureRulesTest {

    // ── the Augurey ──

    @Test
    void anAugureyHearsRainOnlyWhenItIsGenuinelyComing() {
        assertTrue(SignatureRules.rainComing(true, false, 0, 600));
        assertTrue(SignatureRules.rainComing(true, false, 0, SignatureRules.AUGUREY_FORECAST_TICKS));
        assertFalse(SignatureRules.rainComing(true, false, 0, SignatureRules.AUGUREY_FORECAST_TICKS + 1),
                "rain three hours off is not yet in the air");
        assertFalse(SignatureRules.rainComing(true, true, 0, 600), "it is already raining");
        assertFalse(SignatureRules.rainComing(true, false, 1200, 600), "forced clear weather cannot turn to rain");
        assertFalse(SignatureRules.rainComing(false, false, 0, 600), "no weather cycle, no rain");
        assertFalse(SignatureRules.rainComing(true, false, 0, 0));
    }

    // ── the Streeler ──

    @Test
    void aStreelerHoldsOneColourForAnHourThenChanges() {
        int hour = SignatureRules.HOUR_TICKS;
        assertEquals(SignatureRules.streelerColour(0, 0), SignatureRules.streelerColour(hour - 1, 0));
        assertNotEquals(SignatureRules.streelerColour(0, 0), SignatureRules.streelerColour(hour, 0));
        Set<Integer> seen = new HashSet<>();
        for (int h = 0; h < 24; h++) {
            seen.add(SignatureRules.streelerColour((long) h * hour, 0));
        }
        assertEquals(SignatureRules.STREELER_COLOURS.size(), seen.size(), "a day shows every colour it has");
        assertNotEquals(SignatureRules.streelerColour(0, 0), SignatureRules.streelerColour(0, 1),
                "two streelers need not match");
        assertEquals(SignatureRules.streelerColour(-1, 0), SignatureRules.streelerColour(-1 + 8L * hour, 0),
                "the cycle is periodic, negative times included");
    }

    // ── the Cornish Pixie ──

    @Test
    void aPixieSnatchesOnlyWhatCanBeLost() {
        assertTrue(SignatureRules.pixieMaySnatch(true, true, false));
        assertFalse(SignatureRules.pixieMaySnatch(false, true, false), "empty hands");
        assertFalse(SignatureRules.pixieMaySnatch(true, false, false), "a creative player loses nothing");
        assertFalse(SignatureRules.pixieMaySnatch(true, true, true), "already carrying something off");
        assertFalse(SignatureRules.pixiesHoist(SignatureRules.PIXIE_HOIST_SWARM - 1));
        assertTrue(SignatureRules.pixiesHoist(SignatureRules.PIXIE_HOIST_SWARM));
    }

    // ── the Blast-Ended Skrewt ──

    @Test
    void skrewtsTurnOnEachOtherOnlyWhenCrowded() {
        assertFalse(SignatureRules.skrewtsFight(1));
        assertFalse(SignatureRules.skrewtsFight(2), "a pair tolerates each other");
        assertTrue(SignatureRules.skrewtsFight(3));
    }

    // ── the Griffin ──

    @Test
    void aGriffinWarnsOnceThenDefendsItsGold() {
        double near = SignatureRules.HOARD_RADIUS - 1;
        assertEquals(Crowding.NONE, SignatureRules.hoardCrowding(false, SignatureRules.HOARD_RADIUS + 1, -1),
                "outside its hoard nobody is a thief");
        assertEquals(Crowding.NONE, SignatureRules.hoardCrowding(true, near, -1), "the person who feeds it may come near");
        assertEquals(Crowding.WARN, SignatureRules.hoardCrowding(false, near, -1));
        assertEquals(Crowding.NONE, SignatureRules.hoardCrowding(false, near, 10), "a moment to back off");
        assertEquals(Crowding.ATTACK, SignatureRules.hoardCrowding(false, near, 60));
        assertEquals(Crowding.WARN, SignatureRules.hoardCrowding(false, near, SignatureRules.HOARD_WARNING_TICKS),
                "an old warning is forgotten and given again");
    }

    // ── the Centaur ──

    @Test
    void aCentaurReadsTheMostPressingTruthInTheSky() {
        assertEquals(Omen.MARS_BRIGHT, SignatureRules.centaurOmen(true, true, true));
        assertEquals(Omen.FULL_MOON, SignatureRules.centaurOmen(false, true, true));
        assertEquals(Omen.STORM_COMING, SignatureRules.centaurOmen(false, false, true));
        assertEquals(Omen.SILENT, SignatureRules.centaurOmen(false, false, false));
        assertTrue(SignatureRules.canStargaze(true, true, false));
        assertFalse(SignatureRules.canStargaze(false, true, false), "not by day");
        assertFalse(SignatureRules.canStargaze(true, false, false), "not under a roof or canopy");
        assertFalse(SignatureRules.canStargaze(true, true, true), "not through rain cloud");
    }

    // ── merfolk song ──

    @Test
    void merfolkSongIsOnlyUnderstoodUnderWater() {
        assertEquals(MerfolkSong.SONG, SignatureRules.merfolkSong(true, 10));
        assertEquals(MerfolkSong.SCREECH, SignatureRules.merfolkSong(false, 10));
        assertEquals(MerfolkSong.SONG, SignatureRules.merfolkSong(true, SignatureRules.MERFOLK_SONG_RANGE));
        assertEquals(MerfolkSong.NONE, SignatureRules.merfolkSong(false, SignatureRules.MERFOLK_SCREECH_RANGE + 1),
                "a screech does not carry as far as song under water");
        assertEquals(MerfolkSong.NONE, SignatureRules.merfolkSong(true, SignatureRules.MERFOLK_SONG_RANGE + 1),
                "beyond the song's reach there is nothing to hear");
    }

    // ── the Boggart ──

    @Test
    void aBoggartBecomesTheWorstThingYouHaveMet() {
        assertEquals(Optional.empty(), SignatureRules.boggartForm(List.of()), "nothing met, nothing to become");
        assertEquals(Optional.of("acromantula"), SignatureRules.boggartForm(List.of(
                new Fear("acromantula", 5), new Fear("dementor", 5), new Fear("ghoul", 2))), "ties keep the first listed");
        assertEquals(Optional.of("werewolf"), SignatureRules.boggartForm(List.of(
                new Fear("ghoul", 2), new Fear("werewolf", 5))));
    }

    @Test
    void aBoggartFacingAGroupCannotChoose() {
        assertFalse(SignatureRules.boggartConfused(1));
        assertTrue(SignatureRules.boggartConfused(2));
    }

    @Test
    void aBoggartKeepsToDarkRoofedPlaces() {
        assertTrue(SignatureRules.boggartSettled(0, true));
        assertTrue(SignatureRules.boggartSettled(SignatureRules.BOGGART_MAX_LIGHT, true));
        assertFalse(SignatureRules.boggartSettled(SignatureRules.BOGGART_MAX_LIGHT + 1, true), "too bright");
        assertFalse(SignatureRules.boggartSettled(0, false), "open sky is no cupboard");
    }
}
