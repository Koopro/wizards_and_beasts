package at.koopro.wizardsandbeasts.network.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.wand.WandGlobalsClient;
import at.koopro.wizardsandbeasts.wand.rules.WandGlobals;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * Server → every client: the server-wide wand rules, so a wand's tooltip on a remote server shows the numbers its
 * casts will actually use. Display only — every cast is computed on the server.
 */
@NullMarked
public record WandGlobalsSyncS2CPayload(WandGlobals.Values values) implements CustomPacketPayload {

    public static final Type<WandGlobalsSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wand_globals_sync"));

    public static final StreamCodec<ByteBuf, WandGlobalsSyncS2CPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.values.affinityStrength());
                buf.writeFloat(p.values.bondGrowth());
                buf.writeInt(p.values.defeatsToWin());
                buf.writeFloat(p.values.neglectLoss());
                buf.writeBoolean(p.values.foreignBackfire());
            },
            buf -> new WandGlobalsSyncS2CPayload(new WandGlobals.Values(
                    finite(buf.readFloat(), 0f, 2f), finite(buf.readFloat(), 0f, 5f), Mth.clamp(buf.readInt(), 1, 5),
                    finite(buf.readFloat(), 0f, 5f), buf.readBoolean())));

    /** A hostile or broken server cannot hand the client a NaN or a runaway multiplier. */
    private static float finite(float value, float min, float max) {
        return Float.isFinite(value) ? Mth.clamp(value, min, max) : 1.0f;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(WandGlobalsSyncS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> WandGlobalsClient.accept(payload.values()));
    }
}
