package at.koopro.wizardsandbeasts.item.darkartefact;

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

public class RiddlesDiaryItem extends Item implements IHorcruxVessel {

    public RiddlesDiaryItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isSoulIntact(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SOUL_FRAGMENT_INTACT.get(), true);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isSoulIntact(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("T.M. Riddle — Property of")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("1943")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (!isSoulIntact(stack)) {
            tooltipAdder.accept(Component.literal("[Destroyed — Basilisk fang, 1993]")
                    .withStyle(ChatFormatting.STRIKETHROUGH, ChatFormatting.DARK_RED));
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
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        // TODO: [dark_arts] possession mechanic — write into diary opens dialogue channel
        return InteractionResult.PASS;
    }
}
