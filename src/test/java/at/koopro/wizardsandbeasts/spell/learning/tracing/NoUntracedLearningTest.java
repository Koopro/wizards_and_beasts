package at.koopro.wizardsandbeasts.spell.learning.tracing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The acceptance rule for spell tracing, as a source scan: no normal gameplay path writes a spell into a
 * player without passing a trace.
 *
 * <p>Two calls write spell knowledge: {@code PlayerSpellData.learnSpell} and the learning service's
 * {@code commit}/{@code tryLearnSpell}. Each may only appear in the files listed here, each for a stated
 * reason. A new caller fails this test and has to either go through {@code SpellTraceService} or be added
 * to the list with its reason — which is the review the rule needs.
 */
class NoUntracedLearningTest {

    private static final Path ROOT = Path.of("src/main/java/at/koopro/wizardsandbeasts");

    /** Files allowed to call {@code learnSpell(}. */
    private static final Set<String> LEARN_SPELL_ALLOWED = Set.of(
            "spell/data/PlayerSpellData.java",                 // the definition
            "spell/learning/SpellLearningService.java",        // commit (after a passed trace) + companions
            "client/spell/state/ClientSpellDataState.java",    // client mirror of server syncs
            "spell/command/SpellCommands.java",                // /wandb spell learn — operator override
            "admin/player/PlayerAdminService.java",            // admin panel grant — operator override
            "spell/debug/SpellDevKit.java"                     // dev kit — operator override
    );

    /** Files allowed to call the service's commit entry points. */
    private static final Set<String> COMMIT_ALLOWED = Set.of(
            "spell/learning/SpellLearningService.java",        // tryLearnSpell delegates to commit
            "spell/learning/tracing/SpellTraceService.java"    // the one gameplay caller: after a passed trace
    );

    private static final Pattern LEARN_SPELL = Pattern.compile("\\.learnSpell\\s*\\(");
    private static final Pattern COMMIT = Pattern.compile("SpellLearningService\\s*\\.\\s*(commit|tryLearnSpell)\\s*\\(");

    @Test
    void onlyAllowlistedFilesWriteSpellKnowledge() throws IOException {
        assertEquals(List.of(), offenders(LEARN_SPELL, LEARN_SPELL_ALLOWED), "untraced learnSpell callers");
    }

    @Test
    void onlyTheTraceServiceCommitsALesson() throws IOException {
        assertEquals(List.of(), offenders(COMMIT, COMMIT_ALLOWED), "callers of SpellLearningService.commit");
    }

    private static List<String> offenders(Pattern pattern, Set<String> allowed) throws IOException {
        List<String> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ROOT)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String rel = ROOT.relativize(file).toString().replace('\\', '/');
                if (rel.startsWith("gametest/") || allowed.contains(rel)) {
                    continue; // game tests set up exact states on purpose
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = pattern.matcher(source);
                while (m.find()) {
                    out.add(rel + ":" + (source.substring(0, m.start()).split("\n", -1).length));
                }
            }
        }
        return out;
    }
}
