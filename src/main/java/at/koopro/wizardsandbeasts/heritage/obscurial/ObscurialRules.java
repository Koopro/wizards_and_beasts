package at.koopro.wizardsandbeasts.heritage.obscurial;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.TransformationState;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Centralized balance rules for Obscurial gameplay.
 * Keeps stat/environment/instability/spell-policy logic in one place so type hooks can reuse it.
 * Resource state (drain/charge/stress) lives in {@link ObscurialResourceManager}.
 * Combat multipliers and ability costs live in {@link ObscurialCombatRules}.
 */
public final class ObscurialRules {

    private static final double TRANSFORMED_HEALTH_BONUS = 2.0;
    private static final double TRANSFORMED_SPEED_BONUS = 0.01;
    private static final double TRANSFORMED_ARMOR_BONUS = 2.0;

    /*
     * Recommended alpha tuning baseline (gameplay-first, lore-aligned):
     * - Daylight should be survivable briefly but punishing if sustained in dark form.
     * - Agitated/Volatile stress should increase ability costs/cooldowns without hard-locking play.
     * - Hostile density should escalate pressure steadily, not instantly.
     * Update related stress/cooldown/daylight constants together to preserve this feel.
     */
    private static final float DRAIN_TICK_DARK_BASE = 0.22f;
    private static final float DRAIN_TICK_DARK_DAYLIGHT_BONUS = 0.30f;
    private static final float DRAIN_TICK_FLYING_BONUS = 0.14f;
    private static final float DRAIN_REGEN_HUMAN_BASE = 0.18f;
    private static final float DRAIN_REGEN_HUMAN_DAYLIGHT_PENALTY = 0.10f;
    private static final float CHARGE_TICK_DARK_BASE = 0.28f;
    private static final float CHARGE_TICK_DARK_DAYLIGHT_BONUS = 0.24f;
    private static final float CHARGE_TICK_DARK_FLYING_BONUS = 0.12f;
    private static final float CHARGE_REGEN_HUMAN_BASE = 0.24f;
    private static final float CHARGE_REGEN_HUMAN_DAYLIGHT_BONUS = 0.08f;
    private static final float STRESS_REGEN_SAFE_BASE = 0.16f;
    private static final float STRESS_REGEN_NIGHT_BONUS = 0.14f;
    private static final float STRESS_GAIN_LOW_HP_TICK = 0.28f;
    private static final float STRESS_GAIN_HOSTILE_DENSITY = 0.15f;

    private static final float INSTABILITY_FIZZLE_BASE = 0.12f;
    private static final float INSTABILITY_FIZZLE_DAYLIGHT = 0.18f;
    private static final float INSTABILITY_FIZZLE_LOW_HP = 0.20f;
    private static final float INSTABILITY_FIZZLE_LOW_DRAIN = 0.24f;
    private static final float INSTABILITY_BACKLASH_DAMAGE = 3.0f;
    private static final float DAYLIGHT_VULN_TICK_DAMAGE = 1.5f;
    private static final int DAYLIGHT_VULN_INTERVAL_TICKS = 50;

    private static final Set<String> DARK_FORM_ALLOWED_IDS = Set.of(
            "obscurus_blast",
            "obscurus_surge",
            "obscurus_grasp"
    );
    private static final Set<String> DARK_FORM_ONLY_SPELL_IDS = Set.of(
            "obscurus_surge",
            "obscurus_grasp"
    );

    public enum StabilityTier {
        STABLE,
        UNSTABLE,
        CRITICAL
    }

    public enum LoreControlTier {
        CONTROLLED,
        FRACTURING,
        CATASTROPHIC
    }

    public enum StressTier {
        CALM,
        AGITATED,
        VOLATILE
    }

    private ObscurialRules() {}

    public static boolean isObscurial(PlayerHeritageData data) {
        return data.getSelectedHeritage() == Heritage.OBSCURIAL;
    }

    public static boolean isDarkForm(PlayerHeritageData data) {
        return "obscurial_dark".equals(data.getActiveFormId());
    }

    public static TransformationState deriveState(PlayerHeritageData data) {
        return isDarkForm(data) ? TransformationState.TRANSFORMED : TransformationState.NORMAL;
    }

    public static double getHealthBonus(PlayerHeritageData data) {
        return isDarkForm(data) ? TRANSFORMED_HEALTH_BONUS : 0.0;
    }

    public static double getSpeedBonus(PlayerHeritageData data, ServerLevel level, ServerPlayer player) {
        double amount = isDarkForm(data) ? TRANSFORMED_SPEED_BONUS : 0.0;
        if (isDaylightStrained(level, player)) {
            amount -= 0.01;
        }
        if (isNightEmpowered(level)) {
            amount += 0.005;
        }
        return amount;
    }

    public static double getArmorBonus(PlayerHeritageData data) {
        return isDarkForm(data) ? TRANSFORMED_ARMOR_BONUS : 0.0;
    }

    public static boolean isDaylightStrained(ServerLevel level, ServerPlayer player) {
        return isDaytime(level) && level.canSeeSky(player.blockPosition());
    }

    public static boolean isNightEmpowered(ServerLevel level) {
        return !isDaytime(level);
    }

    public static float computeDarkDrainPerTick(ServerPlayer player, ServerLevel level) {
        float cost = DRAIN_TICK_DARK_BASE;
        StressTier stressTier = getStressTier(ObscurialResourceManager.getStress(player));
        if (stressTier == StressTier.AGITATED) {
            cost += 0.06f;
        } else if (stressTier == StressTier.VOLATILE) {
            cost += 0.12f;
        }
        if (isDaylightStrained(level, player)) {
            cost += DRAIN_TICK_DARK_DAYLIGHT_BONUS;
        }
        if (player.getAbilities().flying) {
            cost += DRAIN_TICK_FLYING_BONUS;
        }
        return cost;
    }

    public static float computeHumanRegenPerTick(ServerPlayer player, ServerLevel level) {
        float regen = DRAIN_REGEN_HUMAN_BASE;
        if (isDaylightStrained(level, player)) {
            regen -= DRAIN_REGEN_HUMAN_DAYLIGHT_PENALTY;
        }
        return Math.max(0.01f, regen);
    }

    public static float computeDarkChargeCostPerTick(ServerPlayer player, ServerLevel level) {
        float cost = CHARGE_TICK_DARK_BASE;
        StressTier stressTier = getStressTier(ObscurialResourceManager.getStress(player));
        if (stressTier == StressTier.AGITATED) {
            cost += 0.06f;
        } else if (stressTier == StressTier.VOLATILE) {
            cost += 0.12f;
        }
        if (isDaylightStrained(level, player)) {
            cost += CHARGE_TICK_DARK_DAYLIGHT_BONUS;
        }
        if (player.getAbilities().flying) {
            cost += CHARGE_TICK_DARK_FLYING_BONUS;
        }
        return cost;
    }

    public static float computeHumanChargeRegenPerTick(ServerPlayer player, ServerLevel level) {
        float regen = CHARGE_REGEN_HUMAN_BASE;
        if (isDaylightStrained(level, player)) {
            regen += CHARGE_REGEN_HUMAN_DAYLIGHT_BONUS;
        }
        return Math.max(0.01f, regen);
    }

    public static float computeStressRecoveryPerTick(ServerPlayer player, ServerLevel level, boolean hasNearbyHostiles) {
        if (hasNearbyHostiles) return 0f;
        float recovery = STRESS_REGEN_SAFE_BASE;
        if (isNightEmpowered(level)) {
            recovery += STRESS_REGEN_NIGHT_BONUS;
        }
        return Math.max(0f, recovery);
    }

    public static float computeStressGainPerTick(ServerPlayer player, ServerLevel level, int nearbyHostiles) {
        float gain = 0f;
        if (player.getHealth() <= player.getMaxHealth() * 0.40f) {
            gain += STRESS_GAIN_LOW_HP_TICK;
        }
        if (nearbyHostiles > 0) {
            gain += Math.min(1.2f, nearbyHostiles * STRESS_GAIN_HOSTILE_DENSITY);
        }
        if (isDaylightStrained(level, player)) {
            gain += 0.12f;
        }
        return Math.max(0f, gain);
    }

    public static StabilityTier getStabilityTier(float stabilityPercent) {
        return ObscurialTierRules.getStabilityTier(stabilityPercent);
    }

    public static LoreControlTier getLoreControlTier(float stabilityPercent) {
        return ObscurialTierRules.getLoreControlTier(stabilityPercent);
    }

    public static StressTier getStressTier(float stressPercent) {
        return ObscurialTierRules.getStressTier(stressPercent);
    }

    public static float estimateDarkFormSecondsRemaining(ServerPlayer player, ServerLevel level) {
        float stability = ObscurialResourceManager.getDrain(player);
        float charge = ObscurialResourceManager.getCharge(player);
        float stabilityPerTick = Math.max(0.0001f, computeDarkDrainPerTick(player, level));
        float chargePerTick = Math.max(0.0001f, computeDarkChargeCostPerTick(player, level));
        float limitingTicks = Math.min(stability / stabilityPerTick, charge / chargePerTick);
        return Math.max(0f, limitingTicks / 20.0f);
    }

    public static boolean isSpellAllowedInDarkForm(Spell spell) {
        return ObscurialSpellPolicy.isSpellAllowedInDarkForm(spell, DARK_FORM_ALLOWED_IDS);
    }

    public static boolean isDarkFormOnlySpell(Spell spell) {
        return ObscurialSpellPolicy.isDarkFormOnlySpell(spell, DARK_FORM_ONLY_SPELL_IDS);
    }

    public static boolean isObscurialOnlySpell(Spell spell) {
        return ObscurialSpellPolicy.isObscurialOnlySpell(spell);
    }

    public static boolean isObscurialAbility(Spell spell) {
        return ObscurialSpellPolicy.isObscurialAbility(spell);
    }

    public static boolean isObscurialAbilityId(String spellId) {
        return ObscurialSpellPolicy.isObscurialAbilityId(spellId);
    }

    public static boolean canHeritageUseSpell(Heritage heritage, Spell spell) {
        if (spell == null) return false;
        if (isObscurialAbility(spell)) return false;
        if (!isObscurialOnlySpell(spell)) return true;
        return heritage == Heritage.OBSCURIAL;
    }

    public static float getInstabilityFizzleChance(ServerPlayer player, ServerLevel level) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (!isObscurial(data) || !isDarkForm(data)) return 0f;

        float chance = INSTABILITY_FIZZLE_BASE;
        if (isDaylightStrained(level, player)) chance += INSTABILITY_FIZZLE_DAYLIGHT;
        if (player.getHealth() <= player.getMaxHealth() * 0.40f) chance += INSTABILITY_FIZZLE_LOW_HP;
        if (ObscurialResourceManager.getDrain(player) <= ObscurialResourceManager.getLowDrainWarning()) chance += INSTABILITY_FIZZLE_LOW_DRAIN;
        return Math.min(0.9f, chance);
    }

    public static float getStressAbilityPenaltyMultiplier(ServerPlayer player) {
        return switch (getStressTier(ObscurialResourceManager.getStress(player))) {
            case CALM -> 1.0f;
            case AGITATED -> 1.15f;
            case VOLATILE -> 1.35f;
        };
    }

    public static float getInstabilityBacklashDamage() {
        return INSTABILITY_BACKLASH_DAMAGE;
    }

    public static boolean shouldApplyDaylightVulnerability(ServerPlayer player, ServerLevel level, long gameTick) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        return isObscurial(data)
                && isDarkForm(data)
                && isDaylightStrained(level, player)
                && gameTick % DAYLIGHT_VULN_INTERVAL_TICKS == 0;
    }

    public static float getDaylightVulnerabilityDamage() {
        return DAYLIGHT_VULN_TICK_DAMAGE;
    }

    private static boolean isDaytime(ServerLevel level) {
        return ObscurialTierRules.isDaytime(level);
    }
}
