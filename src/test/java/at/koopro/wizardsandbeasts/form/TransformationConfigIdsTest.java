package at.koopro.wizardsandbeasts.form;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every form id a transformation config names must be a form that exists.
 *
 * <p>Six of the ten registered transitions pointed at ids that were never registered:
 * {@code vampire_default} against a form actually called {@code vampire_human}, and
 * {@code merpeople_land}/{@code merpeople_water} against {@code merfolk_land}/{@code merfolk_water}.
 * A config keyed on a nonexistent form is not a crash — the lookup simply never matches, so the
 * transition silently has no configuration and any trigger written for it would fail to resolve.
 *
 * <p>That is why the punchlist recorded veela, merfolk and vampire as "config-only, nothing drives
 * them": the configs themselves could not have driven anything. A string-keyed registry with no
 * compile-time link to the thing it keys on needs exactly this check.
 */
class TransformationConfigIdsTest {

    private static final Path FORM_REGISTRY = Path.of("src", "main", "java", "at", "koopro",
            "wizardsandbeasts", "form", "FormRegistry.java");
    private static final Path CONFIG_REGISTRY = Path.of("src", "main", "java", "at", "koopro",
            "wizardsandbeasts", "form", "TransformationConfigRegistry.java");

    private static Set<String> matches(Path path, Pattern pattern, int... groups) throws IOException {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(Files.readString(path));
        while (matcher.find()) {
            for (int group : groups) {
                found.add(matcher.group(group));
            }
        }
        return found;
    }

    @Test
    void everyTransitionNamesAFormThatExists() throws IOException {
        Set<String> forms = matches(FORM_REGISTRY,
                Pattern.compile("register\\(form\\(\"([a-z_]+)\""), 1);
        assertTrue(forms.size() > 10, "form id scrape found only " + forms.size()
                + " forms — the registration shape changed and this test is no longer reading it");

        Set<String> referenced = matches(CONFIG_REGISTRY,
                Pattern.compile("register\\(\"([a-z_]+)\", \"([a-z_]+)\""), 1, 2);
        assertTrue(!referenced.isEmpty(), "no transitions found to check");

        for (String id : referenced) {
            assertTrue(forms.contains(id),
                    "TransformationConfigRegistry names form '" + id + "', which FormRegistry never "
                            + "registers — the config can never be looked up, so the transition has no "
                            + "configuration at all. Known forms: " + forms);
        }
    }
}
