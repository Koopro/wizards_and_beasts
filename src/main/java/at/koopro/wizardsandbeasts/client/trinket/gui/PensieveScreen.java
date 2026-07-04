package at.koopro.wizardsandbeasts.client.trinket.gui;

import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.memory.MemoryEntry;
import at.koopro.wizardsandbeasts.memory.MemoryType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only Pensieve memory viewer. Browses the snapshot of stored memories delivered by
 * {@link at.koopro.wizardsandbeasts.network.trinket.PensieveOpenS2CPayload}; newest memory first.
 */
public class PensieveScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 188;
    private static final int ROW_H = 22;
    private static final int HEADER_H = 26;

    private final List<MemoryEntry> memories;
    private GuiScaleHelper.Layout layout;
    private int panelX;
    private int panelY;
    private int listTop;
    private int listBottom;
    private int scroll;
    private int selected = -1;

    public PensieveScreen(List<MemoryEntry> memories) {
        super(Component.literal("Pensieve"));
        this.memories = new ArrayList<>(memories);
        this.memories.sort(Comparator.comparingLong(MemoryEntry::createdGameTime).reversed());
    }

    @Override
    protected void init() {
        super.init();
        layout = GuiScaleHelper.Layout.fit(width, height, PANEL_W, PANEL_H);
        // Origin is the scaled panel corner; inner offsets stay in design space
        // and are scaled by the pose pushed in render().
        panelX = layout.panelX();
        panelY = layout.panelY();
        listTop = panelY + HEADER_H;
        listBottom = panelY + PANEL_H - 14;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, memories.size() - visibleRows());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean isDoubleClick) {
        double mouseX = layout.unmapX(event.x());
        double mouseY = layout.unmapY(event.y());
        if (event.button() == 0
                && mouseX >= panelX && mouseX <= panelX + PANEL_W && mouseY >= listTop && mouseY < listBottom) {
            int row = (int) ((mouseY - listTop) / ROW_H) + scroll;
            if (row >= 0 && row < memories.size()) {
                selected = (selected == row) ? -1 : row;
                return true;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(graphics);

        // Draw the fixed-size panel in design space; the pose shrinks it to fit.
        layout.applyScale(graphics);
        double mx = layout.unmapX(mouseX);
        double my = layout.unmapY(mouseY);

        // Panel
        graphics.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xE6100A1A);
        graphics.renderOutline(panelX, panelY, PANEL_W, PANEL_H, 0xFF5B4B8A);
        graphics.drawString(font, "§dPensieve §7— stored memories",
                panelX + 10, panelY + 9, 0xFFFFFF, false);

        if (memories.isEmpty()) {
            graphics.drawCenteredString(font, "§8The basin is still. No memories drawn.",
                    panelX + PANEL_W / 2, panelY + PANEL_H / 2 - 4, 0xFFFFFF);
            graphics.pose().popMatrix();
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        long now = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        int rows = visibleRows();
        for (int i = 0; i < rows; i++) {
            int index = scroll + i;
            if (index >= memories.size()) {
                break;
            }
            int rowY = listTop + i * ROW_H;
            boolean isSel = index == selected;
            boolean hover = mx >= panelX + 4 && mx <= panelX + PANEL_W - 4
                    && my >= rowY && my < rowY + ROW_H;
            if (isSel || hover) {
                graphics.fill(panelX + 4, rowY, panelX + PANEL_W - 4, rowY + ROW_H - 2,
                        isSel ? 0x66B388FF : 0x33FFFFFF);
            }
            MemoryEntry m = memories.get(index);
            graphics.drawString(font, tag(m.type()) + " §f" + prettySource(m.source()),
                    panelX + 10, rowY + 3, 0xFFFFFF, false);
            graphics.drawString(font, "§7" + intensityBar(m.intensity()) + "  §8" + ageText(now, m.createdGameTime()),
                    panelX + 10, rowY + 12, 0xFFFFFF, false);
        }

        // Footer
        graphics.drawString(font, "§8" + memories.size() + " memor" + (memories.size() == 1 ? "y" : "ies")
                        + (maxScroll() > 0 ? "  ·  scroll to browse" : ""),
                panelX + 10, panelY + PANEL_H - 11, 0xFFFFFF, false);

        graphics.pose().popMatrix();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String tag(MemoryType type) {
        return switch (type) {
            case HAPPY -> "§6[Happy]";
            case PAINFUL -> "§4[Painful]";
            case MUNDANE -> "§7[Mundane]";
        };
    }

    private static String prettySource(String source) {
        if (source == null || source.isBlank()) {
            return "(unknown)";
        }
        String cleaned = source.contains(":") ? source.substring(source.indexOf(':') + 1) : source;
        return cleaned.replace('_', ' ').replace('.', ' ');
    }

    private static String intensityBar(float intensity) {
        int filled = Math.round(Math.max(0f, Math.min(1f, intensity)) * 10f);
        StringBuilder sb = new StringBuilder("§d");
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? '|' : '.');
        }
        return sb.toString();
    }

    private static String ageText(long now, long created) {
        long ticks = Math.max(0, now - created);
        long seconds = ticks / 20;
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        return (hours / 24) + "d ago";
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
