package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class JellyLegsEffect extends MobEffect {
    public JellyLegsEffect() {
        super(MobEffectCategory.HARMFUL, 0x4A6080);
        addAttributeModifier(Attributes.MOVEMENT_SPEED,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "effect/jelly_legs_speed"),
                -0.4D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "effect/jelly_legs_jump"),
                -1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }
}
