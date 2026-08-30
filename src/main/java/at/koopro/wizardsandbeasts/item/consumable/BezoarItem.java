package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.spell.petrify.PetrifyServerLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public class BezoarItem extends ConsumedItem {
    public BezoarItem(Properties properties) {
        super(properties, 28, ItemUseAnimation.DRINK);
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        player.removeAllEffects();
        if (level instanceof ServerLevel serverLevel) {
            PetrifyServerLogic.partialCure(serverLevel, player);
        }
        stack.consume(1, player);
        player.getCooldowns().addCooldown(stack, 200);
    }
}
