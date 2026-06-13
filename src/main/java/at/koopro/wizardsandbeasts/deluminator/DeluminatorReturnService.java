package at.koopro.wizardsandbeasts.deluminator;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.deluminator.DeluminatorItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * "Ron return" guided teleport: channelled multi-tick return of the Deluminator
 * holder to the attuned player, with guiding-light particle trail.
 *
 * <p>Extracted from {@link DeluminatorItem} so the item class holds only item
 * interaction surface; the server-tick channel state machine lives here.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class DeluminatorReturnService {

    public static final String TAG_GUIDED_PLAYER = "GuidedPlayer";
    public static final String TAG_GUIDED_PLAYER_NAME = "GuidedPlayerName";
    private static final int RETURN_WINDUP_TICKS = 20;
    private static final Map<UUID, PendingReturn> PENDING_RETURNS = new HashMap<>();

    private DeluminatorReturnService() {
    }

    /** Binds the Deluminator to a target player ("attunement"). */
    public static void bindGuidedPlayer(@NonNull ItemStack stack, @NonNull ServerPlayer target) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(TAG_GUIDED_PLAYER, target.getUUID().toString());
        tag.putString(TAG_GUIDED_PLAYER_NAME, target.getName().getString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /**
     * Starts a channelled return toward the attuned player.
     *
     * @return true if a channel began (or feedback was sent for an active channel attempt)
     */
    public static boolean tryBeginReturn(@NonNull ServerPlayer player, @NonNull ItemStack stack) {
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
            DeluminatorItem.adjustStoredLights(pending.stackRef, -1);

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

    @Nullable
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

    @Nullable
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
}
