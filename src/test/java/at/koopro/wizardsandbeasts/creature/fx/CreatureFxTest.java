package at.koopro.wizardsandbeasts.creature.fx;

import at.koopro.wizardsandbeasts.creature.profile.CreatureActivity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Species effects: the codec, the pure rules, and that every effect named in a definition can be drawn. */
class CreatureFxTest {

    private static final Path DEFS = Path.of("src/main/resources/data/wizards_and_beasts/creatures");
    private static final Path PARTICLES = Path.of("src/main/resources/assets/wizards_and_beasts/particles");
    private static final Set<String> WHERE = Set.of("body", "head", "back", "feet");
    private static final Set<String> WHEN = Set.of("always", "moving", "still", "resting", "sleeping", "threaten", "appear");

    @Test
    void theCodecReadsAnEffectsBlock() {
        CreatureFx fx = CreatureFx.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"footfall": 1.0, "emitters": [{"particle": "wizards_and_beasts:smoke_puff", "color": "#7A7570",
                 "rate": 0.06, "where": "head", "when": "resting"}]}""")).getOrThrow();
        assertEquals(1.0f, fx.footfall());
        assertEquals("head", fx.emitters().getFirst().where());
        assertFalse(fx.isEmpty());
        assertTrue(CreatureFx.NONE.isEmpty());
    }

    @Test
    void restingCoversSleepButSleepingDoesNotCoverRest() {
        assertTrue(CreatureFx.applies("resting", CreatureActivity.SLEEP, false));
        assertTrue(CreatureFx.applies("resting", CreatureActivity.REST, false));
        assertFalse(CreatureFx.applies("sleeping", CreatureActivity.REST, false));
        assertTrue(CreatureFx.applies("moving", CreatureActivity.IDLE, true));
        assertFalse(CreatureFx.applies("still", CreatureActivity.IDLE, true));
        assertTrue(CreatureFx.applies("always", CreatureActivity.FLEE, true));
        assertFalse(CreatureFx.applies("appear", CreatureActivity.IDLE, false), "a burst, not a per-tick chance");
    }

    @Test
    void aBigSlowBeastStepsLessOftenThanAFastSmallOne() {
        int dragon = CreatureFx.strideTicks(3.0f, 0.15);
        int horse = CreatureFx.strideTicks(1.4f, 0.4);
        assertTrue(dragon > horse, dragon + " vs " + horse);
        assertEquals(24, CreatureFx.strideTicks(5f, 0.05), "capped at 24 ticks");
        assertEquals(Integer.MAX_VALUE, CreatureFx.strideTicks(1f, 0), "no steps standing still");
    }

    @Test
    void colourParsing() {
        assertEquals(0xFF7A7570, CreatureFx.argb("#7A7570"));
        assertEquals(0x807A7570, CreatureFx.argb("807A7570"));
    }

    @Test
    void everyEmitterNamesAParticleThatExists() throws IOException {
        List<String> problems = new ArrayList<>();
        int emitters = 0;
        try (Stream<Path> files = Files.list(DEFS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject def = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!def.has("fx")) {
                    continue;
                }
                String id = file.getFileName().toString();
                CreatureFx fx = CreatureFx.CODEC.parse(JsonOps.INSTANCE, def.get("fx")).getOrThrow();
                for (CreatureFx.Emitter e : fx.emitters()) {
                    emitters++;
                    if (e.particle().getNamespace().equals("wizards_and_beasts")
                            && !Files.exists(PARTICLES.resolve(e.particle().getPath() + ".json"))) {
                        problems.add(id + ": no particle " + e.particle());
                    }
                    if (!WHERE.contains(e.where())) problems.add(id + ": where '" + e.where() + "'");
                    if (!WHEN.contains(e.when())) problems.add(id + ": when '" + e.when() + "'");
                    if (e.rate() <= 0f || e.rate() > 1f) problems.add(id + ": rate " + e.rate());
                }
            }
        }
        assertTrue(emitters > 0, "no creature declares an emitter");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
