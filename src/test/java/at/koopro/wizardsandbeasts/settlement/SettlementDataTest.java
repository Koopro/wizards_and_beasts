package at.koopro.wizardsandbeasts.settlement;

import at.koopro.wizardsandbeasts.settlement.post.PostRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a settlement knows comes back from a save exactly: identity, whether they are away, and every shelf. */
class SettlementDataTest {

    @Test
    void aSiteSurvivesASave() {
        SettlementData data = new SettlementData();
        data.site("overworld@1,2", 99L);
        PostRef ref = new PostRef("overworld@1,2", 42L);
        UUID id = UUID.randomUUID();
        data.setPost(ref, SettlementData.Post.vacant("wizards_and_beasts:keeper", 3).present(id));
        data.setShelf("overworld@1,2", "wizards_and_beasts:apothecary|dittany", new SettlementData.Shelf(5, 1000L));

        Codec<SettlementData> codec = SettlementData.TYPE.codec();
        SettlementData back = codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, data).getOrThrow())
                .getOrThrow();

        SettlementData.Site site = back.site("overworld@1,2");
        assertEquals(99L, site.seed());
        SettlementData.Post post = back.post(ref);
        assertEquals(Optional.of(id), post.npc());
        assertEquals(PostRules.Status.PRESENT, post.status());
        assertEquals(3, post.name());
        assertEquals(new SettlementData.Shelf(5, 1000L), site.shelves().get("wizards_and_beasts:apothecary|dittany"));
    }

    @Test
    void someoneAwayKeepsTheirNameAndNoLongerNamesABody() {
        SettlementData.Post present = SettlementData.Post.vacant("r", 7).present(UUID.randomUUID());
        SettlementData.Post away = present.away(500L, 24_000L);
        assertEquals(PostRules.Status.AWAY, away.status());
        assertEquals(7, away.name());
        assertEquals(500L, away.backAt());
        assertEquals(24_000L, away.dawn());
        assertTrue(away.npc().isEmpty());
    }

    /** A restart while people are away: both come back from the save still away, with the same dawn, nobody doubled. */
    @Test
    void peopleAwaySurviveARestartAsTheyWere() {
        SettlementData data = new SettlementData();
        data.site("overworld@1,2", 99L);
        PostRef keeper = new PostRef("overworld@1,2", 1L);
        PostRef scholar = new PostRef("overworld@1,2", 2L);
        data.setPost(keeper, SettlementData.Post.vacant("keeper", 2).present(UUID.randomUUID()).away(30_000L, 48_000L));
        data.setPost(scholar, SettlementData.Post.vacant("scholar", 5).present(UUID.randomUUID()).away(31_000L, 72_000L));

        SettlementData back = roundTrip(roundTrip(data));

        assertEquals(new SettlementData.Post("keeper", PostRules.Status.AWAY, Optional.empty(), 30_000L, 48_000L, 2),
                back.post(keeper));
        assertEquals(new SettlementData.Post("scholar", PostRules.Status.AWAY, Optional.empty(), 31_000L, 72_000L, 5),
                back.post(scholar));
        assertFalse(back.post(keeper).due(29_000L, 47_999L));
        assertTrue(back.post(keeper).due(29_000L, 48_000L));
    }

    /** A save written before {@code dawn} existed: the away record keeps to the game time it was written with. */
    @Test
    void anAwayRecordFromAnOlderSaveKeepsItsGameTime() {
        com.google.gson.JsonObject old = new com.google.gson.JsonObject();
        old.addProperty("role", "keeper");
        old.addProperty("status", "away");
        old.addProperty("back_at", 5000L);
        old.addProperty("name", 4);
        SettlementData.Post post = SettlementData.Post.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        assertEquals(PostRules.NO_DAWN, post.dawn());
        assertFalse(post.due(4999L, 10 * 24_000L));
        assertTrue(post.due(5000L, 0L));
    }

    private static SettlementData roundTrip(SettlementData data) {
        Codec<SettlementData> codec = SettlementData.TYPE.codec();
        return codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
    }

    @Test
    void aPostOnASiteNeverSeenIsNothing() {
        assertNull(new SettlementData().post(new PostRef("nowhere", 1L)));
    }

    @Test
    void aForgottenSiteIsGone() {
        SettlementData data = new SettlementData();
        data.site("a", 1L);
        data.forget("a");
        assertNull(data.site("a"));
    }

    @Test
    void knownPlacesAreKeptOnceInOrderAndPerChart() {
        KnownPlaces places = KnownPlaces.EMPTY.with("a").with("b").with("a");
        assertEquals(java.util.List.of("a", "b"), places.sites());
        assertFalse(places.charted());
        assertTrue(places.withChart().charted());
        assertTrue(places.withChart().has("b"));
    }
}
