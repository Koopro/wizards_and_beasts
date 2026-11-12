package at.koopro.wizardsandbeasts.creature.ability;

import at.koopro.wizardsandbeasts.entity.creature.CreatureMissile;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.ai.RangedShotGoal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * A physical ranged attack: an arrow loosed, a rock thrown, venom spat. The counterpart of {@link RangedHex} for
 * creatures that do not cast.
 *
 * <p>The missile is a real entity that flies an arc and can be sidestepped or blocked with a shield. An arrow is
 * vanilla's own arrow (never collectable); a boulder and spit are {@link CreatureMissile}s.
 *
 * @param kind          what is sent
 * @param range         furthest target it will shoot at
 * @param minRange      nearest target it will shoot at; inside this it must fight another way
 * @param damage        damage on a hit
 * @param effect        status effect on a hit (a poisoned arrow, venom)
 * @param cooldownTicks pause between shots
 * @param windupTicks   ticks between showing the shot and releasing it
 * @param defensive     also answers a blow at once by shooting whoever struck it, target or not. This is how a
 *                      creature with no will to hunt (the Lobalug) still uses its venom.
 */
public record RangedShot(
        Kind kind,
        double range,
        double minRange,
        float damage,
        Optional<AbilitySupport.EffectSpec> effect,
        int cooldownTicks,
        int windupTicks,
        boolean defensive
) implements CreatureAbility {

    public static final MapCodec<RangedShot> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Kind.CODEC.fieldOf("kind").forGetter(RangedShot::kind),
            Codec.DOUBLE.optionalFieldOf("range", 14.0).forGetter(RangedShot::range),
            Codec.DOUBLE.optionalFieldOf("min_range", 0.0).forGetter(RangedShot::minRange),
            Codec.FLOAT.optionalFieldOf("damage", 3.0f).forGetter(RangedShot::damage),
            AbilitySupport.EffectSpec.CODEC.optionalFieldOf("effect").forGetter(RangedShot::effect),
            Codec.INT.optionalFieldOf("cooldown_ticks", 60).forGetter(RangedShot::cooldownTicks),
            Codec.INT.optionalFieldOf("windup_ticks", 0).forGetter(RangedShot::windupTicks),
            Codec.BOOL.optionalFieldOf("defensive", false).forGetter(RangedShot::defensive)
    ).apply(instance, RangedShot::new));

    public RangedShot {
        range = Math.max(1.0, Math.min(48.0, range));
        minRange = Math.max(0.0, Math.min(range, minRange));
        cooldownTicks = Math.max(10, cooldownTicks);
        windupTicks = Math.max(0, Math.min(60, windupTicks));
    }

    @Override
    public CreatureAbility.Type type() {
        return CreatureAbility.Type.RANGED_SHOT;
    }

    @Override
    public void registerGoals(@NonNull GenericBeastEntity entity, @NonNull GoalSelector goalSelector) {
        goalSelector.addGoal(2, new RangedShotGoal(entity, this));
        // An archer is seen with its bow from the moment it exists, not from its first shot.
        if (kind == Kind.ARROW && !entity.level().isClientSide() && entity.getMainHandItem().isEmpty()) {
            entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            entity.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0f);
        }
    }

    @Override
    public void onHurt(@NonNull GenericBeastEntity entity, @NonNull DamageSource source, float amount) {
        if (!defensive || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (source.getEntity() instanceof LivingEntity attacker && attacker != entity && attacker.isAlive()
                && inBand(entity.distanceTo(attacker)) && entity.getSensing().hasLineOfSight(attacker)) {
            release(level, entity, attacker);
        }
    }

    /** True when a target {@code distance} blocks away is one this shot is for. */
    public boolean inBand(double distance) {
        return distance >= minRange && distance <= range;
    }

    /**
     * How far above the target to aim so the arc comes down on it.
     *
     * <p>The missile falls {@code gravity} faster each tick and takes about {@code distance / speed} ticks to
     * arrive, so it drops half of gravity times that squared. A little is added for air drag, which slows it and
     * so lengthens the flight.
     */
    public static double lift(double distance, double speed, double gravity) {
        double ticks = distance / speed;
        return 0.5 * gravity * ticks * ticks * 1.15;
    }

    /** Send one missile at {@code target} now. */
    public void release(ServerLevel level, GenericBeastEntity shooter, LivingEntity target) {
        Projectile missile = switch (kind) {
            case ARROW -> arrow(level, shooter);
            case BOULDER -> CreatureMissile.boulder(level, shooter, damage, effect.orElse(null));
            case SPIT -> CreatureMissile.spit(level, shooter, damage, effect.orElse(null));
        };
        double dx = target.getX() - shooter.getX();
        double dz = target.getZ() - shooter.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        double dy = target.getY(0.5) - missile.getY() + lift(flat, kind.speed, kind.gravity);
        missile.shoot(dx, dy, dz, kind.speed, kind.spread);
        level.addFreshEntity(missile);
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), kind.sound(), SoundSource.HOSTILE,
                1.0f, kind.pitch);
    }

    private Arrow arrow(ServerLevel level, GenericBeastEntity shooter) {
        Arrow arrow = new Arrow(level, shooter, new ItemStack(Items.ARROW), null);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        // An arrow deals its base damage times its speed.
        arrow.setBaseDamage(damage / Kind.ARROW.speed);
        effect.ifPresent(spec -> {
            Holder<MobEffect> holder = AbilitySupport.resolveEffect(spec.effect());
            if (holder != null) {
                arrow.addEffect(new MobEffectInstance(holder, spec.durationTicks(), spec.amplifier()));
            }
        });
        return arrow;
    }

    /** What is sent, with how it flies. */
    public enum Kind implements StringRepresentable {
        ARROW("arrow", 1.6f, 0.05, 2.0f, 1.0f),
        BOULDER("boulder", 1.1f, 0.05, 3.0f, 0.5f),
        SPIT("spit", 1.3f, 0.03, 4.0f, 1.3f);

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String name;
        /** Blocks per tick at release. */
        public final float speed;
        /** Fall added per tick; must match the missile entity's own gravity. */
        public final double gravity;
        /** Vanilla's inaccuracy figure. */
        public final float spread;
        final float pitch;

        Kind(String name, float speed, double gravity, float spread, float pitch) {
            this.name = name;
            this.speed = speed;
            this.gravity = gravity;
            this.spread = spread;
            this.pitch = pitch;
        }

        SoundEvent sound() {
            return switch (this) {
                case ARROW -> SoundEvents.ARROW_SHOOT;
                case BOULDER -> SoundEvents.WITCH_THROW;
                case SPIT -> SoundEvents.LLAMA_SPIT;
            };
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
