package at.koopro.wizardsandbeasts.floo.call;

import at.koopro.wizardsandbeasts.util.PlayerScopedState;

import at.koopro.wizardsandbeasts.block.floo.FlooFireplaceBlock;
import at.koopro.wizardsandbeasts.floo.FlooAccess;
import at.koopro.wizardsandbeasts.floo.FlooAddress;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import at.koopro.wizardsandbeasts.floo.FlooRegistryEntry;
import at.koopro.wizardsandbeasts.floo.FlooTravelHandler;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModSounds;
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

import java.util.UUID;

/**
 * Manages live "head in the fire" Floo calls. The caller kneels at a lit grate, names a
 * connected address, and their voice carries to the remote grate; nearby chat carries back.
 * Sessions are transient (cleared on server stop).
 */
public final class FlooCallService {

    public static final int CALL_DURATION_TICKS = 1200; // 60s
    private static final double LEASH_RANGE_SQ = 2.5 * 2.5;
    private static final double REMOTE_HEAR_RANGE_SQ = 6.0 * 6.0;

    // FlooAccess and FlooAddress are the shared vocabulary for "who may reach what" and "what is
    // the same address"; a call that answered either question its own way would drift from travel.
    private static final PlayerScopedState<FlooCall> SESSIONS =
            PlayerScopedState.create("floo-calls");

    private FlooCallService() {
    }

    /**
     * Begins a call from the player's nearby lit grate to the given address.
     *
     * <p><b>Visibility is checked here, not only on the travel path.</b> This used to look the
     * address straight up in the registry, which meant a private hearth nobody had ever visited could
     * still be called — and, worse, that calling it answered differently from calling a name that did
     * not exist. Every guarantee the travel path makes about not confirming a private address was
     * undone by a second door that did not know about it.
     *
     * <p>Resolved through {@link FlooAccess} for the same reason, so a spoken call is heard the same
     * way a spoken destination is, mumbles included.
     */
    public static void startCall(@NonNull ServerPlayer caller, @NonNull String address) {
        if (!ModuleManager.isEnabled(Module.FLOO_NETWORK)) return;

        var origin = FlooTravelHandler.findNearbyLitFireplace(caller);
        if (origin == null) {
            caller.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.no_fireplace"), true);
            return;
        }

        FlooNetworkManager manager = FlooNetworkManager.get((ServerLevel) caller.level());
        FlooRegistryEntry dest = FlooAccess.resolveSpoken(caller, manager, address);
        if (dest == null) {
            // The same sentence the travel path gives, and deliberately: "no such connection" has to
            // cover an address that does not exist AND one the caller may not reach, or the
            // difference between the two replies is itself the leak.
            caller.displayClientMessage(Component.translatable(
                    "floo.wizards_and_beasts.fail.unknown_address", FlooAddress.display(address)), true);
            return;
        }
        if (!dest.isEnabled()) {
            caller.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.fail.sealed"), true);
            return;
        }
        if (FlooAddress.sameAddress(dest.networkAddress(), origin.getNetworkAddress())) {
            caller.displayClientMessage(
                    Component.translatable("floo.wizards_and_beasts.call.fail.self"), true);
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

        caller.sendSystemMessage(Component.translatable(
                "floo.wizards_and_beasts.call.start", dest.networkAddress()));
        caller.level().playSound(null, origin.getBlockPos(), ModSounds.FLOO_WHOOSH.get(),
                SoundSource.BLOCKS, 0.7f, 1.2f);

        ServerLevel targetLevel = resolveLevel(server, dest.dimension());
        if (targetLevel != null) {
            announceNearRemote(targetLevel, dest.blockPos(), Component.translatable(
                    "floo.wizards_and_beasts.call.remote.arrive", caller.getName().getString()));
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
            endCall(server, player.getUUID(),
                    Component.translatable("floo.wizards_and_beasts.call.end.timeout"));
            return;
        }

        // Caller must stay at the grate.
        if (!player.level().dimension().identifier().equals(call.originDim)
                || player.distanceToSqr(call.originPos.getX() + 0.5,
                call.originPos.getY() + 0.5, call.originPos.getZ() + 0.5) > LEASH_RANGE_SQ) {
            endCall(server, player.getUUID(),
                    Component.translatable("floo.wizards_and_beasts.call.end.moved"));
            return;
        }

        // Origin grate must stay lit.
        var state = player.level().getBlockState(call.originPos);
        if (!state.hasProperty(FlooFireplaceBlock.LIT) || !state.getValue(FlooFireplaceBlock.LIT)) {
            endCall(server, player.getUUID(),
                    Component.translatable("floo.wizards_and_beasts.call.end.doused"));
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
        for (FlooCall call : SESSIONS.view().values()) {
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

    /**
     * Hang up.
     *
     * <p>{@code reason} is a {@link Component} rather than a {@code String} because every caller now
     * hands over a finished, translated sentence. Taking raw text and wrapping it in
     * {@code Component.literal} here made this method the one place a translated message could not be
     * passed through — the type is what keeps English out of the call sites.
     *
     * <p>Null still means "ended silently": a login/logout teardown has nobody to tell.
     */
    public static void endCall(@NonNull MinecraftServer server, @NonNull UUID callerUuid,
                               @Nullable Component reason) {
        FlooCall call = SESSIONS.remove(callerUuid);
        if (call == null) return;

        ServerPlayer caller = server.getPlayerList().getPlayer(callerUuid);
        if (caller != null && reason != null) {
            // Root slowness is re-applied each tick at 10t; once the call ends it lapses
            // on its own, so we avoid clearing unrelated Slowness here.
            caller.sendSystemMessage(reason);
        }
        ServerLevel targetLevel = resolveLevel(server, call.targetDim);
        if (targetLevel != null) {
            announceNearRemote(targetLevel, call.targetPos,
                    Component.translatable("floo.wizards_and_beasts.call.remote.depart"));
        }
    }

    public static boolean isInCall(@NonNull UUID callerUuid) {
        return SESSIONS.contains(callerUuid);
    }

    public static void clearAll() {
        SESSIONS.clear();
    }

    private static @NonNull Component flooLine(@NonNull ServerPlayer speaker, @NonNull String message) {
        // The player's own words go in as an argument, never into the template: translation
        // arguments are inserted after the template is parsed, so a message containing "%s" cannot
        // reach back into the format string.
        return Component.translatable("floo.wizards_and_beasts.call.line",
                speaker.getName().getString(), message);
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
