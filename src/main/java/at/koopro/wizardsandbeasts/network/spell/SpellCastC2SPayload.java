package at.koopro.wizardsandbeasts.network.spell;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.command.debug.DebugHooks;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import at.koopro.wizardsandbeasts.spell.cast.SpellCastService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record SpellCastC2SPayload() implements CustomPacketPayload {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * After a server-driven wand release (e.g. Avada kill), ignore duplicate release packets from the client
     * for a few ticks so cooldown / cast count are not applied twice and chat is not spammed.
     */
    private static final Map<UUID, Long> IGNORE_RELEASE_UNTIL_GAME_TICK = new ConcurrentHashMap<>();

    public static final Type<SpellCastC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_cast"));

    public static final StreamCodec<ByteBuf, SpellCastC2SPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(SpellCastC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellCastC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            completeWandCastRelease(player);
        });
    }

    /**
     * Ignore {@link #completeWandCastRelease(ServerPlayer)} calls (e.g. from a follow-up client packet) until this
     * game tick (inclusive).
     */
    public static void ignoreDuplicateReleasesUntil(ServerPlayer player, long gameTickInclusive) {
        IGNORE_RELEASE_UNTIL_GAME_TICK.put(player.getUUID(), gameTickInclusive);
    }

    /** Logout cleanup — entries otherwise persist for the server's lifetime (AUD-G-004). */
    public static void clearFor(UUID playerId) {
        IGNORE_RELEASE_UNTIL_GAME_TICK.remove(playerId);
    }

    /**
     * Applies the same release logic as a client {@link SpellCastC2SPayload} (execute, cooldown, cast count, sync).
     * Safe to call from server-only flows (e.g. Avada Kedavra ending the wand channel early).
     */
    public static void completeWandCastRelease(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            debugReject(player, SpellRejectCodes.NOT_SERVER_LEVEL);
            return;
        }

        Long ignoreUntil = IGNORE_RELEASE_UNTIL_GAME_TICK.get(player.getUUID());
        if (ignoreUntil != null && serverLevel.getGameTime() <= ignoreUntil) {
            player.getData(ModAttachments.SPELL_DATA.get()).incrementSyncCorrections();
            debugReject(player, SpellRejectCodes.DUPLICATE_RELEASE_GUARD);
            return;
        }
        if (ignoreUntil != null && serverLevel.getGameTime() > ignoreUntil) {
            IGNORE_RELEASE_UNTIL_GAME_TICK.remove(player.getUUID());
        }
        SpellCastService.completeWandCastRelease(player);
    }

    private static void debugReject(ServerPlayer player, String reason) {
        player.getData(ModAttachments.SPELL_DATA.get()).incrementRejectReason(reason);
        DebugHooks.logSpellCast(player, "cast_reject_packet", reason);
        if (Config.debugLogSpellGateReasons) {
            LOGGER.debug("SpellCast rejected for '{}' reason={}", player.getName().getString(), reason);
        }
    }

}
