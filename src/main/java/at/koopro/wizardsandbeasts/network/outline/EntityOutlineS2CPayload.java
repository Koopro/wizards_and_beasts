package at.koopro.wizardsandbeasts.network.outline;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.render.outline.OutlineEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server -> Client: which entities are outlined, in what colour, and until when.
 *
 * <p>Broadcast to <b>every</b> player rather than only the target, because an outline is something other
 * people are meant to see. The first attempt at this feature carried its colour in entity scoreboard tags,
 * which are NBT-only and never reach a client at all — hence a real payload.
 *
 * @param entities   entity UUID to outline; {@link OutlineEntry#NONE} (colour {@code 0}) clears that entity
 * @param replaceAll true for a full snapshot (login), false for an incremental update
 */
@NullMarked
public record EntityOutlineS2CPayload(Map<UUID, OutlineEntry> entities, boolean replaceAll)
        implements CustomPacketPayload {

    public static final Type<EntityOutlineS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "entity_outline"));

    public static final StreamCodec<ByteBuf, EntityOutlineS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EntityOutlineS2CPayload decode(ByteBuf buf) {
            boolean replaceAll = buf.readBoolean();
            int count = PacketCodecUtils.readBoundedCount(
                    buf, PacketCodecUtils.MAX_MAP_ENTRIES, "entity outlines");
            Map<UUID, OutlineEntry> entities = new LinkedHashMap<>(Math.max(4, count));
            for (int i = 0; i < count; i++) {
                UUID id = PacketCodecUtils.readUUID(buf);
                entities.put(id, new OutlineEntry(buf.readInt(), buf.readLong()));
            }
            return new EntityOutlineS2CPayload(entities, replaceAll);
        }

        @Override
        public void encode(ByteBuf buf, EntityOutlineS2CPayload payload) {
            buf.writeBoolean(payload.replaceAll);
            buf.writeInt(payload.entities.size());
            payload.entities.forEach((id, entry) -> {
                PacketCodecUtils.writeUUID(buf, id);
                buf.writeInt(entry.argb());
                buf.writeLong(entry.expiresAt());
            });
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Entities changed — tell everyone, in as few packets as the decoder's bound allows. Nothing for none. */
    public static void broadcast(Map<UUID, OutlineEntry> changes) {
        for (EntityOutlineS2CPayload page : paginate(changes, false)) {
            PacketDistributor.sendToAllPlayers(page);
        }
    }

    /** Full state for a player who just joined and has no map yet. */
    public static void sendSnapshot(ServerPlayer player, Map<UUID, OutlineEntry> snapshot) {
        for (EntityOutlineS2CPayload page : paginate(snapshot, true)) {
            PacketDistributor.sendToPlayer(player, page);
        }
    }

    /**
     * Splits an oversized map so the decoder's {@code MAX_MAP_ENTRIES} bound is never the thing that drops
     * state. For a snapshot the first page replaces and the rest append; an update only appends.
     */
    public static List<EntityOutlineS2CPayload> paginate(Map<UUID, OutlineEntry> entries, boolean snapshot) {
        List<EntityOutlineS2CPayload> pages = new ArrayList<>();
        Map<UUID, OutlineEntry> page = new LinkedHashMap<>();
        for (Map.Entry<UUID, OutlineEntry> entry : entries.entrySet()) {
            page.put(entry.getKey(), entry.getValue());
            if (page.size() == PacketCodecUtils.MAX_MAP_ENTRIES) {
                pages.add(new EntityOutlineS2CPayload(page, snapshot && pages.isEmpty()));
                page = new LinkedHashMap<>();
            }
        }
        // An empty snapshot still goes out, since replacing with nothing is how a stale client map is
        // cleared. An empty update would say nothing.
        if (!page.isEmpty() || (snapshot && pages.isEmpty())) {
            pages.add(new EntityOutlineS2CPayload(page, snapshot && pages.isEmpty()));
        }
        return pages;
    }
}
