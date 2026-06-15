package at.koopro.wizardsandbeasts.floo.call;

import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooRegistryEntry;
import at.koopro.wizardsandbeasts.floo.FlooTravelHandler;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages live "head in the fire" Floo calls. The caller kneels at a lit grate, names a
 * connected address, and their voice carries to the remote grate; nearby chat carries back.
 * Sessions are transient (cleared on server stop).
 */
public final class FlooCallService {

    public static final int CALL_DURATION_TICKS = 1200; // 60s
    private static final double LEASH_RANGE_SQ = 2.5 * 2.5;
    private static final double REMOTE_HEAR_RANGE_SQ = 6.0 * 6.0;

    private static final Map<UUID, FlooCall> SESSIONS = new ConcurrentHashMap<>();

    private FlooCallService() {
    }

    /** Begins a call from the player's nearby lit grate to the given address. */
    public static void startCall(@NonNull ServerPlayer caller, @NonNull String address) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) return;

        var origin = FlooTravelHandler.findNearbyLitFireplace(caller);
        if (origin == null) {
            caller.displayClientMessage(Component.literal("You need a lit fireplace to make a Floo call."), true);
            return;
        }

        FlooNetworkManager manager = FlooNetworkManager.get((ServerLevel) caller.level());
        FlooRegistryEntry dest = manager.getEntry(address);
        if (dest == null || !dest.isEnabled()) {
            caller.displayClientMessage(Component.literal("That connection has been sealed."), true);
            return;
        }
        if (address.equalsIgnoreCase(origin.getNetworkAddress())) {
            caller.displayClientMessage(Component.literal("You cannot call your own grate."), true);
            return;
        }

        MinecraftServer server = ((ServerLevel) caller.level()).getServer();
        // Replace any prior call by this player.
        endCall(server, caller.getUUID(), null);

        FlooCall call = new FlooCall(
                caller.getUUID(),
                caller.level().dimension().identifier(), origin.getBlockPos(),
                dest.networkAddress(), dest.dimension(), dest.blockPos(),
                CALL_DURATION_TICKS);
        SESSIONS.put(caller.getUUID(), call);

        caller.sendSystemMessage(Component.literal("You lean into the emerald flames and call ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("\"" + dest.networkAddress() + "\"")
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
        caller.level().playSound(null, origin.getBlockPos(), ModSounds.FLOO_WHOOSH.get(),
                SoundSource.BLOCKS, 0.7f, 1.2f);

        ServerLevel targetLevel = resolveLevel(server, dest.dimension());
        if (targetLevel != null) {
            announceNearRemote(targetLevel, dest.blockPos(),
                    Component.literal(caller.getName().getString() + "'s head appears in the emerald flames.")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
            spawnHeadParticles(targetLevel, dest.blockPos());
        }
    }

    /** Per-tick maintenance for a calling player. No-op if they have no active call. */
    public static void tick(@NonNull ServerPlayer player) {
        FlooCall call = SESSIONS.get(player.getUUID());
        if (call == null) return;

        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        call.ticksRemaining--;
        if (call.ticksRemaining <= 0) {
            endCall(server, player.getUUID(), "The connection fades and the flames die down.");
            return;
        }

        // Caller must stay at the grate.
        if (!player.level().dimension().identifier().equals(call.originDim)
                || player.distanceToSqr(call.originPos.getX() + 0.5,
                call.originPos.getY() + 0.5, call.originPos.getZ() + 0.5) > LEASH_RANGE_SQ) {
            endCall(server, player.getUUID(), "You pull your head out of the fire.");
            return;
        }

        // Origin grate must stay lit.
        var state = player.level().getBlockState(call.originPos);
        if (!state.hasProperty(FlooFireplaceBlock.LIT) || !state.getValue(FlooFireplaceBlock.LIT)) {
            endCall(server, player.getUUID(), "The flames die and the connection breaks.");
            return;
        }

        // Root the kneeling caller in place.
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 6, false, false, false));

        // Spectral head shimmer at both ends every half second.
        if (call.ticksRemaining % 10 == 0) {
            if (player.level() instanceof ServerLevel originLevel) {
                spawnHeadParticles(originLevel, call.originPos);
            }
            ServerLevel targetLevel = resolveLevel(server, call.targetDim);
            if (targetLevel != null) {
                spawnHeadParticles(targetLevel, call.targetPos);
            }
        }
    }

    /** Bridges chat across an active call. Returns silently — never cancels normal chat. */
    public static void handleChat(@NonNull ServerPlayer speaker, @NonNull String message) {
        MinecraftServer server = ((ServerLevel) speaker.level()).getServer();
        // Caller speaking -> remote grate hears.
        FlooCall ownCall = SESSIONS.get(speaker.getUUID());
        if (ownCall != null) {
            ServerLevel targetLevel = resolveLevel(server, ownCall.targetDim);
            if (targetLevel != null) {
                announceNearRemote(targetLevel, ownCall.targetPos, flooLine(speaker, message));
            }
            return;
        }

        // Someone near a remote grate -> the caller on the other end hears.
        for (FlooCall call : SESSIONS.values()) {
            if (!speaker.level().dimension().identifier().equals(call.targetDim)) continue;
            if (speaker.distanceToSqr(call.targetPos.getX() + 0.5,
                    call.targetPos.getY() + 0.5, call.targetPos.getZ() + 0.5) > REMOTE_HEAR_RANGE_SQ) {
                continue;
            }
            ServerPlayer caller = server.getPlayerList().getPlayer(call.caller);
            if (caller != null) {
                caller.sendSystemMessage(flooLine(speaker, message));
            }
        }
    }

    public static void endCall(@NonNull MinecraftServer server, @NonNull UUID callerUuid, @Nullable String reason) {
        FlooCall call = SESSIONS.remove(callerUuid);
        if (call == null) return;

        ServerPlayer caller = server.getPlayerList().getPlayer(callerUuid);
        if (caller != null && reason != null) {
            // Root slowness is re-applied each tick at 10t; once the call ends it lapses
            // on its own, so we avoid clearing unrelated Slowness here.
            caller.sendSystemMessage(Component.literal(reason).withStyle(ChatFormatting.GRAY));
        }
        ServerLevel targetLevel = resolveLevel(server, call.targetDim);
        if (targetLevel != null) {
            announceNearRemote(targetLevel, call.targetPos,
                    Component.literal("The head withdraws from the flames.")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    public static boolean isInCall(@NonNull UUID callerUuid) {
        return SESSIONS.containsKey(callerUuid);
    }

    public static void clearAll() {
        SESSIONS.clear();
    }

    private static @NonNull Component flooLine(@NonNull ServerPlayer speaker, @NonNull String message) {
        return Component.literal("[Floo] ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(speaker.getName().getString() + ": ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }

    private static void announceNearRemote(@NonNull ServerLevel level, @NonNull BlockPos pos,
                                           @NonNull Component message) {
        for (ServerPlayer nearby : level.players()) {
            if (nearby.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    <= REMOTE_HEAR_RANGE_SQ) {
                nearby.sendSystemMessage(message);
            }
        }
    }

    private static void spawnHeadParticles(@NonNull ServerLevel level, @NonNull BlockPos pos) {
        if (!level.isLoaded(pos)) return;
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                8, 0.2, 0.2, 0.2, 0.01);
    }

    @Nullable
    private static ServerLevel resolveLevel(@NonNull MinecraftServer server, @NonNull Identifier dimensionId) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return server.getLevel(key);
    }
}
