package at.koopro.wizardsandbeasts.client.apparition.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.ApparitionPoint;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPointsState;
import at.koopro.wizardsandbeasts.client.gui.WizardsMetrics;
import at.koopro.wizardsandbeasts.client.gui.WizardsPalette;
import at.koopro.wizardsandbeasts.client.gui.ui.Ui;
import at.koopro.wizardsandbeasts.client.gui.ui.UiSurface;
import at.koopro.wizardsandbeasts.client.gui.ui.UiText;
import at.koopro.wizardsandbeasts.client.gui.ui.UiTooltips;
import at.koopro.wizardsandbeasts.client.gui.widget.ScrollList;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionTravelC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Destination picker for Apparition. Lists the places the player has memorised with their distance and
 * whether they are reachable from here at all, and Apparates on click or Enter.
 *
 * <p>On the night card, like the spell and ability wheels: it is opened mid-stride over the world, not
 * laid on a desk. The list scrolls — a seasoned Apparator keeps up to sixteen places, and at 22px a row
 * that is 352px against a 270px screen at GUI scale 4 on 1080p; before the scroll the bottom rows were
 * simply off the screen.
 *
 * <p>The screen is purely presentational: it sends an index, and the server re-reads the destination from
 * its own copy of the list and re-runs every Apparition gate.
 */
@NullMarked
public final class ApparitionSelectorScreen extends Screen {

    private static final String KEY = "gui." + WizardsAndBeastsMod.MODID + ".apparition_selector.";

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_W = 220;
    private static final int PAD = WizardsMetrics.SPACE_M;
    private static final int PANEL_W = LIST_W + 2 * PAD;
    /** Clearance between the panel and the screen edge when the list is cut to fit. */
    private static final int SCREEN_MARGIN = WizardsMetrics.SPACE_L;
    private static final int TEXT_INSET = 5;

    private static final UiSurface S = UiSurface.NIGHT;

    private final ScrollList list = new ScrollList(ROW_HEIGHT);
    /** The row the keyboard (or the last hover) is on; the one Enter Apparates to. */
    private int selected;
    /** Where the mouse was last frame: hover only takes the selection when the mouse actually moves. */
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private int panelX;
    private int panelY;
    private int panelH;

    public ApparitionSelectorScreen() {
        super(Component.translatable("screen." + WizardsAndBeastsMod.MODID + ".apparition_selector"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<ApparitionPoint> points() {
        return ClientApparitionPointsState.points();
    }

    /** True when the point is in this dimension — the one rule the client can honestly evaluate itself. */
    private boolean reachable(ApparitionPoint point) {
        return this.minecraft != null && this.minecraft.level != null
                && this.minecraft.level.dimension().equals(point.dimension());
    }

    /** Title, its rule and the gap under it: what {@link Ui#title} takes before the list starts. */
    private int titleBlockH() {
        return font.lineHeight + 1 + WizardsMetrics.DIVIDER_H + WizardsMetrics.SPACE_XS;
    }

    private int footerH() {
        return WizardsMetrics.SPACE_S + WizardsMetrics.LINE_TIGHT;
    }

    /** Lays the panel out for the current point count and screen; cheap, so it runs every frame. */
    private void layout(int count) {
        int chrome = 2 * PAD + titleBlockH() + footerH();
        int fitRows = Math.max(1, (this.height - 2 * SCREEN_MARGIN - chrome) / ROW_HEIGHT);
        // An empty list keeps two rows: room for the two-line "how to memorise a place" message.
        int rows = Mth.clamp(count == 0 ? 2 : count, 1, fitRows);
        panelH = chrome + rows * ROW_HEIGHT;
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - panelH) / 2;
        list.setBounds(panelX + PAD, panelY + PAD + titleBlockH(), LIST_W, rows * ROW_HEIGHT);
        list.setItemCount(count);
        selected = count == 0 ? 0 : Mth.clamp(selected, 0, count - 1);
    }

    @Override
    protected void init() {
        super.init();
        layout(points().size());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<ApparitionPoint> points = points();
        layout(points.size());

        Ui.panel(g, S, panelX, panelY, PANEL_W, panelH);
        Ui.title(g, font, S, this.title, panelX + PAD, panelY + PAD, LIST_W, null);

        if (points.isEmpty()) {
            Ui.emptyState(g, font, S, new Ui.Rect(list.rowLeft(), list.rowTop(0), LIST_W, 2 * ROW_HEIGHT),
                    Component.translatable(KEY + "empty"), null);
            return;
        }

        Integer hover = list.indexAt(mouseX, mouseY);
        boolean mouseMoved = mouseX != lastMouseX || mouseY != lastMouseY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (hover != null && mouseMoved) {
            // Hover takes the selection only when the mouse moves, so a resting cursor does not undo
            // every arrow-key press.
            selected = hover;
        }
        Vec3 self = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.position() : Vec3.ZERO;
        int rowW = list.rowWidth() - WizardsMetrics.SPACE_XS;
        Component truncatedName = null;
        Ui.Rect truncatedOver = null;

        for (int i = list.firstVisible(); i < list.lastVisibleExclusive(); i++) {
            ApparitionPoint point = points.get(i);
            int x0 = list.rowLeft();
            int y0 = list.rowTop(i);
            int y1 = y0 + ROW_HEIGHT - 2;
            boolean ok = reachable(point);

            Ui.inset(g, S, x0, y0, rowW, ROW_HEIGHT - 2);
            if (i == selected) {
                g.fill(x0 + 1, y0 + 1, x0 + rowW - 1, y1 - 1, ok ? S.selectWash() : S.hoverWash());
                g.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, ok ? WizardsPalette.GILT_LIGHT : S.ink3());
            }

            Component name = Component.literal(point.name());
            int nameW = rowW - 2 * TEXT_INSET;
            boolean cut = UiText.drawFit(g, font, S, name, x0 + TEXT_INSET, y0 + 3, nameW, ok ? S.ink() : S.ink3());
            if (cut && hover != null && hover == i) {
                truncatedName = name;
                truncatedOver = new Ui.Rect(x0, y0, rowW, ROW_HEIGHT);
            }

            Component detail;
            int detailColour;
            if (ok) {
                Vec3 at = point.position();
                detail = Component.translatable(KEY + "detail", Mth.floor(self.distanceTo(at)),
                        Mth.floor(at.x), Mth.floor(at.y), Mth.floor(at.z));
                detailColour = S.ink2();
            } else {
                // "You cannot Apparate there" is a reason, not a theme: the surface's bad ink.
                detail = Component.translatable(KEY + "other_world");
                detailColour = S.bad();
            }
            UiText.drawFit(g, font, S, detail, x0 + TEXT_INSET, y0 + 12, nameW, detailColour);
        }
        list.renderScrollbar(g, S);

        UiText.drawFit(g, font, S, Component.translatable(KEY + "hint"), panelX + PAD,
                panelY + panelH - PAD - font.lineHeight, LIST_W, S.ink3());

        if (truncatedName != null) {
            UiTooltips.ifTruncated(g, font, true, truncatedName, truncatedOver, mouseX, mouseY);
        }
    }

    private void apparate(int index) {
        List<ApparitionPoint> points = points();
        if (index < 0 || index >= points.size() || !reachable(points.get(index))) {
            return;
        }
        ClientPacketDistributor.sendToServer(new ApparitionTravelC2SPayload(index));
        onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() == 0) {
            Integer index = list.indexAt(event.x(), event.y());
            if (index != null) {
                apparate(index);
                return true;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return list.mouseScrolled(scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int count = points().size();
        switch (event.key()) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> {
                moveSelection(-1, count);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> {
                moveSelection(1, count);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                apparate(selected);
                return true;
            }
            default -> {
                return super.keyPressed(event);
            }
        }
    }

    private void moveSelection(int by, int count) {
        if (count == 0) {
            return;
        }
        selected = Math.floorMod(selected + by, count);
        list.scrollTo(selected);
    }
}
