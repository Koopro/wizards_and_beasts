package at.koopro.wizardsandbeasts.spell.effect;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.spell.lib.SpellHelper;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Composable, data-driven spell effect primitive. A spell's behavior is (eventually) a
 * {@code List<SpellEffectComponent>} declared in JSON and applied through {@link #apply(SpellEffectContext)}.
 *
 * <p>Mirrors the {@code SkillNodeEffect} pattern exactly: a sealed interface, a {@link Type} enum that
 * pairs a {@code type} discriminator string with the variant's {@link MapCodec}, and a top-level
 * {@link #CODEC} built via {@code Type.CODEC.dispatch(...)}.
 *
 * <p><b>v1 vocabulary (9 components):</b> {@code apply_effect}, {@code damage}, {@code ignite},
 * {@code impulse}, {@code heal}, {@code dispel}, {@code light}, {@code repair}, {@code explosion}.
 *
 * <p><b>Module gating (§4).</b> Every {@code apply(...)} no-ops unless {@link Module#WANDS_AND_SPELLS}
 * is enabled, matching the effect-site gate in {@code Spell.canApplyEffect}. {@link ApplyEffect} also
 * accepts a {@code darkArts} flag so a dark component no-ops unless {@link Module#DARK_ARTS} is enabled
 * (relevant to spells like Crucio when/if migrated). This keeps components honoring the existing
 * gating model rather than introducing a new path.
 *
 * <p><b>Status: not wired.</b> No spell constructs or runs these yet — see {@link SpellEffectContext}.
 */
public sealed interface SpellEffectComponent permits
        SpellEffectComponent.ApplyEffect,
        SpellEffectComponent.Damage,
        SpellEffectComponent.Ignite,
        SpellEffectComponent.Impulse,
        SpellEffectComponent.Heal,
        SpellEffectComponent.Dispel,
        SpellEffectComponent.Light,
        SpellEffectComponent.Repair,
        SpellEffectComponent.Explosion {

    Logger LOGGER = LogUtils.getLogger();

    Codec<SpellEffectComponent> CODEC = Type.CODEC.dispatch(SpellEffectComponent::type, Type::codec);

    /** Discriminator. */
    Type type();

    /** Applies this primitive. No-ops when its required module(s) are disabled (see interface docs). */
    void apply(SpellEffectContext ctx);

    // ── Shared helpers ──────────────────────────────────────────────────

    /** Baseline gate: standard spell effects require {@link Module#WANDS_AND_SPELLS}. */
    static boolean standardEnabled() {
        return ModuleManager.isEnabled(Module.WANDS_AND_SPELLS);
    }

    private static Holder<MobEffect> resolveMobEffect(Identifier id) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(id);
        if (effect == null) {
            LOGGER.warn("Unknown mob effect id in spell effect component: {}", id);
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }

    enum Type implements StringRepresentable {
        APPLY_EFFECT("apply_effect", ApplyEffect.CODEC),
        DAMAGE("damage", Damage.CODEC),
        IGNITE("ignite", Ignite.CODEC),
        IMPULSE("impulse", Impulse.CODEC),
        HEAL("heal", Heal.CODEC),
        DISPEL("dispel", Dispel.CODEC),
        LIGHT("light", Light.CODEC),
        REPAIR("repair", Repair.CODEC),
        EXPLOSION("explosion", Explosion.CODEC);

        public static final Codec<Type> CODEC = StringRepresentable.fromValues(Type::values);

        private final String serializedName;
        private final MapCodec<? extends SpellEffectComponent> codec;

        Type(String serializedName, MapCodec<? extends SpellEffectComponent> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public MapCodec<? extends SpellEffectComponent> codec() {
            return codec;
        }
    }

    /** Push/pull/lift direction for {@link Impulse}. */
    enum ImpulseDirection implements StringRepresentable {
        PUSH("push"),
        PULL("pull"),
        UP("up");

        public static final Codec<ImpulseDirection> CODEC = StringRepresentable.fromValues(ImpulseDirection::values);

        private final String serializedName;

        ImpulseDirection(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    // ── Components ──────────────────────────────────────────────────────

    /**
     * Applies a mob effect. {@code target=false} (default) affects the caster (self effect);
     * {@code target=true} affects the impact target (falling back to the caster). {@code darkArts=true}
     * gates the effect behind {@link Module#DARK_ARTS} instead of {@link Module#WANDS_AND_SPELLS}.
     */
    record ApplyEffect(Identifier effect, int duration, int amplifier, boolean target, boolean darkArts)
            implements SpellEffectComponent {
        public static final MapCodec<ApplyEffect> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Identifier.CODEC.fieldOf("effect").forGetter(ApplyEffect::effect),
                Codec.INT.fieldOf("duration").forGetter(ApplyEffect::duration),
                Codec.INT.optionalFieldOf("amplifier", 0).forGetter(ApplyEffect::amplifier),
                Codec.BOOL.optionalFieldOf("target", false).forGetter(ApplyEffect::target),
                Codec.BOOL.optionalFieldOf("darkArts", false).forGetter(ApplyEffect::darkArts)
        ).apply(inst, ApplyEffect::new));

        @Override
        public Type type() {
            return Type.APPLY_EFFECT;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            boolean enabled = darkArts ? ModuleManager.isEnabled(Module.DARK_ARTS) : standardEnabled();
            if (!enabled) return;
            Holder<MobEffect> holder = resolveMobEffect(effect);
            if (holder == null) return;
            LivingEntity who = target ? ctx.subject() : ctx.caster();
            who.addEffect(new MobEffectInstance(holder, duration, amplifier, false, true, true));
        }
    }

    /**
     * Magic (or typed) damage to the impact target. No-ops without a target. {@code damageType} is an
     * optional {@link DamageType} {@link Identifier}; absent → vanilla magic damage.
     */
    record Damage(float amount, Optional<Identifier> damageType) implements SpellEffectComponent {
        public static final MapCodec<Damage> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("amount").forGetter(Damage::amount),
                Identifier.CODEC.optionalFieldOf("damageType").forGetter(Damage::damageType)
        ).apply(inst, Damage::new));

        @Override
        public Type type() {
            return Type.DAMAGE;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled() || amount <= 0f) return;
            LivingEntity target = ctx.target();
            if (target == null) return;
            target.hurt(resolveDamageSource(ctx), amount);
        }

        private DamageSource resolveDamageSource(SpellEffectContext ctx) {
            DamageSource magic = ctx.level().damageSources().magic();
            if (damageType.isEmpty()) return magic;
            try {
                ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, damageType.get());
                Holder<DamageType> holder = ctx.level().registryAccess()
                        .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
                return new DamageSource(holder, ctx.caster());
            } catch (RuntimeException ex) {
                LOGGER.warn("Unknown damage type '{}' in spell effect component; using magic.", damageType.get());
                return magic;
            }
        }
    }

    /** Sets the subject (impact target, else caster) on fire for {@code seconds}. */
    record Ignite(int seconds) implements SpellEffectComponent {
        public static final MapCodec<Ignite> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.fieldOf("seconds").forGetter(Ignite::seconds)
        ).apply(inst, Ignite::new));

        @Override
        public Type type() {
            return Type.IGNITE;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled() || seconds <= 0) return;
            SpellHelper.ignite(ctx.subject(), seconds);
        }
    }

    /**
     * Pushes/pulls the subject. {@code PUSH} drives away from the caster, {@code PULL} toward the caster,
     * {@code UP} lifts vertically. Uses the same capped knockback helper as combat spells.
     */
    record Impulse(float strength, ImpulseDirection direction) implements SpellEffectComponent {
        public static final MapCodec<Impulse> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("strength").forGetter(Impulse::strength),
                ImpulseDirection.CODEC.optionalFieldOf("direction", ImpulseDirection.PUSH).forGetter(Impulse::direction)
        ).apply(inst, Impulse::new));

        @Override
        public Type type() {
            return Type.IMPULSE;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled() || strength == 0f) return;
            LivingEntity subject = ctx.subject();
            if (direction == ImpulseDirection.UP) {
                subject.push(0.0, Math.min(2.0f, strength), 0.0);
                subject.hurtMarked = true;
                return;
            }
            Vec3 casterCenter = ctx.caster().getBoundingBox().getCenter();
            Vec3 subjectCenter = subject.getBoundingBox().getCenter();
            Vec3 dir = direction == ImpulseDirection.PUSH
                    ? subjectCenter.subtract(casterCenter)
                    : casterCenter.subtract(subjectCenter);
            if (dir.lengthSqr() < 1.0e-4) {
                dir = ctx.caster().getLookAngle();
            }
            SpellHelper.applyKnockback(subject, dir, strength);
        }
    }

    /** Heals the subject (impact target, else caster). */
    record Heal(float amount) implements SpellEffectComponent {
        public static final MapCodec<Heal> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("amount").forGetter(Heal::amount)
        ).apply(inst, Heal::new));

        @Override
        public Type type() {
            return Type.HEAL;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled() || amount <= 0f) return;
            ctx.subject().heal(amount);
        }
    }

    /**
     * Removes mob effects from the subject. When {@code effects} is present, only those ids are removed;
     * when absent/empty, all active effects are removed.
     */
    record Dispel(Optional<List<Identifier>> effects) implements SpellEffectComponent {
        public static final MapCodec<Dispel> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Identifier.CODEC.listOf().optionalFieldOf("effects").forGetter(Dispel::effects)
        ).apply(inst, Dispel::new));

        @Override
        public Type type() {
            return Type.DISPEL;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled()) return;
            LivingEntity subject = ctx.subject();
            if (effects.isEmpty() || effects.get().isEmpty()) {
                subject.removeAllEffects();
                return;
            }
            for (Identifier id : effects.get()) {
                Holder<MobEffect> holder = resolveMobEffect(id);
                if (holder != null) subject.removeEffect(holder);
            }
        }
    }

    /**
     * Places ({@code place=true}) or removes ({@code place=false}) a vanilla light block at the
     * application position.
     */
    record Light(boolean place) implements SpellEffectComponent {
        public static final MapCodec<Light> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.BOOL.optionalFieldOf("place", true).forGetter(Light::place)
        ).apply(inst, Light::new));

        @Override
        public Type type() {
            return Type.LIGHT;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled()) return;
            Level level = ctx.level();
            BlockPos pos = BlockPos.containing(ctx.position());
            BlockState state = level.getBlockState(pos);
            if (place) {
                if (state.isAir()) {
                    level.setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState());
                }
            } else if (state.is(Blocks.LIGHT)) {
                level.removeBlock(pos, false);
            }
        }
    }

    /** Repairs the caster's held damageable item (offhand first, else main hand) by {@code durability} points. */
    record Repair(int durability) implements SpellEffectComponent {
        public static final MapCodec<Repair> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.fieldOf("durability").forGetter(Repair::durability)
        ).apply(inst, Repair::new));

        @Override
        public Type type() {
            return Type.REPAIR;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled() || durability <= 0) return;
            ItemStack offhand = ctx.caster().getOffhandItem();
            ItemStack chosen = offhand.isDamageableItem() && offhand.isDamaged()
                    ? offhand
                    : ctx.caster().getMainHandItem();
            if (chosen.isDamageableItem() && chosen.isDamaged()) {
                chosen.setDamageValue(Math.max(0, chosen.getDamageValue() - durability));
            }
        }
    }

    /** Creates an explosion at the application position. */
    record Explosion(float radius, boolean fire, boolean breakBlocks) implements SpellEffectComponent {
        public static final MapCodec<Explosion> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("radius").forGetter(Explosion::radius),
                Codec.BOOL.optionalFieldOf("fire", false).forGetter(Explosion::fire),
                Codec.BOOL.optionalFieldOf("breakBlocks", false).forGetter(Explosion::breakBlocks)
        ).apply(inst, Explosion::new));

        @Override
        public Type type() {
            return Type.EXPLOSION;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled() || radius <= 0f) return;
            Vec3 pos = ctx.position();
            Level.ExplosionInteraction interaction = breakBlocks
                    ? Level.ExplosionInteraction.TNT
                    : Level.ExplosionInteraction.MOB;
            ctx.level().explode(ctx.caster(), pos.x, pos.y, pos.z, radius, fire, interaction);
        }
    }
}
