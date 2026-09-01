package at.koopro.wizardsandbeasts.network.standing;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.standing.StandingAxis;
import at.koopro.wizardsandbeasts.standing.StandingBand;
import at.koopro.wizardsandbeasts.standing.StandingService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * The player's own standing, pushed to their own client.
 *
 * <p>Sends <b>resolved values and bands</b>, not the stored record. The client would otherwise need
 * dark corruption, the criminal record, the Ministry module flag and four config numbers to work out
 * what to draw — four more things to keep synchronised, each an opportunity for the sheet to disagree
 * with the server. Deciding it once on the authority and sending the answer is both smaller on the
 * wire and impossible to get out of step.
 *
 * <p>Only ever sent to the subject: standing is a fact about a character that other players learn by
 * playing with them, not by reading a packet.
 *
 * @param values  axis → resolved value, {@code −bound … +bound}
 * @param bands   axis → band, decided server-side with the server's own thresholds
 * @param bound   the axis magnitude in force, so meters can be drawn to scale
 */
public record StandingSyncS2CPayload(Map<StandingAxis, Float> values,
                                     Map<StandingAxis, StandingBand> bands,
                                     float bound) implements CustomPacketPayload {

    public static final Type<StandingSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "standing_sync"));

    public static final StreamCodec<ByteBuf, StandingSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NonNull StandingSyncS2CPayload decode(@NonNull ByteBuf buf) {
            StandingAxis[] axes = StandingAxis.values();
            Map<StandingAxis, Float> values = new EnumMap<>(StandingAxis.class);
            Map<StandingAxis, StandingBand> bands = new EnumMap<>(StandingAxis.class);
            // Fixed-length over the enum rather than a length-prefixed map: the axis set is a closed
            // compile-time constant shared by both sides, so there is no count to disagree about and
            // no bound to enforce on a hostile packet.
            for (StandingAxis axis : axes) {
                values.put(axis, buf.readFloat());
                bands.put(axis, decodeBand(buf.readByte()));
            }
            return new StandingSyncS2CPayload(values, bands, buf.readFloat());
        }

        @Override
        public void encode(@NonNull ByteBuf buf, @NonNull StandingSyncS2CPayload pkt) {
            for (StandingAxis axis : StandingAxis.values()) {
                buf.writeFloat(pkt.values.getOrDefault(axis, 0.0f));
                buf.writeByte(pkt.bands.getOrDefault(axis, StandingBand.NEUTRAL).ordinal());
            }
            buf.writeFloat(pkt.bound);
        }
    };

    /** Out-of-range ordinals fall back to neutral rather than throwing on the network thread. */
    private static StandingBand decodeBand(byte ordinal) {
        StandingBand[] bands = StandingBand.values();
        return ordinal >= 0 && ordinal < bands.length ? bands[ordinal] : StandingBand.NEUTRAL;
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void syncToPlayer(@NonNull ServerPlayer player) {
        Map<StandingAxis, Float> values = StandingService.snapshot(player);
        Map<StandingAxis, StandingBand> bands = new EnumMap<>(StandingAxis.class);
        for (StandingAxis axis : StandingAxis.values()) {
            bands.put(axis, StandingService.bandOf(player, axis));
        }
        PacketDistributor.sendToPlayer(player,
                new StandingSyncS2CPayload(values, bands, StandingService.bound()));
    }
}
