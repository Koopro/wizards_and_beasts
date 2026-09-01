package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.client.map.style.MapBiomeStyle;
import at.koopro.wizardsandbeasts.client.map.style.MapStyles;
import at.koopro.wizardsandbeasts.map.MapGeometry;
import at.koopro.wizardsandbeasts.map.MapRelief;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Draws the charted world onto the parchment.
 *
 * <h2>Why this is not a minimap</h2>
 * Nothing here reads a block. The renderer only ever sees a biome id and a relief band per chunk,
 * and turns that pair into one hand-drawn sprite — trees for a wood, wave lines for water, hachures
 * for a ridge. It is an <em>interpretation</em>, which is the point: a rendered top-down screenshot
 * of block colours is what every other map mod already is, and it is also the thing that would make
 * this a cheat tool.
 *
 * <h2>Cost</h2>
 * One blit per drawn cell, from a single sheet, so the whole terrain pass is one texture bind. The
 * cell count is held roughly constant across the zoom range by {@link MapView#lodStep()}: zooming
 * out collapses tiles into blocks rather than drawing more of them, so the far view costs about
 * what the near view costs instead of sixteen times more. There is no per-frame allocation in the
 * loop and no dependence on how much of the world has been charted — only on how much of it is on
 * screen.
 */
public final class MapTerrainRenderer {

    private MapTerrainRenderer() {
    }

    /**
     * Draws every charted cell inside the view's viewport.
     *
     * <p>The caller is expected to have scissored to the viewport already: cells are drawn on a
     * whole-tile grid and the ones straddling the edge must be cut, not skipped, or the parchment
     * gains a ragged margin that moves as you pan.
     */
    public static void render(GuiGraphics graphics, MapView view) {
        int step = view.lodStep();
        double cellPx = view.tilePixels() * step;

        // Snapped outward to a multiple of the step so the sampling grid is anchored to the world
        // rather than to the camera. Without this the blocks a cell samples change as you pan, and
        // a coastline visibly crawls between two different renderings of itself.
        int firstX = Math.floorDiv(view.firstVisibleTileX(), step) * step;
        int firstZ = Math.floorDiv(view.firstVisibleTileZ(), step) * step;
        int lastX = view.lastVisibleTileX();
        int lastZ = view.lastVisibleTileZ();

        // +1 so a cell whose origin is just off the left/top edge is still drawn; it covers the
        // first visible pixels.
        int drawSize = (int) Math.ceil(cellPx) + 1;

        for (int tileZ = firstZ; tileZ <= lastZ; tileZ += step) {
            double screenY = view.screenY(MapGeometry.tileToBlock(tileZ));
            for (int tileX = firstX; tileX <= lastX; tileX += step) {
                drawCell(graphics, view, tileX, tileZ, step,
                        (int) Math.floor(view.screenX(MapGeometry.tileToBlock(tileX))),
                        (int) Math.floor(screenY), drawSize);
            }
        }
    }

    private static void drawCell(GuiGraphics graphics, MapView view, int tileX, int tileZ, int step,
                                 int screenX, int screenY, int drawSize) {
        Sample sample = step == 1 ? sampleTile(tileX, tileZ) : sampleBlock(tileX, tileZ, step);
        if (sample == null) {
            return; // uncharted: the parchment shows through, which is the whole discovery system
        }

        MapBiomeStyle style = MapStyles.biome(sample.biome());
        int type = MapStyles.tileFor(style, sample.relief());
        int tint = MapStyles.tintFor(style, sample.relief());
        int variant = MaraudersMapTextures.variantFor(tileX, tileZ);

        graphics.blit(RenderPipelines.GUI_TEXTURED, MaraudersMapTextures.TILES,
                screenX, screenY,
                MaraudersMapTextures.tileU(variant), MaraudersMapTextures.tileV(type),
                drawSize, drawSize,
                MaraudersMapTextures.TILE_CELL, MaraudersMapTextures.TILE_CELL,
                MaraudersMapTextures.TILE_SHEET_W, MaraudersMapTextures.TILE_SHEET_H,
                tint);
    }

    private record Sample(Identifier biome, MapRelief relief) {
    }

    private static @Nullable Sample sampleTile(int tileX, int tileZ) {
        Identifier biome = ClientMapAtlas.biomeAt(tileX, tileZ);
        if (biome == null) {
            return null;
        }
        return new Sample(biome, ClientMapAtlas.reliefAt(tileX, tileZ));
    }

    /**
     * Collapses a {@code step x step} block of tiles into one.
     *
     * <p>Not a majority vote. Water and peaks win outright, because those are the features that
     * carry a map's readable shape: a river is one tile wide and a majority vote erases every river
     * in the world the moment you zoom out, which is exactly when you most want to see where the
     * rivers go. Everything else falls back to the first charted tile in the block, which is stable
     * because the block's origin is world-anchored.
     */
    private static @Nullable Sample sampleBlock(int originX, int originZ, int step) {
        Sample fallback = null;
        Sample water = null;
        Sample peak = null;

        for (int dz = 0; dz < step; dz++) {
            for (int dx = 0; dx < step; dx++) {
                int tileX = originX + dx;
                int tileZ = originZ + dz;
                Identifier biome = ClientMapAtlas.biomeAt(tileX, tileZ);
                if (biome == null) {
                    continue;
                }
                MapRelief relief = ClientMapAtlas.reliefAt(tileX, tileZ);
                Sample sample = new Sample(biome, relief);
                if (fallback == null) {
                    fallback = sample;
                }
                if (relief == null) {
                    continue;
                }
                if (water == null && relief.isWater()) {
                    water = sample;
                } else if (peak == null && (relief == MapRelief.PEAK || relief == MapRelief.MOUNTAIN)) {
                    peak = sample;
                }
            }
        }
        if (peak != null) {
            return peak;
        }
        return water != null ? water : fallback;
    }
}
