package at.koopro.wizardsandbeasts.spell.impl;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import at.koopro.wizardsandbeasts.spell.cast.CastContext;
import at.koopro.wizardsandbeasts.spell.cast.WandCastTiming;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.spell.cast.SpellPower;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellProficiencyTracker;
import at.koopro.wizardsandbeasts.skill.GameplayStat;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoCharge;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoFeedback;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoRules;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoTier;
import at.koopro.wizardsandbeasts.spell.protego.ProtegoWardManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * The Shield Charm. One spell, four shapes — see {@link ProtegoTier}.
 *
 * <p>The wand hold chooses the shape ({@link ProtegoCharge}), sneaking on release plants the dome
 * tiers, and the shape decides everything else: how much the ward can absorb, how long it stands,
 * who it covers, what a Dark bolt costs it, what recovering from a break costs the caster.
 *
 * <p>Totalum, Maxima and Horribilis are <em>not</em> separate spells. They are ranks of this one,
 * named at the charge-up so a player learns the ladder by casting it.
 */
public class Protego extends Spell {

    public Protego() {
        super("protego", "Protego", SpellCategory.DEFENSE, 100, 0.0f, 0xFF4488FF);
    }

    @Override
    protected SpellProperties buildProperties() {
        return SpellProperties.self()
                .sound(net.minecraft.sounds.SoundEvents.SHIELD_BLOCK.value(), 1.0f, 1.2f)
                .build();
    }

    @Override
    public void executeCast(CastContext ctx, ServerLevel level) {
        ServerPlayer caster = ctx.caster();
        int heldTicks = WandCastTiming.consumeLastHoldTicks(caster);
        ProtegoTier tier = ProtegoCharge.resolve(caster, heldTicks).tier();
        boolean planted = ProtegoRules.canPlant(tier, caster.isShiftKeyDown());

        float proficiency = getProficiencyScalar(caster);
        // Shield control, from the Defence line of the Spell Mastery web: what the caster has trained is how
        // much the ward can swallow and how long they can hold it up, not how hard they hit.
        float integrity = ProtegoRules.integrity(tier, proficiency, castPower(ctx), planted)
                * (1.0f + Math.max(0.0f, SkillSystemAPI.getGameplayBonus(caster, GameplayStat.WARD_INTEGRITY)));
        int lifetime = Math.round(ProtegoRules.lifetimeTicks(tier, proficiency, planted)
                * (1.0f + Math.max(0.0f, SkillSystemAPI.getGameplayBonus(caster, GameplayStat.WARD_LIFETIME))));

        // The shape decides the recovery. Applied to the cast's cooldown channel rather than to a
        // timer of our own, because SpellCastService reads that channel after executeCast returns —
        // so a quick parry really is quicker to get back, and a Horribilis really does leave the
        // caster without a shield for a while.
        ctx.modifiers().multiplyCooldown(tier.cooldownFactor(), "protego_tier");
        if (tier.raiseExhaustion() > 0.0f) {
            caster.causeFoodExhaustion(tier.raiseExhaustion());
        }

        // Replace first: collapsing the old shield releases the effect and the registry entry, and
        // both are re-taken below. (The collapse only releases what still points at that shield, so
        // this ordering is a courtesy now rather than the load-bearing thing it used to be.)
        ProtegoWardManager.replaceExisting(caster);

        ProtegoShieldEntity shield = ProtegoShieldEntity.raise(level, caster, tier, planted, integrity, lifetime);
        level.addFreshEntity(shield);
        ProtegoWardManager.register(caster.getUUID(), shield.getId());
        caster.addEffect(new MobEffectInstance(ModEffects.PROTEGO_SHIELD, lifetime, tier.index(),
                false, false, true));
        ProtegoFeedback.raise(level, caster, shield);

        // Raising a ward is the practice that improves it. Protego never recorded a hit — it
        // overrides executeCast and so skipped the SELF branch that does it for every other
        // self-cast — which left its proficiency pinned at NOVICE forever: no tier above Totalum
        // was reachable, and Expecto Patronum (which requires Protego at PROFICIENT) was unlearnable.
        SpellProficiencyTracker.recordSuccessfulHit(caster, getId());
    }

    /**
     * The cast's power, with the proficiency channel left out.
     *
     * <p>{@code finalDamage()} composes situational × proficiency × skill; integrity applies
     * proficiency itself, in its own curve, so taking the full product here would count practice
     * twice — the double-multiplication this codebase has already had to unpick once.
     */
    private static float castPower(CastContext ctx) {
        SpellPower.Breakdown damage = ctx.modifiers().damageBreakdown();
        return damage.situational() * damage.skill();
    }

    @Override
    protected SpellRequirement buildRequirement() {
        return SpellRequirement.knows("expelliarmus");
    }

    @Override
    public int getBaseEffectDurationTicks() {
        return 200;
    }

    @Override
    public float getProjectileSpeed() {
        return 0.0f;
    }
}
