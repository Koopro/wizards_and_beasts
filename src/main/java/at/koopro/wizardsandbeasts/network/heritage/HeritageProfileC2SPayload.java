package at.koopro.wizardsandbeasts.network.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.identity.HeritageProfiles;
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
 * C→S: the Character Sheet's Heritage tab asks for its owner's profile (shown, then every couple of seconds while it
 * stays open). Carries nothing: the server answers only about the sender, so nobody can ask for anyone else's —
 * which is what keeps a condition private.
 */
@NullMarked
public record HeritageProfileC2SPayload() implements CustomPacketPayload {

    public static final Type<HeritageProfileC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "heritage_profile_request"));

    public static final StreamCodec<ByteBuf, HeritageProfileC2SPayload> STREAM_CODEC =
            StreamCodec.unit(new HeritageProfileC2SPayload());

    /** Ticks between answered requests per player. */
    private static final long MIN_TICKS_BETWEEN = 10;
    private static final PlayerScopedState<Long> LAST = PlayerScopedState.create("heritage_profile_request");

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HeritageProfileC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            long now = player.level().getGameTime();
            Long last = LAST.get(player);
            if (last != null && now - last < MIN_TICKS_BETWEEN && now >= last) {
                return;
            }
            LAST.put(player, now);
            PacketDistributor.sendToPlayer(player, new HeritageProfileS2CPayload(HeritageProfiles.build(player)));
        });
    }
}
