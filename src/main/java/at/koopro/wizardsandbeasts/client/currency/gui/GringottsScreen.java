package at.koopro.wizardsandbeasts.client.currency.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.currency.state.ClientDragotQuoteState;
import at.koopro.wizardsandbeasts.client.currency.state.ClientVaultDataState;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui.Rect;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.ui.UiTooltips;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedChip;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.network.currency.VaultActionC2SPayload;
import at.koopro.wizardsandbeasts.network.currency.VaultActionC2SPayload.Action;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The counter at Gringotts: the bank's white marble round two ledger pages.
 *
 * <p>The <b>vault page</b> is the account. Each coin is a row -- its real item icon, its name, what the
 * vault holds, then two arrows, then what the wallet holds -- so the arrows sit <em>between</em> the two
 * figures they move coins between: ◀ into the vault, ▶ out to the wallet. How many each arrow moves is the
 * amount picker on the page's heading (1, 10, 64, or all of that coin); the server has always taken an
 * amount and caps it itself. Under the rows: everything in or out at once, and both totals.
 *
 * <p>The <b>counter page</b> is everything else the goblins do at the desk: the four coin exchanges
 * (inside the vault), the Dragot rate with its drift and the player's purse, Dragot trades, and the door
 * to the bank's wider business.
 *
 * <p>Every rectangle comes from {@link GringottsLayout}, in native pixels; below 360px of content the two
 * pages become tabs. Controls that cannot do anything right now are greyed from the synced vault and the
 * player's inventory -- the server still checks every action itself. Art from {@code tools/gui_chrome.py}
 * ({@code artefact/gringotts/}, {@code sprites/gringotts/}).
 *
 * <p>Replaced 2026-10-22: a scaled 400x256 ledger sheet with fifteen buttons in fixed design widths, whose
 * labels and arrows no longer fitted at 720p, and which was rebuilt from scratch on every vault sync.
 */
public class GringottsScreen extends Screen {

    /** Inks and seal of the goblins' ledger; the page material is this screen's own. */
    private static final GuiSkin SKIN = GuiSkin.GOBLIN_LEDGER;
    private static final UiSurface S = UiSurface.of(SKIN);
    private static final String BUTTONS = "gringotts";
    private static final String KEY = "gui.wizards_and_beasts.gringotts.";
    private static final Identifier MARBLE = art("marble");
    private static final Identifier LEDGER = art("ledger");

    /** The ledger stock, for clamping inks onto it (gui_chrome.py LEDGER). */
    private static final int PAPER = 0xFFEFE4C6;
    private static final int INK = SKIN.ink();
    private static final int INK_2 = SKIN.muted();
    private static final int HEADING = UiContrast.readableOn(0x6A1E1A, PAPER, UiContrast.AA_TEXT);
    /** Engraved title on the marble. */
    private static final int MARBLE_INK = 0xFF3A2E24;
    /** The ledger's blue ruling, under each coin row. */
    private static final int RULING = 0x709FB2C4;
    private static final int BUTTON_INK = 0xFF2A1512;
    private static final int BUTTON_INK_OFF = 0xFF7A6A55;
    private static final int[] AMOUNTS = {1, 10, 64, -1};
    /** The server's per-action cap ({@code VaultActionC2SPayload.MAX_TRANSACTION_AMOUNT}). */
    private static final int MAX_AMOUNT = 4096;

    private enum Coin {
        GALLEON("galleon", 0xD4AF37, Action.DEPOSIT_GALLEON, Action.WITHDRAW_GALLEON),
        SICKLE("sickle", 0x8A8A8A, Action.DEPOSIT_SICKLE, Action.WITHDRAW_SICKLE),
        KNUT("knut", 0xCD7F32, Action.DEPOSIT_KNUT, Action.WITHDRAW_KNUT);

        final String key;
        final int ink;
        final Action deposit;
        final Action withdraw;

        Coin(String key, int hue, Action deposit, Action withdraw) {
            this.key = key;
            this.ink = UiContrast.readableOn(hue, PAPER, UiContrast.AA_TEXT);
            this.deposit = deposit;
            this.withdraw = withdraw;
        }

        Item item() {
            return switch (this) {
                case GALLEON -> CurrencyItemRegistry.GALLEON.get();
                case SICKLE -> CurrencyItemRegistry.SICKLE.get();
                case KNUT -> CurrencyItemRegistry.KNUT.get();
            };
        }

        Component plural() {
            return Component.translatable(KEY + "coin." + key);
        }

        Component name(long amount) {
            return amount == 1 ? Component.translatable(KEY + "coin." + key + ".one") : plural();
        }
    }

    private GringottsLayout layout;
    /** Index into {@link #AMOUNTS}. Survives vault syncs: the screen is kept, not rebuilt, when one lands. */
    private int amount = 0;
    private boolean counterTab = false;

    private final List<ThemedButton> deposit = new ArrayList<>(3);
    private final List<ThemedButton> withdraw = new ArrayList<>(3);
    private final List<ThemedButton> exchange = new ArrayList<>(4);
    private final List<ThemedButton> dragot = new ArrayList<>(4);
    private final List<net.minecraft.client.gui.components.AbstractWidget> vaultWidgets = new ArrayList<>();
    private final List<net.minecraft.client.gui.components.AbstractWidget> counterWidgets = new ArrayList<>();
    private @Nullable ThemedButton depositAll;
    private @Nullable ThemedButton withdrawAll;
    private int tooltipAmount = Integer.MIN_VALUE;

    public GringottsScreen() {
        super(Component.translatable(KEY + "title"));
    }

    private static Identifier art(String name) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "textures/gui/artefact/gringotts/" + name + ".png");
    }

    // ── widgets ───────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        int[] chipW = new int[AMOUNTS.length];
        for (int i = 0; i < AMOUNTS.length; i++) {
            chipW[i] = ThemedChip.widthFor(font, amountLabel(i)) + 2;
        }
        int countW = font.width("99,999");
        layout = GringottsLayout.of(width, height, new GringottsLayout.Measure(chipW,
                Math.max(countW, font.width(Component.translatable(KEY + "vault"))),
                Math.max(countW, font.width(Component.translatable(KEY + "wallet"))),
                Math.max(ThemedChip.widthFor(font, Component.translatable(KEY + "tab.vault")),
                        ThemedChip.widthFor(font, Component.translatable(KEY + "tab.counter"))) + 8));

        deposit.clear();
        withdraw.clear();
        exchange.clear();
        dragot.clear();
        vaultWidgets.clear();
        counterWidgets.clear();
        tooltipAmount = Integer.MIN_VALUE;

        if (layout.mode() == GringottsLayout.Mode.TABBED) {
            Rect a = layout.tabs().get(0);
            Rect b = layout.tabs().get(1);
            addRenderableWidget(ThemedChip.skinned(a.x(), a.y(), a.w(), a.h(), Component.translatable(KEY + "tab.vault"),
                    () -> setTab(false), () -> !counterTab, BUTTONS, BUTTON_INK, BUTTON_INK_OFF));
            addRenderableWidget(ThemedChip.skinned(b.x(), b.y(), b.w(), b.h(), Component.translatable(KEY + "tab.counter"),
                    () -> setTab(true), () -> counterTab, BUTTONS, BUTTON_INK, BUTTON_INK_OFF));
        }

        GringottsLayout.Vault v = layout.vault();
        for (int i = 0; i < AMOUNTS.length; i++) {
            int index = i;
            Rect r = v.amounts().get(i);
            ThemedChip chip = ThemedChip.skinned(r.x(), r.y(), r.w(), r.h(), amountLabel(i),
                    () -> amount = index, () -> amount == index, BUTTONS, BUTTON_INK, BUTTON_INK_OFF);
            chip.setTooltip(Tooltip.create(Component.translatable(KEY + "amount.tip")));
            vaultWidgets.add(addRenderableWidget(chip));
        }
        Coin[] coins = Coin.values();
        for (int i = 0; i < coins.length; i++) {
            Coin coin = coins[i];
            deposit.add(vault(button(v.deposit().get(i), Component.translatable(KEY + "deposit.glyph"),
                    () -> move(coin, true))));
            withdraw.add(vault(button(v.withdraw().get(i), Component.translatable(KEY + "withdraw.glyph"),
                    () -> move(coin, false))));
        }
        depositAll = vault(button(v.depositAll(), Component.translatable(KEY + "deposit_all"),
                () -> send(Action.DEPOSIT_ALL, 1)));
        withdrawAll = vault(button(v.withdrawAll(), Component.translatable(KEY + "withdraw_all"),
                () -> send(Action.WITHDRAW_ALL, 1)));

        GringottsLayout.Counter c = layout.counter();
        exchangeButton(c.exchange().get(0), "knuts_to_sickle", Action.EXCHANGE_KNUTS_TO_SICKLE, CurrencyHelper.KNUTS_PER_SICKLE);
        exchangeButton(c.exchange().get(1), "sickle_to_knuts", Action.EXCHANGE_SICKLE_TO_KNUTS, CurrencyHelper.KNUTS_PER_SICKLE);
        exchangeButton(c.exchange().get(2), "sickles_to_galleon", Action.EXCHANGE_SICKLES_TO_GALLEON, CurrencyHelper.SICKLES_PER_GALLEON);
        exchangeButton(c.exchange().get(3), "galleon_to_sickles", Action.EXCHANGE_GALLEON_TO_SICKLES, CurrencyHelper.SICKLES_PER_GALLEON);
        dragot.add(counter(button(c.dragot().get(0), Component.translatable("gui.wizards_and_beasts.dragot.sell", 1),
                () -> send(Action.SELL_DRAGOTS, 1))));
        dragot.add(counter(button(c.dragot().get(1), Component.translatable("gui.wizards_and_beasts.dragot.sell", 10),
                () -> send(Action.SELL_DRAGOTS, 10))));
        dragot.add(counter(button(c.dragot().get(2), Component.translatable("gui.wizards_and_beasts.dragot.buy", 1),
                () -> send(Action.BUY_DRAGOTS, 1))));
        dragot.add(counter(button(c.dragot().get(3), Component.translatable("gui.wizards_and_beasts.dragot.buy", 10),
                () -> send(Action.BUY_DRAGOTS, 10))));
        ThemedButton business = counter(button(c.business(), Component.translatable(KEY + "business"),
                () -> at.koopro.wizardsandbeasts.client.location.LocationScreen.request(
                        Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "gringotts"))));
        business.setTooltip(Tooltip.create(Component.translatable(KEY + "business.tip")));

        applyTab();
        refresh();
    }

    private ThemedButton button(Rect r, Component label, Runnable action) {
        return addRenderableWidget(ThemedButton.skinned(r.x(), r.y(), r.w(), r.h(), label, action, BUTTONS, null, 0,
                BUTTON_INK, BUTTON_INK_OFF));
    }

    private ThemedButton vault(ThemedButton b) {
        vaultWidgets.add(b);
        return b;
    }

    private ThemedButton counter(ThemedButton b) {
        counterWidgets.add(b);
        return b;
    }

    private void exchangeButton(Rect r, String key, Action action, int ratio) {
        ThemedButton b = counter(button(r, Component.translatable(KEY + "exchange." + key, ratio), () -> send(action, 1)));
        b.setTooltip(Tooltip.create(Component.translatable(KEY + "exchange." + key + ".tip", ratio)));
        exchange.add(b);
    }

    private Component amountLabel(int index) {
        return AMOUNTS[index] < 0 ? Component.translatable(KEY + "amount.all") : Component.literal(Integer.toString(AMOUNTS[index]));
    }

    private void setTab(boolean counter) {
        counterTab = counter;
        applyTab();
    }

    /** In the tabbed arrangement only the chosen page's controls exist on screen. */
    private void applyTab() {
        boolean tabbed = layout.mode() == GringottsLayout.Mode.TABBED;
        for (var w : vaultWidgets) {
            w.visible = !tabbed || !counterTab;
        }
        for (var w : counterWidgets) {
            w.visible = !tabbed || counterTab;
        }
    }

    // ── actions ───────────────────────────────────────────────────────────

    /**
     * One arrow press: the chosen amount, or for "all" what the wallet carries (deposit) or the vault holds
     * (withdraw), capped as the server caps it. A press with nothing to move still sends one, so the server
     * answers with its own reason rather than the click doing nothing.
     */
    private void move(Coin coin, boolean in) {
        long n = resolveAmount(coin, in);
        send(in ? coin.deposit : coin.withdraw, (int) Math.max(1, Math.min(MAX_AMOUNT, n)));
    }

    private long resolveAmount(Coin coin, boolean in) {
        int chosen = AMOUNTS[amount];
        if (chosen > 0) {
            return chosen;
        }
        return in ? wallet(coin) : vaultCount(ClientVaultDataState.get(), coin);
    }

    private void send(Action action, int n) {
        ClientPacketDistributor.sendToServer(new VaultActionC2SPayload(action.ordinal(), n));
    }

    @Override
    public void tick() {
        super.tick();
        refresh();
    }

    /**
     * Greys what cannot act, from the synced vault and the inventory, and keeps the arrows' tooltips saying
     * how many they move. Mirrors the server's own checks (a Knut withdrawal may break larger coins; an
     * exchange works inside the vault); the server still decides.
     */
    private void refresh() {
        PlayerVaultData vault = ClientVaultDataState.get();
        Coin[] coins = Coin.values();
        boolean anyWallet = false;
        for (int i = 0; i < coins.length; i++) {
            Coin coin = coins[i];
            long inWallet = wallet(coin);
            anyWallet |= inWallet > 0;
            deposit.get(i).active = inWallet > 0;
            withdraw.get(i).active = switch (coin) {
                case GALLEON -> vault.getGalleons() > 0;
                case SICKLE -> vault.getSickles() > 0 || vault.getGalleons() > 0;
                case KNUT -> vault.getTotalInKnuts() > 0;
            };
        }
        if (depositAll != null && withdrawAll != null) {
            depositAll.active = anyWallet;
            withdrawAll.active = vault.getTotalInKnuts() > 0;
        }
        exchange.get(0).active = vault.getKnuts() >= CurrencyHelper.KNUTS_PER_SICKLE;
        exchange.get(1).active = vault.getSickles() >= 1;
        exchange.get(2).active = vault.getSickles() >= CurrencyHelper.SICKLES_PER_GALLEON;
        exchange.get(3).active = vault.getGalleons() >= 1;
        boolean quoted = ClientDragotQuoteState.hasQuote();
        dragot.get(0).active = quoted && ClientDragotQuoteState.purse() >= 1;
        dragot.get(1).active = quoted && ClientDragotQuoteState.purse() >= 10;
        dragot.get(2).active = quoted;
        dragot.get(3).active = quoted;

        // Arrow tooltips depend on the amount and on the counts; rebuilt only when either moved.
        int stamp = amount * 31 + (int) (vault.getTotalInKnuts() * 7 + wallet(Coin.GALLEON) * 3 + wallet(Coin.SICKLE) * 5
                + wallet(Coin.KNUT));
        if (stamp == tooltipAmount) {
            return;
        }
        tooltipAmount = stamp;
        for (int i = 0; i < coins.length; i++) {
            Coin coin = coins[i];
            deposit.get(i).setTooltip(Tooltip.create(arrowTip(coin, true)));
            withdraw.get(i).setTooltip(Tooltip.create(arrowTip(coin, false)));
        }
    }

    private Component arrowTip(Coin coin, boolean in) {
        String dir = in ? "deposit" : "withdraw";
        long have = in ? wallet(coin) : vaultCount(ClientVaultDataState.get(), coin);
        if (have <= 0 && !(coin != Coin.GALLEON && !in && ClientVaultDataState.get().getTotalInKnuts() > 0)) {
            return Component.translatable(KEY + dir + ".none", coin.plural());
        }
        if (AMOUNTS[amount] < 0) {
            return Component.translatable(KEY + dir + ".tip_all", coin.plural(), count(Math.min(MAX_AMOUNT, have)));
        }
        int n = AMOUNTS[amount];
        return Component.translatable(KEY + dir + ".tip_n", n, coin.name(n));
    }

    // ── painting ──────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderMenuBackground(g);
        Rect p = layout.panel();
        McStylePanel.drawNineSliceTiled(g, MARBLE, p.x(), p.y(), p.w(), p.h(),
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
        renderTitle(g);

        boolean tabbed = layout.mode() == GringottsLayout.Mode.TABBED;
        if (!tabbed || !counterTab) {
            page(g, layout.vaultPage());
            renderVault(g, mouseX, mouseY);
        }
        if (!tabbed || counterTab) {
            if (!tabbed) {
                page(g, layout.counterPage());
            }
            renderCounter(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** The bank's name engraved on the marble, a gold double rule under it, the goblins' seal at its end. */
    private void renderTitle(GuiGraphics g) {
        Rect c = layout.content();
        UiText.drawCentered(g, font, S, title, c.x() + c.w() / 2, layout.titleY(), MARBLE_INK);
        int y = layout.ruleY();
        g.fill(c.x() + 18, y, c.right() - 18, y + 1, 0xFF8F6522);
        g.fill(c.x() + 18, y + 2, c.right() - 18, y + 3, 0xFFC9973A);
        McStylePanel.drawSkinSeal(g, SKIN, c.right() - McStylePanel.SEAL_SIZE, layout.titleY() - 5);
    }

    private static void page(GuiGraphics g, Rect r) {
        McStylePanel.drawNineSliceTiled(g, LEDGER, r.x(), r.y(), r.w(), r.h(),
                WizardsMetrics.PANEL_SPRITE_SIZE, WizardsMetrics.PANEL_SPRITE_BORDER);
    }

    private void renderVault(GuiGraphics g, int mouseX, int mouseY) {
        GringottsLayout.Vault v = layout.vault();
        PlayerVaultData vault = ClientVaultDataState.get();
        Rect first = v.rows().get(0);

        g.drawString(font, Component.translatable(KEY + "page.vault"), first.x(), v.headingY(), HEADING, false);
        UiText.drawRight(g, font, S, Component.translatable(KEY + "vault"), v.vaultRight(), v.columnHeadY(), INK_2);
        UiText.drawRight(g, font, S, Component.translatable(KEY + "wallet"), v.walletRight(), v.columnHeadY(), INK_2);

        Coin[] coins = Coin.values();
        for (int i = 0; i < coins.length; i++) {
            Coin coin = coins[i];
            Rect row = v.rows().get(i);
            int textY = row.y() + (row.h() - font.lineHeight) / 2 + 1;
            g.fill(row.x(), row.bottom() - 1, row.right(), row.bottom(), RULING);
            g.renderItem(new ItemStack(coin.item()), v.iconX(), row.y() + (row.h() - 16) / 2);

            int nameRoom = v.nameRight() - v.nameX();
            String name = coin.plural().getString();
            if (font.width(name) > nameRoom) {
                name = Component.translatable(KEY + "coin." + coin.key + ".short").getString();
            }
            g.drawString(font, name, v.nameX(), textY, coin.ink, false);
            long inVault = vaultCount(vault, coin);
            long inWallet = wallet(coin);
            UiText.drawRight(g, font, S, Component.literal(count(inVault)), v.vaultRight(), textY,
                    inVault > 0 ? INK : INK_2);
            UiText.drawRight(g, font, S, Component.literal(count(inWallet)), v.walletRight(), textY,
                    inWallet > 0 ? INK : INK_2);

            if (new Rect(row.x(), row.y(), v.nameRight() - row.x(), row.h()).contains(mouseX, mouseY)) {
                UiTooltips.show(g, font, coin.plural(), List.of(
                        Component.translatable(KEY + "row.tip", count(inVault), count(inWallet))), mouseX, mouseY);
            }
        }

        // The ledger's foot: a double rule, then what the vault and the wallet come to.
        int x = first.x();
        int w = first.w();
        g.fill(x, v.footRuleY(), x + w, v.footRuleY() + 1, INK_2);
        g.fill(x, v.footRuleY() + 2, x + w, v.footRuleY() + 3, INK_2);
        Inventory inv = inventory();
        int wg = wallet(Coin.GALLEON);
        int ws = wallet(Coin.SICKLE);
        int wk = wallet(Coin.KNUT);
        totalLine(g, x, v.totalsY(), w, Component.translatable(KEY + "total.vault"),
                CurrencyHelper.formatCurrency(vault.getGalleons(), vault.getSickles(), vault.getKnuts()),
                vault.getTotalInKnuts(), mouseX, mouseY);
        if (inv != null) {
            totalLine(g, x, v.totalsY() + 11, w, Component.translatable(KEY + "total.wallet"),
                    CurrencyHelper.formatCurrency(wg, ws, wk), CurrencyHelper.toKnuts(wg, ws, wk), mouseX, mouseY);
        }
    }

    private void totalLine(GuiGraphics g, int x, int y, int w, Component label, String value, long knuts,
                           int mouseX, int mouseY) {
        g.drawString(font, label, x, y, INK_2, false);
        String shown = UiText.fit(font, value, w - font.width(label) - 8);
        g.drawString(font, shown, x + w - font.width(shown), y, INK, false);
        if (mouseX >= x && mouseX < x + w && mouseY >= y - 1 && mouseY < y + 10) {
            UiTooltips.show(g, font, Component.literal(value), List.of(
                    Component.translatable(KEY + "total.tip", count(knuts))), mouseX, mouseY);
        }
    }

    private void renderCounter(GuiGraphics g) {
        GringottsLayout.Counter c = layout.counter();
        Rect in = layout.counterPage().inset(7);
        g.drawString(font, Component.translatable(KEY + "exchange_head"), in.x(), c.exchangeHeadY(), HEADING, false);

        g.renderItem(new ItemStack(CurrencyItemRegistry.DRAGOT.get()), in.x(), c.dragotHeadY() - 4);
        g.drawString(font, Component.translatable(KEY + "dragot.head"), in.x() + 20, c.dragotHeadY(), HEADING, false);
        if (!ClientDragotQuoteState.hasQuote()) {
            UiText.drawWrapped(g, font, S, UiText.Role.LABEL, Component.translatable(KEY + "dragot.none"),
                    in.x(), c.boardY(), in.w(), 2);
            return;
        }
        Component rate = Component.translatable(KEY + "dragot.rate",
                String.format(Locale.ROOT, "%.3f", ClientDragotQuoteState.rate()));
        g.drawString(font, rate, in.x(), c.boardY(), INK, false);
        float drift = ClientDragotQuoteState.drift();
        // The rate's move against the bank's standing rate: an arrow and a sign as well as a colour.
        Component move = Component.literal((drift >= 0 ? "▲ " : "▼ ")
                + String.format(Locale.ROOT, "%+.1f%%", drift));
        int chipX = in.right() - Ui.chipWidth(font, move);
        if (chipX > in.x() + font.width(rate) + 4) {
            Ui.chip(g, font, S, chipX, c.boardY() - 2, move, drift >= 0 ? Ui.Tone.GOOD : Ui.Tone.BAD);
        }
        g.drawString(font, Component.translatable(KEY + "dragot.purse", ClientDragotQuoteState.purse()),
                in.x(), c.boardY() + 11, INK_2, false);
    }

    // ── data ──────────────────────────────────────────────────────────────

    private static @Nullable Inventory inventory() {
        return Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
    }

    private static int wallet(Coin coin) {
        Inventory inv = inventory();
        return inv == null ? 0 : CurrencyHelper.countItem(inv, coin.item());
    }

    private static long vaultCount(PlayerVaultData vault, Coin coin) {
        return switch (coin) {
            case GALLEON -> vault.getGalleons();
            case SICKLE -> vault.getSickles();
            case KNUT -> vault.getKnuts();
        };
    }

    private static String count(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
