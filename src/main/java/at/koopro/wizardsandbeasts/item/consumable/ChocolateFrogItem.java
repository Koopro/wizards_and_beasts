package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.item.trinket.FamousWizardCardItem;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public class ChocolateFrogItem extends Item {
    public ChocolateFrogItem(Properties properties) {
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
        return 18;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.EAT;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof Player player) {
            player.getFoodData().eat(3, 0.45f);
            ItemStack card = FamousWizardCardItem.randomCard(level);
            if (!player.getInventory().add(card)) {
                player.drop(card, false);
            }
            stack.consume(1, player);
            player.getCooldowns().addCooldown(stack, 10);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return stack;
    }
}