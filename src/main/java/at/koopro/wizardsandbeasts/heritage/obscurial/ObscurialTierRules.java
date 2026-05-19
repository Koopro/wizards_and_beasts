package at.koopro.wizardsandbeasts.heritage.obscurial;

import net.minecraft.server.level.ServerLevel;

final class ObscurialTierRules {

    private static final float STABILITY_CRITICAL_THRESHOLD = 25f;
    private static final float STABILITY_UNSTABLE_THRESHOLD = 60f;
    private static final float STRESS_AGITATED_THRESHOLD = 40f;
    private static final float STRESS_VOLATILE_THRESHOLD = 70f;

    private ObscurialTierRules() {
    }

    static ObscurialRules.StabilityTier getStabilityTier(float stabilityPercent) {
        if (stabilityPercent <= STABILITY_CRITICAL_THRESHOLD) {
            return ObscurialRules.StabilityTier.CRITICAL;
        }
        if (stabilityPercent <= STABILITY_UNSTABLE_THRESHOLD) {
            return ObscurialRules.StabilityTier.UNSTABLE;
        }
        return ObscurialRules.StabilityTier.STABLE;
    }

    static ObscurialRules.LoreControlTier getLoreControlTier(float stabilityPercent) {
        return switch (getStabilityTier(stabilityPercent)) {
            case STABLE -> ObscurialRules.LoreControlTier.CONTROLLED;
            case UNSTABLE -> ObscurialRules.LoreControlTier.FRACTURING;
            case CRITICAL -> ObscurialRules.LoreControlTier.CATASTROPHIC;
        };
    }

    static ObscurialRules.StressTier getStressTier(float stressPercent) {
        if (stressPercent >= STRESS_VOLATILE_THRESHOLD) {
            return ObscurialRules.StressTier.VOLATILE;
        }
        if (stressPercent >= STRESS_AGITATED_THRESHOLD) {
            return ObscurialRules.StressTier.AGITATED;
        }
        return ObscurialRules.StressTier.CALM;
    }

    static boolean isDaytime(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000L;
        return dayTime >= 0L && dayTime < 12300L;
    }
}
