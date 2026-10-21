package at.koopro.wizardsandbeasts.network.travel;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.travel.profile.TravelActions;
import at.koopro.wizardsandbeasts.travel.profile.TravelProfile;
import at.koopro.wizardsandbeasts.travel.profile.TravelProfiles;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * C→S from the Travel tab: "send me my travel profile", optionally after doing one of its actions. Carries an action
 * kind and its argument and nothing else — the server answers only about the sender, and every action is re-checked
 * through the door it belongs to ({@link TravelActions}).
 *
 * @param action ordinal of a {@link TravelProfile.ActionKind}; {@code NONE} just refreshes
 * @param arg    the action's argument (a destination name, a hearth address), bounded
 */
@NullMarked
public record TravelProfileC2SPayload(int action, String arg) implements CustomPacketPayload {

    public static final Type<TravelProfileC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "travel_profile_request"));

    /** Longest argument read: the longest Floo address (48) with room to spare. */
    public static final int MAX_ARG = 64;

    public static final StreamCodec<ByteBuf, TravelProfileC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TravelProfileC2SPayload::action,
            ByteBufCodecs.stringUtf8(MAX_ARG), TravelProfileC2SPayload::arg,
            TravelProfileC2SPayload::new);

    /** Ticks between answered requests per player; an action spends the same budget. */
    private static final long MIN_TICKS_BETWEEN = 10;
    private static final PlayerScopedState<Long> LAST = PlayerScopedState.create("travel_profile_request");

    public static TravelProfileC2SPayload refresh() {
        return new TravelProfileC2SPayload(TravelProfile.ActionKind.NONE.ordinal(), "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TravelProfileC2SPayload payload, IPayloadContext context) {
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
            TravelActions.perform(player, TravelProfile.ActionKind.of(payload.action()), payload.arg());
            PacketDistributor.sendToPlayer(player, new TravelProfileS2CPayload(TravelProfiles.build(player)));
        });
    }
}
