package at.koopro.wizardsandbeasts.broom.fitting;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomTier;
import at.koopro.wizardsandbeasts.broom.rules.BroomStat;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Broom fittings and wear: every fitting is a trade, a broom has room by tier, no combination flies outside what the
 * admin bounds allow, and wear makes a neglected broom harder to fly without making it more dangerous to fall off.
 */
class BroomFittingTest {

    private static final Path DEFINITIONS = Path.of("src/main/resources/data/wizards_and_beasts/broom_definitions");
    private static final List<BroomDefinition> SHIPPED = new ArrayList<>();

    @BeforeAll
    static void load() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (Stream<Path> files = Files.list(DEFINITIONS)) {
            for (Path file : files.sorted().toList()) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    SHIPPED.add(BroomDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
                }
            }
        }
        assertFalse(SHIPPED.isEmpty(), "no broom definitions found");
    }

    @Test
    void everyFittingIsATrade() {
        for (BroomFitting fitting : BroomFitting.values()) {
            boolean helps = false;
            boolean costs = false;
            for (Map.Entry<FittingStat, Float> mod : fitting.modifiers().entrySet()) {
                boolean up = mod.getValue() > 1.0f;
                if (up == mod.getKey().higherIsBetter()) {
                    helps = true;
                } else {
                    costs = true;
                }
                assertTrue(mod.getValue() >= 0.5f && mod.getValue() <= 1.6f,
                        fitting + " moves " + mod.getKey() + " by more than a fitting should: " + mod.getValue());
            }
            assertTrue(helps && costs, fitting + " must both help and cost something");
        }
    }

    @Test
    void roomGrowsWithTierAndASchoolBroomTakesOne() {
        assertEquals(1, BroomFitting.capacity(BroomTier.SCHOOL));
        int previous = 0;
        for (BroomTier tier : BroomTier.values()) {
            assertTrue(BroomFitting.capacity(tier) >= previous, tier + " has less room than the tier below");
            previous = BroomFitting.capacity(tier);
        }
        assertTrue(BroomFitting.capacity(BroomTier.RACING) <= FittingSlot.values().length);
    }

    @Test
    void theRefusalsAreTheOnesThePlayerIsTold() {
        BroomFittings none = BroomFittings.NONE;
        assertEquals(BroomFittings.Refusal.TIER_TOO_LOW,
                none.check(BroomFitting.ANTI_JINX_VARNISH, BroomTier.SCHOOL), "racing varnish on a school broom");
        BroomFittings one = none.with(BroomFitting.CUSHIONING_CHARM);
        assertEquals(BroomFittings.Refusal.NO_ROOM, one.check(BroomFitting.BALANCED_TAIL, BroomTier.SCHOOL));
        assertEquals(BroomFittings.Refusal.ALREADY_FITTED, one.check(BroomFitting.CUSHIONING_CHARM, BroomTier.SCHOOL));
        // Same slot replaces, so a full school broom can still swap its one charm.
        assertEquals(BroomFittings.Refusal.OK, one.check(BroomFitting.BRAKING_CHARM, BroomTier.STANDARD));
        assertEquals(BroomFittings.Refusal.OK, one.check(BroomFitting.BALANCED_TAIL, BroomTier.STANDARD));
        assertEquals(BroomFitting.BRAKING_CHARM,
                one.with(BroomFitting.BRAKING_CHARM).in(FittingSlot.CHARM).orElseThrow(), "same slot is a swap");
    }

    @Test
    void fittingsSurviveTheStackAndTheEntityWire() {
        BroomFittings full = allFittings();
        assertEquals(full, BroomFittings.CODEC.parse(JsonOps.INSTANCE,
                BroomFittings.CODEC.encodeStart(JsonOps.INSTANCE, full).getOrThrow()).getOrThrow());
        assertEquals(full, BroomFittings.decode(full.encode()));
        assertEquals(BroomFittings.NONE, BroomFittings.decode(""));
        // A retired fitting is dropped, not a decode fault: an old broom still loads.
        assertEquals(BroomFittings.NONE, BroomFittings.fromIds(List.of("retired_fitting")));
    }

    @Test
    void noCombinationFliesOutsideTheAdminBounds() {
        BroomFittings full = allFittings();
        for (BroomDefinition base : SHIPPED) {
            for (BroomWear wear : BroomWear.values()) {
                BroomDefinition flown = BroomTune.apply(base, full, wear);
                for (BroomStat stat : BroomStat.values()) {
                    float value = stat.read(flown);
                    assertTrue(value >= stat.min() - 1e-6f && value <= stat.max() + 1e-6f,
                            base.id() + " " + wear + " " + stat + " = " + value);
                }
                assertTrue(flown.boostMultiplier() >= 1.0f, "a boost became a brake on " + base.id());
                assertTrue(flown.maxSpeed() <= base.maxSpeed() * BroomFitting.MAX_SPEED_GAIN + 1e-6f,
                        "fittings raised " + base.id() + " past the speed cap");
            }
        }
    }

    @Test
    void anUnfittedSoundBroomIsTheDefinitionItself() {
        for (BroomDefinition base : SHIPPED) {
            assertSame(base, BroomTune.apply(base, BroomFittings.NONE, BroomWear.SOUND));
        }
    }

    @Test
    void streamlinedTwigsAreFasterAndWanderMore() {
        BroomDefinition nimbus = shipped("nimbus_2000");
        BroomDefinition fitted = BroomTune.apply(nimbus, BroomFittings.NONE.with(BroomFitting.STREAMLINED_TWIGS),
                BroomWear.SOUND);
        assertTrue(fitted.maxSpeed() > nimbus.maxSpeed());
        assertTrue(fitted.handling().yawDrift() > nimbus.handling().yawDrift());
    }

    @Test
    void wearBandsAndWhatTheyDo() {
        assertEquals(BroomWear.SOUND, BroomWear.of(60, 120));
        assertEquals(BroomWear.WORN, BroomWear.of(59, 120));
        assertEquals(BroomWear.WORN, BroomWear.of(24, 120));
        assertEquals(BroomWear.BATTERED, BroomWear.of(23, 120));
        assertEquals(BroomWear.SOUND, BroomWear.of(0, 0), "no durability scale, nothing to wear");
        BroomDefinition comet = shipped("comet_260");
        BroomDefinition battered = BroomTune.apply(comet, BroomFittings.NONE, BroomWear.BATTERED);
        assertTrue(battered.maxSpeed() < comet.maxSpeed());
        assertTrue(battered.handling().yawDrift() > comet.handling().yawDrift());
        assertEquals(comet.handling().crashDamageMultiplier(), battered.handling().crashDamageMultiplier(),
                "wear makes a broom harder to fly, not more dangerous to fall off");
    }

    @Test
    void theCushioningCharmSoftensCrashesAndTheBindingWear() {
        BroomDefinition firebolt = shipped("firebolt");
        BroomFittings fitted = BroomFittings.NONE.with(BroomFitting.CUSHIONING_CHARM).with(BroomFitting.REINFORCED_BINDING);
        BroomDefinition flown = BroomTune.apply(firebolt, fitted, BroomWear.SOUND);
        assertTrue(flown.handling().crashDamageMultiplier() < firebolt.handling().crashDamageMultiplier());
        assertTrue(flown.handling().severeImpactDurabilityLoss() < firebolt.handling().severeImpactDurabilityLoss());
    }

    private static BroomFittings allFittings() {
        // One per slot, the first fitting of each.
        Map<FittingSlot, BroomFitting> bySlot = new EnumMap<>(FittingSlot.class);
        for (BroomFitting fitting : BroomFitting.values()) {
            bySlot.putIfAbsent(fitting.slot(), fitting);
        }
        return new BroomFittings(bySlot);
    }

    private static BroomDefinition shipped(String path) {
        return SHIPPED.stream().filter(d -> d.id().getPath().equals(path)).findFirst().orElseThrow();
    }
}
