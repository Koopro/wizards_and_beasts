package at.koopro.wizardsandbeasts.network.location;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.location.LocationProgression;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * C→S: the Character Sheet asks where its owner stands at every place. Carries nothing — the server answers only
 * about the sender, from the stored standing, so nobody can ask about anyone else.
 */
@NullMarked
public record PlaceStandingsC2SPayload() implements CustomPacketPayload {

    public static final Type<PlaceStandingsC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "place_standings_request"));

    public static final StreamCodec<ByteBuf, PlaceStandingsC2SPayload> STREAM_CODEC =
            StreamCodec.unit(new PlaceStandingsC2SPayload());

    /** Ticks between answered requests per player. */
    private static final long MIN_TICKS_BETWEEN = 10;
    private static final PlayerScopedState<Long> LAST = PlayerScopedState.create("place_standings_request");

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlaceStandingsC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            long now = player.level().getGameTime();
            if (!PlayerScopedState.throttle(LAST, player, now, MIN_TICKS_BETWEEN)) {
                return;
            }
            PacketDistributor.sendToPlayer(player, new PlaceStandingsS2CPayload(LocationProgression.standings(player)));
        });
    }
}
