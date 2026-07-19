package at.koopro.wizardsandbeasts.client.apparition.gui;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.ApparitionPoint;
import at.koopro.wizardsandbeasts.client.apparition.state.ClientApparitionPointsState;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionTravelC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Destination picker for Apparition. Lists the places the player has memorised with their distance and
 * whether they are reachable from here at all, and Apparates on click. Drawn procedurally, matching the
 * ability wheel — no texture dependency.
 *
 * <p>The screen is purely presentational: it sends an index, and the server re-reads the destination from
 * its own copy of the list and re-runs every Apparition gate.
 */
@NullMarked
public final class ApparitionSelectorScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int PANEL_WIDTH = 220;
    private static final int PADDING = 6;

    private static final int COLOR_PANEL = 0xD0101018;
    private static final int COLOR_ROW = 0xC0303040;
    private static final int COLOR_ROW_HOVER = 0xF0585870;
    private static final int COLOR_ROW_BLOCKED = 0xC0402030;
    private static final int COLOR_TEXT = 0xFFE8E8E8;
    private static final int COLOR_SUBTEXT = 0xFF9A9AA8;
    private static final int COLOR_BLOCKED_TEXT = 0xFFE08080;

    private int hovered = -1;

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

    private int listTop(int count) {
        return this.height / 2 - (count * ROW_HEIGHT) / 2;
    }

    private int listLeft() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<ApparitionPoint> points = points();
        int left = listLeft();
        int top = listTop(Math.max(1, points.size()));

        g.fill(left - PADDING, top - PADDING - 14,
                left + PANEL_WIDTH + PADDING, top + Math.max(ROW_HEIGHT, points.size() * ROW_HEIGHT) + PADDING,
                COLOR_PANEL);
        g.drawString(this.font, this.title, left, top - PADDING - 10, COLOR_TEXT, false);

        if (points.isEmpty()) {
            g.drawString(this.font,
                    Component.translatable("gui." + WizardsAndBeastsMod.MODID + ".apparition_selector.empty"),
                    left, top + 6, COLOR_SUBTEXT, false);
            hovered = -1;
            return;
        }

        hovered = -1;
        Vec3 self = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.position() : Vec3.ZERO;

        for (int i = 0; i < points.size(); i++) {
            ApparitionPoint point = points.get(i);
            int y0 = top + i * ROW_HEIGHT;
            int y1 = y0 + ROW_HEIGHT - 2;
            boolean isHovered = mouseX >= left && mouseX <= left + PANEL_WIDTH && mouseY >= y0 && mouseY < y1;
            boolean ok = reachable(point);
            if (isHovered && ok) {
                hovered = i;
            }

            g.fill(left, y0, left + PANEL_WIDTH, y1,
                    !ok ? COLOR_ROW_BLOCKED : (isHovered ? COLOR_ROW_HOVER : COLOR_ROW));
            g.drawString(this.font, point.name(), left + 5, y0 + 3, COLOR_TEXT, false);

            String detail;
            int detailColor;
            if (ok) {
                int distance = Mth.floor(self.distanceTo(point.position()));
                detail = distance + "m  ·  " + Mth.floor(point.position().x)
                        + ", " + Mth.floor(point.position().y) + ", " + Mth.floor(point.position().z);
                detailColor = COLOR_SUBTEXT;
            } else {
                detail = Component.translatable(
                        "gui." + WizardsAndBeastsMod.MODID + ".apparition_selector.other_world").getString();
                detailColor = COLOR_BLOCKED_TEXT;
            }
            g.drawString(this.font, detail, left + 5, y0 + 12, detailColor, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() == 0 && hovered >= 0 && hovered < points().size()) {
            ClientPacketDistributor.sendToServer(new ApparitionTravelC2SPayload(hovered));
            onClose();
            return true;
        }
        return super.mouseClicked(event, isDoubleClick);
    }
}
