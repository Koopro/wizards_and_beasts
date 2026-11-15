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
 * No {@code sendParticles(…, 1, …)} inside a loop. Each call is one packet to every player within 32 blocks, so a loop
 * of single-particle calls costs a packet per particle per observer: the spell code had them for every particle line,
 * ring and shell, 65 to 300 packets a cast and 160 a second for a Patronus aura (audit 2026-11-15). A shape goes out
 * as one {@code SpellVfxShapeS2CPayload}; a scatter goes out as one call with its count.
 */
class ParticlePacketLoopTest {

    private static final Path SRC = Path.of("src", "main", "java");
    private static final Pattern CALL = Pattern.compile("\\bsendParticles\\s*\\(");
    private static final Pattern LOOP_HEADER = Pattern.compile("\\b(for|while)\\s*\\(");

    @Test
    void noSingleParticlePacketsInALoop() throws IOException {
        List<String> failures = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SRC)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readString(file);
                Matcher m = CALL.matcher(src);
                while (m.find()) {
                    List<String> args = args(src, m.end());
                    // ServerLevel.sendParticles(options, x, y, z, count, dx, dy, dz, speed)
                    if (args.size() == 9 && args.get(4).equals("1") && insideLoop(src, m.start())) {
                        int line = (int) src.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
                        failures.add(SRC.relativize(file) + ":" + line);
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), "single-particle sendParticles inside a loop (send one "
                + "SpellVfxShapeS2CPayload, or one call with a count):\n" + String.join("\n", failures));
    }

    /** Top-level comma-separated arguments of the call whose '(' ends just before {@code open}. */
    private static List<String> args(String src, int open) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) {
                    out.add(cur.toString().trim());
                    return out;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                out.add(cur.toString().trim());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        return out;
    }

    /** Whether any block enclosing {@code pos} is the body of a for or while loop. */
    private static boolean insideLoop(String src, int pos) {
        int depth = 0;
        for (int i = pos - 1; i >= 0; i--) {
            char c = src.charAt(i);
            if (c == '}') {
                depth++;
            } else if (c == '{') {
                if (depth > 0) {
                    depth--;
                    continue;
                }
                // An enclosing block opens here; its header runs back to the previous statement end.
                int start = i - 1;
                int parens = 0;
                while (start >= 0) {
                    char h = src.charAt(start);
                    if (h == ')') {
                        parens++;
                    } else if (h == '(') {
                        parens--;
                    } else if (parens == 0 && (h == ';' || h == '{' || h == '}')) {
                        break;
                    }
                    start--;
                }
                String header = src.substring(start + 1, i);
                if (LOOP_HEADER.matcher(header).find()) {
                    return true;
                }
                if (header.contains("class ") || header.contains("interface ") || header.contains("enum ")
                        || header.contains("record ")) {
                    return false;
                }
            }
        }
        return false;
    }
}
