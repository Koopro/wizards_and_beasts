package at.koopro.wizardsandbeasts.client.journey.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedChip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The quest journal (UI renaissance P6, 2026-11-19): an open brown leather field journal. The left page lists a
 * section's entries with ink tick-boxes (a quill nib on the one to do now); the right page reads the chosen entry —
 * what to do, how far along, what it pays. Sections are chips at the top of the left page.
 *
 * <p>Read-only over synced state ({@link QuestJournal}); it re-reads every frame, so a step done while it is open
 * ticks itself.
 */
@NullMarked
public final class QuestJournalScreen extends Screen {

    static final int W = 300;
    static final int H = 190;
    private static final int BORDER = 8;
    private static final int SPINE_W = 6;
    private static final int PAGE_PAD = 8;
    private static final int ROW_H = 13;
    private static final int LIST_TOP = 42;
    /** The caption under the chips: how the section stands, in words. */
    private static final int CAPTION_Y = 29;
    private static final UiSurface S = UiSurface.PAGE;

    private final java.util.Map<QuestJournal.Section, ThemedChip> chips = new java.util.EnumMap<>(QuestJournal.Section.class);
    private QuestJournal.Section section = QuestJournal.Section.PATH;
    private int selected = -1;
    private int scroll;
    private int left;
    private int top;

    public QuestJournalScreen() {
        this(QuestJournal.Section.PATH);
    }

    /** The journal opened on {@code section} (the sheet's links and the capture harness). */
    public QuestJournalScreen(QuestJournal.Section section) {
        super(Component.translatable("gui.wizards_and_beasts.journal.title"));
        this.section = section;
    }

    private int pageW() {
        return (W - 2 * BORDER - SPINE_W) / 2;
    }

    private int leftPageX() {
        return left + BORDER;
    }

    private int rightPageX() {
        return left + BORDER + pageW() + SPINE_W;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int cx = leftPageX() + PAGE_PAD;
        for (QuestJournal.Section s : QuestJournal.Section.values()) {
            Component label = chipLabel(s);
            int w = ThemedChip.widthFor(font, label);
            ThemedChip chip = new ThemedChip(cx, top + BORDER + 6, w, 13, label, () -> pick(s), () -> section == s);
            chips.put(s, chip);
            addRenderableWidget(chip);
            cx += w + 3;
        }
    }

    private Component chipLabel(QuestJournal.Section s) {
        return s.title();
    }

    private void pick(QuestJournal.Section s) {
        section = s;
        selected = -1;
        scroll = 0;
    }

    private int visibleRows() {
        return (H - BORDER - LIST_TOP - PAGE_PAD) / ROW_H;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<QuestJournal.Entry> entries = QuestJournal.entries(section);
        if (selected < 0 || selected >= entries.size()) {
            selected = firstCurrent(entries);
        }
        drawBook(g);
        super.render(g, mouseX, mouseY, partialTick);
        drawList(g, entries, mouseX, mouseY);
        drawDetail(g, entries);
    }

    private static int firstCurrent(List<QuestJournal.Entry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).state() == QuestJournal.State.CURRENT) {
                return i;
            }
        }
        return entries.isEmpty() ? -1 : 0;
    }

    private void drawBook(GuiGraphics g) {
        at.koopro.wizardsandbeasts.client.gui.ui.BookChrome.draw(g,
                at.koopro.wizardsandbeasts.client.gui.ui.BookChrome.FIELD_JOURNAL, left, top, W, H);
    }

    private void drawList(GuiGraphics g, List<QuestJournal.Entry> entries, int mouseX, int mouseY) {
        int x = leftPageX() + PAGE_PAD;
        int w = pageW() - 2 * PAGE_PAD - 4;
        int y0 = top + LIST_TOP;
        // Re-read every frame, so a step done while the journal is open is counted at once.
        UiText.drawFit(g, font, S, QuestJournal.caption(section, entries), x, top + CAPTION_Y, w, S.ink3());
        Ui.hairline(g, S, x, top + CAPTION_Y + 10, w);
        if (entries.isEmpty()) {
            Ui.emptyState(g, font, S, new Ui.Rect(x, y0, w, H - LIST_TOP - BORDER - PAGE_PAD),
                    QuestJournal.emptyTitle(section), QuestJournal.emptyHint(section));
            return;
        }
        int rows = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, entries.size() - rows));
        for (int r = 0; r < rows && scroll + r < entries.size(); r++) {
            int i = scroll + r;
            QuestJournal.Entry e = entries.get(i);
            int ry = y0 + r * ROW_H;
            boolean over = mouseX >= x - 2 && mouseX < x + w + 2 && mouseY >= ry - 1 && mouseY < ry + ROW_H - 1;
            if (i == selected) {
                g.fill(x - 2, ry - 1, x + w + 2, ry + ROW_H - 1, S.selectWash());
                g.fill(x - 2, ry - 1, x - 1, ry + ROW_H - 1, WizardsPalette.GILT_DARK);
            } else if (over) {
                g.fill(x - 2, ry - 1, x + w + 2, ry + ROW_H - 1, S.hoverWash());
            }
            mark(g, x, ry + 1, e.state());
            int colour = e.state() == QuestJournal.State.DONE ? S.ink3()
                    : e.state() == QuestJournal.State.CURRENT ? S.ink() : S.ink2();
            UiText.drawFit(g, font, S, e.title(), x + 12, ry + 1, w - 12, colour);
            if (e.state() == QuestJournal.State.DONE) {
                // struck through, as a finished line in a field journal
                int tw = Math.min(w - 12, font.width(e.title()));
                g.fill(x + 12, ry + 5, x + 12 + tw, ry + 6, (0x90 << 24) | (S.ink3() & 0xFFFFFF));
            }
        }
        if (entries.size() > rows) {
            String more = (scroll + rows) + " / " + entries.size();
            g.drawString(font, more, x + w - font.width(more), top + H - BORDER - 9, S.ink3(), false);
        }
    }

    /** The row's ink mark: a ticked box, the quill nib for now, an empty box for later, a coin for an offer. */
    private static void mark(GuiGraphics g, int x, int y, QuestJournal.State state) {
        int ink = WizardsPalette.PAGE_INK_2;
        switch (state) {
            case DONE -> {
                box(g, x, y, ink);
                g.fill(x + 2, y + 4, x + 3, y + 5, WizardsPalette.PAGE_GOOD);
                g.fill(x + 3, y + 5, x + 4, y + 6, WizardsPalette.PAGE_GOOD);
                g.fill(x + 4, y + 2, x + 5, y + 5, WizardsPalette.PAGE_GOOD);
                g.fill(x + 5, y + 1, x + 6, y + 3, WizardsPalette.PAGE_GOOD);
            }
            case CURRENT -> {
                // a quill nib pointing at the line
                g.fill(x + 1, y + 1, x + 3, y + 7, WizardsPalette.PAGE_RUBRIC);
                g.fill(x + 3, y + 2, x + 5, y + 6, WizardsPalette.PAGE_RUBRIC);
                g.fill(x + 5, y + 3, x + 7, y + 5, WizardsPalette.PAGE_RUBRIC);
            }
            case OPEN -> box(g, x, y, (0xA0 << 24) | (ink & 0xFFFFFF));
            case OFFERED -> {
                g.fill(x + 1, y + 1, x + 6, y + 6, WizardsPalette.GILT_DARK);
                g.fill(x + 2, y + 2, x + 5, y + 5, WizardsPalette.GILT);
            }
        }
    }

    private static void box(GuiGraphics g, int x, int y, int colour) {
        g.fill(x, y, x + 7, y + 1, colour);
        g.fill(x, y + 6, x + 7, y + 7, colour);
        g.fill(x, y + 1, x + 1, y + 6, colour);
        g.fill(x + 6, y + 1, x + 7, y + 6, colour);
    }

    private void drawDetail(GuiGraphics g, List<QuestJournal.Entry> entries) {
        int x = rightPageX() + PAGE_PAD;
        // Clear of the ribbon, which lies across this page near its outer edge.
        int w = pageW() - PAGE_PAD - at.koopro.wizardsandbeasts.client.gui.ui.BookChrome.RIBBON_INSET - 4;
        int y = top + BORDER + 6;
        if (selected < 0 || selected >= entries.size()) {
            UiText.drawWrapped(g, font, S, UiText.Role.HEADING, section.title(), x, y, w, 2);
            return;
        }
        QuestJournal.Entry e = entries.get(selected);
        y = UiText.drawWrapped(g, font, S, UiText.Role.HEADING, e.title(), x, y, w, 2);
        Component state = QuestJournal.stateLabel(e.state());
        g.drawString(font, state, x, y + 1, e.state() == QuestJournal.State.DONE ? WizardsPalette.PAGE_GOOD : S.ink3(),
                false);
        y += 12;
        Ui.hairline(g, S, x, y, w);
        y += 4;
        int bottom = top + H - BORDER - PAGE_PAD;
        int reserved = (e.target() > 0 ? 18 : 0) + (e.reward() != null ? 11 : 0) + (e.note() != null ? 11 : 0);
        int maxLines = Math.max(1, (bottom - reserved - y) / 10);
        y = UiText.drawWrapped(g, font, S, UiText.Role.BODY, e.body(), x, y, w, maxLines) + 4;
        if (e.target() > 0) {
            float f = Mth.clamp(e.progress() / (float) e.target(), 0f, 1f);
            y = Ui.labelledBar(g, font, S, x, y, w, Component.translatable("gui.wizards_and_beasts.journal.progress"),
                    Component.literal(e.progress() + " / " + e.target()), f, WizardsPalette.GILT_DARK);
        }
        if (e.reward() != null) {
            UiText.drawFit(g, font, S, e.reward(), x, y, w, S.ink2());
            y += 11;
        }
        if (e.note() != null) {
            UiText.drawFit(g, font, S, e.note(), x, y, w,
                    e.state() == QuestJournal.State.DONE ? WizardsPalette.PAGE_GOOD : WizardsPalette.PAGE_RUBRIC);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        List<QuestJournal.Entry> entries = QuestJournal.entries(section);
        int x = leftPageX() + PAGE_PAD;
        int w = pageW() - 2 * PAGE_PAD - 4;
        int y0 = top + LIST_TOP;
        if (event.x() >= x - 2 && event.x() < x + w + 2 && event.y() >= y0 - 1) {
            int r = (int) Math.floor((event.y() - y0 + 1) / ROW_H);
            int i = scroll + r;
            if (r >= 0 && r < visibleRows() && i < entries.size()) {
                selected = i;
                if (minecraft != null) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
