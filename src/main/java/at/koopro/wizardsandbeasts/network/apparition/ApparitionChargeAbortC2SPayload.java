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
 * Client → server "I am not doing this after all".
 *
 * <p>Distinct from {@link ApparitionChargeReleaseC2SPayload} because the two mean opposite things. A release
 * is a commitment and is judged against the window; an abort is a withdrawal and costs nothing — no
 * cooldown, no exhaustion, no splinch.
 *
 * <p>It exists because the client can lose a charge without the player letting go: opening a screen, or the
 * armed ability changing underneath them. Before this, those paths tore down client state silently and left
 * the server holding an attempt that nobody was going to release, which then discharged itself into a
 * catastrophic splinch the player never asked for.
 */
@NullMarked
public record ApparitionChargeAbortC2SPayload() implements CustomPacketPayload {

    public static final ApparitionChargeAbortC2SPayload INSTANCE = new ApparitionChargeAbortC2SPayload();

    public static final Type<ApparitionChargeAbortC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "apparition_charge_abort"));

    public static final StreamCodec<ByteBuf, ApparitionChargeAbortC2SPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ApparitionChargeAbortC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ApparitionChargeManager.abort(player);
            }
        });
    }
}
