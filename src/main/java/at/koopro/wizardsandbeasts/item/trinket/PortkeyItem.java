package at.koopro.wizardsandbeasts.item.trinket;

import at.koopro.wizardsandbeasts.item.AnimatedItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.portkey.PortkeyService;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A Portkey. Sneak-use on a block sets where it goes; hold it (everyone who wants to come stands close) and after the
 * tug it carries you all there and is spent — see {@link PortkeyService}.
 */
public class PortkeyItem extends Item implements AnimatedItem {

    /** "Three... two... one..." — the moment everyone has to be holding on. */
    public static final int TUG_TICKS = 60;

    public PortkeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown() || !ModuleManager.isEnabled(Module.ARTEFACTS)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            PortkeyService.set(context.getItemInHand(), serverLevel, context.getClickedPos());
            serverPlayer.displayClientMessage(Component.translatable("item.wizards_and_beasts.portkey.set")
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.ARTEFACTS) || player.isShiftKeyDown()
                || player.getItemInHand(hand).get(ModDataComponents.PORTKEY_TARGET.get()) == null) {
            return InteractionResult.PASS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return TUG_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player && ModuleManager.isEnabled(Module.ARTEFACTS)) {
            PortkeyService.travel(player, stack);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltipAdder, flag);
        var target = stack.get(ModDataComponents.PORTKEY_TARGET.get());
        if (target != null) {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.portkey.linked",
                            target.getX(), target.getY(), target.getZ())
                    .withStyle(ChatFormatting.DARK_AQUA));
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.portkey.rule")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.portkey.unlinked").withStyle(ChatFormatting.GRAY));
        }
    }
}
