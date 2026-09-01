package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * An empty Butterbeer mug. Worth keeping, because it is worth refilling.
 *
 * <p>Right-click any block in {@value #TAG_PATH} and it fills. Which blocks those are is a datapack
 * decision — the mod ships the brass cauldron in it, which is the tavern-shaped one and the cheapest
 * to build, and leaves the copper and pewter cauldrons alone so a brewer's kit does not double as a
 * tap.
 *
 * <p>The refill is free, and that is not an oversight: an inn's cauldron of Butterbeer is exactly a
 * free refill, and the drink is already rate-limited by {@code Butterbeer}'s two-minute window
 * rather than by scarcity. What the mug buys the player is not having to carry a stack.
 */
@NullMarked
public class EmptyButterbeerMugItem extends Item {

    static final String TAG_PATH = "butterbeer_source";

    /** Blocks that will fill a mug. */
    public static final TagKey<Block> BUTTERBEER_SOURCE = TagKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, TAG_PATH));

    public EmptyButterbeerMugItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.getBlockState(context.getClickedPos()).is(BUTTERBEER_SOURCE)) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack mug = context.getItemInHand();
        ItemStack full = new ItemStack(ConsumableItemRegistry.BUTTERBEER.get());
        mug.shrink(1);
        if (mug.isEmpty()) {
            player.setItemInHand(context.getHand(), full);
        } else if (!player.getInventory().add(full)) {
            player.drop(full, false);
        }

        level.playSound(null, context.getClickedPos(), ModSounds.BUTTERBEER_FIZZ.get(),
                SoundSource.BLOCKS, 0.8f, 0.9f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.empty_butterbeer_mug.refill")
                .withStyle(ChatFormatting.GRAY));
    }
}
