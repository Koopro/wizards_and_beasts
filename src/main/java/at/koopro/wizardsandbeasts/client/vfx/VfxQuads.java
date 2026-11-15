package at.koopro.wizardsandbeasts.client.vfx;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flat textured quads in the world that grow, turn and fade: shockwave rings, scorch and frost marks, rune circles,
 * corruption creeping over the ground. The one geometry the mod's particles could not draw — a particle is a small
 * camera-facing square, and a ring on the ground or a mark on a wall is neither (audit 2026-11-15).
 *
 * <h2>Two looks</h2>
 * <ul>
 *   <li>{@link Look#GLOW}: full-bright and lifted a third of the way to white, for light — a shockwave, a ward's
 *       runes.</li>
 *   <li>{@link Look#LIT}: lit by the world at the spot, for matter — a scorch is dark in daylight and darker at night,
 *       never a glowing black.</li>
 * </ul>
 * Textures are white or grey (tools/vfx_textures.py) and take the quad's colour, so one ring serves every spell.
 *
 * <h2>Budget</h2>
 * Live quads are capped by {@code vfxQuality} (24 / 64 / 128); a new quad over the cap retires the oldest. Quads
 * further than 48 blocks are not spawned. Drawn after translucent blocks so water does not hide a mark on a pool's
 * floor, depth-tested so walls do.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class VfxQuads {

    public enum Look { GLOW, LIT }

    /** Size curve over a quad's life. */
    public enum Growth {
        /** Fast then settling: a shockwave. */
        BURST,
        /** Even: creep, a slow ring. */
        LINEAR,
        /** Size1 reached in the first fifth, then held: a mark that lands. */
        LAND
    }

    public static final Identifier SHOCKWAVE = tex("shockwave");
    public static final Identifier RING_THIN = tex("ring_thin");
    public static final Identifier GLOW_DISC = tex("glow_disc");
    public static final Identifier SCORCH = tex("scorch");
    public static final Identifier RUNE_CIRCLE = tex("rune_circle");
    public static final Identifier CORRUPTION = tex("corruption");
    public static final Identifier FROST = tex("frost");
    public static final Identifier SLASH = tex("slash");
    public static final Identifier CRACK = tex("crack");

    static final double MAX_SPAWN_DISTANCE = 48.0;
    /** Lifts a quad off the surface it lies on, so it does not z-fight the block face. */
    private static final double SURFACE_LIFT = 0.012;
    /** Alpha steps: whole-step fades, like the mod's particles, not a smooth ramp. */
    private static final int ALPHA_STEPS = 6;

    /** One quad. Mutable so a caller can keep it alive (a held curse refreshes its creep). */
    public static final class Quad {
        final Identifier texture;
        final Look look;
        final Vec3 pos;
        final Vec3 normal;
        final Vec3 u;
        final Vec3 v;
        final float size0;
        float size1;
        final int argb;
        final long born;
        int life;
        final int fadeIn;
        final int fadeOut;
        final float spinPerTick;
        final float angle0;
        final Growth growth;
        boolean flicker;
        int light;

        Quad(Identifier texture, Look look, Vec3 pos, Vec3 normal, float size0, float size1, int argb, long born,
             int life, int fadeIn, int fadeOut, float spinPerTick, float angle0, Growth growth) {
            this.texture = texture;
            this.look = look;
            this.normal = normal.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : normal.normalize();
            this.pos = pos.add(this.normal.scale(SURFACE_LIFT));
            Vec3[] basis = basis(this.normal);
            this.u = basis[0];
            this.v = basis[1];
            this.size0 = size0;
            this.size1 = size1;
            this.argb = argb;
            this.born = born;
            this.life = life;
            this.fadeIn = fadeIn;
            this.fadeOut = fadeOut;
            this.spinPerTick = spinPerTick;
            this.angle0 = angle0;
            this.growth = growth;
        }

        /** Lives {@code ticks} more from now (a held curse calls this on every cue). */
        public void keepAlive(long now, int ticks) {
            life = Math.max(life, (int) (now - born) + ticks);
        }

        public void growTo(float size) {
            size1 = Math.max(size1, size);
        }

        public void flicker(boolean on) {
            flicker = on;
        }
    }

    private static final ArrayDeque<Quad> LIVE = new ArrayDeque<>();

    private VfxQuads() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/vfx/" + name + ".png");
    }

    /**
     * The vanilla Particles option thins rings and marks too: they are not particles, but a player who turned
     * particles down asked for less on screen. Half on Decreased, a quarter on Minimal.
     */
    static float particleShare(net.minecraft.server.level.ParticleStatus status) {
        return switch (status) {
            case ALL -> 1.0f;
            case DECREASED -> 0.5f;
            case MINIMAL -> 0.25f;
        };
    }

    static int cap(Config.VfxQuality q) {
        return switch (q) {
            case LOW -> 24;
            case MEDIUM -> 64;
            case HIGH -> 128;
        };
    }

    /**
     * Adds a quad, or returns null when it is too far to matter.
     *
     * @param delay ticks before it appears (a second ring a beat after the first)
     */
    public static @Nullable Quad spawn(Identifier texture, Look look, Vec3 pos, Vec3 normal, float size0, float size1,
                             int argb, int life, int fadeIn, int fadeOut, float spinPerTick, Growth growth,
                             int delay) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return null;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().position();
        if (eye.distanceToSqr(pos) > MAX_SPAWN_DISTANCE * MAX_SPAWN_DISTANCE) {
            return null;
        }
        int cap = Math.max(4, Math.round(cap(VfxBudget.quality()) * particleShare(mc.options.particles().get())));
        while (LIVE.size() >= cap) {
            LIVE.pollFirst();
        }
        float angle0 = level.random.nextFloat() * Mth.TWO_PI;
        int colour = look == Look.GLOW ? lift(argb, GLOW_LIFT) : argb;
        Quad quad = new Quad(texture, look, pos, normal, size0, size1, colour, level.getGameTime() + delay, life,
                fadeIn, fadeOut, spinPerTick, angle0, growth);
        quad.light = look == Look.GLOW ? LightTexture.FULL_BRIGHT
                : LevelRenderer.getLightColor(level, BlockPos.containing(quad.pos.add(quad.normal.scale(0.5))));
        LIVE.addLast(quad);
        return quad;
    }

    /** How far a glowing quad's colour is lifted toward white: light reads as light, not as paint. */
    static final float GLOW_LIFT = 0.3f;

    static int lift(int argb, float t) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Math.round(r + (255 - r) * t);
        g = Math.round(g + (255 - g) * t);
        b = Math.round(b + (255 - b) * t);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static int live() {
        return LIVE.size();
    }

    /** Size at fraction {@code t} of the quad's life. */
    static float sizeAt(Growth growth, float size0, float size1, float t) {
        float k = switch (growth) {
            case BURST -> 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
            case LINEAR -> t;
            case LAND -> Math.min(1.0f, t * 5.0f);
        };
        return size0 + (size1 - size0) * k;
    }

    /** Alpha (0..1) at {@code age} ticks into a life of {@code life}: stepped fade in and out. */
    static float alphaAt(float age, int life, int fadeIn, int fadeOut) {
        if (age < 0 || age >= life) {
            return 0f;
        }
        float a = 1f;
        if (fadeIn > 0 && age < fadeIn) {
            a = age / fadeIn;
        }
        float left = life - age;
        if (fadeOut > 0 && left < fadeOut) {
            a = Math.min(a, left / fadeOut);
        }
        return Math.max(0f, (float) Math.ceil(a * ALPHA_STEPS) / ALPHA_STEPS);
    }

    /** Two unit vectors spanning the plane whose normal is {@code n}. */
    static Vec3[] basis(Vec3 n) {
        Vec3 helper = Math.abs(n.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = helper.cross(n).normalize();
        Vec3 v = n.cross(u).normalize();
        return new Vec3[]{u, v};
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (LIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        LIVE.removeIf(q -> now - q.born >= q.life);
        if (LIVE.isEmpty()) {
            return;
        }
        Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        // One batch per texture and look; a handful of textures exist, so this is a handful of draws.
        Map<RenderType, List<Quad>> batches = new LinkedHashMap<>();
        for (Quad q : LIVE) {
            RenderType type = VfxRenderTypes.quad(q.texture);
            batches.computeIfAbsent(type, k -> new ArrayList<>()).add(q);
        }
        for (Map.Entry<RenderType, List<Quad>> batch : batches.entrySet()) {
            VertexConsumer vc = buffers.getBuffer(batch.getKey());
            for (Quad q : batch.getValue()) {
                draw(vc, pose, q, now, partial, cam);
            }
            buffers.endBatch(batch.getKey());
        }
    }

    private static void draw(VertexConsumer vc, PoseStack.Pose pose, Quad q, long now, float partial, Vec3 cam) {
        float age = (now - q.born) + partial;
        float alpha = alphaAt(age, q.life, q.fadeIn, q.fadeOut);
        if (q.flicker && ((int) age / 2) % 2 == 1) {
            alpha *= 0.35f;
        }
        if (alpha <= 0f) {
            return;
        }
        float t = Mth.clamp(age / q.life, 0f, 1f);
        float half = sizeAt(q.growth, q.size0, q.size1, t) * 0.5f;
        float angle = q.angle0 + q.spinPerTick * age * Mth.DEG_TO_RAD;
        float c = Mth.cos(angle) * half;
        float s = Mth.sin(angle) * half;
        // Rotated in-plane axes, scaled to the half size.
        double ax = q.u.x * c + q.v.x * s;
        double ay = q.u.y * c + q.v.y * s;
        double az = q.u.z * c + q.v.z * s;
        double bx = -q.u.x * s + q.v.x * c;
        double by = -q.u.y * s + q.v.y * c;
        double bz = -q.u.z * s + q.v.z * c;
        float px = (float) (q.pos.x - cam.x);
        float py = (float) (q.pos.y - cam.y);
        float pz = (float) (q.pos.z - cam.z);
        int a = Math.round(alpha * ((q.argb >>> 24) == 0 ? 255 : (q.argb >>> 24)));
        int colour = (a << 24) | (q.argb & 0xFFFFFF);
        float nx = (float) q.normal.x;
        float ny = (float) q.normal.y;
        float nz = (float) q.normal.z;
        vertex(vc, pose, px - (float) ax - (float) bx, py - (float) ay - (float) by, pz - (float) az - (float) bz,
                0f, 0f, colour, q.light, nx, ny, nz);
        vertex(vc, pose, px - (float) ax + (float) bx, py - (float) ay + (float) by, pz - (float) az + (float) bz,
                0f, 1f, colour, q.light, nx, ny, nz);
        vertex(vc, pose, px + (float) ax + (float) bx, py + (float) ay + (float) by, pz + (float) az + (float) bz,
                1f, 1f, colour, q.light, nx, ny, nz);
        vertex(vc, pose, px + (float) ax - (float) bx, py + (float) ay - (float) by, pz + (float) az - (float) bz,
                1f, 0f, colour, q.light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int colour, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(colour)
                .setUv(u, v)
                .setLight(light);
    }

    /** A world change leaves nothing behind. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        LIVE.clear();
    }

    /** Drops every quad (tests, and the spell showcase between spells). */
    public static void clear() {
        LIVE.clear();
    }
}
