package at.koopro.wizardsandbeasts.client.dummy;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.dummy.DamageNumberS2CPayload;

/**
 * The numbers that float off a duelling dummy.
 *
 * <p>Projected onto the HUD rather than drawn as world geometry, for the reason
 * {@code WorldDebugPanel} sets out at length: this mod has no world-space text renderer, text drawn
 * at world scale goes soft at any distance it was not tuned for, and a damage readout that is hard
 * to read has failed at its only job. The projection below is that class's, kept in step with it.
 *
 * <p>State is a fixed-size queue of live numbers and nothing else. Each carries the world point it
 * was born at, so it stays where the blow landed while the dummy keeps being hit - and a dummy
 * dismantled mid-flight leaves its last numbers to finish rising instead of deleting them.
 */
@NullMarked
public final class DamageNumberOverlay {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "dummy_damage_numbers");

    /** Lifetime of one number, in ticks. Long enough to read a burst, short enough not to stack up. */
    private static final int LIFETIME_TICKS = 30;
    /** How far a number drifts upward over its life, in blocks. */
    private static final double RISE = 0.9;
    /** Sideways scatter, so two simultaneous hits do not print on top of each other. */
    private static final double SCATTER = 0.35;
    /**
     * Hard ceiling on live numbers. A channelled beam can land twenty hits a second; without a cap
     * a long session would grow this list without bound and every frame would pay for it.
     */
    private static final int MAX_LIVE = 48;
    /** Screen scale at one block away. Numbers shrink with distance, but never below MIN_SCALE. */
    private static final float BASE_SCALE = 1.6f;
    private static final float MIN_SCALE = 0.5f;
    /** Beyond this the number is dropped rather than drawn as an illegible speck. */
    private static final double MAX_DISTANCE = 48.0;

    private static final Deque<Number> LIVE = new ArrayDeque<>();

    private DamageNumberOverlay() {}

    /** One number, fixed in the world at the point the blow landed. */
    private record Number(Vec3 origin, double drift, String text, int rgb, long bornTick) {}

    public static void add(DamageNumberS2CPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        while (LIVE.size() >= MAX_LIVE) {
            LIVE.pollFirst();
        }
        boolean healing = payload.amount() < 0.0f;
        float magnitude = Math.abs(payload.amount());
        float shown = Config.dummyShowHearts ? magnitude / 2.0f : magnitude;
        String text = (healing ? "+" : "") + String.format(Locale.ROOT, "%.1f", shown);
        double drift = (mc.level.random.nextDouble() - 0.5) * 2.0 * SCATTER;
        LIVE.addLast(new Number(new Vec3(payload.x(), payload.y(), payload.z()), drift, text,
                payload.colour(), mc.level.getGameTime()));
    }

    /** Drops everything. Called on disconnect so numbers do not survive into the next world. */
    public static void clear() {
        LIVE.clear();
    }

    /**
     * Draws every live number.
     *
     * <p>Deliberately does not consult {@code dummyDamageNumbers}: who sees a number is the
     * server's decision, taken when it chooses whom to send the payload to. The common config is
     * not synced, so a client re-checking it here would silently drop numbers the server had
     * decided this player should see.
     */
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || LIVE.isEmpty()) {
            return;
        }
        long now = mc.level.getGameTime();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.gameRenderer.getMainCamera().position();

        LIVE.removeIf(number -> now - number.bornTick() >= LIFETIME_TICKS);
        for (Number number : LIVE) {
            float age = (now - number.bornTick()) + partial;
            float life = Mth.clamp(age / LIFETIME_TICKS, 0.0f, 1.0f);
            Vec3 anchor = number.origin().add(number.drift(), RISE * life, 0.0);
            double distance = anchor.distanceTo(eye);
            if (distance > MAX_DISTANCE) {
                continue;
            }
            float[] screen = project(mc, anchor);
            if (screen == null) {
                continue;
            }
            draw(graphics, mc.font, number, screen, life, distance);
        }
    }

    private static void draw(GuiGraphics graphics, Font font, Number number, float[] screen,
                             float life, double distance) {
        // Fades over the back half of its life: a number that vanishes at full opacity reads as a
        // dropped frame rather than as one that has finished.
        float alpha = life < 0.5f ? 1.0f : 1.0f - (life - 0.5f) * 2.0f;
        int argb = (Math.round(Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f) << 24) | (number.rgb() & 0xFFFFFF);
        if ((argb >>> 24) == 0) {
            return;
        }
        float scale = Math.max(MIN_SCALE, (float) (BASE_SCALE / Math.max(1.0, distance * 0.25)));
        int width = font.width(number.text());

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(screen[0], screen[1]);
        pose.scale(scale, scale);
        // Drawn with the vanilla drop shadow: these land on top of whatever the world happens to be,
        // and a pale number over a pale wall is unreadable without one.
        graphics.drawString(font, number.text(), -width / 2, 0, argb, true);
        pose.popMatrix();
    }

    /**
     * Anchor point in gui-scaled pixels, or {@code null} when it is behind the camera.
     *
     * <p>Built from the camera basis and the FOV option rather than the projection matrix, which is
     * private; see {@code WorldDebugPanel#project} for the full reasoning and the one known
     * consequence (a few pixels of drift while an FOV modifier is animating).
     *
     * @return {@code {x, y}}
     */
    private static float @Nullable [] project(Minecraft mc, Vec3 anchor) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return null;
        }
        Vec3 toTarget = anchor.subtract(camera.position());
        Vector3fc forward = camera.forwardVector();
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();

        double depth = dot(toTarget, forward);
        if (depth <= 0.1) {
            return null;
        }
        double rightward = -dot(toTarget, left);
        double upward = dot(toTarget, up);

        double tanHalfFov = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        if (tanHalfFov <= 0.0) {
            return null;
        }
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double ndcX = (rightward / depth) / (tanHalfFov * aspect);
        double ndcY = (upward / depth) / tanHalfFov;

        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        return new float[]{
                (float) ((ndcX * 0.5 + 0.5) * guiWidth),
                (float) ((0.5 - ndcY * 0.5) * guiHeight)
        };
    }

    private static double dot(Vec3 vec, Vector3fc other) {
        return vec.x * other.x() + vec.y * other.y() + vec.z * other.z();
    }
}
