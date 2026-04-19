package at.koopro.neo.item.wizarding;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ChocolateFrogItem extends Item {
    public ChocolateFrogItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            player.getFoodData().eat(2, 0.4f);
            ItemStack card = FamousWizardCardItem.randomCard(level);
            if (!player.getInventory().add(card)) {
                player.drop(card, false);
            }
            stack.consume(1, player);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.SUCCESS;
    }
}