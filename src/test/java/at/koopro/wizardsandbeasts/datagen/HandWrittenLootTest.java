package at.koopro.wizardsandbeasts.datagen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One file per creature's drops: the hand-written set matches its folder, and datagen writes none of them. */
class HandWrittenLootTest {

    private static final Path MAIN = Path.of("src", "main", "resources", "data", "wizards_and_beasts",
            "loot_table", "entities");
    private static final Path GENERATED = Path.of("src", "generated", "resources", "data", "wizards_and_beasts",
            "loot_table", "entities");

    @Test
    void theSetIsTheFolder() throws IOException {
        assertEquals(new TreeSet<>(HandWrittenLoot.ENTITY_TABLES), names(MAIN),
                "HandWrittenLoot.ENTITY_TABLES and the hand-written loot_table/entities folder disagree");
    }

    @Test
    void datagenWritesNoHandWrittenTable() throws IOException {
        Set<String> both = names(GENERATED);
        both.retainAll(names(MAIN));
        assertTrue(both.isEmpty(), "both a generated and a hand-written table for " + both + " -- run runData");
    }

    private static Set<String> names(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }
}
