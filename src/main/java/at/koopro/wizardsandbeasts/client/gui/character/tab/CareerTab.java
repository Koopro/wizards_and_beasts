package at.koopro.wizardsandbeasts.client.gui.character.tab;

import at.koopro.wizardsandbeasts.client.career.ClientCareerState;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.career.CareerActionC2SPayload;
import at.koopro.wizardsandbeasts.owl.career.track.CareerView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Career tab: the career held, its rank and what the next one asks for, the contracts in hand and today's
 * board.
 *
 * <p>A readout plus buttons. Everything drawn comes from {@link ClientCareerState}, which holds the view the
 * server built; the buttons send an intent ({@link CareerActionC2SPayload}) and the server answers with a
 * fresh view. Opening the tab asks for one, so delivery counts reflect what is in the pockets now.
 */
public final class CareerTab implements CharacterTab {

    private static final String KEY = "gui.wizards_and_beasts.character_sheet.";

    private static final int COLOR_SECTION = WizardsPalette.PAGE_RUBRIC;
    private static final int COLOR_LABEL = WizardsPalette.PAGE_INK_2;
    private static final int COLOR_VALUE = WizardsPalette.PAGE_INK;
    private static final int COLOR_MUTED = WizardsPalette.PAGE_INK_3;
    private static final int COLOR_GOOD = WizardsPalette.PAGE_GOOD;
    private static final int COLOR_BAR_TRACK = WizardsPalette.PAGE_SHADE;
    private static final int COLOR_BAR_FILL = WizardsPalette.GILT_DARK;
    private static final int COLOR_BUTTON = WizardsPalette.PAGE_SHADE;
    private static final int COLOR_BUTTON_HOVER = WizardsPalette.GILT;
    private static final int COLOR_BUTTON_OFF = WizardsPalette.PAGE_INK_3;

    private static final int ROW_H = 9;
    private static final int BAR_H = 4;
    private static final int BUTTON_H = 11;

    /** A button drawn this frame, hit-tested on click in the same design space. */
    private record Button(int x, int y, int w, int h, CareerActionC2SPayload.Action action, String id) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Button> buttons = new ArrayList<>();
    private float scrollOffset = 0f;
    private int lastTotalH = 0;
    private int clipTop;
    private int clipBottom;

    @Override
    public String translationKey() {
        return KEY + "tab.career";
    }

    @Override
    public void onShown() {
        scrollOffset = 0f;
        send(CareerActionC2SPayload.Action.VIEW, "");
    }

    @Override
    public void render(@NonNull GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        buttons.clear();
        clipTop = y;
        clipBottom = y + h;
        float maxScroll = Math.max(0, lastTotalH - h);
        scrollOffset = Mth.clamp(scrollOffset, 0f, maxScroll);

        g.enableScissor(x, y, x + w, y + h);
        int cx = x + 2;
        int cy = y + 2 - (int) scrollOffset;
        int top = cy;
        int innerW = w - 4 - TabScrollbar.WIDTH;

        CareerView view = ClientCareerState.get();
        if (!ModuleManager.isEnabled(Module.OWLS)) {
            cy = paragraph(g, font, cx, cy, innerW, Component.translatable(KEY + "career.off"), COLOR_MUTED);
        } else if (view.state() == CareerView.NO_CAREER) {
            cy = paragraph(g, font, cx, cy, innerW, Component.translatable(KEY + "career.none"), COLOR_VALUE);
            cy = paragraph(g, font, cx, cy + 2, innerW, Component.translatable(KEY + "career.none_hint"), COLOR_MUTED);
        } else {
            cy = header(g, font, cx, cy, innerW, view);
            if (view.state() == CareerView.FLAT_CAREER) {
                cy = paragraph(g, font, cx, cy + 2, innerW, Component.translatable(KEY + "career.flat"), COLOR_MUTED);
            } else {
                cy = rank(g, font, cx, cy + 3, innerW, view);
                cy = contracts(g, font, cx, cy + 4, innerW, view, mouseX, mouseY);
            }
        }

        g.disableScissor();
        lastTotalH = cy - top + 4;
        TabScrollbar.draw(g, x, y, w, h, scrollOffset, lastTotalH);
    }

    private int header(GuiGraphics g, Font font, int x, int y, int w, CareerView view) {
        g.drawString(font, font.plainSubstrByWidth(view.profession().getString(), w), x, y, COLOR_SECTION, false);
        y += ROW_H + 1;
        String wageLabel = Component.translatable(KEY + "career.wage").getString() + ": ";
        g.drawString(font, wageLabel, x, y, COLOR_LABEL, false);
        String wage = CurrencyHelper.formatFromKnuts(view.wageKnuts());
        if (!view.coinPaid()) {
            wage += " " + Component.translatable(KEY + "career.no_vault").getString();
        }
        g.drawString(font, font.plainSubstrByWidth(wage, w - font.width(wageLabel)), x + font.width(wageLabel), y,
                COLOR_VALUE, false);
        return y + ROW_H;
    }

    private int rank(GuiGraphics g, Font font, int x, int y, int w, CareerView view) {
        g.drawString(font, font.plainSubstrByWidth(view.rank().getString(), w), x, y, COLOR_VALUE, false);
        String of = Component.translatable(KEY + "career.rank", view.rankIndex() + 1, view.rankCount()).getString();
        g.drawString(font, of, x + w - font.width(of), y, COLOR_MUTED, false);
        y += ROW_H + 1;
        if (view.nextXp() < 0) {
            g.drawString(font, Component.translatable(KEY + "career.max").getString(), x, y, COLOR_GOOD, false);
            return y + ROW_H;
        }
        String xpLabel = Component.translatable(KEY + "career.xp").getString();
        String xp = view.xp() + " / " + view.nextXp();
        g.drawString(font, xpLabel, x, y, COLOR_LABEL, false);
        g.drawString(font, xp, x + w - font.width(xp), y, COLOR_VALUE, false);
        y += ROW_H;
        bar(g, x, y, w, view.xp() / (float) Math.max(1, view.nextXp()));
        y += BAR_H + 3;
        if (!view.goals().isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(
                    Component.translatable(KEY + "career.next", view.nextRank()).getString(), w), x, y, COLOR_LABEL, false);
            y += ROW_H;
            for (CareerView.GoalLine goal : view.goals()) {
                String count = Math.min(goal.progress(), goal.target()) + "/" + goal.target();
                int colour = goal.met() ? COLOR_GOOD : COLOR_VALUE;
                String mark = goal.met() ? "✔ " : "• ";
                g.drawString(font, font.plainSubstrByWidth(mark + goal.text().getString(), w - font.width(count) - 4),
                        x + 2, y, colour, false);
                g.drawString(font, count, x + w - font.width(count), y, colour, false);
                y += ROW_H;
            }
        }
        return y;
    }

    private int contracts(GuiGraphics g, Font font, int x, int y, int w, CareerView view, int mx, int my) {
        g.drawString(font, Component.translatable(KEY + "career.active", view.active().size(), view.maxActive())
                .getString(), x, y, COLOR_SECTION, false);
        y += ROW_H + 1;
        if (view.active().isEmpty()) {
            y = paragraph(g, font, x, y, w, Component.translatable(KEY + "career.none_active"), COLOR_MUTED);
        }
        for (CareerView.ContractLine line : view.active()) {
            y = contract(g, font, x, y, w, line, true, view, mx, my);
        }
        y += 3;
        g.drawString(font, Component.translatable(KEY + "career.offers").getString(), x, y, COLOR_SECTION, false);
        y += ROW_H + 1;
        if (view.offers().isEmpty()) {
            y = paragraph(g, font, x, y, w, Component.translatable(KEY + "career.none_offered"), COLOR_MUTED);
        }
        for (CareerView.ContractLine line : view.offers()) {
            y = contract(g, font, x, y, w, line, false, view, mx, my);
        }
        return y;
    }

    private int contract(GuiGraphics g, Font font, int x, int y, int w, CareerView.ContractLine line, boolean held,
                         CareerView view, int mx, int my) {
        g.drawString(font, font.plainSubstrByWidth(line.title().getString(), w), x, y, COLOR_VALUE, false);
        y += ROW_H;
        y = paragraph(g, font, x + 4, y, w - 4, line.brief(), COLOR_MUTED);
        String count = held ? Math.min(line.progress(), line.target()) + "/" + line.target() : "×" + line.target();
        g.drawString(font, font.plainSubstrByWidth(line.objective().getString(), w - font.width(count) - 8),
                x + 4, y, COLOR_LABEL, false);
        g.drawString(font, count, x + w - font.width(count), y, line.ready() ? COLOR_GOOD : COLOR_LABEL, false);
        y += ROW_H;
        String days = Component.translatable(KEY + (held ? "career.days_left" : "career.days"), line.daysLeft())
                .getString();
        Component pays = line.knuts() > 0 && view.coinPaid()
                ? Component.translatable(KEY + "career.pays", CurrencyHelper.formatFromKnuts(line.knuts()), line.xp())
                : Component.translatable(KEY + "career.pays_xp", line.xp());
        g.drawString(font, font.plainSubstrByWidth(pays.getString() + " · " + days, w - 4), x + 4, y, COLOR_MUTED,
                false);
        y += ROW_H + 1;

        int bx = x + 4;
        if (held) {
            if (line.delivery()) {
                bx = button(g, font, bx, y, Component.translatable(KEY + "career.turn_in"),
                        CareerActionC2SPayload.Action.TURN_IN, line.id(), line.ready(), mx, my) + 4;
            }
            button(g, font, bx, y, Component.translatable(KEY + "career.abandon"),
                    CareerActionC2SPayload.Action.ABANDON, line.id(), true, mx, my);
        } else {
            boolean room = view.active().size() < view.maxActive();
            button(g, font, bx, y, Component.translatable(KEY + "career.accept"),
                    CareerActionC2SPayload.Action.ACCEPT, line.id(), room, mx, my);
        }
        return y + BUTTON_H + 4;
    }

    /** Draws a small parchment button and registers it for clicks; returns its right edge. */
    private int button(GuiGraphics g, Font font, int x, int y, Component label, CareerActionC2SPayload.Action action,
                       String id, boolean enabled, int mx, int my) {
        int w = font.width(label) + 8;
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + BUTTON_H;
        g.fill(x, y, x + w, y + BUTTON_H, hover ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        g.renderOutline(x, y, w, BUTTON_H, enabled ? COLOR_LABEL : COLOR_BUTTON_OFF);
        g.drawString(font, label.getString(), x + 4, y + 2, enabled ? COLOR_VALUE : COLOR_BUTTON_OFF, false);
        // A button scrolled out of the clipped area must not take clicks meant for what is drawn there.
        if (enabled && y >= clipTop && y + BUTTON_H <= clipBottom) {
            buttons.add(new Button(x, y, w, BUTTON_H, action, id));
        }
        return x + w;
    }

    private static int paragraph(GuiGraphics g, Font font, int x, int y, int w, Component text, int colour) {
        for (FormattedCharSequence line : font.split(text, Math.max(20, w))) {
            g.drawString(font, line, x, y, colour, false);
            y += ROW_H;
        }
        return y;
    }

    private static void bar(GuiGraphics g, int x, int y, int w, float fraction) {
        g.fill(x, y, x + w, y + BAR_H, COLOR_BAR_TRACK);
        int filled = Math.round(Mth.clamp(fraction, 0f, 1f) * w);
        if (filled > 0) {
            g.fill(x, y, x + filled, y + BAR_H, COLOR_BAR_FILL);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        for (Button b : buttons) {
            if (b.contains(mouseX, mouseY)) {
                send(b.action(), b.id());
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset -= (float) (delta * 10.0);
        return true;
    }

    private static void send(CareerActionC2SPayload.Action action, @Nullable String id) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(new CareerActionC2SPayload(action, id == null ? "" : id));
        }
    }
}
