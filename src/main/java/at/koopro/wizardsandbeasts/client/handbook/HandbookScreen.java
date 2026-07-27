package at.koopro.wizardsandbeasts.client.handbook;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.network.ClientScreenHooks;
import at.koopro.wizardsandbeasts.handbook.HandbookChapter;
import at.koopro.wizardsandbeasts.handbook.HandbookChapterManager;
import at.koopro.wizardsandbeasts.handbook.HandbookPage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Datapack-driven Ministry handbook screen, styled as a bound guidebook (parchment pages in a
 * leather-and-wood cover, after the Patchouli/Lavender aesthetic). Reads from
 * {@link HandbookChapterManager#CLIENT_CHAPTERS} only — never touches server state.
 *
 * <p>The book background (aubergine cover, gold pinstripe, two parchment pages, plum spine) and
 * the Ministry {@code M} emblem are PNG textures authored at 1× design resolution, blitted to map
 * 1:1 in the GUI. All text, navigation and overlays are still drawn procedurally on top. Two modes:
 * an {@code INDEX} landing (left page) + chapter icon grid (right page), and a {@code CHAPTER}
 * reader whose body scrolls (wheel + scrollbar) when it overflows the parchment. Navigation is
 * handled by manual click hotspots recomputed each frame.
 */
public final class HandbookScreen extends Screen {
    // ── Book geometry (design space, centred on screen) ──────────────────────
    private static final int W = 340;
    private static final int H = 212;
    private static final int FRAME = 13;
    private static final int PAD = 12;
    private static final int GUTTER = 8;
    private static final int GRID_COLS = 4;
    private static final int CELL = 30;
    private static final int LINE_H = 10;

    // ── Ministry textures (1× design resolution, blitted 1:1) ────────────────
    private static final Identifier TEX_BOOK =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/handbook/book.png");
    private static final Identifier TEX_EMBLEM =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/handbook/emblem.png");

    // ── Palette (Ministry: aubergine cover, sepia ink, plum titles) ──────────
    private static final int COVER_OUTER = 0xFF221328;
    private static final int COVER_HI = 0xFF5C3E66;
    private static final int PAGE = 0xFFEFE7CF;
    private static final int PAGE_SHADE = 0xFFE6DCBE;
    private static final int INK = 0xFF3A2E24;
    private static final int INK_TITLE = 0xFF3E1F47;
    private static final int INK_GREY = 0xFF9A8E74;
    private static final int DIVIDER = 0xFFCDBD97;
    private static final int DIVIDER_SH = 0xFF9C8B66;
    private static final int BAR_TRACK = 0xFF2E1B34;
    private static final int BAR_FILL = 0xFFB08E4A;
    private static final int HOVER = 0x333E1F47;
    private static final int ARROW = 0xFF3E1F47;
    private static final int ARROW_OFF = 0xFFBCB096;

    private enum Mode { INDEX, CHAPTER }

    private GuiScaleHelper.Layout layout;
    private int x;
    private int y;
    private Mode mode = Mode.INDEX;
    private int chapterIndex = -1;
    private int pageIndex = 0;
    private int textScroll = 0;        // first visible body line of the current page
    private int pageMaxScroll = 0;     // max scroll for the current frame's body (0 = no overflow)

    // Click hotspots, recomputed every render frame.
    private final List<int[]> catCells = new ArrayList<>(); // {x0,y0,x1,y1,chapterIndex}
    private @Nullable int[] backRect;
    private @Nullable int[] prevRect;
    private @Nullable int[] nextRect;
    private @Nullable int[] crossRefRect;

    public HandbookScreen() {
        super(Component.translatable("gui.wizards_and_beasts.handbook.title"));
    }

    private List<HandbookChapter> chapters() {
        return HandbookChapterManager.CLIENT_CHAPTERS;
    }

    private @Nullable HandbookChapter currentChapter() {
        List<HandbookChapter> ch = chapters();
        return chapterIndex >= 0 && chapterIndex < ch.size() ? ch.get(chapterIndex) : null;
    }

    private @Nullable HandbookPage currentPage() {
        HandbookChapter chapter = currentChapter();
        if (chapter == null || chapter.pages().isEmpty()) {
            return null;
        }
        return chapter.pages().get(Math.max(0, Math.min(pageIndex, chapter.pages().size() - 1)));
    }

    @Override
    protected void init() {
        layout = GuiScaleHelper.Layout.fit(width, height, W, H);
        x = layout.panelX();
        y = layout.panelY();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(gg);
        catCells.clear();
        backRect = prevRect = nextRect = crossRefRect = null;
        pageMaxScroll = 0;

        // Draw the book in design space; the pose shrinks it to fit small screens.
        // Hotspots stay in design space, so hover/click use design-space mouse.
        layout.applyScale(gg);
        int dmx = (int) Math.round(layout.unmapX(mouseX));
        int dmy = (int) Math.round(layout.unmapY(mouseY));
        drawBook(gg);
        if (mode == Mode.INDEX) {
            renderIndex(gg, dmx, dmy);
        } else {
            renderChapter(gg, dmx, dmy);
        }
        gg.pose().popMatrix();
        super.render(gg, mouseX, mouseY, partialTick);
    }

    /** The Ministry book background: aubergine cover, gold pinstripe, parchment pages, plum spine. */
    private void drawBook(GuiGraphics gg) {
        gg.blit(RenderPipelines.GUI_TEXTURED, TEX_BOOK, x, y, 0.0F, 0.0F, W, H, W, H, W, H);
    }

    private void renderIndex(GuiGraphics gg, int mouseX, int mouseY) {
        int centerX = x + W / 2;
        // ── Left page: landing ──
        int lx0 = x + FRAME + PAD;
        int lx1 = centerX - GUTTER - PAD;
        int lcx = (lx0 + lx1) / 2;
        int ly = y + FRAME + 6;

        // Ministry "M" emblem crest, centred above the title.
        int em = 26;
        // Scale the whole 72×72 crest down into em×em (12-arg blit). The 10-arg overload would sample
        // only a 26×26 corner of the source, cropping the emblem to its top-left quarter.
        gg.blit(RenderPipelines.GUI_TEXTURED, TEX_EMBLEM, lcx - em / 2, ly, 0.0F, 0.0F, em, em, 72, 72, 72, 72);
        ly += em + 4;

        ly += titleBox(gg, lcx, lx0, lx1, ly, Component.translatable("gui.wizards_and_beasts.handbook.title")) + 12;
        ly = body(gg, Component.translatable("gui.wizards_and_beasts.handbook.landing.body"), lx0, ly, lx1 - lx0);
        ly += 6;
        body(gg, Component.literal("§o").append(Component.translatable("gui.wizards_and_beasts.handbook.landing.flavor")),
                lx0, ly, lx1 - lx0, INK_GREY);

        // progress bar pinned to the bottom of the left page; the caption wraps upwards so a long
        // translation grows into the page instead of bleeding over the spine.
        int total = chapters().size();
        Component unlocked = Component.translatable("gui.wizards_and_beasts.handbook.unlocked", total, total);
        List<FormattedCharSequence> unlockedLines = font.split(unlocked, lx1 - lx0);
        int barY = y + H - FRAME - 24 - (unlockedLines.size() - 1) * LINE_H;
        divider(gg, lx0, lx1, barY - 6);
        int cy = barY;
        for (FormattedCharSequence line : unlockedLines) {
            ink(gg, line, lx0, cy, INK);
            cy += LINE_H;
        }
        progressBar(gg, lx0, y + H - FRAME - 13, lx1 - lx0, total == 0 ? 0f : 1f);

        // ── Right page: category icon grid ──
        int rx0 = centerX + GUTTER + PAD;
        int rx1 = x + W - FRAME - PAD;
        int rcx = (rx0 + rx1) / 2;
        int ry = y + FRAME + 12;
        header(gg, rcx, rx0, rx1, ry, Component.translatable("gui.wizards_and_beasts.handbook.categories"));
        ry += 20;

        List<HandbookChapter> ch = chapters();
        if (ch.isEmpty()) {
            body(gg, Component.translatable("gui.wizards_and_beasts.handbook.empty"), rx0, ry + 8, rx1 - rx0, INK_GREY);
            return;
        }
        int gridW = GRID_COLS * CELL;
        int gx = rcx - gridW / 2;
        @Nullable HandbookChapter hovered = null;
        for (int i = 0; i < ch.size(); i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int cx0 = gx + col * CELL;
            int cy0 = ry + row * CELL;
            boolean hot = mouseX >= cx0 && mouseX < cx0 + CELL && mouseY >= cy0 && mouseY < cy0 + CELL;
            if (hot) {
                gg.fill(cx0 + 2, cy0 + 1, cx0 + CELL - 2, cy0 + CELL - 3, HOVER);
                hovered = ch.get(i);
            }
            gg.renderItem(iconStack(ch.get(i)), cx0 + (CELL - 16) / 2, cy0 + 4);
            catCells.add(new int[]{cx0, cy0, cx0 + CELL, cy0 + CELL, i});
        }
        if (hovered != null) {
            gg.setTooltipForNextFrame(font, Component.literal(hovered.title()), mouseX, mouseY);
        }
    }

    private void renderChapter(GuiGraphics gg, int mouseX, int mouseY) {
        HandbookChapter chapter = currentChapter();
        if (chapter == null) {
            mode = Mode.INDEX;
            return;
        }
        HandbookPage page = currentPage();
        int centerX = x + W / 2;
        int lx0 = x + FRAME + PAD;
        int lx1 = centerX - GUTTER - PAD;
        int lcx = (lx0 + lx1) / 2;
        int ly = y + FRAME + 14;

        ly += titleBox(gg, lcx, lx0, lx1, ly, Component.literal(chapter.title())) + 12;

        // ── Left page body (confined to the parchment, scrolls on overflow) ──
        int contentBottom = y + H - FRAME - 22;
        int sbX = lx1 + 4;
        if (page instanceof HandbookPage.Text text) {
            if (text.heading().isPresent()) {
                ly += heading(gg, Component.literal(text.heading().get()), lx0, ly, lx1 - lx0);
            }
            scrollableBody(gg, Component.literal(text.body()), lx0, ly, lx1 - lx0, contentBottom, sbX, INK);
        } else if (page instanceof HandbookPage.CrossRef ref) {
            scrollableBody(gg, Component.literal("§o" + ref.entryId().getPath()), lx0, ly, lx1 - lx0, contentBottom, sbX, INK_GREY);
        } else if (page instanceof HandbookPage.Recipe || page instanceof HandbookPage.Image) {
            body(gg, Component.translatable("gui.wizards_and_beasts.handbook.see_facing"), lx0, ly, lx1 - lx0, INK_GREY);
        }

        // ── Right page context ──
        int rx0 = centerX + GUTTER + PAD;
        int rx1 = x + W - FRAME - PAD;
        int ry = y + FRAME + 14;
        if (page instanceof HandbookPage.Recipe recipe) {
            renderRecipe(gg, recipe, rx0, ry, rx1 - rx0);
        } else if (page instanceof HandbookPage.Image image) {
            renderImage(gg, image, rx0, ry, rx1 - rx0);
        } else if (page instanceof HandbookPage.CrossRef ref && "bestiary".equals(ref.targetType())) {
            crossRefRect = banner(gg, rx0, ry + 20, rx1 - rx0, mouseX, mouseY,
                    Component.translatable("gui.wizards_and_beasts.handbook.see_bestiary").copy().append(" →"));
        } else {
            // plain text page: decorative facing — large chapter icon
            gg.renderItem(iconStack(chapter), (rx0 + rx1) / 2 - 8, ry + 24);
        }

        // ── Navigation ──
        int total = chapter.pages().size();
        int navY = y + H - FRAME - 16;
        backRect = textHotspot(gg, lx0, navY, "‹ " + asString("gui.wizards_and_beasts.handbook.index"), mouseX, mouseY);

        boolean hasPrev = pageIndex > 0;
        boolean hasNext = pageIndex < total - 1;
        prevRect = arrow(gg, lx1 - 12, navY - 1, "◀", hasPrev, mouseX, mouseY);
        nextRect = arrow(gg, rx1 - 4, navY - 1, "▶", hasNext, mouseX, mouseY);
        if (!hasPrev) prevRect = null;
        if (!hasNext) nextRect = null;

        Component indicator = Component.translatable("gui.wizards_and_beasts.handbook.page_indicator", pageIndex + 1, total);
        ink(gg, indicator, (rx0 + rx1) / 2 - font.width(indicator) / 2, navY, INK_GREY);
    }

    // ── Text drawing ─────────────────────────────────────────────────────────
    // Every glyph in the book sits on parchment. Vanilla's drop shadow is drawn one design pixel
    // down-right of the glyph, which at GUI scale 3–4 reads as a second, offset copy of the text
    // ("double render") rather than as depth, so all page text is drawn flat.

    private void ink(GuiGraphics gg, Component text, int x0, int yy, int color) {
        gg.drawString(font, text, x0, yy, color, false);
    }

    private void ink(GuiGraphics gg, FormattedCharSequence line, int x0, int yy, int color) {
        gg.drawString(font, line, x0, yy, color, false);
    }

    private void ink(GuiGraphics gg, String text, int x0, int yy, int color) {
        gg.drawString(font, text, x0, yy, color, false);
    }

    /**
     * A page sub-heading. Wraps to the parchment column: headings are free-form datapack strings
     * ("This Document Is Ministry Property" is wider than the half-page column), and drawn unwrapped
     * they ran straight over the spine onto the facing page. Returns the height consumed.
     */
    private int heading(GuiGraphics gg, Component text, int x0, int yy, int wrap) {
        List<FormattedCharSequence> lines = font.split(text, wrap);
        int drawn = yy;
        for (FormattedCharSequence line : lines) {
            ink(gg, line, x0, drawn, INK_TITLE);
            drawn += LINE_H;
        }
        return lines.size() * LINE_H + 3;
    }

    // ── Page-element helpers ──────────────────────────────────────────────────

    private ItemStack iconStack(HandbookChapter chapter) {
        Item icon = chapter.icon().map(BuiltInRegistries.ITEM::getValue).orElse(Items.BOOK);
        return new ItemStack(icon == Items.AIR ? Items.BOOK : icon);
    }

    /**
     * The plate at the head of a page. Titles wrap rather than being cut: every chapter is named
     * "Part N — Something Or Other", which is far wider than the half-page plate, and the old
     * single-line clip chopped them mid-word with nothing to show that it had ({@code plainSubstrByWidth}
     * adds no ellipsis). Returns the plate height so callers can place what follows.
     */
    private int titleBox(GuiGraphics gg, int cx, int x0, int x1, int yy, Component text) {
        List<FormattedCharSequence> lines = font.split(text, x1 - x0 - 22);
        int h = Math.max(18, 8 + lines.size() * LINE_H);
        gg.fill(x0 + 6, yy, x1 - 6, yy + h, PAGE_SHADE);
        gg.fill(x0 + 6, yy, x1 - 6, yy + 1, DIVIDER);
        gg.fill(x0 + 6, yy + h - 1, x1 - 6, yy + h, DIVIDER);
        // bracket caps
        gg.fill(x0 + 6, yy - 1, x0 + 8, yy + h + 1, DIVIDER_SH);
        gg.fill(x1 - 8, yy - 1, x1 - 6, yy + h + 1, DIVIDER_SH);
        int ty = yy + (h - lines.size() * LINE_H) / 2 + 1;
        for (FormattedCharSequence line : lines) {
            ink(gg, line, cx - font.width(line) / 2, ty, INK_TITLE);
            ty += LINE_H;
        }
        return h;
    }

    private void header(GuiGraphics gg, int cx, int x0, int x1, int yy, Component text) {
        ink(gg, text, cx - font.width(text) / 2, yy, INK_TITLE);
        int lineY = yy + 12;
        int half = font.width(text) / 2;
        divider(gg, x0, cx - half - 6, lineY);
        divider(gg, cx + half + 6, x1, lineY);
    }

    private void divider(GuiGraphics gg, int x0, int x1, int yy) {
        if (x1 - x0 < 4) {
            return;
        }
        gg.fill(x0, yy, x1, yy + 1, DIVIDER);
        gg.fill(x0, yy, x0 + 8, yy + 2, DIVIDER_SH);
        gg.fill(x1 - 8, yy, x1, yy + 2, DIVIDER_SH);
    }

    private void progressBar(GuiGraphics gg, int x0, int yy, int w, float frac) {
        gg.fill(x0, yy, x0 + w, yy + 7, BAR_TRACK);
        gg.fill(x0, yy, x0 + 1, yy + 7, COVER_OUTER);
        int fill = Math.round((w - 2) * Math.max(0f, Math.min(1f, frac)));
        if (fill > 0) {
            gg.fill(x0 + 1, yy + 1, x0 + 1 + fill, yy + 6, BAR_FILL);
        }
    }

    private int body(GuiGraphics gg, Component text, int x0, int y0, int wrapWidth) {
        return body(gg, text, x0, y0, wrapWidth, INK);
    }

    private int body(GuiGraphics gg, Component text, int x0, int y0, int wrapWidth, int color) {
        List<FormattedCharSequence> lines = font.split(text, wrapWidth);
        int yy = y0;
        for (FormattedCharSequence line : lines) {
            if (yy > y + H - FRAME - 30) {
                break;
            }
            ink(gg, line, x0, yy, color);
            yy += LINE_H;
        }
        return yy;
    }

    /**
     * Draws wrapped body text clipped to the parchment window {@code [yTop, yBottom)}. When the
     * text is taller than the window it scrolls by whole lines: a scrollbar is drawn at {@code sbX},
     * a chevron hints there is more below, and {@link #pageMaxScroll} is published so the wheel
     * handler knows the clamp. No partial lines spill onto the cover.
     *
     * <p>An overflowing page gives up its last text line to the chevron: drawn at {@code yBottom}
     * the glyph hung below the window and collided with the navigation row underneath it.
     */
    private void scrollableBody(GuiGraphics gg, Component text, int x0, int yTop, int wrap,
                               int yBottom, int sbX, int color) {
        List<FormattedCharSequence> lines = font.split(text, wrap);
        int windowLines = Math.max(1, (yBottom - yTop) / LINE_H);
        boolean overflows = lines.size() > windowLines;
        int maxLines = overflows ? Math.max(1, windowLines - 1) : windowLines;
        int maxScroll = Math.max(0, lines.size() - maxLines);
        pageMaxScroll = maxScroll;
        textScroll = Math.max(0, Math.min(textScroll, maxScroll));

        int yy = yTop;
        for (int i = textScroll; i < Math.min(lines.size(), textScroll + maxLines); i++) {
            ink(gg, lines.get(i), x0, yy, color);
            yy += LINE_H;
        }
        if (maxScroll > 0) {
            drawScrollbar(gg, sbX, yTop, maxLines * LINE_H, maxLines, lines.size(), textScroll);
            if (textScroll < maxScroll) {
                // more-below hint, on the reserved line inside the window
                ink(gg, "▾", x0 + wrap / 2 - 3, yTop + maxLines * LINE_H, INK_GREY);
            }
        }
    }

    private void drawScrollbar(GuiGraphics gg, int sx, int sy, int h, int visible, int total, int offset) {
        gg.fill(sx, sy, sx + 3, sy + h, DIVIDER);
        int thumbH = Math.max(8, h * visible / total);
        int travel = h - thumbH;
        int maxOff = Math.max(1, total - visible);
        int ty = sy + (int) ((long) offset * travel / maxOff);
        gg.fill(sx, ty, sx + 3, ty + thumbH, DIVIDER_SH);
        gg.fill(sx, ty, sx + 3, ty + 2, COVER_HI);
    }

    /** Structural crafting-grid render (recipe slots are populated in a later content pass). */
    private void renderRecipe(GuiGraphics gg, HandbookPage.Recipe recipe, int rx, int ry, int w) {
        ink(gg, Component.translatable("gui.wizards_and_beasts.handbook.recipe"), rx, ry, INK_TITLE);
        int gx = rx + 6;
        int gy = ry + 16;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sx = gx + col * 20;
                int sy = gy + row * 20;
                gg.fill(sx, sy, sx + 18, sy + 18, PAGE_SHADE);
                gg.renderOutline(sx, sy, 18, 18, DIVIDER_SH);
            }
        }
        int arrowX = gx + 64;
        int arrowY = gy + 22;
        ink(gg, "→", arrowX, arrowY, INK);
        int resX = arrowX + 14;
        gg.fill(resX, arrowY - 5, resX + 18, arrowY + 13, PAGE_SHADE);
        gg.renderOutline(resX, arrowY - 5, 18, 18, DIVIDER_SH);
        // recipe ids are datapack-supplied and routinely wider than the half page
        int cy = gy + 64;
        for (FormattedCharSequence line : font.split(Component.literal("§8" + recipe.recipeId().getPath()), w)) {
            ink(gg, line, gx, cy, INK_GREY);
            cy += LINE_H;
        }
    }

    private void renderImage(GuiGraphics gg, HandbookPage.Image image, int rx, int ry, int w) {
        int size = Math.min(w, 96);
        gg.blit(RenderPipelines.GUI_TEXTURED, image.texture(), rx, ry, 0.0F, 0.0F, size, size, size, size, size, size);
        image.caption().ifPresent(caption ->
                gg.drawWordWrap(font, Component.literal(caption), rx, ry + size + 4, w, INK_GREY, false));
    }

    /** A pressable banner (used for cross_ref). Returns its bounds for hit-testing. */
    private int[] banner(GuiGraphics gg, int x0, int yy, int w, int mouseX, int mouseY, Component label) {
        List<FormattedCharSequence> lines = font.split(label, w - 8);
        int h = Math.max(22, 10 + lines.size() * LINE_H);
        boolean hot = mouseX >= x0 && mouseX < x0 + w && mouseY >= yy && mouseY < yy + h;
        gg.fill(x0, yy, x0 + w, yy + h, hot ? PAGE_SHADE : PAGE);
        gg.renderOutline(x0, yy, w, h, hot ? COVER_HI : DIVIDER_SH);
        int ty = yy + (h - lines.size() * LINE_H) / 2 + 1;
        for (FormattedCharSequence line : lines) {
            ink(gg, line, x0 + (w - font.width(line)) / 2, ty, hot ? INK_TITLE : INK);
            ty += LINE_H;
        }
        return new int[]{x0, yy, x0 + w, yy + h, 0};
    }

    private int[] arrow(GuiGraphics gg, int x0, int yy, String glyph, boolean active, int mouseX, int mouseY) {
        int w = font.width(glyph) + 6;
        int h = 12;
        boolean hot = active && mouseX >= x0 && mouseX < x0 + w && mouseY >= yy && mouseY < yy + h;
        ink(gg, glyph, x0 + 3, yy + 2, !active ? ARROW_OFF : hot ? COVER_HI : ARROW);
        return new int[]{x0, yy, x0 + w, yy + h, 0};
    }

    private int[] textHotspot(GuiGraphics gg, int x0, int yy, String text, int mouseX, int mouseY) {
        int w = font.width(text);
        boolean hot = mouseX >= x0 && mouseX < x0 + w && mouseY >= yy && mouseY < yy + 10;
        ink(gg, text, x0, yy, hot ? INK_TITLE : INK_GREY);
        return new int[]{x0, yy, x0 + w, yy + 10, 0};
    }

    private String asString(String key) {
        return Component.translatable(key).getString();
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() == 0) {
            int mx = (int) Math.round(layout.unmapX(event.x()));
            int my = (int) Math.round(layout.unmapY(event.y()));
            if (mode == Mode.INDEX) {
                for (int[] cell : catCells) {
                    if (hit(cell, mx, my)) {
                        chapterIndex = cell[4];
                        pageIndex = 0;
                        textScroll = 0;
                        mode = Mode.CHAPTER;
                        return true;
                    }
                }
            } else {
                if (hit(backRect, mx, my)) {
                    mode = Mode.INDEX;
                    textScroll = 0;
                    return true;
                }
                if (hit(prevRect, mx, my)) {
                    pageIndex = Math.max(0, pageIndex - 1);
                    textScroll = 0;
                    return true;
                }
                if (hit(nextRect, mx, my)) {
                    HandbookChapter chapter = currentChapter();
                    if (chapter != null) {
                        pageIndex = Math.min(chapter.pages().size() - 1, pageIndex + 1);
                    }
                    textScroll = 0;
                    return true;
                }
                if (hit(crossRefRect, mx, my)) {
                    ClientScreenHooks.openBestiaryScreen();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mode == Mode.CHAPTER) {
            int delta = -(int) Math.signum(scrollY);
            // Scroll the body first; only flip pages once the body is at its scroll bound.
            if (delta != 0 && pageMaxScroll > 0) {
                int ns = textScroll + delta;
                if (ns >= 0 && ns <= pageMaxScroll) {
                    textScroll = ns;
                    return true;
                }
            }
            HandbookChapter chapter = currentChapter();
            if (chapter != null && delta != 0) {
                int old = pageIndex;
                pageIndex = Math.max(0, Math.min(chapter.pages().size() - 1, pageIndex + delta));
                if (pageIndex != old) {
                    textScroll = 0;
                }
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static boolean hit(@Nullable int[] rect, int mx, int my) {
        return rect != null && mx >= rect[0] && mx < rect[2] && my >= rect[1] && my < rect[3];
    }
}
