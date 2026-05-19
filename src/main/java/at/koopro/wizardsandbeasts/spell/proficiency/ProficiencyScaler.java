package at.koopro.wizardsandbeasts.spell.proficiency;

import at.koopro.wizardsandbeasts.spell.core.*;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * AUDIT FINDINGS (captured before implementation):
 *
 * 1) Player spell attachment and proficiency source:
 * - File: data/PlayerSpellData.java
 * - Existing progression was tracked via successfulHits map (spellId -> int) and Proficiency enum tiers.
 * - No existing float map for per-spell proficiency in [0.0, 1.0] was present; this class now reads
 *   PlayerSpellData#getSpellProficiency(spellId) introduced for cast-scaling.
 *
 * 2) Existing proficiency increment point:
 * - File: spell/SpellProficiencyTracker.java
 * - recordSuccessfulHit(...) was called after successful cast effects from SpellExecutor / SpellProjectileEntity /
 *   WandBeamChannelLogic and incremented successfulHits by +1 with no diminishing returns.
 *
 * 3) Cast execution and cooldown application points:
 * - File: spell/cast/SpellCastService.java
 * - Cast execution call: SpellExecutor.executeGeneric(castContext, serverLevel).
 * - Cooldown stamping after execution: data.setCooldown(spellId, expiryTick) using
 *   spell.getBaseCooldownTicks() * castContext.modifiers().finalCooldown().
 *
 * 4) SpellScalingProfile pre-existence:
 * - No existing SpellScalingProfile record/class existed in the codebase.
 *
 * 5) MobEffectInstance duration source:
 * - Durations were mixed:
 *   - hardcoded in Java spell handlers (e.g. SpellCastHandlers and spell classes),
 *   - and JSON-defined target/self effects in SpellDefinition.MobEffectDef used by JsonSpell.
 *
 * 6) SpellDefinition base fields and projectile controls:
 * - File: spell/def/SpellDefinition.java
 * - Existing fields included baseDamage and cooldownTicks, but no dedicated baseCooldownTicks alias,
 *   projectileSpeed, projectileSpread, baseEffectDurationTicks, baseKnockback, or baseAoeRadius.
 */
public final class ProficiencyScaler {
    private ProficiencyScaler() {}

    public static SpellScalingProfile computeProfile(float proficiency) {
        float p = clamp01(proficiency);

        // Damage: smoothstep S-curve.
        float damageT = p * p * (3.0f - 2.0f * p);
        float damageMult = 0.50f + damageT;

        // Cooldown: power curve with stronger early rewards.
        float cooldownT = (float) Math.pow(p, 0.7f);
        float cooldownMult = 1.40f - (cooldownT * 0.70f);

        // Duration: linear.
        float durationMult = 0.60f + (p * 0.80f);

        // Control: linear floor->ceiling.
        float controlMult = 0.70f + (p * 0.60f);

        // Accuracy multiplier (lower spread with mastery).
        float accuracyMult = 1.00f - (p * 0.20f);

        return new SpellScalingProfile(damageMult, cooldownMult, durationMult, controlMult, accuracyMult);
    }

    public static SpellScalingProfile getProfileForPlayer(ServerPlayer player, Identifier spellId) {
        if (!ModuleManager.isEnabled(Module.PROFICIENCY)) {
            return SpellScalingProfile.DEFAULT;
        }
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        if (data == null) {
            return computeProfile(0.0f);
        }
        float proficiency = data.getSpellProficiency(spellId.toString());
        return computeProfile(proficiency);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
