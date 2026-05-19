package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class ReducioEffect extends MobEffect {
    public ReducioEffect() {
        super(MobEffectCategory.NEUTRAL, 0xD4AF37);
        addAttributeModifier(Attributes.SCALE,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "effect/reducio_scale"),
                -0.5D,
                AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
