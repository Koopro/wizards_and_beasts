package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel.Sprite;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.map.style.MapMarkerStyle;
import at.koopro.wizardsandbeasts.client.map.style.MapStyles;
import at.koopro.wizardsandbeasts.map.MapMarker;
import at.koopro.wizardsandbeasts.map.MapMarkerSource;
import at.koopro.wizardsandbeasts.map.MapMarkerTypes;
import at.koopro.wizardsandbeasts.map.TrackedEntityEntry;
import at.koopro.wizardsandbeasts.network.map.MapCloseC2SPayload;
import at.koopro.wizardsandbeasts.network.map.MapWaypointC2SPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Marauder's Map.
 *
 * <p>Structurally an atlas — pan, zoom, tiles, markers, waypoints — with the one thing that makes
 * it this artefact rather than a minimap drawn on top: named, moving ink. The layering is
 * deliberate and reads bottom to top as paper, country, annotation, life:
 *
 * <ol>
 *   <li>parchment grain, tiled</li>
 *   <li>charted terrain, {@link MapTerrainRenderer}</li>
 *   <li>fold creases and edge burn, over the terrain so the page is one object</li>
 *   <li>markers, {@link MapMarkerRenderer}</li>
 *   <li>footprints and dots, {@link MapTrackedRenderer}</li>
 * </ol>
 *
 * <p>The screen owns no map data. Everything it draws comes from {@link ClientMapAtlas}, which
 * survives the screen being reconstructed — which it is, on every dimension change and on every
 * window resize.
 */
public class MaraudersMapScreen extends Screen {

    // -- Layout, in design pixels ------------------------------------------

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 284;
    private static final int HEADER_H = 30;
    private static final int FOOTER_H = 26;
    private static final int VIEW_INSET = 8;
    private static final int SIDE_PANEL_W = 124;

    /**
     * 18, not 16, and this is load-bearing.
     *
     * <p>{@code McStylePanel.drawNineSlice} cuts an 8px border out of the 32px skin sprite, so a
     * widget under 2*8+1 has no interior left: the centre and edge blits come out zero-sized and
     * the button renders as four disconnected corners around a hole. Everything skinned on this
     * screen has to clear that floor, which is why the filter buttons are this tall too rather
     * than the 14 a text button would otherwise want.
     */
    private static final int CONTROL_SIZE = 18;
    private static final int CONTROL_GAP = 2;
    private static final int CONTROL_MARGIN = 4;
    /** The nine-slice floor: below this a skinned widget has no face. */
    private static final int MIN_SKINNED = 2 * WizardsMetrics.PANEL_SPRITE_BORDER + 2;

    // -- Ink ---------------------------------------------------------------

    /** The page. Warmer and dirtier than the Ministry's memo stock; this thing is old. */
    private static final int PARCHMENT_INK = MaraudersMapTextures.SKIN.ink();
    private static final int PARCHMENT_INK_DIM = MaraudersMapTextures.SKIN.muted();
    private static final int TITLE_INK = 0xFF4A2B18;
    private static final int BRASS = 0xFFB08A4A;
    /** Wash over uncharted parchment, so "not yet been there" reads as unfinished, not as empty. */
    private static final int UNCHARTED_WASH = 0x22201408;

    private final UUID selfUuid;

    private final MapView view = new MapView();
    private GuiScaleHelper.Layout layout;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private boolean dragging;

    private boolean showPassive = true;
    private boolean showHostile = true;
    private boolean showHiddenMarkers;

    /** Which side panel is open, if any. Only one at a time — the page is not that wide. */
    private enum SidePanel {NONE, LEGEND, WAYPOINTS}

    private SidePanel sidePanel = SidePanel.NONE;

    private @Nullable MapMarker hoveredMarker;
    private @Nullable TrackedEntityEntry hoveredEntity;
    private @Nullable UUID selectedMarker;

    /** Where a pending waypoint will be planted, in world blocks. */
    private int pendingX;
    private int pendingZ;
    private boolean placing;
    private int pendingIconIndex;

    private @Nullable EditBox nameBox;
    private final List<MapMarker> waypointRows = new ArrayList<>();
    private int waypointScroll;

    public MaraudersMapScreen() {
        super(Component.translatable("screen.wizards_and_beasts.marauders_map.title"));
        Minecraft mc = Minecraft.getInstance();
        this.selfUuid = mc.player != null ? mc.player.getUUID() : new UUID(0, 0);
    }

    // -- Layout ------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.panel(width, height, PANEL_W, PANEL_H);
        panelW = layout.panelW();
        panelH = layout.panelH();
        panelX = layout.panelX();
        panelY = layout.panelY();

        int frame = WizardsMetrics.PANEL_SPRITE_BORDER;
        int viewX = panelX + layout.s(VIEW_INSET) + frame;
        int viewY = panelY + layout.s(HEADER_H) + frame;
        int viewW = panelW - 2 * (layout.s(VIEW_INSET) + frame);
        int viewH = panelH - layout.s(HEADER_H + FOOTER_H) - 2 * frame;

        boolean firstOpen = view.viewW() <= 1;
        view.setViewport(viewX, viewY, viewW, viewH);
        view.setUiScale(layout.scale());
        if (firstOpen) {
            centerOnPlayer();
        }

        addControls();
        rebuildSidePanel();
    }

    /**
     * The map opens looking at its holder, every time.
     *
     * <p>The old implementation opened on wherever the map had first been unfolded, forever. This
     * is the same function the "centre on me" control calls, so the two can never drift apart.
     */
    /**
     * A skinned widget's size, held at or above the nine-slice floor.
     *
     * <p>{@code layout.s()} shrinks below 1.0 on a small viewport -- a 1920x1080 window at GUI
     * scale 4 gives the panel 0.89 -- so a design size of 18 arrives as 16 and the button loses
     * its face on exactly the setups least able to spare the pixels. The floor is absolute
     * because the border it protects is absolute: the sprite's 8px frame does not scale.
     */
    private int skinned(int designSize) {
        return Math.max(MIN_SKINNED, layout.s(designSize));
    }

    private void centerOnPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            view.centerOn(mc.player.getX(), mc.player.getZ());
        }
    }

    private void addControls() {
        int size = skinned(CONTROL_SIZE);
        int gap = layout.s(CONTROL_GAP);
        int margin = layout.s(CONTROL_MARGIN);
        int y = view.viewY() + view.viewH() - margin - size;
        int x = view.viewX() + view.viewW() - margin - size;

        x = addIcon(x, y, size, MaraudersMapTextures.ICON_LEGEND, "legend",
                () -> toggleSidePanel(SidePanel.LEGEND)) - gap;
        x = addIcon(x, y, size, MaraudersMapTextures.ICON_WAYPOINT, "waypoints",
                () -> toggleSidePanel(SidePanel.WAYPOINTS)) - gap;
        x = addIcon(x, y, size, MaraudersMapTextures.ICON_CENTER, "center",
                this::centerOnPlayer) - gap;
        x = addIcon(x, y, size, MaraudersMapTextures.ICON_ZOOM_IN, "zoom_in",
                () -> view.zoomAboutCenter(MapView.ZOOM_STEP * MapView.ZOOM_STEP)) - gap;
        addIcon(x, y, size, MaraudersMapTextures.ICON_ZOOM_OUT, "zoom_out",
                () -> view.zoomAboutCenter(1.0 / (MapView.ZOOM_STEP * MapView.ZOOM_STEP)));

        int filterW = layout.s(58);
        int filterH = skinned(MIN_SKINNED);
        int filterY = panelY + panelH - layout.s(FOOTER_H) + layout.s(4);
        addRenderableWidget(mapButton(panelX + panelW - layout.s(VIEW_INSET) - filterW * 2 - gap,
                filterY, filterW, filterH, filterLabel("hostile", showHostile), null, () -> {
                    showHostile = !showHostile;
                    rebuild();
                }));
        addRenderableWidget(mapButton(panelX + panelW - layout.s(VIEW_INSET) - filterW,
                filterY, filterW, filterH, filterLabel("passive", showPassive), null, () -> {
                    showPassive = !showPassive;
                    rebuild();
                }));
    }

    /** @return the control's left edge, so the caller can walk leftward along the row */
    private int addIcon(int x, int y, int size, int icon, String key, Runnable action) {
        Component name = Component.translatable("screen.wizards_and_beasts.marauders_map.control." + key);
        ThemedButton button = mapButton(x, y, size, size, Component.empty(), iconSprite(icon), action);
        button.setTooltip(Tooltip.create(name));
        addRenderableWidget(button);
        return x - size;
    }

    private static Sprite iconSprite(int icon) {
        return new Sprite(MaraudersMapTextures.CONTROLS,
                MaraudersMapTextures.controlU(icon), 0,
                MaraudersMapTextures.CONTROL_CELL, MaraudersMapTextures.CONTROL_CELL,
                MaraudersMapTextures.CONTROL_SHEET_W, MaraudersMapTextures.CONTROL_SHEET_H);
    }

    private ThemedButton mapButton(int x, int y, int w, int h, Component label,
                                   @Nullable Sprite icon, Runnable action) {
        return new ThemedButton(x, y, w, h, label, action, icon,
                MaraudersMapTextures.SKIN);
    }

    private Component filterLabel(String key, boolean on) {
        return Component.translatable("screen.wizards_and_beasts.marauders_map.filter." + key,
                Component.translatable(on
                        ? "screen.wizards_and_beasts.marauders_map.filter.on"
                        : "screen.wizards_and_beasts.marauders_map.filter.off"));
    }

    /** Rebuilds widgets in place, keeping pan and zoom — {@code init} is not a reset. */
    private void rebuild() {
        clearWidgets();
        addControls();
        rebuildSidePanel();
    }

    private void toggleSidePanel(SidePanel panel) {
        sidePanel = sidePanel == panel ? SidePanel.NONE : panel;
        selectedMarker = null;
        placing = false;
        rebuild();
    }

    // -- Render ------------------------------------------------------------

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(gfx);
        McStylePanel.drawSkinPanel(gfx, MaraudersMapTextures.SKIN, panelX, panelY, panelW, panelH);
        renderHeader(gfx);

        McStylePanel.drawSkinInset(gfx, MaraudersMapTextures.SKIN,
                view.viewX() - WizardsMetrics.PANEL_SPRITE_BORDER,
                view.viewY() - WizardsMetrics.PANEL_SPRITE_BORDER,
                view.viewW() + 2 * WizardsMetrics.PANEL_SPRITE_BORDER,
                view.viewH() + 2 * WizardsMetrics.PANEL_SPRITE_BORDER);

        gfx.enableScissor(view.viewX(), view.viewY(),
                view.viewX() + view.viewW(), view.viewY() + view.viewH());
        renderPage(gfx, mouseX, mouseY);
        gfx.disableScissor();

        renderFooter(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);

        switch (sidePanel) {
            case LEGEND -> MapLegendPanel.render(gfx, font, layout, sidePanelX(), sidePanelY(),
                    layout.s(SIDE_PANEL_W), sidePanelH());
            case WAYPOINTS -> renderWaypointPanel(gfx, mouseX, mouseY);
            case NONE -> {
            }
        }

        renderTooltips(gfx, mouseX, mouseY);
    }

    private void renderPage(GuiGraphics gfx, int mouseX, int mouseY) {
        // The grain is tiled in screen space rather than panned with the world: it is the paper,
        // and paper does not move when you slide your finger across the drawing on it.
        McStylePanel.drawTiled(gfx, MaraudersMapTextures.PARCHMENT,
                view.viewX(), view.viewY(), view.viewW(), view.viewH(),
                MaraudersMapTextures.PARCHMENT_TILE);
        gfx.fill(view.viewX(), view.viewY(),
                view.viewX() + view.viewW(), view.viewY() + view.viewH(), UNCHARTED_WASH);

        MapTerrainRenderer.render(gfx, view);

        McStylePanel.drawTexture(gfx, MaraudersMapTextures.CREASES,
                view.viewX(), view.viewY(), view.viewW(), view.viewH(),
                MaraudersMapTextures.CREASES_SIZE, MaraudersMapTextures.CREASES_SIZE);

        long gameTime = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime() : 0L;
        hoveredMarker = MapMarkerRenderer.render(gfx, font, view, ClientMapAtlas.markers(),
                mouseX, mouseY, gameTime, showHiddenMarkers);

        double viewerY = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getY() : 64.0;
        hoveredEntity = MapTrackedRenderer.render(gfx, font, view, ClientMapAtlas.entities(),
                selfUuid, viewerY, mouseX, mouseY, showPassive, showHostile);

        if (placing) {
            renderPendingPin(gfx);
        }
        renderCompass(gfx);
    }

    /** Ghost of the pin about to be planted, so "where will this go" is answered before it goes. */
    private void renderPendingPin(GuiGraphics gfx) {
        Identifier type = MapMarkerTypes.WAYPOINT_ICONS.get(pendingIconIndex);
        MapMarkerStyle style = MapStyles.marker(type);
        int size = view.scaled(style.size());
        gfx.blit(RenderPipelines.GUI_TEXTURED, MaraudersMapTextures.MARKERS,
                (int) Math.round(view.screenX(pendingX)) - size / 2,
                (int) Math.round(view.screenY(pendingZ)) - size / 2,
                MaraudersMapTextures.markerU(style.icon()),
                MaraudersMapTextures.markerV(style.icon()),
                size, size, MaraudersMapTextures.MARKER_CELL, MaraudersMapTextures.MARKER_CELL,
                MaraudersMapTextures.MARKER_SHEET_W, MaraudersMapTextures.MARKER_SHEET_H,
                (style.tint() & 0x00FFFFFF) | 0x99000000);
    }

    private void renderCompass(GuiGraphics gfx) {
        int size = layout.s(MaraudersMapTextures.COMPASS_SIZE);
        int margin = layout.s(CONTROL_MARGIN);
        McStylePanel.drawTintedTexture(gfx, MaraudersMapTextures.COMPASS,
                view.viewX() + margin, view.viewY() + margin, size, size,
                MaraudersMapTextures.COMPASS_SIZE, MaraudersMapTextures.COMPASS_SIZE,
                0xB0000000 | (BRASS & 0x00FFFFFF));
    }

    private void renderHeader(GuiGraphics gfx) {
        int cx = panelX + panelW / 2;
        gfx.drawCenteredString(font, title, cx, panelY + layout.s(7), TITLE_INK);
        gfx.drawCenteredString(font,
                Component.translatable("screen.wizards_and_beasts.marauders_map.oath")
                        .withStyle(ChatFormatting.ITALIC),
                cx, panelY + layout.s(18), PARCHMENT_INK_DIM);
        McStylePanel.drawSkinDivider(gfx, MaraudersMapTextures.SKIN,
                panelX + layout.s(VIEW_INSET), panelY + layout.s(HEADER_H) - layout.s(6),
                panelW - 2 * layout.s(VIEW_INSET));
    }

    /**
     * Coordinates, dimension and how much of this world the map has charted.
     *
     * <p>Reads the point under the cursor when the cursor is on the page and the holder's own
     * position otherwise, which is the behaviour that answers both "where am I" and "what is that"
     * without a second readout or a mode switch.
     */
    private void renderFooter(GuiGraphics gfx) {
        int textY = panelY + panelH - layout.s(FOOTER_H) + layout.s(8);
        int left = panelX + layout.s(VIEW_INSET);

        gfx.drawString(font, coordinateReadout(), left, textY, PARCHMENT_INK, false);

        Component charted = Component.translatable(
                "screen.wizards_and_beasts.marauders_map.charted", ClientMapAtlas.chartedTiles());
        gfx.drawString(font, charted, left, textY - layout.s(9), PARCHMENT_INK_DIM, false);
    }

    private Component coordinateReadout() {
        double mouseX = Minecraft.getInstance().mouseHandler.getScaledXPos(
                Minecraft.getInstance().getWindow());
        double mouseY = Minecraft.getInstance().mouseHandler.getScaledYPos(
                Minecraft.getInstance().getWindow());
        if (view.contains(mouseX, mouseY)) {
            return Component.translatable("screen.wizards_and_beasts.marauders_map.coords",
                    (int) Math.floor(view.worldX(mouseX)),
                    (int) Math.floor(view.worldZ(mouseY)));
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return Component.empty();
        }
        return Component.translatable("screen.wizards_and_beasts.marauders_map.coords_self",
                player.getBlockX(), player.getBlockY(), player.getBlockZ());
    }

    private void renderTooltips(GuiGraphics gfx, int mouseX, int mouseY) {
        if (sidePanel != SidePanel.NONE && mouseX >= sidePanelX()) {
            return; // the side panel owns this half of the screen
        }
        if (hoveredMarker != null) {
            MapMarkerStyle style = MapStyles.marker(hoveredMarker.type());
            List<Component> lines = new ArrayList<>(3);
            lines.add(MapMarkerRenderer.label(hoveredMarker, style));
            lines.add(Component.translatable("screen.wizards_and_beasts.marauders_map.coords",
                    hoveredMarker.x(), hoveredMarker.z()).withStyle(ChatFormatting.GRAY));
            distanceTo(hoveredMarker.x(), hoveredMarker.z()).ifPresent(lines::add);
            drawTooltip(gfx, lines, mouseX, mouseY);
            return;
        }
        if (hoveredEntity != null) {
            List<Component> lines = new ArrayList<>(2);
            lines.add(Component.literal(hoveredEntity.displayName()));
            distanceTo((int) hoveredEntity.x(), (int) hoveredEntity.z()).ifPresent(lines::add);
            drawTooltip(gfx, lines, mouseX, mouseY);
        }
    }

    /**
     * Vanilla's multi-line tooltip takes visual-order text, not components, so the lines are
     * flattened here. Routed through one helper so both call sites lay out identically -- and so
     * the flattening, which is the part that is easy to get wrong for RTL and for bidi names, is
     * written once.
     */
    private void drawTooltip(GuiGraphics gfx, List<Component> lines, int mouseX, int mouseY) {
        List<net.minecraft.util.FormattedCharSequence> visual = new ArrayList<>(lines.size());
        for (Component line : lines) {
            visual.add(line.getVisualOrderText());
        }
        gfx.setTooltipForNextFrame(font, visual, mouseX, mouseY);
    }

    private java.util.Optional<Component> distanceTo(int x, int z) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return java.util.Optional.empty();
        }
        double dx = x - player.getX();
        double dz = z - player.getZ();
        int blocks = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        return java.util.Optional.of(
                Component.translatable("screen.wizards_and_beasts.marauders_map.distance", blocks)
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    // -- Side panels -------------------------------------------------------

    private int sidePanelX() {
        return view.viewX() + view.viewW() - layout.s(SIDE_PANEL_W);
    }

    private int sidePanelY() {
        return view.viewY();
    }

    private int sidePanelH() {
        return view.viewH() - skinned(CONTROL_SIZE) - layout.s(CONTROL_MARGIN * 2);
    }

    private void rebuildSidePanel() {
        nameBox = null;
        waypointRows.clear();
        if (sidePanel != SidePanel.WAYPOINTS) {
            return;
        }
        for (MapMarker marker : ClientMapAtlas.markers()) {
            if (marker.source() != MapMarkerSource.DISCOVERY) {
                waypointRows.add(marker);
            }
        }
        waypointRows.sort(java.util.Comparator.comparingLong(MapMarker::discoveredAt).reversed());

        int x = sidePanelX() + layout.s(4);
        int w = layout.s(SIDE_PANEL_W) - layout.s(8);
        int rowStep = skinned(MIN_SKINNED) + layout.s(2);
        int boxY = sidePanelY() + sidePanelH() - rowStep * 3 - layout.s(4);

        nameBox = new EditBox(font, x, boxY, w, skinned(MIN_SKINNED),
                Component.translatable("screen.wizards_and_beasts.marauders_map.waypoint.name"));
        nameBox.setMaxLength(at.koopro.wizardsandbeasts.map.MapMarker.MAX_LABEL_LENGTH);
        nameBox.setHint(Component.translatable(
                "screen.wizards_and_beasts.marauders_map.waypoint.name_hint"));
        MapMarker selected = selected();
        if (selected != null) {
            nameBox.setValue(selected.label());
        }
        addRenderableWidget(nameBox);

        int btnH = skinned(MIN_SKINNED);
        int btnY = boxY + rowStep;
        int third = w / 3;

        addRenderableWidget(mapButton(x, btnY, third - 1, btnH,
                Component.literal("<"), null, () -> cycleIcon(-1)));
        addRenderableWidget(mapButton(x + third, btnY, third - 1, btnH,
                Component.translatable(MapStyles.marker(currentIconType()).name()), null,
                () -> cycleIcon(1)));
        addRenderableWidget(mapButton(x + third * 2, btnY, w - third * 2, btnH,
                Component.literal(">"), null, () -> cycleIcon(1)));

        int actionY = btnY + rowStep;
        int half = w / 2;
        addRenderableWidget(mapButton(x, actionY, half - 1, btnH,
                Component.translatable(selected == null
                        ? "screen.wizards_and_beasts.marauders_map.waypoint.place"
                        : "screen.wizards_and_beasts.marauders_map.waypoint.rename"),
                null, this::commitWaypoint));
        ThemedButton delete = mapButton(x + half, actionY, w - half, btnH,
                Component.translatable("screen.wizards_and_beasts.marauders_map.waypoint.delete"),
                null, this::deleteSelected);
        delete.active = selected != null;
        addRenderableWidget(delete);
    }

    private @Nullable MapMarker selected() {
        if (selectedMarker == null) {
            return null;
        }
        for (MapMarker marker : ClientMapAtlas.markers()) {
            if (marker.id().equals(selectedMarker)) {
                return marker;
            }
        }
        return null;
    }

    private Identifier currentIconType() {
        MapMarker selected = selected();
        if (selected != null && MapMarkerTypes.WAYPOINT_ICONS.contains(selected.type())) {
            return selected.type();
        }
        return MapMarkerTypes.WAYPOINT_ICONS.get(pendingIconIndex);
    }

    private void cycleIcon(int delta) {
        int size = MapMarkerTypes.WAYPOINT_ICONS.size();
        MapMarker selected = selected();
        if (selected != null) {
            int current = Math.max(0, MapMarkerTypes.WAYPOINT_ICONS.indexOf(selected.type()));
            Identifier next = MapMarkerTypes.WAYPOINT_ICONS.get(Math.floorMod(current + delta, size));
            ClientPacketDistributor.sendToServer(
                    MapWaypointC2SPayload.retype(selected.id(), next));
        } else {
            pendingIconIndex = Math.floorMod(pendingIconIndex + delta, size);
        }
        rebuild();
    }

    /**
     * Plants a new pin, or renames the selected one.
     *
     * <p>One button for both because they are the same gesture from the player's side: type a name,
     * confirm. Which of the two it is depends only on whether a pin is selected, and the label says
     * which.
     */
    private void commitWaypoint() {
        String name = nameBox == null ? "" : nameBox.getValue().trim();
        MapMarker selected = selected();
        if (selected != null) {
            ClientPacketDistributor.sendToServer(MapWaypointC2SPayload.rename(selected.id(), name));
        } else {
            int x = placing ? pendingX : playerBlockX();
            int z = placing ? pendingZ : playerBlockZ();
            ClientPacketDistributor.sendToServer(MapWaypointC2SPayload.create(
                    MapMarkerTypes.WAYPOINT_ICONS.get(pendingIconIndex), x, z, name));
            placing = false;
        }
        if (nameBox != null) {
            nameBox.setValue("");
        }
    }

    private void deleteSelected() {
        MapMarker selected = selected();
        if (selected != null) {
            ClientPacketDistributor.sendToServer(MapWaypointC2SPayload.delete(selected.id()));
            selectedMarker = null;
            rebuild();
        }
    }

    private int playerBlockX() {
        var player = Minecraft.getInstance().player;
        return player == null ? 0 : player.getBlockX();
    }

    private int playerBlockZ() {
        var player = Minecraft.getInstance().player;
        return player == null ? 0 : player.getBlockZ();
    }

    private void renderWaypointPanel(GuiGraphics gfx, int mouseX, int mouseY) {
        int x = sidePanelX();
        int y = sidePanelY();
        int w = layout.s(SIDE_PANEL_W);
        int h = sidePanelH();
        McStylePanel.drawSkinPanel(gfx, MaraudersMapTextures.SKIN, x, y, w, h);
        gfx.drawString(font,
                Component.translatable("screen.wizards_and_beasts.marauders_map.waypoint.title"),
                x + layout.s(5), y + layout.s(5), TITLE_INK, false);

        int rowH = layout.s(12);
        int listTop = y + layout.s(16);
        int listBottom = y + h - (skinned(MIN_SKINNED) + layout.s(2)) * 3 - layout.s(6);
        int visible = Math.max(0, (listBottom - listTop) / rowH);

        gfx.enableScissor(x, listTop, x + w, listBottom);
        for (int i = 0; i < visible && waypointScroll + i < waypointRows.size(); i++) {
            MapMarker marker = waypointRows.get(waypointScroll + i);
            int rowY = listTop + i * rowH;
            boolean isSelected = marker.id().equals(selectedMarker);
            if (isSelected) {
                gfx.fill(x + layout.s(3), rowY - 1, x + w - layout.s(3), rowY + rowH - 2, 0x33000000);
            }
            MapMarkerStyle style = MapStyles.marker(marker.type());
            gfx.blit(RenderPipelines.GUI_TEXTURED, MaraudersMapTextures.MARKERS,
                    x + layout.s(5), rowY, MaraudersMapTextures.markerU(style.icon()),
                    MaraudersMapTextures.markerV(style.icon()),
                    layout.s(8), layout.s(8),
                    MaraudersMapTextures.MARKER_CELL, MaraudersMapTextures.MARKER_CELL,
                    MaraudersMapTextures.MARKER_SHEET_W, MaraudersMapTextures.MARKER_SHEET_H,
                    style.tint());
            Component label = MapMarkerRenderer.label(marker, style);
            gfx.drawString(font, font.plainSubstrByWidth(label.getString(), w - layout.s(20)),
                    x + layout.s(16), rowY, isSelected ? TITLE_INK : PARCHMENT_INK, false);
        }
        gfx.disableScissor();

        if (waypointRows.isEmpty()) {
            gfx.drawString(font,
                    Component.translatable("screen.wizards_and_beasts.marauders_map.waypoint.empty")
                            .withStyle(ChatFormatting.ITALIC),
                    x + layout.s(5), listTop + layout.s(4), PARCHMENT_INK_DIM, false);
        }
    }

    // -- Input -------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (super.mouseClicked(event, isDoubleClick)) {
            return true;
        }
        if (sidePanel == SidePanel.WAYPOINTS && inSidePanel(event.x(), event.y())) {
            return clickWaypointRow(event.y());
        }
        if (!view.contains(event.x(), event.y())) {
            return false;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            // Right-click plants a pin where you are pointing, which is the fast path. The panel
            // opens with the position already captured so naming it is one keystroke away.
            pendingX = (int) Math.floor(view.worldX(event.x()));
            pendingZ = (int) Math.floor(view.worldZ(event.y()));
            placing = true;
            selectedMarker = null;
            sidePanel = SidePanel.WAYPOINTS;
            rebuild();
            if (nameBox != null) {
                setFocused(nameBox);
            }
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (hoveredMarker != null && hoveredMarker.source() != MapMarkerSource.DISCOVERY) {
                selectedMarker = hoveredMarker.id();
                placing = false;
                sidePanel = SidePanel.WAYPOINTS;
                rebuild();
                return true;
            }
            dragging = true;
            return true;
        }
        return false;
    }

    private boolean inSidePanel(double mouseX, double mouseY) {
        return mouseX >= sidePanelX() && mouseX < sidePanelX() + layout.s(SIDE_PANEL_W)
                && mouseY >= sidePanelY() && mouseY < sidePanelY() + sidePanelH();
    }

    private boolean clickWaypointRow(double mouseY) {
        int rowH = layout.s(12);
        int listTop = sidePanelY() + layout.s(16);
        int index = waypointScroll + (int) ((mouseY - listTop) / rowH);
        if (mouseY < listTop || index < 0 || index >= waypointRows.size()) {
            return false;
        }
        MapMarker marker = waypointRows.get(index);
        selectedMarker = marker.id().equals(selectedMarker) ? null : marker.id();
        placing = false;
        // Selecting a pin also takes you to it: a waypoint list that names a place but will not
        // show you where it is has answered half the question.
        if (selectedMarker != null) {
            view.centerOn(marker.x(), marker.z());
        }
        rebuild();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            view.panByPixels(dragX, dragY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (sidePanel == SidePanel.WAYPOINTS && inSidePanel(mouseX, mouseY)) {
            int maxScroll = Math.max(0, waypointRows.size() - 1);
            waypointScroll = Math.clamp(waypointScroll - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        if (!view.contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        view.zoomAbout(mouseX, mouseY, scrollY > 0 ? MapView.ZOOM_STEP : 1.0 / MapView.ZOOM_STEP);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // The name box owns the keyboard while it has focus, or typing "wand" pans the map west.
        if (nameBox != null && nameBox.isFocused()) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                commitWaypoint();
                return true;
            }
            return super.keyPressed(event);
        }

        double panStep = 32.0;
        switch (event.key()) {
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                view.zoomAboutCenter(MapView.ZOOM_STEP * MapView.ZOOM_STEP);
                return true;
            }
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                view.zoomAboutCenter(1.0 / (MapView.ZOOM_STEP * MapView.ZOOM_STEP));
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                view.centerOn(view.panX() - panStep, view.panZ());
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                view.centerOn(view.panX() + panStep, view.panZ());
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                view.centerOn(view.panX(), view.panZ() - panStep);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                view.centerOn(view.panX(), view.panZ() + panStep);
                return true;
            }
            case GLFW.GLFW_KEY_C -> {
                centerOnPlayer();
                return true;
            }
            case GLFW.GLFW_KEY_L -> {
                toggleSidePanel(SidePanel.LEGEND);
                return true;
            }
            case GLFW.GLFW_KEY_H -> {
                showHiddenMarkers = !showHiddenMarkers;
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ClientPacketDistributor.sendToServer(new MapCloseC2SPayload());
        ClientMapAtlas.close();
        MapTrails.clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
