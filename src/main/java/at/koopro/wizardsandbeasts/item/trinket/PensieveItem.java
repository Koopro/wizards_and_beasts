package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class PensieveItem extends Item {

    public PensieveItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("A wide stone basin. Extract memories with a wand to the temple.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        int memories = stack.getOrDefault(ModDataComponents.PENSIEVE_MEMORIES_STORED.get(), 0);
        tooltipAdder.accept(Component.literal("Memories stored: " + memories)
                .withStyle(ChatFormatting.AQUA));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            at.koopro.wizardsandbeasts.network.trinket.PensieveOpenS2CPayload.open(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
