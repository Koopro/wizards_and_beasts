package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.gui.widget.CyclerWidget;
import at.koopro.wizardsandbeasts.client.gui.widget.ThemedButton;
import at.koopro.wizardsandbeasts.wand.style.SwitchBenchRolePayload;
import at.koopro.wizardsandbeasts.wand.style.WandRestyle;
import at.koopro.wizardsandbeasts.wand.style.WandRestyle.Aspect;
import at.koopro.wizardsandbeasts.wand.style.WandRestyle.Cost;
import at.koopro.wizardsandbeasts.wand.style.WandRestyleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Restyling a wand at the bench: the wand drawn large on the left, a row per look on the right, the price beneath.
 *
 * <p>The large wand is the real wand renderer drawing the wand as it would come out ({@link WandRestyle#apply}), so
 * a change of colour, metal, grain, light or shape shows on it the frame the arrow is clicked: the click is run on
 * this side's copy of the menu at once ({@link WandRestyleMenu#clickMenuButton}) and sent to the server, which runs
 * the same rules to make the wand the player takes. The wand as it is now sits small in the corner for comparison.
 */
@NullMarked
public class WandRestyleScreen extends AbstractContainerScreen<WandRestyleMenu> {

    private static final UiSurface S = UiSurface.PAGE;
    private static final GuiSkin SKIN = GuiSkin.WORKBENCH;
    private static final String KEY = "gui.wizards_and_beasts.wand_restyle.";

    private static final int SIDE = 10;
    private static final int TITLE_Y = 8;
    private static final int PREVIEW_X = 10;
    private static final int PREVIEW_Y = 22;
    private static final int PREVIEW_W = 96;
    private static final int PREVIEW_H = 120;
    private static final float PREVIEW_SCALE = 6.0f;
    private static final int ROWS_X = 112;
    private static final int ROWS_Y = 24;
    private static final int ROW_H = 16;
    private static final int LABEL_W = 56;
    private static final int CYCLER_H = 14;
    private static final int COST_X = 112;
    private static final int COST_Y = 140;
    /** Mid tone behind the wand: black swallowed the dark woods, parchment the pale ones. */
    private static final int STAGE = 0xFF857A6B;
    private static final int STAGE_EDGE = 0xFF5E5547;

    private final List<CyclerWidget<Integer>> cyclers = new ArrayList<>();
    private int[] shown = new int[0];

    public WandRestyleScreen(WandRestyleMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WandRestyleMenu.GUI_W;
        this.imageHeight = WandRestyleMenu.GUI_H;
        this.titleLabelX = SIDE;
        this.titleLabelY = TITLE_Y;
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        cyclers.clear();
        shown = menu.selection();
        int width = imageWidth - ROWS_X - LABEL_W - SIDE;
        for (Aspect aspect : Aspect.values()) {
            List<Integer> indices = IntStream.range(0, WandRestyle.size(aspect)).boxed().toList();
            CyclerWidget<Integer> cycler = new CyclerWidget<>(indices, shown[aspect.ordinal()],
                    i -> WandRestyle.label(aspect, i).getString(),
                    i -> choose(aspect, i)).skin(SKIN);
            cycler.setBounds(leftPos + ROWS_X + LABEL_W, topPos + ROWS_Y + aspect.ordinal() * ROW_H, width, CYCLER_H);
            cycler.buttons().forEach(this::addRenderableWidget);
            cyclers.add(cycler);
        }
        addRenderableWidget(ThemedButton.skinned(leftPos + imageWidth - SIDE - 56, topPos + 5, 56, 12,
                Component.translatable(KEY + "to_refine"),
                () -> ClientPacketDistributor.sendToServer(new SwitchBenchRolePayload(menu.containerId, false)),
                SKIN));
    }

    /** Applies the choice here at once, for the preview, and asks the server to do the same. */
    private void choose(Aspect aspect, int index) {
        int id = aspect.ordinal() * WandRestyleMenu.STRIDE + index;
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        menu.clickMenuButton(minecraft.player, id);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        rebuild();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // The server holds the choice; follow it when it moves (a take resets every row to "keep").
        if (!Arrays.equals(shown, menu.selection())) {
            rebuild();
        }
    }

    // ── drawing ──────────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        Ui.panel(g, S, x, y, imageWidth, imageHeight);
        Ui.divider(g, S, x + SIDE - 4, y + 18, imageWidth - 2 * (SIDE - 4));

        // The stage the wand is shown on.
        g.fill(x + PREVIEW_X - 1, y + PREVIEW_Y - 1, x + PREVIEW_X + PREVIEW_W + 1, y + PREVIEW_Y + PREVIEW_H + 1,
                STAGE_EDGE);
        g.fill(x + PREVIEW_X, y + PREVIEW_Y, x + PREVIEW_X + PREVIEW_W, y + PREVIEW_Y + PREVIEW_H, STAGE);

        Ui.slot(g, x + WandRestyleMenu.WAND_X, y + WandRestyleMenu.STATION_Y);
        Ui.resultSlot(g, x + WandRestyleMenu.RESULT_X, y + WandRestyleMenu.STATION_Y, 4);
        int midY = y + WandRestyleMenu.STATION_Y + 8;
        int arrow = menu.status() == WandRestyleMenu.Status.READY ? WizardsPalette.GILT_DARK : S.ink2();
        int from = x + WandRestyleMenu.WAND_X + 22;
        int tip = x + WandRestyleMenu.RESULT_X - 12 - Ui.ARROW;
        g.fill(from, midY - 1, tip + 2, midY + 2, arrow);
        Ui.arrow(g, 1, tip, midY - 3, arrow);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Ui.slot(g, x + WandRestyleMenu.INV_X + col * 18, y + WandRestyleMenu.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            Ui.slot(g, x + WandRestyleMenu.INV_X + col * 18, y + WandRestyleMenu.HOTBAR_Y);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, WizardsPalette.PAGE_INK, false);
        int[] pick = menu.selection();
        for (Aspect aspect : Aspect.values()) {
            int rowY = ROWS_Y + aspect.ordinal() * ROW_H;
            UiText.draw(g, font, S, Component.translatable(aspect.key()), ROWS_X, rowY + 3, S.ink2());
            int swatch = WandRestyle.swatch(aspect, pick[aspect.ordinal()]);
            if (swatch >= 0) {
                int sx = ROWS_X + LABEL_W - 9;
                g.fill(sx - 1, rowY + 3, sx + 7, rowY + 11, S.ink());
                g.fill(sx, rowY + 4, sx + 6, rowY + 10, 0xFF000000 | swatch);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        cyclers.forEach(c -> c.renderLabel(g));
        renderPreview(g);
        renderPrice(g);
        renderTooltip(g, mouseX, mouseY);
    }

    /** The wand as it will come out, large, and as it is now, small in the corner. */
    private void renderPreview(GuiGraphics g) {
        ItemStack wand = menu.wand();
        int x = leftPos + PREVIEW_X;
        int y = topPos + PREVIEW_Y;
        if (wand.isEmpty()) {
            GuiText.drawFitted(g, font, Component.translatable(KEY + "empty").getString(), x + 6,
                    y + PREVIEW_H / 2 - 4, PREVIEW_W - 12, 0xFFEDE4D2);
            return;
        }
        ItemStack after = minecraft == null || minecraft.level == null ? wand
                : WandRestyle.apply(minecraft.level.registryAccess(), wand, menu.selection());
        int size = Math.round(16 * PREVIEW_SCALE);
        int sx = x + (PREVIEW_W - size) / 2;
        int sy = y + (PREVIEW_H - size) / 2 + 6;
        drawScaled(g, after, sx, sy, PREVIEW_SCALE);
        renderCoreLight(g, after, sx, sy, size);
        if (WandRestyle.changes(menu.selection())) {
            g.drawString(font, Component.translatable(KEY + "now"), x + 3, y + 3, 0xFFEDE4D2, false);
            drawScaled(g, wand, x + 3, y + 12, 1.5f);
        }
    }

    /**
     * Where the point of a 13-inch wand lands in the preview square, as a share of its side, measured off the drawn
     * wand (the slot pose turns it up and to the right). A longer or shorter wand reaches further or less far.
     */
    private static final float TIP_X = 0.747f;
    private static final float TIP_Y = 0.156f;
    private static final float REFERENCE_DRAWN = 28.4f;

    /**
     * The core's light at the wand's point, with the odd mote drifting off it, in the light the wand will show in a
     * hand (WandIdleEffects). Drawn here because the wand model has no lit tip of its own since the tips were retired,
     * and a light chosen with nothing to show for it is a choice made blind.
     */
    private void renderCoreLight(GuiGraphics g, ItemStack wand, int sx, int sy, int size) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        at.koopro.wizardsandbeasts.wand.visual.WandLook look =
                at.koopro.wizardsandbeasts.client.wand.WandLookCache.look(minecraft.level.registryAccess(), wand);
        int rgb = look.glint() & 0xFFFFFF;
        if (rgb == 0 || look.elder()) {
            return;
        }
        float reach = look.drawnLength() / REFERENCE_DRAWN;
        int cx = sx + Math.round(size * (0.5f + (TIP_X - 0.5f) * reach));
        int cy = sy + Math.round(size * (0.5f + (TIP_Y - 0.5f) * reach));
        float pulse = 0.75f + 0.25f * (float) Math.sin(net.minecraft.util.Util.getMillis() / 300.0);
        int[] radius = {7, 5, 3, 1};
        int[] alpha = {40, 70, 120, 230};
        for (int i = 0; i < radius.length; i++) {
            int a = Math.round(alpha[i] * pulse);
            g.fill(cx - radius[i], cy - radius[i], cx + radius[i] + 1, cy + radius[i] + 1, a << 24 | rgb);
        }
        // Three motes on their own slow loops, falling away from the point the way they leave a held wand.
        long now = net.minecraft.util.Util.getMillis();
        for (int i = 0; i < 3; i++) {
            float t = ((now + i * 900L) % 2700L) / 2700.0f;
            int mx = cx + Math.round((float) Math.sin(i * 2.1 + t * 3.0) * 4);
            int my = cy + 3 + Math.round(t * 22);
            int a = Math.round(220 * (1.0f - t));
            g.fill(mx, my, mx + 2, my + 2, a << 24 | rgb);
        }
    }

    private static void drawScaled(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.renderItem(stack, 0, 0);
        g.pose().popMatrix();
    }

    /** What taking the wand costs, line by line, each marked had or short; or why nothing is coming out. */
    private void renderPrice(GuiGraphics g) {
        int x = leftPos + COST_X;
        int y = topPos + COST_Y;
        int width = imageWidth - COST_X - SIDE;
        WandRestyleMenu.Status status = menu.status();
        if (status == WandRestyleMenu.Status.NO_WAND || status == WandRestyleMenu.Status.NO_CHANGE) {
            GuiText.drawFitted(g, font, Component.translatable(KEY + "status."
                    + status.name().toLowerCase(java.util.Locale.ROOT)).getString(), x, y + 9, width, S.ink2());
            return;
        }
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        boolean free = minecraft.player.getAbilities().instabuild;
        List<ItemStack> items = new ArrayList<>();
        Inventory inv = minecraft.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            items.add(inv.getItem(i));
        }
        List<Cost> costs = WandRestyle.cost(menu.selection());
        for (int i = 0; i < costs.size(); i++) {
            Cost cost = costs.get(i);
            boolean have = free || WandRestyle.held(items, cost) >= cost.count();
            int lineY = y + i * 9;
            g.drawString(font, have ? "✔" : "✖", x, lineY, have ? 0xFF3E7A3A : 0xFFA03228, false);
            GuiText.drawFitted(g, font, cost.label().getString(), x + 10, lineY, width - 10,
                    have ? S.ink() : 0xFFA03228);
        }
    }
}
