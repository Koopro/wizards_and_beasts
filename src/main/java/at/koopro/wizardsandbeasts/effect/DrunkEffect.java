package at.koopro.wizardsandbeasts.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Three Firewhiskies in two minutes.
 *
 * <p>Carries the slowness as an attribute modifier — a wizard in this state is not going anywhere
 * quickly — and is read by {@code SpellCastGate} as a hard block on casting. Fifteen seconds of not
 * being able to draw on your wand is the whole penalty; the effect deliberately does nothing else,
 * because a drink that also damaged you would be a poison rather than a drink.
 */
public final class DrunkEffect extends MobEffect {

    /** Enough to be a stumble, well short of a stun. */
    private static final double SLOW = -0.45;

    /** Whisky amber. */
    private static final int COLOUR = 0xB5651D;

    public DrunkEffect() {
        super(MobEffectCategory.HARMFUL, COLOUR);
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                Identifier.fromNamespaceAndPath(
                        at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID, "drunk_slow"),
                SLOW,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
