package at.koopro.wizardsandbeasts.network.skill;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.ability.grant.AbilityGrantService;
import at.koopro.wizardsandbeasts.ability.grant.AbilityGrants;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pushes the derived {@link AbilityGrants} snapshot to the owning client so client code (future UI) can
 * query held abilities without a server round-trip. Transmits one key list per source; the client rebuilds
 * the snapshot with the same pure {@link AbilityGrants#of} builder the server used. State-only — there is
 * no client consumer today ({@code §3.3 no content}); this is the forward-looking sync the layer mandates.
 */
public record AbilityGrantsSyncS2CPayload(int syncVersion,
                                          List<String> heritage,
                                          List<String> vocation,
                                          List<String> skillNode) implements CustomPacketPayload {

    /** Defensive upper bound on a per-source key list — a sane ceiling well above any real grant count. */
    private static final int MAX_KEYS = 256;

    private static final AtomicInteger NEXT_SYNC_VERSION = new AtomicInteger();

    public static final Type<AbilityGrantsSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "ability_grants_sync"));

    public static final StreamCodec<ByteBuf, AbilityGrantsSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AbilityGrantsSyncS2CPayload decode(ByteBuf buf) {
            int syncVersion = PacketCodecUtils.clampNonNegative(buf.readInt());
            List<String> heritage = decodeList(buf);
            List<String> vocation = decodeList(buf);
            List<String> skillNode = decodeList(buf);
            return new AbilityGrantsSyncS2CPayload(syncVersion, heritage, vocation, skillNode);
        }

        @Override
        public void encode(ByteBuf buf, AbilityGrantsSyncS2CPayload pkt) {
            buf.writeInt(PacketCodecUtils.clampNonNegative(pkt.syncVersion));
            encodeList(buf, pkt.heritage);
            encodeList(buf, pkt.vocation);
            encodeList(buf, pkt.skillNode);
        }
    };

    private static void encodeList(ByteBuf buf, List<String> values) {
        int count = Math.min(values.size(), MAX_KEYS);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            PacketCodecUtils.writeString(buf, values.get(i));
        }
    }

    private static List<String> decodeList(ByteBuf buf) {
        int count = Math.max(0, Math.min(buf.readInt(), MAX_KEYS));
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(PacketCodecUtils.readString(buf));
        }
        return out;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Recomputes the grant snapshot server-side and sends it to {@code player}. */
    public static void syncToPlayer(ServerPlayer player) {
        AbilityGrants grants = AbilityGrantService.compute(player);
        PacketDistributor.sendToPlayer(player, new AbilityGrantsSyncS2CPayload(
                NEXT_SYNC_VERSION.incrementAndGet(),
                grants.keysFrom(AbilityGrants.Source.HERITAGE),
                grants.keysFrom(AbilityGrants.Source.VOCATION),
                grants.keysFrom(AbilityGrants.Source.SKILL_NODE)));
    }
}
