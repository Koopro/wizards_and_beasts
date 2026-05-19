package at.koopro.wizardsandbeasts.item.deluminator;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.deluminator.DeluminatorBlockActions;
import at.koopro.wizardsandbeasts.item.GeoItemBase;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.util.ClientClassBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public class DeluminatorItem extends GeoItemBase {

    private static final String CONTROLLER = "deluminator_controller";
    private static final String TAG_OPEN = "Open";
    private static final String TAG_GUIDED_PLAYER = "GuidedPlayer";
    private static final String TAG_GUIDED_PLAYER_NAME = "GuidedPlayerName";
    private static final int RETURN_WINDUP_TICKS = 20;
    private static final Map<UUID, PendingReturn> PENDING_RETURNS = new HashMap<>();
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
        String boundName = tag.getString(TAG_GUIDED_PLAYER_NAME).orElse("");
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
        setGuidedPlayer(stack, target);
        player.displayClientMessage(Component.translatable("item.wizards_and_beasts.deluminator.bound", target.getName())
                .withStyle(ChatFormatting.GOLD), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && isOpen(stack) && getStoredLights(stack) > 0 && player instanceof ServerPlayer) {
                if (tryRonReturn((ServerPlayer) player, stack)) {
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

    private boolean tryRonReturn(ServerPlayer player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel)) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        ServerPlayer target = getGuidedPlayer(player, stack);

        if (target == null) {
            player.displayClientMessage(Component.translatable("item.wizards_and_beasts.deluminator.return_failed")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        if (PENDING_RETURNS.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("item.wizards_and_beasts.deluminator.return_channeling")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }

        PENDING_RETURNS.put(player.getUUID(), new PendingReturn(target.getUUID(), stack, RETURN_WINDUP_TICKS));
        player.displayClientMessage(Component.translatable("item.wizards_and_beasts.deluminator.return_channeling")
                .withStyle(ChatFormatting.GOLD), true);
        level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.6F, 1.4F);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_RETURNS.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, PendingReturn>> iterator = PENDING_RETURNS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingReturn> entry = iterator.next();
            PendingReturn pending = entry.getValue();

            ServerPlayer user = event.getServer().getPlayerList().getPlayer(entry.getKey());
            ServerPlayer target = event.getServer().getPlayerList().getPlayer(pending.targetPlayerId);
            if (user == null || target == null || !user.isAlive() || !target.isAlive() || target.isSpectator()) {
                iterator.remove();
                continue;
            }

            if (!(user.level() instanceof ServerLevel) || !(target.level() instanceof ServerLevel)) {
                iterator.remove();
                continue;
            }
            ServerLevel userLevel = (ServerLevel) user.level();
            ServerLevel targetLevel = (ServerLevel) target.level();

            if (pending.ticksRemaining > 0) {
                pending.ticksRemaining--;

                Vec3 trailStart = new Vec3(user.getX(), user.getEyeY(), user.getZ());
                userLevel.sendParticles(ParticleTypes.END_ROD,
                        trailStart.x, trailStart.y, trailStart.z,
                        2, 0.08D, 0.08D, 0.08D, 0.004D);

                if (userLevel == targetLevel) {
                    double progress = 1.0D - (pending.ticksRemaining / (double) RETURN_WINDUP_TICKS);
                    Vec3 fullEnd = new Vec3(target.getX(), target.getEyeY(), target.getZ());
                    Vec3 partialEnd = trailStart.lerp(fullEnd, Mth.clamp(progress, 0.15D, 1.0D));
                    spawnGuidingLightTrail(userLevel, trailStart, partialEnd);
                }
                continue;
            }

            BlockPos arrival = findSafeArrival(targetLevel, target.blockPosition());
            if (arrival == null) {
                user.displayClientMessage(Component.translatable("item.wizards_and_beasts.deluminator.return_failed")
                        .withStyle(ChatFormatting.GRAY), true);
                iterator.remove();
                continue;
            }

            Vec3 origin = user.position();
            Vec3 destination = new Vec3(arrival.getX() + 0.5D, arrival.getY(), arrival.getZ() + 0.5D);
            if (userLevel == targetLevel) {
                user.teleportTo(destination.x, destination.y, destination.z);
            } else {
                String dimId = targetLevel.dimension().identifier().toString();
                String command = "execute in " + dimId + " run tp " + user.getScoreboardName()
                        + " " + destination.x + " " + destination.y + " " + destination.z
                        + " " + user.getYRot() + " " + user.getXRot();
                targetLevel.getServer().getCommands().performPrefixedCommand(
                        targetLevel.getServer().createCommandSourceStack().withSuppressedOutput(),
                        command
                );
            }
            adjustStoredLights(pending.stackRef, -1);

            userLevel.playSound(null, BlockPos.containing(origin), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
            targetLevel.playSound(null, arrival, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
            userLevel.sendParticles(ParticleTypes.END_ROD, origin.x, origin.y + 1.0D, origin.z, 20, 0.2D, 0.4D, 0.2D, 0.02D);
            targetLevel.sendParticles(ParticleTypes.END_ROD, arrival.getX() + 0.5D, arrival.getY() + 1.0D, arrival.getZ() + 0.5D, 20, 0.2D, 0.4D, 0.2D, 0.02D);
            user.displayClientMessage(Component.translatable("item.wizards_and_beasts.deluminator.return_success", target.getName())
                    .withStyle(ChatFormatting.AQUA), true);
            iterator.remove();
        }
    }

    /**
     * Streak of light motes along the path from Deluminator to the attuned witch or wizard (same-tick read).
     */
    private static void spawnGuidingLightTrail(ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 1.0E-4D) {
            return;
        }
        int steps = Mth.clamp((int) (length * 1.8D), 12, 48);
        Vec3 dir = delta.normalize();
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3 p = start.add(delta.scale(t));
            double wobble = (level.random.nextDouble() - 0.5D) * 0.06D;
            level.sendParticles(ParticleTypes.END_ROD,
                    p.x + wobble, p.y + wobble * 0.5D, p.z + wobble,
                    1, 0.02D, 0.03D, 0.02D, 0.008D);
            if (i == steps || i % 6 == 0) {
                level.sendParticles(ParticleTypes.FIREWORK,
                        p.x, p.y, p.z,
                        1, dir.x * 0.02D, dir.y * 0.02D, dir.z * 0.02D, 0.02D);
            }
        }
    }

    private static void setGuidedPlayer(ItemStack stack, ServerPlayer target) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(TAG_GUIDED_PLAYER, target.getUUID().toString());
        tag.putString(TAG_GUIDED_PLAYER_NAME, target.getName().getString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static ServerPlayer getGuidedPlayer(ServerPlayer user, ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String targetId = tag.getString(TAG_GUIDED_PLAYER).orElse("");
        if (targetId.isBlank() || !(user.level() instanceof ServerLevel)) {
            return null;
        }
        ServerLevel level = (ServerLevel) user.level();
        UUID parsedId;
        try {
            parsedId = UUID.fromString(targetId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(parsedId);
        if (target == null || !target.isAlive() || target.isSpectator()) {
            return null;
        }
        return target;
    }

    private static final class PendingReturn {
        private final UUID targetPlayerId;
        private final ItemStack stackRef;
        private int ticksRemaining;

        private PendingReturn(UUID targetPlayerId, ItemStack stackRef, int ticksRemaining) {
            this.targetPlayerId = targetPlayerId;
            this.stackRef = stackRef;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private static BlockPos findSafeArrival(ServerLevel level, BlockPos center) {
        BlockPos[] checks = new BlockPos[] {
                center.above(),
                center.east().above(),
                center.west().above(),
                center.north().above(),
                center.south().above(),
                center.east().north().above(),
                center.east().south().above(),
                center.west().north().above(),
                center.west().south().above()
        };
        for (BlockPos pos : checks) {
            if (level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.above()).canBeReplaced()) {
                return pos;
            }
        }
        return null;
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
