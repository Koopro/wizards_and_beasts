package at.koopro.wizardsandbeasts.network.azkaban;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AzkabanTrespasserSyncPayload(boolean tagged) implements CustomPacketPayload {

    public static final Type<AzkabanTrespasserSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "azkaban_trespasser_sync"));

    public static final StreamCodec<ByteBuf, AzkabanTrespasserSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public @NonNull AzkabanTrespasserSyncPayload decode(@NonNull ByteBuf buf) {
                    return new AzkabanTrespasserSyncPayload(buf.readBoolean());
                }

                @Override
                public void encode(@NonNull ByteBuf buf, @NonNull AzkabanTrespasserSyncPayload pkt) {
                    buf.writeBoolean(pkt.tagged);
                }
            };

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
