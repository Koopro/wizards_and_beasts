package at.koopro.wizardsandbeasts.entity.spell;

import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;

public class WizardingThrownEntity extends ThrowableItemProjectile {

    public WizardingThrownEntity(EntityType<? extends ThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public WizardingThrownEntity(Level level, LivingEntity shooter, ItemStack stack) {
        super(ModEntities.WIZARDING_THROWN.get(), shooter, level, stack);
    }

    private WizardingThrownKind kindFromStack() {
        ItemStack stack = getItem();
        Item item = stack.getItem();
        if (item == ConsumableItemRegistry.ERUMPENT_HORN.get()) {
            return WizardingThrownKind.ERUMPENT_HORN;
        }
        if (item == TrinketItemRegistry.DECOY_DETONATOR.get()) {
            return WizardingThrownKind.DECOY_DETONATOR;
        }
        return WizardingThrownKind.PERUVIAN_DARKNESS_POWDER;
    }

    @Override
    protected Item getDefaultItem() {
        return ConsumableItemRegistry.ERUMPENT_HORN.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide()) {
            return;
        }
        Vec3 p = result.getLocation();
        switch (kindFromStack()) {
            case ERUMPENT_HORN -> level().explode(this, p.x, p.y, p.z, 2.0f, Level.ExplosionInteraction.TNT);
            case DECOY_DETONATOR -> {
                level().playSound(null, p.x, p.y, p.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2.0f, 0.6f + random.nextFloat() * 0.4f);
                level().playSound(null, p.x, p.y, p.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 2.0f, 0.6f + random.nextFloat() * 0.4f);
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.FIREWORK, p.x, p.y, p.z, 24, 0.4, 0.4, 0.4, 0.12);
                }
            }
            case PERUVIAN_DARKNESS_POWDER -> {
                AABB box = new AABB(BlockPos.containing(p)).inflate(4.0);
                for (Entity e : level().getEntities(this, box, en -> en instanceof LivingEntity)) {
                    ((LivingEntity) e).addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0));
                }
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 40, 1.5, 0.5, 1.5, 0.02);
                }
            }
        }
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && kindFromStack() == WizardingThrownKind.DECOY_DETONATOR && tickCount % 4 == 0) {
            level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        }
    }
}
