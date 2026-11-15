package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * A particle <em>shape</em> — a line, a ring or a shell — sent as one packet and laid out by each client under its
 * own budget ({@code client.vfx.VfxBudget}).
 *
 * <h2>Why</h2>
 * <p>{@code ServerLevel.sendParticles} sends one packet per call to every player within 32 blocks. Every particle
 * line, ring and shell in the spell code was a loop of count-1 calls, so a 20-block cone cast cost 201 packets per
 * observer, a Patronus aura 160 a second, a Protego planting ring 64 a second (audit 2026-11-15). The shape is
 * the same information in one packet, and the client — not the server's {@code perfProfile} — decides how many
 * points it can afford.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@link Shape#LINE}: {@code a} → {@code b}, {@code points} spaced evenly, each offset by a gaussian of
 *       {@code jitter} — what {@code sendParticles(…, 1, j, j, j, 0)} did at each step.</li>
 *   <li>{@link Shape#RING}: centre {@code a}, plane normal {@code b} (unit), {@code radius}, {@code points} from angle
 *       {@code phase}.</li>
 *   <li>{@link Shape#SHELL}: centre {@code a}, {@code radius}, {@code points} spiralled over the sphere from the top
 *       down to {@code y = 1 − phase} (1 = upper half, 2 = whole sphere).</li>
 * </ul>
 */
@NullMarked
public record SpellVfxShapeS2CPayload(
        ParticleOptions particle,
        Shape shape,
        Vec3 a,
        Vec3 b,
        float radius,
        int points,
        float jitter,
        float phase
) implements CustomPacketPayload {

    /** Hard ceiling per shape; a caller asking for more gets this. */
    public static final int MAX_POINTS = 256;
    /** Same reach as an unforced {@code sendParticles}. */
    public static final double RANGE = 32.0;

    public enum Shape { LINE, RING, SHELL }

    public static final Type<SpellVfxShapeS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "spell_vfx_shape"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpellVfxShapeS2CPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SpellVfxShapeS2CPayload decode(RegistryFriendlyByteBuf buf) {
                    ParticleOptions particle = ParticleTypes.STREAM_CODEC.decode(buf);
                    Shape shape = Shape.values()[Math.floorMod(buf.readByte(), Shape.values().length)];
                    Vec3 a = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                    Vec3 b = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                    float radius = buf.readFloat();
                    int points = Math.min(MAX_POINTS, ByteBufCodecs.VAR_INT.decode(buf));
                    float jitter = buf.readFloat();
                    float phase = buf.readFloat();
                    return new SpellVfxShapeS2CPayload(particle, shape, a, b, radius, points, jitter, phase);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SpellVfxShapeS2CPayload p) {
                    ParticleTypes.STREAM_CODEC.encode(buf, p.particle);
                    buf.writeByte(p.shape.ordinal());
                    buf.writeDouble(p.a.x);
                    buf.writeDouble(p.a.y);
                    buf.writeDouble(p.a.z);
                    buf.writeDouble(p.b.x);
                    buf.writeDouble(p.b.y);
                    buf.writeDouble(p.b.z);
                    buf.writeFloat(p.radius);
                    ByteBufCodecs.VAR_INT.encode(buf, Math.min(MAX_POINTS, Math.max(0, p.points)));
                    buf.writeFloat(p.jitter);
                    buf.writeFloat(p.phase);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** A line of {@code points} particles from {@code from} to {@code to}. */
    public static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, int points,
                            float jitter) {
        // Everyone within reach of any point of the line, as when each point was its own packet.
        send(level, from.lerp(to, 0.5), RANGE + from.distanceTo(to) * 0.5,
                new SpellVfxShapeS2CPayload(particle, Shape.LINE, from, to, 0f, points, jitter, 0f));
    }

    /** A flat ring round {@code centre}, lying in the plane whose normal is {@code normal}. */
    public static void ring(ServerLevel level, ParticleOptions particle, Vec3 centre, Vec3 normal, double radius,
                            int points, float phase) {
        send(level, centre, RANGE + radius,
                new SpellVfxShapeS2CPayload(particle, Shape.RING, centre, normal, (float) radius, points, 0f, phase));
    }

    /** A horizontal ring round {@code centre}. */
    public static void ring(ServerLevel level, ParticleOptions particle, Vec3 centre, double radius, int points) {
        ring(level, particle, centre, new Vec3(0, 1, 0), radius, points, 0f);
    }

    /** A spiral of points over a sphere; {@code coverage} 1 is the top half, 2 the whole sphere. */
    public static void shell(ServerLevel level, ParticleOptions particle, Vec3 centre, double radius, int points,
                             float coverage) {
        send(level, centre, RANGE + radius,
                new SpellVfxShapeS2CPayload(particle, Shape.SHELL, centre, Vec3.ZERO, (float) radius, points, 0f,
                        coverage));
    }

    private static void send(ServerLevel level, Vec3 near, double range, SpellVfxShapeS2CPayload payload) {
        if (payload.points <= 0) {
            return;
        }
        PacketDistributor.sendToPlayersNear(level, null, near.x, near.y, near.z, range, payload);
    }
}
