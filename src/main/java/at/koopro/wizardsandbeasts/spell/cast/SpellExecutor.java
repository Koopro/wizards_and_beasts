package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;
import at.koopro.wizardsandbeasts.spell.lib.*;
import at.koopro.wizardsandbeasts.spell.proficiency.*;

import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.spell.cast.CastContext;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectEntry;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectContext;
import at.koopro.wizardsandbeasts.spell.effect.SpellEffectRunner;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellScalingProfile;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialCombatRules;
import at.koopro.wizardsandbeasts.wand.corruption.WandCorruptionSystem;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Default spell execution logic. Individual spells delegate here via {@link Spell#execute}.
 * Spells can override execute() entirely for custom behavior.
 */
public final class SpellExecutor {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SpellExecutor() {}

    public static void executeGeneric(Spell spell, ServerLevel level, ServerPlayer caster, ItemStack wandStack) {
        WandStats wandStats = WandStatsResolver.resolve(wandStack);
        CastContext ctx = CastContext.create(
                caster,
                wandStack,
                spell,
                spell instanceof JsonSpell jsonSpell ? jsonSpell.definition() : null,
                wandStats,
                spell.getProficiency(caster));
        executeGeneric(ctx, level);
    }

    public static void executeGeneric(CastContext ctx, ServerLevel level) {
        Spell spell = ctx.spell();
        ServerPlayer caster = ctx.caster();
        ItemStack wandStack = ctx.wandStack();
        WandStats wand = ctx.wandStats();
        if (ModuleManager.isEnabled(Module.WANDS) && wandStack.getItem() instanceof WandItem) {
            Identifier spellKey;
            try {
                spellKey = Identifier.parse(spell.getId());
            } catch (Exception ex) {
                LOGGER.warn("[WizardsAndBeasts] Malformed spell id '{}' — using sanitized fallback key. Fix the spell registration.",
                        spell.getId(), ex);
                spellKey = Identifier.fromNamespaceAndPath(at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID,
                        spell.getId().replace(":", "_").replace(".", "_"));
            }
            WandCorruptionSystem.onSpellCast(caster, wandStack, spellKey);
            float corruptIntegrity = WandCorruptionSystem.getEffectivePowerMultiplier(wandStack, spellKey, level.registryAccess());
            ctx.modifiers().multiplyDamage(corruptIntegrity, "wand_corruption_integrity");
            Optional<UUID> master = WandComponents.getMaster(wandStack);
            if (master.isPresent() && !master.get().equals(caster.getUUID())) {
                ctx.modifiers().multiplyDamage(WandComponents.getAllegianceScore(wandStack), "wand_foreign_master");
            }
        }
        SkillSystemAPI.applyDamageModifiers(ctx.modifiers(), caster, spell);
        SkillSystemAPI.applyCooldownModifiers(ctx.modifiers(), caster, spell);
        WandStatsResolver.applyToStack(ctx.modifiers(), wand, spell);
        ObscurialCombatRules.applyCastModifiers(ctx.modifiers(), caster);
        at.koopro.wizardsandbeasts.skill.vocation.VocationAbilityHooks.applyCastModifiers(ctx.modifiers(), caster, spell);
        at.koopro.wizardsandbeasts.entity.niffler.HappinessSpellPower.applyCastModifiers(ctx.modifiers(), caster);

        float corruption = caster.getData(ModAttachments.DARK_CORRUPTION.get());
        if (corruption >= 90.0f
                && spell.getCategory() != SpellCategory.DARK_ARTS
                && (spell.getCategory() == SpellCategory.DEFENSE || spell.getCategory() == SpellCategory.UTILITY)) {
            ctx.modifiers().multiplyDamage(0.7f, "dark_corruption_light_magic");
        }

        if (ctx.modifiers().finalMisfireChance() > 0.0f
                && level.random.nextFloat() < ctx.modifiers().finalMisfireChance()) {
            SpellCastTelemetry.recordCast(caster, true);
            level.playSound(null, caster.blockPosition(),
                    ModSounds.SPELL_FIZZLE.get(), SoundSource.PLAYERS, 0.5f, 1.05f + level.random.nextFloat() * 0.25f);
            caster.displayClientMessage(
                    Component.literal("\u00A77Your wand fizzles."), true);
            return;
        }
        SpellCastTelemetry.recordCast(caster, false);

        // Dispatch via the spell's overridable cast hook so subclass overrides (entity spawns, etc.)
        // run on a normal wand cast with the fully-enriched context. Default hook -> dispatchGeneric.
        spell.executeCast(ctx, level);
    }

    /**
     * Generic {@code castType} dispatch. Public so {@link Spell#executeCast} (and overrides via
     * {@code super.executeCast}) can invoke the default behavior. Expects {@code ctx} to already
     * carry the finalized modifier pipeline output.
     */
    public static void dispatchGeneric(CastContext ctx, ServerLevel level) {
        Spell spell = ctx.spell();
        ServerPlayer caster = ctx.caster();
        ItemStack wandStack = ctx.wandStack();
        WandStats wand = ctx.wandStats();
        // TODO(skill_tree): keep damage stacking as base * proficiency * skill-tree multipliers.
        float damageMultiplier = ctx.modifiers().finalDamage() * ctx.scalingProfile().damageMult();
        SpellProperties props = spell.getProperties();
        if (props == null) return;

        CastType castType = props.getCastType();
        if (castType != CastType.BEAM_LETHAL && castType != CastType.BEAM_CHANNEL) {
            spell.playSound(level, caster);
        }
        SpellScalingProfile scalingProfile = ctx.scalingProfile();
        spell.applySelfEffects(caster, scalingProfile.durationMult());

        switch (castType) {
            case PROJECTILE -> spell.spawnProjectile(level, caster, scalingProfile);
            case SELF -> {
                boolean successful = false;
                if (props.repairsItem()) {
                    boolean repaired = SpellCastHandlers.handleRepair(caster, wandStack, props.getRepairAmount());
                    if (!repaired) {
                        Vec3 start = caster.getEyePosition();
                        float range = 5.0f * wand.rangeFor(spell);
                        Vec3 end = start.add(caster.getLookAngle().scale(range));
                        BlockHitResult blockHit = level.clip(new ClipContext(
                                start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));
                        if (blockHit.getType() == HitResult.Type.BLOCK) {
                            repaired = SpellHelper.tryReparoRepairBlock(level, blockHit.getBlockPos(), spell);
                        }
                    }
                    if (repaired) {
                        Vec3 p = caster.getEyePosition();
                        SpellHelper.spawnBurst(level, spell, p, 12, 0.2);
                        successful = true;
                    }
                }
                successful |= handleSelfUtilitySpell(level, caster, spell);
                if (!props.getSelfEffects().isEmpty()) {
                    successful = true;
                }
                // Data-driven effect components (Step 3): run the spell's authored effect list with the
                // caster as subject, at the same SELF cast-time point legacy self-effects run. No-op for
                // spells without an effects list (all Java spells today).
                List<SpellEffectEntry> selfComponents = SpellEffectRunner.effectsOf(spell);
                if (!selfComponents.isEmpty()) {
                    SpellEffectRunner.run(selfComponents, SpellEffectContext.ofSelf(caster, level)
                            .withScaling(ctx.modifiers().finalDamage() * scalingProfile.damageMult(),
                                    scalingProfile.durationMult(), scalingProfile.controlMult()));
                    successful = true;
                }
                if (successful) {
                    SpellProficiencyTracker.recordSuccessfulHit(caster, spell.getId());
                }
            }
            case CONE -> {
                if (SpellCastHandlers.handleCone(level, caster, spell, props, damageMultiplier, wand, scalingProfile)) {
                    SpellProficiencyTracker.recordSuccessfulHit(caster, spell.getId());
                }
            }
            case TARGETED -> {
                if (SpellCastHandlers.handleTargeted(level, caster, spell, props, damageMultiplier, wand, scalingProfile)) {
                    SpellProficiencyTracker.recordSuccessfulHit(caster, spell.getId());
                }
            }
            case BEAM_LETHAL, BEAM_CHANNEL -> {
                /* Damage and targeting run each tick in {@link at.koopro.wizardsandbeasts.spell.WandBeamChannelLogic}. */
            }
        }
    }

    private static final SelfUtilityRule[] SELF_UTILITY_RULES = new SelfUtilityRule[] {
            // Step 3/4 migration notes:
            //  - lumos (S3) & nox (S4) are JSON spells; their light/dispel ride effect components, and
            //    the lumos<->nox learn + loadout-swap toggle rides a swap_active_spell component.
            //  - reparo (S4): residual tail. SpellDefinition has no repairsItem field and the `repair`
            //    component can't match full-repair / wand-aware selection / ElderWand guard, so reparo's
            //    behavior stays here (block-repair range no longer wand-scaled — logged delta).
            //  - arresto_momentum (S4): residual tail for the AoE stabilize (velocity dampen has no
            //    component); its self slow-fall rides an apply_effect component in JSON.
            //  - episkey/frigora (S4): handlers trimmed to the residual bit only (regen / block-interaction);
            //    heal+cleanse / clear-fire+resistances ride components in JSON.
            new SelfUtilityRule("reparo", (level, caster, spell) ->
                    SpellCastHandlers.handleReparoSelf(level, caster, spell)),
            new SelfUtilityRule("protego", (level, caster, spell) -> {
                SpellHelper.applyProtegoCastPulse(level, caster, spell);
                return true;
            }),
            new SelfUtilityRule("arresto_momentum", (level, caster, spell) -> {
                SpellHelper.applyArrestoAreaStabilize(level, caster, spell);
                return true;
            }),
            new SelfUtilityRule("episkey", (level, caster, spell) -> SpellCastHandlers.handleEpiskeySelf(caster, spell)),
            new SelfUtilityRule("frigora", (level, caster, spell) -> SpellCastHandlers.handleFrigoraSelf(level, caster, spell)),
            new SelfUtilityRule("capacious_extremis", (level, caster, spell) -> SpellCastHandlers.handlePocketIngress(caster)),
            new SelfUtilityRule("claustra_reverto", (level, caster, spell) -> SpellCastHandlers.handlePocketEgress(caster)),
            new SelfUtilityRule("riddikulus", (level, caster, spell) -> {
                SpellHelper.spawnBurst(level, spell, caster.getEyePosition(), 18, 0.32);
                return true;
            })
    };

    private static boolean handleSelfUtilitySpell(ServerLevel level, ServerPlayer caster, Spell spell) {
        String id = spell.getId();
        for (SelfUtilityRule rule : SELF_UTILITY_RULES) {
            if (SpellIds.matches(id, rule.spellId())) {
                return rule.handler().apply(level, caster, spell);
            }
        }
        return false;
    }

    private record SelfUtilityRule(String spellId, SelfUtilityHandler handler) {}

    @FunctionalInterface
    private interface SelfUtilityHandler {
        boolean apply(ServerLevel level, ServerPlayer caster, Spell spell);
    }
}
