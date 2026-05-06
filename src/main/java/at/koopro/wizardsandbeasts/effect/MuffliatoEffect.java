package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class MuffliatoEffect extends MobEffect {
    public MuffliatoEffect() {
        super(MobEffectCategory.NEUTRAL, 0xD4AF37);
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }
}
