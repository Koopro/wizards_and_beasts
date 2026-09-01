package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.client.map.style.MapMarkerStyle;
import at.koopro.wizardsandbeasts.client.map.style.MapStyles;
import at.koopro.wizardsandbeasts.map.MapMarker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Draws the places the map knows the names of.
 *
 * <p>Two rules do most of the work of keeping the page readable, and both come from real
 * cartography rather than from UI convention:
 *
 * <ul>
 *   <li><b>Symbols scale with importance, not with zoom.</b> A castle is drawn larger than a pin at
 *       every zoom level, because size is how a map says "this one matters" before you have read
 *       anything. Markers do not grow as you zoom in — they are annotations on the page, not
 *       objects in the world.</li>
 *   <li><b>Almost nothing is labelled on the page.</b> Lettering is reserved for the handful of
 *       styles that ask for it; everything else names itself on hover. A map that writes every name
 *       it knows is a map you cannot see the terrain through.</li>
 * </ul>
 */
public final class MapMarkerRenderer {

    /** How long a newly discovered place shimmers, in milliseconds of wall clock. */
    private static final long REVEAL_MILLIS = 2400L;

    /** Extra pixels around a symbol that still count as hovering it. */
    private static final int HOVER_PAD = 2;

    private MapMarkerRenderer() {
    }

    /**
     * Draws every visible marker and returns the one under the cursor.
     *
     * <p>Hit-testing during the draw rather than in a second pass: the two would otherwise have to
     * agree about position and size independently, and the day they stop agreeing is the day
     * tooltips appear next to the wrong symbol.
     */
    public static @Nullable MapMarker render(GuiGraphics graphics, Font font, MapView view,
                                             List<MapMarker> markers, double mouseX, double mouseY,
                                             long gameTime, boolean showHidden) {
        MapMarker hovered = null;
        long now = System.currentTimeMillis();

        for (MapMarker marker : markers) {
            if (marker.hidden() && !showHidden) {
                continue;
            }
            MapMarkerStyle style = MapStyles.marker(marker.type());
            if (style.minZoom().isPresent() && view.zoom() < style.minZoom().get()) {
                continue;
            }

            double screenX = view.screenX(marker.x());
            double screenY = view.screenY(marker.z());
            int size = view.scaled(style.size());
            int half = size / 2;

            // Culled against the viewport with the symbol's own half-size, so a castle whose centre
            // is just off screen still draws the half of it that is on.
            if (screenX + half < view.viewX() || screenX - half > view.viewX() + view.viewW()
                    || screenY + half < view.viewY() || screenY - half > view.viewY() + view.viewH()) {
                continue;
            }

            int tint = marker.hidden() ? fade(style.tint(), 0.35F) : style.tint();
            tint = applyReveal(tint, marker, now, gameTime);

            graphics.blit(RenderPipelines.GUI_TEXTURED, MaraudersMapTextures.MARKERS,
                    (int) Math.round(screenX - half), (int) Math.round(screenY - half),
                    MaraudersMapTextures.markerU(style.icon()),
                    MaraudersMapTextures.markerV(style.icon()),
                    size, size,
                    MaraudersMapTextures.MARKER_CELL, MaraudersMapTextures.MARKER_CELL,
                    MaraudersMapTextures.MARKER_SHEET_W, MaraudersMapTextures.MARKER_SHEET_H,
                    tint);

            if (style.labelled()) {
                Component label = label(marker, style);
                int textX = (int) Math.round(screenX) - font.width(label) / 2;
                int textY = (int) Math.round(screenY) + half + 1;
                // Drawn without a drop shadow: a shadow is a screen convention and reads as a
                // sticker floating over the page rather than as something written on it.
                graphics.drawString(font, label, textX, textY, style.tint(), false);
            }

            if (mouseX >= screenX - half - HOVER_PAD && mouseX <= screenX + half + HOVER_PAD
                    && mouseY >= screenY - half - HOVER_PAD && mouseY <= screenY + half + HOVER_PAD) {
                hovered = marker;
            }
        }
        return hovered;
    }

    /** The name to show: what a player typed, or the marker's translated style name. */
    public static Component label(MapMarker marker, MapMarkerStyle style) {
        if (marker.label().isBlank()) {
            return Component.translatable(style.name());
        }
        return marker.translatable()
                ? Component.translatable(marker.label())
                : Component.literal(marker.label());
    }

    /**
     * Brightens a marker for a moment after it is first found.
     *
     * <p>Keyed on game time rather than wall clock because {@code discoveredAt} is a game time, and
     * the two only line up if you compare like with like. The shimmer is the map's one piece of
     * unprompted magic and it is deliberately brief: a permanent animation on a discovered place
     * would make the page never settle.
     */
    private static int applyReveal(int tint, MapMarker marker, long now, long gameTime) {
        long ageTicks = gameTime - marker.discoveredAt();
        if (ageTicks < 0 || ageTicks > REVEAL_MILLIS / 50) {
            return tint;
        }
        float pulse = 0.5F + 0.5F * (float) Math.sin(now / 120.0);
        return lighten(tint, 0.35F * pulse);
    }

    private static int fade(int argb, float factor) {
        int a = (int) ((argb >>> 24) * factor);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int lighten(int argb, float amount) {
        int a = argb >>> 24;
        int r = channel((argb >> 16) & 0xFF, amount);
        int g = channel((argb >> 8) & 0xFF, amount);
        int b = channel(argb & 0xFF, amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int value, float amount) {
        return Math.clamp((int) (value + (255 - value) * amount), 0, 255);
    }
}
