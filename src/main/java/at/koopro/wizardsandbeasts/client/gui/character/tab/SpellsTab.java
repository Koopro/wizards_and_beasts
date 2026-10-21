package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.character.SheetCaches;
import at.koopro.wizardsandbeasts.client.gui.character.widget.SpellCardWidget;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.client.spell.ui.SpellPowerTooltip;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Magic page: how many spells this wizard knows and how well, then every known spell grouped by its family, each
 * card showing the casts it has toward its next tier.
 *
 * <p>Tiers are the server's ({@link Proficiency#fromCastCount} over the synced successful-cast counts — the same call
 * the cast path makes). The spellbook is derived once per spell sync ({@link SheetCaches#spellbook}); it used to be
 * looked up, resolved and sorted every frame.
 */
@NullMarked
public final class SpellsTab extends ScrollingTab {

    private static final UiSurface S = UiSurface.PAGE;
    private static final String KEY = "gui.wizards_and_beasts.character_sheet.magic.";
    private static final int CARD_GAP = 3;

    /** The hover card built for one spell, kept while the pointer stays on it and nothing it says has changed. */
    private SheetCaches.@Nullable KnownSpell tooltipFor;
    private @Nullable List<Component> tooltipLines;

    @Override
    public String translationKey() {
        return "gui.wizards_and_beasts.character_sheet.tab.spells";
    }

    @Override
    public void onShown() {
        resetScroll();
    }

    @Override
    protected int drawContent(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY) {
        SheetCaches.Spellbook book = SheetCaches.spellbook();
        if (book.all().isEmpty()) {
            y = UiText.draw(g, font, S, UiText.Role.BODY, Component.translatable(KEY + "none"), x, y);
            return UiText.drawWrapped(g, font, S, UiText.Role.CAPTION, Component.translatable(KEY + "none.hint"),
                    x, y, w, 6);
        }

        // ── the summary ──
        y = Ui.statRow(g, font, S, x, y, w, Component.translatable(KEY + "known"),
                Component.literal(Integer.toString(book.all().size())));
        int cx = x;
        for (Proficiency tier : new Proficiency[] {Proficiency.MASTERED, Proficiency.PROFICIENT, Proficiency.NOVICE}) {
            Component chip = Component.translatable(KEY + "chip", book.count(tier), tierName(tier));
            if (cx > x && cx + Ui.chipWidth(font, chip) > x + w) {
                cx = x;
                y += Ui.CHIP_H + WizardsMetrics.SPACE_XS;
            }
            cx += Ui.chip(g, font, S, cx, y, chip, tierTone(tier)) + WizardsMetrics.SPACE_XS;
        }
        y += Ui.CHIP_H + WizardsMetrics.SPACE_S;
        y = Ui.labelledBar(g, font, S, x, y, w, Component.translatable(KEY + "mastery"),
                Component.literal(Math.round(book.mastery() * 100) + "%"), book.mastery(), S.warn());
        y = UiText.drawWrapped(g, font, S, UiText.Role.CAPTION, Component.translatable(KEY + "how",
                Proficiency.PROFICIENT.getCastsRequired(), Proficiency.MASTERED.getCastsRequired()), x, y, w, 3);
        y += WizardsMetrics.SPACE_S;

        // ── the families ──
        int cols = Math.max(1, (w + CARD_GAP) / (SpellCardWidget.CARD_W + CARD_GAP));
        int cardW = (w - (cols - 1) * CARD_GAP) / cols;
        SheetCaches.KnownSpell hovered = null;
        for (SpellCategory category : SpellCategory.values()) {
            List<SheetCaches.KnownSpell> spells = book.byCategory().get(category);
            if (spells == null || spells.isEmpty()) {
                continue;
            }
            int hue = UiContrast.readableOn(SpellCardWidget.spellCategoryColor(category), S.field());
            Component heading = Component.translatable("spell.wizards_and_beasts.category." + category.getSerializedName());
            UiText.drawFit(g, font, S, heading, x, y, w - 20, hue);
            UiText.drawRight(g, font, S, Component.literal(Integer.toString(spells.size())), x + w, y, S.ink3());
            y += font.lineHeight + 1;
            Ui.hairline(g, S, x, y, w);
            y += WizardsMetrics.SPACE_S;
            for (int i = 0; i < spells.size(); i++) {
                SheetCaches.KnownSpell spell = spells.get(i);
                int col = i % cols;
                int cardX = x + col * (cardW + CARD_GAP);
                int cardY = y + (i / cols) * (SpellCardWidget.CARD_H + CARD_GAP);
                SpellCardWidget.draw(g, cardX, cardY, cardW, spell.spell(), spell.proficiency(), spell.hits(),
                        spell.nextAt(), spell.tierProgress());
                if (hovered(cardX, cardY, cardW, SpellCardWidget.CARD_H, mouseX, mouseY)) {
                    hovered = spell;
                }
            }
            int rows = (spells.size() + cols - 1) / cols;
            y += rows * (SpellCardWidget.CARD_H + CARD_GAP) + WizardsMetrics.SPACE_S;
        }
        if (hovered != null) {
            tooltip(cardTooltip(hovered));
        }
        return y;
    }

    /** Name, family, tier, how far to the next, and what the spell hits for now. */
    private List<Component> cardTooltip(SheetCaches.KnownSpell spell) {
        List<Component> cached = tooltipLines;
        if (cached != null && spell.equals(tooltipFor)) {
            return cached;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(spell.name()));
        lines.add(Component.translatable("spell.wizards_and_beasts.category." + spell.spell().getCategory().getSerializedName())
                .append(" · ").append(tierName(spell.proficiency())));
        if (spell.nextAt() < 0) {
            lines.add(Component.translatable(KEY + "tip.mastered", spell.hits()));
        } else {
            Proficiency next = spell.proficiency() == Proficiency.NOVICE ? Proficiency.PROFICIENT : Proficiency.MASTERED;
            lines.add(Component.translatable(KEY + "tip.next", spell.nextAt() - spell.hits(), tierName(next)));
        }
        lines.addAll(SpellPowerTooltip.lines(spell.spell()));
        tooltipFor = spell;
        tooltipLines = lines;
        return lines;
    }

    static Component tierName(Proficiency tier) {
        return Component.translatable(KEY + "tier." + tier.name().toLowerCase(Locale.ROOT));
    }

    private static Ui.Tone tierTone(Proficiency tier) {
        return switch (tier) {
            case MASTERED -> Ui.Tone.ACCENT;
            case PROFICIENT -> Ui.Tone.GOOD;
            case NOVICE -> Ui.Tone.MUTED;
        };
    }
}
