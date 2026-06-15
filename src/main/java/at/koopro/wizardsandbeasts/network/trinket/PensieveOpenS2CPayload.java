package at.koopro.wizardsandbeasts.network.trinket;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.memory.MemoryEntry;
import at.koopro.wizardsandbeasts.memory.MemoryType;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot of a player's stored memories, sent when they open a Pensieve so the read-only viewer can
 * browse them client-side. The memory list lives only in the server-side {@code MEMORIES} attachment;
 * this is a one-shot snapshot, not a continuous sync.
 */
public record PensieveOpenS2CPayload(List<MemoryEntry> memories) implements CustomPacketPayload {

    public static final int MAX_MEMORIES = 256;

    public static final Type<PensieveOpenS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "pensieve_open"));

    public static final StreamCodec<ByteBuf, PensieveOpenS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PensieveOpenS2CPayload decode(ByteBuf buf) {
            int count = PacketCodecUtils.readBoundedCount(buf, MAX_MEMORIES, "pensieve-memories");
            List<MemoryEntry> entries = new ArrayList<>(Math.min(count, 64));
            for (int i = 0; i < count; i++) {
                MemoryType type = typeByName(PacketCodecUtils.readString(buf));
                float intensity = Math.max(0f, Math.min(1f, buf.readFloat()));
                String source = PacketCodecUtils.readString(buf);
                long createdGameTime = buf.readLong();
                entries.add(new MemoryEntry(type, intensity, source, createdGameTime));
            }
            return new PensieveOpenS2CPayload(entries);
        }

        @Override
        public void encode(ByteBuf buf, PensieveOpenS2CPayload pkt) {
            List<MemoryEntry> entries = pkt.memories;
            int count = Math.min(entries.size(), MAX_MEMORIES);
            buf.writeInt(count);
            for (int i = 0; i < count; i++) {
                MemoryEntry entry = entries.get(i);
                PacketCodecUtils.writeString(buf, entry.type().getSerializedName());
                buf.writeFloat(entry.intensity());
                PacketCodecUtils.writeString(buf, entry.source());
                buf.writeLong(entry.createdGameTime());
            }
        }
    };

    private static MemoryType typeByName(String name) {
        for (MemoryType type : MemoryType.values()) {
            if (type.getSerializedName().equals(name)) {
                return type;
            }
        }
        return MemoryType.MUNDANE;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Snapshots the player's stored memories and opens their Pensieve viewer. */
    public static void open(ServerPlayer player) {
        List<MemoryEntry> snapshot = List.copyOf(player.getData(ModAttachments.MEMORIES.get()).memories());
        PacketDistributor.sendToPlayer(player, new PensieveOpenS2CPayload(snapshot));
    }
}
