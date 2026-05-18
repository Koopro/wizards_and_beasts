package at.koopro.wizardsandbeasts.item.consumable;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public class BertieBottsBeansItem extends Item {
    public BertieBottsBeansItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 16;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.EAT;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof Player player) {
            player.getFoodData().eat(1, 0.2f);
            if (level.random.nextBoolean()) {
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 2, 0));
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 120, 0));
            } else {
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0));
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            }
            stack.consume(1, player);
            player.getCooldowns().addCooldown(stack, 8);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return stack;
    }
}
