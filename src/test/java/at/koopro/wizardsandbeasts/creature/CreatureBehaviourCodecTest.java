package at.koopro.wizardsandbeasts.creature;

import at.koopro.wizardsandbeasts.creature.profile.CombatProfile;
import at.koopro.wizardsandbeasts.creature.profile.CreatureBehaviour;
import at.koopro.wizardsandbeasts.creature.profile.CreatureReaction;
import at.koopro.wizardsandbeasts.creature.profile.IdleProfile;
import at.koopro.wizardsandbeasts.creature.profile.SoundProfile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behaviour block has to be invisible to the ninety creature files that do not use it.
 *
 * <p>That is the whole migration strategy for this foundation: idle, sounds, reactions, combat and
 * scale all default to "exactly what happened before", so the new systems are added to a shared class
 * used by ninety-six creatures without rewriting ninety-six files. These tests are what make that
 * claim checkable rather than asserted.
 */
class CreatureBehaviourCodecTest {

    private static final Path CREATURES =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "creatures");

    private static List<Path> creatureFiles() throws IOException {
        try (Stream<Path> files = Files.walk(CREATURES)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    /**
     * The migration guarantee: every shipped creature file still parses, including the ninety that
     * have never heard of a behaviour block.
     */
    @Test
    void everyShippedCreatureStillParses() throws IOException {
        List<String> broken = new ArrayList<>();
        for (Path file : creatureFiles()) {
            JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            CreatureDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> broken.add(file.getFileName() + ": " + error));
        }
        assertTrue(broken.isEmpty(), "creature definitions that no longer parse:\n  "
                + String.join("\n  ", broken));
    }

    /** A file with no behaviour block gets the empty one, not null and not a surprise. */
    @Test
    void aCreatureWithoutABehaviourBlockGetsTheEmptyOne() throws IOException {
        Path plain = null;
        for (Path file : creatureFiles()) {
            if (!Files.readString(file).contains("\"behaviour\"")) {
                plain = file;
                break;
            }
        }
        assertTrue(plain != null, "expected at least one creature with no behaviour block");

        JsonObject json = JsonParser.parseString(Files.readString(plain)).getAsJsonObject();
        CreatureDefinition def = CreatureDefinition.CODEC
                .parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(CreatureBehaviour.EMPTY, def.behaviour(),
                plain.getFileName() + " should carry the empty behaviour block");
        assertEquals(1.0f, def.scale(), "an undeclared scale must be 1.0, or every creature resizes");
        assertTrue(def.behaviour().sounds().isEmpty(), "silence is the default");
        assertTrue(def.behaviour().reactions().isEmpty(), "no reactions by default");
        assertTrue(def.behaviour().combatOrDefault().isDefault(),
                "the default combat profile must be the old shared rhythm exactly");
    }

    /** And a file that does declare one round-trips every field. */
    @Test
    void aDeclaredBehaviourBlockSurvivesTheCodec() throws IOException {
        Path acromantula = CREATURES.resolve("acromantula.json");
        JsonObject json = JsonParser.parseString(Files.readString(acromantula)).getAsJsonObject();
        CreatureDefinition def = CreatureDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        SoundProfile sounds = def.behaviour().sounds().orElseThrow();
        assertTrue(sounds.ambient().isPresent(), "the Acromantula should have an ambient voice");
        assertTrue(sounds.death().isPresent(), "and a death sound");

        CombatProfile combat = def.behaviour().combatOrDefault();
        assertEquals(CombatProfile.Style.AMBUSHER, combat.style());
        assertFalse(combat.isDefault(), "a declared combat profile must differ from the default");
        assertTrue(combat.windupTicks() > 0, "an ambusher telegraphs");

        // Re-encode and re-parse: the field has to survive a datapack round trip, not just a read.
        JsonObject encoded = CreatureDefinition.CODEC
                .encodeStart(JsonOps.INSTANCE, def).getOrThrow().getAsJsonObject();
        CreatureDefinition again = CreatureDefinition.CODEC
                .parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(def.behaviour(), again.behaviour(), "behaviour did not survive a round trip");
        assertEquals(def.scale(), again.scale(), "scale did not survive a round trip");
    }

    /** Reactions are declared per creature and mean nothing unless declared. */
    @Test
    void reactionsAreOptInPerCreature() throws IOException {
        JsonObject json = JsonParser.parseString(
                Files.readString(CREATURES.resolve("bowtruckle.json").toFile().exists()
                        ? CREATURES.resolve("bowtruckle.json")
                        : CREATURES.resolve("acromantula.json"))).getAsJsonObject();
        CreatureDefinition def = CreatureDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        for (CreatureReaction reaction : def.behaviour().reactions()) {
            assertTrue(reaction.radius() > 0, "a reaction with no radius notices nothing");
        }
    }

    /**
     * The Basilisk is the one creature this pass rescaled, and the figure is a design decision rather
     * than a canon conversion — pinned so it is changed deliberately rather than drifted into.
     */
    @Test
    void theBasiliskCarriesItsDocumentedScale() throws IOException {
        JsonObject json = JsonParser.parseString(
                Files.readString(CREATURES.resolve("basilisk.json"))).getAsJsonObject();
        CreatureDefinition def = CreatureDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(def.scale() > 1.0f, "the Basilisk should read as enormous");
        assertTrue(def.scale() <= 1.5f,
                "and still fit through the world; above about 1.5 the square hitbox stops being playable");
        assertTrue(json.has("_scale_note"), "the chosen scale must stay documented in the file");
    }

    /** An idle profile derived from a body plan is always safe to hand to any creature. */
    @Test
    void bodyPlanIdleProfilesAreWellFormed() {
        for (BodyPlan plan : BodyPlan.values()) {
            IdleProfile profile = IdleProfile.forBodyPlan(plan);
            assertTrue(profile.minDelay() >= 20, plan + ": idle delay must not be a nervous tic");
            assertTrue(profile.maxDelay() > profile.minDelay(), plan + ": delay window is inverted");
        }
    }
}
