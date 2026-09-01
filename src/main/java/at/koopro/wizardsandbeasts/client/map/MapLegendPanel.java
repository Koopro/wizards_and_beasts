package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.map.style.MapMarkerStyle;
import at.koopro.wizardsandbeasts.client.map.style.MapStyles;
import at.koopro.wizardsandbeasts.map.MapMarkerTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The key to the symbols.
 *
 * <p>Grouped rather than exhaustive. Listing all twenty marker types produces a legend longer than
 * the map is tall, which is a legend nobody reads; markers declare a {@code legend} group in their
 * style and the panel shows one row per group, drawn with the first symbol in it. A player who
 * needs the exact name of a specific symbol hovers it on the page.
 *
 * <p>Drawn from the loaded styles rather than from a hardcoded list, so a resource pack that adds a
 * marker type gets a legend row for free and one that removes a type does not leave a row
 * explaining a symbol that no longer exists.
 */
public final class MapLegendPanel {

    /**
     * The order groups appear in, most important first.
     *
     * <p>Fixed rather than alphabetical: a legend is read top-down and the first thing a player
     * looks for is themself, then the places that matter, then their own marks.
     */
    private static final List<String> GROUP_ORDER =
            List.of("self", "wizarding", "structure", "magical", "waypoint", "danger");

    private MapLegendPanel() {
    }

    public static void render(GuiGraphics gfx, Font font, GuiScaleHelper.Layout layout,
                              int x, int y, int w, int h) {
        McStylePanel.drawSkinPanel(gfx, MaraudersMapTextures.SKIN, x, y, w, h);
        gfx.drawString(font, Component.translatable("screen.wizards_and_beasts.marauders_map.legend.title"),
                x + layout.s(5), y + layout.s(5), 0xFF4A2B18, false);

        int rowH = layout.s(12);
        int rowY = y + layout.s(17);
        // Exactly half the 16px source: a clean nearest-neighbour halving rather than the
        // non-integer resample that would soften every symbol in the key.
        int iconSize = layout.s(8);

        // The holder's own mark is not a marker type, so it cannot come from the style table -- but
        // it is the first thing anyone looks for, so it is drawn by hand at the top rather than
        // left out of the key entirely.
        gfx.blit(RenderPipelines.GUI_TEXTURED, MaraudersMapTextures.PLAYER_MARK,
                x + layout.s(5), rowY, 0.0F, 0.0F, iconSize, iconSize,
                MaraudersMapTextures.PLAYER_MARK_SIZE, MaraudersMapTextures.PLAYER_MARK_SIZE,
                MaraudersMapTextures.PLAYER_MARK_SIZE, MaraudersMapTextures.PLAYER_MARK_SIZE,
                MapTrackedRenderer.INK_SELF);
        gfx.drawString(font, Component.translatable("screen.wizards_and_beasts.marauders_map.legend.self"),
                x + layout.s(17), rowY, 0xFF3A2E24, false);
        rowY += rowH;

        for (Map.Entry<String, Identifier> group : groups().entrySet()) {
            if (rowY + rowH > y + h - layout.s(4)) {
                break; // ran out of page; the remaining symbols still name themselves on hover
            }
            MapMarkerStyle style = MapStyles.marker(group.getValue());
            gfx.blit(RenderPipelines.GUI_TEXTURED, MaraudersMapTextures.MARKERS,
                    x + layout.s(5), rowY,
                    MaraudersMapTextures.markerU(style.icon()),
                    MaraudersMapTextures.markerV(style.icon()),
                    iconSize, iconSize,
                    MaraudersMapTextures.MARKER_CELL, MaraudersMapTextures.MARKER_CELL,
                    MaraudersMapTextures.MARKER_SHEET_W, MaraudersMapTextures.MARKER_SHEET_H,
                    style.tint());
            gfx.drawString(font,
                    Component.translatable("screen.wizards_and_beasts.marauders_map.legend."
                            + group.getKey()),
                    x + layout.s(17), rowY, 0xFF3A2E24, false);
            rowY += rowH;
        }
    }

    /**
     * One representative marker type per legend group, in {@link #GROUP_ORDER}.
     *
     * <p>The representatives are the mod's own types rather than whichever type happened to load
     * first, so the wizarding row is always the castle and never, say, Gringotts — the symbol in
     * the key should be the one a player has most likely already seen on the page.
     */
    private static Map<String, Identifier> groups() {
        Map<String, Identifier> byGroup = new LinkedHashMap<>();
        byGroup.put("wizarding", MapMarkerTypes.HOGWARTS);
        byGroup.put("structure", MapMarkerTypes.STRUCTURE);
        byGroup.put("magical", MapMarkerTypes.MAGICAL_SITE);
        byGroup.put("waypoint", MapMarkerTypes.WAYPOINT);
        byGroup.put("danger", MapMarkerTypes.DEATH);

        Map<String, Identifier> ordered = new LinkedHashMap<>();
        for (String group : GROUP_ORDER) {
            Identifier representative = byGroup.get(group);
            if (representative != null) {
                ordered.put(group, representative);
            }
        }
        return ordered;
    }
}
