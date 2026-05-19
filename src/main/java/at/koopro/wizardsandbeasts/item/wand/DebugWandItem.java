package at.koopro.wizardsandbeasts.item.wand;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class DebugWandItem extends Item {

    public DebugWandItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;

        ServerLevel level = (ServerLevel) context.getLevel();
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;

        DebugWandState state = DebugWandState.get(player.getUUID());
        BlockPos target = context.getClickedPos().relative(context.getClickedFace());

        if (context.isSecondaryUseActive() && state.hasPreview()) {
            int count = state.confirm(level);
            player.displayClientMessage(Component.literal(
                    "\u00A7a\u2714 " + capitalize(state.getTreeType())
                            + " tree placed! (" + count + " blocks)"), true);
        } else {
            if (state.hasPreview()) {
                state.clearPreview(level);
            }
            boolean success = state.createPreview(level, target);
            if (success) {
                player.displayClientMessage(Component.literal(
                        "\u00A7e" + capitalize(state.getTreeType())
                                + " preview \u00A77| \u00A76Shift+click\u00A77 to confirm"), true);
            } else {
                player.displayClientMessage(Component.literal(
                        "\u00A7cFailed to preview tree here"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        DebugWandState state = DebugWandState.get(player.getUUID());

        if (player.isShiftKeyDown()) {
            if (state.hasPreview()) {
                state.clearPreview((ServerLevel) level);
                player.displayClientMessage(Component.literal(
                        "\u00A77Preview cleared"), true);
            }
        } else {
            String newType = state.cycleTreeType();
            if (state.hasPreview()) {
                state.clearPreview((ServerLevel) level);
            }
            player.displayClientMessage(Component.literal(
                    "\u00A7bTree: \u00A7f" + capitalize(newType)), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("\u00A77Right-click block: preview tree"));
        tooltipAdder.accept(Component.literal("\u00A77Shift+click block: confirm placement"));
        tooltipAdder.accept(Component.literal("\u00A77Right-click air: cycle tree type"));
        tooltipAdder.accept(Component.literal("\u00A77Shift+click air: clear preview"));
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
