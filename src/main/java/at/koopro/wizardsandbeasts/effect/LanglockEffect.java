package at.koopro.wizardsandbeasts.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class LanglockEffect extends MobEffect {
    public LanglockEffect() {
        super(MobEffectCategory.HARMFUL, 0x6A0DAD);
    }

    @Override
    public boolean isBeneficial() {
        return false;
    }
}
