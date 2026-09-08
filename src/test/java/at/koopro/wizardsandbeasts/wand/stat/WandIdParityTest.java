package at.koopro.wizardsandbeasts.wand.stat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
     * The reverse direction for woods. The core equivalent used to be excluded, because
     * {@code rougarou_hair} and {@code white_river_monster_spine} were enum constants with no
     * definition at all — a tracked defect rather than something this test should fail on. Both are
     * authored now, and {@code WandCoreCastModifierTest} asserts the core direction against
     * {@link WandCore#getDefinitionPath()}, which is the id the cast path actually keys on.
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

    /**
     * Thestral is the one core whose persisted name and definition id differ, and it has broken the
     * cast path in both directions. Reading a modern stack, {@code byName} had to accept the item's
     * id; writing a lookup key for a legacy stack, the resolver has to emit the definition's id and
     * not the persisted one. Both are asserted, because fixing either alone leaves half the wands
     * silently contributing no core.
     */
    @Test
    void thestralTailHair_resolvesUnderTheIdTheGameActuallyStores() {
        WandCore resolved = WandCore.byName("thestral_tail_hair");
        assertNotNull(resolved,
                "Wands store the core material item's id, which is thestral_tail_hair.");
        assertEquals(WandCore.THESTRAL_TAIL, resolved);
        assertEquals("thestral_tail", resolved.getSerializedName(),
                "The serialized name backs the persistent wand_core_legacy codec and must not change.");
        assertEquals("thestral_tail_hair", resolved.getDefinitionPath(),
                "A legacy Thestral wand is looked up by its definition path; the persisted "
                        + "thestral_tail names no file and would contribute nothing.");
    }

    /**
     * Nine of the ten cores name themselves the same way twice. Pinned so that a future core added
     * with a mismatched pair is a test failure rather than a wand that quietly casts as if it had no
     * core — the failure mode Thestral demonstrated.
     */
    @Test
    void onlyThestral_hasADefinitionPathThatDiffersFromItsSerializedName() {
        List<String> divergent = new ArrayList<>();
        for (WandCore core : WandCore.values()) {
            if (!core.getSerializedName().equals(core.getDefinitionPath())) {
                divergent.add(core.name());
            }
        }
        assertEquals(List.of(WandCore.THESTRAL_TAIL.name()), divergent,
                "a core whose persisted name and definition id disagree needs both a byName alias "
                        + "and a resolver that keys on the definition path");
    }

    /**
     * A core id written into a wand's modern component must come from {@code getDefinitionPath()}.
     *
     * <p>This is a source scan rather than a behavioural test because the thing it guards cannot fail
     * loudly. {@code WandItem.createWand} built the {@code WAND_CORE} component from
     * {@code getSerializedName()}, which for Thestral is {@code thestral_tail} — an id no
     * {@code wand_cores} definition carries. The wand was created, named, tinted and bonded exactly as
     * expected, and quietly contributed no core modifier to any cast. Every path that hands out a wand
     * from the enums went through it: {@code /wandb wand give}, the dev kit and the game-test fixtures.
     *
     * <p>Deliberately narrow. It flags {@code getSerializedName()} on a core-ish receiver inside an
     * {@code Identifier} construction and nothing else — the legacy {@code wand_core_legacy} codec must
     * keep using the serialized name, so a blanket ban would be wrong.
     */
    @Test
    void noCoreIdIsBuiltFromTheSerializedName() throws IOException {
        Pattern identifierFromCore = Pattern.compile(
                "Identifier\\.fromNamespaceAndPath\\([^;]*?\\b(\\w*[Cc]ore)\\.getSerializedName\\(\\)");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = identifierFromCore.matcher(Files.readString(file));
                while (m.find()) {
                    offenders.add(file.getFileName() + ": " + m.group(1) + ".getSerializedName()");
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "these build a core Identifier from the persisted enum name instead of "
                        + "getDefinitionPath(); for Thestral that names no definition and the wand "
                        + "silently casts with no core: " + offenders);
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
