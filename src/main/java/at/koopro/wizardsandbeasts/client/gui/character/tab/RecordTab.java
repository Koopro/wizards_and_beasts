package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.ministry.state.ClientMinistryRecordState;
import at.koopro.wizardsandbeasts.client.standing.state.ClientStandingState;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.ministry.data.MinistryRank;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.WantedLevel;
import at.koopro.wizardsandbeasts.standing.StandingAxis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Map;

/**
 * Record tab: who this wizard is in magical society — where they stand on each axis, and what the
 * Ministry in particular has on them.
 *
 * <p>Standing sits above the Ministry sections because it is the wider statement and the Ministry axis
 * is one of its three; the criminal file below is the detail behind that one axis. Two panels rather
 * than two tabs, because they are one subject and splitting them would make the player compare across
 * a tab switch.
 *
 * <p>Reads only {@link ClientMinistryRecordState}, which holds this player's own record and nobody else's.
 * Nothing here is computed client-side: the band, the heat and the debt all arrive already decided by the
 * server, and this tab is a readout of them.
 */
public final class RecordTab implements CharacterTab {

    private static final String KEY = "gui.wizards_and_beasts.character_sheet.";

    private static final int COLOR_SECTION = 0xFFDDB97A;
    private static final int COLOR_LABEL   = 0xFF887766;
    private static final int COLOR_VALUE   = 0xFFEEDDBB;
    private static final int COLOR_MUTED   = 0xFF6E5C44;
    private static final int COLOR_FINE    = 0xFFDDAA44;
    /** Same warm track/fill as the attribute meters, so the heat bar reads as furniture from this sheet. */
    private static final int COLOR_BAR_TRACK = 0xFF3B2A16;
    private static final int COLOR_BAR_FILL  = 0xFF886622;

    private static final int ROW_H = 9;
    private static final int BAR_H = 4;
    /** Bipolar meter: taller than the notoriety bar, because it carries a centre mark and a fill either way. */
    private static final int AXIS_BAR_H = 5;
    /** Centre tick: where an axis reads as genuinely uncommitted. */
    private static final int COLOR_AXIS_CENTRE = 0xFF6E5C44;

    private float scrollOffset = 0f;
    private int lastTotalH = 0;

    @Override
    public String translationKey() {
        return KEY + "tab.record";
    }

    @Override
    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h,
                       int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        float maxScroll = Math.max(0, lastTotalH - h);
        scrollOffset = Mth.clamp(scrollOffset, 0f, maxScroll);

        g.enableScissor(x, y, x + w, y + h);

        int cx = x + 2;
        int cy = y + 2 - (int) scrollOffset;
        int top = cy;
        int innerW = w - 4 - TabScrollbar.WIDTH;

        // ── Where this wizard stands ──
        // Drawn before the Ministry check, and outside it: tradition and alignment are facts about the
        // character that survive the Ministry module being switched off. Only the criminal file below
        // depends on the Trace running.
        cy = drawStandingSection(g, font, cx, cy, innerW);

        if (!ClientMinistryRecordState.traceActive()) {
            // An empty record and a switched-off Ministry look identical from the data alone, and a player
            // reading "no offences" on a server with the module off would draw the wrong conclusion.
            section(g, font, cx, cy, "record.ministry");
            cy += 10;
            g.drawString(font, Component.translatable(KEY + "record.trace_off").getString(),
                    cx, cy, COLOR_MUTED, false);
            cy += ROW_H;
            finish(g, x, y, w, h, cy, top);
            return;
        }

        PlayerMinistryRecord record = ClientMinistryRecordState.get();

        // ── The Ministry in particular: the band, then the heat behind it ──
        section(g, font, cx, cy, "record.ministry");
        cy += 10;

        String statusLabel = Component.translatable(KEY + "record.status").getString();
        g.drawString(font, statusLabel + ":", cx, cy, COLOR_LABEL, false);
        WantedLevel band = record.wantedLevel();
        g.drawString(font, band.displayName().getString(),
                cx + font.width(statusLabel + ": "), cy, bandColor(band), false);
        cy += ROW_H;

        drawKV(g, font, cx, cy, innerW, "record.notoriety",
                String.format(Locale.ROOT, "%.1f / %.0f",
                        record.notoriety(), PlayerMinistryRecord.MAX_NOTORIETY));
        cy += ROW_H;

        drawBar(g, cx, cy, innerW, record.notoriety() / PlayerMinistryRecord.MAX_NOTORIETY);
        cy += BAR_H + 4;

        if (record.rank() != MinistryRank.NONE) {
            drawKV(g, font, cx, cy, innerW, "record.rank", record.rank().displayName().getString());
            cy += ROW_H;
        }
        if (record.fugitive()) {
            g.drawString(font, Component.translatable(KEY + "record.fugitive").getString(),
                    cx, cy, 0xFFCC4444, false);
            cy += ROW_H;
        }
        cy += 4;

        // ── Outstanding fine ──
        section(g, font, cx, cy, "record.fines");
        cy += 10;
        if (record.owesFine()) {
            drawKV(g, font, cx, cy, innerW, "record.outstanding",
                    CurrencyHelper.formatFromKnuts(record.outstandingFineKnuts()), COLOR_FINE);
            cy += ROW_H;
            g.drawString(font, Component.translatable(KEY + "record.fine_frozen").getString(),
                    cx, cy, COLOR_MUTED, false);
            cy += ROW_H;
        } else if (!ClientMinistryRecordState.finesActive()) {
            g.drawString(font, Component.translatable(KEY + "record.fines_off").getString(),
                    cx, cy, COLOR_MUTED, false);
            cy += ROW_H;
        } else {
            g.drawString(font, Component.translatable(KEY + "record.no_fines").getString(),
                    cx, cy, COLOR_MUTED, false);
            cy += ROW_H;
        }
        cy += 4;

        // ── The file, which never decays ──
        section(g, font, cx, cy, "record.file");
        cy += 10;
        Map<MagicalOffence, Integer> offences = record.offencesByWeight();
        if (offences.isEmpty()) {
            g.drawString(font, Component.translatable(KEY + "record.clean").getString(),
                    cx, cy, COLOR_MUTED, false);
            cy += ROW_H;
        } else {
            for (Map.Entry<MagicalOffence, Integer> entry : offences.entrySet()) {
                String name = entry.getKey().displayName().getString();
                String count = "×" + entry.getValue();
                g.drawString(font, font.plainSubstrByWidth(name, innerW - font.width(count) - 4),
                        cx, cy, COLOR_VALUE, false);
                g.drawString(font, count, cx + innerW - font.width(count), cy, COLOR_LABEL, false);
                cy += ROW_H;
            }
        }

        finish(g, x, y, w, h, cy, top);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset -= (float) (delta * 10.0);
        return true;
    }

    // ── private helpers ───────────────────────────────────────────────────

    /**
     * The three axes, each as a name, the band it currently sits in, and a meter filled from the
     * centre toward whichever pole the wizard has moved to.
     *
     * <p>Filled from the centre rather than from the left on purpose: these are not quantities, they
     * are positions between two named ends, and a left-filled bar would read as "37% traditionalist"
     * rather than "somewhat traditionalist".
     *
     * @return the new content cursor
     */
    private int drawStandingSection(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w) {
        section(g, font, x, y, "record.axes");
        y += 10;

        if (!ClientStandingState.hasData()) {
            // Never sent is not the same as all zeroes, and the honest line for it is not "neutral".
            g.drawString(font, Component.translatable(KEY + "record.standing_unknown").getString(),
                    x, y, COLOR_MUTED, false);
            return y + ROW_H + 4;
        }

        for (StandingAxis axis : StandingAxis.values()) {
            String name = axis.displayName().getString();
            String band = axis.bandName(ClientStandingState.bandOf(axis)).getString();
            g.drawString(font, font.plainSubstrByWidth(name, w - font.width(band) - 6),
                    x, y, COLOR_VALUE, false);
            g.drawString(font, band, x + w - font.width(band), y, axis.color(), false);
            y += ROW_H;

            drawAxisMeter(g, x, y, w, ClientStandingState.fractionOf(axis), axis.color());
            y += AXIS_BAR_H + 2;

            // Pole names under the meter, so the direction of travel is readable without knowing the
            // system. Dim: they are the scale, not the reading.
            String negative = axis.negativePole().getString();
            String positive = axis.positivePole().getString();
            g.drawString(font, negative, x, y, COLOR_MUTED, false);
            g.drawString(font, positive, x + w - font.width(positive), y, COLOR_MUTED, false);
            y += ROW_H + 3;
        }
        return y + 1;
    }

    /** A track with a centre tick, filled from the middle toward the pole {@code fraction} points at. */
    private static void drawAxisMeter(@NonNull GuiGraphics g, int x, int y, int w, float fraction, int color) {
        g.fill(x, y, x + w, y + AXIS_BAR_H, COLOR_BAR_TRACK);
        int centre = x + w / 2;
        int extent = Math.round(Math.abs(Mth.clamp(fraction, -1.0f, 1.0f)) * (w / 2.0f));
        if (extent > 0) {
            if (fraction > 0.0f) {
                g.fill(centre, y, Math.min(x + w, centre + extent), y + AXIS_BAR_H, color);
            } else {
                g.fill(Math.max(x, centre - extent), y, centre, y + AXIS_BAR_H, color);
            }
        }
        // Drawn last so the tick stays visible through a fill that reaches it.
        g.fill(centre, y - 1, centre + 1, y + AXIS_BAR_H + 1, COLOR_AXIS_CENTRE);
    }

    /** Closes the scissor, measures the frame just laid out, and draws the scrollbar over it. */
    private void finish(@NonNull GuiGraphics g, int x, int y, int w, int h, int cy, int top) {
        g.disableScissor();
        lastTotalH = cy - top + 4;
        TabScrollbar.draw(g, x, y, w, h, scrollOffset, lastTotalH);
    }

    private static void section(@NonNull GuiGraphics g, @NonNull Font font, int x, int y,
                                @NonNull String id) {
        g.drawString(font, Component.translatable(KEY + "section." + id).getString(),
                x, y, COLOR_SECTION, false);
    }

    private static void drawKV(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w,
                               @NonNull String labelId, @NonNull String value) {
        drawKV(g, font, x, y, w, labelId, value, COLOR_VALUE);
    }

    private static void drawKV(@NonNull GuiGraphics g, @NonNull Font font, int x, int y, int w,
                               @NonNull String labelId, @NonNull String value, int valueColor) {
        String label = Component.translatable(KEY + labelId).getString();
        g.drawString(font, label + ":", x, y, COLOR_LABEL, false);
        int lw = font.width(label + ": ");
        g.drawString(font, font.plainSubstrByWidth(value, w - lw), x + lw, y, valueColor, false);
    }

    private static void drawBar(@NonNull GuiGraphics g, int x, int y, int w, float fraction) {
        g.fill(x, y, x + w, y + BAR_H, COLOR_BAR_TRACK);
        int filled = Math.round(Mth.clamp(fraction, 0f, 1f) * w);
        if (filled > 0) {
            g.fill(x, y, x + filled, y + BAR_H, COLOR_BAR_FILL);
        }
    }

    /**
     * The band's own colour, lifted onto the parchment. {@link WantedLevel#color()} is picked for chat on
     * black and its lower bands are too dark to read on this panel, so the tones are restated here at the
     * same contrast the rest of the sheet uses.
     */
    private static int bandColor(WantedLevel level) {
        return switch (level) {
            case CLEAR -> 0xFF8FBF6A;
            case OF_INTEREST -> 0xFFD8C070;
            case WANTED -> 0xFFDDAA44;
            case DANGEROUS -> 0xFFCC7755;
            case UNDESIRABLE -> 0xFFCC4444;
        };
    }
}
