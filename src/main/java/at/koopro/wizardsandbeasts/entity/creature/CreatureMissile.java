package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.creature.ability.AbilitySupport;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Something a creature throws or spits that is a thing and not a spell: a giant's rock, a gob of venom.
 *
 * <p>Two entity types share this class, {@code thrown_boulder} and {@code creature_spit}. Which one it is decides
 * weight, look and what happens where it lands, and is read from the type, so server and client agree without a
 * synced field. Damage and the optional status effect are server state set when it is thrown. Neither kind breaks
 * or places a block.
 */
public class CreatureMissile extends ThrowableItemProjectile {

    private float damage = 3.0f;
    private AbilitySupport.@Nullable EffectSpec effect;

    public CreatureMissile(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    private CreatureMissile(EntityType<? extends ThrowableItemProjectile> type, Level level, LivingEntity thrower,
                            ItemStack shown, float damage, AbilitySupport.@Nullable EffectSpec effect) {
        super(type, thrower, level, shown);
        this.damage = damage;
        this.effect = effect;
    }

    public static CreatureMissile boulder(Level level, LivingEntity thrower, float damage,
                                          AbilitySupport.@Nullable EffectSpec effect) {
        return new CreatureMissile(ModEntities.THROWN_BOULDER.get(), level, thrower,
                new ItemStack(Items.COBBLESTONE), damage, effect);
    }

    public static CreatureMissile spit(Level level, LivingEntity spitter, float damage,
                                       AbilitySupport.@Nullable EffectSpec effect) {
        return new CreatureMissile(ModEntities.CREATURE_SPIT.get(), level, spitter,
                new ItemStack(Items.SLIME_BALL), damage, effect);
    }

    public boolean isBoulder() {
        return getType() == ModEntities.THROWN_BOULDER.get();
    }

    @Override
    protected Item getDefaultItem() {
        return isBoulder() ? Items.COBBLESTONE : Items.SLIME_BALL;
    }

    @Override
    protected double getDefaultGravity() {
        return isBoulder() ? 0.05 : 0.03;
    }

    @Override
    public void tick() {
        super.tick();
        if (!isBoulder() && level() instanceof ServerLevel server && tickCount % 2 == 0) {
            server.sendParticles(ParticleTypes.SPIT, getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Entity hit = result.getEntity();
        LivingEntity thrower = getOwner() instanceof LivingEntity living ? living : null;
        if (hit == thrower || !(hit instanceof LivingEntity victim)) {
            return;
        }
        if (victim.hurtServer(server, server.damageSources().mobProjectile(this, thrower), damage)) {
            if (effect != null) {
                effect.applyTo(victim);
            }
            if (isBoulder()) {
                victim.knockback(1.0, getX() - victim.getX(), getZ() - victim.getZ());
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (isBoulder()) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()),
                    getX(), getY(), getZ(), 24, 0.3, 0.3, 0.3, 0.1);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.4f, 0.6f);
        } else {
            server.sendParticles(ParticleTypes.SPIT, getX(), getY(), getZ(), 8, 0.15, 0.15, 0.15, 0.05);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_BLOCK_HIT, SoundSource.HOSTILE, 0.8f, 1.4f);
        }
        discard();
    }
}
