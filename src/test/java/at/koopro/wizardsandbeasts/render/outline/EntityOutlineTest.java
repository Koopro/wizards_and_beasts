package at.koopro.wizardsandbeasts.render.outline;

import at.koopro.wizardsandbeasts.client.render.outline.ClientOutlineState;
import at.koopro.wizardsandbeasts.client.render.outline.EntityOutlines;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The two rules a coloured outline can get silently wrong.
 *
 * <p>The renderer treats {@code outlineColor == 0} as "no outline", so an unpacked colour disappears
 * with no error anywhere in the chain, and {@code 0} doubles as the sentinel in every map along the
 * way. Both are pinned here because neither failure mode produces a stack trace to follow.
 */
class EntityOutlineTest {

    private static final UUID ALICE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID BOB = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");

    @BeforeEach
    void reset() {
        ClientOutlineState.clear();
    }

    // ── packing ──

    @Test
    void packingMakesEveryColourOpaqueSoNoneReadBackAsNoOutline() {
        assertNotEquals(EntityOutlines.NO_OUTLINE, EntityOutlineService.pack(0xFF0000));
        assertNotEquals(EntityOutlines.NO_OUTLINE, EntityOutlineService.pack(0x00FF00));
    }

    @Test
    void blackIsAUsableOutlineColour() {
        // The whole reason pack() exists. Bare 0x000000 is indistinguishable from NO_OUTLINE; the
        // opaque alpha is what keeps a black outline from being dropped as "no outline at all".
        assertEquals(0xFF000000, EntityOutlineService.pack(0x000000));
        assertNotEquals(EntityOutlines.NO_OUTLINE, EntityOutlineService.pack(0x000000));
    }

    @Test
    void packingKeepsTheRequestedRgb() {
        assertEquals(0xFF3E1F47, EntityOutlineService.pack(0x3E1F47));
    }

    @Test
    void strayHighBitsCannotCorruptTheAlpha() {
        // A caller passing an already-packed ARGB by mistake must not end up with a transparent outline.
        assertEquals(0xFF123456, EntityOutlineService.pack(0xAB123456));
    }

    // ── the hash colour ──

    @Test
    void theHashColourIsStablePerPlayerAndDiffersBetweenPlayers() {
        assertEquals(EntityOutlineService.hashColor(ALICE), EntityOutlineService.hashColor(ALICE));
        assertNotEquals(EntityOutlineService.hashColor(ALICE), EntityOutlineService.hashColor(BOB));
    }

    @Test
    void theHashColourFitsIn24Bits() {
        // It is fed to pack(), which owns the alpha byte; a hash leaking into the top byte would
        // produce a colour nobody asked for.
        assertEquals(0, EntityOutlineService.hashColor(ALICE) & 0xFF000000);
    }

    // ── the client map ──

    @Test
    void anUnknownEntityHasNoOutlineSoVanillaIsLeftAlone() {
        assertEquals(EntityOutlines.NO_OUTLINE, ClientOutlineState.colorFor(ALICE));
    }

    @Test
    void zeroClearsAnEntryRatherThanStoringATransparentColour() {
        ClientOutlineState.put(ALICE, EntityOutlineService.pack(0xFF0000));
        assertNotEquals(EntityOutlines.NO_OUTLINE, ClientOutlineState.colorFor(ALICE));

        ClientOutlineState.put(ALICE, 0);
        assertEquals(EntityOutlines.NO_OUTLINE, ClientOutlineState.colorFor(ALICE));
    }

    @Test
    void replaceAllDropsEntriesMissingFromTheSnapshot() {
        ClientOutlineState.put(ALICE, EntityOutlineService.pack(0xFF0000));
        ClientOutlineState.put(BOB, EntityOutlineService.pack(0x00FF00));

        Map<UUID, Integer> snapshot = new LinkedHashMap<>();
        snapshot.put(BOB, EntityOutlineService.pack(0x0000FF));
        ClientOutlineState.replaceAll(snapshot);

        assertEquals(EntityOutlines.NO_OUTLINE, ClientOutlineState.colorFor(ALICE));
        assertEquals(EntityOutlineService.pack(0x0000FF), ClientOutlineState.colorFor(BOB));
    }
}
