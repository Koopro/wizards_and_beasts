package at.koopro.wizardsandbeasts.felix;

import at.koopro.wizardsandbeasts.brew.effect.BrewEffect;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fortune state machine, and the component that starts it.
 *
 * <p>The hooks themselves need a level and a player — a near-death save reads health and cancels a
 * damage event, an ore drop needs a block break. What is pinned here is the part that decides
 * <em>whether</em> anything lucky may happen: the state transitions, the cooldown arithmetic, and the
 * JSON that a datapack authors. Those are where the behaviour actually lives; the hooks are three
 * lines each on top of them.
 */
class FelixFortuneTest {

    // ── the state machine ──────────────────────────────────────────────────────────────────

    @Test
    void noStateMeansNoLuck() {
        assertFalse(FelixState.NONE.isActive());
        assertFalse(FelixState.NONE.isOnCooldown(0L));
        assertFalse(FelixState.NONE.isOnCooldown(1_000_000L));
    }

    @Test
    void anActiveStateCountsDownToInactive() {
        FelixState state = new FelixState(3, 2, 0L, Long.MIN_VALUE);
        assertTrue(state.isActive());
        assertFalse(state.withTicks(0).isActive());
    }

    @Test
    void tickingNeverGoesNegative() {
        assertEquals(0, new FelixState(1, 1, 0L, Long.MIN_VALUE).withTicks(-5).ticksRemaining());
    }

    @Test
    void expiringClearsTheLuckButKeepsTheCooldown() {
        // The cooldown outliving the luck is the entire anti-chain mechanism. If expiry reset it, a
        // player could drink the moment the last one ran out, forever.
        FelixState expired = new FelixState(1, 3, 0L, 500L).expired(9000L);

        assertFalse(expired.isActive());
        assertEquals(0, expired.strength(), "strength must not survive the potion");
        assertEquals(9000L, expired.cooldownUntil());
        assertTrue(expired.isOnCooldown(8999L));
        assertFalse(expired.isOnCooldown(9000L), "the cooldown ends at its own tick, not after it");
    }

    @Test
    void aCooldownFromTheFutureIsIgnoredRatherThanObeyed() {
        // Game time moves backwards across a world restore or a backup rollback. An unguarded
        // comparison would bar the player for the difference — possibly for the world's whole life.
        FelixState absurd = new FelixState(0, 0, Long.MAX_VALUE / 2, Long.MIN_VALUE);
        assertFalse(absurd.isOnCooldown(0L),
                "a cooldown further out than any sane one must be treated as corrupt");
    }

    @Test
    void aCooldownJustInsideTheSaneWindowIsStillObeyed() {
        long now = 1000L;
        FelixState state = new FelixState(0, 0, now + FelixFortune.MAX_SANE_COOLDOWN - 1, Long.MIN_VALUE);
        assertTrue(state.isOnCooldown(now));
    }

    @Test
    void theSaveTimestampRidesOnTheStateSoItSurvivesARelog() {
        // Held on the attachment rather than in a static map, so logging out cannot reset the
        // internal cooldown on the near-death save.
        assertEquals(4242L, FelixState.NONE.withSaveAt(4242L).lastSaveGameTime());
    }

    // ── balance shape ──────────────────────────────────────────────────────────────────────

    @Test
    void theDurationIsMinutesNotHalfAnHour() {
        // The brief asked for 2-5 minutes. A thirty-minute Felix is a permanent buff with extra steps.
        assertTrue(FelixFortune.DEFAULT_DURATION_TICKS >= 20 * 120);
        assertTrue(FelixFortune.DEFAULT_DURATION_TICKS <= 20 * 300);
    }

    @Test
    void theCooldownOutlastsTheDose() {
        // Otherwise Felix is simply always on for anybody with two bottles.
        assertTrue(FelixFortune.DEFAULT_COOLDOWN_TICKS > FelixFortune.DEFAULT_DURATION_TICKS,
                "a cooldown shorter than the potion would let it be chained back to back");
    }

    @Test
    void anOverdoseIsPunishedHarderThanAnOrdinaryDose() {
        assertTrue(FelixFortune.OVERDOSE_COOLDOWN_TICKS > FelixFortune.DEFAULT_COOLDOWN_TICKS);
    }

    @Test
    void theNearDeathSaveIsRateLimited() {
        // A save every tick is invulnerability. Ten seconds makes it a near miss.
        assertTrue(FelixFortune.SAVE_INTERNAL_COOLDOWN >= 100);
    }

    @Test
    void strengthIsCappedSoAMisconfiguredDatapackCannotTrivialiseIt() {
        assertEquals(3, FelixFortune.MAX_STRENGTH);
    }

    // ── the component ──────────────────────────────────────────────────────────────────────

    private static BrewEffect parse(String json) {
        return BrewEffect.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void theComponentParsesFromItsDiscriminator() {
        BrewEffect effect = parse("""
                {"type":"felix_felicis","durationTicks":3600,"strength":2,"cooldownTicks":6000,"overdose":"overdose"}""");
        BrewEffect.FelixFelicis felix = assertInstanceOf(BrewEffect.FelixFelicis.class, effect);

        assertEquals(3600, felix.durationTicks());
        assertEquals(2, felix.strength());
        assertEquals(6000, felix.cooldownTicks());
        assertEquals(FelixFortune.OverdosePolicy.OVERDOSE, felix.overdose());
    }

    @Test
    void everyFieldHasAUsableDefault() {
        BrewEffect.FelixFelicis bare = (BrewEffect.FelixFelicis) parse("""
                {"type":"felix_felicis"}""");

        assertEquals(FelixFortune.DEFAULT_DURATION_TICKS, bare.durationTicks());
        assertEquals(1, bare.strength());
        assertEquals(FelixFortune.DEFAULT_COOLDOWN_TICKS, bare.cooldownTicks());
        assertEquals(FelixFortune.OverdosePolicy.OVERDOSE, bare.overdose(),
                "the default for a second bottle must be the punishing one, not the forgiving one");
    }

    @Test
    void everyOverdosePolicyIsAddressableFromJson() {
        for (FelixFortune.OverdosePolicy policy : FelixFortune.OverdosePolicy.values()) {
            BrewEffect.FelixFelicis parsed = (BrewEffect.FelixFelicis) parse(
                    "{\"type\":\"felix_felicis\",\"overdose\":\"" + policy.getSerializedName() + "\"}");
            assertEquals(policy, parsed.overdose());
        }
    }

    @Test
    void theComponentRoundTripsThroughItsCodec() {
        BrewEffect original = new BrewEffect.FelixFelicis(2400, 3, 7200,
                FelixFortune.OverdosePolicy.REFUSE);
        var encoded = BrewEffect.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(original, BrewEffect.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void theShippedFelixBrewUsesTheFortuneComponentAndNotAnEffectList() throws Exception {
        // The acceptance criterion in data form: Felix must not be Luck II wearing a famous name.
        String json = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/data/wizards_and_beasts/brews/felix_felicis.json"));
        var doc = JsonParser.parseString(json).getAsJsonObject();

        assertTrue(doc.has("components"), "Felix must declare components");
        boolean hasFortune = false;
        for (var element : doc.getAsJsonArray("components")) {
            if ("felix_felicis".equals(element.getAsJsonObject().get("type").getAsString())) {
                hasFortune = true;
            }
        }
        assertTrue(hasFortune, "Felix must carry the felix_felicis component");
        assertFalse(doc.has("effects") && !doc.getAsJsonArray("effects").isEmpty(),
                "Felix must no longer rely on a placeholder mob-effect list for its identity");
    }
}
