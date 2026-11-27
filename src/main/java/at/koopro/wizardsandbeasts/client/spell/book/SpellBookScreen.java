package at.koopro.wizardsandbeasts.client.spell.book;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.BookChrome;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.spell.trace.SpellTraceClient;
import at.koopro.wizardsandbeasts.item.spell.SpellSourceItem;
import at.koopro.wizardsandbeasts.network.spell.SpellBookChapterC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellBookOpenS2CPayload;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.learning.SpellSource;
import at.koopro.wizardsandbeasts.spell.learning.book.SpellBooks;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceDifficulty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * A spell textbook lying open (spell acquisition remake, 2026-11-27). The left page is the book's title, author and
 * chapter list, each chapter marked known (ticked), open to study (a quill nib) or closed (a faint box); the right
 * page reads the chosen chapter: the spell, its kind, how hard its wand movement is, and either "Study this chapter"
 * or why it is closed. Studying opens the trace; a passed trace sends the book's new state, which this screen takes
 * in place ({@link #show}).
 *
 * <p>Everything it shows is the server's verdict ({@link SpellBookOpenS2CPayload}); the screen judges nothing.
 */
@NullMarked
public final class SpellBookScreen extends Screen {

    private static final String KEY = "gui.wizards_and_beasts.spell_book.";
    static final int W = 300;
    static final int H = 190;
    private static final int PAGE_PAD = 8;
    private static final int ROW_H = 13;
    private static final int LIST_TOP = 48;
    private static final UiSurface S = UiSurface.PAGE;
    /** The spellbook's own cover: the field journal's leather with a deep red ribbon. */
    private static final BookChrome.Style STYLE = BookChrome.FIELD_JOURNAL;

    private SpellBookOpenS2CPayload book;
    private int selected;
    private int scroll;
    private int left;
    private int top;
    private ThemedButton study;

    private SpellBookScreen(SpellBookOpenS2CPayload book) {
        super(SpellSourceItem.bookTitle(book.bookId()));
        this.book = book;
        this.selected = firstOpen(book.chapters());
    }

    /**
     * Opens the book, or updates the one already open on the same book -- also when it is waiting behind a trace,
     * so the page a reader returns to already shows the chapter they just learned.
     */
    public static void show(SpellBookOpenS2CPayload pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SpellBookScreen open && open.book.bookId().equals(pkt.bookId())) {
            open.update(pkt);
            return;
        }
        if (SpellTraceClient.screenBehindTrace() instanceof SpellBookScreen behind
                && behind.book.bookId().equals(pkt.bookId())) {
            behind.update(pkt);
            return;
        }
        mc.setScreen(new SpellBookScreen(pkt));
    }

    private void update(SpellBookOpenS2CPayload pkt) {
        String was = selected >= 0 && selected < book.chapters().size() ? book.chapters().get(selected).spellId() : "";
        this.book = pkt;
        selected = -1;
        for (int i = 0; i < pkt.chapters().size(); i++) {
            if (pkt.chapters().get(i).spellId().equals(was)) {
                selected = i;
            }
        }
        if (selected < 0) {
            selected = firstOpen(pkt.chapters());
        }
    }

    private static int firstOpen(List<SpellBookOpenS2CPayload.Chapter> chapters) {
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).state() == SpellBookOpenS2CPayload.OPEN) {
                return i;
            }
        }
        return chapters.isEmpty() ? -1 : 0;
    }

    private int leftPageX() {
        return BookChrome.leftPageX(left);
    }

    private int rightPageX() {
        return BookChrome.rightPageX(left, W);
    }

    private int pageW() {
        return BookChrome.pageW(W);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int x = rightPageX() + PAGE_PAD;
        int w = pageW() - PAGE_PAD - BookChrome.RIBBON_INSET - 4;
        study = new ThemedButton(x, top + H - BookChrome.BORDER - PAGE_PAD - 16, w, 16,
                Component.translatable(KEY + "study"), this::studySelected);
        addRenderableWidget(study);
    }

    private void studySelected() {
        SpellBookOpenS2CPayload.Chapter chapter = selectedChapter();
        if (chapter != null && chapter.state() == SpellBookOpenS2CPayload.OPEN) {
            ClientPacketDistributor.sendToServer(new SpellBookChapterC2SPayload(book.hand(), chapter.spellId()));
        }
    }

    private SpellBookOpenS2CPayload.@org.jspecify.annotations.Nullable Chapter selectedChapter() {
        return selected >= 0 && selected < book.chapters().size() ? book.chapters().get(selected) : null;
    }

    private int visibleRows() {
        return (H - BookChrome.BORDER - LIST_TOP - PAGE_PAD) / ROW_H;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        SpellBookOpenS2CPayload.Chapter chapter = selectedChapter();
        study.visible = chapter != null && chapter.state() == SpellBookOpenS2CPayload.OPEN;
        BookChrome.draw(g, STYLE, left, top, W, H);
        super.render(g, mouseX, mouseY, partialTick);
        drawContents(g, mouseX, mouseY);
        drawChapter(g, chapter);
    }

    private void drawContents(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPageX() + PAGE_PAD;
        int w = pageW() - 2 * PAGE_PAD - 4;
        int y = top + BookChrome.BORDER + 4;
        String path = SpellBooks.path(book.bookId());
        UiText.drawFit(g, font, S, getTitle(), x, y, w, S.ink());
        UiText.drawFit(g, font, S, Component.translatableWithFallback(
                "spell_book.wizards_and_beasts." + path + ".author", ""), x, y + 11, w, S.ink3());
        List<SpellBookOpenS2CPayload.Chapter> chapters = book.chapters();
        int known = 0;
        for (SpellBookOpenS2CPayload.Chapter c : chapters) {
            if (c.state() == SpellBookOpenS2CPayload.KNOWN) {
                known++;
            }
        }
        UiText.drawFit(g, font, S, Component.translatable(KEY + "progress", known, chapters.size()), x, y + 24, w,
                S.ink3());
        Ui.hairline(g, S, x, top + LIST_TOP - 5, w);
        if (chapters.isEmpty()) {
            UiText.drawWrapped(g, font, S, UiText.Role.BODY, Component.translatable(KEY + "empty"), x, top + LIST_TOP,
                    w, 3);
            return;
        }
        int rows = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, chapters.size() - rows));
        for (int r = 0; r < rows && scroll + r < chapters.size(); r++) {
            int i = scroll + r;
            SpellBookOpenS2CPayload.Chapter c = chapters.get(i);
            int ry = top + LIST_TOP + r * ROW_H;
            boolean over = mouseX >= x - 2 && mouseX < x + w + 2 && mouseY >= ry - 1 && mouseY < ry + ROW_H - 1;
            if (i == selected) {
                g.fill(x - 2, ry - 1, x + w + 2, ry + ROW_H - 1, S.selectWash());
                g.fill(x - 2, ry - 1, x - 1, ry + ROW_H - 1, WizardsPalette.GILT_DARK);
            } else if (over) {
                g.fill(x - 2, ry - 1, x + w + 2, ry + ROW_H - 1, S.hoverWash());
            }
            mark(g, x, ry + 1, c.state());
            int colour = c.state() == SpellBookOpenS2CPayload.OPEN ? S.ink()
                    : c.state() == SpellBookOpenS2CPayload.KNOWN ? S.ink2() : S.ink3();
            UiText.drawFit(g, font, S, SpellSource.writtenName(c.spellId()), x + 12, ry + 1, w - 12, colour);
        }
        if (chapters.size() > rows) {
            String more = (scroll + rows) + " / " + chapters.size();
            g.drawString(font, more, x + w - font.width(more), top + H - BookChrome.BORDER - 9, S.ink3(), false);
        }
    }

    /** Known: a ticked box. Open: a quill nib pointing at the line. Closed: a faint empty box. */
    private static void mark(GuiGraphics g, int x, int y, int state) {
        int ink = WizardsPalette.PAGE_INK_2;
        if (state == SpellBookOpenS2CPayload.KNOWN) {
            box(g, x, y, ink);
            g.fill(x + 2, y + 4, x + 3, y + 5, WizardsPalette.PAGE_GOOD);
            g.fill(x + 3, y + 5, x + 4, y + 6, WizardsPalette.PAGE_GOOD);
            g.fill(x + 4, y + 2, x + 5, y + 5, WizardsPalette.PAGE_GOOD);
            g.fill(x + 5, y + 1, x + 6, y + 3, WizardsPalette.PAGE_GOOD);
        } else if (state == SpellBookOpenS2CPayload.OPEN) {
            g.fill(x + 1, y + 1, x + 3, y + 7, WizardsPalette.PAGE_RUBRIC);
            g.fill(x + 3, y + 2, x + 5, y + 6, WizardsPalette.PAGE_RUBRIC);
            g.fill(x + 5, y + 3, x + 7, y + 5, WizardsPalette.PAGE_RUBRIC);
        } else {
            box(g, x, y, (0x70 << 24) | (ink & 0xFFFFFF));
        }
    }

    private static void box(GuiGraphics g, int x, int y, int colour) {
        g.fill(x, y, x + 7, y + 1, colour);
        g.fill(x, y + 6, x + 7, y + 7, colour);
        g.fill(x, y + 1, x + 1, y + 6, colour);
        g.fill(x + 6, y + 1, x + 7, y + 6, colour);
    }

    private void drawChapter(GuiGraphics g, SpellBookOpenS2CPayload.@org.jspecify.annotations.Nullable Chapter c) {
        int x = rightPageX() + PAGE_PAD;
        int w = pageW() - PAGE_PAD - BookChrome.RIBBON_INSET - 4;
        int y = top + BookChrome.BORDER + 6;
        if (c == null) {
            return;
        }
        y = UiText.drawWrapped(g, font, S, UiText.Role.HEADING, SpellSource.writtenName(c.spellId()), x, y, w, 2);
        Spell spell = Spells.byId(c.spellId());
        int cx = x;
        if (spell != null) {
            cx += Ui.chip(g, font, S, cx, y + 1, Component.translatable(
                    "spell.wizards_and_beasts.category." + spell.getCategory().getSerializedName()), Ui.Tone.NEUTRAL) + 3;
        }
        TraceDifficulty difficulty = TraceDifficulty.byOrdinal(c.difficulty());
        Ui.chip(g, font, S, cx, y + 1, Component.translatable("spell.wizards_and_beasts.trace.difficulty." + difficulty.key()),
                difficulty == TraceDifficulty.DARK ? Ui.Tone.BAD : Ui.Tone.ACCENT);
        y += 15;
        Ui.hairline(g, S, x, y, w);
        y += 5;
        Component body;
        int colour;
        if (c.state() == SpellBookOpenS2CPayload.KNOWN) {
            body = Component.translatable(KEY + "known");
            colour = WizardsPalette.PAGE_GOOD;
        } else if (c.state() == SpellBookOpenS2CPayload.OPEN) {
            body = Component.translatable(KEY + "open");
            colour = S.ink2();
        } else {
            body = SpellBookOpenS2CPayload.reasonText(c);
            colour = WizardsPalette.PAGE_RUBRIC;
        }
        int bottom = top + H - BookChrome.BORDER - PAGE_PAD - 20;
        UiText.drawWrapped(g, font, S, UiText.Role.BODY, body.copy().withColor(colour & 0xFFFFFF), x, y, w,
                Math.max(1, (bottom - y) / 10));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int x = leftPageX() + PAGE_PAD;
        int w = pageW() - 2 * PAGE_PAD - 4;
        int y0 = top + LIST_TOP;
        if (event.x() >= x - 2 && event.x() < x + w + 2 && event.y() >= y0 - 1) {
            int r = (int) Math.floor((event.y() - y0 + 1) / ROW_H);
            int i = scroll + r;
            if (r >= 0 && r < visibleRows() && i < book.chapters().size()) {
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
