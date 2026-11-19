package at.koopro.wizardsandbeasts.item.projectile;

import at.koopro.wizardsandbeasts.entity.spell.WizardingThrownKind;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Where a thrown Weasleys' Wizard Wheezes product lands, and what it does there.
 *
 * <ul>
 *   <li><b>Dungbomb</b> — a stink cloud: everyone within {@link #STINK_RADIUS} blocks retches.</li>
 *   <li><b>Wildfire Whiz-Bang</b> — three rockets of the Weasley colours burst where it lands (the
 *       O.W.L. exam scene, Order of the Phoenix ch. 29).</li>
 *   <li><b>Portable Swamp</b> — the ground round the landing turns to bog. Only grass, dirt and similar
 *       ground, only where the thrower may build, and only with mob griefing on, so a corridor can be
 *       swamped but a base cannot be dissolved.</li>
 * </ul>
 */
@NullMarked
public final class WheezesThrown {

    public static final double STINK_RADIUS = 4.0;
    public static final int SWAMP_RADIUS = 2;

    private WheezesThrown() {}

    public static void land(ServerLevel level, WizardingThrownKind kind, Vec3 p, @Nullable ServerPlayer thrower) {
        switch (kind) {
            case DUNGBOMB -> dungbomb(level, p);
            case WHIZ_BANG -> whizBang(level, p);
            case PORTABLE_SWAMP -> swamp(level, p, thrower);
            default -> { }
        }
    }

    private static void dungbomb(ServerLevel level, Vec3 p) {
        AABB box = new AABB(p, p).inflate(STINK_RADIUS);
        java.util.Set<LivingEntity> near = new java.util.LinkedHashSet<>(level.getEntitiesOfClass(LivingEntity.class, box));
        near.addAll(level.getPlayers(pl -> box.contains(pl.position())));
        for (LivingEntity e : near) {
            if (e.position().distanceTo(p) <= STINK_RADIUS) {
                e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0));
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 0));
            }
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.3, p.z, 30, 1.2, 0.4, 1.2, 0.01);
        level.sendParticles(ParticleTypes.ITEM_SLIME, p.x, p.y + 0.3, p.z, 20, 1.0, 0.3, 1.0, 0.05);
        level.sendParticles(ParticleTypes.SNEEZE, p.x, p.y + 0.5, p.z, 30, 1.5, 0.6, 1.5, 0.02);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2f, 0.6f);
    }

    private static void whizBang(ServerLevel level, Vec3 p) {
        // Weasley orange and red, the shop-front purple, and a gold for the sparks.
        IntList colours = IntList.of(0xE8761E, 0xC0282D, 0x8A3FB8);
        FireworkExplosion.Shape[] shapes = {
                FireworkExplosion.Shape.LARGE_BALL, FireworkExplosion.Shape.STAR, FireworkExplosion.Shape.BURST};
        for (int i = 0; i < 3; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(
                    shapes[i], colours, IntList.of(0xF5C542), true, true))));
            FireworkRocketEntity entity = new FireworkRocketEntity(level, p.x, p.y + 0.2, p.z, rocket);
            level.addFreshEntity(entity);
        }
        level.playSound(null, p.x, p.y, p.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.5f, 1.0f);
    }

    private static void swamp(ServerLevel level, Vec3 p, @Nullable ServerPlayer thrower) {
        level.playSound(null, p.x, p.y, p.z, SoundEvents.FROG_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.8f);
        level.sendParticles(ParticleTypes.ITEM_SLIME, p.x, p.y + 0.2, p.z, 30, 1.5, 0.2, 1.5, 0.05);
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
            return;
        }
        BlockPos centre = BlockPos.containing(p);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-SWAMP_RADIUS, -2, -SWAMP_RADIUS),
                centre.offset(SWAMP_RADIUS, 1, SWAMP_RADIUS))) {
            if (pos.distManhattan(new BlockPos(centre.getX(), pos.getY(), centre.getZ())) > SWAMP_RADIUS + 1) {
                continue;
            }
            if (!level.getBlockState(pos).is(BlockTags.DIRT) || !level.getBlockState(pos.above()).isAir()) {
                continue;
            }
            if (thrower != null && !(thrower.mayBuild() && level.mayInteract(thrower, pos))) {
                continue;
            }
            level.setBlockAndUpdate(pos.immutable(), Blocks.MUD.defaultBlockState());
        }
    }
}
