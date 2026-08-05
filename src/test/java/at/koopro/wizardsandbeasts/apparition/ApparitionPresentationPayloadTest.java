package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTier;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionPresentationS2CPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Presentation payload: stream round-trip, nullable handling, and malformed-ordinal tolerance. */
class ApparitionPresentationPayloadTest {

    private static ApparitionPresentationS2CPayload roundTrip(ApparitionPresentationS2CPayload original) {
        ByteBuf buf = Unpooled.buffer();
        ApparitionPresentationS2CPayload.STREAM_CODEC.encode(buf, original);
        return ApparitionPresentationS2CPayload.STREAM_CODEC.decode(buf);
    }

    @Test
    void aResolutionSurvivesTheRoundTrip() {
        ApparitionPresentationS2CPayload original = new ApparitionPresentationS2CPayload(
                42, ApparitionTier.ANCHORED, ApparitionPhase.RESOLVING, 0, 70, 82,
                SplinchTier.MAJOR, new Vec3(1.5, 64.0, -2.5), new Vec3(900.25, 70.0, -12.75),
                17, ApparitionCrackVariant.ELF);

        ApparitionPresentationS2CPayload restored = roundTrip(original);

        assertEquals(original, restored);
        assertEquals(17, restored.radius());
        assertEquals(ApparitionCrackVariant.ELF, restored.crackVariant());
        assertEquals(new Vec3(900.25, 70.0, -12.75), restored.destination());
    }

    @Test
    void anInFlightChargeSurvivesWithBothNullablesAbsent() {
        ApparitionPresentationS2CPayload original = new ApparitionPresentationS2CPayload(
                7, ApparitionTier.BLINK, ApparitionPhase.DETERMINATION, 4, 10, 15,
                null, new Vec3(0, 64, 0), null, 32, ApparitionCrackVariant.WIZARD);

        ApparitionPresentationS2CPayload restored = roundTrip(original);

        assertEquals(original, restored);
        assertNull(restored.splinchTier());
        assertNull(restored.destination());
    }

    @Test
    void aCatastropheCarriesAnOutcomeButNoDestination() {
        ApparitionPresentationS2CPayload original = new ApparitionPresentationS2CPayload(
                9, ApparitionTier.BLINK, ApparitionPhase.RESOLVING, 0, 10, 15,
                SplinchTier.CATASTROPHIC, new Vec3(3, 64, 3), null, 8,
                ApparitionCrackVariant.MUFFLED);

        ApparitionPresentationS2CPayload restored = roundTrip(original);

        assertEquals(SplinchTier.CATASTROPHIC, restored.splinchTier());
        assertNull(restored.destination(), "a catastrophe never arrives, so it must carry no destination");
    }

    @Test
    void everyPhaseAndTierCombinationRoundTrips() {
        for (ApparitionTier tier : ApparitionTier.values()) {
            for (ApparitionPhase phase : ApparitionPhase.values()) {
                for (ApparitionCrackVariant variant : ApparitionCrackVariant.values()) {
                    ApparitionPresentationS2CPayload original = new ApparitionPresentationS2CPayload(
                            1, tier, phase, 3, 10, 15, null, Vec3.ZERO, null, 32, variant);
                    assertEquals(original, roundTrip(original));
                }
            }
        }
    }

    @Test
    void aGarbageEnumOrdinalDegradesRatherThanThrowing() {
        // A malformed or hostile packet must not throw on the netty thread.
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(1);        // casterId
        buf.writeInt(9999);     // tier ordinal, out of range
        buf.writeInt(-4);       // phase ordinal, out of range
        buf.writeInt(0);        // elapsed
        buf.writeInt(10);       // windowOpen
        buf.writeInt(15);       // windowClose
        buf.writeBoolean(false);
        buf.writeDouble(0);
        buf.writeDouble(64);
        buf.writeDouble(0);
        buf.writeBoolean(false);
        buf.writeInt(32);
        buf.writeInt(77);       // crackVariant ordinal, out of range

        ApparitionPresentationS2CPayload decoded = ApparitionPresentationS2CPayload.STREAM_CODEC.decode(buf);

        assertEquals(ApparitionTier.BLINK, decoded.tier());
        assertEquals(ApparitionPhase.IDLE, decoded.phase());
        assertEquals(ApparitionCrackVariant.WIZARD, decoded.crackVariant());
    }
}
