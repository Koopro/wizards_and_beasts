package at.koopro.wizardsandbeasts.wand.allegiance;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A wand's allegiance history must come back from disk and from the network exactly as it left: nothing about
 * who a wand has served may be lost, duplicated or invented on a save, a restart or a sync.
 */
class WandBondHistoryTest {

    private static final UUID FIRST = UUID.nameUUIDFromBytes("gregorovitch".getBytes());
    private static final UUID FORMER = UUID.nameUUIDFromBytes("grindelwald".getBytes());
    private static final UUID CHALLENGER = UUID.nameUUIDFromBytes("dumbledore".getBytes());

    @Test
    void aFullHistory_survivesTheDiskCodec() {
        WandBondHistory history = new WandBondHistory(FIRST, FORMER, CHALLENGER, 2, 123_456L);
        JsonElement json = WandBondHistory.CODEC.encodeStart(JsonOps.INSTANCE, history).getOrThrow();
        assertEquals(history, WandBondHistory.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void anEmptyHistory_survivesTheDiskCodecAndWritesNothingSpurious() {
        JsonElement json = WandBondHistory.CODEC.encodeStart(JsonOps.INSTANCE, WandBondHistory.EMPTY).getOrThrow();
        assertEquals(WandBondHistory.EMPTY, WandBondHistory.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertNull(json.getAsJsonObject().get("first_master"));
    }

    @Test
    void aFullHistory_survivesTheNetworkCodec() {
        WandBondHistory history = new WandBondHistory(FIRST, null, CHALLENGER, 1, 9L);
        ByteBuf buf = Unpooled.buffer();
        WandBondHistory.STREAM_CODEC.encode(buf, history);
        assertEquals(history, WandBondHistory.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes(), "the codec must read exactly what it wrote");
    }

    @Test
    void aCorruptUuidOnDisk_readsAsAbsentRatherThanFailingTheWand() {
        JsonElement json = com.google.gson.JsonParser.parseString(
                "{\"first_master\":\"not-a-uuid\",\"challenger_wins\":3}");
        WandBondHistory history = WandBondHistory.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertNull(history.firstMaster());
        assertEquals(0, history.challengerWins(), "wins without a challenger are meaningless and dropped");
    }

    @Test
    void theFirstMaster_isRecordedOnceAndNeverOverwritten() {
        WandBondHistory history = WandBondHistory.EMPTY.withFirstMasterIfAbsent(FIRST).withFirstMasterIfAbsent(FORMER);
        assertEquals(FIRST, history.firstMaster());
    }

    @Test
    void aTransfer_remembersTheFormerMasterAndClearsTheChallenge() {
        WandBondHistory challenged = WandBondHistory.EMPTY.withFirstMasterIfAbsent(FIRST).withDefeatBy(CHALLENGER);
        WandBondHistory won = challenged.afterTransferFrom(FIRST);
        assertEquals(FIRST, won.firstMaster());
        assertEquals(FIRST, won.formerMaster());
        assertNull(won.challenger());
        assertEquals(0, won.challengerWins());
        assertEquals(0L, won.lastMasterUseTick(), "the new master has not cast with it yet");
    }
}
