package at.koopro.wizardsandbeasts.item.deluminator;

import at.koopro.wizardsandbeasts.deluminator.DeluminatorBlockActions;
import at.koopro.wizardsandbeasts.item.GeoItemBase;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.util.ClientClassBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.function.Consumer;

public class DeluminatorItem extends GeoItemBase {

    private static final String CONTROLLER = "deluminator_controller";
    private static final String TAG_OPEN = "Open";
    static final int MAX_STORED_LIGHTS = 10;

    private static final RawAnimation OPEN_LID =
            RawAnimation.begin().thenPlay("open_lid")
                    .thenLoop("idle_open");
    private static final RawAnimation CLOSE_LID =
            RawAnimation.begin().thenPlay("close_lid")
                    .thenLoop("idle_closed");

    public DeluminatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null)
                    this.renderer = ClientClassBridge.instantiate(
                            "at.koopro.wizardsandbeasts.client.deluminator.DeluminatorRenderer",
                            GeoItemRenderer.class,
                            new Class<?>[0],
                            new Object[0]);
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<DeluminatorItem>(
                CONTROLLER, 0,
                state -> PlayState.STOP)
                .triggerableAnim("open", OPEN_LID)
                .triggerableAnim("close", CLOSE_LID));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getStoredLights(stack) > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltipAdder, flag);
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String boundName = tag.getString(at.koopro.wizardsandbeasts.deluminator.DeluminatorReturnService.TAG_GUIDED_PLAYER_NAME).orElse("");
        if (!boundName.isBlank()) {
            tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.deluminator.bound_tooltip", boundName)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.deluminator.stored_lights",
                        getStoredLights(stack), MAX_STORED_LIGHTS)
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        Level level = context.getLevel();

        if (player == null) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            return isOpen(stack) ? closeLid(level, player, stack) : openLid(level, player, stack);
        }

        if (!isOpen(stack)) {
            return openLid(level, player, stack);
        }

        return DeluminatorBlockActions.handleUseOnBlock(context, stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, net.minecraft.world.entity.LivingEntity interactionTarget, InteractionHand usedHand) {
        if (player.level().isClientSide() || !isOpen(stack) || !(interactionTarget instanceof ServerPlayer)) {
            return InteractionResult.PASS;
        }
        ServerPlayer target = (ServerPlayer) interactionTarget;
        if (target == player) {
            return InteractionResult.PASS;
        }
        at.koopro.wizardsandbeasts.deluminator.DeluminatorReturnService.bindGuidedPlayer(stack, target);
        player.displayClientMessage(Component.translatable("item.wizards_and_beasts.deluminator.bound", target.getName())
                .withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && isOpen(stack) && getStoredLights(stack) > 0 && player instanceof ServerPlayer) {
                if (at.koopro.wizardsandbeasts.deluminator.DeluminatorReturnService.tryBeginReturn((ServerPlayer) player, stack)) {
                    return InteractionResult.SUCCESS;
                }
            }
            return isOpen(stack) ? closeLid(level, player, stack) : openLid(level, player, stack);
        }

        if (!isOpen(stack)) {
            return openLid(level, player, stack);
        }

        if (!level.isClientSide()) {
            int absorbed = DeluminatorBlockActions.absorbNearbyLights(level, player.blockPosition(), stack, 8);
            if (absorbed > 0) {
                return InteractionResult.SUCCESS;
            }

            int restored = DeluminatorBlockActions.restoreNearbyLights(level, player.blockPosition(), stack, 8);
            if (restored > 0) {
                return InteractionResult.SUCCESS;
            }
        }

        if (getStoredLights(stack) <= 0) return InteractionResult.PASS;

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() == HitResult.Type.MISS) return InteractionResult.PASS;

        BlockPos targetPos = hit.getBlockPos().relative(hit.getDirection());

        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockState replaceCheck = level.getBlockState(targetPos);
        if (replaceCheck.canBeReplaced()) {
            level.setBlock(targetPos, ModBlocks.DELUMINATOR_LIGHT.get().defaultBlockState(), 3);
            adjustStoredLights(stack, -1);
            level.playSound(null, targetPos, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private InteractionResult openLid(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        setOpen(stack, true);
        triggerAnim(player, GeoItem.getOrAssignId(stack, (ServerLevel) level), CONTROLLER, "open");
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.6F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult closeLid(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        setOpen(stack, false);
        triggerAnim(player, GeoItem.getOrAssignId(stack, (ServerLevel) level), CONTROLLER, "close");
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.6F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    private static boolean isOpen(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag().getBoolean(TAG_OPEN).orElse(false);
    }

    private static void setOpen(ItemStack stack, boolean open) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(TAG_OPEN, open);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getStoredLights(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.STORED_LIGHTS.get(), 0);
    }

    public static boolean canStoreMoreLights(ItemStack stack) {
        return getStoredLights(stack) < MAX_STORED_LIGHTS;
    }

    public static void adjustStoredLights(ItemStack stack, int delta) {
        int current = getStoredLights(stack);
        stack.set(ModDataComponents.STORED_LIGHTS.get(), Math.clamp(current + delta, 0, MAX_STORED_LIGHTS));
    }
}
