package at.koopro.wizardsandbeasts.network.ability;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.AbilityResolver;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionState;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-player wheel state pushed to the owning client: the server-resolved usable ability id set (already
 * grant- and module-filtered, so debug grants are included) plus the current selection/pin/toggles/cooldowns.
 * The wheel renders straight off this — it never re-derives grants client-side, keeping the client purely
 * presentational (§2.4).
 */
@NullMarked
public record AbilitySelectionSyncS2CPayload(List<Identifier> usable,
                                             @Nullable Identifier selected,
                                             @Nullable Identifier pinned,
                                             List<Identifier> toggles,
                                             Map<Identifier, Long> cooldowns) implements CustomPacketPayload {

    private static final int MAX_ENTRIES = 256;

    public static final Type<AbilitySelectionSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ability_selection_sync"));

    public static final StreamCodec<ByteBuf, AbilitySelectionSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AbilitySelectionSyncS2CPayload decode(ByteBuf buf) {
            List<Identifier> usable = decodeIdList(buf);
            Identifier selected = decodeNullableId(buf);
            Identifier pinned = decodeNullableId(buf);
            List<Identifier> toggles = decodeIdList(buf);
            int cdCount = PacketCodecUtils.readBoundedCount(buf, MAX_ENTRIES, "ability-cooldowns");
            Map<Identifier, Long> cooldowns = new LinkedHashMap<>(Math.max(4, cdCount));
            for (int i = 0; i < cdCount; i++) {
                Identifier id = Identifier.parse(PacketCodecUtils.readString(buf));
                cooldowns.put(id, buf.readLong());
            }
            return new AbilitySelectionSyncS2CPayload(usable, selected, pinned, toggles, cooldowns);
        }

        @Override
        public void encode(ByteBuf buf, AbilitySelectionSyncS2CPayload payload) {
            encodeIdList(buf, payload.usable);
            encodeNullableId(buf, payload.selected);
            encodeNullableId(buf, payload.pinned);
            encodeIdList(buf, payload.toggles);
            int cdCount = Math.min(payload.cooldowns.size(), MAX_ENTRIES);
            buf.writeInt(cdCount);
            int written = 0;
            for (Map.Entry<Identifier, Long> entry : payload.cooldowns.entrySet()) {
                if (written++ >= cdCount) {
                    break;
                }
                PacketCodecUtils.writeString(buf, entry.getKey().toString());
                buf.writeLong(entry.getValue());
            }
        }
    };

    private static void encodeIdList(ByteBuf buf, List<Identifier> ids) {
        int count = Math.min(ids.size(), MAX_ENTRIES);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            PacketCodecUtils.writeString(buf, ids.get(i).toString());
        }
    }

    private static List<Identifier> decodeIdList(ByteBuf buf) {
        int count = PacketCodecUtils.readBoundedCount(buf, MAX_ENTRIES, "ability-ids");
        List<Identifier> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(Identifier.parse(PacketCodecUtils.readString(buf)));
        }
        return out;
    }

    private static void encodeNullableId(ByteBuf buf, @Nullable Identifier id) {
        PacketCodecUtils.writeString(buf, id == null ? "" : id.toString());
    }

    @Nullable
    private static Identifier decodeNullableId(ByteBuf buf) {
        String raw = PacketCodecUtils.readString(buf);
        return raw.isBlank() ? null : Identifier.parse(raw);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void syncToPlayer(ServerPlayer player) {
        AbilitySelectionState state = player.getData(ModAttachments.ABILITY_SELECTION.get());
        PacketDistributor.sendToPlayer(player, new AbilitySelectionSyncS2CPayload(
                AbilityResolver.usableWheelIds(player),
                state.selected(),
                state.pinned(),
                List.copyOf(state.toggles()),
                state.cooldowns()));
    }
}
