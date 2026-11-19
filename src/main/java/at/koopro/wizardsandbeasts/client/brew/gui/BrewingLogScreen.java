package at.koopro.wizardsandbeasts.client.brew.gui;

import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.knowledge.BrewKnowledgeS2CPayload;
import at.koopro.wizardsandbeasts.brew.knowledge.KnownRecipesView;
import at.koopro.wizardsandbeasts.client.brew.ClientBrewData;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.BookChrome;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedChip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The brewing log (UI renaissance P6, 2026-11-19): an apothecary's notebook in green bookcloth. The left page lists
 * potions — the ones you know, or the ones still to learn — each with a swatch of its colour; the right page reads the
 * chosen one: what it is, every ingredient with its real item and count, the cauldron it wants, and when the
 * catalyst must go in. An unlearned recipe says which textbook teaches it, and nothing more.
 *
 * <p>Read-only over what the server already sends for textbook tooltips ({@link KnownRecipesView}) and the synced brew
 * definitions ({@link ClientBrewData}): no new packets.
 */
@NullMarked
public final class BrewingLogScreen extends Screen {

    private static final String KEY = "gui.wizards_and_beasts.brewing_log.";
    private static final String RKEY = "item.wizards_and_beasts.potion_recipes.";
    private static final UiSurface S = UiSurface.PAGE;
    static final int W = 300;
    static final int H = 190;
    private static final int PAD = 8;
    private static final int ROW_H = 13;
    private static final int LIST_TOP = 42;
    private static final int CAPTION_Y = 29;

    private boolean showKnown = true;
    private int selected = -1;
    private int scroll;
    private int left;
    private int top;

    public BrewingLogScreen() {
        super(Component.translatable(KEY + "title"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int cx = BookChrome.leftPageX(left) + PAD;
        for (boolean known : new boolean[]{true, false}) {
            Component label = Component.translatable(KEY + (known ? "known" : "to_learn"));
            int w = ThemedChip.widthFor(font, label);
            addRenderableWidget(new ThemedChip(cx, top + BookChrome.BORDER + 6, w, 13, label, () -> {
                showKnown = known;
                selected = -1;
                scroll = 0;
            }, () -> showKnown == known));
            cx += w + 3;
        }
    }

    /** The recipes on the current page, known first by name. */
    private List<BrewKnowledgeS2CPayload.Line> entries() {
        List<BrewKnowledgeS2CPayload.Line> out = new ArrayList<>();
        for (BrewKnowledgeS2CPayload.Line line : KnownRecipesView.lines()) {
            if (KnownRecipesView.knows(line.recipe()) == showKnown) {
                out.add(line);
            }
        }
        out.sort(Comparator.comparing(l -> name(l).getString().toLowerCase(Locale.ROOT)));
        return out;
    }

    private static Component name(BrewKnowledgeS2CPayload.Line line) {
        Brew brew = ClientBrewData.brew(line.output());
        return brew == null ? Component.literal(line.recipe()) : Component.translatable(brew.displayName());
    }

    private static int colour(BrewKnowledgeS2CPayload.Line line) {
        Brew brew = ClientBrewData.brew(line.output());
        return brew == null ? WizardsPalette.PAGE_INK_3 : 0xFF000000 | brew.color();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<BrewKnowledgeS2CPayload.Line> entries = entries();
        if (selected >= entries.size()) {
            selected = -1;
        }
        if (selected < 0 && !entries.isEmpty()) {
            selected = 0;
        }
        BookChrome.draw(g, BookChrome.BREWING_LOG, left, top, W, H);
        super.render(g, mouseX, mouseY, partialTick);
        drawList(g, entries, mouseX, mouseY);
        if (selected >= 0) {
            drawDetail(g, entries.get(selected));
        }
    }

    private int visibleRows() {
        return (H - BookChrome.BORDER - LIST_TOP - PAD) / ROW_H;
    }

    private void drawList(GuiGraphics g, List<BrewKnowledgeS2CPayload.Line> entries, int mouseX, int mouseY) {
        int x = BookChrome.leftPageX(left) + PAD;
        int w = BookChrome.pageW(W) - 2 * PAD - 4;
        int known = 0;
        for (BrewKnowledgeS2CPayload.Line line : KnownRecipesView.lines()) {
            if (KnownRecipesView.knows(line.recipe())) {
                known++;
            }
        }
        UiText.drawFit(g, font, S, Component.translatable(KEY + "caption", known, KnownRecipesView.lines().size()),
                x, top + CAPTION_Y, w, S.ink3());
        Ui.hairline(g, S, x, top + CAPTION_Y + 10, w);
        int y0 = top + LIST_TOP;
        if (entries.isEmpty()) {
            Ui.emptyState(g, font, S, new Ui.Rect(x, y0, w, H - LIST_TOP - BookChrome.BORDER - PAD),
                    Component.translatable(KEY + (showKnown ? "empty.known" : "empty.to_learn")),
                    Component.translatable(KEY + (showKnown ? "empty.known.hint" : "empty.to_learn.hint")));
            return;
        }
        int rows = visibleRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, entries.size() - rows));
        for (int r = 0; r < rows && scroll + r < entries.size(); r++) {
            int i = scroll + r;
            BrewKnowledgeS2CPayload.Line line = entries.get(i);
            int ry = y0 + r * ROW_H;
            boolean over = mouseX >= x - 2 && mouseX < x + w + 2 && mouseY >= ry - 1 && mouseY < ry + ROW_H - 1;
            if (i == selected) {
                g.fill(x - 2, ry - 1, x + w + 2, ry + ROW_H - 1, S.selectWash());
                g.fill(x - 2, ry - 1, x - 1, ry + ROW_H - 1, WizardsPalette.GILT_DARK);
            } else if (over) {
                g.fill(x - 2, ry - 1, x + w + 2, ry + ROW_H - 1, S.hoverWash());
            }
            swatch(g, x, ry + 1, colour(line), showKnown);
            UiText.drawFit(g, font, S, showKnown ? name(line) : Component.translatable(KEY + "unknown_name", name(line)),
                    x + 12, ry + 1, w - 12, showKnown ? S.ink() : S.ink2());
        }
        if (entries.size() > rows) {
            String more = (scroll + rows) + " / " + entries.size();
            g.drawString(font, more, x + w - font.width(more), top + H - BookChrome.BORDER - 9, S.ink3(), false);
        }
    }

    /** A small stoppered phial in the potion's colour; an outline only for a recipe not yet learned. */
    private static void swatch(GuiGraphics g, int x, int y, int colour, boolean known) {
        int ink = WizardsPalette.PAGE_INK_2;
        g.fill(x + 2, y, x + 5, y + 1, WizardsPalette.GILT_DARK);  // stopper
        g.fill(x + 2, y + 1, x + 5, y + 2, ink);                    // neck
        g.fill(x, y + 2, x + 7, y + 8, ink);                        // body outline
        g.fill(x + 1, y + 3, x + 6, y + 7, known ? colour : WizardsPalette.PAGE_LIGHT);
        if (known) {
            g.fill(x + 1, y + 3, x + 2, y + 5, 0x60FFFFFF);         // glint
        }
    }

    private void drawDetail(GuiGraphics g, BrewKnowledgeS2CPayload.Line line) {
        int x = BookChrome.rightPageX(left, W) + PAD;
        int w = BookChrome.pageW(W) - PAD - BookChrome.RIBBON_INSET - 4;
        int y = top + BookChrome.BORDER + 6;
        int bottom = top + H - BookChrome.BORDER - PAD;
        Brew brew = ClientBrewData.brew(line.output());
        int nameInk = UiContrast.readableOn(colour(line), WizardsPalette.PAGE, UiContrast.AA_TEXT);
        y = UiText.drawWrapped(g, font, S, UiText.Role.HEADING, name(line), x, y, w, 2);
        // a rule under the name in the potion's own colour (made readable on the page)
        g.fill(x, y + 1, x + w, y + 2, (0x90 << 24) | (nameInk & 0xFFFFFF));
        y += 5;
        boolean known = KnownRecipesView.knows(line.recipe());
        if (brew != null && brew.flavorText() != null && !brew.flavorText().isEmpty()) {
            y = UiText.drawWrapped(g, font, S, UiText.Role.BODY, Component.translatable(brew.flavorText()), x, y, w, 4) + 3;
        }
        if (!known) {
            List<String> books = KnownRecipesView.taughtIn(line.recipe());
            Component where = books.isEmpty() ? Component.translatable(KEY + "learn.elsewhere")
                    : Component.translatable(KEY + "learn.book", itemName(books.get(0)));
            UiText.drawWrapped(g, font, S, UiText.Role.BODY, where, x, y, w, 4);
            return;
        }
        g.drawString(font, Component.translatable(KEY + "ingredients"), x, y, S.ink2(), false);
        y += 11;
        for (String entry : line.ingredients()) {
            if (y + 16 > bottom - 22) {
                g.drawString(font, "…", x, y, S.ink3(), false);
                y += 10;
                break;
            }
            String[] parts = entry.split("\\*");
            ItemStack stack = stack(parts[0]);
            int count = parts.length > 1 ? parseInt(parts[1]) : 1;
            if (!stack.isEmpty()) {
                g.renderItem(stack, x, y);
            }
            Component label = Component.literal(count + " × ").append(stack.isEmpty()
                    ? Component.literal(parts[0]) : stack.getHoverName());
            UiText.drawFit(g, font, S, label, x + 19, y + 4, w - 19, S.ink());
            y += 17;
        }
        y += 2;
        Component pot = Component.translatable(RKEY + "tier." + line.tier().toLowerCase(Locale.ROOT));
        y = UiText.drawWrapped(g, font, S, UiText.Role.BODY, Component.translatable(KEY + "cauldron", pot), x, y, w, 2) + 2;
        if (!line.catalyst().isEmpty()) {
            String[] at = line.catalyst().split("@");
            String[] window = at.length > 1 ? at[1].split("-") : new String[0];
            int from = window.length > 1 ? Math.round(parseFloat(window[0]) * 100) : 0;
            int to = window.length > 1 ? Math.round(parseFloat(window[1]) * 100) : 0;
            UiText.drawWrapped(g, font, S, UiText.Role.BODY,
                    Component.translatable(KEY + "catalyst", itemName(at[0]), from, to), x, y, w, 2);
        }
    }

    private static ItemStack stack(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null || !BuiltInRegistries.ITEM.containsKey(parsed) ? ItemStack.EMPTY
                : new ItemStack(BuiltInRegistries.ITEM.getValue(parsed));
    }

    private static Component itemName(String idOrPath) {
        String id = idOrPath.contains(":") ? idOrPath : "wizards_and_beasts:" + idOrPath;
        ItemStack stack = stack(id);
        return stack.isEmpty() ? Component.literal(idOrPath) : stack.getHoverName();
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static float parseFloat(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int x = BookChrome.leftPageX(left) + PAD;
        int w = BookChrome.pageW(W) - 2 * PAD - 4;
        int y0 = top + LIST_TOP;
        if (event.x() >= x - 2 && event.x() < x + w + 2 && event.y() >= y0 - 1) {
            int r = (int) Math.floor((event.y() - y0 + 1) / ROW_H);
            int i = scroll + r;
            if (r >= 0 && r < visibleRows() && i < entries().size()) {
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
