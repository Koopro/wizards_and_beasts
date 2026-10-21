package at.koopro.wizardsandbeasts;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The common config must be a fixed point of its own correction (KNOWN_ISSUES.md §8a.1).
 *
 * <p>FML corrects the file on load and again every time its file watcher sees a change, and every
 * correction writes the file — which the watcher sees. A spec whose corrected output is not itself
 * correct therefore rewrites the file about once a second for as long as the process lives. That is
 * exactly what {@code werewolfEquipmentWhitelist} did while it used {@code defineList}: the plain form
 * pins {@code ListValueSpec.NON_EMPTY}, so its empty default failed its own check on every read.
 *
 * <p>This test holds the whole spec, not one key: a fresh config corrected once must need no further
 * correction, in memory and after a TOML write and re-parse (the watcher reads the file, not the
 * object — a value whose written form parses back as another type would loop the same way).
 */
class ConfigSpecStabilityTest {

    @Test
    void aCorrectedConfigNeedsNoFurtherCorrection() {
        CommentedConfig config = CommentedConfig.inMemory();
        int first = Config.SPEC.correct(config, (action, path, incorrect, corrected) -> {});
        assertTrue(first > 0, "an empty config was already correct; the test is not exercising the spec");
        assertTrue(Config.SPEC.isCorrect(config), "the spec's own defaults fail its checks");

        StringBuilder again = new StringBuilder();
        int second = Config.SPEC.correct(config,
                (action, path, incorrect, corrected) -> again.append(String.join(".", path)).append(' '));
        assertEquals(0, second, "a corrected config was corrected again: " + again);
    }

    @Test
    void theWrittenFileReadsBackCorrect() {
        CommentedConfig config = CommentedConfig.inMemory();
        Config.SPEC.correct(config);
        String toml = TomlFormat.instance().createWriter().writeToString(config);

        CommentedConfig reread = TomlFormat.instance().createParser().parse(toml);
        StringBuilder drift = new StringBuilder();
        int corrections = Config.SPEC.correct(reread,
                (action, path, incorrect, corrected) -> drift.append(String.join(".", path))
                        .append(" (").append(incorrect).append(" -> ").append(corrected).append(") "));
        assertEquals(0, corrections, "the file FML writes does not read back correct: " + drift);
    }
}
