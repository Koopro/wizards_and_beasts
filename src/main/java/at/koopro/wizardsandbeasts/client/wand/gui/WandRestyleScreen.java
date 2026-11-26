package at.koopro.wizardsandbeasts.client.wand.gui;

import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette.GuiSkin;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.util.GuiText;
import at.koopro.wizardsandbeasts.client.gui.widget.CyclerWidget;
import at.koopro.wizardsandbeasts.wand.style.WandRestyle;
import at.koopro.wizardsandbeasts.wand.style.WandRestyle.Aspect;
import at.koopro.wizardsandbeasts.wand.style.WandRestyle.Cost;
import at.koopro.wizardsandbeasts.wand.bench.BenchRole;
import at.koopro.wizardsandbeasts.wand.style.WandRestyleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Restyling a wand at the bench: the wand drawn large on the left, a row per look on the right, the price beneath.
 *
 * <p>The large wand is a 3D model on a turntable ({@link WandPreviewRenderer}), drawn by the real wand renderer as
 * it would come out ({@link WandRestyle#apply}); dragging it turns it by hand. A change of colour, metal, grain, light
 * or shape shows on it the frame the arrow is clicked: the click is run on this side's copy of the menu at once
 * ({@link WandRestyleMenu#clickMenuButton}) and sent to the server, which runs the same rules to make the wand the
 * player takes. The wand as it is now sits small in the corner for comparison.
 */
@NullMarked
public class WandRestyleScreen extends AbstractContainerScreen<WandRestyleMenu> {

    private static final UiSurface S = UiSurface.PAGE;
    private static final GuiSkin SKIN = GuiSkin.WORKBENCH;
    private static final String KEY = "gui.wizards_and_beasts.wand_restyle.";

    private static final int SIDE = 10;
    private static final int PREVIEW_X = 10;
    private static final int PREVIEW_Y = 22;
    private static final int PREVIEW_W = 96;
    private static final int PREVIEW_H = 120;
    private static final float PREVIEW_SCALE = 6.0f;
    private static final int ROWS_X = 115;
    private static final int ROWS_Y = 24;
    private static final int ROW_H = 15;
    private static final int LABEL_W = 56;
    private static final int CYCLER_H = 14;
    private static final int COST_X = 115;
    private static final int COST_Y = 133;

    private final List<CyclerWidget<Integer>> cyclers = new ArrayList<>();
    private int[] shown = new int[0];

    public WandRestyleScreen(WandRestyleMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WandRestyleMenu.GUI_W;
        this.imageHeight = WandRestyleMenu.GUI_H;
        // Engraved on the brass plate (tools/bench_screen.py).
        this.titleLabelX = 11;
        this.titleLabelY = 7;
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
        // The restyle bench (tools/bench_screen.py): the wand turns on the baize, the choices are on the slip, the
        // wand goes in on the pad and comes out in the box.
        java.util.List<int[]> inventory = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                inventory.add(new int[]{WandRestyleMenu.INV_X + col * 18, WandRestyleMenu.INV_Y + row * 18});
            }
        }
        for (int col = 0; col < 9; col++) {
            inventory.add(new int[]{WandRestyleMenu.INV_X + col * 18, WandRestyleMenu.HOTBAR_Y});
        }
        BenchChrome.back(g, x, y, imageWidth, WandRestyleMenu.TOP_H, imageHeight, BenchChrome.TOP_RESTYLE,
                BenchRole.RESTYLE, inventory);
        if (menu.status() == WandRestyleMenu.Status.READY) {
            Ui.slotTarget(g, x + WandRestyleMenu.RESULT_X, y + WandRestyleMenu.STATION_Y, 0, WizardsPalette.GILT_LIGHT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, WizardsPalette.PAGE_INK, false);
        BenchChrome.tagLabels(g, font, imageWidth, BenchRole.RESTYLE);
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

    /** The wand as it will come out, as a turning 3D model, and as it is now, small in the corner. */
    private void renderPreview(GuiGraphics g) {
        ItemStack wand = menu.wand();
        int x = leftPos + PREVIEW_X;
        int y = topPos + PREVIEW_Y;
        if (wand.isEmpty()) {
            GuiText.drawFitted(g, font, Component.translatable(KEY + "empty").getString(), x + 6,
                    y + PREVIEW_H / 2 - 4, PREVIEW_W - 12, 0xFFEDE4D2);
            return;
        }
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        ItemStack after = WandRestyle.apply(minecraft.level.registryAccess(), wand, menu.selection());
        at.koopro.wizardsandbeasts.wand.visual.WandLook look =
                at.koopro.wizardsandbeasts.client.wand.WandLookCache.look(minecraft.level.registryAccess(), after);
        float tip = look.drawnLength() * FRAME_SCALE / 16.0f * TIP_SHARE;
        g.submitPictureInPictureRenderState(new WandPreviewRenderState(after, yaw(), pitch, PIXELS_PER_BLOCK,
                look.elder() ? 0 : look.glint() & 0xFFFFFF, tip,
                x, y, x + PREVIEW_W, y + PREVIEW_H, null));
        if (WandRestyle.changes(menu.selection())) {
            g.drawString(font, Component.translatable(KEY + "now"), x + 3, y + 3, 0xFFEDE4D2, false);
            drawScaled(g, wand, x + 3, y + 12, 1.5f);
        }
        g.drawString(font, Component.translatable(KEY + "drag"), x + 3, y + PREVIEW_H - 11, 0xFFD6CCB8, false);
    }

    // ── turning the wand ─────────────────────────────────────────────────

    /** The wand's model length shown in a frame pose is scaled by this (wand.json "fixed"). */
    private static final float FRAME_SCALE = 0.618f;
    private static final float PIXELS_PER_BLOCK = 88.0f;
    /** The point's distance from the wand's middle, as a share of its frame-pose length; measured off the model. */
    private static final float TIP_SHARE = 0.433f;
    /** Degrees a second the turntable turns while nobody holds the wand. */
    private static final float TURN_SPEED = 30.0f;
    /** After a drag, the wand stays where it was left this long before turning again. */
    private static final long RESUME_MS = 3000L;

    private float yawBase = 30.0f;
    private float pitch = 12.0f;
    private long turntableFrom = net.minecraft.util.Util.getMillis();
    private boolean dragging;

    private float yaw() {
        long idle = net.minecraft.util.Util.getMillis() - turntableFrom;
        return dragging || idle < 0 ? yawBase : yawBase + idle / 1000.0f * TURN_SPEED;
    }

    private boolean overStage(double mx, double my) {
        int x = leftPos + PREVIEW_X;
        int y = topPos + PREVIEW_Y;
        return mx >= x && mx < x + PREVIEW_W && my >= y && my < y + PREVIEW_H;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && BenchChrome.clickTag(event.x(), event.y(), leftPos, topPos, imageWidth,
                BenchRole.RESTYLE, menu.containerId)) {
            return true;
        }
        if (event.button() == 0 && overStage(event.x(), event.y()) && !menu.wand().isEmpty()) {
            yawBase = yaw();
            dragging = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            yawBase += (float) dx * 2.5f;
            pitch = net.minecraft.util.Mth.clamp(pitch + (float) dy * 2.0f, -60.0f, 60.0f);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (dragging && event.button() == 0) {
            dragging = false;
            turntableFrom = net.minecraft.util.Util.getMillis() + RESUME_MS;
            return true;
        }
        return super.mouseReleased(event);
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
        // One column of four; past four, two columns, so every aspect changed at once still fits on the slip.
        List<Cost> costs = WandRestyle.cost(menu.selection());
        int perColumn = 4;
        int column = costs.size() <= 4 ? width - 8 : (width - 8) / 2;
        for (int i = 0; i < costs.size(); i++) {
            Cost cost = costs.get(i);
            boolean have = free || WandRestyle.held(items, cost) >= cost.count();
            int lineX = x + (i / perColumn) * column;
            int lineY = y + (i % perColumn) * 9;
            g.drawString(font, have ? "✔" : "✖", lineX, lineY, have ? 0xFF3E7A3A : 0xFFA03228, false);
            GuiText.drawFitted(g, font, cost.label().getString(), lineX + 9, lineY, column - 11,
                    have ? S.ink() : 0xFFA03228);
        }
    }
}
