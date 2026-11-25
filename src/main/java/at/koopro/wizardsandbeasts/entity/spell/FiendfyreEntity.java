package at.koopro.wizardsandbeasts.entity.spell;

import at.koopro.wizardsandbeasts.item.darkartefact.HorcruxDestruction;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.lib.SpellHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Fiendfyre: cursed fire loosed as a roaming beast (systems audit B3, 2026-11-25).
 *
 * <p>Canon (DH ch.31, the Room of Requirement): Crabbe's fire takes the shape of "serpents, chimaeras and dragons",
 * pursues them through the room, cannot be stopped by ordinary means, and kills the wizard who cast it, who never
 * learned to control it. Hermione names it: one of the few things that destroy a Horcrux, and the diadem goes in it.
 * The film (DH pt 2) shows the same roaring beasts of flame.
 *
 * <p>Here: a short-lived fire that burns what it touches and sets the ground alight under the server's
 * {@code spellBlockDamage} rule, steered by its caster while they keep control. Control is a roll on the caster's
 * proficiency every second; one failure and the fire is the caster's no longer, it goes for the nearest living thing,
 * caster included, until it burns out. Water does not quench it: nothing here reads water. Any intact Horcrux in its
 * reach, lying loose or carried by something it engulfs, is ended through {@link HorcruxDestruction}.
 *
 * <p>Transient: never saved, so a reload never wakes a fire without its caster.
 */
public class FiendfyreEntity extends Entity {

    /** Twenty seconds, then it burns out. */
    public static final int LIFE_TICKS = 400;
    /** What it engulfs, blocks from its centre. */
    public static final double REACH = 2.5;
    private static final double SPEED = 0.28;
    private static final double STEER_AHEAD = 6.0;
    private static final double HUNT_RANGE = 24.0;
    private static final float BURN_DAMAGE = 4.0f;

    private @Nullable UUID casterId;
    private double controlChance;
    private boolean turned;
    private @Nullable LivingEntity hunted;
    private int age;

    public FiendfyreEntity(EntityType<? extends FiendfyreEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /**
     * The chance per second that the caster keeps the fire. Even a master loses it now and then (twenty rolls in its
     * life: a master keeps it to the end a little over half the time); a novice almost never does.
     */
    public static double controlChance(Proficiency proficiency) {
        return switch (proficiency) {
            case NOVICE -> 0.85;
            case PROFICIENT -> 0.93;
            case MASTERED -> 0.97;
        };
    }

    /** Looses the fire at {@code at}. {@code caster} may be null: a fire with no master hunts from the start. */
    public static FiendfyreEntity loose(ServerLevel level, Vec3 at, @Nullable ServerPlayer caster, double controlChance) {
        FiendfyreEntity fire = new FiendfyreEntity(ModEntities.FIENDFYRE.get(), level);
        fire.setPos(at);
        fire.casterId = caster != null ? caster.getUUID() : null;
        fire.controlChance = controlChance;
        fire.turned = caster == null;
        level.addFreshEntity(fire);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.4f, 0.5f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.5f, 1.6f);
        return fire;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (++age > LIFE_TICKS) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 30, 1.0, 0.6, 1.0, 0.02);
            discard();
            return;
        }
        @Nullable ServerPlayer caster = caster(level);
        if (age % 20 == 0 && !turned && (caster == null || level.random.nextDouble() >= controlChance)) {
            turn(caster);
        }
        move(level, caster);
        endHorcruxes(level);
        if (age % 20 == 0) {
            burnLiving(level, caster);
        }
        if (age % 10 == 0) {
            burnGround(level, 3);
        }
        if (age % 30 == 0) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_BURN, SoundSource.HOSTILE, 1.2f, 0.6f);
        }
        flames(level);
    }

    private void turn(@Nullable ServerPlayer caster) {
        turned = true;
        if (caster != null) {
            caster.sendSystemMessage(Component.translatable("spell.wizards_and_beasts.fiendfyre.turned"));
        }
    }

    /** Whether the caster has lost it. Once lost it never comes back (Crabbe). */
    public boolean isTurned() {
        return turned;
    }

    /** What a turned fire is going for, or null. */
    public @Nullable LivingEntity hunted() {
        return hunted;
    }

    private @Nullable ServerPlayer caster(ServerLevel level) {
        if (casterId == null) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(casterId) instanceof ServerPlayer p
                && p.level() == level && p.isAlive() ? p : null;
    }

    private void move(ServerLevel level, @Nullable ServerPlayer caster) {
        Vec3 goal;
        if (!turned && caster != null) {
            Vec3 look = caster.getLookAngle().multiply(1, 0, 1);
            look = look.lengthSqr() < 1.0e-6 ? Vec3.ZERO : look.normalize();
            goal = caster.position().add(look.scale(STEER_AHEAD));
        } else {
            hunted = nearestLiving(level);
            goal = hunted != null ? hunted.position() : position();
        }
        Vec3 step = goal.subtract(position());
        double dist = step.length();
        if (dist > 0.05) {
            setPos(position().add(step.scale(Math.min(SPEED, dist) / dist)));
        }
    }

    private @Nullable LivingEntity nearestLiving(ServerLevel level) {
        AABB area = getBoundingBox().inflate(HUNT_RANGE);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !e.isSpectator())) {
            if (e instanceof Player p && p.isCreative()) {
                continue;
            }
            double d = e.distanceToSqr(this);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private AABB reach() {
        return new AABB(position(), position()).inflate(REACH, REACH * 0.6, REACH).move(0, REACH * 0.4, 0);
    }

    /** Ends every intact Horcrux in reach: a loose one, or one carried by whatever the fire engulfs. */
    public void endHorcruxes(ServerLevel level) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, reach())) {
            HorcruxDestruction.destroy(level, item.position(), item.getItem());
        }
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, reach())) {
            if (e instanceof Player p) {
                for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                    endIfHorcrux(level, e, p.getInventory().getItem(i));
                }
            } else {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    endIfHorcrux(level, e, e.getItemBySlot(slot));
                }
            }
        }
    }

    private static void endIfHorcrux(ServerLevel level, LivingEntity holder, ItemStack stack) {
        if (HorcruxDestruction.isIntact(stack)) {
            HorcruxDestruction.destroy(level, holder.getBoundingBox().getCenter(), stack);
        }
    }

    private void burnLiving(ServerLevel level, @Nullable ServerPlayer caster) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, reach(), Entity::isAlive)) {
            if (!turned && e == caster) {
                continue;
            }
            e.igniteForSeconds(8);
            e.hurtServer(level, level.damageSources().indirectMagic(this, caster), BURN_DAMAGE);
        }
    }

    /**
     * Sets the ground alight around it, through the same door as Incendio, so the server's {@code spellBlockDamage}
     * rule holds.
     *
     * @return the fire blocks placed
     */
    public int burnGround(ServerLevel level, int attempts) {
        return SpellHelper.scatterGroundFire(level, position(), attempts, REACH);
    }

    private void flames(ServerLevel level) {
        // A rearing column of flame with a head that sways: the film's beasts, read at a distance.
        double sway = Math.sin(age * 0.25) * 0.6;
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.8, getZ(), 14, 0.9, 0.8, 0.9, 0.04);
        level.sendParticles(ParticleTypes.FLAME, getX() + sway, getY() + 2.4, getZ() + sway * 0.5, 8, 0.35, 0.35, 0.35,
                0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.8, getZ(), 2, 0.6, 0.3, 0.6, 0.01);
        if (age % 4 == 0) {
            level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.4, getZ(), 2, 1.0, 0.2, 1.0, 0.0);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        // TRANSIENT
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        // TRANSIENT
    }
}
