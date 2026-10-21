package at.koopro.wizardsandbeasts.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code if (!inventory.add(stack)) player.drop(stack)} destroys items: {@code Inventory.add} returns {@code true}
 * when it inserted <em>anything</em> and leaves the remainder in the stack, so the drop never runs for the part that
 * did not fit. Every hand-over goes through {@link InventoryHandover#giveOrDrop} (or adds, then drops what is left);
 * this keeps the pattern from coming back.
 */
class PartialInsertPatternTest {

    private static final Path SOURCES = Path.of("src/main/java");

    /** {@code if (!….add(x)) {} followed by a drop or a pop of the same stack. */
    private static final Pattern LOSSY = Pattern.compile(
            "if \\(![^\\n]*\\.add\\((\\w+)\\)\\)\\s*\\{\\s*[^\\n]*(?:drop|popResource)\\([^\\n]*\\b\\1\\b");

    @Test
    void noHandOverDropsOnlyWhenNothingFitted() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Matcher matcher = LOSSY.matcher(source);
                while (matcher.find()) {
                    int line = source.substring(0, matcher.start()).split("\n", -1).length;
                    offenders.add(SOURCES.relativize(file) + ":" + line);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "if (!add) drop loses the remainder of a partial insert; use "
                + "InventoryHandover.giveOrDrop: " + offenders);
    }

    @Test
    void thePatternIsRecognised() {
        assertTrue(LOSSY.matcher("if (!player.getInventory().add(gift)) {\n    player.drop(gift, false);\n}").find());
        assertTrue(LOSSY.matcher("if (!player.getInventory().add(x)) {\n Block.popResource(level, pos, x);\n}").find());
    }
}
