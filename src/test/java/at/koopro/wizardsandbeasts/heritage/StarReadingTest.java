package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.heritage.centaur.StarReading;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A centaur's reading has to be <em>true</em>, or it is flavour text pretending to be a mechanic.
 *
 * <p>So the calendar is checked against the same arithmetic the werewolf transform runs on
 * ({@link WerewolfRules#moonPhase}): if the stars say three nights and the wolf comes in two, the trait is a lie the
 * player has no way to catch.
 */
class StarReadingTest {

    private static long dayOf(long day) {
        // Night of the given day, past the 12300 boundary WerewolfRules uses.
        return day * 24000L + 14000L;
    }

    @Test
    void theCalendarAgreesWithTheMoonTheWolvesTransformOn() {
        for (long day = 0; day < 40; day++) {
            long time = dayOf(day);
            int phase = WerewolfRules.moonPhase(time);
            int nights = StarReading.nightsUntilFull(time);
            if (phase == WerewolfRules.FULL_MOON) {
                assertEquals(0, nights, "day " + day + " is a full moon and should be announced as tonight");
            } else {
                assertEquals(WerewolfRules.FULL_MOON, WerewolfRules.moonPhase(dayOf(day + nights)),
                        "day " + day + " promised the full moon in " + nights + " nights and it is not there");
            }
        }
    }

    @Test
    void aFullMoonNightIsAnnouncedAsTonight() {
        long full = -1;
        for (long day = 0; day < 16; day++) {
            if (WerewolfRules.moonPhase(dayOf(day)) == WerewolfRules.FULL_MOON) {
                full = day;
                break;
            }
        }
        assertTrue(full >= 0, "no full moon in sixteen days, which means the phase maths moved");

        List<Component> lines = StarReading.describe(dayOf(full), false, false);
        assertTrue(contains(lines, "full_tonight"), "a full moon was not announced: " + keys(lines));
        assertTrue(contains(lines, "clear"), "clear weather was not reported: " + keys(lines));
    }

    @Test
    void weatherIsReportedWithTheStormWinningOverTheRain() {
        long time = dayOf(1);
        assertTrue(contains(StarReading.describe(time, true, true), "thunder"),
                "a storm should be named, not just rain");
        assertTrue(contains(StarReading.describe(time, true, false), "rain"));
        assertTrue(contains(StarReading.describe(time, false, false), "clear"));
    }

    @Test
    void everyReadingNamesThePhase() {
        for (long day = 0; day < 8; day++) {
            List<Component> lines = StarReading.describe(dayOf(day), false, false);
            assertEquals(3, lines.size(), "a reading is a phase, a calendar line and the weather");
            assertTrue(contains(lines, "phase"), "the phase went unnamed on day " + day);
        }
    }

    private static boolean contains(List<Component> lines, String suffix) {
        return keys(lines).stream().anyMatch(key -> key.endsWith("stars." + suffix));
    }

    private static List<String> keys(List<Component> lines) {
        return lines.stream()
                .map(line -> line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                        ? t.getKey() : line.getString())
                .toList();
    }
}
