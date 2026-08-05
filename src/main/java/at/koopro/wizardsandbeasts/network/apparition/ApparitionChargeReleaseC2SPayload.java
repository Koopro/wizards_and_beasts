package at.koopro.wizardsandbeasts.network.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionChargeManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Client → server "I have let go".
 *
 * <p>Carries nothing on purpose. The server owns the tick counter, the window and the evaluation; all the
 * client is permitted to report is <i>that</i> it released, never <i>when</i>. A payload carrying a release
 * tick would let any client claim a perfect release and never splinch, which is the entire design.
 */
@NullMarked
public record ApparitionChargeReleaseC2SPayload() implements CustomPacketPayload {

    public static final ApparitionChargeReleaseC2SPayload INSTANCE = new ApparitionChargeReleaseC2SPayload();

    public static final Type<ApparitionChargeReleaseC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "apparition_charge_release"));

    public static final StreamCodec<ByteBuf, ApparitionChargeReleaseC2SPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ApparitionChargeReleaseC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ApparitionChargeManager.release(player);
            }
        });
    }
}
