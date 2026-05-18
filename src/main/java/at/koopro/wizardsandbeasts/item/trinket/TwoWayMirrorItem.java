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

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class TwoWayMirrorItem extends Item {

    public TwoWayMirrorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Speak the other's name to open the connection.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        Optional<UUID> pairId = stack.getOrDefault(ModDataComponents.MIRROR_PAIR_ID.get(), Optional.empty());
        Optional<String> recipientName = stack.getOrDefault(ModDataComponents.MIRROR_RECIPIENT_NAME.get(), Optional.empty());
        if (pairId.isPresent()) {
            String display2 = recipientName.orElse(pairId.get().toString());
            tooltipAdder.accept(Component.literal("Paired with: " + display2)
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltipAdder.accept(Component.literal("[Unpaired — find its twin]")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.WANDS)) {
            return InteractionResult.FAIL;
        }
        // TODO: [communication] open mirror channel — requires paired UUID and online recipient
        return InteractionResult.PASS;
    }
}
