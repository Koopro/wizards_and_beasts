package at.koopro.neo.item.wizarding;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DittanyItem extends Item {
    public DittanyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            player.heal(8.0f);
            stack.consume(1, player);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.SUCCESS;
    }
}
