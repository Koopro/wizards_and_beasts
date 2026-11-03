package at.koopro.wizardsandbeasts.resources;

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
 * Every complete texture path written into Java names a file that ships. A screen that binds a deleted sheet
 * compiles, loads and draws the magenta checkerboard -- the wand refine screen did, for a week, after the bench's
 * old sheet was retired. Paths assembled at runtime ({@code "textures/gui/map/" + path}) are not checked here.
 */
class TextureLiteralReferenceTest {

    private static final Path SOURCES = Path.of("src/main/java");
    private static final List<Path> ASSET_ROOTS = List.of(
            Path.of("src/main/resources/assets/wizards_and_beasts"),
            Path.of("src/generated/resources/assets/wizards_and_beasts"));

    /** A whole literal: starts at {@code textures/}, ends at {@code .png}, nothing concatenated after it. */
    private static final Pattern LITERAL = Pattern.compile("\"(textures/[^\"]+\\.png)\"(?!\\s*\\+)");
    /** Vanilla's own textures, bound through the default namespace. */
    private static final Pattern VANILLA = Pattern.compile("withDefaultNamespace\\(\\s*$|\"minecraft\"\\s*,\\s*$");

    @Test
    void everyLiteralTexturePathExists() throws IOException {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Matcher matcher = LITERAL.matcher(source);
                while (matcher.find()) {
                    String before = source.substring(Math.max(0, matcher.start() - 80), matcher.start());
                    if (VANILLA.matcher(before).find()) {
                        continue;
                    }
                    String path = matcher.group(1);
                    if (ASSET_ROOTS.stream().noneMatch(root -> Files.exists(root.resolve(path)))) {
                        int line = source.substring(0, matcher.start()).split("\n", -1).length;
                        missing.add(SOURCES.relativize(file) + ":" + line + " " + path);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "texture paths with no file behind them: " + missing);
    }

    @Test
    void thePatternsAreRecognised() {
        assertTrue(LITERAL.matcher("of(MODID, \"textures/gui/wandmakers_bench.png\");").find());
        assertTrue(!LITERAL.matcher("of(MODID, \"textures/gui/map/\" + path)").find());
        assertTrue(!LITERAL.matcher("\"textures/gui/a.png\" + suffix").find());
        assertTrue(VANILLA.matcher("Identifier.withDefaultNamespace(").find());
    }
}
