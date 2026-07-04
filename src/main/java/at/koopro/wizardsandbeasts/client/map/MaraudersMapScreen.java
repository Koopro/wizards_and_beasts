package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.client.gui.McStylePanel;
import at.koopro.wizardsandbeasts.client.gui.util.GuiScaleHelper;
import at.koopro.wizardsandbeasts.map.TrackedEntityEntry;
import at.koopro.wizardsandbeasts.network.map.MapCloseC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MaraudersMapScreen extends Screen {

    private static final int LABEL_TEXT = 0xFF404040;
    private static final int PLAYER_COLOR = 0xFF8B4513;
    private static final int SELF_COLOR = 0xFFDAA520;
    private static final int HOSTILE_COLOR = 0xFFB22222;
    private static final int PASSIVE_COLOR = 0xFF228B22;
    private static final int COMPASS_COLOR = 0xFF5C4033;

    private final BlockPos center;
    private final int radius;
    private final Identifier dimension;
    private List<TrackedEntityEntry> entities = new ArrayList<>();
    private boolean showPassive = true;
    private boolean showHostile = true;
    private final UUID selfUuid;

    private int mapLeft, mapTop, mapSize, contentTop, contentSize;
    private GuiScaleHelper.Layout layout;

    public MaraudersMapScreen(BlockPos center, int radius, Identifier dimension) {
        super(Component.literal("Marauder's Map"));
        this.center = center;
        this.radius = radius;
        this.dimension = dimension;
        this.selfUuid = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getUUID()
                : new UUID(0, 0);
    }

    @Override
    protected void init() {
        super.init();
        int baseSize = Math.min(width, height) - 60;
        layout = GuiScaleHelper.Layout.panel(width, height, baseSize, baseSize);
        mapSize = Math.min(layout.panelW(), layout.panelH());
        mapLeft = (width - mapSize) / 2;
        mapTop = (height - mapSize) / 2;
        contentSize = mapSize - layout.s(40);
        contentTop = mapTop + layout.s(32);

        int btnW = layout.s(70);
        int btnH = layout.s(16);
        int btnY = mapTop + mapSize + layout.s(6);

        addRenderableWidget(Button.builder(
                        Component.literal(showPassive ? "Passive: ON" : "Passive: OFF"),
                        btn -> {
                            showPassive = !showPassive;
                            btn.setMessage(Component.literal(showPassive ? "Passive: ON" : "Passive: OFF"));
                        })
                .bounds(width / 2 - btnW - 2, btnY, btnW, btnH)
                .build());

        addRenderableWidget(Button.builder(
                        Component.literal(showHostile ? "Hostile: ON" : "Hostile: OFF"),
                        btn -> {
                            showHostile = !showHostile;
                            btn.setMessage(Component.literal(showHostile ? "Hostile: ON" : "Hostile: OFF"));
                        })
                .bounds(width / 2 + 2, btnY, btnW, btnH)
                .build());
    }

    public void updateEntities(List<TrackedEntityEntry> entries) {
        this.entities = entries;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderMenuBackground(gfx);
        McStylePanel.drawTexturedPanel(gfx, mapLeft, mapTop, mapSize, mapSize);
        renderHeader(gfx, mapLeft, mapTop, mapSize);
        renderCompass(gfx, mapLeft, mapTop, mapSize);
        renderEntities(gfx, mapLeft, mapTop, mapSize, mouseX, mouseY);
        renderLegend(gfx, mapLeft, mapTop, mapSize);

        super.render(gfx, mouseX, mouseY, partialTick);

        renderTooltip(gfx, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics gfx, int left, int top, int size) {
        gfx.drawCenteredString(font,
                "The Marauder's Map",
                left + size / 2, top + 6, LABEL_TEXT);
        gfx.drawCenteredString(font,
                "\"I solemnly swear that I am up to no good\"",
                left + size / 2, top + 18, 0xFF606060);
    }

    private void renderCompass(GuiGraphics gfx, int left, int top, int size) {
        int cx = left + size / 2;
        int contentBottom = contentTop + contentSize;
        int contentLeft = left + (size - contentSize) / 2;
        int contentRight = contentLeft + contentSize;

        gfx.drawCenteredString(font, "N", cx, contentTop - 10, COMPASS_COLOR);
        gfx.drawCenteredString(font, "S", cx, contentBottom + 2, COMPASS_COLOR);
        gfx.drawString(font, "W", contentLeft - 10, contentTop + contentSize / 2 - 4, COMPASS_COLOR, false);
        gfx.drawString(font, "E", contentRight + 4, contentTop + contentSize / 2 - 4, COMPASS_COLOR, false);
    }

    private void renderEntities(GuiGraphics gfx, int left, int top, int size, int mouseX, int mouseY) {
        for (TrackedEntityEntry entry : entities) {
            if (!showPassive && entry.category() == TrackedEntityEntry.PASSIVE) continue;
            if (!showHostile && entry.category() == TrackedEntityEntry.HOSTILE) continue;

            int[] pos = entityScreenPos(entry);
            int dotX = pos[0];
            int dotY = pos[1];

            if (dotX < left || dotX > left + size || dotY < top || dotY > top + size)
                continue;

            boolean isSelf = entry.uuid().equals(selfUuid);
            int color = switch (entry.category()) {
                case TrackedEntityEntry.PLAYER -> isSelf ? SELF_COLOR : PLAYER_COLOR;
                case TrackedEntityEntry.HOSTILE -> HOSTILE_COLOR;
                default -> PASSIVE_COLOR;
            };

            int dotSz = (entry.category() == TrackedEntityEntry.PLAYER) ? 3 : 2;
            if (isSelf) dotSz = 4;

            gfx.fill(dotX - dotSz, dotY - dotSz,
                    dotX + dotSz, dotY + dotSz, color);

            float yawRad = (float) Math.toRadians(-entry.yaw() + 180);
            int arrowX = dotX + (int) (Math.sin(yawRad) * (dotSz + 3));
            int arrowY = dotY + (int) (-Math.cos(yawRad) * (dotSz + 3));
            gfx.fill(arrowX - 1, arrowY - 1, arrowX + 1, arrowY + 1, color);

            if (entry.category() == TrackedEntityEntry.PLAYER) {
                gfx.drawString(font, entry.displayName(),
                        dotX + dotSz + 3, dotY - 3, color, false);
            }
        }
    }

    private void renderLegend(GuiGraphics gfx, int left, int top, int size) {
        int lx = left + 6;
        int ly = top + size - 28;

        gfx.fill(lx - 2, ly - 2, lx + 80, ly + 26, 0x30000000);

        gfx.fill(lx, ly + 1, lx + 6, ly + 7, SELF_COLOR);
        gfx.drawString(font, "You", lx + 9, ly, LABEL_TEXT, false);

        gfx.fill(lx, ly + 10, lx + 5, ly + 15, PLAYER_COLOR);
        gfx.drawString(font, "Players", lx + 9, ly + 9, LABEL_TEXT, false);

        gfx.fill(lx + 45, ly + 1, lx + 49, ly + 5, HOSTILE_COLOR);
        gfx.drawString(font, "Foe", lx + 52, ly, LABEL_TEXT, false);

        gfx.fill(lx + 45, ly + 10, lx + 49, ly + 14, PASSIVE_COLOR);
        gfx.drawString(font, "Mob", lx + 52, ly + 9, LABEL_TEXT, false);
    }

    private void renderTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        for (TrackedEntityEntry entry : entities) {
            if (!showPassive && entry.category() == TrackedEntityEntry.PASSIVE) continue;
            if (!showHostile && entry.category() == TrackedEntityEntry.HOSTILE) continue;

            int[] pos = entityScreenPos(entry);
            int dotX = pos[0];
            int dotY = pos[1];

            boolean isSelf = entry.uuid().equals(selfUuid);
            int dotSz = (entry.category() == TrackedEntityEntry.PLAYER) ? 3 : 2;
            if (isSelf) dotSz = 4;

            int hoverMargin = dotSz + 2;
            if (mouseX >= dotX - hoverMargin && mouseX <= dotX + hoverMargin
                    && mouseY >= dotY - hoverMargin && mouseY <= dotY + hoverMargin) {

                int blockDist = (int) Math.sqrt(
                        Math.pow(entry.x() - center.getX(), 2) +
                                Math.pow(entry.z() - center.getZ(), 2));

                String line1 = entry.displayName();
                String line2 = blockDist + "m away";

                int tipX = mouseX + 10;
                int tipY = mouseY - 18;
                int tipW = Math.max(font.width(line1), font.width(line2)) + 6;

                gfx.fill(tipX - 2, tipY - 2, tipX + tipW, tipY + 22, 0xCC2A1E14);
                gfx.fill(tipX - 1, tipY - 1, tipX + tipW - 1, tipY + 21, 0xCC4A3520);
                gfx.drawString(font, line1, tipX + 1, tipY + 1, 0xFFFFFFFF, false);
                gfx.drawString(font, line2, tipX + 1, tipY + 11, 0xFFAAAAAA, false);
                break;
            }
        }
    }

    private int[] entityScreenPos(TrackedEntityEntry entry) {
        double relX = entry.x() - center.getX();
        double relZ = entry.z() - center.getZ();
        int contentLeft = mapLeft + (mapSize - contentSize) / 2;

        int dotX = contentLeft + (int) ((relX / radius + 1.0) * contentSize / 2.0);
        int dotY = contentTop + (int) ((relZ / radius + 1.0) * contentSize / 2.0);
        return new int[]{dotX, dotY};
    }

    @Override
    public void onClose() {
        ClientPacketDistributor.sendToServer(new MapCloseC2SPayload());
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
