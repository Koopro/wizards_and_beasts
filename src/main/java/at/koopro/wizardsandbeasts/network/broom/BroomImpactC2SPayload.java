package at.koopro.wizardsandbeasts.network.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;

/**
 * The rider's client reporting that the broom it is flying hit something.
 *
 * <h2>Why the client is the one that saw it</h2>
 * A ridden vehicle is client-authoritative in vanilla ({@code Player.isClientAuthoritative()}), and the
 * server's copy of the broom has its position overwritten from {@code ServerboundMoveVehiclePacket} every
 * tick. The position it receives has already had the client's collisions resolved out of it, so a server-side
 * {@code move()} along that delta sails straight through the wall the client stopped at: the server cannot
 * observe these collisions at all. It used to appear to, by simulating its own flight from stale input — and
 * what that produced was durability loss and crash damage for impacts the rider never flew into.
 *
 * <h2>What is trusted, and what is not</h2>
 * The client sends a severity it computed with {@code BroomFlightRules}, which is pure and shared, so both
 * sides agree on the scale. The server does not take it on faith:
 * <ul>
 *   <li>the sender must be the broom's controlling passenger — you cannot crash somebody else's broom;</li>
 *   <li>severity is clamped to a sane band, so a modified client cannot report an impact worse than flying
 *       flat out into a cliff, which is the worst thing the game can do to a broom anyway;</li>
 *   <li>a report is only acted on once per {@code IMPACT_REPORT_INTERVAL_TICKS}, so a client cannot bill
 *       itself for the same wall sixty times a second.</li>
 * </ul>
 * The exposure that remains is a client under-reporting its own crashes, which costs that player durability
 * they should have paid and affects nobody else. This is the same trust vanilla already extends here — the
 * vehicle's fall damage is computed from client-reported motion in {@code handleMoveVehicle}.
 *
 * @param broomId  entity id of the broom, checked against what the sender is actually riding
 * @param severity impact severity on {@code BroomTuning}'s scale, where 1.0 is a severe crash
 * @param gentle   the client classified this as a landing rather than an impact
 */
@NullMarked
public record BroomImpactC2SPayload(int broomId, float severity, boolean gentle)
        implements CustomPacketPayload {

    /**
     * Hardest impact the server will act on. Above {@code SEVERE_IMPACT_THRESHOLD} because severity is a
     * ratio that legitimately overshoots on a corner, and far below anything that would let a report be
     * worth forging: a severe impact already destroys the broom.
     */
    public static final float MAX_SEVERITY = 2.0f;

    /** Minimum ticks between two reports the server will act on, per rider. */
    public static final int IMPACT_REPORT_INTERVAL_TICKS = 5;

    public static final Type<BroomImpactC2SPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "broom_impact"));

    public static final StreamCodec<ByteBuf, BroomImpactC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BroomImpactC2SPayload::broomId,
            ByteBufCodecs.FLOAT, BroomImpactC2SPayload::severity,
            ByteBufCodecs.BOOL, BroomImpactC2SPayload::gentle,
            BroomImpactC2SPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BroomImpactC2SPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            // The vehicle the server believes this player is on, not the id they named. The id travels only
            // so a report that arrives a tick after a dismount can be discarded rather than applied to
            // whatever the player climbed onto next.
            if (!(player.getVehicle() instanceof BroomEntity broom)
                    || broom.getId() != pkt.broomId
                    || broom.getControllingPassenger() != player) {
                return;
            }
            broom.acceptImpactReport(pkt.gentle, pkt.severity);
        });
    }
}
