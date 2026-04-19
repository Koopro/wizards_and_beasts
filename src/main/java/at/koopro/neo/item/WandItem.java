package at.koopro.neo.item;

import at.koopro.neo.client.wand.WandRenderer;
import at.koopro.neo.registry.ModDataComponents;
import at.koopro.neo.item.wand.WandCore;
import at.koopro.neo.item.wand.WandFlexibility;
import at.koopro.neo.item.wand.WandLength;
import at.koopro.neo.item.wand.WandWood;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import at.koopro.neo.network.SpellCastC2SPacket;

import java.util.function.Consumer;

public class WandItem extends GeoItemBase {

    public WandItem(Properties properties) {
        super(properties);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private WandRenderer renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null)
                    this.renderer = new WandRenderer();
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<WandItem>(
                "wand_controller", 0,
                state -> PlayState.STOP));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide()) {
            ClientPacketDistributor.sendToServer(new SpellCastC2SPacket());
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        WandWood wood = stack.get(ModDataComponents.WAND_WOOD.get());
        WandCore core = stack.get(ModDataComponents.WAND_CORE.get());
        WandLength length = stack.get(ModDataComponents.WAND_LENGTH.get());
        WandFlexibility flex = stack.get(ModDataComponents.WAND_FLEXIBILITY.get());

        if (wood != null) {
            tooltipAdder.accept(Component.literal(wood.getDisplayName() + " Wood")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (core != null) {
            tooltipAdder.accept(Component.literal(core.getDisplayName() + " Core")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (length != null) {
            tooltipAdder.accept(Component.literal(length.getDisplayName() + " Length")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (flex != null) {
            tooltipAdder.accept(Component.literal(flex.getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    public static ItemStack createWand(WandWood wood, WandCore core, WandLength length, WandFlexibility flexibility) {
        ItemStack stack = new ItemStack(at.koopro.neo.registry.ModItems.WAND.get());
        stack.set(ModDataComponents.WAND_WOOD.get(), wood);
        stack.set(ModDataComponents.WAND_CORE.get(), core);
        stack.set(ModDataComponents.WAND_LENGTH.get(), length);
        stack.set(ModDataComponents.WAND_FLEXIBILITY.get(), flexibility);
        return stack;
    }
}
