package at.koopro.wizardsandbeasts.item.darkartefact;

import at.koopro.wizardsandbeasts.diary.DiaryService;
import at.koopro.wizardsandbeasts.network.ClientScreenHooksInvoker;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The only horcrux that answers back. A ruined diary still opens, but nothing writes in it.
 */
@NullMarked
public class RiddlesDiaryItem extends HorcruxItem {

    public RiddlesDiaryItem(Properties properties) {
        super(properties, "Basilisk fang, 1993");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("T.M. Riddle — Property of")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("1943")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (!isSoulIntact(stack)) {
            tooltipAdder.accept(soulDestroyed());
        }
        Optional<UUID> possessing = stack.getOrDefault(ModDataComponents.DIARY_POSSESSING.get(), Optional.empty());
        if (possessing.isPresent()) {
            tooltipAdder.accept(Component.literal("[Influencing: " + possessing.get() + "]")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        tooltipAdder.accept(Component.literal("Source: Chamber of Secrets")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
    }

    @Override
    protected InteractionResult onDarkArtsUse(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isSoulIntact(stack)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.literal("The diary is ruined — no voice answers.")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            DiaryService.tryOpen(serverPlayer, stack);
        } else if (level.isClientSide()) {
            ClientScreenHooksInvoker.invoke("openDiaryWriteScreen");
        }
        return InteractionResult.SUCCESS;
    }
}
