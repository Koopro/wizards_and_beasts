package at.koopro.wizardsandbeasts.client.debug;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.command.debug.report.DebugLine;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * The debug dump for whatever you are looking at, drawn beside it rather than into chat.
 *
 * <h2>Why it is a HUD layer and not world geometry</h2>
 *
 * <p>The box is anchored to a point in the world, so the obvious home is
 * {@code RenderLevelStageEvent} with a billboarded {@code Font.drawInBatch}. Two things argue
 * against it. Text drawn in the world is drawn at world scale and goes soft or aliased at any
 * distance that is not the one it was tuned for, which is a poor property for something whose entire
 * job is to be read; and this mod has no world-space text renderer to follow, so it would be the
 * first — a new rendering path to keep working across versions, for a developer tool.
 *
 * <p>Projecting the anchor to the screen and drawing a flat panel there gives crisp text at every
 * distance and uses only {@link GuiGraphics}. Occlusion comes free: the server's pick stops at the
 * first solid block, so there is never an anchor you cannot see.
 *
 * <h2>The projection</h2>
 *
 * <p>Built from the camera's own basis vectors and the FOV option rather than fetching the
 * projection matrix, because {@code GameRenderer.getFov} is private and the matrix would have to be
 * rebuilt from the same option anyway. The consequence is honest and small: while an FOV
 * <em>modifier</em> is running — sprinting, Speed — the base option no longer describes the frustum
 * and the panel drifts by a few pixels until it settles. For a box of debug text beside a block that
 * is not worth a mixin.
 */
@NullMarked
public final class WorldDebugPanel {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "world_debug_panel");

    /** Text scale. The panel is meant to sit beside a block without covering it. */
    private static final float SCALE = 0.75f;
    /** Screen-space gap between the anchor and the panel's near edge, before scaling. */
    private static final int GAP = 14;
    private static final int PAD = 3;
    private static final int LINE_HEIGHT = 10;
    /**
     * Rows drawn before the panel gives up and points at the command.
     *
     * <p>A full player dump is several hundred rows. A panel that tried to draw them would be taller
     * than the screen and unreadable, and the answer to "I need all of it" is a chat report you can
     * scroll, which is what {@code /wandb debug feature all} is for.
     */
    private static final int MAX_ROWS = 22;

    private static final int BG = 0xD0100A06;
    private static final int BORDER = 0xFF000000 | ChatPalette.RULE;
    private static final int TITLE = 0xFF000000 | ChatPalette.ACCENT;
    private static final int STALE = 0xFF000000 | ChatPalette.MUTED;

    private static final char BAR_FULL = '█';
    private static final char BAR_EMPTY = '░';
    private static final int BAR_CELLS = 10;

    private WorldDebugPanel() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (!ClientDebugPanelState.isDebugMode() || !ClientDebugPanelState.hasTarget()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 anchor = ClientDebugPanelState.anchor();
        if (mc.player == null || mc.level == null || anchor == null || mc.options.hideGui) {
            return;
        }
        float[] screen = project(mc, anchor);
        if (screen == null) {
            return;
        }
        boolean stale = ClientDebugPanelState.isStale(mc.level.getGameTime());
        draw(graphics, mc.font, screen[0], screen[1], stale);
    }

    /**
     * Anchor point in gui-scaled pixels, or {@code null} when it is behind the camera or off screen.
     *
     * @return {@code {x, y}}
     */
    private static float @org.jspecify.annotations.Nullable [] project(Minecraft mc, Vec3 anchor) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return null;
        }
        Vec3 toTarget = anchor.subtract(camera.position());
        Vector3fc forward = camera.forwardVector();
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();

        double depth = dot(toTarget, forward);
        // Behind the camera, or so close that the division below stops meaning anything.
        if (depth <= 0.1) {
            return null;
        }
        double rightward = -dot(toTarget, left);
        double upward = dot(toTarget, up);

        double tanHalfFov = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        if (tanHalfFov <= 0.0) {
            return null;
        }
        // Minecraft's FOV option is the vertical angle, so the horizontal half-extent is the
        // vertical one times the aspect. Taken from the window rather than the gui-scaled size,
        // which is rounded to whole scale steps and would skew the horizontal placement.
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double ndcX = (rightward / depth) / (tanHalfFov * aspect);
        double ndcY = (upward / depth) / tanHalfFov;

        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        return new float[]{
                (float) ((ndcX * 0.5 + 0.5) * guiWidth),
                (float) ((0.5 - ndcY * 0.5) * guiHeight)
        };
    }

    private static double dot(Vec3 vec, Vector3fc other) {
        return vec.x * other.x() + vec.y * other.y() + vec.z * other.z();
    }

    private static void draw(GuiGraphics graphics, Font font, float anchorX, float anchorY,
                             boolean stale) {
        List<DebugLine> lines = ClientDebugPanelState.lines();
        List<Row> rows = layOut(font, lines);

        int width = font.width(ClientDebugPanelState.title());
        for (Row row : rows) {
            width = Math.max(width, row.width(font));
        }
        width += PAD * 2;
        int height = PAD * 2 + LINE_HEIGHT * (rows.size() + 1);

        // Which side of the anchor has room. Flipping beats clamping: a panel pinned to the screen
        // edge stops pointing at the thing it describes.
        int guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int guiHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        float scaledWidth = width * SCALE;
        float scaledHeight = height * SCALE;
        float originX = anchorX + GAP;
        if (originX + scaledWidth > guiWidth) {
            originX = anchorX - GAP - scaledWidth;
        }
        float originY = Math.max(2f, Math.min(anchorY - scaledHeight / 2f, guiHeight - scaledHeight - 2f));

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX, originY);
        pose.scale(SCALE, SCALE);

        graphics.fill(0, 0, width, height, BG);
        graphics.fill(0, 0, width, 1, BORDER);
        graphics.fill(0, height - 1, width, height, BORDER);
        graphics.fill(0, 0, 1, height, BORDER);
        graphics.fill(width - 1, 0, width, height, BORDER);

        int y = PAD;
        graphics.drawString(font, ClientDebugPanelState.title(), PAD, y, stale ? STALE : TITLE, false);
        y += LINE_HEIGHT;
        for (Row row : rows) {
            row.draw(graphics, font, PAD, y, stale);
            y += LINE_HEIGHT;
        }
        pose.popMatrix();
    }

    private static List<Row> layOut(Font font, List<DebugLine> lines) {
        List<Row> rows = new ArrayList<>();
        for (DebugLine line : lines) {
            if (rows.size() >= MAX_ROWS) {
                rows.add(new Row("… " + (lines.size() - MAX_ROWS) + " more — /wandb debug inspect",
                        "", STALE));
                break;
            }
            rows.add(toRow(line));
        }
        return rows;
    }

    private static Row toRow(DebugLine line) {
        int colour = line.rgb() == DebugLine.DEFAULT_COLOUR
                ? defaultColour(line.kind())
                : 0xFF000000 | line.rgb();
        return switch (line.kind()) {
            case SECTION -> new Row("— " + line.label(), "", 0xFF000000 | ChatPalette.ACCENT);
            case NOTE -> new Row(line.label(), "", 0xFF000000 | ChatPalette.MUTED);
            case WARN -> new Row("! " + line.label(), "", 0xFF000000 | ChatPalette.BAD);
            case BAR -> new Row(line.label(), track(line.progress()) + " " + line.value(), colour);
            case ROW, FLAG -> new Row(line.label(), line.value(), colour);
        };
    }

    private static int defaultColour(DebugLine.Kind kind) {
        return 0xFF000000 | switch (kind) {
            case SECTION -> ChatPalette.ACCENT;
            case NOTE -> ChatPalette.MUTED;
            case WARN -> ChatPalette.BAD;
            case ROW, FLAG, BAR -> ChatPalette.TEXT;
        };
    }

    private static String track(float progress) {
        int filled = Math.round(Math.max(0f, Math.min(1f, progress)) * BAR_CELLS);
        return String.valueOf(BAR_FULL).repeat(filled)
                + String.valueOf(BAR_EMPTY).repeat(BAR_CELLS - filled);
    }

    /**
     * One drawn line: a dim label and a bright value.
     *
     * <p>No column alignment, for the reason {@code ChatReport} spells out — Minecraft's font is
     * proportional, so padding labels to the same character count does not put their values in the
     * same place.
     */
    private record Row(String label, String value, int valueColour) {

        private static final int LABEL_COLOUR = 0xFF000000 | ChatPalette.LABEL;
        private static final String SEPARATOR = " · ";

        int width(Font font) {
            return value.isEmpty()
                    ? font.width(label)
                    : font.width(label) + font.width(SEPARATOR) + font.width(value);
        }

        void draw(GuiGraphics graphics, Font font, int x, int y, boolean stale) {
            if (value.isEmpty()) {
                graphics.drawString(font, label, x, y, stale ? STALE : valueColour, false);
                return;
            }
            graphics.drawString(font, label, x, y, stale ? STALE : LABEL_COLOUR, false);
            int afterLabel = x + font.width(label);
            graphics.drawString(font, SEPARATOR, afterLabel, y, stale ? STALE : LABEL_COLOUR, false);
            graphics.drawString(font, value, afterLabel + font.width(SEPARATOR), y,
                    stale ? STALE : valueColour, false);
        }
    }
}
