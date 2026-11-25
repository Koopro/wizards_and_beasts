package at.koopro.wizardsandbeasts.creature.bond;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B5, the Magizoologist: trust at half the curve, and the rare harvests a tier early. */
class TrustCurveTest {

    @Test
    void theVocationHalvesTheCurve() {
        assertEquals(4, TrustCurve.scaled(4, false, false));
        assertEquals(6, TrustCurve.scaled(4, false, true), "Creature Knowledge keeps its half again");
        assertEquals(8, TrustCurve.scaled(4, true, true), "a Magizoologist earns trust at twice the rate");
    }

    @Test
    void harvestsOpenATierEarly() {
        assertEquals(DiscoveryTier.STUDIED, DiscoveryTier.OBSERVED.raised());
        assertEquals(DiscoveryTier.KNOWN, DiscoveryTier.KNOWN.raised(), "never past a complete page");
    }

    @Test
    void theMagizoologistIsGrantedBoth() throws IOException {
        String json = Files.readString(Path.of("src/main/resources/data/wizards_and_beasts/vocations/magizoologist.json"));
        var granted = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("grantedAbilities").toString();
        assertTrue(granted.contains("trust_half_curve") && granted.contains("advanced_harvest"), granted);
    }
}
