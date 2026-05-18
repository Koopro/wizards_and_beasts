package at.koopro.wizardsandbeasts.type;

import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObscurialTierRulesTest {

    @Test
    void stabilityThresholds_matchLoreTuning() {
        assertEquals(ObscurialRules.StabilityTier.CRITICAL, ObscurialRules.getStabilityTier(25.0f));
        assertEquals(ObscurialRules.StabilityTier.UNSTABLE, ObscurialRules.getStabilityTier(60.0f));
        assertEquals(ObscurialRules.StabilityTier.STABLE, ObscurialRules.getStabilityTier(60.1f));
    }

    @Test
    void stressThresholds_matchLoreTuning() {
        assertEquals(ObscurialRules.StressTier.CALM, ObscurialRules.getStressTier(39.9f));
        assertEquals(ObscurialRules.StressTier.AGITATED, ObscurialRules.getStressTier(40.0f));
        assertEquals(ObscurialRules.StressTier.VOLATILE, ObscurialRules.getStressTier(70.0f));
    }

    @Test
    void loreControlTier_tracksStabilityTiers() {
        assertEquals(ObscurialRules.LoreControlTier.CATASTROPHIC, ObscurialRules.getLoreControlTier(24.0f));
        assertEquals(ObscurialRules.LoreControlTier.FRACTURING, ObscurialRules.getLoreControlTier(45.0f));
        assertEquals(ObscurialRules.LoreControlTier.CONTROLLED, ObscurialRules.getLoreControlTier(75.0f));
    }
}
