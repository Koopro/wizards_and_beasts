package at.koopro.wizardsandbeasts.wand;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every wandwood the mod grows must be shapeable into a wand of that wood.
 *
 * <p>This is the invariant {@code WandBlankItem.wandWoodFromLogBlock} broke. That method mapped only
 * vanilla log tags, so the nine species the mod actually plants — the entire point of the wandwood
 * worldgen — all fell through to {@code null}: clicking a blackthorn log with a blank did nothing at
 * all. Nothing threw and nothing logged, because "this block is not a wand wood log" is a legitimate
 * answer for the overwhelming majority of blocks a player will ever click.
 *
 * <p>So the two halves are asserted against each other rather than trusted. A species that registers
 * blocks but has no {@code wand_woods} entry would shape a blank to an id no registry can resolve; a
 * {@code wand_woods} entry with no blocks and no vanilla donor is a wood no player can ever obtain.
 * Both are silent in play and neither is visible in a diff that only touches one side.
 *
 * <p>Species are read from the generated blockstates rather than from {@code ModBlocks.ALL_WOOD_SETS}
 * directly: that array is the datagen input, so the files it produced are the evidence registration
 * actually happened, and reading them keeps this test free of a live registry.
 */
class WandBlankWoodMappingTest {

    private static final Path BLOCKSTATES = Path.of("src", "generated", "resources", "assets",
            "wizards_and_beasts", "blockstates");

    private static final Path WAND_WOODS = Path.of("src", "main", "resources", "data",
            "wizards_and_beasts", "wizards_and_beasts", "wand_woods");

    /**
     * Wand woods with no block family of their own, obtainable only through the vanilla donor table in
     * {@code wandWoodFromLogBlock}. Listed by hand because that is the whole point — an unobtainable
     * wood must be a deliberate entry here, not an oversight nobody noticed.
     */
    private static final Set<String> VANILLA_DONOR_ONLY = Set.of("vine");

    /** The four pillar variants vanilla's {@code *_LOGS} tags cover, and so must the mod's mapping. */
    private static final List<String> PILLAR_VARIANTS =
            List.of("%s_log", "stripped_%s_log", "%s_wood", "stripped_%s_wood");

    /** Species that registered a block family, derived from the blockstates datagen wrote. */
    private static Set<String> registeredSpecies() throws IOException {
        try (Stream<Path> files = Files.list(BLOCKSTATES)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith("_log.json") && !n.startsWith("stripped_"))
                    .map(n -> n.substring(0, n.length() - "_log.json".length()))
                    .collect(TreeSet::new, Set::add, Set::addAll);
        }
    }

    private static Set<String> declaredWandWoods() throws IOException {
        try (Stream<Path> files = Files.list(WAND_WOODS)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .collect(TreeSet::new, Set::add, Set::addAll);
        }
    }

    @Test
    void everyGrownSpeciesIsADeclaredWandWood() throws IOException {
        Set<String> declared = declaredWandWoods();
        List<String> orphans = new ArrayList<>();
        for (String species : registeredSpecies()) {
            if (!declared.contains(species)) {
                orphans.add(species);
            }
        }
        assertEquals(List.of(), orphans,
                "these species grow logs but have no wand_woods entry, so shaping a blank on one would "
                        + "stamp an id nothing can resolve");
    }

    @Test
    void everyDeclaredWandWoodIsObtainable() throws IOException {
        Set<String> registered = registeredSpecies();
        List<String> unobtainable = new ArrayList<>();
        for (String wood : declaredWandWoods()) {
            if (!registered.contains(wood) && !VANILLA_DONOR_ONLY.contains(wood)) {
                unobtainable.add(wood);
            }
        }
        assertEquals(List.of(), unobtainable,
                "these wand woods have no block family and are not listed as vanilla-donor-only, so no "
                        + "player can ever shape one");
    }

    /**
     * The mapping tests all four pillar variants per species. If a variant were never registered the
     * corresponding {@code state.is(...)} would compare against a block that cannot exist, which reads
     * as working code and matches nothing.
     */
    @Test
    void everySpeciesRegistersAllFourPillarVariants() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String species : registeredSpecies()) {
            for (String pattern : PILLAR_VARIANTS) {
                String block = String.format(pattern, species);
                if (!Files.exists(BLOCKSTATES.resolve(block + ".json"))) {
                    missing.add(block);
                }
            }
        }
        assertEquals(List.of(), missing,
                "a pillar variant the wand-wood mapping checks is not registered");
    }

    /**
     * The one assertion that would actually have caught the original bug.
     *
     * <p>The tests above check that the two data sets agree; they passed throughout the period when
     * clicking a blackthorn log did nothing, because the defect was never in the data. It was that the
     * mapping enumerated vanilla tags by hand and no mod wood appeared in that list at all.
     *
     * <p>Driving the mapping off {@code ALL_WOOD_SETS} is what makes a newly added species work without
     * anyone remembering to touch this file, so that — not any particular species — is what is pinned
     * here. Reading the source is a blunt instrument, but the alternative needs a live block registry,
     * and the failure being guarded against is a silent no-op that no data assertion can see.
     */
    @Test
    void theMappingIsDrivenOffTheWoodSetRoster_notAHandWrittenList() throws IOException {
        Path source = Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts",
                "item", "wand", "WandBlankItem.java");
        assertTrue(Files.exists(source), "missing " + source);
        assertTrue(Files.readString(source).contains("ALL_WOOD_SETS"),
                "WandBlankItem no longer enumerates ModBlocks.ALL_WOOD_SETS. If the wandwood mapping has "
                        + "gone back to a hand-written table, every species missing from it silently "
                        + "refuses to shape a blank — which is exactly how blackthorn broke.");
    }

    /** Sanity floor: the nine species the wandwood pass shipped must all still be here. */
    @Test
    void theNineShippedSpeciesArePresent() throws IOException {
        Set<String> registered = registeredSpecies();
        for (String species : List.of("elder", "yew", "holly", "rowan", "ash",
                "blackthorn", "hawthorn", "walnut", "willow")) {
            assertTrue(registered.contains(species),
                    species + " no longer registers a log — it was one of the nine shipped wandwoods");
        }
    }
}
