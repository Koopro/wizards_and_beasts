package at.koopro.wizardsandbeasts.brew.effect;

import at.koopro.wizardsandbeasts.felix.FelixFortune;
import at.koopro.wizardsandbeasts.polyjuice.PolyjuiceSample;
import at.koopro.wizardsandbeasts.polyjuice.PolyjuiceService;
import at.koopro.wizardsandbeasts.veritaserum.VeritaserumService;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * What a brew <em>does</em>, as composable data.
 *
 * <h2>Why this exists</h2>
 * <p>A {@link at.koopro.wizardsandbeasts.brew.Brew} was a {@code List<EffectSpec>} and nothing else,
 * so every potion in the wizarding world was necessarily a list of vanilla mob effects with a name on
 * it. Wiggenweld <em>was</em> Regeneration II plus Absorption. There was no seam at which Felix
 * Felicis could be luck-shaped, or Polyjuice could be a disguise, because the data model had no way
 * to say anything except "apply these effects for this long".
 *
 * <p>Mirrors {@code SpellEffectComponent}, which solved the identical problem for spells and deleted
 * twenty Java classes doing it: a sealed interface, a {@link Type} enum pairing a discriminator with
 * each variant's {@link MapCodec}, and a top-level {@link #CODEC} built by {@code dispatch}.
 *
 * <h2>The default is the old behaviour</h2>
 * <p>{@link ApplyEffects} is exactly what a brew used to be. A brew that declares no {@code components}
 * has its legacy {@code effects} list wrapped in one, so nothing that existed before this class
 * behaves any differently — see {@code BrewDefinition#toBrew}. The migration is opt-in per brew, one
 * JSON file at a time, and un-migrated brews are not second-class: an effect list <em>is</em> the
 * right description of most potions.
 *
 * <h2>The two famous ones</h2>
 * <p>{@link FelixFelicis} and {@link PolyjuiceDisguise} were both held back until the systems behind
 * them existed, and both are now short hand-offs to those systems rather than mob-effect lists
 * wearing famous names. Adding each one touched this file and one JSON, which is what the seam was
 * for.
 */
public sealed interface BrewEffect permits
        BrewEffect.ApplyEffects,
        BrewEffect.HealAndCure,
        BrewEffect.Nourish,
        BrewEffect.Extinguish,
        BrewEffect.Flourish,
        BrewEffect.FelixFelicis,
        BrewEffect.PolyjuiceDisguise,
        BrewEffect.TruthSerum {

    Logger LOGGER = LogUtils.getLogger();

    Codec<BrewEffect> CODEC = Type.CODEC.dispatch(BrewEffect::type, Type::codec);

    /**
     * Map form of {@link #CODEC}, so {@link BrewEffectEntry} can merge the optional {@code phase}
     * field into the same flat JSON object without any variant codec knowing about it.
     */
    MapCodec<BrewEffect> MAP_CODEC = Type.CODEC.dispatchMap(BrewEffect::type, Type::codec);

    /** Discriminator. */
    Type type();

    /** Do the thing. */
    void apply(BrewEffectContext ctx);

    // ── shared helpers ──────────────────────────────────────────────────────────────────────

    private static @Nullable Holder<MobEffect> resolveMobEffect(Identifier id) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(id);
        if (effect == null) {
            LOGGER.warn("Unknown mob effect id in brew component: {}", id);
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }

    enum Type implements StringRepresentable {
        APPLY_EFFECTS("apply_effects", ApplyEffects.CODEC),
        HEAL_AND_CURE("heal_and_cure", HealAndCure.CODEC),
        NOURISH("nourish", Nourish.CODEC),
        EXTINGUISH("extinguish", Extinguish.CODEC),
        FLOURISH("flourish", Flourish.CODEC),
        FELIX_FELICIS("felix_felicis", FelixFelicis.CODEC),
        POLYJUICE_DISGUISE("polyjuice_disguise", PolyjuiceDisguise.CODEC),
        TRUTH_SERUM("truth_serum", TruthSerum.CODEC);

        public static final Codec<Type> CODEC = StringRepresentable.fromValues(Type::values);

        private final String serializedName;
        private final MapCodec<? extends BrewEffect> codec;

        Type(String serializedName, MapCodec<? extends BrewEffect> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public MapCodec<? extends BrewEffect> codec() {
            return codec;
        }
    }

    // ── the default ─────────────────────────────────────────────────────────────────────────

    /**
     * Apply a list of mob effects. What every brew used to be, and what most should stay.
     *
     * <p>Durations are scaled by the drinker's potency; amplifiers are not. Potency is a
     * <em>herbology</em> skill about making a draught last, and a skill that also raised amplifiers
     * would silently turn Regeneration II into Regeneration III on a good brewer and break every
     * balance assumption a datapack author made.
     */
    record ApplyEffects(List<EffectSpec> effects) implements BrewEffect {

        public static final MapCodec<ApplyEffects> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                EffectSpec.CODEC.listOf().fieldOf("effects").forGetter(ApplyEffects::effects)
        ).apply(inst, ApplyEffects::new));

        @Override
        public Type type() {
            return Type.APPLY_EFFECTS;
        }

        @Override
        public void apply(BrewEffectContext ctx) {
            for (EffectSpec spec : effects) {
                Holder<MobEffect> holder = resolveMobEffect(spec.id());
                if (holder == null) continue;
                ctx.drinker().addEffect(new MobEffectInstance(holder,
                        ctx.scaleDuration(spec.duration()), spec.amplifier(), spec.ambient(), true, true));
            }
        }

        /** One authored mob effect. Same shape as the legacy {@code effects} entry, on purpose. */
        public record EffectSpec(Identifier id, int duration, int amplifier, boolean ambient) {
            public static final Codec<EffectSpec> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                    Identifier.CODEC.fieldOf("id").forGetter(EffectSpec::id),
                    Codec.INT.fieldOf("duration").forGetter(EffectSpec::duration),
                    Codec.INT.optionalFieldOf("amplifier", 0).forGetter(EffectSpec::amplifier),
                    Codec.BOOL.optionalFieldOf("ambient", false).forGetter(EffectSpec::ambient)
            ).apply(inst, EffectSpec::new));
        }
    }

    // ── the healing family ──────────────────────────────────────────────────────────────────

    /**
     * Mend the drinker and clear what is wrong with them.
     *
     * <p>The component Wiggenweld, Skele-Gro and Pepperup all wanted and none could have: an
     * <em>instant</em> mend plus the removal of afflictions. Regeneration is a different thing — it is
     * a slow trickle you can be killed through — and a healing draught that only granted it made every
     * healing potion in the mod feel like the same potion.
     *
     * <p>{@code cure} names effects to remove. Empty means "every harmful effect", which is the
     * common case and saves a datapack listing thirty ids to express "cures what ails you". Beneficial
     * effects are never stripped by the empty form: a potion that healed you and cancelled your own
     * Strength would be a trap.
     */
    record HealAndCure(float heal, List<Identifier> cure, boolean cureAllHarmful) implements BrewEffect {

        public static final MapCodec<HealAndCure> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.FLOAT.optionalFieldOf("heal", 0f).forGetter(HealAndCure::heal),
                Identifier.CODEC.listOf().optionalFieldOf("cure", List.of()).forGetter(HealAndCure::cure),
                Codec.BOOL.optionalFieldOf("cureAllHarmful", false).forGetter(HealAndCure::cureAllHarmful)
        ).apply(inst, HealAndCure::new));

        @Override
        public Type type() {
            return Type.HEAL_AND_CURE;
        }

        @Override
        public void apply(BrewEffectContext ctx) {
            LivingEntity drinker = ctx.drinker();
            if (heal > 0) {
                drinker.heal(heal);
            }
            for (Identifier id : cure) {
                Holder<MobEffect> holder = resolveMobEffect(id);
                if (holder != null) {
                    drinker.removeEffect(holder);
                }
            }
            if (cureAllHarmful) {
                // Copied before iterating: removeEffect mutates the same collection getActiveEffects
                // is a view of, and removing while iterating it is a concurrent modification.
                List<Holder<MobEffect>> harmful = drinker.getActiveEffects().stream()
                        .filter(instance -> !instance.getEffect().value().isBeneficial())
                        .map(MobEffectInstance::getEffect)
                        .toList();
                harmful.forEach(drinker::removeEffect);
            }
            ctx.level().playSound(null, drinker.getX(), drinker.getY(), drinker.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.8f);
            ctx.level().sendParticles(ParticleTypes.HEART,
                    drinker.getX(), drinker.getY() + 1.0, drinker.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
        }
    }

    /**
     * Feed the drinker.
     *
     * <p>A potion is not a sandwich, so this is deliberately small and exists for the draughts whose
     * canon description is "restorative" — something that puts a meal back into somebody who has been
     * ill. No-ops on anything without a hunger bar rather than pretending.
     */
    record Nourish(int food, float saturation) implements BrewEffect {

        public static final MapCodec<Nourish> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.optionalFieldOf("food", 0).forGetter(Nourish::food),
                Codec.FLOAT.optionalFieldOf("saturation", 0f).forGetter(Nourish::saturation)
        ).apply(inst, Nourish::new));

        @Override
        public Type type() {
            return Type.NOURISH;
        }

        @Override
        public void apply(BrewEffectContext ctx) {
            Player player = ctx.player();
            if (player == null) {
                return;
            }
            FoodData data = player.getFoodData();
            data.eat(food, saturation);
        }
    }

    /** Put the drinker out, and give them a moment where fire cannot take again. */
    record Extinguish(int fireResistanceTicks) implements BrewEffect {

        public static final MapCodec<Extinguish> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.optionalFieldOf("fireResistanceTicks", 0).forGetter(Extinguish::fireResistanceTicks)
        ).apply(inst, Extinguish::new));

        @Override
        public Type type() {
            return Type.EXTINGUISH;
        }

        @Override
        public void apply(BrewEffectContext ctx) {
            ctx.drinker().clearFire();
            if (fireResistanceTicks > 0) {
                ctx.drinker().addEffect(new MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,
                        ctx.scaleDuration(fireResistanceTicks), 0, false, true, true));
            }
        }
    }

    /**
     * Liquid luck. Hands off to {@link at.koopro.wizardsandbeasts.felix.FelixFortune}.
     *
     * <p>Deliberately thin. Everything interesting — the weighted rolls, the internal cooldowns, the
     * overdose — is a system with its own state, and a component that reimplemented any of it would be
     * a second place for Felix to disagree with itself. What is authored here is only what a datapack
     * should get to decide: how long, how strong, and what a second bottle does.
     *
     * <p>No-ops for a non-player drinker. A pig cannot have a run of good fortune.
     */
    record FelixFelicis(int durationTicks, int strength, int cooldownTicks,
                        FelixFortune.OverdosePolicy overdose) implements BrewEffect {

        public static final MapCodec<FelixFelicis> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.optionalFieldOf("durationTicks", FelixFortune.DEFAULT_DURATION_TICKS)
                        .forGetter(FelixFelicis::durationTicks),
                Codec.INT.optionalFieldOf("strength", 1).forGetter(FelixFelicis::strength),
                Codec.INT.optionalFieldOf("cooldownTicks", FelixFortune.DEFAULT_COOLDOWN_TICKS)
                        .forGetter(FelixFelicis::cooldownTicks),
                StringRepresentable.fromEnum(FelixFortune.OverdosePolicy::values)
                        .optionalFieldOf("overdose", FelixFortune.OverdosePolicy.OVERDOSE)
                        .forGetter(FelixFelicis::overdose)
        ).apply(inst, FelixFelicis::new));

        @Override
        public Type type() {
            return Type.FELIX_FELICIS;
        }

        @Override
        public void apply(BrewEffectContext ctx) {
            if (!(ctx.drinker() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }
            // Duration takes the drinker's potency, the way every other brewed duration does; the
            // strength does not, because potency is about making a draught last rather than making it
            // better — see ApplyEffects.
            FelixFortune.drink(player, ctx.scaleDuration(durationTicks), strength,
                    cooldownTicks, overdose);
        }
    }

    /**
     * Somebody else's face, for a while.
     *
     * <p>Hands off to {@link at.koopro.wizardsandbeasts.polyjuice.PolyjuiceService}, which owns the
     * state, the sync and the revert. As with {@link FelixFelicis}, the component is thin on purpose:
     * a disguise has security consequences, and a second place that could start one would be a second
     * place to get them wrong.
     *
     * <p>The target is read from the <b>bottle</b>, not the brew. Every bottle of Polyjuice is the same
     * brew; what makes one of them a disguise of a particular person is the hair that went into it,
     * which lives on the stack as {@code POLYJUICE_TARGET}.
     */
    record PolyjuiceDisguise(int durationTicks) implements BrewEffect {

        public static final MapCodec<PolyjuiceDisguise> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.optionalFieldOf("durationTicks", PolyjuiceService.DEFAULT_DURATION_TICKS)
                        .forGetter(PolyjuiceDisguise::durationTicks)
        ).apply(inst, PolyjuiceDisguise::new));

        @Override
        public Type type() {
            return Type.POLYJUICE_DISGUISE;
        }

        @Override
        public void apply(BrewEffectContext ctx) {
            if (!(ctx.drinker() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }
            PolyjuiceSample sample = PolyjuiceSample.read(ctx.source());
            PolyjuiceService.drink(player, sample.id(), sample.name(),
                    ctx.scaleDuration(durationTicks));
        }
    }

    /**
     * Veritaserum. Hands off to {@link at.koopro.wizardsandbeasts.veritaserum.VeritaserumService}.
     *
     * <p>Thin for the same reason {@link FelixFelicis} and {@link PolyjuiceDisguise} are: the
     * compulsion is enforced from inside two refusals that live elsewhere — a Polyjuice dose and the
     * Occlumency read — and a component that tried to own any of that would be a second place for
     * them to disagree. What is authored here is only how long it lasts.
     *
     * <p>No-ops for a non-player drinker. There is nothing to compel a cow to be honest about.
     */
    record TruthSerum(int durationTicks) implements BrewEffect {

        public static final MapCodec<TruthSerum> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.optionalFieldOf("durationTicks", VeritaserumService.DEFAULT_DURATION_TICKS)
                        .forGetter(TruthSerum::durationTicks)
        ).apply(inst, TruthSerum::new));

        @Override
        public Type type() {
            return Type.TRUTH_SERUM;
        }

        @Override
        public void apply(BrewEffectContext ctx) {
            if (!(ctx.drinker() instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }
            // Potency lengthens it like every other brewed duration. A better-brewed Veritaserum
            // holds somebody longer; it does not hold them harder, because there is no strength here
            // to raise — see VeritaserumState.
            VeritaserumService.dose(player, ctx.scaleDuration(durationTicks));
        }
    }

    /**
     * Show, not tell: a burst of the brew's own colour around whoever it happened to.
     *
     * <p>The one component with no mechanical effect at all, and it earns its place because every
     * other component is invisible. A potion that heals silently and a potion that does nothing look
     * identical, and a datapack author composing a new brew has no other way to make it feel like
     * something happened.
     */
    record Flourish(int count, Optional<Boolean> useBrewColor) implements BrewEffect {

        public static final MapCodec<Flourish> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.optionalFieldOf("count", 16).forGetter(Flourish::count),
                Codec.BOOL.optionalFieldOf("useBrewColor").forGetter(Flourish::useBrewColor)
        ).apply(inst, Flourish::new));

        @Override
        public Type type() {
            return Type.FLOURISH;
        }

        @Override
        public void apply(BrewEffectContext ctx) {
            LivingEntity drinker = ctx.drinker();
            int n = Math.max(1, Math.min(64, count));
            // Opaque: a DustParticleOptions colour with a zero alpha byte reads as "no colour" and
            // draws nothing, which is the silent-failure shape this repo has hit before on outlines.
            int rgb = useBrewColor.orElse(true) ? ctx.brew().color() & 0x00FFFFFF : 0x00FFFFFF;
            ctx.level().sendParticles(
                    new net.minecraft.core.particles.DustParticleOptions(0xFF000000 | rgb, 1.1f),
                    drinker.getX(), drinker.getY() + 1.0, drinker.getZ(), n, 0.35, 0.5, 0.35, 0.01);
        }
    }
}
