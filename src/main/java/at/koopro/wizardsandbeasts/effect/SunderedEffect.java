package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * "Sundered" — armor shredded by a severing charm (Diffindo). Cuts the victim's armor value so
 * follow-up hits bite harder. A multiplicative reduction so it scales with however armored the
 * target is rather than a flat point removal.
 */
public final class SunderedEffect extends MobEffect {
    public SunderedEffect() {
        super(MobEffectCategory.HARMFUL, 0xA83232);
        addAttributeModifier(Attributes.ARMOR,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "effect/sundered_armor"),
                -0.40D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }
}
