package at.koopro.wizardsandbeasts.wand.stat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the id vocabulary shared by the wand enums and the datapack registries.
 *
 * <p>The cast path resolves a wand's core and wood by the {@code Identifier} stored on the stack
 * ({@code WandCore.byName(id.getPath())}), so a datapack id with no matching enum constant does not
 * fail loudly — it resolves to {@code null} and the contributor is silently skipped. That is exactly
 * how Thestral tail hair lost its core modifier, and it is invisible without a test like this one.
 */
class WandIdParityTest {

    private static final Path DATA =
            Path.of("src", "main", "resources", "data", "wizards_and_beasts", "wizards_and_beasts");

    @Test
    void everyCoreDefinition_resolvesToAnEnumConstant() throws IOException {
        List<String> unresolved = new ArrayList<>();
        for (String id : definitionIds("wand_cores")) {
            if (WandCore.byName(id) == null) unresolved.add(id);
        }
        assertTrue(unresolved.isEmpty(),
                "Core definitions with no WandCore constant — these contribute nothing to a cast: "
                        + unresolved);
    }

    /**
     * The reverse direction for woods. Not asserted for cores: {@code rougarou_hair} and
     * {@code white_river_monster_spine} are enum constants with no definition and no obtainable
     * item, which is a separate (tracked) defect rather than something this test should fail on.
     */
    @Test
    void everyWoodConstant_hasADefinition() throws IOException {
        List<String> ids = definitionIds("wand_woods");
        List<String> missing = new ArrayList<>();
        for (WandWood wood : WandWood.values()) {
            if (!ids.contains(wood.getSerializedName())) missing.add(wood.getSerializedName());
        }
        assertTrue(missing.isEmpty(), "WandWood constants with no wand_woods definition: " + missing);
    }

    @Test
    void thestralTailHair_resolvesUnderTheIdTheGameActuallyStores() {
        WandCore resolved = WandCore.byName("thestral_tail_hair");
        assertNotNull(resolved,
                "Wands store the core material item's id, which is thestral_tail_hair.");
        assertEquals(WandCore.THESTRAL_TAIL, resolved);
        assertEquals("thestral_tail", resolved.getSerializedName(),
                "The serialized name backs the persistent wand_core_legacy codec and must not change.");
    }

    private static List<String> definitionIds(String registryPath) throws IOException {
        Path dir = DATA.resolve(registryPath);
        assertTrue(Files.isDirectory(dir), "Missing definition directory: " + dir);
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .sorted()
                    .toList();
        }
    }
}
