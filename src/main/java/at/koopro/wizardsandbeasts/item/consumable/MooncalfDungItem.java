package at.koopro.wizardsandbeasts.item.consumable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class MooncalfDungItem extends Item {
    public MooncalfDungItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        if (BoneMealItem.applyBonemeal(stack, level, pos, context.getPlayer())) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
