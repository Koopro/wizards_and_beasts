package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.apparition.charge.ApparitionRiskReadout;
import at.koopro.wizardsandbeasts.apparition.charge.Destabilization;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchResolver;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The risk readout tells the truth: the margin it shows is exactly the slack the ladder gives, and every factor it
 * can name has words.
 */
class ApparitionRiskReadoutTest {

    private static Destabilization with(boolean moving, boolean famished, boolean licensed) {
        return new Destabilization(0, moving, false, false, famished, false, licensed);
    }

    @Test
    void theMarginIsTheLadderSlack() {
        assertEquals(SplinchResolver.CLEAN_MAX, SplinchResolver.cleanMargin(Destabilization.NONE));
        for (Destabilization d : List.of(Destabilization.NONE, with(true, false, true), with(false, false, false),
                with(true, true, true), with(true, false, false))) {
            int margin = SplinchResolver.cleanMargin(d);
            if (margin >= 0) {
                assertEquals(SplinchTier.CLEAN, SplinchResolver.resolve(margin, d), "the shown slack must land clean");
            }
            if (margin < SplinchResolver.CLEAN_MAX) {
                assertTrue(SplinchResolver.resolve(margin + 1, d) != SplinchTier.CLEAN,
                        "one tick past the shown slack must not be clean: " + d);
            }
        }
        assertEquals(-1, SplinchResolver.cleanMargin(with(true, true, true)),
                "two careless conditions splinch even a perfect release, and the readout says so");
    }

    @Test
    void everyFactorHasWords() throws IOException {
        String lang = Files.readString(Path.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json"));
        Destabilization everything = new Destabilization(1, true, true, true, true, true, false);
        List<String> factors = ApparitionRiskReadout.factors(everything);
        assertEquals(7, factors.size());
        for (String factor : factors) {
            assertTrue(lang.contains("\"apparition.wizards_and_beasts.risk.factor." + factor + "\""), factor);
        }
        for (String key : List.of("steady", "unsteady", "doomed")) {
            assertTrue(lang.contains("\"apparition.wizards_and_beasts.risk." + key + "\""), key);
        }
        assertTrue(ApparitionRiskReadout.factors(Destabilization.NONE).isEmpty());
    }

    @Test
    void theTestRefusesAwayFromTheMinistryOnlyWhenTheMinistryExamines() throws IOException {
        String licence = Files.readString(Path.of(
                "src/main/java/at/koopro/wizardsandbeasts/apparition/licence/ApparitionLicence.java"));
        assertTrue(licence.contains("apparition.wizards_and_beasts.test.deny.at_ministry"));
        // Fail-open: the in-person rule hangs off the Ministry location being open, never off a bare module check.
        assertTrue(licence.contains("Locations.open(MINISTRY) != null"));
        String commands = Files.readString(Path.of(
                "src/main/java/at/koopro/wizardsandbeasts/apparition/command/ApparitionPointCommands.java"));
        assertTrue(commands.contains("ApparitionLicence.takeTestAway(player)"),
                "the command must not bypass the Ministry's counter");
    }
}
