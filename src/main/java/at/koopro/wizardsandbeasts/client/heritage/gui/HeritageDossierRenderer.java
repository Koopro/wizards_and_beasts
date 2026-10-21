package at.koopro.wizardsandbeasts.client.heritage.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageTraits;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Drawing for the heritage gate (night sheet) and the Control Center's heritage preview (parchment): the
 * dossier, the trait readout and the confirm card, each in the inks of the surface it is given.
 *
 * <p>Replaces the previous ceremony renderer, which painted a tiled parchment ground, a candlelight
 * glow that tracked the selected row, a wax seal and a staggered ink reveal. All of that is gone on
 * purpose — the screen is now a character creator rather than a certificate, and a flat surface is
 * both faster to read and cheaper to keep consistent.
 *
 * <p>Everything here draws from {@link WizardsPalette}'s page inks and the shared {@code gui/theme/}
 * sprites via {@link McStylePanel}. The old screen was the last one carrying its own parchment colour
 * block (since deleted from {@code WizardsAndBeastsUiTokens}); moving onto the palette <em>is</em> the
 * flattening, and it is why nothing in this file mixes its own browns. (The screen is parchment again
 * now, but the kit's parchment — the same sheet every other screen is written on.)
 */
public final class HeritageDossierRenderer {

    /** Inner padding shared by both panels. */
    private static final int PAD = 8;
    /** Ribbon for a heritage that cannot yet be chosen: a strip of sealing wax, lettered in paper. */
    private static final int LOCKED_RIBBON = WizardsPalette.WAX;
    private static final int LOCKED_RIBBON_EDGE = WizardsPalette.PAGE_BAD;
    private static final int LOCKED_RIBBON_TEXT = WizardsPalette.PAGE_LIGHT;
    /** The dossier's field: the inset is the page one shade down, which PAGE_SHADE stands in for. */
    private static final int DOSSIER_GROUND = WizardsPalette.PAGE_SHADE;
    private HeritageDossierRenderer() {}

    // ── Centre column: the dossier ───────────────────────────────────────────

    /**
     * Heritage name, lineage, signature trait, flavour line and the wrapped lore blurb.
     *
     * <p>The name is tinted with {@link Heritage#getColor()} pushed onto the dossier ground. Several
     * signature colours (Veela, House-Elf, Wizardkind) are near-white and would bloom illegibly on
     * paper, so {@link UiContrast#readableOn} darkens them to body-text contrast (no shadow on paper to
     * help a large-text target) while keeping their hue.
     */
    public static void drawDossier(@NonNull GuiGraphics g, @NonNull Font font,
                                   int x, int y, int w, int h,
                                   @NonNull Heritage heritage, @Nullable HeritageVariant variant,
                                   boolean locked) {
        // A recess in the sheet rather than a second sheet on it: the inset's edge is 3px, so PAD
        // still lands on paper, and there is no double rule to strike through the name.
        McStylePanel.drawThemedInset(g, x, y, w, h);

        int tx = x + PAD;
        int innerW = w - PAD * 2;
        int cursorY = y + PAD;

        // The signature colour is chosen to work as a sigil, not as ink. Pushed onto the dossier
        // ground so the pale ones (Veela, House-Elf, Wizardkind) stop vanishing into the paper.
        g.drawString(font, heritage.getDisplayName(), tx, cursorY,
                UiContrast.readableOn(heritage.getColor(), DOSSIER_GROUND), false);
        cursorY += font.lineHeight + 2;

        if (variant != null) {
            g.drawString(font, variant.getDisplayName(), tx, cursorY, WizardsPalette.PAGE_INK_2, false);
            cursorY += font.lineHeight + 3;
        }

        // The divider sprite is DIVIDER_H tall, not a 1px rule. Advancing by less than its own
        // height printed it straight through the signature line beneath it.
        McStylePanel.drawDivider(g, tx, cursorY, innerW);
        cursorY += WizardsMetrics.DIVIDER_H;

        // Signature trait — the one line that says what this heritage *does*. Wrapped, not drawn
        // flat: it is a translated string composed from a per-heritage key, so its width is not ours
        // to assume, and "Wandcraft & spellcasting" already overran the panel.
        List<FormattedCharSequence> signature = font.split(
                Component.translatable("gui.wizards_and_beasts.heritage.signature_trait",
                        Component.translatable("heritage.wizards_and_beasts." + heritage.getId() + ".trait")),
                innerW);
        for (FormattedCharSequence line : signature) {
            g.drawString(font, line, tx, cursorY, WizardsPalette.PAGE_RUBRIC, false);
            cursorY += font.lineHeight;
        }
        cursorY += 4;

        // Flavour sits in a footer above its own rule, so the space between lore and flavour reads
        // as a deliberate margin rather than as the panel having run out of things to say.
        List<FormattedCharSequence> flavour = font.split(
                Component.translatable("heritage.wizards_and_beasts." + heritage.getId() + ".flavor")
                        .withStyle(style -> style.withItalic(true)), innerW);
        int flavourLines = Math.min(2, flavour.size());
        int footerY = y + h - PAD - font.lineHeight * flavourLines;

        // Lore. Bounded by the space actually left above the footer rather than a fixed line count,
        // so a long blurb truncates instead of running out through the panel floor.
        int maxLines = Math.max(0, (footerY - 6 - cursorY) / font.lineHeight);
        List<FormattedCharSequence> lore = font.split(
                Component.translatable("heritage.wizards_and_beasts." + heritage.getId() + ".lore"), innerW);
        for (int i = 0; i < Math.min(maxLines, lore.size()); i++) {
            g.drawString(font, lore.get(i), tx, cursorY, WizardsPalette.PAGE_INK, false);
            cursorY += font.lineHeight;
        }

        if (flavourLines > 0) {
            McStylePanel.drawDivider(g, tx, footerY - WizardsMetrics.DIVIDER_H - 1, innerW);
            for (int i = 0; i < flavourLines; i++) {
                g.drawString(font, flavour.get(i), tx, footerY, WizardsPalette.PAGE_INK_2, false);
                footerY += font.lineHeight;
            }
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

    // ── Night sheet: the dossier as one scrolling column ─────────────────────

    /**
     * The dossier for the night-sheet gate, flowing down from {@code top} inside {@code area}'s width, in
     * {@code s}'s inks. Unlike {@link #drawDossier} nothing is pinned to the floor: the screen clips and
     * scrolls it, so a long translation is read in full rather than cut. Returns the y below the last line.
     *
     * <p>Order: who (name, lineage, a chip if it cannot be chosen), what it does (signature trait), where it
     * comes from (lore, the lineage's own note), what it begins with (condition), and the closing line.
     *
     * @param refusalKey {@code HeritageRules.refusalKey} for the heritage, or null when it can be chosen
     */
    public static int drawDossierFlow(@NonNull GuiGraphics g, @NonNull Font font, @NonNull UiSurface s,
                                      Ui.@NonNull Rect area, int top,
                                      @NonNull Heritage heritage, @Nullable HeritageVariant variant,
                                      @NonNull Optional<ConditionOrigin> condition, @Nullable String refusalKey) {
        int x = area.x();
        int w = area.w();
        int y = top;

        g.drawString(font, heritage.getDisplayName(), x, y,
                UiContrast.readableOn(heritage.getColor(), s.well()), s.shadow());
        y += font.lineHeight + 2;
        if (variant != null) {
            g.drawString(font, variant.getDisplayName(), x, y, s.ink2(), s.shadow());
        }
        if (refusalKey != null) {
            Component tag = lockChip(refusalKey);
            Ui.chip(g, font, s, x + w - Ui.chipWidth(font, tag), y - 2, tag, Ui.Tone.BAD);
        }
        y += font.lineHeight + 4;
        Ui.hairline(g, s, x, y, w);
        y += 5;

        y = wrapped(g, font, s, Component.translatable("gui.wizards_and_beasts.heritage.signature_trait",
                Component.translatable("heritage.wizards_and_beasts." + heritage.getId() + ".trait")),
                x, y, w, s.heading());
        y += 4;
        y = wrapped(g, font, s, Component.translatable("heritage.wizards_and_beasts." + heritage.getId() + ".lore"),
                x, y, w, s.ink());
        if (variant != null && I18n.exists(variant.getDescriptionTranslationKey())) {
            y += 4;
            y = wrapped(g, font, s, Component.translatable(variant.getDescriptionTranslationKey()), x, y, w, s.ink2());
        }
        if (condition.isPresent()) {
            ConditionOrigin origin = condition.get();
            y += 6;
            y = wrapped(g, font, s, conditionName(origin), x, y, w, s.warn());
            if (I18n.exists(origin.getDescriptionTranslationKey())) {
                y = wrapped(g, font, s, Component.translatable(origin.getDescriptionTranslationKey()), x, y, w, s.ink2());
            }
        }
        y += 6;
        Ui.hairline(g, s, x, y, w);
        y += 5;
        return wrapped(g, font, s, Component.translatable("heritage.wizards_and_beasts." + heritage.getId() + ".flavor")
                .withStyle(style -> style.withItalic(true)), x, y, w, s.ink2());
    }

    /** The short tag for a heritage the gate refuses: "Closed" when an administrator shut it, else "Soon". */
    public static Component lockChip(String refusalKey) {
        return Component.translatable(refusalKey.endsWith(".closed")
                ? "gui.wizards_and_beasts.heritage.closed_chip"
                : "gui.wizards_and_beasts.spell.coming_soon_short");
    }

    /** "Lycanthropy: Bitten". */
    public static Component conditionName(ConditionOrigin origin) {
        return Component.translatable(origin.condition().getTranslationKey()).append(": ")
                .append(Component.translatable(origin.getTranslationKey()));
    }

    private static int wrapped(GuiGraphics g, Font font, UiSurface s, Component text, int x, int y, int w, int colour) {
        for (FormattedCharSequence line : font.split(text, w)) {
            g.drawString(font, line, x, y, colour, s.shadow());
            y += font.lineHeight;
        }
        return y;
    }

    // ── Right column: the trait readout ──────────────────────────────────────

    /** {@link #drawTraits(GuiGraphics, Font, UiSurface, int, int, int, Heritage, HeritageVariant)} on parchment. */
    public static void drawTraits(@NonNull GuiGraphics g, @NonNull Font font,
                                  int x, int y, int w,
                                  @NonNull Heritage heritage, @Nullable HeritageVariant variant) {
        drawTraits(g, font, UiSurface.PAGE, x, y, w, heritage, variant);
    }

    /**
     * What this heritage <em>means</em>: how their magic reaches them, what body they have, and then their
     * traits, their magical affinities and their special characteristics, each by name.
     *
     * <p>This replaced a stat block — POWER band, Health ±, Speed %, Armour ± — which was the readout of a
     * selectable MMO race. Heritage carries no bonus for being itself now, so there are no numbers left to
     * print; what a player needs before committing is who they would be.
     *
     * <p>Reads {@link HeritageTraits} live rather than a cached snapshot, so the panel always agrees with the tags
     * the lineage actually carries. Returns the y below the last line.
     */
    public static int drawTraits(@NonNull GuiGraphics g, @NonNull Font font, @NonNull UiSurface s,
                                 int x, int y, int w,
                                 @NonNull Heritage heritage, @Nullable HeritageVariant variant) {
        int cursorY = y;
        cursorY = row(g, font, s, x, cursorY, w, "gui.wizards_and_beasts.heritage.trait.magic",
                Component.literal(heritage.getMagicSource().getDisplayName()));
        cursorY = row(g, font, s, x, cursorY, w, "gui.wizards_and_beasts.heritage.trait.size",
                Component.literal(title(heritage.getSizeCategory().name())));
        cursorY += 2;
        for (HeritageTraits.Kind kind : HeritageTraits.Kind.values()) {
            cursorY = section(g, font, s, x, cursorY, w, kind, HeritageTraits.of(variant, kind));
        }
        return cursorY;
    }

    /** The height {@link #drawTraits} will use at width {@code w}, for callers that lay out below it. */
    public static int measureTraits(@NonNull Font font, int w, @Nullable HeritageVariant variant) {
        int height = (font.lineHeight + 1) * 2 + 2;
        for (HeritageTraits.Kind kind : HeritageTraits.Kind.values()) {
            List<HeritageTraits.Trait> traits = HeritageTraits.of(variant, kind);
            if (traits.isEmpty()) {
                continue;
            }
            height += font.split(Component.translatable(kind.getHeadingKey()), w).size() * font.lineHeight + 1;
            for (HeritageTraits.Trait trait : traits) {
                height += font.split(Component.translatable(trait.getNameKey()), w - 6).size() * font.lineHeight;
            }
            height += 3;
        }
        return height;
    }

    /**
     * One heading plus its traits by name, wrapped. Nothing is drawn for an empty heading — a lineage with no
     * affinities should read as a lineage with no affinities, not as an empty label.
     */
    private static int section(GuiGraphics g, Font font, UiSurface s, int x, int y, int w,
                               HeritageTraits.Kind kind, List<HeritageTraits.Trait> traits) {
        if (traits.isEmpty()) {
            return y;
        }
        int cursorY = y;
        // Wrapped: in a 92px column "Special characteristics" is wider than the column.
        for (FormattedCharSequence line : font.split(Component.translatable(kind.getHeadingKey()), w)) {
            g.drawString(font, line, x, cursorY, s.heading(), s.shadow());
            cursorY += font.lineHeight;
        }
        cursorY += 1;
        for (HeritageTraits.Trait trait : traits) {
            for (FormattedCharSequence line : font.split(Component.translatable(trait.getNameKey()), w - 6)) {
                g.drawString(font, line, x + 6, cursorY, s.ink(), s.shadow());
                cursorY += font.lineHeight;
            }
        }
        return cursorY + 3;
    }

    /** One label-left / value-right row. Returns the next row's Y. */
    private static int row(GuiGraphics g, Font font, UiSurface s, int x, int y, int w,
                           String labelKey, Component value) {
        Component label = Component.translatable(labelKey);
        g.drawString(font, label, x, y, s.ink2(), s.shadow());
        g.drawString(font, value, x + w - font.width(value), y, s.ink(), s.shadow());
        return y + font.lineHeight + 1;
    }

    // ── Confirm card ─────────────────────────────────────────────────────────

    /**
     * The "this is final" card: what is being sworn (heritage, lineage, any condition), why it matters, and
     * that it cannot be undone -- the last line in the bad ink behind a "!", so it does not rest on colour.
     * The buttons are the screen's widgets, under this text.
     */
    public static void drawConfirm(@NonNull GuiGraphics g, @NonNull Font font, @NonNull UiSurface s,
                                   Ui.@NonNull Rect r, @NonNull Heritage heritage, @NonNull HeritageVariant variant,
                                   @NonNull Optional<ConditionOrigin> condition) {
        Ui.panel(g, s, r.x(), r.y(), r.w(), r.h());
        int cx = r.x() + r.w() / 2;
        int w = r.w() - 28;
        int ty = r.y() + 14;

        centered(g, font, s, Component.translatable("gui.wizards_and_beasts.heritage.confirm_title"), cx, ty, s.heading());
        ty += font.lineHeight + 6;
        centered(g, font, s, Component.literal(UiText.fit(font,
                        heritage.getDisplayName() + " — " + variant.getDisplayName(), w)),
                cx, ty, UiContrast.readableOn(heritage.getColor(), s.field() | 0xFF000000));
        ty += font.lineHeight + 2;
        if (condition.isPresent()) {
            Component c = Component.translatable("gui.wizards_and_beasts.heritage.confirm_condition",
                    conditionName(condition.get()));
            centered(g, font, s, Component.literal(UiText.fit(font, c.getString(), w)), cx, ty, s.warn());
            ty += font.lineHeight + 2;
        }
        ty += 4;
        for (FormattedCharSequence line : font.split(
                Component.translatable("gui.wizards_and_beasts.heritage.confirm_body"), w)) {
            g.drawString(font, line, cx - font.width(line) / 2, ty, s.ink2(), s.shadow());
            ty += font.lineHeight;
        }
        ty += 4;
        centered(g, font, s, Component.literal("! ").append(
                Component.translatable("gui.wizards_and_beasts.heritage.confirm_final")), cx, ty, s.bad());
    }

    // ── Primitives ───────────────────────────────────────────────────────────

    private static void centered(GuiGraphics g, Font font, UiSurface s, Component text, int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, s.shadow());
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static String title(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(Locale.ROOT);
    }
}
