package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.entity.creature.AcromantulaSpeech.Situation;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When it rears, what it says, and that every line it can say exists. */
class AcromantulaRulesTest {

    private static final Path LANG = Path.of("src/main/resources/assets/wizards_and_beasts/lang/en_us.json");

    @Test
    void itRearsAtQuarryInSightButOutOfReach() {
        assertTrue(AcromantulaEntity.threatens(true, 8, true));
        assertFalse(AcromantulaEntity.threatens(true, 2, true), "close enough to bite");
        assertFalse(AcromantulaEntity.threatens(true, 30, true), "too far to care");
        assertFalse(AcromantulaEntity.threatens(true, 8, false), "cannot see it");
        assertFalse(AcromantulaEntity.threatens(false, 8, true), "no quarry");
    }

    @Test
    void woundsOutrankTheHuntWhichOutranksTheTerritory() {
        assertEquals(Situation.WOUNDED, AcromantulaSpeech.situation(true, true, true));
        assertEquals(Situation.HUNTING, AcromantulaSpeech.situation(true, false, true));
        assertEquals(Situation.TRESPASS, AcromantulaSpeech.situation(false, false, true));
        assertEquals(Situation.WATCHING, AcromantulaSpeech.situation(false, false, false));
    }

    @Test
    void everyLineItCanSpeakIsWritten() throws IOException {
        try (Reader reader = Files.newBufferedReader(LANG)) {
            JsonObject lang = JsonParser.parseReader(reader).getAsJsonObject();
            assertTrue(lang.has("entity.wizards_and_beasts.acromantula.says"));
            for (Situation situation : Situation.values()) {
                for (int i = 0; i < AcromantulaSpeech.LINES; i++) {
                    String key = AcromantulaSpeech.key(situation, i);
                    assertTrue(lang.has(key), "missing speech line " + key);
                }
            }
        }
    }
}
