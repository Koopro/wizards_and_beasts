package at.koopro.wizardsandbeasts.spell.darkmark;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dark Mark in the sky: "an ominous skull with a snake's tongue" (wizardingworld.com), "a large green skull with a
 * snake protruding from its mouth" (Goblet of Fire ch. 9), glowing green at the World Cup. Drawn as particles on a
 * vertical plane, one particle cluster per pixel of {@link #SHAPE}; call {@link #draw} every few ticks to hold it.
 */
@NullMarked
public final class DarkMark {

    /** The mark, top row first: # skull, s serpent. Thirty-one wide, the serpent coiling out of the jaw and down. */
    static final String[] SHAPE = {
            "          #########          ",
            "       ###############       ",
            "     ###################     ",
            "    #####################    ",
            "   #######################   ",
            "   #######################   ",
            "  #####   #########   #####  ",
            "  ####     #######     ####  ",
            "  ####     #######     ####  ",
            "  #####   #########   #####  ",
            "   #########     #########   ",
            "    ########  #  ########    ",
            "     ###################     ",
            "      ##  ## ss ##  ##       ",
            "       #  #  ss  #  #        ",
            "             ss              ",
            "            ss               ",
            "          sss                ",
            "        sss                  ",
            "       ss                    ",
            "       ss                    ",
            "        sss                  ",
            "          ssss               ",
            "             sss             ",
            "               sss           ",
            "                 ss          ",
            "                 ss          ",
            "               sss           ",
            "            ssss    s s      ",
            "          sss        s       ",
    };

    public static final int CORE = 0x3DFF6A;
    public static final int GLOW = 0x1FAF4A;
    public static final int SMOKE = 0x0E3B1E;

    private DarkMark() {}

    /** Pixel positions of the mark, centred, in units of pixels: (right, up). */
    public static List<double[]> pixels() {
        List<double[]> out = new ArrayList<>();
        int h = SHAPE.length;
        for (int row = 0; row < h; row++) {
            String line = SHAPE[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != ' ') {
                    out.add(new double[] {col - line.length() / 2.0, (h / 2.0) - row});
                }
            }
        }
        return out;
    }

    /**
     * Draws the mark once, centred on {@code centre}, {@code pixel} blocks to a pixel, on the vertical plane facing
     * {@code facingYaw} (Minecraft yaw: the mark faces that way, so a viewer looking the opposite way sees it front on).
     */
    public static void draw(ServerLevel level, Vec3 centre, double pixel, float facingYaw) {
        double rad = Math.toRadians(facingYaw);
        double rightX = Math.cos(rad);
        double rightZ = Math.sin(rad);
        // Self-lit, so it glows against the night as the World Cup's did; a faint dust haze gives it its smoke.
        var core = new at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions(
                at.koopro.wizardsandbeasts.registry.ModParticles.VFX_MOTE.get(), 0xFF000000 | CORE);
        var glow = new at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions(
                at.koopro.wizardsandbeasts.registry.ModParticles.VFX_MOTE.get(), 0xFF000000 | GLOW);
        DustParticleOptions smoke = new DustParticleOptions(SMOKE, 3.5f);
        for (double[] p : pixels()) {
            double x = centre.x + rightX * p[0] * pixel;
            double y = centre.y + p[1] * pixel;
            double z = centre.z + rightZ * p[0] * pixel;
            level.sendParticles(core, true, true, x, y, z, 3, pixel * 0.25, pixel * 0.25, pixel * 0.25, 0.0);
            level.sendParticles(glow, true, true, x, y, z, 1, pixel * 0.6, pixel * 0.6, pixel * 0.6, 0.0);
            level.sendParticles(smoke, true, true, x, y, z, 1, pixel * 0.9, pixel * 0.9, pixel * 0.9, 0.0);
        }
    }
}
