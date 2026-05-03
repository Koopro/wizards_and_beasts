package at.koopro.wizardsandbeasts.spell;

import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.cast.CastContext;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.type.ObscurialRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Default spell execution logic. Individual spells delegate here via {@link Spell#execute}.
 * Spells can override execute() entirely for custom behavior.
 */
public final class SpellExecutor {

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
        SkillSystemAPI.applyDamageModifiers(ctx.modifiers(), caster, spell);
        SkillSystemAPI.applyCooldownModifiers(ctx.modifiers(), caster, spell);
        WandStatsResolver.applyToStack(ctx.modifiers(), wand, spell);
        ObscurialRules.applyCastModifiers(ctx.modifiers(), caster);

        if (ctx.modifiers().finalMisfireChance() > 0.0f
                && level.random.nextFloat() < ctx.modifiers().finalMisfireChance()) {
            level.playSound(null, caster.blockPosition(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.6f);
            caster.displayClientMessage(
                    Component.literal("\u00A77Your wand fizzles."), true);
            return;
        }

        executeGeneric(spell, level, caster, wandStack, wand, ctx.modifiers().finalDamage());
    }

    private static void executeGeneric(Spell spell,
                                       ServerLevel level,
                                       ServerPlayer caster,
                                       ItemStack wandStack,
                                       WandStats wand,
                                       float damageMultiplier) {
        SpellProperties props = spell.getProperties();
        if (props == null) return;

        CastType castType = props.getCastType();
        if (castType != CastType.BEAM_LETHAL && castType != CastType.BEAM_CHANNEL) {
            spell.playSound(level, caster);
        }
        spell.applySelfEffects(caster);

        switch (castType) {
            case PROJECTILE -> spell.spawnProjectile(level, caster);
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
                        SpellHelper.spawnBurst(level, p, spell.getColor(), 12, 0.2);
                        successful = true;
                    }
                }
                successful |= handleSelfUtilitySpell(level, caster, spell);
                if (!props.getSelfEffects().isEmpty()) {
                    successful = true;
                }
                if (successful) {
                    SpellProficiencyTracker.recordSuccessfulHit(caster, spell.getId());
                }
            }
            case CONE -> {
                if (SpellCastHandlers.handleCone(level, caster, spell, props, damageMultiplier, wand)) {
                    SpellProficiencyTracker.recordSuccessfulHit(caster, spell.getId());
                }
            }
            case TARGETED -> {
                if (SpellCastHandlers.handleTargeted(level, caster, spell, props, damageMultiplier, wand)) {
                    SpellProficiencyTracker.recordSuccessfulHit(caster, spell.getId());
                }
            }
            case BEAM_LETHAL, BEAM_CHANNEL -> {
                /* Damage and targeting run each tick in {@link at.koopro.wizardsandbeasts.spell.WandBeamChannelLogic}. */
            }
        }
    }

    private static final SelfUtilityRule[] SELF_UTILITY_RULES = new SelfUtilityRule[] {
            new SelfUtilityRule("lumos", (level, caster, spell) -> {
                SpellHelper.spawnBurst(level, caster.getEyePosition(), spell.getColor(), 16, 0.28);
                return true;
            }),
            new SelfUtilityRule("nox", (level, caster, spell) -> {
                caster.removeEffect(MobEffects.GLOWING);
                caster.removeEffect(MobEffects.NIGHT_VISION);
                SpellHelper.spawnBurst(level, caster.getEyePosition(), spell.getColor(), 12, 0.22);
                return true;
            }),
            new SelfUtilityRule("protego", (level, caster, spell) -> {
                SpellHelper.applyProtegoCastPulse(level, caster, spell);
                return true;
            }),
            new SelfUtilityRule("arresto_momentum", (level, caster, spell) -> {
                SpellHelper.applyArrestoAreaStabilize(level, caster, spell);
                return true;
            }),
            new SelfUtilityRule("episkey", (level, caster, spell) -> SpellCastHandlers.handleEpiskeySelf(caster, spell)),
            new SelfUtilityRule("frigora", (level, caster, spell) -> SpellCastHandlers.handleFrigoraSelf(level, caster, spell))
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
