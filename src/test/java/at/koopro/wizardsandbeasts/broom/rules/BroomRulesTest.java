package at.koopro.wizardsandbeasts.broom.rules;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomDefinitionRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The broom overlay: stat overrides and the server speed scale reach the effective registry (what is synced to every
 * rider), every other field survives, the admin slider bounds hold every shipped broom, and a rider's own speed
 * preference can only slow them on someone else's server.
 */
class BroomRulesTest {

    private static final Path BROOM_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "broom_definitions");

    @AfterEach
    void reset() {
        BroomRules.publish(Map.of(), Set.of(), 1.0f);
        BroomRules.acceptAuthored(Map.of());
        BroomRules.setRemoteServer(false);
    }

    @Test
    void everyShippedBroomSitsInsideItsSliderBounds() throws IOException {
        Gson gson = new Gson();
        try (Stream<Path> files = Files.list(BROOM_DIR)) {
            List<Path> jsons = files.filter(p -> p.toString().endsWith(".json")).toList();
            assertFalse(jsons.isEmpty());
            for (Path json : jsons) {
                BroomDefinition def = BroomDefinition.CODEC.decode(JsonOps.INSTANCE,
                                gson.fromJson(Files.readString(json), JsonElement.class))
                        .resultOrPartial(err -> fail(json + ": " + err)).orElseThrow().getFirst();
                for (BroomStat stat : BroomStat.values()) {
                    float value = stat.read(def);
                    assertTrue(value >= stat.min() && value <= stat.max(),
                            json.getFileName() + " " + stat.id() + "=" + value + " outside " + stat.min() + "–" + stat.max());
                }
            }
        }
    }

    @Test
    void boundsAndStepsAreMeaningful() {
        for (BroomStat stat : BroomStat.values()) {
            assertTrue(stat.min() < stat.max(), stat.id());
            assertTrue(stat.step() > 0 && stat.step() <= (stat.max() - stat.min()) / 10, stat.id() + " step too coarse");
            assertEquals(stat, BroomStat.byId(stat.id()));
        }
        assertTrue(BroomStat.BOOST_MULTIPLIER.min() >= 1.0f, "a boost never slows a broom");
    }

    @Test
    void applyReplacesOnlyTheNamedStats() {
        BroomDefinition base = BroomDefinitionRegistry.codeDefault();
        BroomDefinition tuned = BroomStat.apply(base, Map.of(BroomStat.MAX_SPEED, 0.9f, BroomStat.TURN_SPEED, 1.1f));
        assertEquals(0.9f, tuned.maxSpeed(), 1e-6f);
        assertEquals(1.1f, tuned.turnSpeed(), 1e-6f);
        assertEquals(base.acceleration(), tuned.acceleration());
        assertEquals(base.id(), tuned.id());
        assertEquals(base.durability(), tuned.durability());
        assertEquals(base.handling(), tuned.handling());
        assertEquals(base.modelSlots(), tuned.modelSlots());
        assertEquals(base.seat(), tuned.seat());
        assertEquals(base.boostDurationTicks(), tuned.boostDurationTicks());
    }

    @Test
    void overridesAndTheSpeedScaleReachTheSyncedRegistry() {
        BroomDefinition base = BroomDefinitionRegistry.codeDefault();
        Identifier id = base.id();
        BroomRules.acceptAuthored(Map.of(id, base));
        assertEquals(base.maxSpeed(), BroomDefinitionRegistry.get(id).maxSpeed(), 1e-6f, "no rules: authored");

        BroomRules.publish(Map.of(id.toString(), Map.of(BroomStat.MAX_SPEED, 1.0f)), Set.of(), 0.5f);
        BroomDefinition flies = BroomDefinitionRegistry.get(id);
        assertEquals(0.5f, flies.maxSpeed(), 1e-6f, "override, then scale");
        assertEquals(base.turnSpeed(), flies.turnSpeed(), 1e-6f);
        assertEquals(base.maxSpeed(), BroomRules.authored(id).maxSpeed(), 1e-6f, "authored copy is never edited");

        BroomRules.publish(Map.of(), Set.of(id.toString()), 1.0f);
        assertFalse(BroomRules.enabled(id));
        assertEquals(base.maxSpeed(), BroomDefinitionRegistry.get(id).maxSpeed(), 1e-6f, "removing the override restores it");
    }

    @Test
    void aRidersOwnPreferenceOnlySlowsThemOnARemoteServer() {
        assertEquals(1.5f, BroomRules.personalSpeedMultiplier(1.5f), 1e-6f, "their own world: as configured");
        BroomRules.setRemoteServer(true);
        assertEquals(1.0f, BroomRules.personalSpeedMultiplier(1.5f), 1e-6f);
        assertEquals(0.5f, BroomRules.personalSpeedMultiplier(0.5f), 1e-6f);
    }
}
