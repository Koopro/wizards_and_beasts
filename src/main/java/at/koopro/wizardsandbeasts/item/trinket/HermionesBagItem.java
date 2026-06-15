package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class HermionesBagItem extends Item {

    public HermionesBagItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Undetectable Extension Charm. Featherlight Charm.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("It weighs nothing. It contains everything.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_PURPLE));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new HermionesBagMenu(id, inv, hand),
                    Component.translatable("container.wizards_and_beasts.hermiones_bag"));
            serverPlayer.openMenu(provider, buf -> buf.writeEnum(hand));
        }
        return InteractionResult.SUCCESS;
    }
}
