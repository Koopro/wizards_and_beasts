package at.koopro.wizardsandbeasts.spell.effect;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.FiniteImmuneEffects;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellFamily;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Composable, data-driven spell effect primitive. A spell's behavior is (eventually) a
 * {@code List<SpellEffectComponent>} declared in JSON and applied through {@link #apply(SpellEffectContext)}.
 *
 * <p>Mirrors the {@code SkillNodeEffect} pattern: a sealed interface, a {@link Type} enum that pairs a
 * {@code type} discriminator with the variant's {@link MapCodec}, and a top-level {@link #CODEC} built
 * via {@code Type.CODEC.dispatch(...)}.
 *
 * <p><b>Scaling (F1).</b> {@code damage}, {@code apply_effect}, and {@code impulse} scale by the
 * {@link SpellEffectContext}'s {@code damageMult}/{@code durationMult}/{@code controlMult} so they
 * reproduce the legacy proficiency/wand/skill scaling. <b>Primary projectile/melee damage still rides
 * the scaled {@code baseDamage} legacy path</b> (which also folds in skill+wand via
 * {@code Spell.getDamageForCaster}); the {@code damage} component is for <i>adjunct / secondary</i>
 * damage and scales by the context's {@code damageMult} only.
 *
 * <p><b>Module gating (§4).</b> Every {@code apply(...)} no-ops unless {@link Module#WANDS_AND_SPELLS}
 * is enabled; {@link ApplyEffect} accepts a {@code darkArts} flag to gate on {@link Module#DARK_ARTS}.
 *
 * <p><b>Status: not wired to spells.</b> No spell ships an {@code effects} list except the step-3
 * pilots (lumos, stupefy). This pass only enriches the vocabulary; migration is the revised Step 4.
 */
public sealed interface SpellEffectComponent permits
        SpellEffectComponent.ApplyEffect,
        SpellEffectComponent.Damage,
        SpellEffectComponent.Ignite,
        SpellEffectComponent.Impulse,
        SpellEffectComponent.Heal,
        SpellEffectComponent.Dispel,
        SpellEffectComponent.ClearFire,
        SpellEffectComponent.Light,
        SpellEffectComponent.Repair,
        SpellEffectComponent.Explosion,
        SpellEffectComponent.AoeApply,
        SpellEffectComponent.SwapActiveSpell,
        SpellEffectComponent.InterruptCast,
        SpellEffectComponent.ParticleBurst,
        SpellEffectComponent.BoggartBanish {

    Logger LOGGER = LogUtils.getLogger();

    Codec<SpellEffectComponent> CODEC = Type.CODEC.dispatch(SpellEffectComponent::type, Type::codec);

    /**
     * Map form of {@link #CODEC}; lets {@link SpellEffectEntry} merge the optional top-level
     * {@code cadence} field into the same flat JSON object without touching any variant codec.
     */
    MapCodec<SpellEffectComponent> MAP_CODEC = Type.CODEC.dispatchMap(SpellEffectComponent::type, Type::codec);

    /** Discriminator. */
    Type type();

    /** Applies this primitive. No-ops when its required module(s) are disabled (see interface docs). */
    void apply(SpellEffectContext ctx);

    // ── Shared helpers ──────────────────────────────────────────────────

    /** Baseline gate: standard spell effects require {@link Module#WANDS_AND_SPELLS}. */
    static boolean standardEnabled() {
        return ModuleManager.isEnabled(Module.WANDS_AND_SPELLS);
    }

    /** Legacy duration-scaling rule: negative (infinite) durations pass through unscaled. */
    private static int scaleDuration(int rawDuration, float durationMult) {
        return rawDuration < 0 ? rawDuration : Math.max(1, Math.round(rawDuration * durationMult));
    }

    private static @Nullable Holder<MobEffect> resolveMobEffect(Identifier id) {
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
        CLEAR_FIRE("clear_fire", ClearFire.CODEC),
        LIGHT("light", Light.CODEC),
        REPAIR("repair", Repair.CODEC),
        EXPLOSION("explosion", Explosion.CODEC),
        AOE_APPLY("aoe_apply", AoeApply.CODEC),
        SWAP_ACTIVE_SPELL("swap_active_spell", SwapActiveSpell.CODEC),
        INTERRUPT_CAST("interrupt_cast", InterruptCast.CODEC),
        PARTICLE_BURST("particle_burst", ParticleBurst.CODEC),
        BOGGART_BANISH("boggart_banish", BoggartBanish.CODEC);

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

    /** Which entities an {@link AoeApply} touches. */
    enum AoeTarget implements StringRepresentable {
        /** Every living entity (the caster is added separately via {@code include_caster}). */
        ALL("all"),
        /** Only players. */
        PLAYERS("players"),
        /** Only hostiles ({@link net.minecraft.world.entity.monster.Enemy}). */
        MONSTERS("monsters");

        public static final Codec<AoeTarget> CODEC = StringRepresentable.fromValues(AoeTarget::values);

        private final String serializedName;

        AoeTarget(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        boolean matches(LivingEntity e) {
            return switch (this) {
                case ALL -> true;
                case PLAYERS -> e instanceof net.minecraft.world.entity.player.Player;
                case MONSTERS -> e instanceof net.minecraft.world.entity.monster.Enemy;
            };
        }
    }

    /** Which effects {@link Dispel} removes. */
    enum DispelScope implements StringRepresentable {
        /** Every active effect. */
        ALL("all"),
        /** Only effects registered under this mod's namespace (Finite Incantatem's behavior). */
        MOD_NAMESPACED("mod_namespaced"),
        /** Only the explicitly listed effect ids. */
        SPECIFIC("specific");

        public static final Codec<DispelScope> CODEC = StringRepresentable.fromValues(DispelScope::values);

        private final String serializedName;

        DispelScope(String serializedName) {
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
     * {@code target=true} affects the impact target (falling back to the caster). Duration is scaled by
     * the context's {@code durationMult} (negative/infinite durations pass through). {@code darkArts=true}
     * gates on {@link Module#DARK_ARTS} instead of {@link Module#WANDS_AND_SPELLS}.
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
            who.addEffect(new MobEffectInstance(holder, scaleDuration(duration, ctx.durationMult()),
                    amplifier, false, true, true));
        }
    }

    /**
     * Adjunct/secondary magic (or typed) damage to the impact target, scaled by the context's
     * {@code damageMult}. No-ops without a target. Primary projectile/melee damage stays on the legacy
     * scaled {@code baseDamage} path; do not use this to express a spell's primary damage.
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
            target.hurt(resolveDamageSource(ctx), amount * ctx.damageMult());
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
     * Pushes/pulls the subject, scaled by the context's {@code controlMult}. {@code PUSH} drives away
     * from the caster, {@code PULL} toward the caster, {@code UP} lifts vertically.
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
            float scaled = strength * ctx.controlMult();
            LivingEntity subject = ctx.subject();
            if (direction == ImpulseDirection.UP) {
                subject.push(0.0, Math.min(2.0f, scaled), 0.0);
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
            SpellHelper.applyKnockback(subject, dir, scaled);
        }
    }

    /** Heals the subject (impact target, else caster). Flat amount (matches legacy heal). */
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
     * Removes mob effects from the subject. {@code scope} selects {@code all}, {@code mod_namespaced}
     * (only this mod's effects — Finite Incantatem), or {@code specific} (only the listed ids).
     * {@code respect_immunity} (default true) skips effects flagged by {@link FiniteImmuneEffects}.
     */
    record Dispel(DispelScope scope, List<Identifier> effects, boolean respectImmunity)
            implements SpellEffectComponent {
        public static final MapCodec<Dispel> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                DispelScope.CODEC.optionalFieldOf("scope", DispelScope.ALL).forGetter(Dispel::scope),
                Identifier.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Dispel::effects),
                Codec.BOOL.optionalFieldOf("respect_immunity", true).forGetter(Dispel::respectImmunity)
        ).apply(inst, Dispel::new));

        @Override
        public Type type() {
            return Type.DISPEL;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled()) return;
            LivingEntity subject = ctx.subject();
            if (scope == DispelScope.SPECIFIC) {
                for (Identifier id : effects) {
                    Holder<MobEffect> holder = resolveMobEffect(id);
                    if (holder == null) continue;
                    if (respectImmunity && FiniteImmuneEffects.isImmune(holder.value())) continue;
                    subject.removeEffect(holder);
                }
                return;
            }
            List<Holder<MobEffect>> toRemove = new ArrayList<>();
            for (MobEffectInstance inst : subject.getActiveEffects()) {
                Holder<MobEffect> holder = inst.getEffect();
                if (respectImmunity && FiniteImmuneEffects.isImmune(holder.value())) continue;
                if (scope == DispelScope.MOD_NAMESPACED) {
                    Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(holder.value());
                    if (id == null || !WizardsAndBeastsMod.MODID.equals(id.getNamespace())) continue;
                }
                toRemove.add(holder);
            }
            for (Holder<MobEffect> holder : toRemove) {
                subject.removeEffect(holder);
            }
        }
    }

    /** Extinguishes the subject (compose resistances via separate {@code apply_effect} components). */
    record ClearFire() implements SpellEffectComponent {
        public static final MapCodec<ClearFire> CODEC = MapCodec.unit(ClearFire::new);

        @Override
        public Type type() {
            return Type.CLEAR_FIRE;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled()) return;
            ctx.subject().clearFire();
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

    /**
     * Applies a nested component list to every living entity (excluding the caster) within {@code radius}
     * of the application position. Each entity is run as a fresh subject through the runner, preserving
     * the parent context's scaling. Composes recursively; the nested codec is lazily initialized to break
     * the self-reference on {@link #CODEC}.
     */
    record AoeApply(float radius, List<SpellEffectComponent> components, AoeTarget filter,
                    boolean includeCaster) implements SpellEffectComponent {
        public static final MapCodec<AoeApply> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.fieldOf("radius").forGetter(AoeApply::radius),
                Codec.lazyInitialized(() -> SpellEffectComponent.CODEC).listOf()
                        .fieldOf("effects").forGetter(AoeApply::components),
                AoeTarget.CODEC.optionalFieldOf("filter", AoeTarget.ALL).forGetter(AoeApply::filter),
                Codec.BOOL.optionalFieldOf("include_caster", false).forGetter(AoeApply::includeCaster)
        ).apply(inst, AoeApply::new));

        @Override
        public Type type() {
            return Type.AOE_APPLY;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled() || radius <= 0f || components.isEmpty()) return;
            AABB area = new AABB(ctx.position(), ctx.position()).inflate(radius);
            List<LivingEntity> targets = ctx.level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e.isAlive()
                            && (includeCaster || e != ctx.caster())
                            && filter.matches(e));
            for (LivingEntity entity : targets) {
                SpellEffectRunner.runComponents(components, ctx.forTarget(entity));
            }
        }
    }

    /**
     * Swaps the caster's active loadout slot to {@code spell} (bare or namespaced id) and syncs the
     * change to the client. {@code learn=true} additionally teaches the spell first, so the swap target
     * is always usable. Expresses paired toggle spells (lumos→nox, nox→lumos) that the legacy
     * SELF-utility rules used to hardcode.
     *
     * <p>The id is canonicalized through the registry ({@code Spells.byId(...).getId()}) before being
     * stored: {@code knowsSpell}/loadout lookups are exact-string, and JSON spells register under
     * namespaced ids, so storing the authored bare id verbatim would strand the slot on an id the
     * learning paths never wrote.
     */
    record SwapActiveSpell(String spell, boolean learn) implements SpellEffectComponent {
        public static final MapCodec<SwapActiveSpell> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.STRING.fieldOf("spell").forGetter(SwapActiveSpell::spell),
                Codec.BOOL.optionalFieldOf("learn", false).forGetter(SwapActiveSpell::learn)
        ).apply(inst, SwapActiveSpell::new));

        @Override
        public Type type() {
            return Type.SWAP_ACTIVE_SPELL;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled()) return;
            Spell swapTarget = Spells.byId(spell);
            if (swapTarget == null) {
                LOGGER.warn("swap_active_spell: unknown spell id '{}'", spell);
                return;
            }
            String canonicalId = swapTarget.getId();
            PlayerSpellData data = ctx.caster().getData(ModAttachments.SPELL_DATA.get());
            if (learn) {
                data.learnSpell(canonicalId);
            }
            data.setLoadoutSpell(data.getActiveSlot(), canonicalId);
            SpellDataSyncS2CPayload.syncToPlayer(ctx.caster());
        }
    }

    /**
     * Interrupts the impact target's spellcasting: cancels an in-progress wand beam channel (players
     * only) and langlocks them for {@code lock_ticks} so they can't immediately re-cast. No-ops on the
     * caster. The anti-caster payload for Stupefy / Finite Incantatem.
     */
    record InterruptCast(int lockTicks) implements SpellEffectComponent {
        public static final MapCodec<InterruptCast> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.optionalFieldOf("lock_ticks", 30).forGetter(InterruptCast::lockTicks)
        ).apply(inst, InterruptCast::new));

        @Override
        public Type type() {
            return Type.INTERRUPT_CAST;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled()) return;
            LivingEntity subject = ctx.subject();
            if (subject == ctx.caster()) return;
            if (subject instanceof net.minecraft.server.level.ServerPlayer target) {
                at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic.endChannel(target);
            }
            subject.addEffect(new MobEffectInstance(
                    at.koopro.wizardsandbeasts.effect.ModEffects.LANGLOCK,
                    Math.max(1, lockTicks), 0, false, true, true));
        }
    }

    /**
     * Emits a cosmetic tinted particle burst at the application position (self = caster's eye, impact =
     * target/point). {@code family} picks the FX particle, {@code color} its ARGB tint; {@code count} and
     * {@code spread} shape the puff. Purely visual — server-spawned so every nearby client sees it — this
     * is the data-driven form of the legacy id-keyed cast bursts (e.g. Riddikulus).
     */
    record ParticleBurst(SpellFamily family, int color, int count, double spread)
            implements SpellEffectComponent {
        private static final Codec<SpellFamily> FAMILY_CODEC = StringRepresentable.fromValues(SpellFamily::values);
        public static final MapCodec<ParticleBurst> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                FAMILY_CODEC.optionalFieldOf("family", SpellFamily.ARCANE).forGetter(ParticleBurst::family),
                Codec.INT.fieldOf("color").forGetter(ParticleBurst::color),
                Codec.INT.optionalFieldOf("count", 12).forGetter(ParticleBurst::count),
                Codec.DOUBLE.optionalFieldOf("spread", 0.2).forGetter(ParticleBurst::spread)
        ).apply(inst, ParticleBurst::new));

        @Override
        public Type type() {
            return Type.PARTICLE_BURST;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled() || count <= 0) return;
            SpellHelper.spawnBurst(ctx.level(), family, color, ctx.position(), count, spread);
        }
    }

    /**
     * The anti-Boggart charm. Scans {@code radius} around the application point for Boggart creatures
     * ({@code wizards_and_beasts:boggart}) and banishes each with a burst of colour and a comic pop —
     * the laughter that finishes a Boggart forced into a ridiculous shape. Reveals a hidden (Invisible)
     * Boggart before defeating it. No-ops when no Boggart is near, so Riddikulus stays a harmless
     * courage-steadying cast against ordinary fear. This is what makes Riddikulus the anti-Boggart
     * charm rather than a generic buff — the Boggart is the only thing it targets.
     */
    record BoggartBanish(float radius) implements SpellEffectComponent {
        private static final Identifier BOGGART_ID =
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "boggart");

        public static final MapCodec<BoggartBanish> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("radius", 8.0f).forGetter(BoggartBanish::radius)
        ).apply(inst, BoggartBanish::new));

        @Override
        public Type type() {
            return Type.BOGGART_BANISH;
        }

        @Override
        public void apply(SpellEffectContext ctx) {
            if (!standardEnabled() || radius <= 0f) return;
            AABB area = new AABB(ctx.position(), ctx.position()).inflate(radius);
            List<GenericBeastEntity> boggarts = ctx.level().getEntitiesOfClass(GenericBeastEntity.class, area,
                    e -> e.isAlive() && BOGGART_ID.equals(e.creatureId()));
            if (boggarts.isEmpty()) return;
            DamageSource laughter = ctx.level().damageSources().magic();
            for (GenericBeastEntity boggart : boggarts) {
                boggart.removeEffect(MobEffects.INVISIBILITY);
                SpellHelper.spawnBurst(ctx.level(), SpellFamily.ARCANE, 0xFFFFF0A0,
                        boggart.getBoundingBox().getCenter(), 24, 0.45);
                ctx.level().playSound(null, boggart.getX(), boggart.getY(), boggart.getZ(),
                        SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.7f, 1.6f);
                boggart.hurt(laughter, 1000.0f);
            }
        }
    }
}
