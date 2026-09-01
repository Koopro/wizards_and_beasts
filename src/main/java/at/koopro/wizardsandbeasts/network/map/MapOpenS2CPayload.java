package at.koopro.wizardsandbeasts.network.map;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Opens the map, or re-points an already-open one at a new dimension.
 *
 * <p>Carries the biome palette because the client cannot draw a single tile without it: regions
 * ship palette <em>indices</em>, and an index is meaningless on its own. Sending it here rather
 * than alongside the first region means the client is ready before any terrain arrives.
 *
 * <p>{@code centerX/centerZ} is where the holder is standing <em>now</em>, not where the map was
 * made. The screen opens looking at the player; everything after that is theirs to pan.
 */
public record MapOpenS2CPayload(
        UUID mapId,
        Identifier dimension,
        int centerX,
        int centerZ,
        int senseRadius,
        List<Identifier> palette
) implements CustomPacketPayload {

    public static final Type<MapOpenS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "map_open"));

    private static final Identifier OVERWORLD =
            Identifier.fromNamespaceAndPath("minecraft", "overworld");

    public static final StreamCodec<ByteBuf, MapOpenS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MapOpenS2CPayload decode(ByteBuf buf) {
            UUID mapId = PacketCodecUtils.readUUID(buf);
            Identifier dimension = PacketCodecUtils.readIdentifier(buf, OVERWORLD);
            int centerX = buf.readInt();
            int centerZ = buf.readInt();
            int senseRadius = PacketCodecUtils.clampNonNegative(buf.readInt());
            int count = PacketCodecUtils.readBoundedCount(buf, PacketCodecUtils.MAX_MAP_PALETTE,
                    "map-palette");
            List<Identifier> palette = new ArrayList<>(Math.min(count, 256));
            for (int i = 0; i < count; i++) {
                // A malformed entry keeps its slot rather than shifting the rest: the indices in
                // every region already shipped are positional, and compacting the palette here
                // would silently repaint the world one biome to the left.
                palette.add(PacketCodecUtils.readIdentifier(buf, OVERWORLD));
            }
            return new MapOpenS2CPayload(mapId, dimension, centerX, centerZ, senseRadius, palette);
        }

        @Override
        public void encode(ByteBuf buf, MapOpenS2CPayload pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.mapId);
            PacketCodecUtils.writeIdentifier(buf, pkt.dimension);
            buf.writeInt(pkt.centerX);
            buf.writeInt(pkt.centerZ);
            buf.writeInt(pkt.senseRadius);
            buf.writeInt(pkt.palette.size());
            for (Identifier biome : pkt.palette) {
                PacketCodecUtils.writeIdentifier(buf, biome);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
