package at.koopro.wizardsandbeasts.client.bestiary.gui;

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
import java.util.Set;

public final class BestiaryScreen extends Screen {
    private static final int W = 320;
    private static final int H = 200;
    private static final int ROW_HEIGHT = 14;
    private static final int ROW_STEP = 16;
    private static final int LIST_START_Y = 32;
    /** Right edge of list row content (scrollbar sits just to the right). */
    private static final int LIST_ROWS_W = 112;
    private static final int SCROLLBAR_W = 3;
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
    private static final Identifier TEX_SCROLL_TRACK =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/bestiary/scroll_track.png");
    private static final Identifier TEX_SCROLL_THUMB =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/bestiary/scroll_thumb.png");
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
        search = new EditBox(font, x + Math.round(6 * guiScale), y + Math.round(6 * guiScale),
                Math.round(108 * guiScale), Math.round(16 * guiScale),
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
        return Math.max(1, (H - (LIST_START_Y + 18)) / ROW_STEP);
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - visibleRowCount());
    }

    private int listRowsRight() {
        return x + 6 + LIST_ROWS_W;
    }

    private int scrollbarLeft() {
        return listRowsRight() + 1;
    }

    private static String formatCategoryLabel(BestiaryCategory category) {
        String raw = category.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder(raw.length());
        boolean capNext = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                capNext = true;
                out.append(c);
            } else if (capNext) {
                out.append(Character.toTitleCase(c));
                capNext = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private void renderListScrollbar(GuiGraphics gg, int trackTop, int trackBottom) {
        int trackLeft = scrollbarLeft();
        drawStretched(gg, TEX_SCROLL_TRACK, trackLeft, trackTop, SCROLLBAR_W, trackBottom - trackTop, SCROLLBAR_W, 120);
        int max = maxScroll();
        if (max <= 0 || rows.isEmpty()) {
            return;
        }
        int trackH = trackBottom - trackTop;
        int thumbH = Math.max(6, trackH * visibleRowCount() / rows.size());
        int travel = Math.max(0, trackH - thumbH);
        int thumbY = trackTop + (travel == 0 ? 0 : scrollOffset * travel / max);
        drawStretched(gg, TEX_SCROLL_THUMB, trackLeft, thumbY, SCROLLBAR_W, thumbH, SCROLLBAR_W, 24);
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
        if (tier == DiscoveryTier.UNDISCOVERED) {
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
    private LivingEntity getRenderEntity(BestiaryEntry entry) {
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
        int pX = x + 132;
        int pY = y + 22;
        int pSize = 32;
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
        drawStretched(gg, TEX_SCREEN, x, y, W, H, W, H);
        drawStretched(gg, TEX_LEFT_PANEL, x + 4, y + 28, 120, H - 32, 120, 168);
        drawStretched(gg, TEX_RIGHT_PANEL, x + 126, y + 4, W - 130, H - 8, 190, 190);
        gg.drawString(font, title, x + 8, y - 10, BestiaryColors.TEXT);

        int listTop = y + LIST_START_Y;
        int listBottom = y + H - 18;
        int rowY = listTop;
        int start = Math.min(scrollOffset, rows.size());
        int end = Math.min(rows.size(), start + visibleRowCount());
        int rowRight = listRowsRight();
        for (int idx = start; idx < end; idx++) {
            Row row = rows.get(idx);
            if (row.header != null) {
                drawStretched(gg, TEX_HEADER, x + 6, rowY, LIST_ROWS_W, ROW_HEIGHT, 112, 14);
                boolean isCollapsed = collapsed.getOrDefault(row.header, Boolean.FALSE);
                String arrow = isCollapsed ? "\u25b6 " : "\u25bc ";
                gg.drawString(font, Component.literal(arrow + formatCategoryLabel(row.header)), x + 8, rowY + 3, BestiaryColors.TEXT);
            } else if (row.entry != null) {
                BestiaryEntry e = row.entry;
                boolean isSel = e.id().equals(selected);
                drawStretched(gg, TEX_ROW, x + 6, rowY, LIST_ROWS_W, ROW_HEIGHT, 112, 14);
                if (isSel) {
                    gg.fill(x + 6, rowY, rowRight, rowY + ROW_HEIGHT, 0x335A84C5);
                }
                DiscoveryTier tier = ClientBestiaryCache.get().tiers().getOrDefault(e.id(), DiscoveryTier.UNDISCOVERED);
                Component name = tier == DiscoveryTier.UNDISCOVERED ? Component.literal("???") : e.displayName();
                int nameMaxWidth = 72;
                String clipped = font.plainSubstrByWidth(name.getString(), nameMaxWidth);
                gg.drawString(font, Component.literal(clipped), x + 8, rowY + 3, tier == DiscoveryTier.UNDISCOVERED ? BestiaryColors.TEXT_DIM : BestiaryColors.TEXT);
                for (int i = 0; i < 5; i++) {
                    int c = i <= tier.tierIndex() ? BestiaryColors.PIP_ON : BestiaryColors.PIP_OFF;
                    gg.fill(x + 82 + i * 5, rowY + 5, x + 85 + i * 5, rowY + 8, c);
                }
            }
            rowY += ROW_STEP;
        }
        renderListScrollbar(gg, listTop, listBottom);

        BestiaryEntry selectedEntry = selected == null ? null : BestiaryEntryRegistry.clientGet(selected);
        if (selectedEntry != null) {
            renderDetail(gg, selectedEntry);
        }

        pose.popMatrix();

        // Live entity render happens in screen space (outside the design-scale matrix), since
        // the entity helper sets up its own projection/scissor from raw screen coordinates.
        if (selectedEntry != null) {
            DiscoveryTier detailTier = ClientBestiaryCache.get().tiers()
                    .getOrDefault(selectedEntry.id(), DiscoveryTier.UNDISCOVERED);
            if (detailTier != DiscoveryTier.UNDISCOVERED) {
                LivingEntity living = getRenderEntity(selectedEntry);
                if (living != null) {
                    renderBestiaryEntity(gg, living, mouseX, mouseY);
                }
            }
        }

        int dmx = (int) toDesignX(mouseX);
        int dmy = (int) toDesignY(mouseY);
        if (dmx >= x + 6 && dmx <= listRowsRight() && dmy >= listTop && dmy <= listBottom) {
            int hovered = (dmy - listTop) / ROW_STEP + start;
            if (hovered >= 0 && hovered < rows.size()) {
                Row row = rows.get(hovered);
                if (row.entry != null) {
                    DiscoveryTier tier = ClientBestiaryCache.get().tiers().getOrDefault(row.entry.id(), DiscoveryTier.UNDISCOVERED);
                    if (tier == DiscoveryTier.UNDISCOVERED) {
                        gg.setTooltipForNextFrame(font, Component.literal(tier.unlockHint()), mouseX, mouseY);
                    }
                }
            }
        }

        super.render(gg, mouseX, mouseY, partialTick);
    }

    private void renderDetail(GuiGraphics gg, BestiaryEntry entry) {
        int dx = x + 132;
        int dy = y + 10;
        DiscoveryTier tier = ClientBestiaryCache.get().tiers().getOrDefault(entry.id(), DiscoveryTier.UNDISCOVERED);
        Component name = tier == DiscoveryTier.UNDISCOVERED ? Component.literal("???") : entry.displayName();
        gg.drawString(font, name, dx, dy, BestiaryColors.TEXT);
        dy += 12;

        int portraitX = dx;
        int portraitY = dy;
        int portraitSize = 32;
        // When the entry has a live entity, it is drawn later in screen space (post-scale);
        // only fall back to the static portrait texture when no entity is available.
        boolean liveEntity = tier != DiscoveryTier.UNDISCOVERED && getRenderEntity(entry) != null;
        if (!liveEntity) {
            drawStretched(gg, resolveEntryPortrait(entry, tier), portraitX, portraitY, portraitSize, portraitSize, 32, 32);
        }
        gg.fill(portraitX, portraitY + portraitSize, portraitX + portraitSize, portraitY + portraitSize + 1, 0x664F5B72);
        gg.fill(portraitX + portraitSize, portraitY, portraitX + portraitSize + 1, portraitY + portraitSize, 0x664F5B72);

        int textX = portraitX + portraitSize + 8;
        int textW = 176 - (portraitSize + 8);
        if (tier == DiscoveryTier.UNDISCOVERED) {
            gg.drawWordWrap(font, Component.literal(tier.unlockHint()), textX, portraitY, textW, BestiaryColors.TEXT_DIM);
            return;
        }
        gg.drawString(font, Component.literal("MM Rating: " + entry.mmRating()), textX, portraitY, BestiaryColors.TEXT_DIM);
        int loreY = portraitY + 12;
        if (tier.ordinal() >= DiscoveryTier.ENCOUNTERED.ordinal()) {
            gg.drawWordWrap(font, entry.shortLore(), textX, loreY, textW, BestiaryColors.TEXT);
            loreY += font.wordWrapHeight(entry.shortLore(), textW) + 4;
        }
        dy = Math.max(portraitY + portraitSize + 6, loreY);
        if (tier.ordinal() >= DiscoveryTier.ENCOUNTERED.ordinal()) {
            Component habitatLine = Component.literal("Habitat: ").append(entry.habitat());
            gg.drawWordWrap(font, habitatLine, dx, dy, 176, BestiaryColors.TEXT_DIM);
            dy += font.wordWrapHeight(habitatLine, 176) + 2;
            Component sizeLine = Component.literal("Size: " + entry.size());
            gg.drawWordWrap(font, sizeLine, dx, dy, 176, BestiaryColors.TEXT_DIM);
            dy += font.wordWrapHeight(sizeLine, 176) + 2;
        }
        if (tier.ordinal() >= DiscoveryTier.STUDIED.ordinal()) {
            gg.drawWordWrap(font, entry.fullLore(), dx, dy, 176, BestiaryColors.TEXT);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, isDoubleClick);
        }

        double mouseX = toDesignX(event.x());
        double mouseY = toDesignY(event.y());
        if (mouseX >= x + 6 && mouseX <= listRowsRight() && mouseY >= y + LIST_START_Y && mouseY <= y + H - 18) {
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
        if (dmx >= x + 4 && dmx <= x + 124 && dmy >= y + 28 && dmy <= y + H - 4) {
            scrollOffset -= (int) Math.signum(scrollY);
            if (scrollOffset < 0) {
                scrollOffset = 0;
            } else if (scrollOffset > maxScroll()) {
                scrollOffset = maxScroll();
            }
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
