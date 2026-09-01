package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.brew.CauldronBrewing;
import at.koopro.wizardsandbeasts.brew.SilverRefining;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * The shell an Occamy hatched out of: pure silver, thin as paper, and worth more in a cauldron than
 * on a shelf.
 *
 * <p>Two uses, and which one you get is decided by what you click:
 *
 * <ul>
 *   <li><b>On a cauldron</b> that is brewing something silver-based, the shell is a catalyst — the
 *       whole batch refines into its pure-silver variant. See {@link SilverRefining}.</li>
 *   <li><b>On anything else</b> it sets down as a small ornament, like an egg.</li>
 * </ul>
 *
 * <p>The cauldron branch is checked <em>before</em> {@link BlockItem}'s placement, because otherwise
 * clicking a cauldron would put a shell on the rim and the catalyst would be unreachable except by
 * standing on the pot.
 *
 * <p><b>Fragile.</b> Sixteen to a stack and they do not survive a bad landing — see
 * {@code OccamyEggshellFragility}, which is a separate handler because the breakage is caused by the
 * player's fall rather than by anything the item does.
 */
@NullMarked
public class OccamyEggshellItem extends BlockItem {

    public OccamyEggshellItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (!CauldronBrewing.isCauldron(level.getBlockState(pos).getBlock())) {
            return super.useOn(context);
        }
        // Client predicts the swing; the refinement is server state and is decided there.
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        SilverRefining.Result result = SilverRefining.refine(level, pos);
        if (context.getPlayer() instanceof ServerPlayer player) {
            PlayerFeedback.actionBar(player, result.message().copy()
                    .withStyle(result.refined() ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        }
        if (!result.refined()) {
            // Nothing is spent on a refusal. A shell this rare must not be eaten by a mistimed click.
            return InteractionResult.FAIL;
        }
        context.getItemInHand().consume(1, context.getPlayer());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.occamy_eggshell.use")
                .withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.occamy_eggshell.fragile")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
