package at.koopro.wizardsandbeasts.whizzbee;

import org.jspecify.annotations.NullMarked;

/**
 * Eight seconds of not quite touching the ground.
 *
 * <p><b>Not just Jump Boost.</b> Jump Boost alone is a bigger hop and then the same fall; the joke
 * here is the float — you go up further, you come down slowly, and every jump while you are fizzing
 * gives you another kick on the way. The three together are what make it feel like being carried by
 * a sweet rather than like drinking a potion.
 *
 * <p>The extra kick is deliberately additive to the jump rather than a replacement for it: a jump
 * that <em>set</em> your velocity would ignore Jump Boost, and the two are supposed to compound.
 */
@NullMarked
public final class Whizzbee {

    /** How long it fizzes. */
    public static final int DURATION_TICKS = 160;   // 8s

    /** Jump Boost II, as briefed. Amplifier 1 is level II. */
    public static final int JUMP_AMPLIFIER = 1;

    /**
     * The whizz: extra upward velocity added on top of each jump.
     *
     * <p>Small. A vanilla jump is about 0.42, so this is roughly a third again on top of whatever
     * Jump Boost already gave — noticeable as a little kick, nowhere near a second jump.
     */
    public static final double WHIZZ_BOOST = 0.14;

    /** Ticks between fizz puffs at the feet. */
    public static final int FIZZ_INTERVAL = 2;

    private Whizzbee() {}

    /**
     * The vertical velocity a jump should end up at.
     *
     * <p>Pure, and additive on purpose: {@code jumped + WHIZZ_BOOST}, never a fixed value. Setting it
     * outright would throw away Jump Boost's contribution and make the sweet worse the better your
     * other buffs were.
     */
    public static double whizz(double jumpedVelocity) {
        return jumpedVelocity + WHIZZ_BOOST;
    }
}
