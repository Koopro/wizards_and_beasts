package at.koopro.wizardsandbeasts.render.outline;

import at.koopro.wizardsandbeasts.client.render.outline.ClientOutlineState;
import at.koopro.wizardsandbeasts.client.render.outline.EntityOutlines;
import at.koopro.wizardsandbeasts.network.outline.EntityOutlineS2CPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules an entity outline can get silently wrong.
 *
 * <p>The renderer treats {@code outlineColor == 0} as "no outline", so an unpacked colour disappears with no
 * error anywhere in the chain, and {@code 0} doubles as the clear in every map along the way. Timed expiry is
 * pinned for the same reason: an outline that outlives its duration, takes a debug outline down with it, or is
 * cut short by a second cast looks like a render quirk rather than a bug.
 */
class EntityOutlineTest {

    private static final UUID ALICE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID BOB = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");

    private static final int RED = EntityOutlineService.pack(0xFF0000);
    private static final int GREEN = EntityOutlineService.pack(0x00FF00);
    private static final int BLUE = EntityOutlineService.pack(0x0000FF);

    @BeforeEach
    void reset() {
        ClientOutlineState.clear();
        EntityOutlineService.reset();
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

    // ── the debug hash colour ──

    @Test
    void theHashColourIsStablePerPlayerAndDiffersBetweenPlayers() {
        assertEquals(DebugOutlines.hashColor(ALICE), DebugOutlines.hashColor(ALICE));
        assertNotEquals(DebugOutlines.hashColor(ALICE), DebugOutlines.hashColor(BOB));
    }

    @Test
    void theHashColourFitsIn24Bits() {
        // It is fed to pack(), which owns the alpha byte; a hash leaking into the top byte would
        // produce a colour nobody asked for.
        assertEquals(0, DebugOutlines.hashColor(ALICE) & 0xFF000000);
    }

    // ── timed layer ──

    @Test
    void aTimedOutlineLastsUntilItsExpiryTickAndNotOneTickLonger() {
        EntityOutlineService.putTimed(ALICE, RED, 100);

        assertTrue(EntityOutlineService.expire(99).isEmpty());
        assertEquals(new OutlineEntry(RED, 100), EntityOutlineService.effective(ALICE));

        assertEquals(Map.of(ALICE, OutlineEntry.NONE), EntityOutlineService.expire(100));
        assertTrue(EntityOutlineService.effective(ALICE).isNone());
    }

    @Test
    void expiryHandsBackToThePermanentOutlineInsteadOfClearingIt() {
        // The reason the service sends an effective outline rather than a bare clear on expiry: a debug
        // outline set before the timed one must survive it — and must not start fading, since it is permanent.
        EntityOutlineService.putPermanent(ALICE, BLUE);
        EntityOutlineService.putTimed(ALICE, RED, 100);
        assertEquals(RED, EntityOutlineService.effective(ALICE).argb());

        assertEquals(Map.of(ALICE, OutlineEntry.permanent(BLUE)), EntityOutlineService.expire(100));
    }

    @Test
    void aSecondCastNeverShortensTheFirst() {
        // Two reveals a second apart must read as one; a short cast must not cut a long one off.
        EntityOutlineService.putTimed(ALICE, RED, 200);
        EntityOutlineService.putTimed(ALICE, GREEN, 150);

        assertEquals(new OutlineEntry(GREEN, 200), EntityOutlineService.effective(ALICE));
        assertTrue(EntityOutlineService.expire(199).isEmpty());
    }

    @Test
    void aLongerSecondCastExtendsTheFirst() {
        EntityOutlineService.putTimed(ALICE, RED, 100);
        EntityOutlineService.putTimed(ALICE, GREEN, 200);

        assertTrue(EntityOutlineService.expire(150).isEmpty());
        assertEquals(new OutlineEntry(GREEN, 200), EntityOutlineService.effective(ALICE));
    }

    @Test
    void onlyTheOutlinesThatAreDueExpire() {
        EntityOutlineService.putTimed(ALICE, RED, 100);
        EntityOutlineService.putTimed(BOB, GREEN, 200);

        assertEquals(Map.of(ALICE, OutlineEntry.NONE), EntityOutlineService.expire(150));
        assertEquals(GREEN, EntityOutlineService.effective(BOB).argb());
    }

    @Test
    void theJoinSnapshotCarriesEachEntitysEffectiveOutline() {
        EntityOutlineService.putPermanent(ALICE, BLUE);
        EntityOutlineService.putTimed(ALICE, RED, 100);
        EntityOutlineService.putTimed(BOB, GREEN, 100);

        assertEquals(Map.of(ALICE, new OutlineEntry(RED, 100), BOB, new OutlineEntry(GREEN, 100)),
                EntityOutlineService.snapshot());
    }

    @Test
    void holdsSeesEitherLayerSoLogoutKnowsWhetherToBroadcast() {
        assertFalse(EntityOutlineService.holds(ALICE));
        EntityOutlineService.putPermanent(ALICE, BLUE);
        EntityOutlineService.putTimed(BOB, GREEN, 100);

        assertTrue(EntityOutlineService.holds(ALICE));
        assertTrue(EntityOutlineService.holds(BOB));
    }

    @Test
    void resetForgetsBothLayers() {
        EntityOutlineService.putPermanent(ALICE, BLUE);
        EntityOutlineService.putTimed(BOB, GREEN, 100);

        EntityOutlineService.reset();

        assertTrue(EntityOutlineService.snapshot().isEmpty());
        assertTrue(EntityOutlineService.expire(Long.MAX_VALUE).isEmpty());
    }

    // ── the wire ──

    @Test
    void anEntrySurvivesTheWireWithItsExpiry() {
        Map<UUID, OutlineEntry> sent = new LinkedHashMap<>();
        sent.put(ALICE, new OutlineEntry(RED, 1234L));
        sent.put(BOB, OutlineEntry.permanent(BLUE));
        EntityOutlineS2CPayload payload = EntityOutlineS2CPayload.paginate(sent, false).get(0);

        ByteBuf buf = Unpooled.buffer();
        EntityOutlineS2CPayload.STREAM_CODEC.encode(buf, payload);
        assertEquals(payload, EntityOutlineS2CPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void anEmptyUpdateSendsNothingButAnEmptySnapshotStillReplaces() {
        assertTrue(EntityOutlineS2CPayload.paginate(Map.of(), false).isEmpty());
        assertEquals(1, EntityOutlineS2CPayload.paginate(Map.of(), true).size());
    }

    // ── the client map ──

    @Test
    void anUnknownEntityHasNoOutlineSoVanillaIsLeftAlone() {
        assertEquals(EntityOutlines.NO_OUTLINE, ClientOutlineState.colorFor(ALICE));
    }

    @Test
    void theClearRemovesAnEntryRatherThanStoringATransparentColour() {
        ClientOutlineState.put(ALICE, OutlineEntry.permanent(RED));
        assertNotEquals(EntityOutlines.NO_OUTLINE, ClientOutlineState.colorFor(ALICE));

        ClientOutlineState.put(ALICE, OutlineEntry.NONE);
        assertEquals(EntityOutlines.NO_OUTLINE, ClientOutlineState.colorFor(ALICE));
    }

    @Test
    void replaceAllDropsEntriesMissingFromTheSnapshot() {
        ClientOutlineState.put(ALICE, OutlineEntry.permanent(RED));
        ClientOutlineState.put(BOB, OutlineEntry.permanent(GREEN));

        ClientOutlineState.replaceAll(Map.of(BOB, OutlineEntry.permanent(BLUE)));

        assertEquals(EntityOutlines.NO_OUTLINE, ClientOutlineState.colorFor(ALICE));
        assertEquals(BLUE, ClientOutlineState.colorFor(BOB));
    }
}
