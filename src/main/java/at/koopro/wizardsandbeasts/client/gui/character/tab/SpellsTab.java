package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.gui.character.widget.SpellCardWidget;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Spells tab: scrollable grid of known spell cards sorted by proficiency then alphabetically. */
public final class SpellsTab implements CharacterTab {

    private static final int CARD_GAP    = 3;
    private static final int SCROLLBAR_W = 4;
    private static final int COLOR_SCROLL_TRACK = 0xFF1A1005;
    private static final int COLOR_SCROLL_THUMB = 0xFF886622;
    private static final int COLOR_SECTION      = 0xFFDDB97A;
    private static final int COLOR_NONE         = 0xFF776655;

    private float scrollOffset = 0f; // pixels scrolled from top

    @Override
    public @NonNull String translationKey() {
        return "gui.wizards_and_beasts.character_sheet.tab.spells";
    }

    @Override
    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h, float partialTick) {
        Font font = Minecraft.getInstance().font;

        List<SpellEntry> entries = buildSortedEntries();

        if (entries.isEmpty()) {
            g.drawString(font, "No spells learned.", x + 4, y + 4, COLOR_NONE, false);
            return;
        }

        int gridW = w - SCROLLBAR_W - 3;
        int cols  = Math.max(1, gridW / (SpellCardWidget.CARD_W + CARD_GAP));
        int rows  = (entries.size() + cols - 1) / cols;
        int totalH = rows * (SpellCardWidget.CARD_H + CARD_GAP);

        // Clamp scroll
        float maxScroll = Math.max(0, totalH - h);
        scrollOffset = Mth.clamp(scrollOffset, 0f, maxScroll);

        // Scissor to content area (exclude scrollbar column)
        g.enableScissor(x, y, x + gridW, y + h);

        int cardOY = y - (int) scrollOffset;
        for (int i = 0; i < entries.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cx  = x + col * (SpellCardWidget.CARD_W + CARD_GAP);
            int cy  = cardOY + row * (SpellCardWidget.CARD_H + CARD_GAP);

            // skip cards fully outside the viewport
            if (cy + SpellCardWidget.CARD_H < y || cy > y + h) continue;

            SpellCardWidget.draw(g, cx, cy, entries.get(i).spell(), entries.get(i).proficiency());
        }

        g.disableScissor();

        // Scrollbar
        if (totalH > h) {
            int sbX  = x + gridW + 2;
            int sbH  = h;
            g.fill(sbX, y, sbX + SCROLLBAR_W, y + sbH, COLOR_SCROLL_TRACK);
            float thumbPct = (float) h / totalH;
            int thumbH     = Math.max(8, (int)(sbH * thumbPct));
            int thumbY     = y + (int)((scrollOffset / maxScroll) * (sbH - thumbH));
            g.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, COLOR_SCROLL_THUMB);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset -= (float)(delta * 10.0);
        return true;
    }

    // ── internals ──────────────────────────────────────────────────────────

    private record SpellEntry(@NonNull Spell spell, @NonNull Proficiency proficiency) {}

    @NonNull
    private List<SpellEntry> buildSortedEntries() {
        Set<String> known = ClientSpellDataState.get().getKnownSpells();
        List<SpellEntry> list = new ArrayList<>(known.size());
        for (String id : known) {
            Spell spell = Spells.byId(id);
            if (spell == null) continue;
            int hits = ClientSpellDataState.get().getSuccessfulHits(id);
            Proficiency prof = Proficiency.fromCastCount(hits);
            list.add(new SpellEntry(spell, prof));
        }
        // Sort: MASTERED first, then PROFICIENT, then NOVICE; within tier: alphabetical
        list.sort(Comparator.<SpellEntry, Integer>comparing(e -> switch (e.proficiency()) {
            case MASTERED   -> 0;
            case PROFICIENT -> 1;
            case NOVICE     -> 2;
        }).thenComparing(e -> e.spell().getDisplayName()));
        return list;
    }
}
