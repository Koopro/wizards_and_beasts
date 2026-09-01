package at.koopro.wizardsandbeasts.comfort;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import org.jspecify.annotations.NullMarked;

/**
 * What a slice of treacle tart is worth when things are bad.
 *
 * <p>Not a combat buff and deliberately not much of a heal — one heart spread across a minute and a
 * half is slower than eating almost anything else. What it actually does is take the edge off fear:
 * a Dementor's chill, a soul-drain, the dread a dark creature leaves behind, all thirty percent
 * shorter while the comfort lasts.
 *
 * <p>That is the difference between this and a Chocolate Frog. Chocolate is <em>first aid</em> — it
 * clears the despair outright and locks it out. This is comfort food: it does not stop anything
 * happening, it just makes it pass sooner. Both are worth carrying and neither replaces the other.
 */
@NullMarked
public final class HomeComfort {

    /** How long the comfort lasts. */
    public static final int DURATION_TICKS = 1800;   // 90s

    /**
     * How much fear is shortened while it holds.
     *
     * <p>Applied to the incoming effect at the moment it lands, not to one already running: a comfort
     * eaten <em>after</em> the fright does not retroactively shorten it, which is the right way round
     * and also the only way that does not need a per-tick sweep.
     */
    public static final float FEAR_SCALE = 0.7f;      // -30%

    /** Total healing over the whole duration, in half-hearts. One heart, very slowly. */
    public static final float TOTAL_HEAL = 2.0f;

    /** Ticks between heartbeats of healing. Nine bites of a heart across ninety seconds. */
    public static final int HEAL_INTERVAL = 200;

    /** Healing per beat, sized so the whole duration adds up to {@link #TOTAL_HEAL}. */
    public static final float HEAL_PER_BEAT = TOTAL_HEAL / (DURATION_TICKS / (float) HEAL_INTERVAL);

    /**
     * What counts as fear or despair.
     *
     * <p>Its own tag rather than reusing {@code chocolate_frog_cures}. The two overlap heavily and
     * still mean different things: chocolate answers the dark-creature ailments as a whole, where
     * this is specifically about dread — and a pack should be able to say a curse is curable by
     * chocolate without also saying a slice of tart makes it pass faster.
     */
    public static final TagKey<MobEffect> FEAR = TagKey.create(
            Registries.MOB_EFFECT,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "fear_and_despair"));

    private HomeComfort() {}

    /** The duration a fear effect should actually land with, given {@code base}. */
    public static int shorten(int base) {
        if (base <= 0) {
            return base;
        }
        return Math.max(1, (int) Math.floor(base * FEAR_SCALE));
    }
}
