package at.koopro.wizardsandbeasts.render.outline;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server side of block highlights: the position copy a caller can get silently wrong, and when a highlight
 * ends. (The colour rule lives on {@link OutlineStyle} — see {@code OutlineAppearanceTest}.)
 */
class BlockOutlineTest {

    private static final UUID ALICE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID BOB = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");

    @BeforeEach
    void reset() {
        BlockOutlineService.reset();
    }

    // ── positions ──

    @Test
    void betweenClosedCanBePassedStraightIn() {
        // betweenClosed yields one MutableBlockPos; collected naively, all four entries are the last block.
        List<BlockPos> copy = BlockOutlineService.copyPositions(
                BlockPos.betweenClosed(new BlockPos(0, 0, 0), new BlockPos(1, 0, 1)));

        assertEquals(List.of(
                new BlockPos(0, 0, 0), new BlockPos(1, 0, 0),
                new BlockPos(0, 0, 1), new BlockPos(1, 0, 1)), copy);
    }

    // ── expiry ──

    @Test
    void aHighlightLastsUntilItsExpiryTickAndNotOneTickLonger() {
        BlockOutlineService.track(ALICE, 7, 100);

        assertTrue(BlockOutlineService.expire(99).isEmpty());
        assertEquals(1, BlockOutlineService.activeCount(ALICE));

        assertEquals(Map.of(ALICE, List.of(7)), BlockOutlineService.expire(100));
        assertEquals(0, BlockOutlineService.activeCount(ALICE));
    }

    @Test
    void onlyTheDueHighlightEndsAndOnlyItsViewerIsTold() {
        BlockOutlineService.track(ALICE, 1, 100);
        BlockOutlineService.track(ALICE, 2, 200);
        BlockOutlineService.track(BOB, 3, 200);

        assertEquals(Map.of(ALICE, List.of(1)), BlockOutlineService.expire(150));
        assertEquals(1, BlockOutlineService.activeCount(ALICE));
        assertEquals(1, BlockOutlineService.activeCount(BOB));
    }

    @Test
    void aViewerWithNothingLeftIsDroppedSoTheTickCanReturnEarly() {
        BlockOutlineService.track(ALICE, 1, 100);
        BlockOutlineService.expire(100);

        // A second pass must find nothing at all, not an empty map it has to walk.
        assertTrue(BlockOutlineService.expire(Long.MAX_VALUE).isEmpty());
    }
}
