package at.koopro.wizardsandbeasts.client.currency.gui;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.ScreenLayoutScaler;
import at.koopro.wizardsandbeasts.client.currency.state.ClientVaultDataState;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.network.currency.VaultActionC2SPayload;
import at.koopro.wizardsandbeasts.network.currency.VaultActionC2SPayload.Action;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;

public class GringottsScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 260;

    private static final int COL_GOLD = 0xFFD4AF37;
    private static final int COL_SILVER = 0xFFC0C0C0;
    private static final int COL_BRONZE = 0xFFCD7F32;
    private ScreenLayoutScaler layout;

    public GringottsScreen() {
        super(Component.literal("Gringotts Wizarding Bank"));
    }

    @Override
    protected void init() {
        super.init();
        layout = ScreenLayoutScaler.forScreen(width, height, PANEL_W, PANEL_H);
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();

        int px = layout.panelX();
        int py = layout.panelY();
        int btnW = layout.s(80);
        int btnH = layout.s(18);
        int colSpacing = layout.s(90);

        // Individual deposit/withdraw buttons
        int depositY = py + layout.s(82);
        addRenderableWidget(Button.builder(Component.literal("Deposit Knut"), b -> sendAction(Action.DEPOSIT_KNUT, 1))
                .bounds(px + 10, depositY, btnW, btnH).build());
        addRenderableWidget(Button.builder(Component.literal("Deposit Sickle"), b -> sendAction(Action.DEPOSIT_SICKLE, 1))
                .bounds(px + 10 + colSpacing, depositY, btnW, btnH).build());
        addRenderableWidget(Button.builder(Component.literal("Deposit Galleon"), b -> sendAction(Action.DEPOSIT_GALLEON, 1))
                .bounds(px + 10 + colSpacing * 2, depositY, btnW, btnH).build());

        int withdrawY = py + layout.s(104);
        addRenderableWidget(Button.builder(Component.literal("Withdraw Knut"), b -> sendAction(Action.WITHDRAW_KNUT, 1))
                .bounds(px + 10, withdrawY, btnW, btnH).build());
        addRenderableWidget(Button.builder(Component.literal("Withdraw Sickle"), b -> sendAction(Action.WITHDRAW_SICKLE, 1))
                .bounds(px + 10 + colSpacing, withdrawY, btnW, btnH).build());
        addRenderableWidget(Button.builder(Component.literal("Withdraw Galleon"), b -> sendAction(Action.WITHDRAW_GALLEON, 1))
                .bounds(px + 10 + colSpacing * 2, withdrawY, btnW, btnH).build());

        // Deposit All / Withdraw All bulk buttons
        int bulkY = py + layout.s(128);
        int bulkW = layout.s(130);
        addRenderableWidget(Button.builder(Component.literal("Deposit All"), b -> sendAction(Action.DEPOSIT_ALL, 1))
                .bounds(px + 10, bulkY, bulkW, btnH).build());
        addRenderableWidget(Button.builder(Component.literal("Withdraw All"), b -> sendAction(Action.WITHDRAW_ALL, 1))
                .bounds(px + layout.panelW() - layout.s(10) - bulkW, bulkY, bulkW, btnH).build());

        // Exchange buttons
        int exchangeY = py + layout.s(168);
        int exBtnW = layout.s(60);
        addRenderableWidget(Button.builder(Component.literal("29K\u21921S"), b -> sendAction(Action.EXCHANGE_KNUTS_TO_SICKLE, 1))
                .bounds(px + 10, exchangeY, exBtnW, btnH).build());
        addRenderableWidget(Button.builder(Component.literal("1S\u219229K"), b -> sendAction(Action.EXCHANGE_SICKLE_TO_KNUTS, 1))
                .bounds(px + layout.s(10 + 65), exchangeY, exBtnW, btnH).build());
        addRenderableWidget(Button.builder(Component.literal("17S\u21921G"), b -> sendAction(Action.EXCHANGE_SICKLES_TO_GALLEON, 1))
                .bounds(px + layout.s(10 + 130), exchangeY, exBtnW, btnH).build());
        addRenderableWidget(Button.builder(Component.literal("1G\u219217S"), b -> sendAction(Action.EXCHANGE_GALLEON_TO_SICKLES, 1))
                .bounds(px + layout.s(10 + 195), exchangeY, exBtnW, btnH).build());
    }

    private void sendAction(Action action, int amount) {
        ClientPacketDistributor.sendToServer(new VaultActionC2SPayload(action.ordinal(), amount));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);
        int px = layout.panelX();
        int py = layout.panelY();
        int panelW = layout.panelW();
        int panelH = layout.panelH();

        McStylePanel.drawTexturedPanel(graphics, px, py, panelW, panelH);

        // Title
        graphics.drawCenteredString(font, "Gringotts Wizarding Bank", px + panelW / 2, py + layout.s(8), COL_GOLD);
        graphics.fill(px + layout.s(5), py + layout.s(22), px + panelW - layout.s(5), py + layout.s(23), 0xFF444466);

        PlayerVaultData vault = ClientVaultDataState.get();

        // --- Vault Balance (left side) ---
        int balanceY = py + layout.s(28);
        graphics.drawString(font, "Vault Balance", px + layout.s(10), balanceY, 0xFFCCCCCC, false);
        graphics.drawString(font, String.format("Galleons: %,d", vault.getGalleons()), px + layout.s(14), balanceY + layout.s(13), COL_GOLD, false);
        graphics.drawString(font, String.format("Sickles:  %,d", vault.getSickles()), px + layout.s(14), balanceY + layout.s(24), COL_SILVER, false);
        graphics.drawString(font, String.format("Knuts:    %,d", vault.getKnuts()), px + layout.s(14), balanceY + layout.s(35), COL_BRONZE, false);

        // --- Wallet (inventory) Balance (right side) ---
        int walletX = px + panelW / 2 + layout.s(10);
        graphics.drawString(font, "Wallet", walletX, balanceY, 0xFFCCCCCC, false);

        Inventory inv = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getInventory() : null;
        int walletG = inv != null ? CurrencyHelper.countItem(inv, CurrencyItemRegistry.GALLEON.get()) : 0;
        int walletS = inv != null ? CurrencyHelper.countItem(inv, CurrencyItemRegistry.SICKLE.get()) : 0;
        int walletK = inv != null ? CurrencyHelper.countItem(inv, CurrencyItemRegistry.KNUT.get()) : 0;

        graphics.drawString(font, String.format("Galleons: %,d", walletG), walletX + 4, balanceY + 13, COL_GOLD, false);
        graphics.drawString(font, String.format("Sickles:  %,d", walletS), walletX + 4, balanceY + 24, COL_SILVER, false);
        graphics.drawString(font, String.format("Knuts:    %,d", walletK), walletX + 4, balanceY + 35, COL_BRONZE, false);

        // Divider between balance and buttons
        graphics.fill(px + layout.s(5), py + layout.s(77), px + panelW - layout.s(5), py + layout.s(78), 0xFF444466);

        // "Exchange" label
        graphics.drawString(font, "Exchange", px + layout.s(10), py + layout.s(154), 0xFFCCCCCC, false);

        // Divider above total
        graphics.fill(px + layout.s(5), py + layout.s(192), px + panelW - layout.s(5), py + layout.s(193), 0xFF444466);

        // Total value with denomination breakdown
        long total = vault.getTotalInKnuts();
        String formatted = CurrencyHelper.formatCurrency(vault.getGalleons(), vault.getSickles(), vault.getKnuts());
        graphics.drawCenteredString(font, "Total: " + formatted + String.format(" (%,d Knuts)", total),
                px + panelW / 2, py + panelH - layout.s(40), 0xFF888888);

        // Wallet total
        long walletTotal = CurrencyHelper.toKnuts(walletG, walletS, walletK);
        String walletFormatted = CurrencyHelper.formatCurrency(walletG, walletS, walletK);
        graphics.drawCenteredString(font, "Wallet: " + walletFormatted + String.format(" (%,d Knuts)", walletTotal),
                px + panelW / 2, py + panelH - layout.s(26), 0xFF666666);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
