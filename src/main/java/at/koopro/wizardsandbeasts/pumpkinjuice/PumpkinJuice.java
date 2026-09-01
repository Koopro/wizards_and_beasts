package at.koopro.wizardsandbeasts.pumpkinjuice;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A glass of pumpkin juice: the school drink, as against Butterbeer's pub one.
 *
 * <p>Both restore a little and both grant sixty seconds of something pleasant, and they are
 * deliberately not interchangeable. Butterbeer is warmth, calm and a soft view — being comfortable.
 * This is a clear head: it shakes off a minor ailment and makes the next hour of lessons and
 * duelling practice count for slightly more. A player choosing between them is choosing between
 * being cosy and being sharp.
 *
 * <h2>Clearing one thing, not everything</h2>
 * Milk strips every effect a player has, good and bad, instantly, for free. That is a blunt
 * instrument and it is the reason nobody drinks anything else in a fight. This clears <em>one</em>
 * harmful effect, at random, and only ones that are both amplifier 0 and not on the
 * {@link #CANNOT_CLEAR} list — so it shrugs off a stray Slowness and does nothing at all about a
 * Wither, a Poison II, or anything the mod considers serious.
 */
@NullMarked
public final class PumpkinJuice {

    /** Hunger restored, as briefed. */
    public static final int NUTRITION = 4;
    /** Saturation restored, as briefed. */
    public static final float SATURATION = 0.6f;

    /** How long the clear head lasts. */
    public static final int COMFORT_TICKS = 1200;   // 60s

    /** How long the orange trail follows a walker. */
    public static final int TRAIL_TICKS = 200;      // 10s

    /** Before another glass. Enough to stop it being spammed, short enough to be a drink. */
    public static final int COOLDOWN_TICKS = 300;   // 15s

    /** The highest amplifier this will touch. Anything stronger is a real problem, not an ailment. */
    public static final int MAX_CLEARABLE_AMPLIFIER = 0;

    /**
     * Effects a glass of juice has no business curing, whatever their amplifier.
     *
     * <p>A tag rather than a list in code: which ailments are "serious" is a balance judgement that a
     * pack should be able to disagree with, and hard-coding Poison here would mean a modpack could
     * never make its own venom uncurable.
     */
    public static final TagKey<MobEffect> CANNOT_CLEAR = TagKey.create(
            Registries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "pumpkin_juice_cannot_clear"));

    private PumpkinJuice() {}

    /** Whether a single held effect is the kind a glass of juice can shake off. */
    public static boolean isClearable(MobEffectInstance instance) {
        if (instance.getAmplifier() > MAX_CLEARABLE_AMPLIFIER) {
            return false;
        }
        Holder<MobEffect> effect = instance.getEffect();
        if (effect.value().getCategory() != MobEffectCategory.HARMFUL) {
            return false;
        }
        return !effect.is(CANNOT_CLEAR);
    }

    /**
     * Picks one clearable effect at random, or {@code null} when the drinker has none.
     *
     * <p>Random rather than "the worst one": choosing the most severe would make this a targeted
     * cure and put it back in milk's territory. Taking pot luck is what keeps it a drink.
     */
    public static @Nullable Holder<MobEffect> pickClearable(LivingEntity drinker, RandomSource random) {
        List<Holder<MobEffect>> clearable = new ArrayList<>();
        for (MobEffectInstance instance : drinker.getActiveEffects()) {
            if (isClearable(instance)) {
                clearable.add(instance.getEffect());
            }
        }
        if (clearable.isEmpty()) {
            return null;
        }
        return clearable.get(random.nextInt(clearable.size()));
    }

    /**
     * Clears one minor ailment.
     *
     * @return the effect that was cleared, or {@code null} if there was nothing to clear
     */
    public static @Nullable Holder<MobEffect> clearOne(LivingEntity drinker, RandomSource random) {
        Holder<MobEffect> chosen = pickClearable(drinker, random);
        if (chosen != null) {
            drinker.removeEffect(chosen);
        }
        return chosen;
    }
}
