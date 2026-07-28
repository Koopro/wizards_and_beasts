package at.koopro.wizardsandbeasts.item.wand;

import at.koopro.wizardsandbeasts.item.GeoItemBase;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.WandLoreNames;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import at.koopro.wizardsandbeasts.spell.cast.WandCastTiming;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic;
import at.koopro.wizardsandbeasts.util.ClientClassBridge;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.resonance.WandResonanceSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;

public class WandItem extends GeoItemBase {
    public WandItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = ClientClassBridge.instantiate(
                            "at.koopro.wizardsandbeasts.client.wand.WandRenderer",
                            GeoItemRenderer.class,
                            new Class<?>[0],
                            new Object[0]);
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<WandItem>("wand_controller", 0, state -> PlayState.STOP));
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return 1;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!WandModuleHooks.isWandsEnabled()) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            Optional<UUID> master = WandComponents.getMaster(stack);
            if (master.isEmpty()) {
                float score = WandResonanceSystem.computeResonance(player, stack, level.registryAccess());
                WandResonanceSystem.applyResonance(player, stack, score, level.registryAccess());
                return InteractionResult.SUCCESS;
            }
            if (master.get().equals(player.getUUID())) {
                // Spell system (future): dispatch cast event from here when not using beam channel.
            } else {
                player.displayClientMessage(Component.translatable("wandcraft.resonance.notYourWand"), true);
            }
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
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
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        super.onUseTick(level, entity, stack, remainingUseDuration);
        if (!level.isClientSide() && entity instanceof ServerPlayer sp) {
            WandBeamChannelLogic.tick(sp, stack);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide() && entity instanceof ServerPlayer sp) {
            int holdTicks = Math.max(0, getUseDuration(stack, entity) - timeLeft);
            WandCastTiming.recordRelease(sp, holdTicks);
            WandBeamChannelLogic.endChannel(sp);
        }
        if (level.isClientSide()) {
            ClientClassBridge.callStaticBoolean(
                    "at.koopro.wizardsandbeasts.client.wand.WandCastClient",
                    "castOrOpenImperioMenu");
        }
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return WandComponents.getMaster(stack).isPresent();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipAdder, TooltipFlag flag) {
        Identifier wood = WandComponents.getWood(stack);
        Identifier core = WandComponents.getCore(stack);
        WandFlexibility flexibility = WandComponents.getFlexibility(stack);
        Optional<UUID> master = WandComponents.getMaster(stack);

        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.wood",
                WandLoreNames.wood(context.registries(), wood)).withStyle(ChatFormatting.GOLD));
        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.core",
                WandLoreNames.core(context.registries(), core)).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.flexibility",
                flexibility == null ? "?" : flexibility.getSerializedName()).withStyle(ChatFormatting.GRAY));
        if (master.isPresent()) {
            tooltipAdder.accept(Component.translatable("wandcraft.tooltip.master", master.get().toString())
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.integrity", WandComponents.getIntegrity(stack))
                .withStyle(ChatFormatting.GREEN));
        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.corruption", WandComponents.getCorruption(stack))
                .withStyle(ChatFormatting.DARK_RED));
        Float len = WandComponents.getLength(stack);
        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.length_in", len == null ? 0.0f : len)
                .withStyle(ChatFormatting.BLUE));
        tooltipAdder.accept(Component.translatable("wandcraft.tooltip.allegiance", WandComponents.getAllegianceScore(stack))
                .withStyle(ChatFormatting.DARK_GREEN));
    }

    public static ItemStack createWand(WandWood wood, WandCore core, WandLength length, WandFlexibility flexibility) {
        ItemStack stack = new ItemStack(WandItemRegistry.WAND.get());
        if (wood != null) {
            stack.set(WandComponents.WAND_WOOD.get(), Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, wood.getSerializedName()));
        }
        if (core != null) {
            stack.set(WandComponents.WAND_CORE.get(), Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, core.getSerializedName()));
        }
        if (flexibility != null) {
            stack.set(WandComponents.WAND_FLEXIBILITY.get(), flexibility);
        }
        if (length != null) {
            float inches = 13.0f;
            switch (length) {
                case SHORT:
                    inches = 9.0f;
                    break;
                case MEDIUM:
                    inches = 11.0f;
                    break;
                case LONG:
                    inches = 15.0f;
                    break;
                case STANDARD:
                default:
                    inches = 13.0f;
                    break;
            }
            stack.set(WandComponents.WAND_LENGTH.get(), inches);
        }
        ModDataComponents.refreshElderWandMarker(stack);
        return stack;
    }
}
