package at.koopro.wizardsandbeasts.wand.resonance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the arithmetic that decides whether a wand can bond at all.
 *
 * <p>The shipped configuration made bonding impossible for a wizard with no heritage variant: wood
 * affinity scored a hard 0 against a 0.4 weight, capping the weighted total at 0.60 under a 0.65 match
 * threshold. Since casting requires a bonded wand ({@code SpellCastService} rejects with
 * {@code WAND_NOT_BONDED}), that single unreachable inequality locked a survival player out of every
 * spell in the mod. These are cheap inequalities, and the failure mode they cover is total.
 */
class WandBondReachabilityTest {

    private static float bestPossibleScore(WandResonanceConfig cfg, float woodAffinity) {
        // Core, length and flexibility each top out at 1.0 for a perfectly suited wand.
        return cfg.woodWeight() * woodAffinity
                + cfg.coreWeight()
                + cfg.lengthWeight()
                + cfg.flexibilityWeight();
    }

    @Test
    void aWizardWithNoVariantCanStillReachTheMatchThreshold() {
        WandResonanceConfig cfg = WandResonanceConfig.DEFAULT;
        assertTrue(bestPossibleScore(cfg, WandResonanceSystem.NEUTRAL_WOOD_AFFINITY) >= cfg.matchThreshold(),
                "no-variant ceiling " + bestPossibleScore(cfg, WandResonanceSystem.NEUTRAL_WOOD_AFFINITY)
                        + " must reach matchThreshold " + cfg.matchThreshold()
                        + " — otherwise no wand can ever bond and nothing can be cast");
    }

    @Test
    void aWizardWithAMatchingVariantCanReachTheMatchThreshold() {
        WandResonanceConfig cfg = WandResonanceConfig.DEFAULT;
        assertTrue(bestPossibleScore(cfg, 1.0f) >= cfg.matchThreshold());
    }

    @Test
    void neutralAffinityStillLeavesTheVariantWorthSomething() {
        WandResonanceConfig cfg = WandResonanceConfig.DEFAULT;
        assertTrue(bestPossibleScore(cfg, 1.0f) > bestPossibleScore(cfg, WandResonanceSystem.NEUTRAL_WOOD_AFFINITY),
                "a matching variant must still score strictly better than no variant at all");
    }

    @Test
    void theWeightsFormAFullUnit() {
        WandResonanceConfig cfg = WandResonanceConfig.DEFAULT;
        float sum = cfg.woodWeight() + cfg.coreWeight() + cfg.lengthWeight() + cfg.flexibilityWeight();
        assertTrue(Math.abs(sum - 1.0f) < 1.0e-5f, "weights should sum to 1.0 but were " + sum);
    }
}
