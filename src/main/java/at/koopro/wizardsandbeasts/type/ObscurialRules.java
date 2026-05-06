package at.koopro.wizardsandbeasts.type;

import at.koopro.wizardsandbeasts.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.Spell;
import at.koopro.wizardsandbeasts.spell.SpellCategory;
import at.koopro.wizardsandbeasts.spell.cast.ModifierStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.Set;

/**
 * Centralized balance rules for Obscurial gameplay.
 * Keeps stat/combat/environment logic in one place so type hooks can reuse it.
 */
public final class ObscurialRules {

    private static final double TRANSFORMED_HEALTH_BONUS = 2.0;
    private static final double TRANSFORMED_SPEED_BONUS = 0.01;
    private static final double TRANSFORMED_ARMOR_BONUS = 2.0;

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

    private static final String FLAG_DRAIN = "obscurial_drain";
    private static final String FLAG_CHARGE = "obscurial_charge";
    private static final String FLAG_STRESS = "obscurial_stress";
    private static final String FLAG_LOCKOUT_UNTIL = "obscurial_lockout_until_tick";
    private static final String FLAG_VENT_COOLDOWN_UNTIL = "obscurial_vent_cooldown_until_tick";

    /*
     * Recommended alpha tuning baseline (gameplay-first, lore-aligned):
     * - Daylight should be survivable briefly but punishing if sustained in dark form.
     * - Agitated/Volatile stress should increase ability costs/cooldowns without hard-locking play.
     * - Hostile density should escalate pressure steadily, not instantly.
     * Update related stress/cooldown/daylight constants together to preserve this feel.
     */
    private static final float MAX_DRAIN = 100f;
    private static final float LOW_DRAIN_WARNING = 20f;
    private static final float DRAIN_TICK_DARK_BASE = 0.22f;
    private static final float DRAIN_TICK_DARK_DAYLIGHT_BONUS = 0.30f;
    private static final float DRAIN_TICK_FLYING_BONUS = 0.14f;
    private static final float DRAIN_TICK_CASTING_SPIKE = 7.5f;
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
    private static final float STRESS_VENT_RECOVERY = 24.0f;
    private static final long STRESS_VENT_COOLDOWN_TICKS = 20L * 16L;
    private static final int STRESS_VENT_DRAWBACK_TICKS = 20 * 4;
    private static final int COLLAPSE_WEAKNESS_TICKS = 20 * 8;
    private static final int COLLAPSE_SLOWNESS_TICKS = 20 * 6;
    private static final int COLLAPSE_WEAKNESS_AMPLIFIER = 0;
    private static final int COLLAPSE_SLOWNESS_AMPLIFIER = 0;
    private static final long COLLAPSE_CAST_INSTABILITY_TICKS = 20L * 9L;
    private static final float COLLAPSE_CAST_FIZZLE_CHANCE = 0.20f;
    private static final float COLLAPSE_CAST_BACKLASH_DAMAGE = 1.5f;
    private static final long FORCED_DARK_FORM_DURATION_TICKS = 20L * 12L;
    private static final long LOCKOUT_TICKS = 20L * 18L;

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

    public static float getDamageMultiplier(ServerPlayer player) {
        PlayerHeritageData data = player.getData(at.koopro.wizardsandbeasts.registry.ModAttachments.HERITAGE_DATA.get());
        if (!isObscurial(data)) {
            return 1.0f;
        }

        ServerLevel level = (ServerLevel) player.level();
        float multiplier = isDarkForm(data) ? BASE_TRANSFORMED_DAMAGE_MULT : BASE_NORMAL_DAMAGE_MULT;

        if (isDaylightStrained(level, player)) {
            multiplier -= DAYLIGHT_DAMAGE_PENALTY;
        } else if (isNightEmpowered(level)) {
            multiplier += NIGHT_DAMAGE_BONUS;
        }

        if (isDarkForm(data) && player.getHealth() <= player.getMaxHealth() * 0.5f) {
            multiplier += LOW_HEALTH_FRENZY_BONUS;
        }
        if (isDarkForm(data) && isRageThresholdActive(player)) {
            multiplier += RAGE_DAMAGE_BONUS;
        }
        return Math.max(0.1f, multiplier);
    }

    public static float getCooldownMultiplier(ServerPlayer player) {
        PlayerHeritageData data = player.getData(at.koopro.wizardsandbeasts.registry.ModAttachments.HERITAGE_DATA.get());
        if (!isObscurial(data)) {
            return 1.0f;
        }

        ServerLevel level = (ServerLevel) player.level();
        float multiplier = isDarkForm(data) ? BASE_TRANSFORMED_COOLDOWN_MULT : BASE_NORMAL_COOLDOWN_MULT;

        if (isDaylightStrained(level, player)) {
            multiplier += DAYLIGHT_COOLDOWN_PENALTY;
        } else if (isNightEmpowered(level)) {
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
        PlayerHeritageData data = player.getData(at.koopro.wizardsandbeasts.registry.ModAttachments.HERITAGE_DATA.get());
        if (!isObscurial(data)) {
            return 1.0f;
        }
        ServerLevel level = (ServerLevel) player.level();
        float mult = getCooldownMultiplier(player);
        if (isDarkForm(data)) {
            mult += ABILITY_DARK_FORM_BONUS;
        }
        StressTier stressTier = getStressTier(getStress(player));
        if (stressTier == StressTier.VOLATILE) {
            mult += ABILITY_CRITICAL_STRESS_PENALTY;
        } else if (stressTier == StressTier.AGITATED) {
            mult += 0.08f;
        } else if (isNightEmpowered(level)) {
            mult -= 0.04f;
        }
        return Math.max(0.2f, mult);
    }

    public static boolean isDaylightStrained(ServerLevel level, ServerPlayer player) {
        return isDaytime(level) && level.canSeeSky(player.blockPosition());
    }

    public static boolean isNightEmpowered(ServerLevel level) {
        return !isDaytime(level);
    }

    public static float getDrain(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        return clampDrain(ObscurialValueCodec.parseFloat(data.getFlag(FLAG_DRAIN), MAX_DRAIN));
    }

    public static void setDrain(ServerPlayer player, float value) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        data.setFlag(FLAG_DRAIN, String.valueOf(clampDrain(value)));
    }

    public static float getMaxDrain() {
        return MAX_DRAIN;
    }

    public static float getLowDrainWarning() {
        return LOW_DRAIN_WARNING;
    }

    public static float getCharge(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        return clampDrain(ObscurialValueCodec.parseFloat(data.getFlag(FLAG_CHARGE), MAX_DRAIN));
    }

    public static void setCharge(ServerPlayer player, float value) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        data.setFlag(FLAG_CHARGE, String.valueOf(clampDrain(value)));
    }

    public static float getStress(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        return clampDrain(ObscurialValueCodec.parseFloat(data.getFlag(FLAG_STRESS), 0f));
    }

    public static void setStress(ServerPlayer player, float value) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        data.setFlag(FLAG_STRESS, String.valueOf(clampDrain(value)));
    }

    public static void addStress(ServerPlayer player, float amount) {
        setStress(player, getStress(player) + Math.max(0f, amount));
    }

    public static void reduceStress(ServerPlayer player, float amount) {
        setStress(player, getStress(player) - Math.max(0f, amount));
    }

    public static float computeDarkDrainPerTick(ServerPlayer player, ServerLevel level) {
        float cost = DRAIN_TICK_DARK_BASE;
        StressTier stressTier = getStressTier(getStress(player));
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
        StressTier stressTier = getStressTier(getStress(player));
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
        float stability = getDrain(player);
        float charge = getCharge(player);
        float stabilityPerTick = Math.max(0.0001f, computeDarkDrainPerTick(player, level));
        float chargePerTick = Math.max(0.0001f, computeDarkChargeCostPerTick(player, level));
        float limitingTicks = Math.min(stability / stabilityPerTick, charge / chargePerTick);
        return Math.max(0f, limitingTicks / 20.0f);
    }

    public static void consumeCastSpike(ServerPlayer player) {
        setDrain(player, getDrain(player) - DRAIN_TICK_CASTING_SPIKE);
    }

    public static void applyDarkFormStressSpike(ServerPlayer player) {
        addStress(player, STRESS_SPIKE_DARK_DAMAGE);
        setDrain(player, getDrain(player) - DARK_DESTABILIZE_CONTROL_SPIKE);
        setCharge(player, getCharge(player) - DARK_DESTABILIZE_PRESSURE_SPIKE);
    }

    public static void applyBlockedCastStressSpike(ServerPlayer player) {
        addStress(player, STRESS_SPIKE_BLOCKED_CAST);
    }

    public static void applyDamageStressSpike(ServerPlayer player) {
        addStress(player, STRESS_SPIKE_DAMAGE);
    }

    public static void applyBlockedCastPressureBacklash(ServerPlayer player) {
        setCharge(player, getCharge(player) - BLOCKED_CAST_PRESSURE_BACKLASH);
    }

    public static void applyHumanFailedCastStressSpike(ServerPlayer player) {
        addStress(player, HUMAN_FAILED_CAST_STRESS_SPIKE);
    }

    public static void applySurgeCosts(ServerPlayer player) {
        applyAbilityCosts(player, SURGE_CONTROL_COST, SURGE_PRESSURE_COST, SURGE_STRESS_COST);
    }

    public static void applyGraspCosts(ServerPlayer player) {
        applyAbilityCosts(player, GRASP_CONTROL_COST, GRASP_PRESSURE_COST, GRASP_STRESS_COST);
    }

    public static boolean isRageThresholdActive(ServerPlayer player) {
        return getLoreControlTier(getDrain(player)) == LoreControlTier.CATASTROPHIC;
    }

    public static void applyRageThresholdEffects(ServerPlayer player, ServerLevel level) {
        if (!isRageThresholdActive(player)) return;
        setDrain(player, getDrain(player) - RAGE_CONTROL_DRAIN_TICK);
        addStress(player, RAGE_STRESS_GAIN_TICK);
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

    public static float getStressVentRecovery() {
        return STRESS_VENT_RECOVERY;
    }

    public static long getStressVentCooldownTicks() {
        return STRESS_VENT_COOLDOWN_TICKS;
    }

    public static int getStressVentDrawbackTicks() {
        return STRESS_VENT_DRAWBACK_TICKS;
    }

    public static void setLockoutUntilTick(ServerPlayer player, long gameTick) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        data.setFlag(FLAG_LOCKOUT_UNTIL, String.valueOf(gameTick));
    }

    public static long getLockoutUntilTick(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        return ObscurialValueCodec.parseLong(data.getFlag(FLAG_LOCKOUT_UNTIL), 0L);
    }

    public static boolean isTransformLockedOut(ServerPlayer player, long gameTick) {
        return gameTick < getLockoutUntilTick(player);
    }

    public static long getDefaultLockoutTicks() {
        return LOCKOUT_TICKS;
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

    public static long getStressVentCooldownUntil(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        return ObscurialValueCodec.parseLong(data.getFlag(FLAG_VENT_COOLDOWN_UNTIL), 0L);
    }

    public static void setStressVentCooldownUntil(ServerPlayer player, long gameTick) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        data.setFlag(FLAG_VENT_COOLDOWN_UNTIL, String.valueOf(gameTick));
    }

    public static float getInstabilityFizzleChance(ServerPlayer player, ServerLevel level) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        if (!isObscurial(data) || !isDarkForm(data)) return 0f;

        float chance = INSTABILITY_FIZZLE_BASE;
        if (isDaylightStrained(level, player)) chance += INSTABILITY_FIZZLE_DAYLIGHT;
        if (player.getHealth() <= player.getMaxHealth() * 0.40f) chance += INSTABILITY_FIZZLE_LOW_HP;
        if (getDrain(player) <= LOW_DRAIN_WARNING) chance += INSTABILITY_FIZZLE_LOW_DRAIN;
        return Math.min(0.9f, chance);
    }

    public static float getStressAbilityPenaltyMultiplier(ServerPlayer player) {
        return switch (getStressTier(getStress(player))) {
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

    private static void applyAbilityCosts(ServerPlayer player, float drainCost, float pressureCost, float stressCost) {
        float stressPenalty = getStressAbilityPenaltyMultiplier(player);
        if (isDaylightStrained((ServerLevel) player.level(), player)) {
            stressPenalty += 0.15f;
        }
        setDrain(player, getDrain(player) - (drainCost * stressPenalty));
        setCharge(player, getCharge(player) - (pressureCost * stressPenalty));
        addStress(player, stressCost * stressPenalty);
    }

    private static float clampDrain(float value) {
        return ObscurialValueCodec.clampPercent(value, MAX_DRAIN);
    }
}
