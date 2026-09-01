package at.koopro.wizardsandbeasts.network.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One 512x512-block square of charted parchment, run-length encoded.
 *
 * <p>Regions are shipped a few at a time as the screen is open rather than all at once, so opening
 * a map of a heavily explored world is not a multi-megabyte stall. The client draws whatever has
 * arrived; the rest fills in over the following second or two, which reads as ink developing.
 *
 * <p>The dimension travels with the region so a packet that arrives after the holder has stepped
 * through a portal is discarded rather than drawn as Nether terrain over an Overworld map.
 */
public record MapRegionS2CPayload(
        Identifier dimension,
        long regionKey,
        byte[] runs
) implements CustomPacketPayload {

    public static final Type<MapRegionS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "map_region"));

    private static final Identifier OVERWORLD =
            Identifier.fromNamespaceAndPath("minecraft", "overworld");

    public static final StreamCodec<ByteBuf, MapRegionS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapRegionS2CPayload decode(ByteBuf buf) {
            Identifier dimension = PacketCodecUtils.readIdentifier(buf, OVERWORLD);
            long key = buf.readLong();
            byte[] runs = PacketCodecUtils.readBytes(buf,
                    PacketCodecUtils.MAX_MAP_REGION_BYTES, "map-region");
            return new MapRegionS2CPayload(dimension, key, runs);
        }

        @Override
        public void encode(ByteBuf buf, MapRegionS2CPayload pkt) {
            PacketCodecUtils.writeIdentifier(buf, pkt.dimension);
            buf.writeLong(pkt.regionKey);
            PacketCodecUtils.writeBytes(buf, pkt.runs,
                    PacketCodecUtils.MAX_MAP_REGION_BYTES, "map-region");
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
