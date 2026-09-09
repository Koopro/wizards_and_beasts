package at.koopro.wizardsandbeasts.client.currency.gui;

import at.koopro.wizardsandbeasts.client.currency.state.ClientDragotQuoteState;
import at.koopro.wizardsandbeasts.client.currency.state.ClientVaultDataState;
import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.gui.util.UiContrast;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
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
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Gringotts, as a ledger.
 *
 * <p>Cut on the {@code goblin_ledger} material, which was generated with the note that "the vault is
 * the one screen where that reads" and then had no consumer.
 *
 * <h2>What was wrong with the old one</h2>
 *
 * <p><strong>Eleven hardcoded English strings and no lang keys at all.</strong> The title was a
 * {@code Component.literal}, the column headings, every one of the fifteen button labels, and the
 * two totals were {@code String.format} calls. It was the only screen in the mod with no
 * translatable text whatsoever.
 *
 * <p><strong>Fifteen vanilla grey buttons in four loose rows</strong> on vanilla's own
 * {@code demo_background}, ruled with {@code #444466} — a cold blue-grey that appears nowhere else
 * in this mod. A goblin bank drawn in Minecraft's demo chrome.
 *
 * <p><strong>The layout gave no reason for anything.</strong> Six deposit and withdraw buttons in
 * two rows, disconnected from the balances they move; a bulk pair; four exchange buttons; a Dragot
 * row. Nothing said which button touched which coin.
 *
 * <h2>The rewrite</h2>
 *
 * <p>Two ruled columns, VAULT and WALLET, and the coins are <em>rows</em>: each denomination shows
 * both balances with its own deposit and withdraw controls on the same line. A button now sits next
 * to the number it changes.
 *
 * <p>The coin colours stay — they carry meaning, and {@code WizardsPalette} deliberately excludes
 * semantic colour — but they go through {@link UiContrast} first. This material's face is pale
 * ruled paper and the three of them measure 1.37, 1.58 and 2.36 : 1 against it; the helper keeps
 * each hue and moves only its luminance, so gold, silver and bronze stay distinguishable from one
 * another and become readable at the same time.
 *
 * <p>The payload contract is untouched: every control still sends one {@link Action}.
 */
public class GringottsScreen extends Screen {

    private static final GuiSkin SKIN = GuiSkin.GOBLIN_LEDGER;

    private static final int PANEL_W = WizardsMetrics.PANEL_WIDE_W;
    private static final int PANEL_H = WizardsMetrics.PANEL_WIDE_H;
    private static final int FRAME = WizardsMetrics.PANEL_SPRITE_BORDER;
    private static final int PAD = WizardsMetrics.SPACE_M;

    // ── The ladder, in design-space pixels of the 400x256 panel ──
    //
    // Every rung goes through `layout.s()`, and that is load-bearing rather than tidy. Mixing
    // scaled and unscaled offsets is what broke the first cut of this screen: with the header fixed
    // at 32 while the rows scaled, `Layout.panel`'s 0.72 floor put the Dragot buttons at y 170 and
    // the totals at y 148 — the foot of the ledger drawn through the middle of the desk.
    //
    // Two columns rather than one tall stack, which is the deeper fix. Seven bands stacked in a
    // 256-tall panel do not survive 0.72 whatever the arithmetic; the panel is 400 wide and the
    // first cut used none of it. Balances and their totals read down the left, the two desks that
    // trade one thing for another sit on the right.
    private static final int HEADER_H = 32;

    /** Left column: the vault and wallet balances, and what they come to. */
    private static final int COLUMN_HEAD_Y = 32;
    private static final int COIN_TABLE_Y = 48;
    private static final int COIN_ROW_H = 30;
    private static final int BULK_ROW_Y = 146;
    private static final int FOOT_RULE_Y = 186;
    private static final int TOTALS_Y = 192;

    /** Right column: the two exchange desks, each a 2x2 of buttons under its heading. */
    private static final int EXCHANGE_LABEL_Y = 32;
    private static final int EXCHANGE_ROW_Y = 48;
    private static final int DRAGOT_LABEL_Y = 126;
    private static final int DRAGOT_ROW_Y = 142;
    private static final int DESK_ROW_H = 34;

    /**
     * Control height, in design pixels.
     *
     * <p>26 rather than 16, and chosen rather than floored: the nine-slice sprites are 32 cut on 8,
     * so a control below {@code 2 * 8 + 2} has two borders and no face. A {@code Math.max} floor
     * would have fixed that and broken the ladder, because a floored button is taller than the rung
     * that reserved space for it. 26 x 0.72 is 19, so the smallest scale this layout can reach
     * still clears the sprite's own minimum and no floor is needed.
     */
    private static final int BTN_H = 26;

    private static final int MOVE_BTN_W = 22;
    private static final int WIDE_BTN_W = 74;
    private static final int SMALL_BTN_W = 56;

    /** Gold, silver and bronze. Semantic, and lifted onto this material by {@link UiContrast}. */
    private final int goldInk;
    private final int silverInk;
    private final int bronzeInk;
    /** A rate that has moved in the holder's favour, and one that has not. */
    private final int riseInk;
    private final int fallInk;

    private GuiScaleHelper.Layout layout;
    private int panelX;
    private int panelY;

    public GringottsScreen() {
        super(Component.translatable("gui.wizards_and_beasts.gringotts.title"));
        this.goldInk = UiContrast.readableOn(0xD4AF37, SKIN.base(), UiContrast.AA_TEXT);
        this.silverInk = UiContrast.readableOn(0x8A8A8A, SKIN.base(), UiContrast.AA_TEXT);
        this.bronzeInk = UiContrast.readableOn(0xCD7F32, SKIN.base(), UiContrast.AA_TEXT);
        this.riseInk = UiContrast.readableOn(0x2E7D32, SKIN.base(), UiContrast.AA_TEXT);
        this.fallInk = UiContrast.readableOn(0xB3261E, SKIN.base(), UiContrast.AA_TEXT);
    }

    /**
     * One coin, as it appears on a ledger line.
     *
     * <p>Bundles the four things a row needs — its name, its ink, and the two actions that move it —
     * so the three rows are one loop rather than three near-identical blocks. The old screen built
     * six buttons in two rows from six copies of the same three lines.
     */
    private enum Coin {
        GALLEON("galleon", Action.DEPOSIT_GALLEON, Action.WITHDRAW_GALLEON),
        SICKLE("sickle", Action.DEPOSIT_SICKLE, Action.WITHDRAW_SICKLE),
        KNUT("knut", Action.DEPOSIT_KNUT, Action.WITHDRAW_KNUT);

        private final String key;
        private final Action deposit;
        private final Action withdraw;

        Coin(String key, Action deposit, Action withdraw) {
            this.key = key;
            this.deposit = deposit;
            this.withdraw = withdraw;
        }

        Component label() {
            return Component.translatable("gui.wizards_and_beasts.gringotts.coin." + key);
        }
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.panel(width, height, PANEL_W, PANEL_H);
        panelX = layout.panelX();
        panelY = layout.panelY();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int btnH = layout.s(BTN_H);
        int leftX = leftColX();
        int leftW = colW();
        int rightX = rightColX();

        // Deposit and withdraw sit on each coin's own line, at that line's right-hand end. A button
        // beside the number it changes, which the old two rows of "Deposit Galleon" never were.
        Coin[] coins = Coin.values();
        int moveW = layout.s(MOVE_BTN_W);
        for (int i = 0; i < coins.length; i++) {
            Coin coin = coins[i];
            int rowY = coinRowY(i);
            int right = leftX + leftW;
            addRenderableWidget(moveButton(right - 2 * moveW - WizardsMetrics.SPACE_XS,
                    rowY, moveW, btnH, "deposit", coin, coin.deposit));
            addRenderableWidget(moveButton(right - moveW, rowY, moveW, btnH, "withdraw",
                    coin, coin.withdraw));
        }

        int bulkY = y(BULK_ROW_Y);
        int wideW = layout.s(WIDE_BTN_W);
        addRenderableWidget(ledgerButton(leftX, bulkY, wideW, btnH,
                "gui.wizards_and_beasts.gringotts.deposit_all", () -> send(Action.DEPOSIT_ALL, 1)));
        addRenderableWidget(ledgerButton(leftX + leftW - wideW, bulkY, wideW, btnH,
                "gui.wizards_and_beasts.gringotts.withdraw_all", () -> send(Action.WITHDRAW_ALL, 1)));

        // Exchange: four fixed integer ratios inside one currency, as a 2x2 so each pair of
        // opposite directions reads as a pair.
        int deskStep = colW() - layout.s(SMALL_BTN_W);
        int deskRow = layout.s(DESK_ROW_H);
        addExchange(rightX, y(EXCHANGE_ROW_Y), btnH, 0, "knuts_to_sickle",
                Action.EXCHANGE_KNUTS_TO_SICKLE, CurrencyHelper.KNUTS_PER_SICKLE, deskStep);
        addExchange(rightX, y(EXCHANGE_ROW_Y), btnH, 1, "sickle_to_knuts",
                Action.EXCHANGE_SICKLE_TO_KNUTS, CurrencyHelper.KNUTS_PER_SICKLE, deskStep);
        addExchange(rightX, y(EXCHANGE_ROW_Y) + deskRow, btnH, 0, "sickles_to_galleon",
                Action.EXCHANGE_SICKLES_TO_GALLEON, CurrencyHelper.SICKLES_PER_GALLEON, deskStep);
        addExchange(rightX, y(EXCHANGE_ROW_Y) + deskRow, btnH, 1, "galleon_to_sickles",
                Action.EXCHANGE_GALLEON_TO_SICKLES, CurrencyHelper.SICKLES_PER_GALLEON, deskStep);

        // Dragots: a rate that moves and a bank that takes a cut. Its own desk, because that is not
        // the same operation as the four fixed ratios above it.
        int smallW = layout.s(SMALL_BTN_W);
        int dragotY = y(DRAGOT_ROW_Y);
        addRenderableWidget(ledgerButton(rightX, dragotY, smallW, btnH,
                "gui.wizards_and_beasts.dragot.sell", () -> send(Action.SELL_DRAGOTS, 1), 1));
        addRenderableWidget(ledgerButton(rightX + deskStep, dragotY, smallW, btnH,
                "gui.wizards_and_beasts.dragot.sell", () -> send(Action.SELL_DRAGOTS, 10), 10));
        addRenderableWidget(ledgerButton(rightX, dragotY + deskRow, smallW, btnH,
                "gui.wizards_and_beasts.dragot.buy", () -> send(Action.BUY_DRAGOTS, 1), 1));
        addRenderableWidget(ledgerButton(rightX + deskStep, dragotY + deskRow, smallW, btnH,
                "gui.wizards_and_beasts.dragot.buy", () -> send(Action.BUY_DRAGOTS, 10), 10));
    }

    private void addExchange(int x, int y, int h, int slot, String key,
                             Action action, int ratio, int step) {
        ThemedButton button = ThemedButton.skinned(x + step * slot, y, layout.s(SMALL_BTN_W), h,
                Component.translatable("gui.wizards_and_beasts.gringotts.exchange." + key, ratio),
                () -> send(action, 1), SKIN);
        // The label is a ratio like "29K -> 1S". What that means belongs in a tooltip rather than in
        // a button too narrow to hold the sentence.
        button.setTooltip(Tooltip.create(Component.translatable(
                "gui.wizards_and_beasts.gringotts.exchange." + key + ".tip", ratio)));
        addRenderableWidget(button);
    }

    /**
     * A deposit or withdraw control for one coin.
     *
     * <p>The glyph is an arrow, because there is no room for a word beside three balances; the coin
     * and the direction are both named in the tooltip. The old screen spent 80 pixels per button on
     * "Deposit Galleon" and still did not say which line it belonged to.
     */
    private ThemedButton moveButton(int x, int y, int w, int h, String direction, Coin coin,
                                    Action action) {
        ThemedButton button = ThemedButton.skinned(x, y, w, h,
                Component.translatable("gui.wizards_and_beasts.gringotts." + direction + ".glyph"),
                () -> send(action, 1), SKIN);
        button.setTooltip(Tooltip.create(Component.translatable(
                "gui.wizards_and_beasts.gringotts." + direction + ".tip", coin.label())));
        return button;
    }

    private ThemedButton ledgerButton(int x, int y, int w, int h, String key, Runnable action) {
        return ThemedButton.skinned(x, y, w, h, Component.translatable(key), action, SKIN);
    }

    private ThemedButton ledgerButton(int x, int y, int w, int h, String key, Runnable action,
                                      Object arg) {
        return ThemedButton.skinned(x, y, w, h, Component.translatable(key, arg), action, SKIN);
    }

    private void send(Action action, int amount) {
        ClientPacketDistributor.sendToServer(new VaultActionC2SPayload(action.ordinal(), amount));
    }

    // ── The ladder, resolved. Shared by init and render so a label cannot drift off its control ──

    /** A design-space y, placed on screen. The only way this screen turns a rung into a pixel. */
    private int y(int designY) {
        return panelY + layout.s(designY);
    }

    private int coinRowY(int index) {
        return y(COIN_TABLE_Y + index * COIN_ROW_H);
    }

    /** Each column gets half the content width, less half a gutter. */
    private int colW() {
        return (layout.panelW() - 2 * (FRAME + PAD) - WizardsMetrics.SPACE_XXL) / 2;
    }

    private int leftColX() {
        return panelX + FRAME + PAD;
    }

    private int rightColX() {
        return leftColX() + colW() + WizardsMetrics.SPACE_XXL;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderMenuBackground(graphics);

        int panelW = layout.panelW();
        int panelH = layout.panelH();
        McStylePanel.drawSkinPanel(graphics, SKIN, panelX, panelY, panelW, panelH);
        McStylePanel.drawSkinSeal(graphics, SKIN,
                panelX + panelW - FRAME - McStylePanel.SEAL_SIZE, panelY + FRAME);
        McStylePanel.drawSkinDivider(graphics, SKIN, panelX + FRAME,
                y(HEADER_H) - WizardsMetrics.DIVIDER_H, panelW - 2 * FRAME);
        graphics.drawCenteredString(font, this.title, panelX + panelW / 2,
                panelY + FRAME + WizardsMetrics.SPACE_S, SKIN.ink());

        renderCoinTable(graphics, leftColX(), colW());
        renderTotals(graphics, leftColX(), colW());
        renderDesks(graphics, rightColX(), colW());

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** The three coins, with both balances and the controls that move them, one per line. */
    private void renderCoinTable(GuiGraphics graphics, int x, int w) {
        PlayerVaultData vault = ClientVaultDataState.get();
        Inventory inventory = Minecraft.getInstance().player == null
                ? null : Minecraft.getInstance().player.getInventory();

        // Column origins, and the width each figure has before the next thing starts. The wallet
        // column used to be set from the right edge and ran straight under the two move buttons,
        // which is 44px of this row that is not text.
        int moveW = layout.s(MOVE_BTN_W);
        int buttonsX = x + w - 2 * moveW - WizardsMetrics.SPACE_XS;
        int vaultCol = x + layout.s(56);
        int walletCol = x + layout.s(104);
        int labelW = vaultCol - x - WizardsMetrics.SPACE_S;
        int vaultW = walletCol - vaultCol - WizardsMetrics.SPACE_S;
        int walletW = buttonsX - walletCol - WizardsMetrics.SPACE_S;
        int headY = y(COLUMN_HEAD_Y);

        graphics.drawString(font, Component.translatable("gui.wizards_and_beasts.gringotts.vault"),
                vaultCol, headY, SKIN.muted(), false);
        graphics.drawString(font, Component.translatable("gui.wizards_and_beasts.gringotts.wallet"),
                walletCol, headY, SKIN.muted(), false);

        Coin[] coins = Coin.values();
        for (int i = 0; i < coins.length; i++) {
            Coin coin = coins[i];
            int rowY = coinRowY(i);
            int textY = rowY + WizardsMetrics.SPACE_S;
            int ink = coinInk(coin);

            // A ruled line under each entry, which is what makes this read as a ledger rather than
            // as three rows of numbers.
            McStylePanel.drawSkinDivider(graphics, SKIN, x,
                    coinRowY(i + 1) - WizardsMetrics.DIVIDER_H, w);

            // Fitted, not clipped. A vault can hold six figures and this column is not six figures
            // wide; GuiText shrinks the glyphs rather than letting the number run into the next one.
            GuiText.drawFitted(graphics, font, coin.label().getString(), x, textY, labelW, ink);
            GuiText.drawFitted(graphics, font, count(vaultAmount(vault, coin)).getString(),
                    vaultCol, textY, vaultW, ink);
            GuiText.drawFitted(graphics, font, count(walletAmount(inventory, coin)).getString(),
                    walletCol, textY, walletW, ink);
        }
    }

    /**
     * The right column: two desks that each trade one thing for another.
     *
     * <p>Separated from the balances by the gutter rather than by a rule. A ledger's left side is
     * what you have and its right side is what you can do with it; a line between them would say
     * they are two screens.
     */
    private void renderDesks(GuiGraphics graphics, int x, int w) {
        graphics.drawString(font, Component.translatable("gui.wizards_and_beasts.gringotts.exchange"),
                x, y(EXCHANGE_LABEL_Y), SKIN.muted(), false);

        int labelY = y(DRAGOT_LABEL_Y);
        if (!ClientDragotQuoteState.hasQuote()) {
            graphics.drawString(font,
                    Component.translatable("gui.wizards_and_beasts.gringotts.dragot_no_quote"),
                    x, labelY, SKIN.muted(), false);
            return;
        }
        // Quoted by the server and pinned for the visit; this only ever draws it. A rate computed
        // here would be a rate the till does not honour.
        float drift = ClientDragotQuoteState.drift();
        graphics.drawString(font, Component.translatable(
                        "gui.wizards_and_beasts.gringotts.dragot_board",
                        String.format("%.3f", ClientDragotQuoteState.rate()),
                        String.format("%+.1f", drift),
                        ClientDragotQuoteState.purse()),
                x, labelY, drift >= 0.0f ? riseInk : fallInk, false);
    }

    /** Both purses in one figure each, on a ruled foot. */
    private void renderTotals(GuiGraphics graphics, int x, int w) {
        PlayerVaultData vault = ClientVaultDataState.get();
        Inventory inventory = Minecraft.getInstance().player == null
                ? null : Minecraft.getInstance().player.getInventory();
        int walletG = walletAmount(inventory, Coin.GALLEON);
        int walletS = walletAmount(inventory, Coin.SICKLE);
        int walletK = walletAmount(inventory, Coin.KNUT);

        int footY = y(TOTALS_Y);
        McStylePanel.drawSkinDivider(graphics, SKIN, x, y(FOOT_RULE_Y), w);

        graphics.drawString(font, Component.translatable(
                        "gui.wizards_and_beasts.gringotts.total_vault",
                        CurrencyHelper.formatCurrency(vault.getGalleons(), vault.getSickles(),
                                vault.getKnuts()),
                        count(vault.getTotalInKnuts())),
                x, footY, SKIN.ink(), false);
        graphics.drawString(font, Component.translatable(
                        "gui.wizards_and_beasts.gringotts.total_wallet",
                        CurrencyHelper.formatCurrency(walletG, walletS, walletK),
                        count(CurrencyHelper.toKnuts(walletG, walletS, walletK))),
                x, footY + layout.s(WizardsMetrics.LINE_TIGHT), SKIN.muted(), false);
    }

    private int coinInk(Coin coin) {
        return switch (coin) {
            case GALLEON -> goldInk;
            case SICKLE -> silverInk;
            case KNUT -> bronzeInk;
        };
    }

    private static long vaultAmount(PlayerVaultData vault, Coin coin) {
        return switch (coin) {
            case GALLEON -> vault.getGalleons();
            case SICKLE -> vault.getSickles();
            case KNUT -> vault.getKnuts();
        };
    }

    private static int walletAmount(Inventory inventory, Coin coin) {
        if (inventory == null) {
            return 0;
        }
        return CurrencyHelper.countItem(inventory, switch (coin) {
            case GALLEON -> CurrencyItemRegistry.GALLEON.get();
            case SICKLE -> CurrencyItemRegistry.SICKLE.get();
            case KNUT -> CurrencyItemRegistry.KNUT.get();
        });
    }

    /**
     * A grouped figure.
     *
     * <p>{@code String.format("%,d", …)} without a Locale groups by the JVM's default, which is the
     * machine's and not the game's. Left to the language file, where a translator can say whether
     * their locale groups at all.
     */
    private static Component count(long amount) {
        return Component.translatable("gui.wizards_and_beasts.gringotts.amount", amount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
