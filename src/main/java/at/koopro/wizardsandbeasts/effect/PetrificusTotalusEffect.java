package at.koopro.wizardsandbeasts.effect;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class PetrificusTotalusEffect extends MobEffect {
    public PetrificusTotalusEffect() {
        super(MobEffectCategory.HARMFUL, 0x4A6080);
        addAttributeModifier(Attributes.MOVEMENT_SPEED,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "effect/petrificus_totalus_speed"),
                -1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "effect/petrificus_totalus_attack"),
                -1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }
}
