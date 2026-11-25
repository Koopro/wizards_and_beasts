package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Amortentia: you cannot bring yourself to raise a hand to anyone.
 *
 * <p>Amortentia used to be Hero of the Village plus Glowing, which is a reputation buff wearing the
 * name of the most powerful love potion in the world. What it does now is the one mechanical thing
 * infatuation reliably means: the drinker's blows against other players simply do not land. Read by
 * {@code InfatuationHandler}.
 *
 * <h2>Aimed, or not</h2>
 * <p>In canon the obsession has an object: Romilda's chocolates were meant for Harry (HBP ch.18). A bottle pressed to
 * someone takes one of their hairs and is brewed for them; its drinker cannot strike that one person and nobody else
 * is spared. An unaimed bottle stays honest about being broad: while it lasts the drinker is harmless to every
 * person. Both live in {@code brew.amortentia.Amortentia} (systems audit C1, 2026-11-25).
 *
 * <h2>What it does not touch</h2>
 * <p>Movement, camera, chat and inventory are all left alone. A potion that steered a player's body
 * would be Imperio, which this mod already has, gates behind the Unforgivables, and treats as a
 * crime. Amortentia is not that, and making it feel like that would be both wrong about the fiction
 * and a much worse thing to be able to slip into a drink.
 *
 * <p>Mobs are untouched: the drinker can still defend themselves against anything that is not a
 * person, so a dose is a social disaster rather than a death sentence.
 */
public final class InfatuationEffect extends MobEffect {

    /** Mother-of-pearl pink, matching Amortentia's own sheen. */
    private static final int COLOUR = 0xE59FA7;

    public InfatuationEffect() {
        super(MobEffectCategory.HARMFUL, COLOUR);
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }
}
