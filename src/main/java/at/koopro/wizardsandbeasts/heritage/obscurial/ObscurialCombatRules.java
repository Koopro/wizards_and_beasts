package at.koopro.wizardsandbeasts.heritage.obscurial;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Combat calculations for Obscurial: damage/cooldown multipliers, cast spikes, ability costs,
 * rage threshold, and collapse constants. Reads resource state via {@link ObscurialResourceManager}.
 */
public final class ObscurialCombatRules {

    private static final float BASE_NORMAL_DAMAGE_MULT = 0.95f;
    private static final float BASE_TRANSFORMED_DAMAGE_MULT = 1.20f;
    private static final float DAYLIGHT_DAMAGE_PENALTY = 0.10f;
    private static final float NIGHT_DAMAGE_BONUS = 0.08f;
    private static final float LOW_HEALTH_FRENZY_BONUS = 0.10f;

    private static final float BASE_NORMAL_COOLDOWN_MULT = 1.08f;
    private static final float BASE_TRANSFORMED_COOLDOWN_MULT = 0.90f;
    private static final float DAYLIGHT_COOLDOWN_PENALTY = 0.12f;
    private static final float NIGHT_COOLDOWN_BONUS = -0.06f;
    private static final float ABILITY_DARK_FORM_BONUS = -0.10f;
    private static final float ABILITY_CRITICAL_STRESS_PENALTY = 0.16f;

    private static final float DRAIN_TICK_CASTING_SPIKE = 7.5f;
    private static final float STRESS_SPIKE_DAMAGE = 6.0f;
    private static final float STRESS_SPIKE_BLOCKED_CAST = 7.0f;
    private static final float STRESS_SPIKE_DARK_DAMAGE = 4.0f;
    private static final float DARK_DESTABILIZE_CONTROL_SPIKE = 3.5f;
    private static final float DARK_DESTABILIZE_PRESSURE_SPIKE = 4.5f;
    private static final float BLOCKED_CAST_PRESSURE_BACKLASH = 8.0f;
    private static final float HUMAN_FAILED_CAST_STRESS_SPIKE = 2.0f;
    private static final float SURGE_CONTROL_COST = 3.0f;
    private static final float SURGE_PRESSURE_COST = 4.5f;
    private static final float SURGE_STRESS_COST = 2.5f;
    private static final float GRASP_CONTROL_COST = 4.5f;
    private static final float GRASP_PRESSURE_COST = 5.0f;
    private static final float GRASP_STRESS_COST = 3.0f;
    private static final float RAGE_CONTROL_DRAIN_TICK = 0.08f;
    private static final float RAGE_STRESS_GAIN_TICK = 0.10f;
    private static final float RAGE_DAMAGE_BONUS = 0.22f;
    private static final int RAGE_SPEED_AMPLIFIER = 0;
    private static final int RAGE_SPEED_DURATION_TICKS = 30;
    private static final int COLLAPSE_WEAKNESS_TICKS = 20 * 8;
    private static final int COLLAPSE_SLOWNESS_TICKS = 20 * 6;
    private static final int COLLAPSE_WEAKNESS_AMPLIFIER = 0;
    private static final int COLLAPSE_SLOWNESS_AMPLIFIER = 0;
    private static final long COLLAPSE_CAST_INSTABILITY_TICKS = 20L * 9L;
    private static final float COLLAPSE_CAST_FIZZLE_CHANCE = 0.20f;
    private static final float COLLAPSE_CAST_BACKLASH_DAMAGE = 1.5f;
    private static final long FORCED_DARK_FORM_DURATION_TICKS = 20L * 12L;

    private ObscurialCombatRules() {}

    public static float getDamageMultiplier(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (!ObscurialRules.isObscurial(data)) return 1.0f;

        ServerLevel level = (ServerLevel) player.level();
        float multiplier = ObscurialRules.isDarkForm(data) ? BASE_TRANSFORMED_DAMAGE_MULT : BASE_NORMAL_DAMAGE_MULT;

        if (ObscurialRules.isDaylightStrained(level, player)) {
            multiplier -= DAYLIGHT_DAMAGE_PENALTY;
        } else if (ObscurialRules.isNightEmpowered(level)) {
            multiplier += NIGHT_DAMAGE_BONUS;
        }

        if (ObscurialRules.isDarkForm(data) && player.getHealth() <= player.getMaxHealth() * 0.5f) {
            multiplier += LOW_HEALTH_FRENZY_BONUS;
        }
        if (ObscurialRules.isDarkForm(data) && isRageThresholdActive(player)) {
            multiplier += RAGE_DAMAGE_BONUS;
        }
        return Math.max(0.1f, multiplier);
    }

    public static float getCooldownMultiplier(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (!ObscurialRules.isObscurial(data)) return 1.0f;

        ServerLevel level = (ServerLevel) player.level();
        float multiplier = ObscurialRules.isDarkForm(data) ? BASE_TRANSFORMED_COOLDOWN_MULT : BASE_NORMAL_COOLDOWN_MULT;

        if (ObscurialRules.isDaylightStrained(level, player)) {
            multiplier += DAYLIGHT_COOLDOWN_PENALTY;
        } else if (ObscurialRules.isNightEmpowered(level)) {
            multiplier += NIGHT_COOLDOWN_BONUS;
        }

        return Math.max(0.2f, multiplier);
    }

    public static void applyCastModifiers(ModifierStack stack, ServerPlayer player) {
        applyDamageModifier(stack, player);
        applyCooldownModifier(stack, player);
    }

    public static void applyDamageModifier(ModifierStack stack, ServerPlayer player) {
        stack.multiplyDamage(getDamageMultiplier(player), "obscurial");
    }

    public static void applyCooldownModifier(ModifierStack stack, ServerPlayer player) {
        stack.multiplyCooldown(getCooldownMultiplier(player), "obscurial");
    }

    public static void applyAbilityCooldownModifier(ModifierStack stack, ServerPlayer player) {
        stack.multiplyCooldown(getAbilityCooldownMultiplier(player), "obscurial_ability");
    }

    public static float getAbilityCooldownMultiplier(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (!ObscurialRules.isObscurial(data)) return 1.0f;
        ServerLevel level = (ServerLevel) player.level();
        float mult = getCooldownMultiplier(player);
        if (ObscurialRules.isDarkForm(data)) {
            mult += ABILITY_DARK_FORM_BONUS;
        }
        ObscurialRules.StressTier stressTier = ObscurialRules.getStressTier(ObscurialResourceManager.getStress(player));
        if (stressTier == ObscurialRules.StressTier.VOLATILE) {
            mult += ABILITY_CRITICAL_STRESS_PENALTY;
        } else if (stressTier == ObscurialRules.StressTier.AGITATED) {
            mult += 0.08f;
        } else if (ObscurialRules.isNightEmpowered(level)) {
            mult -= 0.04f;
        }
        return Math.max(0.2f, mult);
    }

    public static void consumeCastSpike(ServerPlayer player) {
        ObscurialResourceManager.setDrain(player, ObscurialResourceManager.getDrain(player) - DRAIN_TICK_CASTING_SPIKE);
    }

    public static void applyDarkFormStressSpike(ServerPlayer player) {
        ObscurialResourceManager.addStress(player, STRESS_SPIKE_DARK_DAMAGE);
        ObscurialResourceManager.setDrain(player, ObscurialResourceManager.getDrain(player) - DARK_DESTABILIZE_CONTROL_SPIKE);
        ObscurialResourceManager.setCharge(player, ObscurialResourceManager.getCharge(player) - DARK_DESTABILIZE_PRESSURE_SPIKE);
    }

    public static void applyBlockedCastStressSpike(ServerPlayer player) {
        ObscurialResourceManager.addStress(player, STRESS_SPIKE_BLOCKED_CAST);
    }

    public static void applyDamageStressSpike(ServerPlayer player) {
        ObscurialResourceManager.addStress(player, STRESS_SPIKE_DAMAGE);
    }

    public static void applyBlockedCastPressureBacklash(ServerPlayer player) {
        ObscurialResourceManager.setCharge(player, ObscurialResourceManager.getCharge(player) - BLOCKED_CAST_PRESSURE_BACKLASH);
    }

    public static void applyHumanFailedCastStressSpike(ServerPlayer player) {
        ObscurialResourceManager.addStress(player, HUMAN_FAILED_CAST_STRESS_SPIKE);
    }

    public static void applySurgeCosts(ServerPlayer player) {
        applyAbilityCosts(player, SURGE_CONTROL_COST, SURGE_PRESSURE_COST, SURGE_STRESS_COST);
    }

    public static void applyGraspCosts(ServerPlayer player) {
        applyAbilityCosts(player, GRASP_CONTROL_COST, GRASP_PRESSURE_COST, GRASP_STRESS_COST);
    }

    public static boolean isRageThresholdActive(ServerPlayer player) {
        return ObscurialRules.getLoreControlTier(ObscurialResourceManager.getDrain(player)) == ObscurialRules.LoreControlTier.CATASTROPHIC;
    }

    public static void applyRageThresholdEffects(ServerPlayer player, ServerLevel level) {
        if (!isRageThresholdActive(player)) return;
        ObscurialResourceManager.setDrain(player, ObscurialResourceManager.getDrain(player) - RAGE_CONTROL_DRAIN_TICK);
        ObscurialResourceManager.addStress(player, RAGE_STRESS_GAIN_TICK);
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, RAGE_SPEED_DURATION_TICKS,
                RAGE_SPEED_AMPLIFIER, true, false, true));
    }

    public static int getCollapseWeaknessTicks() {
        return COLLAPSE_WEAKNESS_TICKS;
    }

    public static int getCollapseSlownessTicks() {
        return COLLAPSE_SLOWNESS_TICKS;
    }

    public static int getCollapseWeaknessAmplifier() {
        return COLLAPSE_WEAKNESS_AMPLIFIER;
    }

    public static int getCollapseSlownessAmplifier() {
        return COLLAPSE_SLOWNESS_AMPLIFIER;
    }

    public static long getForcedDarkFormDurationTicks() {
        return FORCED_DARK_FORM_DURATION_TICKS;
    }

    public static long getCollapseCastInstabilityTicks() {
        return COLLAPSE_CAST_INSTABILITY_TICKS;
    }

    public static float getCollapseCastFizzleChance() {
        return COLLAPSE_CAST_FIZZLE_CHANCE;
    }

    public static float getCollapseCastBacklashDamage() {
        return COLLAPSE_CAST_BACKLASH_DAMAGE;
    }

    private static void applyAbilityCosts(ServerPlayer player, float drainCost, float pressureCost, float stressCost) {
        float stressPenalty = ObscurialRules.getStressAbilityPenaltyMultiplier(player);
        if (ObscurialRules.isDaylightStrained((ServerLevel) player.level(), player)) {
            stressPenalty += 0.15f;
        }
        ObscurialResourceManager.setDrain(player, ObscurialResourceManager.getDrain(player) - (drainCost * stressPenalty));
        ObscurialResourceManager.setCharge(player, ObscurialResourceManager.getCharge(player) - (pressureCost * stressPenalty));
        ObscurialResourceManager.addStress(player, stressCost * stressPenalty);
    }
}
