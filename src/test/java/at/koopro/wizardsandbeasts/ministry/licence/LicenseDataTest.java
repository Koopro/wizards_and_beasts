package at.koopro.wizardsandbeasts.ministry.licence;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The document itself: what survives a save, what survives the wire, and what a licence actually
 * authorises.
 *
 * <p>The one that earns its keep is {@link #aForgeryAuthorisesUntilItIsCaught}. A forgery that failed
 * {@code authorises} would be useless on its first use and the whole fifteen-percent mechanic would
 * never fire, so the fact that {@code forged} is <em>not</em> consulted there is load-bearing rather
 * than an omission.
 */
class LicenseDataTest {

    private static final Gson GSON = new Gson();
    private static final UUID HOLDER = UUID.fromString("0d4a1e6c-1111-4222-8333-444455556666");
    private static final UUID SOMEBODY_ELSE = UUID.fromString("99999999-1111-4222-8333-444455556666");

    @Test
    void aFreshLicenceIsRankZeroPermanentGenuineAndInForce() {
        LicenseData licence = LicenseData.issue(LicenseType.APPARITION, HOLDER);

        assertEquals(0, licence.rank());
        assertTrue(licence.isPermanent());
        assertFalse(licence.forged());
        assertFalse(licence.revoked());
        assertTrue(licence.authorises(HOLDER, 1_000_000L));
    }

    @Test
    void aLicenceOnlyEverAuthorisesTheWizardNamedOnIt() {
        LicenseData licence = LicenseData.issue(LicenseType.BROOM, HOLDER);

        assertTrue(licence.authorises(HOLDER, 0L));
        assertFalse(licence.authorises(SOMEBODY_ELSE, 0L),
                "picking up somebody else's papers must not licence you");
    }

    @Test
    void revocationIsTerminal() {
        LicenseData revoked = LicenseData.issue(LicenseType.ANIMAGUS, HOLDER).revokedCopy();

        assertTrue(revoked.revoked());
        assertFalse(revoked.authorises(HOLDER, 0L));
        // Endorsing a struck-off licence must not quietly bring it back.
        assertTrue(revoked.withRank(3).revoked());
    }

    @Test
    void aForgeryAuthorisesUntilItIsCaught() {
        LicenseData forgery = LicenseData.issue(LicenseType.MINISTRY_ACCESS, HOLDER).forgedCopy();

        assertTrue(forgery.forged());
        assertTrue(forgery.authorises(HOLDER, 0L),
                "a forgery that does not work is not a forgery; detection is what stops it");
        assertFalse(forgery.revokedCopy().authorises(HOLDER, 0L));
    }

    @Test
    void aPermanentLicenceNeverLapsesAndADatedOneDoes() {
        LicenseData permanent = LicenseData.issue(LicenseType.DANGEROUS_CREATURES, HOLDER);
        assertFalse(permanent.hasExpired(Long.MAX_VALUE));

        LicenseData dated = new LicenseData(
                LicenseType.DANGEROUS_CREATURES, 1, HOLDER, 500L, false, false);
        assertFalse(dated.hasExpired(499L));
        assertTrue(dated.hasExpired(500L), "the expiry tick is the first tick it is no longer valid");
        assertFalse(dated.authorises(HOLDER, 500L));
    }

    @Test
    void rankIsClampedToTheEndorsementLadder() {
        assertEquals(LicenseType.MAX_RANK,
                new LicenseData(LicenseType.BROOM, 99, HOLDER, LicenseData.NEVER_EXPIRES, false, false).rank());
        assertEquals(0,
                new LicenseData(LicenseType.BROOM, -4, HOLDER, LicenseData.NEVER_EXPIRES, false, false).rank());
    }

    @Test
    void anyNegativeExpiryMeansPermanent() {
        // Otherwise a hand-edited -7 would be an expiry in the distant past and would silently
        // invalidate the licence rather than reading as "no expiry".
        LicenseData odd = new LicenseData(LicenseType.BROOM, 0, HOLDER, -7L, false, false);
        assertTrue(odd.isPermanent());
        assertEquals(LicenseData.NEVER_EXPIRES, odd.expiryTick());
    }

    @Test
    void survivesTheSave() {
        LicenseData original = new LicenseData(LicenseType.AUROR_TRAINEE, 2, HOLDER, 12345L, true, true);

        JsonElement json = LicenseData.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(m -> new AssertionError("encode failed: " + m));
        LicenseData round = LicenseData.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(m -> new AssertionError("decode failed: " + m));

        assertEquals(original, round, "round trip changed the document: " + GSON.toJson(json));
    }

    @Test
    void aLicenceWrittenBeforeTheOptionalFieldsExistedStillLoads() {
        // Only type and issued_to are required. A save that predates rank/expiry/revoked/forged must
        // read back as a plain permanent licence rather than failing and blanking the scroll.
        JsonElement minimal = GSON.fromJson(
                "{\"type\":\"broom\",\"issued_to\":\"" + HOLDER + "\"}", JsonElement.class);
        LicenseData parsed = LicenseData.CODEC.parse(JsonOps.INSTANCE, minimal)
                .getOrThrow(m -> new AssertionError("legacy document failed to load: " + m));

        assertEquals(LicenseType.BROOM, parsed.type());
        assertEquals(0, parsed.rank());
        assertTrue(parsed.isPermanent());
        assertFalse(parsed.revoked());
        assertFalse(parsed.forged());
    }

    @Test
    void survivesTheWire() {
        for (boolean revoked : new boolean[] {false, true}) {
            for (boolean forged : new boolean[] {false, true}) {
                LicenseData original =
                        new LicenseData(LicenseType.RESTRICTED_SUBSTANCES, 3, HOLDER, 987L, revoked, forged);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                LicenseData.STREAM_CODEC.encode(buf, original);
                assertEquals(original, LicenseData.STREAM_CODEC.decode(buf),
                        "revoked=" + revoked + " forged=" + forged + " did not survive the wire");
                assertEquals(0, buf.readableBytes(), "stream codec left bytes on the buffer");
            }
        }
    }

    @Test
    void everyLicenceTypeNamesAnExamSubjectAndASerialisedName() {
        for (LicenseType type : LicenseType.values()) {
            assertEquals(type, LicenseType.byName(type.getSerializedName()),
                    type + " does not round-trip through its serialised name");
            // The upgrade ladder reads this on every endorsement; a null would be an NPE in play.
            assertNotEquals(null, type.examinedSubject());
        }
    }
}
