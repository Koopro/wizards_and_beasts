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
     * Resolves one wand release: spends the open session's release token, then applies the cast
     * (execute, cooldown, cast count, sync).
     *
     * <p>Safe to call from server-only flows — Avada Kedavra ends its own channel on the kill by
     * calling this. Doing so spends the same token, so the client's release for that hold, whenever it
     * arrives, is refused as a duplicate rather than casting a second time. That is what replaced the
     * fifteen-tick ignore window this class used to keep: a window let a genuine re-press inside it be
     * eaten and a duplicate outside it through, because it was standing in for state it could not see.
     */
    public static void completeWandCastRelease(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            debugReject(player, SpellRejectCodes.NOT_SERVER_LEVEL);
            return;
        }

        CastReleaseGate refused = WandCastSessions.offerRelease(player, serverLevel.getGameTime());
        if (refused != null) {
            player.getData(ModAttachments.SPELL_DATA.get()).incrementSyncCorrections();
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
