package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class LumosFieldEffect extends MobEffect {
    public LumosFieldEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFF8E7);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
