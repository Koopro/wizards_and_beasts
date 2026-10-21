package at.koopro.wizardsandbeasts.client.gui.ui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The shared components, drawn on a {@link UiSurface}.
 *
 * <p>Static draw calls rather than widgets: most of what a screen shows is read, not clicked, and a
 * widget per stat row is ceremony. Interactive controls live beside this in {@code gui.widget}
 * ({@code ThemedButton}, {@code ThemedToggle}, {@code ThemedCheckbox}, {@code ThemedDropdown},
 * {@code ThemedSlider}, {@code ThemedTextField}, {@code CyclerWidget}, {@code ScrollList}) and in
 * {@link ConfirmDialog}. Every call here returns the y below what it drew, so a column is a chain of
 * calls and never a column of hand-added offsets. See {@code documentation/GUI_COMPONENTS.md}.
 *
 * <p>Corners: paper panels carry their deckle and studs in the sprite; procedural pieces (bars, chips,
 * cards) cut their corner texel, which at GUI scale reads as the same soft corner. Nothing here is
 * rounded more than one texel and nothing is drawn without its 1px edge.
 */
@NullMarked
public final class Ui {

    private Ui() {}

    /** A box on screen. */
    public record Rect(int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public Rect inset(int by) {
            return new Rect(x + by, y + by, Math.max(0, w - 2 * by), Math.max(0, h - 2 * by));
        }

        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    // ── surfaces ──────────────────────────────────────────────────────────────

    /** A raised panel of the surface's material: the parchment sheet, a skin's sheet, or the night card. */
    public static void panel(GuiGraphics g, UiSurface s, int x, int y, int w, int h) {
        if (s.skin() != null) {
            McStylePanel.drawSkinPanel(g, s.skin(), x, y, w, h);
        } else if (s.dark()) {
            nightCard(g, x, y, w, h);
        } else {
            McStylePanel.drawThemedPanel(g, x, y, w, h);
        }
    }

    /** A recessed well on the surface: lists, viewports, previews. */
    public static void inset(GuiGraphics g, UiSurface s, int x, int y, int w, int h) {
        if (s.skin() != null) {
            McStylePanel.drawSkinInset(g, s.skin(), x, y, w, h);
        } else if (s.dark()) {
            g.fill(x, y, x + w, y + h, s.well());
            // Inverted bevel: shadow top-left, a faint light bottom-right.
            g.fill(x, y, x + w, y + 1, 0xFF000000);
            g.fill(x, y, x + 1, y + h, 0xFF000000);
            g.fill(x, y + h - 1, x + w, y + h, 0x30FFFFFF);
            g.fill(x + w - 1, y, x + w, y + h, 0x30FFFFFF);
        } else {
            McStylePanel.drawThemedInset(g, x, y, w, h);
        }
    }

    /**
     * The dark magical card: soot-violet field, a gilt hairline lit top-left, a gilt stud at each corner.
     * Drawn procedurally to match the {@code night} tooltip sprites texel for texel.
     */
    public static void nightCard(GuiGraphics g, int x, int y, int w, int h) {
        int x2 = x + w;
        int y2 = y + h;
        g.fill(x + 1, y, x2 - 1, y2, UiSurface.NIGHT_FIELD);
        g.fill(x, y + 1, x + 1, y2 - 1, UiSurface.NIGHT_FIELD);
        g.fill(x2 - 1, y + 1, x2, y2 - 1, UiSurface.NIGHT_FIELD);
        int lit = WizardsPalette.GILT;
        int shade = WizardsPalette.GILT_DARK;
        g.fill(x + 2, y + 1, x2 - 2, y + 2, lit);
        g.fill(x + 1, y + 2, x + 2, y2 - 2, lit);
        g.fill(x + 2, y2 - 2, x2 - 2, y2 - 1, shade);
        g.fill(x2 - 2, y + 2, x2 - 1, y2 - 2, shade);
        int stud = WizardsPalette.GILT_LIGHT;
        g.fill(x + 1, y + 1, x + 2, y + 2, stud);
        g.fill(x2 - 2, y + 1, x2 - 1, y + 2, stud);
        g.fill(x + 1, y2 - 2, x + 2, y2 - 1, stud);
        g.fill(x2 - 2, y2 - 2, x2 - 1, y2 - 1, stud);
    }

    /** A full-width rule: the 8px divider sprite on paper, a gilt hairline at night. Returns the y below it. */
    public static int divider(GuiGraphics g, UiSurface s, int x, int y, int w) {
        if (s.skin() != null) {
            McStylePanel.drawSkinDivider(g, s.skin(), x, y, w);
        } else if (s.dark()) {
            g.fill(x, y + 3, x + w, y + 4, WizardsPalette.GILT_DARK);
            g.fill(x, y + 4, x + w, y + 5, 0x60000000);
        } else {
            McStylePanel.drawDivider(g, x, y, w);
        }
        return y + WizardsMetrics.DIVIDER_H;
    }

    /** A one-texel rule in the surface's edge colour, for separating rows. */
    public static void hairline(GuiGraphics g, UiSurface s, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, (0x80 << 24) | (s.edge() & 0xFFFFFF));
    }

    /** Dims everything behind a modal. Night-tinted, never pure black. */
    public static void scrim(GuiGraphics g, int screenW, int screenH) {
        g.fill(0, 0, screenW, screenH, 0xB0000000 | (WizardsPalette.MINISTRY_DARK & 0xFFFFFF));
    }

    // ── text blocks ───────────────────────────────────────────────────────────

    /** The screen's title, left-aligned, with the divider under it. Returns the y where content starts. */
    public static int title(GuiGraphics g, Font font, UiSurface s, Component title, int x, int y, int w,
                            @Nullable Component trailing) {
        UiText.draw(g, font, s, UiText.Role.TITLE, title, x, y);
        if (trailing != null) {
            UiText.drawRight(g, font, s, trailing, x + w, y, s.ink2());
        }
        return divider(g, s, x, y + font.lineHeight + 1, w) + WizardsMetrics.SPACE_XS;
    }

    /**
     * A section heading in the surface's heading colour, an optional caption under it, then a hairline.
     * Returns the y where the section's content starts.
     */
    public static int sectionHeader(GuiGraphics g, Font font, UiSurface s, int x, int y, int w,
                                    Component heading, @Nullable Component caption) {
        UiText.drawFit(g, font, s, heading, x, y, w, s.heading());
        int ry = y + font.lineHeight + 1;
        if (caption != null) {
            UiText.drawFit(g, font, s, caption, x, ry, w, s.ink3());
            ry += WizardsMetrics.LINE_TIGHT;
        }
        hairline(g, s, x, ry, w);
        return ry + WizardsMetrics.SPACE_S;
    }

    /**
     * A name/value row: the label in the label ink, shortened with an ellipsis if it would meet the value;
     * the value right-aligned in its own colour. Returns the y of the next row.
     */
    public static int statRow(GuiGraphics g, Font font, UiSurface s, int x, int y, int w,
                              Component label, Component value, int valueColour) {
        int valueW = font.width(value);
        UiText.drawFit(g, font, s, label, x, y, Math.max(0, w - valueW - WizardsMetrics.SPACE_M), s.ink2());
        UiText.drawRight(g, font, s, value, x + w, y, valueColour);
        return y + WizardsMetrics.LINE_BODY;
    }

    /** {@link #statRow} with the surface's body ink for the value. */
    public static int statRow(GuiGraphics g, Font font, UiSurface s, int x, int y, int w,
                              Component label, Component value) {
        return statRow(g, font, s, x, y, w, label, value, s.ink());
    }

    // ── bars ──────────────────────────────────────────────────────────────────

    /** Default bar height; a bar under a label. */
    public static final int BAR_H = 5;

    /**
     * A progress bar: a recessed track, the fill with a lit top row, and an optional marker (a threshold,
     * a target) as a 1px tick that stands a texel proud of the track.
     *
     * @param fraction  0..1, clamped
     * @param marker    0..1 for a tick, or a negative number for none
     */
    public static void bar(GuiGraphics g, UiSurface s, int x, int y, int w, int h,
                           float fraction, int fill, float marker) {
        int x2 = x + w;
        int y2 = y + h;
        g.fill(x, y, x2, y2, s.dark() ? s.well() : WizardsPalette.PAGE_DEEP);
        g.fill(x + 1, y + 1, x2 - 1, y2 - 1, s.dark() ? 0xFF0C0A0F : WizardsPalette.PAGE_SHADE);
        float f = Math.max(0.0f, Math.min(1.0f, fraction));
        int fw = Math.round((w - 2) * f);
        if (fw > 0) {
            g.fill(x + 1, y + 1, x + 1 + fw, y2 - 1, fill);
            g.fill(x + 1, y + 1, x + 1 + fw, y + 2, lighten(fill));
        }
        if (marker >= 0.0f) {
            int mx = x + 1 + Math.round((w - 2) * Math.min(1.0f, marker));
            g.fill(mx, y - 1, mx + 1, y2 + 1, s.ink());
        }
    }

    /**
     * A labelled bar: the label left and the value right on one line, the bar under them. Returns the y
     * below the bar plus a row gap.
     */
    public static int labelledBar(GuiGraphics g, Font font, UiSurface s, int x, int y, int w,
                                  Component label, Component value, float fraction, int fill) {
        statRow(g, font, s, x, y, w, label, value);
        int by = y + font.lineHeight + WizardsMetrics.SPACE_XS;
        bar(g, s, x, by, w, BAR_H, fraction, fill, -1.0f);
        return by + BAR_H + WizardsMetrics.SPACE_M;
    }

    /** The experience bar: vanilla's green, the level as a chip at its left end. Returns the y below it. */
    public static int xpBar(GuiGraphics g, Font font, UiSurface s, int x, int y, int w,
                            Component levelLabel, float progress, Component progressLabel) {
        return labelledBar(g, font, s, x, y, w, levelLabel, progressLabel, progress, XP_GREEN);
    }

    public static final int XP_GREEN = 0xFF5DA233;

    // ── chips and callouts ────────────────────────────────────────────────────

    /** What a chip or callout says about the thing it labels. */
    public enum Tone {
        NEUTRAL, GOOD, BAD, WARN, ACCENT, MUTED;

        int colour(UiSurface s) {
            return switch (this) {
                case NEUTRAL -> s.ink2();
                case GOOD -> s.good();
                case BAD -> s.bad();
                case WARN -> s.warn();
                case ACCENT -> s.accent();
                case MUTED -> s.ink3();
            };
        }
    }

    public static final int CHIP_H = 11;

    /**
     * A small label in a tinted pill: a status ("Coming soon"), a grade, a tag. Returns its width, so a row
     * of chips is laid out by adding widths.
     */
    public static int chip(GuiGraphics g, Font font, UiSurface s, int x, int y, Component text, Tone tone) {
        int colour = tone.colour(s);
        int w = font.width(text) + 2 * WizardsMetrics.SPACE_S;
        int x2 = x + w;
        int y2 = y + CHIP_H;
        // A wash and a half-strength frame: a chip is a label, and a full-strength box read as a button.
        int frame = (0x80 << 24) | (colour & 0xFFFFFF);
        g.fill(x + 1, y, x2 - 1, y2, (0x22 << 24) | (colour & 0xFFFFFF));
        g.fill(x + 1, y, x2 - 1, y + 1, frame);
        g.fill(x + 1, y2 - 1, x2 - 1, y2, frame);
        g.fill(x, y + 1, x + 1, y2 - 1, frame);
        g.fill(x2 - 1, y + 1, x2, y2 - 1, frame);
        g.drawString(font, text, x + WizardsMetrics.SPACE_S, y + 2, colour, s.shadow());
        return w;
    }

    /** Width {@link #chip} would take. */
    public static int chipWidth(Font font, Component text) {
        return font.width(text) + 2 * WizardsMetrics.SPACE_S;
    }

    /** Callout kinds: the warning, error and information states. */
    public enum Callout {
        INFO("i", Tone.ACCENT), WARNING("!", Tone.WARN), ERROR("×", Tone.BAD), SUCCESS("✔", Tone.GOOD);

        private final String glyph;
        private final Tone tone;

        Callout(String glyph, Tone tone) {
            this.glyph = glyph;
            this.tone = tone;
        }
    }

    /**
     * A boxed message with a coloured rule down its left edge and a glyph: a warning before a dangerous
     * action, an error from the server, a hint. Returns its height.
     */
    public static int callout(GuiGraphics g, Font font, UiSurface s, int x, int y, int w,
                              Callout kind, Component text) {
        int colour = kind.tone.colour(s);
        int textX = x + WizardsMetrics.SPACE_M + 8;
        int textW = w - (textX - x) - WizardsMetrics.SPACE_S;
        int lines = Math.max(1, UiText.lineCount(font, text, textW));
        int h = lines * WizardsMetrics.LINE_BODY + 2 * WizardsMetrics.SPACE_S - 2;
        g.fill(x, y, x + w, y + h, (0x24 << 24) | (colour & 0xFFFFFF));
        g.fill(x, y, x + 2, y + h, colour);
        g.drawString(font, kind.glyph, x + WizardsMetrics.SPACE_S + 2, y + WizardsMetrics.SPACE_S, colour, s.shadow());
        UiText.drawWrapped(g, font, s, UiText.Role.BODY, text, textX, y + WizardsMetrics.SPACE_S, textW, lines);
        return h;
    }

    /** Height {@link #callout} would take. */
    public static int calloutHeight(Font font, int w, Component text) {
        int textW = w - WizardsMetrics.SPACE_M - 8 - WizardsMetrics.SPACE_S;
        return Math.max(1, UiText.lineCount(font, text, textW)) * WizardsMetrics.LINE_BODY + 2 * WizardsMetrics.SPACE_S - 2;
    }

    // ── states ────────────────────────────────────────────────────────────────

    /**
     * Nothing to show yet: a short title and a hint about how that changes, centred in the area. The
     * hint says what to do ("Meet a creature to begin its page"), never just "Empty".
     */
    public static void emptyState(GuiGraphics g, Font font, UiSurface s, Rect area, Component title,
                                  @Nullable Component hint) {
        stateBlock(g, font, s, area, title, hint, s.ink2());
    }

    /** Something failed: the same block as {@link #emptyState} with the title in the bad ink. */
    public static void errorState(GuiGraphics g, Font font, UiSurface s, Rect area, Component title,
                                  @Nullable Component hint) {
        stateBlock(g, font, s, area, title, hint, s.bad());
    }

    /** Waiting on the server: the text with three dots that fill in turn. */
    public static void loadingState(GuiGraphics g, Font font, UiSurface s, Rect area, Component text) {
        int dots = (int) ((Util.getMillis() / 350L) % 4L);
        String suffix = ".".repeat(dots) + " ".repeat(3 - dots);
        Component shown = Component.empty().append(text).append(suffix);
        int y = area.y() + (area.h() - font.lineHeight) / 2;
        UiText.drawCentered(g, font, s, shown, area.x() + area.w() / 2, y, s.ink2());
    }

    private static void stateBlock(GuiGraphics g, Font font, UiSurface s, Rect area, Component title,
                                   @Nullable Component hint, int titleColour) {
        int textW = Math.min(area.w() - 2 * WizardsMetrics.SPACE_M, 220);
        var titleLines = font.split(title, textW);
        int titleCount = Math.min(2, titleLines.size());
        int hintLines = hint == null ? 0 : Math.min(4, UiText.lineCount(font, hint, textW));
        int blockH = titleCount * WizardsMetrics.LINE_BODY
                + (hintLines > 0 ? WizardsMetrics.SPACE_S + hintLines * WizardsMetrics.LINE_TIGHT : 0);
        int y = area.y() + (area.h() - blockH) / 2;
        int cx = area.x() + area.w() / 2;
        for (int i = 0; i < titleCount; i++) {
            var line = titleLines.get(i);
            g.drawString(font, line, cx - font.width(line) / 2, y + i * WizardsMetrics.LINE_BODY, titleColour, s.shadow());
        }
        if (hint != null) {
            int hy = y + titleCount * WizardsMetrics.LINE_BODY + WizardsMetrics.SPACE_S - 3;
            for (var line : font.split(hint, textW).subList(0, hintLines)) {
                g.drawString(font, line, cx - font.width(line) / 2, hy, s.ink3(), s.shadow());
                hy += WizardsMetrics.LINE_TIGHT;
            }
        }
    }

    // ── tabs ──────────────────────────────────────────────────────────────────

    /** Height of a tab in a tab bar. Clears the 18px nine-slice floor at every Layout scale from 0.72. */
    public static final int TAB_H = 18;

    /**
     * One tab: the button face, in the select tone when active. The label is shortened with an ellipsis,
     * never shrunk. A screen draws its tab row with this and keeps the hit-testing (a tab is a rectangle).
     */
    public static void tab(GuiGraphics g, Font font, UiSurface s, Rect r, Component label,
                           boolean active, boolean hovered, boolean enabled) {
        McStylePanel.ControlState state = McStylePanel.ControlState.of(enabled, hovered && !active);
        McStylePanel.ButtonTone tone = active ? McStylePanel.ButtonTone.SELECT : McStylePanel.ButtonTone.NEUTRAL;
        if (s.skin() != null) {
            McStylePanel.drawSkinButton(g, s.skin(), r.x(), r.y(), r.w(), r.h(), state);
        } else {
            McStylePanel.drawThemedButton(g, r.x(), r.y(), r.w(), r.h(), tone, state);
        }
        int colour = !enabled ? WizardsPalette.PAGE_INK_3 : (s.skin() != null ? s.ink() : WizardsPalette.PAGE_INK);
        String text = UiText.fit(font, label.getString(), r.w() - 2 * WizardsMetrics.SPACE_S);
        g.drawString(font, text, r.x() + (r.w() - font.width(text)) / 2,
                r.y() + (r.h() - font.lineHeight) / 2 + 1, colour, false);
        if (active && s.skin() != null) {
            g.fill(r.x() + 3, r.bottom() - 3, r.right() - 3, r.bottom() - 2, s.accent());
        }
    }

    /** The rectangle of tab {@code i} of {@code count} equal tabs across {@code [x, x + w]} with a gap between. */
    public static Rect tabRect(int x, int y, int w, int count, int i) {
        int gap = WizardsMetrics.SPACE_XS;
        int tw = (w - gap * (count - 1)) / count;
        return new Rect(x + i * (tw + gap), y, tw, TAB_H);
    }

    // ── previews ──────────────────────────────────────────────────────────────

    /**
     * A preview card: a recessed well for a picture, model or item, with a caption band under it.
     * Returns the well's inner rectangle for the caller to draw into.
     */
    public static Rect previewCard(GuiGraphics g, Font font, UiSurface s, Rect r, @Nullable Component caption) {
        int captionH = caption == null ? 0 : WizardsMetrics.LINE_BODY + WizardsMetrics.SPACE_XS;
        inset(g, s, r.x(), r.y(), r.w(), r.h() - captionH);
        if (caption != null) {
            String text = UiText.fit(font, caption.getString(), r.w());
            g.drawString(font, text, r.x() + (r.w() - font.width(text)) / 2,
                    r.bottom() - font.lineHeight, s.ink2(), s.shadow());
        }
        return new Rect(r.x() + 2, r.y() + 2, r.w() - 4, r.h() - captionH - 4);
    }

    /**
     * An entity standing in a preview card, turning to follow the mouse as the inventory's player does.
     * The model is scaled to the well, so a tall form and a small one both fill it.
     */
    public static void entityPreview(GuiGraphics g, Font font, UiSurface s, Rect r, LivingEntity entity,
                                     @Nullable Component caption, int mouseX, int mouseY) {
        Rect well = previewCard(g, font, s, r, caption);
        float height = Math.max(0.5f, entity.getBbHeight());
        int scale = Math.max(8, Math.round((well.h() - 8) / height));
        g.enableScissor(well.x(), well.y(), well.right(), well.bottom());
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, well.x(), well.y(), well.right(), well.bottom(),
                scale, 0.0625f, mouseX, mouseY, entity);
        g.disableScissor();
    }

    private static int lighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 40);
        int gr = Math.min(255, ((argb >> 8) & 0xFF) + 40);
        int b = Math.min(255, (argb & 0xFF) + 40);
        return (argb & 0xFF000000) | r << 16 | gr << 8 | b;
    }
}
