package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Being partly made of shadow: quick, hard to see, and easy to burn.
 *
 * <p>The speed lives here as an attribute modifier rather than as a second Speed instance inside the
 * brew, so the boost begins and ends exactly with the light vulnerability that pays for it — a brew
 * that handed out plain Swiftness alongside this could be half-cleansed into pure upside.
 *
 * <p>The other two halves deliberately live elsewhere: the concealment is a per-tick flag
 * ({@code ShadowFormHandler}) because vanilla resets it every {@code aiStep}, and the twenty-percent
 * light tax is a damage hook ({@code ShadowForm}). A {@link MobEffect} is the wrong shape for either.
 */
public final class ShadowFormEffect extends MobEffect {

    /** Movement speed added. Noticeable; well short of a Swiftness potion. */
    private static final double SPEED_BONUS = 0.12;

    /** Deep violet-black. */
    private static final int COLOUR = 0x2B2136;

    public ShadowFormEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "shadow_form_speed"),
                SPEED_BONUS,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /**
     * Beneficial on balance — you drank it on purpose — despite carrying a real downside.
     *
     * <p>The category decides milk-bucket behaviour and the HUD tint. Calling it harmful would let a
     * cleanse strip the vulnerability while leaving the speed, which is the wrong way round.
     */
    @Override
    public boolean isBeneficial() {
        return true;
    }
}
