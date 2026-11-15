package at.koopro.wizardsandbeasts.network.spell;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The spell VFX payloads survive the wire. A singleplayer world never serializes a payload (the local connection
 * hands the object across), so the in-game captures prove the looks but not the codecs; a dedicated server does.
 */
class SpellVfxPayloadCodecTest {

    private static void near(Vec3 expected, Vec3 actual, double eps) {
        assertEquals(expected.x, actual.x, eps);
        assertEquals(expected.y, actual.y, eps);
        assertEquals(expected.z, actual.z, eps);
    }

    @Test
    void everyCueStageRoundTrips() {
        for (SpellCueS2CPayload.Stage stage : SpellCueS2CPayload.Stage.values()) {
            SpellCueS2CPayload sent = new SpellCueS2CPayload("wizards_and_beasts:confringo", stage,
                    new Vec3(-12345.25, 71.5, 9876.125), new Vec3(0.25, -9.5, 3.0), 2.5f, 0xFF9CCBFF, 4711);
            ByteBuf buf = Unpooled.buffer();
            SpellCueS2CPayload.STREAM_CODEC.encode(buf, sent);
            SpellCueS2CPayload got = SpellCueS2CPayload.STREAM_CODEC.decode(buf);
            assertEquals(sent.spellId(), got.spellId());
            assertEquals(stage, got.stage());
            near(sent.pos(), got.pos(), 0.0);
            near(sent.normal(), got.normal(), 1.0e-5);
            assertEquals(sent.scale(), got.scale());
            assertEquals(sent.argb(), got.argb());
            assertEquals(sent.entity(), got.entity());
            assertEquals(0, buf.readableBytes(), "nothing left over");
        }
    }

    @Test
    void anUnknownCasterIsCarried() {
        SpellCueS2CPayload sent = new SpellCueS2CPayload("protego", SpellCueS2CPayload.Stage.BREAK, Vec3.ZERO,
                new Vec3(0, 1, 0), 1f, 0, -1);
        ByteBuf buf = Unpooled.buffer();
        SpellCueS2CPayload.STREAM_CODEC.encode(buf, sent);
        assertEquals(-1, SpellCueS2CPayload.STREAM_CODEC.decode(buf).entity());
    }

    @Test
    void shapesRoundTripWithTheirParticle() {
        RegistryAccess access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (var particle : new net.minecraft.core.particles.ParticleOptions[]{
                ParticleTypes.FLAME, new DustParticleOptions(0xB2D8FF, 1.0f)}) {
            for (SpellVfxShapeS2CPayload.Shape shape : SpellVfxShapeS2CPayload.Shape.values()) {
                SpellVfxShapeS2CPayload sent = new SpellVfxShapeS2CPayload(particle, shape, new Vec3(1, 2, 3),
                        new Vec3(0, 1, 0), 2.5f, 16, 0.035f, 0.75f);
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access,
                        ConnectionType.NEOFORGE);
                SpellVfxShapeS2CPayload.STREAM_CODEC.encode(buf, sent);
                SpellVfxShapeS2CPayload got = SpellVfxShapeS2CPayload.STREAM_CODEC.decode(buf);
                // Dust options have no equals; the type and every other field must match.
                assertEquals(sent.particle().getType(), got.particle().getType());
                assertEquals(new SpellVfxShapeS2CPayload(ParticleTypes.FLAME, sent.shape(), sent.a(), sent.b(),
                        sent.radius(), sent.points(), sent.jitter(), sent.phase()),
                        new SpellVfxShapeS2CPayload(ParticleTypes.FLAME, got.shape(), got.a(), got.b(),
                                got.radius(), got.points(), got.jitter(), got.phase()));
                assertEquals(0, buf.readableBytes(), "nothing left over");
            }
        }
    }

    @Test
    void shapePointsAreCapped() {
        RegistryAccess access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access, ConnectionType.NEOFORGE);
        SpellVfxShapeS2CPayload.STREAM_CODEC.encode(buf, new SpellVfxShapeS2CPayload(ParticleTypes.FLAME,
                SpellVfxShapeS2CPayload.Shape.LINE, Vec3.ZERO, new Vec3(40, 0, 0), 0f, 100_000, 0f, 0f));
        assertEquals(SpellVfxShapeS2CPayload.MAX_POINTS, SpellVfxShapeS2CPayload.STREAM_CODEC.decode(buf).points());
    }
}
