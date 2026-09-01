package at.koopro.wizardsandbeasts.broom;

import at.koopro.wizardsandbeasts.entity.broom.handling.BroomHandlingProfile;
import at.koopro.wizardsandbeasts.entity.broom.handling.HandlingMath;
import at.koopro.wizardsandbeasts.entity.broom.handling.HandlingProfileRegistry;
import at.koopro.wizardsandbeasts.entity.broom.handling.RacingHandling;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The acceptance criterion for the handling pass is a blind flight test, which cannot be automated.
 * What can be is every ordering that test would reveal: if a Cleansweep does not decelerate harder
 * than a Comet here, no amount of flying it will make it feel safer.
 *
 * <p>Everything is asserted against the pure response curves rather than through a live entity. That
 * is why {@code RacingHandling.turnRateAt} and friends exist as statics: a threshold nobody can test
 * without spawning a world is a threshold that gets silently inverted.
 */
class BroomHandlingProfileTest {

    private static final Path DEFINITIONS = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "broom_definitions");

    // ── the map from data to behaviour ──────────────────────────────────────

    /** Every profile a datapack can name must have behaviour behind it, under the same id. */
    @Test
    void everyProfileHasBehaviourWithAMatchingId() {
        for (HandlingProfile profile : HandlingProfile.values()) {
            BroomHandlingProfile behaviour = HandlingProfileRegistry.of(profile);
            assertNotNull(behaviour, profile + " has no behaviour");
            assertEquals(profile.profileId(), behaviour.profileId(),
                    profile + " and its behaviour disagree about their own id, so a broom naming one "
                            + "would silently fly the other");
        }
        assertEquals(HandlingProfile.values().length, HandlingProfileRegistry.all().size());
    }

    // ── balanced is the regression gate ─────────────────────────────────────

    /**
     * Balanced must leave everything alone, because it is what an unauthored broom gets.
     *
     * <p>Its own momentum is the anchor of the deceleration mapping, so the scaling has to come out
     * at exactly 1. A drift here silently retunes every broom that never asked for a profile.
     */
    @Test
    void balanced_changesNothing() {
        BroomHandlingProfile balanced = HandlingProfileRegistry.BALANCED;
        BroomDefinition def = definition(HandlingProfile.BALANCED);

        assertEquals(0.30f, balanced.modifyTargetSpeed(0.30f, null, def, true), 1.0e-6f);
        assertEquals(0.11f, balanced.modifyAcceleration(0.11f, null, def, false), 1.0e-6f);
        assertEquals(0.010f, balanced.modifyDeceleration(0.010f, null, def), 1.0e-6f,
                "BALANCED's momentum is the anchor of the mapping, so it must scale by exactly 1");
        assertEquals(0.010f, balanced.modifyWeakGravity(0.010f, null, def), 1.0e-6f);
        assertEquals(12.0f, balanced.modifyRollTilt(12.0f, null, def), 1.0e-6f);
        assertEquals(2, balanced.minorImpactDurabilityLoss(2, def));
    }

    // ── school: safer, wobblier, easier to stop ─────────────────────────────

    /** A school broom will not deliver a racing broom's boost, whatever its JSON asks for. */
    @Test
    void school_capsBoostHoweverGreedyTheJson() {
        BroomDefinition greedy = definition(HandlingProfile.SCHOOL, 2.5f);
        // Target speed as the movement step would have built it: base speed times the full multiplier.
        float uncapped = 0.35f * 2.5f;
        float capped = HandlingProfileRegistry.SCHOOL.modifyTargetSpeed(uncapped, null, greedy, true);

        assertEquals(0.35f * 1.35f, capped, 1.0e-6f,
                "a school broom must deliver at most 1.35x, so the profile is a promise about how "
                        + "the broom treats a student rather than a default a number can beat");
        assertTrue(capped < uncapped);
    }

    /** The shipped school brooms are already under the cap, so nothing in the game changes today. */
    @Test
    void school_capIsANoOpForEveryShippedBroom() throws IOException {
        for (Map.Entry<String, BroomDefinition> entry : shippedByName().entrySet()) {
            BroomDefinition def = entry.getValue();
            if (def.handling().profile() != HandlingProfile.SCHOOL) continue;
            float target = def.maxSpeed() * def.boostMultiplier();
            assertEquals(target,
                    HandlingProfileRegistry.SCHOOL.modifyTargetSpeed(target, null, def, true), 1.0e-6f,
                    entry.getKey() + " is over the school boost cap. That may be intended, but it is "
                            + "a balance change to a shipped broom and should be a deliberate one.");
        }
    }

    /** Not boosting, the cap must not touch anything. */
    @Test
    void school_capOnlyAppliesWhileBoosting() {
        BroomDefinition greedy = definition(HandlingProfile.SCHOOL, 2.5f);
        assertEquals(0.35f, HandlingProfileRegistry.SCHOOL.modifyTargetSpeed(0.35f, null, greedy, false),
                1.0e-6f);
    }

    /** Students need to be able to stop: a school broom brakes harder than a balanced one. */
    @Test
    void school_stopsSoonerThanBalanced() {
        float school = HandlingProfileRegistry.SCHOOL
                .modifyDeceleration(0.012f, null, definition(HandlingProfile.SCHOOL));
        float balanced = HandlingProfileRegistry.BALANCED
                .modifyDeceleration(0.012f, null, definition(HandlingProfile.BALANCED));
        assertTrue(school > balanced,
                "school " + school + " must brake harder than balanced " + balanced);
    }

    /** And banks less alarmingly while doing it. */
    @Test
    void school_banksLessThanBalanced() {
        BroomDefinition def = definition(HandlingProfile.SCHOOL);
        assertTrue(HandlingProfileRegistry.SCHOOL.modifyRollTilt(20.0f, null, def) < 20.0f);
    }

    /** Crashing a training broom must hurt less than crashing anything else. */
    @Test
    void school_isTheMostForgivingCrash() {
        float school = HandlingProfileRegistry.SCHOOL
                .crashDamageMultiplier(definition(HandlingProfile.SCHOOL));
        float racing = HandlingProfileRegistry.RACING
                .crashDamageMultiplier(definition(HandlingProfile.RACING));
        assertTrue(school < 1.0f, "a school broom must be forgiving, not punishing: " + school);
        assertTrue(school < racing, "school " + school + " must be gentler than racing " + racing);
    }

    /**
     * Hooks whose default body <em>is</em> the implementation, so no profile needs to override them.
     *
     * <p>Each one reads the definition and does real work: the crash multiplier and the wander are
     * per-broom scalars that already differ by profile through {@link HandlingProfile}, and the
     * deceleration mapping is the general momentum rule. Overriding them would be duplicating the
     * data layer in code.
     *
     * <p>An allowlist rather than a looser rule, because "the default does something" is easy to
     * claim and hard to check: a hook that returns its own argument passes any test that only looks
     * at whether the body is empty.
     */
    private static final java.util.Set<String> IMPLEMENTED_BY_THEIR_DEFAULT =
            java.util.Set.of("crashDamageMultiplier", "modifyDeceleration", "yawWander", "profileId");

    /**
     * Every hook must have a user — a profile that overrides it, or a default that does real work.
     *
     * <p>A hook nothing implements is the same failure as a tuning constant nothing reads: it looks
     * like a seam that is in use, so the next person wires their feature into it and watches nothing
     * happen. {@code onBoostStart} was very nearly shipped exactly that way.
     */
    @Test
    void everyHookHasAtLeastOneImplementer() {
        var overridden = new java.util.HashSet<String>();
        for (BroomHandlingProfile profile : HandlingProfileRegistry.all().values()) {
            for (var method : profile.getClass().getDeclaredMethods()) {
                overridden.add(method.getName());
            }
        }
        for (var method : BroomHandlingProfile.class.getDeclaredMethods()) {
            String name = method.getName();
            if (IMPLEMENTED_BY_THEIR_DEFAULT.contains(name)) continue;
            assertTrue(overridden.contains(name),
                    "no profile overrides " + name + " and its default does nothing. Either give it "
                            + "a user, list it in IMPLEMENTED_BY_THEIR_DEFAULT with a reason, or "
                            + "delete it -- an unused hook reads as a working seam.");
        }
    }

    // ── racing: holds a line, bites at speed ────────────────────────────────

    /** The heading lock engages only when the rider is holding a heading. */
    @Test
    void racing_locksTheHeadingOnlyWhenNotSteering() {
        float wander = 1.5f;
        assertEquals(wander, RacingHandling.lockedWander(wander, true), 1.0e-6f,
                "damping the wander mid-turn would fight the controls and read as a broken broom");
        assertTrue(RacingHandling.lockedWander(wander, false) < wander * 0.2f,
                "holding a heading on a racing broom must actually hold it");
    }

    /** Below the threshold the turn is untouched; above it, it tightens up. */
    @Test
    void racing_turnTightensOnlyPastTheThreshold() {
        assertEquals(10.0f, RacingHandling.turnRateAt(10.0f, 0.5f), 1.0e-6f);
        assertEquals(10.0f, RacingHandling.turnRateAt(10.0f, 0.7f), 1.0e-6f,
                "the threshold is exclusive, so exactly 0.7 is still free");
        assertTrue(RacingHandling.turnRateAt(10.0f, 0.95f) < 10.0f);
    }

    /** Nose-heaviness scales with speed and with how unstable the broom is. */
    @Test
    void racing_sinksHarderWhenFasterAndLessStable() {
        assertEquals(0.0f, RacingHandling.sinkAt(0.5f, 0.7f), 1.0e-6f, "no sink below the threshold");

        float fast = RacingHandling.sinkAt(1.0f, 0.7f);
        float slower = RacingHandling.sinkAt(0.8f, 0.7f);
        assertTrue(fast > slower, "faster must sink harder");

        float unstable = RacingHandling.sinkAt(1.0f, 0.6f);
        assertTrue(unstable > fast, "less stable must sink harder");
        assertTrue(RacingHandling.sinkAt(1.0f, 1.0f) > 0.0f,
                "even a perfectly stable racing broom gets nose-heavy at its ceiling");
    }

    // ── tank: heavy, floaty, hard to chip ───────────────────────────────────

    @Test
    void tank_isSlowerToStartAndSlowerToTurnThanAnythingElse() {
        BroomDefinition tank = definition(HandlingProfile.TANK);
        assertTrue(HandlingProfileRegistry.TANK.modifyAcceleration(0.085f, null, tank, false) < 0.085f);

        float tankTurn = HandlingProfileRegistry.TANK.modifyTurnRate(10.0f, null, tank, false);
        float schoolTurn = HandlingProfileRegistry.SCHOOL
                .modifyTurnRate(10.0f, null, definition(HandlingProfile.SCHOOL), false);
        assertTrue(tankTurn < schoolTurn, "tank " + tankTurn + " must turn slower than school " + schoolTurn);
    }

    /** It hangs in the air longer than anything else with nothing held. */
    @Test
    void tank_floatsLongerThanBalanced() {
        BroomDefinition tank = definition(HandlingProfile.TANK);
        assertTrue(HandlingProfileRegistry.TANK.modifyWeakGravity(0.004f, null, tank) < 0.004f);

        float tankStop = HandlingProfileRegistry.TANK.modifyDeceleration(0.006f, null, tank);
        float balancedStop = HandlingProfileRegistry.BALANCED
                .modifyDeceleration(0.006f, null, definition(HandlingProfile.BALANCED));
        assertTrue(tankStop < balancedStop,
                "high momentum must mean a longer coast: tank " + tankStop + " vs " + balancedStop);
    }

    /** Glancing knocks are halved — but never all the way to free. */
    @Test
    void tank_halvesGlancingKnocksButNeverToZero() {
        BroomDefinition tank = definition(HandlingProfile.TANK);
        assertEquals(2, HandlingProfileRegistry.TANK.minorImpactDurabilityLoss(4, tank));
        assertEquals(1, HandlingProfileRegistry.TANK.minorImpactDurabilityLoss(1, tank),
                "a broom that takes no damage from scrapes can be flown into scenery forever");
        assertEquals(0, HandlingProfileRegistry.TANK.minorImpactDurabilityLoss(0, tank),
                "but a definition that authored zero meant zero");
    }

    /** A real crash still lands in full on the heaviest broom in the game. */
    @Test
    void tank_doesNotSoftenARealCrash() {
        assertEquals(1.0f,
                HandlingProfileRegistry.TANK.crashDamageMultiplier(definition(HandlingProfile.TANK)),
                1.0e-6f);
    }

    // ── the blind test, as far as it can be written down ────────────────────

    /**
     * The four shipped families must actually differ, on the axes the acceptance names.
     *
     * <p>Every assertion above could pass with all four profiles mapped to the same class; this is
     * the one that says they are not.
     */
    @Test
    void theShippedBroomsDifferOnTheAxesTheAcceptanceNames() throws IOException {
        Map<String, BroomDefinition> shipped = shippedByName();
        BroomDefinition cleansweep = shipped.get("cleansweep_seven");
        BroomDefinition nimbus = shipped.get("nimbus_2000");
        BroomDefinition firebolt = shipped.get("firebolt");
        BroomDefinition oakshaft = shipped.get("oakshaft_79");
        assertNotNull(cleansweep);
        assertNotNull(nimbus);
        assertNotNull(firebolt);
        assertNotNull(oakshaft);

        // "Nimbus holds a straight line better than Cleansweep." Same tick, same speed, so the only
        // thing that can differ is the profile and the drift it inherits.
        float nimbusLine = RacingHandling.lockedWander(
                HandlingMath.defaultWander(40, nimbus.handling(), 1.0f, false), false);
        float cleansweepLine = HandlingMath.defaultWander(40, cleansweep.handling(), 1.0f, false);
        assertTrue(Math.abs(nimbusLine) < Math.abs(cleansweepLine),
                "the Nimbus must hold a line better than the Cleansweep");

        // "Firebolt feels punishing on walls" -- and the Cleansweep does not.
        assertTrue(HandlingProfileRegistry.of(firebolt).crashDamageMultiplier(firebolt)
                        > HandlingProfileRegistry.of(cleansweep).crashDamageMultiplier(cleansweep),
                "a Firebolt must punish a wall harder than a Cleansweep does");

        // "Oakshaft feels heavy and stable."
        assertTrue(HandlingProfileRegistry.of(oakshaft).modifyTurnRate(10f, null, oakshaft, false)
                        < RacingHandling.turnRateAt(10f, 0f),
                "the Oakshaft must turn slower than a Firebolt does even at rest, because its "
                        + "penalty is mass and not momentum");
        assertTrue(oakshaft.handling().momentumRetention() > firebolt.handling().momentumRetention(),
                "the Oakshaft must coast longer than a Firebolt");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** A definition carrying nothing but the profile under test and plausible flight numbers. */
    private static BroomDefinition definition(HandlingProfile profile) {
        return definition(profile, 1.3f);
    }

    private static BroomDefinition definition(HandlingProfile profile, float boostMultiplier) {
        BroomDefinition base = BroomDefinitionRegistry.codeDefault();
        return new BroomDefinition(base.id(), base.displayName(), base.tier(),
                0.35f, 0.09f, 0.012f, boostMultiplier, base.boostDurationTicks(),
                base.boostCooldownTicks(), 0.012f, base.lerpFactor(), base.turnSpeed(),
                base.ascentSpeed(), base.descentSpeed(), base.handlingRating(), base.stabilityRating(),
                base.durability(), base.repairMaterial(), base.loreLines(), base.modelSlots(),
                base.woodTint(), base.assets(), BroomHandling.of(profile), base.audio(), base.seat());
    }

    private static Map<String, BroomDefinition> shippedByName() throws IOException {
        Map<String, BroomDefinition> out = new HashMap<>();
        try (var files = Files.list(DEFINITIONS)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                BroomDefinition def = BroomDefinition.CODEC
                        .decode(com.mojang.serialization.JsonOps.INSTANCE,
                                new com.google.gson.Gson().fromJson(Files.readString(file),
                                        com.google.gson.JsonElement.class))
                        .resultOrPartial(err -> fail(name + " failed to parse: " + err))
                        .orElseThrow()
                        .getFirst();
                out.put(name.substring(0, name.length() - ".json".length()), def);
            }
        }
        return out;
    }
}
