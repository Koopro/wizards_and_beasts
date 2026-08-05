package at.koopro.wizardsandbeasts.network.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.ApparitionCrackVariant;
import at.koopro.wizardsandbeasts.apparition.ApparitionPhase;
import at.koopro.wizardsandbeasts.apparition.ApparitionTier;
import at.koopro.wizardsandbeasts.apparition.splinch.SplinchTier;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Server → client presentation event. Fire-and-forget: the client renders it and replies with nothing.
 *
 * <p>Carries decisions, not inputs. {@code radius} and {@code crackVariant} arrive already resolved so that
 * proficiency and true heritage stay on the server — an observer learns how loud the crack was and what it
 * sounded like, never how good the caster is or what they really are.
 *
 * @param casterId     entity id of the caster, so a client can anchor effects without an entity lookup by UUID
 * @param tier         which travel tier
 * @param phase        where the attempt is
 * @param elapsed      ticks since the charge began; {@code 0} on a resolution event
 * @param windowOpen   tick the Deliberation window opens on
 * @param windowClose  last tick a release still counts
 * @param splinchTier  the outcome, or {@code null} while the attempt is still in flight
 * @param origin       where the caster stood
 * @param destination  where they arrived, or {@code null} while charging or after a catastrophe
 * @param radius       audible radius of the crack in blocks; never zero
 * @param crackVariant which crack to play
 */
@NullMarked
public record ApparitionPresentationS2CPayload(
        int casterId,
        ApparitionTier tier,
        ApparitionPhase phase,
        int elapsed,
        int windowOpen,
        int windowClose,
        @Nullable SplinchTier splinchTier,
        Vec3 origin,
        @Nullable Vec3 destination,
        int radius,
        ApparitionCrackVariant crackVariant) implements CustomPacketPayload {

    public static final Type<ApparitionPresentationS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "apparition_presentation"));

    public static final StreamCodec<ByteBuf, ApparitionPresentationS2CPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ApparitionPresentationS2CPayload decode(ByteBuf buf) {
                    int casterId = buf.readInt();
                    ApparitionTier tier = readEnum(buf, ApparitionTier.values(), ApparitionTier.BLINK);
                    ApparitionPhase phase = readEnum(buf, ApparitionPhase.values(), ApparitionPhase.IDLE);
                    int elapsed = buf.readInt();
                    int windowOpen = buf.readInt();
                    int windowClose = buf.readInt();
                    SplinchTier splinchTier = buf.readBoolean()
                            ? readEnum(buf, SplinchTier.values(), SplinchTier.CLEAN)
                            : null;
                    Vec3 origin = readVec(buf);
                    Vec3 destination = buf.readBoolean() ? readVec(buf) : null;
                    int radius = buf.readInt();
                    ApparitionCrackVariant crackVariant =
                            readEnum(buf, ApparitionCrackVariant.values(), ApparitionCrackVariant.WIZARD);
                    return new ApparitionPresentationS2CPayload(casterId, tier, phase, elapsed, windowOpen,
                            windowClose, splinchTier, origin, destination, radius, crackVariant);
                }

                @Override
                public void encode(ByteBuf buf, ApparitionPresentationS2CPayload payload) {
                    buf.writeInt(payload.casterId);
                    buf.writeInt(payload.tier.ordinal());
                    buf.writeInt(payload.phase.ordinal());
                    buf.writeInt(payload.elapsed);
                    buf.writeInt(payload.windowOpen);
                    buf.writeInt(payload.windowClose);
                    buf.writeBoolean(payload.splinchTier != null);
                    if (payload.splinchTier != null) {
                        buf.writeInt(payload.splinchTier.ordinal());
                    }
                    writeVec(buf, payload.origin);
                    buf.writeBoolean(payload.destination != null);
                    if (payload.destination != null) {
                        writeVec(buf, payload.destination);
                    }
                    buf.writeInt(payload.radius);
                    buf.writeInt(payload.crackVariant.ordinal());
                }
            };

    /** Bounds-checked so a malformed packet degrades to a sane default rather than throwing on the netty thread. */
    private static <E> E readEnum(ByteBuf buf, E[] values, E fallback) {
        int ordinal = buf.readInt();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    private static Vec3 readVec(ByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writeVec(ByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ApparitionPresentationS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPresentationState.accept(payload));
    }
}
