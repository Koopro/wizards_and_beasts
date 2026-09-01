package at.koopro.wizardsandbeasts.network.polyjuice;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import at.koopro.wizardsandbeasts.network.PacketCodecUtils;
import at.koopro.wizardsandbeasts.polyjuice.PolyjuiceState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Server → Client: who a player currently looks like.
 *
 * <p>Broadcast to everybody tracking them <b>and to themselves</b>, the same shape as
 * {@code PetrifiedStateSyncS2CPayload}. A disguise only the drinker could see would be a screen
 * effect; one everyone else could see but they could not would be worse, because they would have no
 * way to tell whether it had worn off.
 *
 * <h2>What is on the wire</h2>
 * <p>The target's UUID and name, and nothing else. Not a skin, not a texture, not a profile with
 * properties: the client already knows how to fetch a skin for a UUID, and sending signed profile
 * data would be both larger and a way for a server to push arbitrary textures at clients.
 *
 * <p>An <b>empty</b> target id means "not disguised" and is how a revert is transmitted, so there is
 * one packet shape rather than two and no way for a set and a clear to arrive out of order.
 */
public record PolyjuiceSyncS2CPayload(UUID playerUUID, UUID targetId, String targetName)
        implements CustomPacketPayload {

    /** Sentinel for "no disguise". A zero UUID is never a real player. */
    public static final UUID NONE = new UUID(0L, 0L);

    /** Well above any legal Minecraft name; a bound so a hostile packet cannot allocate freely. */
    private static final int MAX_NAME_LENGTH = 64;

    public static final Type<PolyjuiceSyncS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "polyjuice_sync"));

    public static final StreamCodec<ByteBuf, PolyjuiceSyncS2CPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PolyjuiceSyncS2CPayload decode(ByteBuf buf) {
            UUID who = PacketCodecUtils.readUUID(buf);
            UUID target = PacketCodecUtils.readUUID(buf);
            String name = ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).decode(buf);
            return new PolyjuiceSyncS2CPayload(who, target, name);
        }

        @Override
        public void encode(ByteBuf buf, PolyjuiceSyncS2CPayload pkt) {
            PacketCodecUtils.writeUUID(buf, pkt.playerUUID);
            PacketCodecUtils.writeUUID(buf, pkt.targetId);
            ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).encode(buf, pkt.targetName);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void broadcast(ServerPlayer player, PolyjuiceState state) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, of(player.getUUID(), state));
    }

    /** Push one player's disguise to one viewer — for a client that has just started tracking. */
    public static void sendTo(ServerPlayer viewer, UUID subject, PolyjuiceState state) {
        PacketDistributor.sendToPlayer(viewer, of(subject, state));
    }

    private static PolyjuiceSyncS2CPayload of(UUID who, PolyjuiceState state) {
        return state.isDisguised()
                ? new PolyjuiceSyncS2CPayload(who, state.targetId().orElse(NONE), state.targetName())
                : new PolyjuiceSyncS2CPayload(who, NONE, "");
    }

    public static void handleClient(PolyjuiceSyncS2CPayload packet,
                                    net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPayloadHandlers.handlePolyjuiceSync(packet));
    }
}
