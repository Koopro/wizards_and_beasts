package at.koopro.wizardsandbeasts.network.pose;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.pose.FlightPoseState;
import at.koopro.wizardsandbeasts.pose.PoseOverride;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;
import java.util.UUID;

/**
 * Server → Client: one player's pose override, sent to everyone tracking them.
 *
 * <p>S→C only. The command is the only writer and it runs server-side, so there is no C→S
 * counterpart to add — a client that could ask for a pose could ask for one on somebody else.
 *
 * @param playerUuid whose pose this is; not necessarily the receiving client's own player
 */
@NullMarked
public record PoseOverrideSyncS2CPayload(UUID playerUuid, PoseOverride override)
        implements CustomPacketPayload {

    public static final Type<PoseOverrideSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "pose_override_sync"));

    /**
     * Ordinal-indexed, with -1 for "no override".
     *
     * <p>An ordinal is safe here in a way it would not be on disk: the packet is written and read by
     * the same build. The attachment's own persistence goes through {@code PoseOverride.CODEC},
     * which is name-keyed, so reordering the enum cannot corrupt a save.
     */
    public static final StreamCodec<ByteBuf, PoseOverrideSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PoseOverrideSyncS2CPayload decode(ByteBuf buf) {
            UUID uuid = new UUID(buf.readLong(), buf.readLong());
            int ordinal = ByteBufCodecs.VAR_INT.decode(buf);
            boolean manual = buf.readBoolean();
            FlightPoseState[] states = FlightPoseState.values();
            Optional<FlightPoseState> state = ordinal >= 0 && ordinal < states.length
                    ? Optional.of(states[ordinal])
                    : Optional.empty();
            return new PoseOverrideSyncS2CPayload(uuid, new PoseOverride(state, manual));
        }

        @Override
        public void encode(ByteBuf buf, PoseOverrideSyncS2CPayload payload) {
            buf.writeLong(payload.playerUuid().getMostSignificantBits());
            buf.writeLong(payload.playerUuid().getLeastSignificantBits());
            ByteBufCodecs.VAR_INT.encode(buf,
                    payload.override().state().map(Enum::ordinal).orElse(-1));
            buf.writeBoolean(payload.override().manual());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
