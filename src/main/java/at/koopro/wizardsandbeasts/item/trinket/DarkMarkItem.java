package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * The Dark Mark brand. Use on yourself to take the Mark; use on another player to brand them;
 * sneak-use while marked to call every marked wizard online — the Mark burns and announces the
 * caller's location, the summons Voldemort used to gather his Death Eaters.
 * <p>
 * The marked state is the {@link ModAttachments#DARK_MARK} attachment. Gated behind
 * {@link Module#DARK_ARTS}.
 */
public class DarkMarkItem extends Item {

    public DarkMarkItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("The Dark Mark. Burned into the left forearm.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("It burned when He was near.")
                .withStyle(ChatFormatting.DARK_RED));
        tooltipAdder.accept(Component.literal("Sneak-use to call the marked").withStyle(ChatFormatting.BLUE));
        tooltipAdder.accept(Component.literal("Source: Goblet of Fire onward")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (target instanceof ServerPlayer victim) {
            if (victim.getData(ModAttachments.DARK_MARK.get())) {
                player.displayClientMessage(Component.literal(victim.getName().getString() + " already bears the Mark.")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
                return InteractionResult.SUCCESS;
            }
            victim.setData(ModAttachments.DARK_MARK.get(), true);
            victim.displayClientMessage(Component.literal("The Dark Mark burns into your skin.")
                    .withStyle(ChatFormatting.DARK_RED), false);
            player.displayClientMessage(Component.literal("You brand " + victim.getName().getString() + " with the Dark Mark.")
                    .withStyle(ChatFormatting.DARK_RED), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer caster) || caster.level().getServer() == null) {
            return InteractionResult.PASS;
        }
        boolean marked = caster.getData(ModAttachments.DARK_MARK.get());

        if (caster.isShiftKeyDown()) {
            if (!marked) {
                caster.displayClientMessage(Component.literal("You bear no Mark to call through.")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
                return InteractionResult.SUCCESS;
            }
            int called = 0;
            for (ServerPlayer other : caster.level().getServer().getPlayerList().getPlayers()) {
                if (other != caster && other.getData(ModAttachments.DARK_MARK.get())) {
                    other.displayClientMessage(Component.literal("The Dark Mark burns — you are summoned. ("
                            + caster.getName().getString() + " at "
                            + caster.getBlockX() + ", " + caster.getBlockY() + ", " + caster.getBlockZ() + ")")
                            .withStyle(ChatFormatting.DARK_RED), false);
                    called++;
                }
            }
            caster.displayClientMessage(Component.literal("You call through the Mark. " + called
                    + (called == 1 ? " answers." : " answer.")).withStyle(ChatFormatting.DARK_PURPLE), true);
            return InteractionResult.SUCCESS;
        }

        if (marked) {
            caster.displayClientMessage(Component.literal("The Mark is already yours.")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return InteractionResult.SUCCESS;
        }
        caster.setData(ModAttachments.DARK_MARK.get(), true);
        caster.displayClientMessage(Component.literal("You take the Dark Mark.")
                .withStyle(ChatFormatting.DARK_RED), false);
        return InteractionResult.SUCCESS;
    }
}
