package at.koopro.wizardsandbeasts.ministry;

import at.koopro.wizardsandbeasts.ministry.data.MinistryRank;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.WantedLevel;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the Ministry's law model: which spells are crimes, how heat bands, that priors escalate, and — the
 * point of the whole design — that heat and the criminal file are separate things.
 */
class MinistryRecordTest {

    private static final Gson GSON = new Gson();

    // ── the law ──

    @Test
    void onlyTheUnforgivablesAreCrimesToCast() {
        assertEquals(MagicalOffence.AVADA_KEDAVRA, MagicalOffence.forSpell("avada_kedavra"));
        assertEquals(MagicalOffence.CRUCIO, MagicalOffence.forSpell("crucio"));
        assertEquals(MagicalOffence.IMPERIO, MagicalOffence.forSpell("imperio"));
        assertEquals(MagicalOffence.CRUCIO, MagicalOffence.forSpell("wizards_and_beasts:crucio"));

        for (String legal : new String[] {"lumos", "stupefy", "incendio", "confringo", "expecto_patronum"}) {
            assertNull(MagicalOffence.forSpell(legal), legal + " is not a crime");
        }
    }

    @Test
    void killingIsWorseThanTortureIsWorseThanControl() {
        assertTrue(MagicalOffence.AVADA_KEDAVRA.notoriety() > MagicalOffence.CRUCIO.notoriety());
        assertTrue(MagicalOffence.CRUCIO.notoriety() > MagicalOffence.IMPERIO.notoriety());
    }

    @Test
    void paperworkOffencesDoNotSendAurors() {
        assertFalse(MagicalOffence.UNREGISTERED_ANIMAGUS.arrestable());
        assertTrue(MagicalOffence.AVADA_KEDAVRA.arrestable());
        assertTrue(MagicalOffence.AZKABAN_BREAKOUT.arrestable());
    }

    // ── banding ──

    @Test
    void wantedBandsAscendAndOnlyTheHigherOnesDispatch() {
        assertEquals(WantedLevel.CLEAR, WantedLevel.forNotoriety(0f));
        assertEquals(WantedLevel.OF_INTEREST, WantedLevel.forNotoriety(20f));
        assertEquals(WantedLevel.WANTED, WantedLevel.forNotoriety(45f));
        assertEquals(WantedLevel.DANGEROUS, WantedLevel.forNotoriety(75f));
        assertEquals(WantedLevel.UNDESIRABLE, WantedLevel.forNotoriety(95f));

        assertFalse(WantedLevel.CLEAR.dispatchesAurors());
        assertFalse(WantedLevel.OF_INTEREST.dispatchesAurors());
        assertTrue(WantedLevel.WANTED.dispatchesAurors());
    }

    @Test
    void escalationIsInNumbersNotPower() {
        assertEquals(0, WantedLevel.OF_INTEREST.aurorsPerDispatch());
        assertEquals(1, WantedLevel.WANTED.aurorsPerDispatch());
        assertEquals(2, WantedLevel.DANGEROUS.aurorsPerDispatch());
        assertEquals(3, WantedLevel.UNDESIRABLE.aurorsPerDispatch());
    }

    // ── heat vs file: the core of the design ──

    @Test
    void anOffenceAddsHeatAndFilesItTogether() {
        PlayerMinistryRecord record = PlayerMinistryRecord.DEFAULT
                .withOffence(MagicalOffence.CRUCIO, MagicalOffence.CRUCIO.notoriety());

        assertEquals(1, record.offenceCount(MagicalOffence.CRUCIO));
        assertEquals(MagicalOffence.CRUCIO.notoriety(), record.notoriety(), 1e-4);
        assertEquals(1, record.totalOffences());
    }

    @Test
    void priorsEscalateButAreCapped() {
        PlayerMinistryRecord clean = PlayerMinistryRecord.DEFAULT;
        assertEquals(1.0f, clean.repeatMultiplier(MagicalOffence.CRUCIO), 1e-4, "a first offence is unscaled");

        PlayerMinistryRecord once = clean.withOffence(MagicalOffence.CRUCIO, 0f);
        assertTrue(once.repeatMultiplier(MagicalOffence.CRUCIO) > 1.0f, "priors bite harder");

        PlayerMinistryRecord career = clean;
        for (int i = 0; i < 40; i++) {
            career = career.withOffence(MagicalOffence.CRUCIO, 0f);
        }
        assertEquals(2.0f, career.repeatMultiplier(MagicalOffence.CRUCIO), 1e-4, "capped at double");
    }

    @Test
    void priorsAreTrackedPerOffenceNotInTotal() {
        PlayerMinistryRecord record = PlayerMinistryRecord.DEFAULT
                .withOffence(MagicalOffence.CRUCIO, 0f)
                .withOffence(MagicalOffence.CRUCIO, 0f);

        assertTrue(record.repeatMultiplier(MagicalOffence.CRUCIO) > 1.0f);
        assertEquals(1.0f, record.repeatMultiplier(MagicalOffence.IMPERIO), 1e-4,
                "a Crucio conviction must not escalate an unrelated first offence");
    }

    @Test
    void aPardonClearsHeatButNeverTheFile() {
        PlayerMinistryRecord record = PlayerMinistryRecord.DEFAULT
                .withOffence(MagicalOffence.AVADA_KEDAVRA, MagicalOffence.AVADA_KEDAVRA.notoriety())
                .withFugitive(true)
                .withSentenceTicks(500);

        PlayerMinistryRecord pardoned = record.pardoned();

        assertEquals(0f, pardoned.notoriety(), 1e-4);
        assertFalse(pardoned.fugitive());
        assertFalse(pardoned.isServingSentence());
        assertEquals(1, pardoned.offenceCount(MagicalOffence.AVADA_KEDAVRA),
                "the Ministry keeps files even after a pardon");
    }

    @Test
    void notorietyIsClampedToTheBand() {
        PlayerMinistryRecord over = PlayerMinistryRecord.DEFAULT.withNotoriety(500f);
        assertEquals(PlayerMinistryRecord.MAX_NOTORIETY, over.notoriety(), 1e-4);
        assertEquals(0f, PlayerMinistryRecord.DEFAULT.withNotoriety(-20f).notoriety(), 1e-4);
    }

    // ── rank authority ──

    @Test
    void rankAuthorityIsOrdered() {
        assertFalse(MinistryRank.NONE.mayArrest());
        assertTrue(MinistryRank.AUROR.mayArrest());
        assertFalse(MinistryRank.AUROR.mayPardon(), "catching people is not the same as forgiving them");
        assertTrue(MinistryRank.MAGICAL_LAW_ENFORCEMENT.mayPardon());
        assertTrue(MinistryRank.MINISTER.mayAppoint());
        assertFalse(MinistryRank.MAGICAL_LAW_ENFORCEMENT.mayAppoint());
        assertTrue(MinistryRank.MINISTER.mayArrest(), "higher ranks keep lower authority");
    }

    // ── persistence ──

    @Test
    void theRecordSurvivesARoundTrip() {
        PlayerMinistryRecord original = PlayerMinistryRecord.DEFAULT
                .withOffence(MagicalOffence.IMPERIO, 28f)
                .withOffence(MagicalOffence.AZKABAN_BREAKOUT, 40f)
                .withSentenceTicks(1234)
                .withFugitive(true)
                .withRank(MinistryRank.AUROR);

        JsonElement encoded = PlayerMinistryRecord.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg));
        PlayerMinistryRecord restored = PlayerMinistryRecord.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson(GSON.toJson(encoded), JsonElement.class))
                .getOrThrow(msg -> new AssertionError("parse failed: " + msg));

        assertEquals(original.notoriety(), restored.notoriety(), 1e-4);
        assertEquals(1, restored.offenceCount(MagicalOffence.IMPERIO));
        assertEquals(1, restored.offenceCount(MagicalOffence.AZKABAN_BREAKOUT));
        assertEquals(1234, restored.sentenceTicks());
        assertTrue(restored.fugitive());
        assertEquals(MinistryRank.AUROR, restored.rank());
    }

    @Test
    void anEmptyRecordIsTheDefault() {
        PlayerMinistryRecord restored = PlayerMinistryRecord.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson("{}", JsonElement.class))
                .getOrThrow(msg -> new AssertionError(msg));
        assertNotNull(restored);
        assertEquals(WantedLevel.CLEAR, restored.wantedLevel());
        assertEquals(MinistryRank.NONE, restored.rank());
        assertFalse(restored.isServingSentence());
    }
}
