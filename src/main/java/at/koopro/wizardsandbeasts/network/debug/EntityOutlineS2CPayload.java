package at.koopro.wizardsandbeasts.network.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server -> Client: which entities are outlined, and in what colour.
 *
 * <p>Broadcast to <b>every</b> player rather than only the target, because an outline is something other
 * people are meant to see. The previous attempt at this feature carried its colour in entity scoreboard
 * tags, which are NBT-only and never reach a client at all — hence a real payload.
 *
 * @param entities   entity UUID to packed ARGB; a colour of {@code 0} clears that entity
 * @param replaceAll true for a full snapshot (login), false for an incremental update
 */
@NullMarked
public record EntityOutlineS2CPayload(Map<UUID, Integer> entities, boolean replaceAll)
        implements CustomPacketPayload {

    public static final Type<EntityOutlineS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "entity_outline"));

    public static final StreamCodec<ByteBuf, EntityOutlineS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EntityOutlineS2CPayload decode(ByteBuf buf) {
            boolean replaceAll = buf.readBoolean();
            int count = PacketCodecUtils.readBoundedCount(
                    buf, PacketCodecUtils.MAX_MAP_ENTRIES, "entity outlines");
            Map<UUID, Integer> entities = new java.util.LinkedHashMap<>(Math.max(4, count));
            for (int i = 0; i < count; i++) {
                UUID id = PacketCodecUtils.readUUID(buf);
                entities.put(id, buf.readInt());
            }
            return new EntityOutlineS2CPayload(entities, replaceAll);
        }

        @Override
        public void encode(ByteBuf buf, EntityOutlineS2CPayload payload) {
            buf.writeBoolean(payload.replaceAll);
            buf.writeInt(payload.entities.size());
            payload.entities.forEach((id, argb) -> {
                PacketCodecUtils.writeUUID(buf, id);
                buf.writeInt(argb);
            });
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** One entity changed — tell everyone who can see it, which is everyone. */
    public static void broadcast(UUID entityId, int argb) {
        PacketDistributor.sendToAllPlayers(
                new EntityOutlineS2CPayload(Map.of(entityId, argb), false));
    }

    /** Full state for a player who just joined and has no map yet. */
    public static void sendSnapshot(ServerPlayer player, Map<UUID, Integer> snapshot) {
        for (EntityOutlineS2CPayload page : paginate(snapshot)) {
            PacketDistributor.sendToPlayer(player, page);
        }
    }

    /**
     * Splits an oversized snapshot so the decoder's {@code MAX_MAP_ENTRIES} bound is never the thing
     * that drops state. The first page replaces, the rest append.
     */
    private static List<EntityOutlineS2CPayload> paginate(Map<UUID, Integer> snapshot) {
        List<EntityOutlineS2CPayload> pages = new ArrayList<>();
        Map<UUID, Integer> page = new java.util.LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : snapshot.entrySet()) {
            page.put(entry.getKey(), entry.getValue());
            if (page.size() == PacketCodecUtils.MAX_MAP_ENTRIES) {
                pages.add(new EntityOutlineS2CPayload(page, pages.isEmpty()));
                page = new java.util.LinkedHashMap<>();
            }
        }
        if (!page.isEmpty() || pages.isEmpty()) {
            pages.add(new EntityOutlineS2CPayload(page, pages.isEmpty()));
        }
        return pages;
    }
}
