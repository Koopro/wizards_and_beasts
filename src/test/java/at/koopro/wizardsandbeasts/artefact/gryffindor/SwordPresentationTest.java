package at.koopro.wizardsandbeasts.artefact.gryffindor;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwordPresentationTest {

    private static boolean may(boolean module, boolean hat, boolean inWorld, boolean foe, float health, long since) {
        return SwordPresentation.mayPresent(module, hat, inWorld, foe, health, 20f, since);
    }

    @Test
    void onlyAWearerInMortalDangerStandingTheirGround() {
        assertTrue(may(true, true, false, true, 6f, 40));
        assertTrue(may(true, true, false, true, 1f, SwordPresentation.STOOD_GROUND_TICKS));

        assertFalse(may(false, true, false, true, 6f, 40), "module off");
        assertFalse(may(true, false, false, true, 6f, 40), "not wearing the Hat");
        assertFalse(may(true, true, true, true, 6f, 40), "the sword is already out in the world");
        assertFalse(may(true, true, false, false, 6f, 40), "a foe not worth the sword");
        assertFalse(may(true, true, false, true, 6.5f, 40), "not yet in mortal danger");
        assertFalse(may(true, true, false, true, 0f, 40), "already dead");
        assertFalse(may(true, true, false, true, 6f, -1), "never struck the foe: fleeing, not fighting");
        assertFalse(may(true, true, false, true, 6f, SwordPresentation.STOOD_GROUND_TICKS + 1), "struck it too long ago");
    }

    @Test
    void theRecordKnowsOneSwordAndForgetsOnlyThatOne() {
        GryffindorSword record = new GryffindorSword();
        UUID sword = UUID.randomUUID();
        record.presented(sword);
        assertFalse(record.lost(UUID.randomUUID()), "another stack's loss cleared the record");
        assertEquals(sword, record.present());

        var json = GryffindorSword.CODEC.encodeStart(JsonOps.INSTANCE, record).getOrThrow();
        assertEquals(sword, GryffindorSword.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().present());

        assertTrue(record.lost(sword));
        assertNull(record.present());
        assertNull(GryffindorSword.CODEC.parse(JsonOps.INSTANCE,
                GryffindorSword.CODEC.encodeStart(JsonOps.INSTANCE, record).getOrThrow()).getOrThrow().present());
    }
}
