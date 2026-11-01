package at.koopro.wizardsandbeasts.network.journey;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * S→C: the Apprentice's Path as the server judges it — the step ids this player walks, in order, and the ids done —
 * and the Chamber of Secrets stages reached, which the sheet points at once the path is walked. Ids only: the client
 * words them from its own lang file. An empty step list means the path is switched off for this player.
 */
@NullMarked
public record ApprenticePathS2CPayload(List<String> steps, List<String> done, List<String> chamber)
        implements CustomPacketPayload {

    /** Far above the step count; bounds what a malformed packet can make the client allocate. */
    private static final int MAX_IDS = 64;

    public static final Type<ApprenticePathS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "apprentice_path"));

    public static final StreamCodec<ByteBuf, ApprenticePathS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_IDS)), ApprenticePathS2CPayload::steps,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_IDS)), ApprenticePathS2CPayload::done,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_IDS)), ApprenticePathS2CPayload::chamber,
            ApprenticePathS2CPayload::new);

    public ApprenticePathS2CPayload {
        steps = List.copyOf(steps);
        done = List.copyOf(done);
        chamber = List.copyOf(chamber);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, ApprenticePathS2CPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
