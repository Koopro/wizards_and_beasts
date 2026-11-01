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
        SettlementData.Post away = present.away(500L);
        assertEquals(PostRules.Status.AWAY, away.status());
        assertEquals(7, away.name());
        assertEquals(500L, away.backAt());
        assertTrue(away.npc().isEmpty());
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
