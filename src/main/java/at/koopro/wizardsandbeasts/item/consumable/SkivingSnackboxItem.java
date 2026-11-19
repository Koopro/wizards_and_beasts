package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.util.InventoryHandover;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.function.Supplier;

/** A Skiving Snackbox: opened, it hands over one of each two-ended sweet inside. */
@NullMarked
public class SkivingSnackboxItem extends Item {

    private final Supplier<List<? extends Item>> contents;

    public SkivingSnackboxItem(Properties properties, Supplier<List<? extends Item>> contents) {
        super(properties);
        this.contents = contents;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        for (Item sweet : contents.get()) {
            InventoryHandover.giveOrDrop(player, new ItemStack(sweet));
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8f, 1.2f);
        player.awardStat(Stats.ITEM_USED.get(this));
        player.getItemInHand(hand).consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
