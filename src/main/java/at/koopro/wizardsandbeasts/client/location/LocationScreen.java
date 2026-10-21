package at.koopro.wizardsandbeasts.client.location;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.location.LocationView;
import at.koopro.wizardsandbeasts.market.DealClass;
import at.koopro.wizardsandbeasts.market.MarketLine;
import at.koopro.wizardsandbeasts.market.MarketTrade;
import at.koopro.wizardsandbeasts.network.location.LocationActionC2SPayload;
import at.koopro.wizardsandbeasts.network.location.LocationActionC2SPayload.Action;
import at.koopro.wizardsandbeasts.owl.career.track.CareerView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * One screen for every location's board: standing and what the next tier asks, the services sold there, the
 * contracts in hand and today's offers.
 *
 * <p>A readout plus buttons, like the Career tab it borrows its lines from. Everything drawn is the
 * {@link LocationView} the server built; a button sends an intent ({@link LocationActionC2SPayload}) and the server
 * answers with a fresh view, which {@link #receive} drops into the open screen. A location's {@code skin} picks the
 * material (the goblin ledger for Gringotts, the workbench for Ollivanders).
 */
@NullMarked
public final class LocationScreen extends Screen {

    private static final String KEY = "location.wizards_and_beasts.gui.";
    private static final int MAX_W = 300;
    private static final int MAX_H = 260;
    private static final int PAD = 8;
    private static final int ROW_H = 9;
    private static final int BUTTON_H = 11;
    private static final int BAR_H = 4;

    private record Button(int x, int y, int w, int h, Action action, String argument) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private LocationView view;
    private final List<Button> buttons = new ArrayList<>();
    private float scroll;
    private int contentH;
    private int clipTop;
    private int clipBottom;

    private LocationScreen(LocationView view) {
        super(view.name());
        this.view = view;
    }

    /** A view arrived: open it when asked to, or refresh the board already open for the same place. */
    public static void receive(LocationView view, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof LocationScreen screen && screen.view.id().equals(view.id())) {
            screen.view = view;
            return;
        }
        if (open) {
            mc.setScreen(new LocationScreen(view));
        }
    }

    /** Asks the server for a location's board (the Gringotts screen's "bank business"). */
    public static void request(net.minecraft.resources.Identifier location) {
        send(Action.OPEN, location, "");
    }

    private UiSurface surface() {
        for (WizardsPalette.GuiSkin skin : WizardsPalette.GuiSkin.values()) {
            if (skin.folder().equals(view.skin())) {
                return UiSurface.of(skin);
            }
        }
        return UiSurface.PAGE;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        UiSurface s = surface();
        int w = Math.min(MAX_W, width - 16);
        int h = Math.min(MAX_H, height - 16);
        int x0 = (width - w) / 2;
        int y0 = (height - h) / 2;
        Ui.panel(g, s, x0, y0, w, h);

        int x = x0 + PAD;
        int innerW = w - 2 * PAD;
        Component tierOf = Component.translatable(KEY + "tier_of", view.tierIndex() + 1, view.tierCount());
        int top = Ui.title(g, font, s, view.name(), x, y0 + PAD, innerW, tierOf);

        buttons.clear();
        clipTop = top;
        clipBottom = y0 + h - PAD;
        float maxScroll = Math.max(0, contentH - (clipBottom - clipTop));
        scroll = Mth.clamp(scroll, 0f, maxScroll);
        g.enableScissor(x0 + 2, clipTop, x0 + w - 2, clipBottom);
        int y = clipTop - (int) scroll;
        int start = y;
        y = standing(g, s, x, y, innerW);
        y = services(g, s, x, y + 4, innerW, mouseX, mouseY);
        y = market(g, s, x, y + 4, innerW, mouseX, mouseY);
        y = contracts(g, s, x, y + 4, innerW, mouseX, mouseY);
        g.disableScissor();
        contentH = y - start + 4;
    }

    private int standing(GuiGraphics g, UiSurface s, int x, int y, int w) {
        if (!view.present()) {
            y = paragraph(g, x, y, w, Component.translatable(KEY + "away"), s.bad());
        }
        draw(g, view.tier(), x, y, w, s.ink());
        y += ROW_H + 1;
        if (view.nextRep() < 0) {
            draw(g, Component.translatable(KEY + "top_tier"), x, y, w, s.good());
            return y + ROW_H;
        }
        String rep = view.rep() + " / " + view.nextRep();
        draw(g, Component.translatable(KEY + "reputation"), x, y, w - font.width(rep) - 4, s.ink2());
        g.drawString(font, rep, x + w - font.width(rep), y, s.ink(), false);
        y += ROW_H;
        g.fill(x, y, x + w, y + BAR_H, s.well());
        int filled = Math.round(Mth.clamp(view.rep() / (float) Math.max(1, view.nextRep()), 0f, 1f) * w);
        if (filled > 0) {
            g.fill(x, y, x + filled, y + BAR_H, s.accent());
        }
        y += BAR_H + 3;
        if (!view.goals().isEmpty()) {
            draw(g, Component.translatable(KEY + "next_tier", view.nextTier()), x, y, w, s.ink2());
            y += ROW_H;
            for (CareerView.GoalLine goal : view.goals()) {
                String count = Math.min(goal.progress(), goal.target()) + "/" + goal.target();
                int colour = goal.met() ? s.good() : s.ink();
                draw(g, Component.literal((goal.met() ? "✔ " : "• ") + goal.text().getString()), x + 2, y,
                        w - font.width(count) - 6, colour);
                g.drawString(font, count, x + w - font.width(count), y, colour, false);
                y += ROW_H;
            }
        }
        return y;
    }

    private int services(GuiGraphics g, UiSurface s, int x, int y, int w, int mx, int my) {
        if (view.services().isEmpty()) {
            return y;
        }
        y = Ui.sectionHeader(g, font, s, x, y, w, Component.translatable(KEY + "services"), null);
        for (LocationView.ServiceLine line : view.services()) {
            Component label = Component.translatable(KEY + "use");
            int bw = font.width(label) + 8;
            draw(g, line.name(), x, y, w - bw - 4, line.unlocked() ? s.ink() : s.disabled());
            button(g, s, x + w - bw, y - 1, label, Action.USE_SERVICE, line.id().toString(),
                    line.unlocked() && line.available() && view.present(), mx, my);
            y += ROW_H + 2;
            y = paragraph(g, x + 4, y, w - 4, line.detail(), line.unlocked() ? s.ink3() : s.disabled());
            y += 2;
        }
        return y;
    }

    /**
     * The trader's book: what it buys (sell one, or all you carry) and what it sells (buy one, or eight). Prices are
     * the server's for the next unit; a deal re-prices on the server, so a stale line can only be refused, never
     * underpaid.
     */
    private int market(GuiGraphics g, UiSurface s, int x, int y, int w, int mx, int my) {
        if (view.market().isEmpty()) {
            return y;
        }
        if (view.illicit()) {
            y = paragraph(g, x, y, w, Component.translatable(KEY + "illicit"), s.bad()) + 4;
        }
        boolean buyingHeader = false;
        boolean sellingHeader = false;
        for (MarketLine line : view.market()) {
            if (line.buying() && !buyingHeader) {
                y = Ui.sectionHeader(g, font, s, x, y, w, Component.translatable(KEY + "buys"), null);
                buyingHeader = true;
            } else if (!line.buying() && !sellingHeader) {
                y = Ui.sectionHeader(g, font, s, x, y + 2, w, Component.translatable(KEY + "sells"), null);
                sellingHeader = true;
            }
            y = marketLine(g, s, x, y, w, line, mx, my);
        }
        return y;
    }

    private int marketLine(GuiGraphics g, UiSurface s, int x, int y, int w, MarketLine line, int mx, int my) {
        // A good valued by the piece (an artefact's worth, a wand's wood) has no price until one is carried.
        String price = line.unitKnuts() > 0 ? CurrencyHelper.formatFromKnuts(line.unitKnuts())
                : Component.translatable(KEY + "by_worth").getString();
        String mark = switch (line.deal()) {
            case LEGAL -> "";
            case RESTRICTED -> "✦ ";
            default -> "⚠ ";
        };
        int nameColour = !line.unlocked() ? s.disabled() : line.deal() == DealClass.LEGAL ? s.ink()
                : line.deal() == DealClass.RESTRICTED ? s.warn() : s.bad();
        draw(g, Component.literal(mark + line.name().getString()), x, y, w - font.width(price) - 6, nameColour);
        g.drawString(font, price, x + w - font.width(price), y, line.unlocked() ? s.ink() : s.disabled(), false);
        y += ROW_H;
        Component detail;
        if (line.buying()) {
            detail = Component.translatable(KEY + "carried", line.count());
            if (line.inDemand()) {
                detail = Component.literal(detail.getString() + " · "
                        + Component.translatable(KEY + "in_demand").getString());
            }
        } else {
            detail = line.note();
        }
        if (line.saturation() >= 25) {
            detail = Component.literal((detail.getString().isEmpty() ? "" : detail.getString() + " · ")
                    + Component.translatable(KEY + (line.buying() ? "glutted" : "scarce"), line.saturation())
                    .getString());
        }
        if (!line.unlocked()) {
            detail = line.note();
        }
        if (!detail.getString().isEmpty()) {
            y = paragraph(g, x + 4, y, w - 4, detail, line.inDemand() ? s.good() : s.ink3());
        }
        int bx = x + 4;
        boolean usable = line.available() && view.present();
        if (line.buying()) {
            bx = button(g, s, bx, y, Component.translatable(KEY + "sell_one"), Action.SELL, line.good() + "|1",
                    usable, mx, my) + 4;
            button(g, s, bx, y, Component.translatable(KEY + "sell_all"), Action.SELL,
                    line.good() + "|" + MarketTrade.MAX_UNITS, usable && line.count() > 1, mx, my);
        } else {
            bx = button(g, s, bx, y, Component.translatable(KEY + "buy_one"), Action.BUY, line.good() + "|1",
                    usable, mx, my) + 4;
            if (line.count() < 0) {
                bx = button(g, s, bx, y, Component.translatable(KEY + "buy_eight"), Action.BUY, line.good() + "|8",
                        usable, mx, my) + 4;
            }
            if (line.dragots() > 0) {
                // The keeper takes foreign coin: one unit, paid from the pocket. The server re-prices it.
                button(g, s, bx, y, Component.translatable(KEY + "buy_dragots", line.dragots()), Action.BUY_DRAGOTS,
                        line.good() + "|1", usable, mx, my);
            }
        }
        return y + BUTTON_H + 4;
    }

    private int contracts(GuiGraphics g, UiSurface s, int x, int y, int w, int mx, int my) {
        y = Ui.sectionHeader(g, font, s, x, y, w,
                Component.translatable(KEY + "in_hand", view.active().size(), view.maxActive()), null);
        if (view.active().isEmpty()) {
            y = paragraph(g, x, y, w, Component.translatable(KEY + "none_in_hand"), s.ink3());
        }
        for (CareerView.ContractLine line : view.active()) {
            y = contract(g, s, x, y, w, line, true, mx, my);
        }
        y = Ui.sectionHeader(g, font, s, x, y + 2, w, Component.translatable(KEY + "board"), null);
        if (view.offers().isEmpty()) {
            y = paragraph(g, x, y, w, Component.translatable(KEY + "none_offered"), s.ink3());
        }
        for (CareerView.ContractLine line : view.offers()) {
            y = contract(g, s, x, y, w, line, false, mx, my);
        }
        return y;
    }

    private int contract(GuiGraphics g, UiSurface s, int x, int y, int w, CareerView.ContractLine line, boolean held,
                         int mx, int my) {
        draw(g, line.title(), x, y, w, s.ink());
        y += ROW_H;
        y = paragraph(g, x + 4, y, w - 4, line.brief(), s.ink3());
        String count = held ? Math.min(line.progress(), line.target()) + "/" + line.target() : "×" + line.target();
        draw(g, line.objective(), x + 4, y, w - font.width(count) - 8, s.ink2());
        g.drawString(font, count, x + w - font.width(count), y, line.ready() ? s.good() : s.ink2(), false);
        y += ROW_H;
        Component pays = line.knuts() > 0
                ? Component.translatable(KEY + "pays", CurrencyHelper.formatFromKnuts(line.knuts()), line.xp())
                : Component.translatable(KEY + "pays_rep", line.xp());
        Component days = Component.translatable(KEY + (held ? "days_left" : "days"), line.daysLeft());
        draw(g, Component.literal(pays.getString() + " · " + days.getString()), x + 4, y, w - 4, s.ink3());
        y += ROW_H + 1;
        int bx = x + 4;
        if (held) {
            if (line.delivery()) {
                bx = button(g, s, bx, y, Component.translatable(KEY + "turn_in"), Action.TURN_IN, line.id(),
                        line.ready() && view.present(), mx, my) + 4;
            }
            button(g, s, bx, y, Component.translatable(KEY + "abandon"), Action.ABANDON, line.id(), true, mx, my);
        } else {
            button(g, s, bx, y, Component.translatable(KEY + "accept"), Action.ACCEPT, line.id(),
                    view.present() && view.active().size() < view.maxActive(), mx, my);
        }
        return y + BUTTON_H + 4;
    }

    private int button(GuiGraphics g, UiSurface s, int x, int y, Component label, Action action, String argument,
                       boolean enabled, int mx, int my) {
        int w = font.width(label) + 8;
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + BUTTON_H
                && my >= clipTop && my < clipBottom;
        g.fill(x, y, x + w, y + BUTTON_H, hover ? s.hoverWash() | 0xFF000000 : s.well());
        g.renderOutline(x, y, w, BUTTON_H, enabled ? s.accent() : s.disabled());
        g.drawString(font, label, x + 4, y + 2, enabled ? s.ink() : s.disabled(), false);
        if (enabled && y >= clipTop && y + BUTTON_H <= clipBottom) {
            buttons.add(new Button(x, y, w, BUTTON_H, action, argument));
        }
        return x + w;
    }

    private void draw(GuiGraphics g, Component text, int x, int y, int w, int colour) {
        g.drawString(font, font.plainSubstrByWidth(text.getString(), Math.max(0, w)), x, y, colour, false);
    }

    private int paragraph(GuiGraphics g, int x, int y, int w, Component text, int colour) {
        for (FormattedCharSequence line : font.split(text, Math.max(20, w))) {
            g.drawString(font, line, x, y, colour, false);
            y += ROW_H;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() == 0) {
            for (Button b : buttons) {
                if (b.contains(event.x(), event.y())) {
                    send(b.action(), view.id(), b.argument());
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (float) (scrollY * 12.0);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void send(Action action, net.minecraft.resources.Identifier location, String argument) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(new LocationActionC2SPayload(action, location, argument));
        }
    }
}
