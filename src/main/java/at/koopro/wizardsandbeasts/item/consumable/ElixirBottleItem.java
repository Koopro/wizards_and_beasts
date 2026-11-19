package at.koopro.wizardsandbeasts.item.consumable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;


/**
 * A bottled draught of the Elixir of Life, drawn from the Stone.
 *
 * <p>The same draught the Philosopher's Stone gives ({@link ElixirOfLife}): a day in which death is held
 * off once. It keeps the Stone's one-draught-a-day rule, so a crate of bottles is a supply for later
 * days, never a stack of lives for today. Refused bottles are kept.
 */
@NullMarked
public class ElixirBottleItem extends ConsumedItem {

    public ElixirBottleItem(Properties properties) {
        super(properties, 32, ItemUseAnimation.DRINK);
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!ElixirOfLife.canDrink(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.translatable("item.wizards_and_beasts.philosophers_stone.wait")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ElixirOfLife.drink(serverPlayer);
        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS,
                0.8f, 1.4f);
        serverPlayer.displayClientMessage(Component.translatable("item.wizards_and_beasts.philosophers_stone.drunk")
                .withStyle(ChatFormatting.GOLD), true);
        stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

}
