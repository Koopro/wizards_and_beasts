package at.koopro.wizardsandbeasts.chocolate;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a Chocolate Frog is worth, and everything it might do instead.
 *
 * <p>Chocolate is what Lupin hands out on the train, and it is the one thing in the books that
 * actually helps after a Dementor. That is the whole of the anti-Dementor rule here: it clears the
 * despair, and for half a minute afterwards the despair cannot get back in. Not resistance to
 * Dementors — resistance to the <em>feeling</em>, which is what chocolate is for.
 *
 * <p>And the frog might not be there when you go to eat it. That is not a punishment mechanic; it is
 * the joke the item is named after, and it is why the odds are low and the reward for chasing it is
 * simply getting your snack back.
 */
@NullMarked
public final class ChocolateFrog {

    /** Hunger and saturation, unchanged from what the item already restored. */
    public static final int NUTRITION = 3;
    public static final float SATURATION = 0.45f;

    /** How long the despair cannot come back. */
    public static final int WARD_TICKS = 600;      // 30s

    /** Chance a frog springs out of your hand instead of being eaten. */
    public static final float ESCAPE_CHANCE = 0.15f;

    /** How long an escaped frog stays catchable, in ticks. */
    public static final int ESCAPE_MIN_TICKS = 100;   // 5s
    public static final int ESCAPE_MAX_TICKS = 160;   // 8s

    /**
     * What a Chocolate Frog talks you out of.
     *
     * <p>A tag rather than a list in code: the mod's own despair effects belong here, and so do the
     * wither and mining fatigue the dark creatures inflict — but which of those a pack considers
     * "the sort of thing chocolate helps with" is a judgement, and a pack that adds its own dread
     * aura should be able to join without this class knowing about it.
     */
    public static final TagKey<MobEffect> CURES = TagKey.create(
            Registries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "chocolate_frog_cures"));

    private ChocolateFrog() {}

    /** Whether the frog gets away this time. */
    public static boolean escapes(RandomSource random) {
        return random.nextFloat() < ESCAPE_CHANCE;
    }

    /** How long a particular escapee hops around for. */
    public static int escapeLifetime(RandomSource random) {
        return ESCAPE_MIN_TICKS + random.nextInt(ESCAPE_MAX_TICKS - ESCAPE_MIN_TICKS + 1);
    }

    /** Whether this effect is one chocolate answers. */
    public static boolean isDespair(Holder<MobEffect> effect) {
        return effect.is(CURES);
    }

    /**
     * Clears every despair the eater is carrying.
     *
     * <p>All of them, not one at random — that is pumpkin juice's job and the two should not overlap.
     * Chocolate after a Dementor is meant to work.
     *
     * @return how many effects were cleared
     */
    public static int clearDespair(LivingEntity eater) {
        List<Holder<MobEffect>> found = new ArrayList<>();
        for (MobEffectInstance instance : eater.getActiveEffects()) {
            if (isDespair(instance.getEffect())) {
                found.add(instance.getEffect());
            }
        }
        // Collected first: removeEffect mutates the collection getActiveEffects is a view of.
        for (Holder<MobEffect> effect : found) {
            eater.removeEffect(effect);
        }
        return found.size();
    }
}
