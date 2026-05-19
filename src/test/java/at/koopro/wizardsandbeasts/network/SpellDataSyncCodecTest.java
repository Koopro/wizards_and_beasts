package at.koopro.wizardsandbeasts.network;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.network.spell.SpellDataDeltaS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Round-trips the typed network codecs to make sure the wire format stays
 * decoupled from the on-disk NBT format and survives encode -> decode without
 * losing fields.
 */
class SpellDataSyncCodecTest {

    @Test
    void fullSync_roundTrip_preservesAllFields() {
        PlayerSpellData data = new PlayerSpellData();
        data.learnSpell("lumos");
        data.learnSpell("nox");
        data.learnSpell("stupefy");
        data.setLoadoutSpell(0, "stupefy");
        data.setLoadoutSpell(2, "lumos");
        data.setActiveSlot(2);
        data.setCooldown("stupefy", 999L);
        data.setCooldown("lumos", 100L);
        data.incrementCastCount("stupefy");
        data.incrementCastCount("stupefy");
        data.incrementCastCount("lumos");
        data.incrementSuccessfulHits("stupefy");
        data.incrementSuccessfulHits("lumos");

        SpellDataSyncS2CPayload original = SpellDataSyncS2CPayload.of(data);

        ByteBuf buf = Unpooled.buffer();
        try {
            SpellDataSyncS2CPayload.STREAM_CODEC.encode(buf, original);
            SpellDataSyncS2CPayload decoded = SpellDataSyncS2CPayload.STREAM_CODEC.decode(buf);

            assertEquals(original.syncVersion(), decoded.syncVersion());
            assertEquals(original.knownSpells(), decoded.knownSpells());
            assertEquals(original.activeSlot(), decoded.activeSlot());
            assertEquals(original.cooldowns(), decoded.cooldowns());
            assertEquals(original.castCounts(), decoded.castCounts());
            assertEquals(original.successfulHits(), decoded.successfulHits());
            assertEquals(original.rejectCounts(), decoded.rejectCounts());
            assertEquals(original.syncCorrections(), decoded.syncCorrections());

            for (int i = 0; i < PlayerSpellData.LOADOUT_SIZE; i++) {
                assertEquals(original.loadout()[i], decoded.loadout()[i],
                        "Loadout slot " + i + " must round-trip.");
            }
        } finally {
            buf.release();
        }
    }

    @Test
    void fullSync_emptyData_roundTrips() {
        SpellDataSyncS2CPayload original = SpellDataSyncS2CPayload.of(new PlayerSpellData());

        ByteBuf buf = Unpooled.buffer();
        try {
            SpellDataSyncS2CPayload.STREAM_CODEC.encode(buf, original);
            SpellDataSyncS2CPayload decoded = SpellDataSyncS2CPayload.STREAM_CODEC.decode(buf);

            assertEquals(original.syncVersion(), decoded.syncVersion());
            assertEquals(0, decoded.knownSpells().size());
            assertEquals(0, decoded.cooldowns().size());
            assertEquals(0, decoded.castCounts().size());
            assertEquals(0, decoded.successfulHits().size());
            assertEquals(0, decoded.rejectCounts().size());
            assertEquals(0, decoded.syncCorrections());
            assertEquals(0, decoded.activeSlot());
            for (int i = 0; i < PlayerSpellData.LOADOUT_SIZE; i++) {
                assertNull(decoded.loadout()[i], "Empty loadout slot " + i + " must decode as null.");
            }
        } finally {
            buf.release();
        }
    }

    @Test
    void delta_roundTrip_preservesFields() {
        SpellDataDeltaS2CPayload original = new SpellDataDeltaS2CPayload("stupefy", 12345L, 7, 4);

        ByteBuf buf = Unpooled.buffer();
        try {
            SpellDataDeltaS2CPayload.STREAM_CODEC.encode(buf, original);
            SpellDataDeltaS2CPayload decoded = SpellDataDeltaS2CPayload.STREAM_CODEC.decode(buf);

            assertEquals(original.spellId(), decoded.spellId());
            assertEquals(original.cooldownExpiryTick(), decoded.cooldownExpiryTick());
            assertEquals(original.newCastCount(), decoded.newCastCount());
            assertEquals(original.newSuccessfulHits(), decoded.newSuccessfulHits());
        } finally {
            buf.release();
        }
    }
}
