package at.koopro.wizardsandbeasts.network.outline;

import at.koopro.wizardsandbeasts.render.outline.OutlineEntry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockOutlineS2CPayloadTest {

    private static final OutlineEntry YELLOW = new OutlineEntry(0x80FFFFAA, 123_456L);

    private static BlockOutlineS2CPayload roundTrip(BlockOutlineS2CPayload payload) {
        ByteBuf buf = Unpooled.buffer();
        BlockOutlineS2CPayload.STREAM_CODEC.encode(buf, payload);
        BlockOutlineS2CPayload decoded = BlockOutlineS2CPayload.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "decoder left bytes unread");
        return decoded;
    }

    @Test
    void everyActionSurvivesTheWireExpiryIncluded() {
        BlockOutlineS2CPayload add = BlockOutlineS2CPayload.pages(
                42, YELLOW, List.of(new BlockPos(1, -60, 3), new BlockPos(-30_000_000, 319, 29_999_999))).get(0);

        assertEquals(add, roundTrip(add));
        assertEquals(BlockOutlineS2CPayload.remove(42), roundTrip(BlockOutlineS2CPayload.remove(42)));
        assertEquals(BlockOutlineS2CPayload.clearAll(), roundTrip(BlockOutlineS2CPayload.clearAll()));
    }

    @Test
    void aLargeHighlightIsSplitIntoPagesOfOneIdThatAddBackUp() {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            positions.add(new BlockPos(i, 0, 0));
        }

        List<BlockOutlineS2CPayload> pages = BlockOutlineS2CPayload.pages(9, YELLOW, positions);

        assertEquals(List.of(1024, 1024, 452), pages.stream().map(p -> p.positions().size()).toList());
        assertTrue(pages.stream().allMatch(p -> p.highlightId() == 9 && p.outline().equals(YELLOW)
                && p.action() == BlockOutlineS2CPayload.Action.ADD));
        assertEquals(positions, pages.stream().flatMap(p -> p.positions().stream()).toList());
    }

    @Test
    void nothingToHighlightSendsNothing() {
        assertTrue(BlockOutlineS2CPayload.pages(1, YELLOW, List.of()).isEmpty());
    }

    @Test
    void aMutablePositionIsFrozenWhenThePayloadIsBuilt() {
        // An integrated server never encodes the payload, so the client reads this very object.
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(1, 2, 3);
        BlockOutlineS2CPayload payload = BlockOutlineS2CPayload.pages(1, YELLOW, List.of(cursor)).get(0);

        cursor.set(9, 9, 9);

        assertEquals(new BlockPos(1, 2, 3), payload.positions().get(0));
    }

    @Test
    void theDecoderRefusesAnOversizedPage() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(BlockOutlineS2CPayload.Action.ADD.ordinal());
        buf.writeInt(1);
        buf.writeInt(0xFFFFFFFF);
        buf.writeLong(0L);
        buf.writeInt(BlockOutlineS2CPayload.MAX_POSITIONS_PER_PAGE + 1);

        assertThrows(IllegalArgumentException.class, () -> BlockOutlineS2CPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void theDecoderRefusesAnUnknownAction() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(99);

        assertThrows(IllegalArgumentException.class, () -> BlockOutlineS2CPayload.STREAM_CODEC.decode(buf));
    }
}
