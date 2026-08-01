package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.spell.def.SpellDefinition;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The durable guard against the dead-end defect class: every skill reference in shipped data must be
 * a <em>bare</em> node id (no namespace) that <em>resolves</em> to an existing skill node. A silent
 * format mismatch or a dangling reference fails closed — a player spends a capped, non-refundable
 * resource and is refused with no visible cause — so this test fails loudly instead.
 *
 * <p>Covers the two ways a skill node is referenced from data:
 * <ul>
 *   <li>a spell's {@code learning.requiredSkillId} (spell → node), the field that gates learning; and
 *   <li>a node's {@code edges} (node → node), the web's adjacency.
 * </ul>
 * The readers key on bare ids ({@code PlayerSkillData.getSkillLevel(String)}), so bare is canonical;
 * a namespaced reference could never match even if the node existed.
 */
class SkillReferenceIntegrityTest {

    private static final Path NODE_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "skill_nodes");
    private static final Path SPELL_DIR =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "spells");

    private static final List<Skill> NODES = new ArrayList<>();
    private static final Set<String> NODE_IDS = new HashSet<>();

    @BeforeAll
    static void loadNodes() throws IOException {
        assertTrue(Files.isDirectory(NODE_DIR), "missing skill_nodes datapack directory");
        try (Stream<Path> files = Files.walk(NODE_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(file));
                Skill skill = Skill.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(msg -> new AssertionError(file + ": " + msg));
                NODES.add(skill);
                NODE_IDS.add(skill.getId());
            }
        }
        assertFalse(NODE_IDS.isEmpty(), "no skill nodes loaded");
    }

    @Test
    void everySpellRequiredSkillIdIsBareAndResolves() throws IOException {
        assertTrue(Files.isDirectory(SPELL_DIR), "missing spells datapack directory");
        try (Stream<Path> files = Files.walk(SPELL_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                SpellDefinition def = SpellDefinition.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                        .getOrThrow(msg -> new AssertionError(file + ": " + msg));
                Optional<String> required = def.learning().requiredSkillId();
                if (required.isEmpty()) {
                    continue;
                }
                assertSkillReference(file.getFileName() + " requiredSkillId", required.get());
            }
        }
    }

    @Test
    void everyNodeEdgeIsBareAndResolves() {
        for (Skill node : NODES) {
            for (String edge : node.getEdges()) {
                assertSkillReference("node '" + node.getId() + "' edge", edge);
            }
        }
    }

    private static void assertSkillReference(String where, String id) {
        assertFalse(id.contains(":"),
                where + " must be a bare node id, got namespaced '" + id + "'");
        assertTrue(NODE_IDS.contains(id),
                where + " '" + id + "' resolves to no skill node");
    }
}
