package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.network.SpellNetworkGuards;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.beam.WandBeamChannelLogic;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The attack key, pressed during a Wingardium Leviosa hold: throw what is being lifted.
 *
 * <p>Empty for the same reason {@link SpellCastC2SPayload} is. The server already knows the spell, the
 * target and the aim; the packet says only that the button went down, and
 * {@link WandBeamChannelLogic#throwLeviosaTarget} decides whether a hold with something in it exists
 * for that to mean anything.
 */
public record SpellLeviosaThrowC2SPayload() implements CustomPacketPayload {

    public static final Type<SpellLeviosaThrowC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_leviosa_throw"));

    public static final StreamCodec<ByteBuf, SpellLeviosaThrowC2SPayload> STREAM_CODEC =
            PacketCodecUtils.noPayloadCodec(SpellLeviosaThrowC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpellLeviosaThrowC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!SpellNetworkGuards.canUseWand(player, player.getData(ModAttachments.SPELL_DATA.get()), "leviosa_throw")) {
                return;
            }
            WandBeamChannelLogic.throwLeviosaTarget(player);
        });
    }
}
