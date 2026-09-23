package at.koopro.wizardsandbeasts.client.bestiary.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedTextField;
import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.bestiary.*;
import at.koopro.wizardsandbeasts.client.bestiary.ClientBestiaryCache;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BestiaryScreen extends Screen {
    /**
     * The sheet. Grown from 320x200 when the Bestiary became parchment: the sheet's double ink rule
     * runs 4-6px in, so everything that sat 4-8px from the edge moved to 12, and the title came
     * inside the frame instead of floating above it over the world. {@code gui_chrome.py} draws the
     * sheet and both wells at exactly these sizes ({@code BESTIARY_*}); the two must agree.
     */
    private static final int W = 344;
    private static final int H = 216;
    private static final int ROW_HEIGHT = 14;

    /** Clearance from the sheet's edge: the frame's inner rule sits at 6px. */
    private static final int FRAME_PAD = WizardsMetrics.SPACE_L;
    /** Title baseline and the rule under it, as on the Character Sheet. */
    private static final int TITLE_TEXT_Y = 10;
    private static final int TITLE_RULE_Y = 18;
    /** Top of both columns, below the title rule. */
    private static final int BODY_TOP = 28;

    /** The index column: the search box, then the inset well the rows sit in. */
    private static final int LIST_PANEL_W = 128;
    private static final int SEARCH_H = 16;
    private static final int LIST_PANEL_Y = BODY_TOP + SEARCH_H + WizardsMetrics.SPACE_S;
    private static final int LIST_PANEL_H = H - FRAME_PAD - LIST_PANEL_Y;
    /** Rows start one step in from the well, clear of its engraved edge. */
    private static final int LIST_X = FRAME_PAD + WizardsMetrics.SPACE_S;

    /** The detail column: one inset well from the index to the sheet's right padding. */
    private static final int DETAIL_PANEL_X = FRAME_PAD + LIST_PANEL_W + 6;
    private static final int DETAIL_PANEL_W = W - FRAME_PAD - DETAIL_PANEL_X;
    private static final int DETAIL_PANEL_H = H - FRAME_PAD - BODY_TOP;
    /** Detail text origin, clear of the well's edge. */
    private static final int DETAIL_X = DETAIL_PANEL_X + 6;
    private static final int DETAIL_Y = BODY_TOP + 6;
    /** Detail scroll track: two pixels, six in from the well's right edge. */
    private static final int DETAIL_TRACK_X = DETAIL_PANEL_X + DETAIL_PANEL_W - 6;
    private static final int DETAIL_W = DETAIL_TRACK_X - 4 - DETAIL_X;
    private static final int DETAIL_BOTTOM = H - FRAME_PAD - 4;

    /** The live creature or placeholder, square, under the entry's name. */
    private static final int PORTRAIT_SIZE = 32;
    private static final int PORTRAIT_Y = DETAIL_Y + 12;

    /** The portrait's drop shadow: the paper's deepest tone, so the frame reads as laid on the page. */
    private static final int PORTRAIT_EDGE = WizardsPalette.PAGE_DEEP;
    /** Detail scroll track and thumb: a groove and an ink stroke. */
    private static final int DETAIL_TRACK = WizardsPalette.PAGE_DEEP;
    private static final int DETAIL_THUMB = WizardsPalette.PAGE_INK_2;
    // On the shared scale rather than local literals.
    private static final int ROW_STEP = WizardsMetrics.ROW_H;
    private static final int LIST_START_Y = LIST_PANEL_Y + WizardsMetrics.SPACE_S;
    /** Bottom of the list, clear of the well's lower edge. */
    private static final int LIST_BOTTOM = LIST_PANEL_Y + LIST_PANEL_H - WizardsMetrics.SPACE_S;
    /** Right edge of list row content (scrollbar sits just to the right). */
    private static final int LIST_ROWS_W = 112;
    private static final Identifier TEX_SCREEN =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/bestiary/screen.png");
    private static final Identifier TEX_LEFT_PANEL =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/bestiary/left_panel.png");
    private static final Identifier TEX_RIGHT_PANEL =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/bestiary/right_panel.png");
    private static final Identifier TEX_ROW =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/bestiary/row.png");
    private static final Identifier TEX_HEADER =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/bestiary/header.png");
    private static final Identifier TEX_ENTRY_PLACEHOLDER =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/bestiary/entry_placeholder.png");

    private int x;
    private int y;
    private float guiScale = 1.0f;
    private EditBox search;
    private final List<BestiaryEntry> entries = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Map<BestiaryCategory, Boolean> collapsed = new EnumMap<>(BestiaryCategory.class);
    private Identifier selected;
    private int scrollOffset = 0;

    /** Detail pane scroll, in wrapped lines; reset whenever the selection changes. */
    private int detailScroll = 0;
    private int detailScrollMax = 0;
    private @Nullable Identifier detailScrollFor;
    private static final int DETAIL_LINE_H = 9;
    private static final int DETAIL_GAP_H = 3;

    /** Cached client-side entity instances for live detail-panel rendering. */
    private final Map<Identifier, LivingEntity> renderEntities = new HashMap<>();
    /** Entity-type ids that failed to resolve to a LivingEntity — don't retry each frame. */
    private final Set<Identifier> renderEntityMisses = new HashSet<>();

    public BestiaryScreen() {
        super(Component.translatable("gui.wizards_and_beasts.bestiary.title"));
        for (BestiaryCategory category : BestiaryCategory.values()) {
            collapsed.put(category, Boolean.FALSE);
        }
    }

    @Override
    protected void init() {
        guiScale = GuiScaleHelper.computeScale(W, H, width, height, GuiScaleHelper.DEFAULT_MARGIN);
        x = GuiScaleHelper.clampedLeft(Math.round(W * guiScale), width, GuiScaleHelper.DEFAULT_MARGIN);
        y = GuiScaleHelper.clampedTop(Math.round(H * guiScale), height, GuiScaleHelper.DEFAULT_MARGIN);
        // Widget lives in screen space (outside the scaled pose), so its bounds scale here.
        search = new ThemedTextField(font, x + Math.round(FRAME_PAD * guiScale), y + Math.round(BODY_TOP * guiScale),
                Math.round(LIST_PANEL_W * guiScale), Math.round(SEARCH_H * guiScale),
                Component.translatable("gui.wizards_and_beasts.bestiary.search"));
        search.setResponder(value -> rebuildRows());
        addRenderableWidget(search);
        refreshList();
    }

    private void refreshList() {
        entries.clear();
        entries.addAll(BestiaryEntryRegistry.clientGetAll());
        entries.sort(Comparator.comparing((BestiaryEntry e) -> e.category().name()).thenComparingInt(BestiaryEntry::sortOrder));
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        String q = search == null ? "" : search.getValue().trim().toLowerCase();
        for (BestiaryCategory category : BestiaryCategory.values()) {
            List<BestiaryEntry> inCategory = entries.stream().filter(e -> e.category() == category).toList();
            List<BestiaryEntry> filtered = new ArrayList<>();
            for (BestiaryEntry e : inCategory) {
                if (!q.isBlank() && !e.id().getPath().contains(q) && !e.displayName().getString().toLowerCase().contains(q)) {
                    continue;
                }
                filtered.add(e);
            }
            if (filtered.isEmpty()) {
                continue;
            }
            rows.add(Row.header(category));
            if (!collapsed.getOrDefault(category, Boolean.FALSE)) {
                for (BestiaryEntry entry : filtered) {
                    rows.add(Row.entry(entry));
                }
            }
        }
        if ((selected == null || rows.stream().noneMatch(r -> r.entry != null && r.entry.id().equals(selected)))) {
            for (Row row : rows) {
                if (row.entry != null) {
                    selected = row.entry.id();
                    break;
                }
            }
        }
        scrollOffset = Math.min(scrollOffset, maxScroll());
    }

    private int visibleRowCount() {
        return Math.max(1, (LIST_BOTTOM - LIST_START_Y) / ROW_STEP);
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - visibleRowCount());
    }

    private int listRowsRight() {
        return x + LIST_X + LIST_ROWS_W;
    }

    /**
     * Left edge of the list scrollbar.
     *
     * <p>Flush against the rows, so the shared 8px bar fits inside the index well: rows end at
     * {@code x+128}, the bar runs to {@code x+136}, and the well's engraved edge is at
     * {@code x+137..139}.
     */
    private int scrollbarLeft() {
        return listRowsRight();
    }

    /**
     * The category header's label.
     *
     * <p>Translated now rather than title-cased from the enum constant: {@code WINGED_BEAST} became
     * "Winged Beast" in every language, which is a mechanical transform of an internal identifier
     * rather than a name anyone wrote.
     */
    private static Component categoryLabel(BestiaryCategory category) {
        return Component.translatable(
                "bestiary.wizards_and_beasts.category." + category.name().toLowerCase(Locale.ROOT));
    }

    /**
     * The list scrollbar, on the shared themed sprites rather than the Bestiary's own 3px pair.
     *
     * <p>Its private textures were 3x120 and 3x24 -- a fixed thumb length that smears when
     * stretched, which is the flaw the shared columnar thumb exists to avoid.
     */
    private void renderListScrollbar(GuiGraphics gg, int trackTop, int trackBottom) {
        int trackLeft = scrollbarLeft();
        int trackH = trackBottom - trackTop;
        int max = maxScroll();
        if (max <= 0 || rows.isEmpty()) {
            McStylePanel.drawScrollbar(gg, trackLeft, trackTop, trackH, trackTop, 0);
            return;
        }
        int thumbH = Math.max(WizardsMetrics.SPACE_M, trackH * visibleRowCount() / rows.size());
        int travel = Math.max(0, trackH - thumbH);
        int thumbY = trackTop + (travel == 0 ? 0 : scrollOffset * travel / max);
        McStylePanel.drawScrollbar(gg, trackLeft, trackTop, trackH, thumbY, thumbH);
    }

    private static void drawStretched(GuiGraphics gg, Identifier texture, int x, int y, int w, int h, int texW, int texH) {
        gg.blit(RenderPipelines.GUI_TEXTURED, texture,
                x, y,
                0.0F, 0.0F,
                w, h,
                texW, texH,
                texW, texH);
    }

    private Identifier resolveEntryPortrait(BestiaryEntry entry, DiscoveryTier tier) {
        if (tier == DiscoveryTier.UNKNOWN) {
            return entry.silhouetteTexture();
        }
        Identifier specific = Identifier.fromNamespaceAndPath(
                WizardsAndBeastsMod.MODID,
                "textures/gui/bestiary/entries/" + entry.id().getPath() + ".png");
        return minecraft != null && minecraft.getResourceManager().getResource(specific).isPresent()
                ? specific
                : TEX_ENTRY_PLACEHOLDER;
    }

    /**
     * Resolve (and cache) a client-side LivingEntity instance for an entry's declared
     * entityType, used for live rendering in the detail panel. Returns {@code null} when
     * the entry has no entityType, the type is unknown, or it is not a LivingEntity.
     */
    private @Nullable LivingEntity getRenderEntity(BestiaryEntry entry) {
        if (entry.entityType().isEmpty() || minecraft == null || minecraft.level == null) {
            return null;
        }
        Identifier typeId = entry.entityType().get();
        if (renderEntityMisses.contains(typeId)) {
            return null;
        }
        LivingEntity cached = renderEntities.get(typeId);
        if (cached != null) {
            return cached;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(typeId);
        if (type == null) {
            renderEntityMisses.add(typeId);
            return null;
        }
        Entity created = type.create(minecraft.level, EntitySpawnReason.LOAD);
        if (created instanceof LivingEntity living) {
            renderEntities.put(typeId, living);
            return living;
        }
        renderEntityMisses.add(typeId);
        return null;
    }

    /** Draw the live entity into the detail-panel portrait box (screen space, post-scale). */
    private void renderBestiaryEntity(GuiGraphics gg, LivingEntity entity, int mouseX, int mouseY) {
        int pX = x + DETAIL_X;
        int pY = y + PORTRAIT_Y;
        int pSize = PORTRAIT_SIZE;
        int sx1 = Math.round(x + (pX - x) * guiScale);
        int sy1 = Math.round(y + (pY - y) * guiScale);
        int sx2 = Math.round(x + (pX + pSize - x) * guiScale);
        int sy2 = Math.round(y + (pY + pSize - y) * guiScale);
        float bbHeight = Math.max(0.5f, entity.getBbHeight());
        int renderScale = Math.max(4, (int) ((sy2 - sy1) * 0.55f / bbHeight));
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                gg, sx1, sy1, sx2, sy2, renderScale, 0.0625f, mouseX, mouseY, entity);
    }

    /** Map a screen-space coordinate into the unscaled design space anchored at (x, y). */
    private double toDesignX(double screenX) {
        return x + (screenX - x) / guiScale;
    }

    private double toDesignY(double screenY) {
        return y + (screenY - y) / guiScale;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(gg);
        // Design-space drawing anchors at (x, y); this transform keeps that point
        // fixed while shrinking, so the draw code below stays unchanged.
        var pose = gg.pose();
        pose.pushMatrix();
        pose.translate(x * (1.0f - guiScale), y * (1.0f - guiScale));
        pose.scale(guiScale, guiScale);
        // The art is generated at exactly these sizes, so every blit is 1:1 and the grain lands
        // where it was drawn.
        drawStretched(gg, TEX_SCREEN, x, y, W, H, W, H);
        drawStretched(gg, TEX_LEFT_PANEL, x + FRAME_PAD, y + LIST_PANEL_Y, LIST_PANEL_W, LIST_PANEL_H,
                LIST_PANEL_W, LIST_PANEL_H);
        drawStretched(gg, TEX_RIGHT_PANEL, x + DETAIL_PANEL_X, y + BODY_TOP, DETAIL_PANEL_W, DETAIL_PANEL_H,
                DETAIL_PANEL_W, DETAIL_PANEL_H);
        // Written on the sheet, clear of the frame's rules, rather than over the world above it.
        gg.drawString(font, title, x + FRAME_PAD, y + TITLE_TEXT_Y, WizardsPalette.PAGE_INK, false);
        McStylePanel.drawDivider(gg, x + FRAME_PAD, y + TITLE_RULE_Y, W - 2 * FRAME_PAD);

        int listTop = y + LIST_START_Y;
        int listBottom = y + LIST_BOTTOM;
        int rowY = listTop;
        int start = Math.min(scrollOffset, rows.size());
        int end = Math.min(rows.size(), start + visibleRowCount());
        int rowRight = listRowsRight();
        for (int idx = start; idx < end; idx++) {
            Row row = rows.get(idx);
            if (row.header != null) {
                drawStretched(gg, TEX_HEADER, x + LIST_X, rowY, LIST_ROWS_W, ROW_HEIGHT, LIST_ROWS_W, ROW_HEIGHT);
                boolean isCollapsed = collapsed.getOrDefault(row.header, Boolean.FALSE);
                String arrow = isCollapsed ? "\u25b6 " : "\u25bc ";
                // Rubricated: a category is a section heading of the index.
                gg.drawString(font, Component.literal(arrow).append(categoryLabel(row.header)),
                        x + LIST_X + 2, rowY + 3, WizardsPalette.PAGE_RUBRIC, false);
            } else if (row.entry != null) {
                BestiaryEntry e = row.entry;
                boolean isSel = e.id().equals(selected);
                // Selection underneath: the row art is only its dotted rule, which stays on top.
                McStylePanel.drawRow(gg, x + LIST_X, rowY, rowRight - (x + LIST_X), ROW_HEIGHT, isSel);
                drawStretched(gg, TEX_ROW, x + LIST_X, rowY, LIST_ROWS_W, ROW_HEIGHT, LIST_ROWS_W, ROW_HEIGHT);
                DiscoveryTier tier = ClientBestiaryCache.get().tiers().getOrDefault(e.id(), DiscoveryTier.UNKNOWN);
                Component name = tier == DiscoveryTier.UNKNOWN
                        ? Component.translatable("bestiary.wizards_and_beasts.entry.unknown")
                        : e.displayName();
                int nameMaxWidth = 72;
                String clipped = font.plainSubstrByWidth(name.getString(), nameMaxWidth);
                gg.drawString(font, Component.literal(clipped), x + LIST_X + 2, rowY + 3,
                        tier == DiscoveryTier.UNKNOWN ? WizardsPalette.PAGE_INK_2 : WizardsPalette.PAGE_INK, false);
                for (int i = 0; i < 5; i++) {
                    int c = i <= tier.tierIndex() ? WizardsPalette.GILT_DARK : WizardsPalette.PAGE_DEEP;
                    gg.fill(x + LIST_X + 76 + i * 5, rowY + 5, x + LIST_X + 79 + i * 5, rowY + 8, c);
                }
            }
            rowY += ROW_STEP;
        }
        if (rows.isEmpty()) {
            gg.drawWordWrap(font, emptyListMessage(), x + LIST_X + 4, listTop + 4, LIST_ROWS_W - 8,
                    WizardsPalette.PAGE_INK_2, false);
        }
        renderListScrollbar(gg, listTop, listBottom);

        BestiaryEntry selectedEntry = selected == null ? null : BestiaryEntryRegistry.clientGet(selected);
        if (selectedEntry != null) {
            renderDetail(gg, selectedEntry);
        } else {
            gg.drawWordWrap(font, Component.translatable("bestiary.wizards_and_beasts.empty.no_selection"),
                    x + DETAIL_X, y + DETAIL_Y, DETAIL_W, WizardsPalette.PAGE_INK_2, false);
        }

        pose.popMatrix();

        // Live entity render happens in screen space (outside the design-scale matrix), since
        // the entity helper sets up its own projection/scissor from raw screen coordinates.
        if (selectedEntry != null) {
            DiscoveryTier detailTier = ClientBestiaryCache.get().tiers()
                    .getOrDefault(selectedEntry.id(), DiscoveryTier.UNKNOWN);
            if (detailTier != DiscoveryTier.UNKNOWN) {
                LivingEntity living = getRenderEntity(selectedEntry);
                if (living != null) {
                    renderBestiaryEntity(gg, living, mouseX, mouseY);
                }
            }
        }

        int dmx = (int) toDesignX(mouseX);
        int dmy = (int) toDesignY(mouseY);
        if (dmx >= x + LIST_X && dmx <= listRowsRight() && dmy >= listTop && dmy <= listBottom) {
            int hovered = (dmy - listTop) / ROW_STEP + start;
            if (hovered >= 0 && hovered < rows.size()) {
                Row row = rows.get(hovered);
                if (row.entry != null) {
                    DiscoveryTier tier = ClientBestiaryCache.get().tiers().getOrDefault(row.entry.id(), DiscoveryTier.UNKNOWN);
                    if (tier == DiscoveryTier.UNKNOWN) {
                        gg.setTooltipForNextFrame(font, tier.unlockHint(), mouseX, mouseY);
                    }
                }
            }
        }

        super.render(gg, mouseX, mouseY, partialTick);
    }

    /**
     * Why the list is blank.
     *
     * <p>Three distinguishable causes and they used to share one blank panel: the search matched
     * nothing, the server has not sent its entries yet, or the pack ships none at all. A player
     * cannot tell "type something else" from "wait a moment" from "this is broken" without being
     * told which it is.
     */
    private Component emptyListMessage() {
        if (entries.isEmpty()) {
            return Component.translatable("bestiary.wizards_and_beasts.empty.no_entries");
        }
        return Component.translatable("bestiary.wizards_and_beasts.empty.no_matches");
    }

    private void renderDetail(GuiGraphics gg, BestiaryEntry entry) {
        int dx = x + DETAIL_X;
        int dy = y + DETAIL_Y;
        if (!entry.id().equals(detailScrollFor)) {
            detailScrollFor = entry.id();
            detailScroll = 0;
        }
        DiscoveryTier tier = ClientBestiaryCache.get().tiers().getOrDefault(entry.id(), DiscoveryTier.UNKNOWN);
        Component name = tier == DiscoveryTier.UNKNOWN
                ? Component.translatable("bestiary.wizards_and_beasts.entry.unobserved")
                : entry.displayName();
        gg.drawString(font, name, dx, dy, WizardsPalette.PAGE_INK, false);
        dy += PORTRAIT_Y - DETAIL_Y;

        int portraitX = dx;
        int portraitY = dy;
        int portraitSize = PORTRAIT_SIZE;
        // When the entry has a live entity, it is drawn later in screen space (post-scale);
        // only fall back to the static portrait texture when no entity is available.
        boolean liveEntity = tier != DiscoveryTier.UNKNOWN && getRenderEntity(entry) != null;
        if (!liveEntity) {
            drawStretched(gg, resolveEntryPortrait(entry, tier), portraitX, portraitY, portraitSize, portraitSize, 32, 32);
        }
        gg.fill(portraitX, portraitY + portraitSize, portraitX + portraitSize, portraitY + portraitSize + 1, PORTRAIT_EDGE);
        gg.fill(portraitX + portraitSize, portraitY, portraitX + portraitSize + 1, portraitY + portraitSize, PORTRAIT_EDGE);

        int textX = portraitX + portraitSize + 8;
        int textW = DETAIL_W - (portraitSize + 8);
        if (tier == DiscoveryTier.UNKNOWN) {
            gg.drawWordWrap(font, tier.unlockHint(), textX, portraitY, textW, WizardsPalette.PAGE_INK_2, false);
            return;
        }
        gg.drawString(font, Component.translatable("bestiary.wizards_and_beasts.field.rating",
                ministryGrade(entry.mmRating())), textX, portraitY, WizardsPalette.PAGE_INK_2, false);
        Optional<CreatureProfile> profile = entry.profile();
        profile.ifPresent(p -> gg.drawString(font, p.classification().displayName(), textX, portraitY + 10,
                WizardsPalette.PAGE_INK_2, false));
        gg.drawString(font, tier.displayName(), textX, portraitY + 20, WizardsPalette.PAGE_INK, false);

        List<Facet> facets = facetsFor(entry, tier);
        int bodyTop = portraitY + portraitSize + 6;
        int bodyBottom = y + DETAIL_BOTTOM;
        List<Line> lines = new ArrayList<>();
        for (Facet facet : facets) {
            for (net.minecraft.util.FormattedCharSequence seq : font.split(facet.text(), DETAIL_W)) {
                lines.add(new Line(seq, facet.color()));
            }
            lines.add(Line.GAP);
        }
        int visible = Math.max(1, (bodyBottom - bodyTop) / DETAIL_LINE_H);
        detailScrollMax = Math.max(0, lines.size() - visible);
        detailScroll = Math.clamp(detailScroll, 0, detailScrollMax);
        int lineY = bodyTop;
        for (int i = detailScroll; i < lines.size() && lineY + DETAIL_LINE_H <= bodyBottom; i++) {
            Line line = lines.get(i);
            if (line.text() != null) {
                gg.drawString(font, line.text(), dx, lineY, line.color(), false);
                lineY += DETAIL_LINE_H;
            } else {
                lineY += DETAIL_GAP_H;
            }
        }
        if (detailScrollMax > 0) {
            int trackX = x + DETAIL_TRACK_X;
            int trackH = bodyBottom - bodyTop;
            gg.fill(trackX, bodyTop, trackX + 2, bodyBottom, DETAIL_TRACK);
            int thumbH = Math.max(8, trackH * visible / lines.size());
            int thumbY = bodyTop + (trackH - thumbH) * detailScroll / detailScrollMax;
            gg.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, DETAIL_THUMB);
        }
    }

    private record Facet(Component text, int color) {}

    private record Line(net.minecraft.util.@Nullable FormattedCharSequence text, int color) {
        static final Line GAP = new Line(null, 0);
    }

    /**
     * What a page shows at each depth of knowledge. Seeing a creature tells you what it looks like and where it
     * lives; watching it tells you how it behaves and what it eats; working with it tells you its magic, how to
     * approach it, what it yields and what wizards make of it; knowing it adds the thing it is famous for.
     */
    private static List<Facet> facetsFor(BestiaryEntry entry, DiscoveryTier tier) {
        List<Facet> out = new ArrayList<>();
        Optional<CreatureProfile> profile = entry.profile();
        String field = "bestiary.wizards_and_beasts.field.";
        out.add(new Facet(entry.shortLore(), WizardsPalette.PAGE_INK));
        out.add(new Facet(Component.translatable(field + "habitat", entry.habitat()), WizardsPalette.PAGE_INK_2));
        out.add(new Facet(Component.translatable(field + "size", sizeName(entry.size())), WizardsPalette.PAGE_INK_2));

        if (tier.atLeast(DiscoveryTier.OBSERVED)) {
            profile.ifPresent(p -> {
                out.add(new Facet(Component.translatable(field + "behaviour", Component.translatable(p.behaviour())),
                        WizardsPalette.PAGE_INK));
                out.add(new Facet(Component.translatable(field + "diet", Component.translatable(p.diet())),
                        WizardsPalette.PAGE_INK_2));
            });
            for (String threat : entry.threatKeys()) {
                out.add(new Facet(Component.translatable(field + "threat", Component.translatable(threat)),
                        WizardsPalette.PAGE_INK_2));
            }
        }

        if (tier.atLeast(DiscoveryTier.STUDIED)) {
            out.add(new Facet(entry.fullLore(), WizardsPalette.PAGE_INK));
            for (String ability : entry.magicAbilityKeys()) {
                out.add(new Facet(Component.translatable(field + "magic", Component.translatable(ability)),
                        WizardsPalette.PAGE_INK_2));
            }
            for (String weakness : entry.weaknessKeys()) {
                out.add(new Facet(Component.translatable(field + "weakness", Component.translatable(weakness)),
                        WizardsPalette.PAGE_INK_2));
            }
            profile.ifPresent(p -> {
                for (String interaction : p.interactions()) {
                    out.add(new Facet(Component.translatable(field + "interaction",
                            Component.translatable(interaction)), WizardsPalette.PAGE_INK));
                }
                for (CreatureProfile.Material material : p.materials()) {
                    Component item = BuiltInRegistries.ITEM.getOptional(material.item())
                            .map(found -> found.getName())
                            .orElse(Component.literal(material.item().toString()));
                    out.add(new Facet(Component.translatable(field + "material", item, material.how().displayName()),
                            WizardsPalette.PAGE_INK_2));
                }
                out.add(new Facet(Component.translatable(field + "society", Component.translatable(p.society())),
                        WizardsPalette.PAGE_INK));
            });
        }

        if (tier.atLeast(DiscoveryTier.KNOWN)) {
            profile.flatMap(CreatureProfile::signature).ifPresent(signature -> out.add(new Facet(
                    Component.translatable(field + "signature", Component.translatable(signature)),
                    WizardsPalette.PAGE_INK)));
        } else {
            // Something still to earn: say how, rather than ending the page on blank parchment.
            out.add(new Facet(tier.unlockHint(), WizardsPalette.PAGE_INK_2));
            profile.ifPresent(p -> {
                if (tier.ordinal() < DiscoveryTier.STUDIED.ordinal() && p.studiedByHand()) {
                    List<Component> acts = new ArrayList<>();
                    p.study().stream().filter(act -> act != CreatureProfile.StudyAct.WATCH)
                            .forEach(act -> acts.add(act.displayName()));
                    out.add(new Facet(Component.translatable(field + "study",
                            net.minecraft.network.chat.ComponentUtils.formatList(acts, Component.literal(", "))),
                            WizardsPalette.PAGE_INK_2));
                }
                if (p.hasSignature()) {
                    out.add(new Facet(Component.translatable(field + "signature_pending"), WizardsPalette.PAGE_INK_2));
                }
            });
        }
        profile.ifPresent(p -> out.add(new Facet(Component.translatable(field + "basis", p.basis().displayName()),
                WizardsPalette.PAGE_INK_2)));
        return out;
    }

    /** {@code LARGE} rendered as localised copy rather than the raw enum constant. */
    private static Component sizeName(BestiarySize size) {
        return Component.translatable(
                "bestiary.wizards_and_beasts.size." + size.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Renders a Ministry of Magic classification the way every canon source writes it — as
     * repeated {@code X} glyphs (X … XXXXX) rather than a bare integer.
     *
     * <p>An absent rating means the creature holds no Ministry grade at all (see
     * {@link BestiaryEntry#mmRating()}) and renders as a translated placeholder, not as an empty
     * string. The clamp is kept although the codec now rejects out-of-range values at load: a
     * client can still reach this method with a stale {@link ClientBestiaryCache} entry synced by
     * an older server.
     */
    private static Component ministryGrade(Optional<Integer> mmRating) {
        if (mmRating.isEmpty()) {
            return Component.translatable("bestiary.wizards_and_beasts.rating.unclassified");
        }
        return Component.literal("X".repeat(Math.clamp(mmRating.get(), 0, 5)));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, isDoubleClick);
        }

        double mouseX = toDesignX(event.x());
        double mouseY = toDesignY(event.y());
        if (mouseX >= x + LIST_X && mouseX <= listRowsRight() && mouseY >= y + LIST_START_Y
                && mouseY <= y + LIST_BOTTOM) {
            int idx = (int) ((mouseY - (y + LIST_START_Y)) / ROW_STEP) + scrollOffset;
            if (idx >= 0 && idx < rows.size()) {
                Row row = rows.get(idx);
                if (row.header != null) {
                    boolean current = collapsed.getOrDefault(row.header, Boolean.FALSE);
                    collapsed.put(row.header, !current);
                    rebuildRows();
                } else if (row.entry != null) {
                    selected = row.entry.id();
                }
                return true;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double dmx = toDesignX(mouseX);
        double dmy = toDesignY(mouseY);
        if (dmx >= x + FRAME_PAD && dmx <= x + FRAME_PAD + LIST_PANEL_W
                && dmy >= y + BODY_TOP && dmy <= y + H - FRAME_PAD) {
            scrollOffset -= (int) Math.signum(scrollY);
            if (scrollOffset < 0) {
                scrollOffset = 0;
            } else if (scrollOffset > maxScroll()) {
                scrollOffset = maxScroll();
            }
            return true;
        }
        if (dmx >= x + DETAIL_PANEL_X && dmx <= x + DETAIL_PANEL_X + DETAIL_PANEL_W
                && dmy >= y + BODY_TOP && dmy <= y + H - FRAME_PAD) {
            detailScroll = Math.clamp(detailScroll - (int) Math.signum(scrollY) * 2, 0, detailScrollMax);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class Row {
        private final BestiaryCategory header;
        private final BestiaryEntry entry;

        private Row(BestiaryCategory header, BestiaryEntry entry) {
            this.header = header;
            this.entry = entry;
        }

        private static Row header(BestiaryCategory category) {
            return new Row(category, null);
        }

        private static Row entry(BestiaryEntry entry) {
            return new Row(null, entry);
        }
    }
}
