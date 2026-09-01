package at.koopro.wizardsandbeasts.client.gui.character;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/**
 * The Character Sheet's stat glyphs: one 112×16 strip of seven 16×16 cells.
 *
 * <p>Five cells are the {@link PlayerStat} constants <em>in declaration order</em>, which is what
 * makes {@link #uFor} an ordinal lookup rather than a switch the next stat would have to be added
 * to. The last two are the marks a row can wear: a padlock for a Power that heritage will not let
 * grow, and the prodigy starburst.
 *
 * <p>One strip rather than seven files, for the same reason the tab panels are one nine-slice:
 * seven texture binds a frame to draw seven 16px squares is seven chances for a pack to ship six.
 *
 * <p>Missing art draws nothing at all, not the magenta checkerboard — the row's text label already
 * names the stat, so an absent glyph costs the sheet a decoration rather than its meaning. Presence
 * is probed once per screen open by {@link CharacterSheetTextures#refresh()}, the same cadence and
 * for the same reason as the tab panels beside it.
 */
@NullMarked
public final class StatIcons {

    public static final Identifier STRIP = Identifier.fromNamespaceAndPath(
            WizardsAndBeastsMod.MODID, "textures/gui/character_sheet/stat_icons.png");

    /** Native cell size. Drawn 1:1 — these are pixel art and a nearest-neighbour halving mangles them. */
    public static final int SIZE = 16;
    private static final int CELLS = 7;
    private static final int STRIP_W = SIZE * CELLS;

    /** Index of the padlock cell. */
    private static final int CELL_CAPPED = 5;
    /** Index of the prodigy starburst cell. */
    private static final int CELL_PRODIGY = 6;

    private static boolean present = true;

    private StatIcons() {}

    /** Re-probes the strip. Called from {@link CharacterSheetTextures#refresh()}. */
    static void refresh() {
        present = Minecraft.getInstance().getResourceManager().getResource(STRIP).isPresent();
    }

    public static boolean isPresent() {
        return present;
    }

    public static void drawStat(GuiGraphics g, PlayerStat stat, int x, int y) {
        draw(g, uFor(stat), x, y);
    }

    public static void drawCapped(GuiGraphics g, int x, int y) {
        draw(g, CELL_CAPPED * SIZE, x, y);
    }

    public static void drawProdigy(GuiGraphics g, int x, int y) {
        draw(g, CELL_PRODIGY * SIZE, x, y);
    }

    /**
     * A stat's cell offset. The strip is authored in {@link PlayerStat} declaration order, so a
     * sixth constant needs a sixth cell drawn and nothing here changes; until it has one, the
     * bounds check below silently draws nothing rather than sampling the padlock.
     */
    private static int uFor(PlayerStat stat) {
        return stat.ordinal() * SIZE;
    }

    private static void draw(GuiGraphics g, int u, int x, int y) {
        if (!present || u < 0 || u + SIZE > STRIP_W) return;
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        g.blit(pipeline, STRIP, x, y, (float) u, 0.0F, SIZE, SIZE, SIZE, SIZE, STRIP_W, SIZE);
    }
}
