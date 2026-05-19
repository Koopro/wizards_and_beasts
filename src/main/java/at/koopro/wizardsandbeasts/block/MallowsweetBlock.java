package at.koopro.wizardsandbeasts.block;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.FlowerBlock;

public class MallowsweetBlock extends FlowerBlock {
    public MallowsweetBlock(Properties properties) {
        super(MobEffects.REGENERATION, 4, properties);
    }
}
