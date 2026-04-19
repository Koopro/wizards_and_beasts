package at.koopro.neo.spell;

import at.koopro.neo.item.WandItem;
import at.koopro.neo.skill.SkillSystemAPI;
import at.koopro.neo.spell.wand.WandStats;
import at.koopro.neo.spell.wand.WandStatsResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Default spell execution logic. Individual spells delegate here via {@link Spell#execute}.
 * Spells can override execute() entirely for custom behavior.
 */
public final class SpellExecutor {

    private SpellExecutor() {}

    public static void executeGeneric(Spell spell, ServerLevel level, ServerPlayer caster, ItemStack wandStack) {
        SpellProperties props = spell.getProperties();
        if (props == null) return;

        WandStats wand = WandStatsResolver.resolve(wandStack);
        if (wand.fizzleChance() > 0.0f && level.random.nextFloat() < wand.fizzleChance()) {
            level.playSound(null, caster.blockPosition(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.6f);
            caster.displayClientMessage(
                    Component.literal("\u00A77Your wand fizzles."), true);
            return;
        }

        float damageMultiplier = SkillSystemAPI.getDamageMultiplier(caster, spell)
                * wand.damageFor(spell);

        spell.playSound(level, caster);
        spell.applySelfEffects(caster);

        switch (props.getCastType()) {
            case PROJECTILE -> spell.spawnProjectile(level, caster);
            case SELF -> {
                if (props.repairsItem()) {
                    handleRepair(caster, wandStack, props.getRepairAmount());
                }
            }
            case CONE -> handleCone(level, caster, spell, props, damageMultiplier, wand);
            case TARGETED -> handleTargeted(level, caster, spell, props, damageMultiplier, wand);
        }
    }

    static void handleCone(ServerLevel level, ServerPlayer caster,
                           Spell spell, SpellProperties props,
                           float damageMultiplier, WandStats wand) {
        Vec3 look = caster.getLookAngle();
        boolean pullsItems = props.getPullStrength() != 0;
        float effectiveRange = props.getRange() * wand.rangeFor(spell);
        List<Entity> entities = SpellHelper.findEntitiesInCone(level, caster, effectiveRange, pullsItems);

        for (Entity entity : entities) {
            if (props.getPullStrength() != 0) {
                Vec3 diff = caster.position().subtract(entity.position()).normalize()
                        .scale(props.getPullStrength());
                entity.push(diff.x, 0.2, diff.z);
                entity.hurtMarked = true;
            }

            if (entity instanceof LivingEntity living) {
                float damage = spell.getBaseDamage() * damageMultiplier;

                if (props.getUndeadBonusDamage() > 0 && living.isInvertedHealAndHarm()) {
                    damage += props.getUndeadBonusDamage();
                }

                if (damage > 0) {
                    living.hurt(level.damageSources().magic(), damage);
                }

                spell.applyTargetEffects(living);

                if (props.ignites()) {
                    SpellHelper.ignite(entity, props.getIgniteDurationSeconds());
                }

                if (props.getKnockbackStrength() != 0) {
                    SpellHelper.applyKnockback(entity, look, props.getKnockbackStrength());
                }
            }
        }
    }

    static void handleTargeted(ServerLevel level, ServerPlayer caster,
                               Spell spell, SpellProperties props,
                               float damageMultiplier, WandStats wand) {
        Vec3 start = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        float effectiveRange = props.getRange() * wand.rangeFor(spell);
        Vec3 end = start.add(look.scale(effectiveRange));

        LivingEntity target = SpellHelper.findTargetedEntity(level, caster, effectiveRange);

        if (target != null) {
            float damage = spell.getBaseDamage() * damageMultiplier;

            if (props.getUndeadBonusDamage() > 0 && target.isInvertedHealAndHarm()) {
                damage += props.getUndeadBonusDamage();
            }

            if (damage > 0) {
                target.hurt(level.damageSources().magic(), damage);
            }

            spell.applyTargetEffects(target);

            if (props.levitatesTarget()) {
                target.addEffect(new MobEffectInstance(MobEffects.LEVITATION,
                        props.getLevitateDurationTicks(), 0, false, true, true));
            }

            if (props.getPullStrength() != 0) {
                Vec3 diff = caster.position().subtract(target.position()).normalize()
                        .scale(props.getPullStrength());
                target.push(diff.x, 0.3, diff.z);
                target.hurtMarked = true;
            }

            if (props.ignites()) {
                SpellHelper.ignite(target, props.getIgniteDurationSeconds());
            }

            if (props.getKnockbackStrength() != 0) {
                SpellHelper.applyKnockback(target, look, props.getKnockbackStrength());
            }

            if (props.controlsMob() && target instanceof Mob mob) {
                mob.setTarget(null);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                        props.getControlDurationTicks(), 4, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                        props.getControlDurationTicks(), 2, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                        props.getControlDurationTicks(), 0, false, true, true));
            }

            if (props.explodes()) {
                SpellHelper.createExplosion(level, caster,
                        target.getBoundingBox().getCenter(), props.getExplosionPower(),
                        props.explosionBreaksBlocks());
            }

            SpellHelper.spawnBeam(level, start, target.getBoundingBox().getCenter(), spell.getColor());
        }

        // Block interactions (Alohomora, Bombarda on blocks)
        if (props.opensBlocks() || (props.explodes() && target == null)) {
            BlockHitResult blockHit = level.clip(new ClipContext(start, end,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, caster));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = blockHit.getBlockPos();

                if (props.opensBlocks()) {
                    handleAlohomora(level, pos);
                }

                if (props.explodes()) {
                    SpellHelper.createExplosion(level, caster,
                            Vec3.atCenterOf(pos), props.getExplosionPower(),
                            props.explosionBreaksBlocks());
                }

                SpellHelper.spawnBeam(level, start, blockHit.getLocation(), spell.getColor());
            }
        }
    }

    static void handleRepair(ServerPlayer caster, ItemStack wandStack, int repairAmount) {
        ItemStack toRepair = (caster.getMainHandItem().getItem() instanceof WandItem)
                ? caster.getOffhandItem() : caster.getMainHandItem();

        if (toRepair.isDamageableItem() && toRepair.isDamaged()) {
            toRepair.setDamageValue(Math.max(0, toRepair.getDamageValue() - repairAmount));
        }
    }

    static void handleAlohomora(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof DoorBlock door) {
            boolean open = state.getValue(DoorBlock.OPEN);
            door.setOpen(null, level, state, pos, !open);
        } else if (state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock) {
            if (state.hasProperty(BlockStateProperties.OPEN)) {
                level.setBlockAndUpdate(pos, state.cycle(BlockStateProperties.OPEN));
            }
        }
    }
}
