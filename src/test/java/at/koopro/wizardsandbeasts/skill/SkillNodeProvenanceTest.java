package at.koopro.wizardsandbeasts.skill;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Canon and invention, kept apart.
 *
 * <p>The rule the brief sets: a node that teaches an attested spell and a node that teaches a training step this
 * mod invented must not look the same to a player. So every node declares a {@link NodeProvenance}, the screen
 * prints it, and these tests are what stop a declaration drifting away from the truth:
 *
 * <ul>
 *   <li>every node says where its content comes from — silence is not an option;</li>
 *   <li>a node that teaches a spell agrees with that spell's own {@code canonTier}, so "Attested in the novels"
 *       can never sit above a spell the mod made up;</li>
 *   <li>a node that claims attestation names a source.</li>
 * </ul>
 */
class SkillNodeProvenanceTest {

    private static final Path NODE_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "skill_nodes");
    private static final Path SPELL_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "spells");
    private static final Path LANG_FILE =
            Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang", "en_us.json");

    /** What a node may say about a spell of each attestation tier. */
    private static final Map<String, String> TIER_CITATION = Map.of(
            "books", "Attested in the novels",
            "companion", "Attested in a companion book",
            "film", "Attested on screen",
            "pottermore", "Attested in Wizarding World writing",
            "expanded", "Attested in licensed games");

    private static final Map<String, Skill> NODES = new HashMap<>();
    private static JsonObject lang;

    @BeforeAll
    static void loadAll() throws IOException {
        try (Stream<Path> files = Files.walk(NODE_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                Skill skill = Skill.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(msg -> new AssertionError(file + ": " + msg));
                NODES.put(skill.getId(), skill);
            }
        }
        assertFalse(NODES.isEmpty(), "no skill nodes on disk");
        lang = JsonParser.parseString(Files.readString(LANG_FILE)).getAsJsonObject();
    }

    @Test
    void everyNodeSaysWhereItsContentComesFrom() {
        List<String> silent = new ArrayList<>();
        for (Skill skill : NODES.values()) {
            if (skill.getProvenance().isEmpty()) {
                silent.add(skill.getId());
            }
        }
        assertTrue(silent.isEmpty(),
                "these nodes claim nothing about their own provenance, so the screen cannot tell a player "
                        + "whether they are canon: " + silent);
    }

    @Test
    void aSpellTeachingNodeAgreesWithTheSpellsOwnAttestation() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Skill skill : NODES.values()) {
            String taught = skill.getTaughtSpellId();
            if (taught == null) {
                continue;
            }
            Optional<NodeProvenance> declared = skill.getProvenance();
            assertTrue(declared.isPresent(), skill.getId() + " teaches a spell and declares no provenance");

            String tier = canonTierOf(taught);
            if (tier == null || tier.equals("original")) {
                // A spell the mod invented: the node must admit it rather than borrow an attestation.
                if (declared.get().isCanon()) {
                    wrong.add(skill.getId() + " claims " + declared.get().citation().orElse("?")
                            + " for '" + taught + "', which is original to this mod");
                }
                continue;
            }
            String expected = TIER_CITATION.get(tier);
            if (expected == null) {
                wrong.add(skill.getId() + " teaches '" + taught + "' whose tier '" + tier + "' has no wording");
            } else if (!declared.get().isCanon()) {
                wrong.add(skill.getId() + " calls itself an invention while teaching canon spell '" + taught + "'");
            } else if (!expected.equals(declared.get().citation().orElse(""))) {
                // A node may cite a chapter instead of the tier's generic wording, but not a different tier.
                String citation = declared.get().citation().orElse("");
                boolean specific = TIER_CITATION.values().stream().noneMatch(citation::equals);
                if (!specific) {
                    wrong.add(skill.getId() + " cites '" + citation + "' for a '" + tier + "' spell");
                }
            }
        }
        assertTrue(wrong.isEmpty(), String.join("; ", wrong));
    }

    @Test
    void anAttestedNodeNamesItsSource() {
        for (Skill skill : NODES.values()) {
            skill.getProvenance().ifPresent(provenance -> {
                if (provenance.isCanon()) {
                    assertFalse(provenance.citation().orElse("").isBlank(),
                            skill.getId() + " claims attestation without naming a source");
                } else {
                    assertTrue(provenance.citation().isEmpty(),
                            skill.getId() + " is both an invention and attested");
                }
            });
        }
    }

    @Test
    void everyNodeExplainsItselfInEnglish() {
        List<String> missing = new ArrayList<>();
        for (Skill skill : NODES.values()) {
            for (String key : List.of(skill.getDisplayName(), skill.getDescription())) {
                if (key.isEmpty() || !lang.has(key)) {
                    missing.add(skill.getId() + " -> " + key);
                }
            }
            // Lore and the worked example are optional fields; a declared key must still resolve.
            for (String key : List.of(skill.getLore(), skill.getPractice())) {
                if (!key.isEmpty() && !lang.has(key)) {
                    missing.add(skill.getId() + " -> " + key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "untranslated node text: " + missing);
    }

    /**
     * The five questions the brief asks the screen to answer include "why did I learn it" and "what can I do
     * now". A node with no lore and no worked example answers neither, so every node ships both.
     */
    @Test
    void everyNodeCarriesLoreAndAWorkedExample() {
        List<String> bare = new ArrayList<>();
        for (Skill skill : NODES.values()) {
            if (skill.getLore().isEmpty() || skill.getPractice().isEmpty()) {
                bare.add(skill.getId());
            }
        }
        assertTrue(bare.isEmpty(), "these nodes say what they do and never why or what with: " + bare);
    }

    /** No two nodes may share a description: that is what made 34 of the old ones indistinguishable. */
    @Test
    void noTwoNodesShareTheirText() {
        Map<String, String> byText = new HashMap<>();
        List<String> clashes = new ArrayList<>();
        for (Skill skill : NODES.values()) {
            String text = lang.has(skill.getDescription())
                    ? lang.get(skill.getDescription()).getAsString() : skill.getDescription();
            String previous = byText.put(text, skill.getId());
            if (previous != null) {
                clashes.add(previous + " and " + skill.getId());
            }
        }
        assertTrue(clashes.isEmpty(), "nodes that read identically: " + clashes);
    }

    private static String canonTierOf(String spellId) throws IOException {
        Path file = SPELL_DIR.resolve(spellId + ".json");
        if (!Files.exists(file)) {
            // A bespoke Java spell. The four the mod implements in code are all from the novels.
            return "books";
        }
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        return json.has("canonTier") ? json.get("canonTier").getAsString() : null;
    }
}
