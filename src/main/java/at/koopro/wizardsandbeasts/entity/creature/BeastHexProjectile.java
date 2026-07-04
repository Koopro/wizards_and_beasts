package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.creature.ability.AbilitySupport;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import javax.annotation.Nullable;

/**
 * A real, travelling ranged "hex" bolt thrown by {@link at.koopro.wizardsandbeasts.creature.ability.RangedHex}
 * beasts — replacing the earlier hitscan. Rendered as a flung item (vanilla {@code ThrownItemRenderer}, the
 * {@code WizardingThrownEntity} pattern); its payload (damage, optional status effect) is server-only state
 * set at spawn. On striking a living target it applies the payload and bursts; on a block it just bursts.
 */
public class BeastHexProjectile extends ThrowableItemProjectile {

    private float damage = 3.0f;
    @Nullable
    private AbilitySupport.EffectSpec effect;
    private AbilitySupport.Particle trail = AbilitySupport.Particle.WITCH;

    public BeastHexProjectile(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public BeastHexProjectile(Level level, LivingEntity shooter, float damage,
                              @Nullable AbilitySupport.EffectSpec effect, AbilitySupport.Particle trail) {
        super(ModEntities.BEAST_HEX_PROJECTILE.get(), shooter, level, new ItemStack(Items.MAGMA_CREAM));
        this.damage = damage;
        this.effect = effect;
        this.trail = trail;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.MAGMA_CREAM;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            AbilitySupport.emitAt(server, trail, getX(), getY(), getZ(), 1, 0.0, 0.0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide()) {
            return;
        }
        Entity hit = result.getEntity();
        Entity owner = getOwner();
        if (owner != null && hit == owner) {
            return;
        }
        if (hit instanceof LivingEntity victim && level() instanceof ServerLevel server) {
            victim.hurtServer(server, server.damageSources().indirectMagic(this, owner), damage);
            if (effect != null) {
                effect.applyTo(victim);
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide()) {
            if (level() instanceof ServerLevel server) {
                AbilitySupport.emitAt(server, trail, getX(), getY(), getZ(), 12, 0.2, 0.05);
            }
            discard();
        }
    }
}
