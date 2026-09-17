package at.koopro.wizardsandbeasts.network.spell;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.command.debug.DebugHooks;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.cast.CastReleaseGate;
import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import at.koopro.wizardsandbeasts.spell.cast.SpellCastService;
import at.koopro.wizardsandbeasts.spell.cast.WandCastSessions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * The wand release edge, as sent by the client.
 *
 * <p>Deliberately empty. The client is not asked which spell, which slot, how long it held, or which
 * session it thinks it is in, because every one of those would be a claim the server must then either
 * trust or re-derive — and the server already knows all of them. The packet says one thing: <em>the
 * button came up</em>. {@link WandCastSessions} decides whether that edge lands on an open hold, and
 * {@link SpellCastService} decides what it is worth.
 */
public record SpellCastC2SPayload() implements CustomPacketPayload {
    private static final Logger LOGGER = LogUtils.getLogger();

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
     * Resolves a client wand release: spends the open session's release token only after vanilla has
     * ended that hold on the server, then applies the cast (execute, cooldown, cast count, sync).
     *
     * <p>The client packet is only an edge after vanilla's ordered release packet. A packet injected
     * while the server still sees a live hold cannot spend the token.
     */
    public static void completeWandCastRelease(ServerPlayer player) {
        completeWandCastRelease(player, false);
    }

    /**
     * Resolves a server-driven release against the same token as a client release. The server is the
     * release authority in this path, so it need not wait for the vanilla callback it is about to cause.
     */
    public static void completeServerDrivenWandCastRelease(ServerPlayer player) {
        completeWandCastRelease(player, true);
    }

    private static void completeWandCastRelease(ServerPlayer player, boolean serverDriven) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            debugReject(player, SpellRejectCodes.NOT_SERVER_LEVEL);
            return;
        }

        CastReleaseGate refused = serverDriven
                ? WandCastSessions.offerServerDrivenRelease(player, serverLevel.getGameTime())
                : WandCastSessions.offerClientRelease(player, serverLevel.getGameTime());
        if (refused != null) {
            if (!refused.endsHoldWithoutCast()) {
                // Letting go of a clash, or of a hold after switching spell, is the game working — not the
                // client and server disagreeing.
                player.getData(ModAttachments.SPELL_DATA.get()).incrementSyncCorrections();
            }
            debugReject(player, refused.rejectCode());
            return;
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
