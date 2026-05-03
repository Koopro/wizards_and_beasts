package at.koopro.wizardsandbeasts.item;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.wand.WandRenderer;
import at.koopro.wizardsandbeasts.item.wand.WandCore;
import at.koopro.wizardsandbeasts.item.wand.WandFlexibility;
import at.koopro.wizardsandbeasts.item.wand.WandLength;
import at.koopro.wizardsandbeasts.item.wand.WandWood;
import at.koopro.wizardsandbeasts.network.SpellCastC2SPacket;
import at.koopro.wizardsandbeasts.registry.ModItems;
import at.koopro.wizardsandbeasts.spell.WandBeamChannelLogic;
import at.koopro.wizardsandbeasts.wand.WandComponents;
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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class WandItem extends GeoItemBase {
    public WandItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private WandRenderer renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new WandRenderer();
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
            WandBeamChannelLogic.endChannel(sp);
        }
        if (level.isClientSide()) {
            ClientPacketDistributor.sendToServer(new SpellCastC2SPacket());
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

        tooltipAdder.accept(Component.literal("Wood: " + readableId(wood)).withStyle(ChatFormatting.GOLD));
        tooltipAdder.accept(Component.literal("Core: " + readableId(core)).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipAdder.accept(Component.literal("Flexibility: " + (flexibility == null ? "unknown" : flexibility.getSerializedName()))
                .withStyle(ChatFormatting.GRAY));
        if (master.isPresent()) {
            tooltipAdder.accept(Component.literal("Master: " + master.get()).withStyle(ChatFormatting.AQUA));
        }
        tooltipAdder.accept(Component.literal(String.format(Locale.ROOT, "Integrity: %.2f", WandComponents.getIntegrity(stack)))
                .withStyle(ChatFormatting.GREEN));
        tooltipAdder.accept(Component.literal(String.format(Locale.ROOT, "Corruption: %.2f", WandComponents.getCorruption(stack)))
                .withStyle(ChatFormatting.DARK_RED));
    }

    private static String readableId(Identifier id) {
        return id == null ? "unknown" : id.toString();
    }

    public static ItemStack createWand(WandWood wood, WandCore core, WandLength length, WandFlexibility flexibility) {
        ItemStack stack = new ItemStack(ModItems.WAND.get());
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
        return stack;
    }
}
