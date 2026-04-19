package at.koopro.neo.item.wizarding;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BertieBottsBeansItem extends Item {
    public BertieBottsBeansItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            player.getFoodData().eat(1, 0.2f);
            if (level.random.nextBoolean()) {
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 1, 0));
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 300, 0));
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 160, 0));
            }
            stack.consume(1, player);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.SUCCESS;
    }
}
