package at.koopro.wizardsandbeasts.network.stats;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * Server → Client: a trainable stat just earned a whole point.
 *
 * <p>Carries the stat id and the two numbers, not a rendered sentence. That is the same split the
 * cast-rejection channel uses: the server decides <em>that</em> something happened, the client
 * decides how to say it. It buys three things a {@code NotifyS2CPayload} could not — the character
 * sheet can flash the row that changed, the client can spend the value on particles and a pitch, and
 * the wording stays in {@code en_us.json} where a translator can reach it.
 *
 * <p>The stat block itself is <em>not</em> in here. {@link PlayerStatsSyncPayload} is sent by the
 * same write and is the only authority on what the numbers are; this packet is presentation. A
 * client that somehow received this one alone would flash a row and show the old value for a moment
 * rather than invent a new one.
 */
@NullMarked
public record StatLevelUpS2CPayload(String statId, int from, int to, String sourceKey)
        implements CustomPacketPayload {

    public static final Type<StatLevelUpS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "stat_level_up"));

    public static final StreamCodec<ByteBuf, StatLevelUpS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public StatLevelUpS2CPayload decode(ByteBuf buf) {
            String statId = PacketCodecUtils.readString(buf);
            int from = PacketCodecUtils.clampNonNegative(buf.readInt());
            int to = PacketCodecUtils.clampNonNegative(buf.readInt());
            // Re-normalised on arrival as well as on send. A lang key is fed straight to
            // Component.translatable, and this end is the one a hostile server can reach.
            String sourceKey = PacketCodecUtils.normalizeIdentifier(PacketCodecUtils.readString(buf));
            return new StatLevelUpS2CPayload(statId, from, to, sourceKey);
        }

        @Override
        public void encode(ByteBuf buf, StatLevelUpS2CPayload pkt) {
            PacketCodecUtils.writeString(buf, pkt.statId());
            buf.writeInt(pkt.from());
            buf.writeInt(pkt.to());
            PacketCodecUtils.writeString(buf, pkt.sourceKey());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, PlayerStat stat, int from, int to, String sourceKey) {
        PacketDistributor.sendToPlayer(player,
                new StatLevelUpS2CPayload(stat.getId(), from, to, sourceKey));
    }
}
