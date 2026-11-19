package at.koopro.wizardsandbeasts.client.vfx;

import at.koopro.wizardsandbeasts.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * This client's one spell-VFX budget: how many particles a spell effect may spend, given the player's quality
 * setting, how far away the effect is, and how much has already been spent this tick.
 *
 * <h2>Why one budget</h2>
 * <p>Before 2026-11-15 every emitter chose its own count. The server scaled some by {@code Config.perfProfile}, a
 * COMMON value that on a dedicated server is the <em>server's</em> file, so a player on a weak PC could not turn
 * spell effects down; the client scaled nothing, ignored distance, and a group fight spent the same per effect at
 * forty blocks as at four. Now the server says what happened and how big (a burst of n, a ring of n points) and the
 * client spends from here.
 *
 * <h2>The three factors</h2>
 * <ul>
 *   <li><b>Quality</b> ({@code Config.vfxQuality}, overridable by {@code /wandb debug beam preset} and the Visuals
 *       debug tab): LOW 0.5, MEDIUM 1.0, HIGH 1.4. MEDIUM is the designed look and changes nothing.</li>
 *   <li><b>Distance</b> to the camera: full up to 16 blocks, falling to a quarter at 32. Beyond that vanilla culls a
 *       non-forced particle anyway; {@link #count} returns the minimum there so a shape still has its outline.</li>
 *   <li><b>Per-tick ceiling</b>: once a tick's spending passes the quality's cap, further requests get their minimum.
 *       Ten wizards trading curses draw a thinner fight, not a stutter.</li>
 * </ul>
 * The vanilla Particles option is not applied here: {@code ClientLevel.addParticle} already thins every
 * non-forced particle by it, and applying it twice would empty the screen on DECREASED.
 *
 * <p>Render thread only (particles are added there); no locking.
 */
@NullMarked
public final class VfxBudget {

    /** Full detail up to here. */
    static final double NEAR = 16.0;
    /** A quarter of the detail from here on. */
    static final double FAR = 32.0;
    static final float FAR_SCALE = 0.25f;

    private static Config.@Nullable VfxQuality override;
    private static long spentTick = Long.MIN_VALUE;
    private static int spent;

    private VfxBudget() {}

    /** The quality in force: a debug override if one was set this session, else the config value. */
    public static Config.VfxQuality quality() {
        Config.VfxQuality o = override;
        return o != null ? o : Config.vfxQuality;
    }

    /** Session override from the debug command or panel; {@code null} goes back to the config value. */
    public static void override(Config.@Nullable VfxQuality next) {
        override = next;
    }

    public static float qualityScale() {
        return qualityScale(quality());
    }

    static float qualityScale(Config.VfxQuality q) {
        return switch (q) {
            case LOW -> 0.5f;
            case MEDIUM -> 1.0f;
            case HIGH -> 1.4f;
        };
    }

    /** Particles one client tick may spend on spell VFX before every further request gets its minimum. */
    static int tickCap(Config.VfxQuality q) {
        return switch (q) {
            case LOW -> 240;
            case MEDIUM -> 600;
            case HIGH -> 1200;
        };
    }

    /** 1 near, falling linearly to {@link #FAR_SCALE} at {@link #FAR} and beyond. */
    static float distanceScale(double distance) {
        if (distance <= NEAR) {
            return 1.0f;
        }
        if (distance >= FAR) {
            return FAR_SCALE;
        }
        float t = (float) ((distance - NEAR) / (FAR - NEAR));
        return Mth.lerp(t, 1.0f, FAR_SCALE);
    }

    /**
     * Pure form of {@link #count}: what {@code requested} becomes under these factors, never below {@code min} (and
     * never above {@code requested} when that is smaller than {@code min}).
     */
    static int scaled(int requested, int min, float quality, float distance, boolean overCap) {
        if (requested <= 0) {
            return 0;
        }
        int floor = Math.min(requested, Math.max(0, min));
        if (overCap) {
            return floor;
        }
        return Math.max(floor, Math.round(requested * quality * distance));
    }

    /**
     * How many of {@code requested} particles an effect at {@code pos} may spawn now, and spends them.
     *
     * @param min the fewest that still read as the shape (a ring's outline, a burst's flash); 0 allows nothing
     */
    public static int count(int requested, int min, Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0;
        }
        long now = mc.level.getGameTime();
        if (now != spentTick) {
            spentTick = now;
            spent = 0;
        }
        Config.VfxQuality q = quality();
        Vec3 eye = mc.gameRenderer.getMainCamera().position();
        int n = scaled(requested, min, qualityScale(q), distanceScale(eye.distanceTo(pos)), spent >= tickCap(q));
        spent += n;
        return n;
    }

    /** {@link #count} with a minimum of one, for effects whose single particle carries the message. */
    public static int count(int requested, Vec3 pos) {
        return count(requested, 1, pos);
    }
}
