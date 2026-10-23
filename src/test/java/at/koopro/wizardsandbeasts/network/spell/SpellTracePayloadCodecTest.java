package at.koopro.wizardsandbeasts.network.spell;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpellTracePayloadCodecTest {

    @Test
    void openRoundTrips() {
        SpellTraceOpenS2CPayload in = new SpellTraceOpenS2CPayload(42L, "wizards_and_beasts:lumos", 3, 180, 200, true, true,
                new int[] {0, 65535, 1234, 4321});
        ByteBuf buf = Unpooled.buffer();
        SpellTraceOpenS2CPayload.STREAM_CODEC.encode(buf, in);
        SpellTraceOpenS2CPayload out = SpellTraceOpenS2CPayload.STREAM_CODEC.decode(buf);
        assertEquals(in.challengeId(), out.challengeId());
        assertEquals(in.spellId(), out.spellId());
        assertEquals(in.difficulty(), out.difficulty());
        assertEquals(in.timeLimitTicks(), out.timeLimitTicks());
        assertEquals(in.startAllowanceTicks(), out.startAllowanceTicks());
        assertEquals(in.spendsSource(), out.spendsSource());
        assertEquals(in.steadyHand(), out.steadyHand());
        assertArrayEquals(in.points(), out.points());
    }

    @Test
    void submitRoundTripsAndRefusesOversizedStreams() {
        SpellTraceSubmitC2SPayload in = new SpellTraceSubmitC2SPayload(-7L, new int[] {1, 2, 3, 4});
        ByteBuf buf = Unpooled.buffer();
        SpellTraceSubmitC2SPayload.STREAM_CODEC.encode(buf, in);
        SpellTraceSubmitC2SPayload out = SpellTraceSubmitC2SPayload.STREAM_CODEC.decode(buf);
        assertEquals(in.challengeId(), out.challengeId());
        assertArrayEquals(in.samples(), out.samples());

        ByteBuf hostile = Unpooled.buffer();
        hostile.writeLong(1L);
        hostile.writeShort(60_000); // claims far more pairs than the cap
        assertThrows(IllegalArgumentException.class, () -> SpellTraceSubmitC2SPayload.STREAM_CODEC.decode(hostile));
    }

    @Test
    void resultRoundTrips() {
        SpellTraceResultS2CPayload in = new SpellTraceResultS2CPayload(9L, 2, 17, 873, 100, true, "Requires skill: x");
        ByteBuf buf = Unpooled.buffer();
        SpellTraceResultS2CPayload.STREAM_CODEC.encode(buf, in);
        assertEquals(in, SpellTraceResultS2CPayload.STREAM_CODEC.decode(buf));
    }
}
