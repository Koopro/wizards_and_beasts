package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.apparition.charge.ApparitionCharge;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionWindow;
import at.koopro.wizardsandbeasts.apparition.charge.Destabilization;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchResolver;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTier;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPresentationState;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionPresentationS2CPayload;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Apparition acceptance list, as far as it can be taken without a running world.
 *
 * <p>Four of the eight criteria are decisions made by pure code and are tested here for real. Two are
 * observable through data that crosses the wire, and are tested by pushing a payload through the client
 * state the renderer reads. Two need a live level — a landing resolved against real blocks, and a player
 * actually dismounting a broom — and are covered here only by pinning the code path, with the in-world
 * check written down in {@link #MANUAL_CHECKS} rather than pretended at.
 *
 * <p>Criterion 3 was rewritten rather than met as first written. "Damage during WINDUP → cancel, no TP" is
 * not what ships and is not what was wanted once stated plainly: Apparition stays usable under fire, and
 * being hit instead <i>floors</i> the outcome — never clean, at least major while anchored, catastrophic
 * while carrying somebody. The rungs themselves are pinned in {@code SplinchResolverTest}; what is checked
 * here is the whole path from a release to an outcome.
 */
class ApparitionAcceptanceTest {

    /**
     * What still has to be checked by hand, and how. Not decoration — these are the two criteria this file
     * cannot honestly cover, written where the next person looks for coverage.
     */
    static final List<String> MANUAL_CHECKS = List.of(
            "Safe destination: aim at flat ground 10 blocks off, Apparate, and confirm you land on the "
                    + "block you aimed at, standing on its centre rather than beside it.",
            "Broom dismount: Apparate while riding a broom and confirm you stay at the destination rather "
                    + "than being pulled back to the broom on the following tick.",
            "Nearby observer: with a second client 10 blocks away and not the caster, confirm the twirl, "
                    + "the crack and the soot all play. Use a caster below 0.85 proficiency — above that "
                    + "the crack radius is under 10 blocks by design and only the twirl is expected.");

    private static final Path SERVER_LOGIC =
            Path.of("src/main/java/at/koopro/wizardsandbeasts/apparition/ApparitionServerLogic.java");

    private Map<Module, ModuleState> savedModules;

    @BeforeEach
    void captureModuleState() {
        savedModules = new EnumMap<>(ModuleManager.snapshot());
    }

    @AfterEach
    void restoreModuleState() {
        ModuleManager.acceptAuthoritative(savedModules);
        ClientApparitionPresentationState.clear();
    }

    private static void setModule(Module module, ModuleState state) {
        Map<Module, ModuleState> states = new EnumMap<>(ModuleManager.snapshot());
        states.put(module, state);
        ModuleManager.acceptAuthoritative(states);
    }

    // ── 1. module off → reject ────────────────────────────────────────────────

    /**
     * The module check is the first thing the gate does and returns before the player is touched, which is
     * what lets this run without one — the same idiom {@code MigratedAbilityBehaviorSeamTest} uses.
     */
    @Test
    void switchingTheApparitionModuleOffRejectsAnAttempt() {
        setModule(Module.APPARITION, ModuleState.DISABLED);
        assertEquals(ApparitionStartResult.REJECTED_MODULE_OFF,
                ApparitionServerLogic.evaluateStart(null));
    }

    @Test
    void switchingOffTheAbilityFrameworkAlsoRejects() {
        setModule(Module.PLAYER_ABILITIES, ModuleState.DISABLED);
        assertEquals(ApparitionStartResult.REJECTED_MODULE_OFF,
                ApparitionServerLogic.evaluateStart(null));
    }

    /** A module-off refusal says nothing: an absent feature does not scold. */
    @Test
    void theModuleRefusalIsSilent() {
        assertEquals(null, ApparitionStartResult.REJECTED_MODULE_OFF.messageKey());
    }

    // ── 2. out of range → reject ──────────────────────────────────────────────

    /**
     * Range is enforced by never casting the ray further than it, so "out of range" is not a refusal the
     * player is told about — it is a destination that never resolves and therefore a clock that never
     * starts. What is worth pinning is the curve itself.
     */
    @Test
    void blinkRangeRunsFromTwelveToThirtyBlocks() {
        assertEquals(12.0, ApparitionTier.BLINK.rangeBlocks(0.0f));
        assertEquals(30.0, ApparitionTier.BLINK.rangeBlocks(1.0f));
        assertEquals(21.0, ApparitionTier.BLINK.rangeBlocks(0.5f));
    }

    @Test
    void aNonsenseProficiencyCannotWidenOrShrinkTheRange() {
        assertEquals(12.0, ApparitionTier.BLINK.rangeBlocks(-5.0f));
        assertEquals(30.0, ApparitionTier.BLINK.rangeBlocks(99.0f));
    }

    @Test
    void anAnchoredJourneyHasNoRangeLimitWithinItsDimension() {
        assertFalse(ApparitionTier.ANCHORED.hasRangeLimit());
        assertEquals(Double.MAX_VALUE, ApparitionTier.ANCHORED.rangeBlocks(0.0f));
    }

    /** Crossing worlds is the one destination refusal that does speak, and it has a sentence to say. */
    @Test
    void anotherDimensionIsRefusedWithAMessage() {
        assertNotNull(ApparitionStartResult.REJECTED_OTHER_DIMENSION.messageKey());
    }

    // ── 3. damage during the wind-up ──────────────────────────────────────────

    @Test
    void onlyAnAnchoredHoldIsEndedByDamage() {
        assertTrue(ApparitionTier.ANCHORED.abortsOnDamage());
        assertFalse(ApparitionTier.BLINK.abortsOnDamage(),
                "a blink is a ten-tick hold; ending it on damage would make it uncastable in a fight, "
                        + "which is the opposite of what it is for");
    }

    /** The whole path a resolution takes: the ladder, then the under-fire floor, then the outcome. */
    private static SplinchTier outcome(int missTicks, Destabilization conditions, boolean anchored) {
        return SplinchResolver.floorForWindupDamage(
                SplinchResolver.resolve(missTicks, conditions),
                conditions.damageInstances(),
                anchored,
                conditions.sideAlong(),
                ApparitionRules.windupDamageMode());
    }

    @Test
    void windupDamage_floorsToMinorAndStillArrives() {
        Destabilization struckOnce = new Destabilization(1, false, false, false, false, false, true);
        SplinchTier tier = outcome(0, struckOnce, false);

        assertEquals(SplinchTier.MINOR, tier);
        assertTrue(tier.arrives(), "a wizard under fire can still get out; they simply cannot get out clean");
        assertTrue(tier.damage() > 0.0f);
    }

    @Test
    void anchoredWindupDamage_floorsToMajorAndStillArrives() {
        Destabilization struckOnce = new Destabilization(1, false, false, false, false, false, true);
        SplinchTier tier = outcome(0, struckOnce, true);

        assertEquals(SplinchTier.MAJOR, tier,
                "the anchored floor is the whole difference between a step and a journey under fire");
        assertTrue(tier.arrives());
    }

    @Test
    void twoHits_floorToMajorEvenOnABlink() {
        Destabilization struckTwice = new Destabilization(2, false, false, false, false, false, true);
        SplinchTier tier = outcome(0, struckTwice, false);

        assertEquals(SplinchTier.MAJOR, tier);
        assertTrue(tier.arrives());
    }

    @Test
    void windupDamageWhileCarrying_floorsToCatastropheAndDoesNotArrive() {
        Destabilization struckCarrying = new Destabilization(1, false, false, false, false, true, true);
        SplinchTier tier = outcome(0, struckCarrying, false);

        assertEquals(SplinchTier.CATASTROPHIC, tier);
        assertFalse(tier.arrives(), "a catastrophe leaves both of them where they stood");
    }

    @Test
    void noDamage_leavesACleanReleaseClean() {
        assertEquals(SplinchTier.CLEAN, outcome(0, Destabilization.NONE, true),
                "the floor must not touch a jump nobody interfered with");
    }

    // ── 5. cooldown blocks immediate re-use ───────────────────────────────────

    @Test
    void successFailureAndSplinchAreThreeDifferentCooldowns() {
        int success = ApparitionTier.BLINK.cooldownTicks();
        int failure = ApparitionServerLogic.FAILED_ATTEMPT_COOLDOWN_TICKS;
        int splinch = SplinchTier.CATASTROPHIC.lockoutTicks();

        assertTrue(success > 0 && failure > 0 && splinch > 0, "every outcome costs some wait");
        assertTrue(failure < success, "a jump that found nowhere to go must cost less than one that worked");
        assertTrue(splinch > ApparitionTier.ANCHORED.cooldownTicks(),
                "the splinch lockout has to outlast the tier cooldown or the max() that combines them "
                        + "would never pick it and being torn apart would cost nothing extra");
    }

    @Test
    void arrivingCleanlyStillCostsTheTiersOwnWait() {
        assertEquals(0, SplinchTier.CLEAN.lockoutTicks());
        assertEquals(40, ApparitionTier.BLINK.cooldownTicks());
        assertEquals(1200, ApparitionTier.ANCHORED.cooldownTicks());
    }

    // ── 6. a nearby player receives the FX ────────────────────────────────────

    /**
     * An observer's client is handed a finished jump and turns it into exactly one set of cues.
     *
     * <p>This is the half of "nearby player receives FX packet" that does not need two clients: the payload
     * is addressed to observers by the server, and what matters here is that a client which is <i>not</i>
     * the caster can reconstruct the crack, its carry and both ends of the journey from it — and does so
     * once.
     */
    @Test
    void anObserverReconstructsTheWholeJumpFromOnePayload() {
        ClientApparitionPresentationState.clear();
        int someoneElse = 77;
        ClientApparitionPresentationState.accept(new ApparitionPresentationS2CPayload(
                someoneElse, ApparitionTier.ANCHORED, ApparitionPhase.RESOLVING, 0, 70, 82,
                SplinchTier.MINOR, new Vec3(4.5, 64.0, 4.5), new Vec3(-120.5, 71.0, 88.5),
                24, ApparitionCrackVariant.MUFFLED));

        List<ClientApparitionPresentationState.Resolution> drained =
                ClientApparitionPresentationState.drainResolutions();

        assertEquals(1, drained.size());
        ClientApparitionPresentationState.Resolution event = drained.getFirst();
        assertEquals(someoneElse, event.casterId());
        assertEquals(ApparitionCrackVariant.MUFFLED, event.crackVariant());
        assertEquals(24, event.radius(), "the crack's carry has to survive, or every jump is equally loud");
        assertEquals(SplinchTier.MINOR, event.splinchTier());
        assertTrue(event.arrived());
        assertEquals(new Vec3(-120.5, 71.0, 88.5), event.destination());

        assertTrue(ClientApparitionPresentationState.drainResolutions().isEmpty(),
                "a resolution is one event and must be rendered exactly once");
    }

    /** The twirl finds a charge by entity id, so an observer can pose somebody who is not themselves. */
    @Test
    void anObserverCanFollowSomebodyElsesWindUp() {
        ClientApparitionPresentationState.clear();
        int someoneElse = 91;
        ClientApparitionPresentationState.accept(new ApparitionPresentationS2CPayload(
                someoneElse, ApparitionTier.BLINK, ApparitionPhase.DETERMINATION, 5, 10, 25,
                null, new Vec3(0, 64, 0), new Vec3(8.5, 64.0, 0.5), 32, ApparitionCrackVariant.WIZARD));

        ClientApparitionPresentationState.Charge charge =
                ClientApparitionPresentationState.charge(someoneElse);

        assertNotNull(charge, "no charge for that entity id — the pose pass would pose nobody");
        assertEquals(0.5f, charge.determinationProgress(), 0.001f);
        assertFalse(charge.isWindowOpen());
        assertNotNull(charge.destination());
    }

    /** A resolution clears the charge, so the twirl stops the instant the crack sounds. */
    @Test
    void resolvingClearsTheWindUpOnTheObserversClient() {
        ClientApparitionPresentationState.clear();
        int caster = 12;
        ClientApparitionPresentationState.accept(new ApparitionPresentationS2CPayload(
                caster, ApparitionTier.BLINK, ApparitionPhase.DETERMINATION, 5, 10, 25,
                null, new Vec3(0, 64, 0), new Vec3(8.5, 64.0, 0.5), 32, ApparitionCrackVariant.WIZARD));
        ClientApparitionPresentationState.accept(new ApparitionPresentationS2CPayload(
                caster, ApparitionTier.BLINK, ApparitionPhase.RESOLVING, 0, 10, 25,
                SplinchTier.CLEAN, new Vec3(0, 64, 0), new Vec3(8.5, 64.0, 0.5),
                32, ApparitionCrackVariant.WIZARD));

        assertEquals(null, ClientApparitionPresentationState.charge(caster),
                "the wizard is gone; a twirl left spinning where they stood is a ghost");
    }

    // ── 7. a splinch hurts, and nothing is left holding ───────────────────────

    @Test
    void everySplinchRungCostsBloodAndACleanArrivalCostsNone() {
        assertEquals(0.0f, SplinchTier.CLEAN.damage());
        for (SplinchTier tier : SplinchTier.values()) {
            if (tier.isSplinch()) {
                assertTrue(tier.damage() > 0.0f, tier + " is a splinch that does not hurt");
            }
        }
    }

    /**
     * No attempt can be held forever.
     *
     * <p>The soft-lock worth fearing is a charge that never resolves: the player is left mid-Determination,
     * unable to start another, with the phase state saying they are still going. A charge with a destination
     * always reaches {@code isOverdue}, which the manager turns into a forced discharge.
     */
    @Test
    void aChargeAlwaysRunsOutOnItsOwn() {
        for (ApparitionTier tier : ApparitionTier.values()) {
            ApparitionCharge charge = new ApparitionCharge(tier, 0.0f,
                    ApparitionWindow.BASE_FLOOR_TICKS, null, Vec3.ZERO);
            charge.setDestination(new Vec3(4.5, 64.0, 4.5));

            int ticks = 0;
            int ceiling = charge.windowClose() + ApparitionWindow.HARD_CAP_TICKS + 2;
            while (!charge.isOverdue() && ticks <= ceiling) {
                charge.advance();
                ticks++;
            }
            assertTrue(charge.isOverdue(),
                    tier + " never times out — a charge nobody releases would hold the player forever");
        }
    }

    /**
     * A discharge nobody released never reaches the splinch ladder at all.
     *
     * <p>It used to: the sentinel fell through to {@link SplinchTier#CATASTROPHIC}, so the harshest rung in
     * the ability was what a wizard got for doing nothing — and an anchored jump picked out of the selector
     * could reach it without the player ever touching a key. {@code ApparitionServerLogic#collapseAttempt}
     * now diverts on this predicate before the ladder is consulted: no arrival, no wound, but the cooldown
     * and the exhaustion are still charged, so it is not a free reset either.
     *
     * <p>{@link SplinchResolver}'s own sentinel branch is left in place and is deliberately unreachable —
     * it is the guard that stops an inflate overflowing if a future caller does hand it the sentinel.
     */
    @Test
    void neverLettingGoIsDivertedBeforeTheLadder() {
        assertTrue(ApparitionWindow.isForcedDischarge(ApparitionWindow.FORCED_DISCHARGE));
        for (int miss : new int[] {0, 1, 4, 5, 12, 13, 600}) {
            assertFalse(ApparitionWindow.isForcedDischarge(miss),
                    "a real miss of " + miss + " must still run the ladder");
        }
        assertEquals(SplinchTier.CATASTROPHIC,
                SplinchResolver.resolve(ApparitionWindow.FORCED_DISCHARGE, Destabilization.NONE));
    }

    /** A live charge only ever reports the two phases it can actually be in. */
    @Test
    void aLiveChargeIsOnlyEverDeterminationOrDeliberation() {
        ApparitionCharge charge = new ApparitionCharge(ApparitionTier.BLINK, 0.0f,
                ApparitionWindow.BASE_FLOOR_TICKS, null, Vec3.ZERO);
        charge.setDestination(new Vec3(1.5, 64.0, 1.5));

        for (int tick = 0; tick < 40; tick++) {
            ApparitionPhase phase = charge.phase();
            assertTrue(phase == ApparitionPhase.DETERMINATION || phase == ApparitionPhase.DELIBERATION,
                    "a charge in flight reported " + phase + "; IDLE and RESOLVING belong to the manager");
            charge.advance();
        }
    }

    // ── 8. off the broom before the teleport ──────────────────────────────────

    /**
     * Order matters and cannot be checked without a world, so it is checked in the source.
     *
     * <p>A teleport that leaves the rider mounted lasts exactly one tick before the vehicle writes the
     * position back — silently, with the cooldown and exhaustion already charged. The dismount has to come
     * first, and "first" is the whole of the bug.
     */
    @Test
    void theDismountHappensBeforeTheTeleport() throws IOException {
        String source = Files.readString(SERVER_LOGIC, StandardCharsets.UTF_8);
        int dismount = source.indexOf("stopRiding()");
        int eject = source.indexOf("ejectPassengers()");
        int teleport = source.indexOf("teleportTo(destination");

        assertTrue(dismount > 0, "nothing dismounts the wizard before they travel");
        assertTrue(eject > 0, "nothing puts down whatever is riding the wizard");
        assertTrue(teleport > 0, "the teleport call has been renamed; this test no longer checks anything");
        assertTrue(dismount < teleport, "the wizard is teleported before leaving the broom");
        assertTrue(eject < teleport, "passengers are ejected after the teleport, stranding them");
    }

    @Test
    void theManualChecksAreWrittenDown() {
        assertEquals(3, MANUAL_CHECKS.size(),
                "the in-world checks this file cannot make must stay listed where coverage is looked for");
    }
}
