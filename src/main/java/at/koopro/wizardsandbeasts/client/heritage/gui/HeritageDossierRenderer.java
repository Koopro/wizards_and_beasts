package at.koopro.wizardsandbeasts.client.heritage.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.stats.PowerBandTable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Flat drawing for the Heritage selection screen: the centre dossier, the right-hand trait readout
 * and the confirm overlay.
 *
 * <p>Replaces the previous ceremony renderer, which painted a tiled parchment ground, a candlelight
 * glow that tracked the selected row, a wax seal and a staggered ink reveal. All of that is gone on
 * purpose — the screen is now a character creator rather than a certificate, and a flat surface is
 * both faster to read and cheaper to keep consistent.
 *
 * <p>Everything here draws from {@link WizardsPalette} and the shared {@code gui/theme/} sprites via
 * {@link McStylePanel}. The old screen was the last one carrying its own parchment colour block in
 * {@code WizardsAndBeastsUiTokens.HeritageSelection}; moving onto the palette <em>is</em> the
 * flattening, and it is why nothing in this file mixes its own browns.
 */
public final class HeritageDossierRenderer {

    /** Inner padding shared by both panels. */
    private static final int PAD = 8;
    /** Ribbon ground for a heritage that cannot yet be chosen. */
    private static final int LOCKED_RIBBON = 0xCC7A1E1E;
    private static final int LOCKED_RIBBON_EDGE = 0xFFB85050;
    private static final int LOCKED_RIBBON_TEXT = 0xFFF2D7D7;
    private static final int OVERLAY_DIM = 0xCC0A0603;

    private HeritageDossierRenderer() {}

    // ── Centre column: the dossier ───────────────────────────────────────────

    /**
     * Heritage name, lineage, signature trait, flavour line and the wrapped lore blurb.
     *
     * <p>The name is tinted with {@link Heritage#getColor()} lifted onto the panel ground. Several
     * signature colours (Veela, House-Elf, Wizardkind) are near-white and would bloom illegibly
     * against a light surface, but this panel is dark, so the same value that reads as a washout on
     * parchment reads correctly here — the tint is used raw and only forced opaque.
     */
    public static void drawDossier(@NonNull GuiGraphics g, @NonNull Font font,
                                   int x, int y, int w, int h,
                                   @NonNull Heritage heritage, @Nullable HeritageVariant variant,
                                   boolean locked) {
        McStylePanel.drawThemedPanel(g, x, y, w, h);

        int tx = x + PAD;
        int innerW = w - PAD * 2;
        int cursorY = y + PAD;

        g.drawString(font, heritage.getDisplayName(), tx, cursorY,
                heritage.getColor() | 0xFF000000, false);
        cursorY += font.lineHeight + 2;

        if (variant != null) {
            g.drawString(font, variant.getDisplayName(), tx, cursorY, WizardsPalette.TEXT_DIM, false);
            cursorY += font.lineHeight + 3;
        }

        McStylePanel.drawDivider(g, tx, cursorY, innerW);
        cursorY += 5;

        // Signature trait — the one line that says what this heritage *does*.
        g.drawString(font, Component.translatable("gui.wizards_and_beasts.heritage.signature_trait",
                        Component.translatable("heritage.wizards_and_beasts." + heritage.getId() + ".trait")),
                tx, cursorY, WizardsPalette.BRASS, false);
        cursorY += font.lineHeight + 4;

        // Lore. Bounded by the space actually left above the flavour line rather than a fixed line
        // count, so a long blurb truncates instead of running out through the panel floor.
        int flavourH = font.lineHeight * 2 + 4;
        int loreBudget = (y + h - PAD - flavourH) - cursorY;
        int maxLines = Math.max(0, loreBudget / font.lineHeight);
        List<FormattedCharSequence> lore = font.split(
                Component.translatable("heritage.wizards_and_beasts." + heritage.getId() + ".lore"), innerW);
        for (int i = 0; i < Math.min(maxLines, lore.size()); i++) {
            g.drawString(font, lore.get(i), tx, cursorY, WizardsPalette.TEXT, false);
            cursorY += font.lineHeight;
        }

        // Flavour line, pinned to the panel floor so it does not float mid-panel for short lore.
        List<FormattedCharSequence> flavour = font.split(
                Component.translatable("heritage.wizards_and_beasts." + heritage.getId() + ".flavor")
                        .withStyle(style -> style.withItalic(true)), innerW);
        int flavourY = y + h - PAD - font.lineHeight * Math.min(2, flavour.size());
        for (int i = 0; i < Math.min(2, flavour.size()); i++) {
            g.drawString(font, flavour.get(i), tx, flavourY, WizardsPalette.TEXT_DIM, false);
            flavourY += font.lineHeight;
        }

        if (locked) {
            drawComingSoonRibbon(g, font, x, y, w);
        }
    }

    /** Corner ribbon marking a heritage that can be browsed but not committed. */
    private static void drawComingSoonRibbon(GuiGraphics g, Font font, int x, int y, int w) {
        Component label = Component.translatable("gui.wizards_and_beasts.heritage.coming_soon");
        int lw = font.width(label);
        int rx = x + w - lw - PAD - 4;
        int ry = y + PAD - 2;
        g.fill(rx - 4, ry - 2, rx + lw + 4, ry + font.lineHeight + 2, LOCKED_RIBBON);
        outline(g, rx - 4, ry - 2, lw + 8, font.lineHeight + 4, LOCKED_RIBBON_EDGE);
        g.drawString(font, label, rx, ry, LOCKED_RIBBON_TEXT, false);
    }

    // ── Right column: the trait readout ──────────────────────────────────────

    /**
     * The stat block under the player preview: what this heritage grants, as label/value rows.
     *
     * <p>Reads the live getters rather than a cached snapshot, so the block always agrees with what
     * {@code HeritageAPI.applyStats} will actually apply on commit.
     */
    public static void drawTraits(@NonNull GuiGraphics g, @NonNull Font font,
                                  int x, int y, int w,
                                  @NonNull Heritage heritage, @Nullable HeritageVariant variant) {
        int cursorY = y;

        cursorY = row(g, font, x, cursorY, w, "gui.wizards_and_beasts.heritage.trait.wand",
                Component.translatable(heritage.canUseWand()
                        ? "gui.wizards_and_beasts.heritage.trait.yes"
                        : "gui.wizards_and_beasts.heritage.trait.no"),
                heritage.canUseWand() ? WizardsPalette.TEXT : WizardsPalette.TEXT_DIM);

        cursorY = row(g, font, x, cursorY, w, "gui.wizards_and_beasts.heritage.trait.magic",
                Component.literal(heritage.getMagicSource().getDisplayName()), WizardsPalette.TEXT);

        cursorY = row(g, font, x, cursorY, w, "gui.wizards_and_beasts.heritage.trait.size",
                Component.literal(title(heritage.getSizeCategory().name())), WizardsPalette.TEXT);

        if (variant != null) {
            cursorY = row(g, font, x, cursorY, w, "gui.wizards_and_beasts.heritage.trait.power",
                    Component.literal(PowerBandTable.getBandMin(variant) + "–" + PowerBandTable.getBandMax(variant)),
                    WizardsPalette.BRASS);
            cursorY = row(g, font, x, cursorY, w, "gui.wizards_and_beasts.heritage.trait.health",
                    Component.literal(signed(variant.getTotalHealth())), deltaColor(variant.getTotalHealth()));
            cursorY = row(g, font, x, cursorY, w, "gui.wizards_and_beasts.heritage.trait.speed",
                    Component.literal(signedPercent(variant.getTotalSpeed())), deltaColor(variant.getTotalSpeed()));
            row(g, font, x, cursorY, w, "gui.wizards_and_beasts.heritage.trait.armour",
                    Component.literal(signed(variant.getTotalArmor())), deltaColor(variant.getTotalArmor()));
        }
    }

    /** One label-left / value-right row. Returns the next row's Y. */
    private static int row(GuiGraphics g, Font font, int x, int y, int w,
                           String labelKey, Component value, int valueColor) {
        Component label = Component.translatable(labelKey);
        g.drawString(font, label, x, y, WizardsPalette.TEXT_DIM, false);
        int vw = font.width(value);
        g.drawString(font, value, x + w - vw, y, valueColor, false);
        return y + font.lineHeight + 1;
    }

    // ── Confirm overlay ──────────────────────────────────────────────────────

    /** Dim ground plus a panel making the permanence explicit. Buttons are screen widgets. */
    public static void drawConfirmOverlay(@NonNull GuiGraphics g, @NonNull Font font,
                                          int screenW, int screenH,
                                          @NonNull Heritage heritage, @NonNull HeritageVariant variant,
                                          int panelX, int panelY, int panelW, int panelH) {
        g.fill(0, 0, screenW, screenH, OVERLAY_DIM);
        McStylePanel.drawThemedPanel(g, panelX, panelY, panelW, panelH);

        int cx = panelX + panelW / 2;
        int ty = panelY + 12;

        centered(g, font, Component.translatable("gui.wizards_and_beasts.heritage.confirm_title"),
                cx, ty, WizardsPalette.BRASS_HI);
        ty += font.lineHeight + 6;

        centered(g, font, Component.literal(heritage.getDisplayName() + " — " + variant.getDisplayName()),
                cx, ty, heritage.getColor() | 0xFF000000);
        ty += font.lineHeight + 8;

        for (FormattedCharSequence line : font.split(
                Component.translatable("gui.wizards_and_beasts.heritage.confirm_body"), panelW - 24)) {
            g.drawString(font, line, cx - font.width(line) / 2, ty, WizardsPalette.TEXT, false);
            ty += font.lineHeight;
        }
        ty += 4;
        centered(g, font, Component.translatable("gui.wizards_and_beasts.heritage.confirm_final"),
                cx, ty, LOCKED_RIBBON_EDGE);
    }

    // ── Primitives ───────────────────────────────────────────────────────────

    private static void centered(GuiGraphics g, Font font, Component text, int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Health and armour are flat point deltas; a whole number reads better than "4.0". */
    private static String signed(double value) {
        long rounded = Math.round(value);
        return (rounded > 0 ? "+" : "") + rounded;
    }

    /** Speed is a small fraction, so it is shown as a percentage rather than "0.01". */
    private static String signedPercent(double value) {
        long pct = Math.round(value * 100.0);
        return (pct > 0 ? "+" : "") + pct + "%";
    }

    private static int deltaColor(double value) {
        if (value > 0) return WizardsPalette.BRASS;
        if (value < 0) return LOCKED_RIBBON_EDGE;
        return WizardsPalette.TEXT_DIM;
    }

    private static String title(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(Locale.ROOT);
    }
}
