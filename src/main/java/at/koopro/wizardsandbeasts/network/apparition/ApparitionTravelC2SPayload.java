package at.koopro.wizardsandbeasts.network.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.ApparitionPoint;
import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.apparition.PlayerApparitionPoints;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client → server "Apparate to my saved destination #n". Only an index is sent: the destination itself is
 * read from the player's own server-side list, so a crafted packet cannot invent a position to teleport to
 * — the worst it can do is pick a different point the player had already memorised.
 */
@NullMarked
public record ApparitionTravelC2SPayload(int index) implements CustomPacketPayload {

    public static final Type<ApparitionTravelC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "apparition_travel"));

    public static final StreamCodec<ByteBuf, ApparitionTravelC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ApparitionTravelC2SPayload decode(ByteBuf buf) {
            return new ApparitionTravelC2SPayload(buf.readInt());
        }

        @Override
        public void encode(ByteBuf buf, ApparitionTravelC2SPayload payload) {
            buf.writeInt(payload.index);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ApparitionTravelC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            PlayerApparitionPoints points = player.getData(ModAttachments.APPARITION_POINTS.get());
            ApparitionPoint point = points.byIndex(payload.index);
            if (point == null) {
                return;
            }
            ApparitionServerLogic.travelTo(player, point);
        });
    }
}
