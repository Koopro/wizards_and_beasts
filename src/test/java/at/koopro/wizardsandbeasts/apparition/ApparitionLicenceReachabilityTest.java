package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.apparition.licence.ApparitionLicence;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleDefaults;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the one property the Apparition licence lost for its whole existence: that a player can get one.
 *
 * <p>{@code apparitionLicensed} was set in exactly two places, both admin commands, while the flag was
 * fully live in play — {@code SplinchResolver} taxes an unlicensed wizard's miss and {@code TraceService}
 * files the jump as an offence. A cost with no remedy is invisible in every single-player test that does
 * not go looking for it, which is why it survived so long, and why these are worth pinning.
 *
 * <p>Deliberately structural rather than behavioural: {@link ApparitionLicence#evaluate} needs a live
 * {@code ServerPlayer}, so what is checked here is the wiring that makes the path exist at all.
 */
class ApparitionLicenceReachabilityTest {

    private static final Path LICENCE_SRC = Path.of("src", "main", "java", "at", "koopro",
            "wizardsandbeasts", "apparition", "licence", "ApparitionLicence.java");
    private static final Path COMMANDS_SRC = Path.of("src", "main", "java", "at", "koopro",
            "wizardsandbeasts", "apparition", "command", "ApparitionPointCommands.java");

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    @Test
    void aNonAdminPathGrantsTheLicence() throws IOException {
        String licence = read(LICENCE_SRC);
        assertTrue(licence.contains("setApparitionLicensed(player, true)"),
                "ApparitionLicence no longer grants the licence — if the only remaining writers are the "
                        + "admin commands then the miss multiplier and the Trace offence are back to "
                        + "punishing a state no player can leave");
    }

    @Test
    void theTestIsReachableFromTheUngatedCommandGroup() throws IOException {
        String commands = read(COMMANDS_SRC);
        assertTrue(commands.contains("Commands.literal(\"test\")"),
                "the apparate command tree no longer registers `test`");
        assertFalse(commands.contains("WizardsAndBeastsCommandPermissions"),
                "ApparitionPointCommands has become permission-gated — `test` and `licence` are ordinary "
                        + "gameplay, and gating them would put the licence back out of a player's reach");
    }

    /**
     * The licence's bite is unconditional, so its remedy must be too. {@code SplinchResolver} carries no
     * Ministry check, meaning an unlicensed wizard is taxed even in a world where MINISTRY is off — which
     * it is by default. Hanging the test off that module would restore the original bug in a subtler form.
     */
    @Test
    void theTestDoesNotDependOnAModuleThatShipsDisabled() throws IOException {
        String licence = read(LICENCE_SRC);
        assertFalse(licence.contains("isEnabled(Module.MINISTRY)"),
                "the Apparition test now gates on Module.MINISTRY, which ships "
                        + ModuleDefaults.shipped(Module.MINISTRY)
                        + " — the licence would be unobtainable again in a default world");
        assertTrue(ModuleDefaults.shipped(Module.PLAYER_ABILITIES).grantsAccess(),
                "PLAYER_ABILITIES no longer grants access by default, so the module the test does gate "
                        + "on would keep it unreachable");
    }

    @Test
    void splinchResolverStillHasNoMinistryGate() throws IOException {
        Path resolver = Path.of("src", "main", "java", "at", "koopro", "wizardsandbeasts",
                "apparition", "splinch", "SplinchResolver.java");
        String text = read(resolver);
        assertFalse(text.contains("Module.MINISTRY"),
                "SplinchResolver gained a Ministry gate — if the unlicensed tax is now Ministry-only then "
                        + "the reasoning behind where the test lives needs revisiting, not just this test");
    }

    @Test
    void everyRefusalReasonHasALangKey() throws IOException {
        String licence = read(LICENCE_SRC);
        String lang = read(Path.of("src", "main", "resources", "assets", "wizards_and_beasts",
                "lang", "en_us.json"));
        // The keys are the argument to Eligibility.no(...), which is the only way a refusal is worded.
        Stream.of("module", "already_licensed", "elf_magic", "untrained", "splinched", "practice")
                .forEach(reason -> {
                    String key = "apparition.wizards_and_beasts.test.deny." + reason;
                    assertTrue(licence.contains(key),
                            "ApparitionLicence no longer refuses with " + key
                                    + " — update this test if the gate stack changed on purpose");
                    assertTrue(lang.contains('"' + key + '"'),
                            key + " has no en_us entry, so the refusal renders as a raw key");
                });
    }

    @Test
    void thePracticeThresholdIsReachableAndNotTrivial() {
        assertTrue(ApparitionLicence.REQUIRED_PROFICIENCY > 0.0f,
                "a zero practice requirement hands out the licence on the first attempt");
        assertTrue(ApparitionLicence.REQUIRED_PROFICIENCY < 1.0f,
                "proficiency is clamped to 1.0, so a requirement at or above it can never be met");
    }
}
