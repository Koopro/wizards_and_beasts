package at.koopro.wizardsandbeasts.client.map;

import at.koopro.wizardsandbeasts.map.TrackedEntityEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The moving dots, their footprints and their names.
 *
 * <p>This is the half of the map nobody else has, so it gets the ink rather than a coloured square:
 * a stamped mark that turns to face the way its subject is walking, a trail of prints behind it,
 * and — for people — a name written on the page beside it.
 *
 * <p>Elevation fades rather than hides. A name three storeys up and a name in the cellar are both
 * real and both worth seeing, but drawing them at the same weight as the person standing next to
 * you turns a castle into an unreadable stack. Fading is the flat map's only honest way to say
 * "not on this floor".
 */
public final class MapTrackedRenderer {

    /** Ink colours. Not from {@code WizardsPalette}: these are annotations, not chrome. */
    public static final int INK_SELF = 0xFF7A2E2E;
    public static final int INK_PLAYER = 0xFF3A2E24;
    public static final int INK_HOSTILE = 0xFF8C3A2A;
    public static final int INK_PASSIVE = 0xFF5A6B4A;

    /** Blocks of vertical separation past which a subject is drawn at its faintest. */
    private static final double FADE_SPAN = 40.0;
    private static final float MIN_ALPHA = 0.32F;

    /** Names are the busiest thing on the page; below this zoom only the symbols are drawn. */
    private static final double NAME_MIN_ZOOM = 0.7;

    private MapTrackedRenderer() {
    }

    /**
     * Draws every tracked subject and returns the one under the cursor.
     *
     * @param viewerY the holder's own Y, which is what the elevation fade is measured against
     */
    public static @Nullable TrackedEntityEntry render(GuiGraphics graphics, Font font, MapView view,
                                                      List<TrackedEntityEntry> entries,
                                                      UUID selfUuid, double viewerY,
                                                      double mouseX, double mouseY,
                                                      boolean showPassive, boolean showHostile) {
        TrackedEntityEntry hovered = null;
        long now = System.currentTimeMillis();

        for (TrackedEntityEntry entry : entries) {
            if (!visible(entry, showPassive, showHostile)) {
                continue;
            }
            double screenX = view.screenX(entry.x());
            double screenY = view.screenY(entry.z());
            boolean isSelf = entry.uuid().equals(selfUuid);

            int size = view.scaled(isSelf ? 16 : entry.category() == TrackedEntityEntry.PLAYER ? 12 : 10);
            int half = size / 2;
            if (screenX + half < view.viewX() || screenX - half > view.viewX() + view.viewW()
                    || screenY + half < view.viewY() || screenY - half > view.viewY() + view.viewH()) {
                continue;
            }

            float alpha = isSelf ? 1.0F : depthAlpha(entry.y(), viewerY);
            int ink = withAlpha(inkFor(entry, isSelf), alpha);

            drawTrail(graphics, view, entry.uuid(), ink, now);

            // Own mark and everyone else's are different sprites, not the same sprite at two
            // sizes: "where am I" has to be answerable in one glance, and one glance does not
            // compare sizes.
            if (isSelf) {
                drawRotated(graphics, view, MaraudersMapTextures.PLAYER_MARK,
                        MaraudersMapTextures.PLAYER_MARK_SIZE, screenX, screenY, size,
                        entry.yaw(), ink);
            } else {
                drawRotated(graphics, view, MaraudersMapTextures.TRACKED_MARK,
                        MaraudersMapTextures.TRACKED_MARK_SIZE, screenX, screenY, size,
                        entry.yaw(), ink);
            }

            if (entry.category() == TrackedEntityEntry.PLAYER && !isSelf
                    && view.zoom() >= NAME_MIN_ZOOM) {
                Component name = Component.literal(entry.displayName());
                graphics.drawString(font, name,
                        (int) Math.round(screenX) - font.width(name) / 2,
                        (int) Math.round(screenY) - half - 9, ink, false);
            }

            if (mouseX >= screenX - half - 2 && mouseX <= screenX + half + 2
                    && mouseY >= screenY - half - 2 && mouseY <= screenY + half + 2) {
                hovered = entry;
            }
        }
        return hovered;
    }

    private static boolean visible(TrackedEntityEntry entry, boolean showPassive, boolean showHostile) {
        return switch (entry.category()) {
            case TrackedEntityEntry.PASSIVE -> showPassive;
            case TrackedEntityEntry.HOSTILE -> showHostile;
            default -> true;
        };
    }

    private static int inkFor(TrackedEntityEntry entry, boolean isSelf) {
        if (isSelf) {
            return INK_SELF;
        }
        return switch (entry.category()) {
            case TrackedEntityEntry.PLAYER -> INK_PLAYER;
            case TrackedEntityEntry.HOSTILE -> INK_HOSTILE;
            default -> INK_PASSIVE;
        };
    }

    private static void drawTrail(GuiGraphics graphics, MapView view, UUID uuid, int ink, long now) {
        for (MapTrails.Step step : MapTrails.of(uuid)) {
            float alpha = MapTrails.alpha(step, now);
            if (alpha <= 0.02F) {
                continue;
            }
            double sx = view.screenX(step.x());
            double sy = view.screenY(step.z());
            int size = view.scaled(MaraudersMapTextures.FOOTPRINT_SIZE);
            graphics.blit(RenderPipelines.GUI_TEXTURED, MaraudersMapTextures.FOOTPRINT,
                    (int) Math.round(sx) - size / 2, (int) Math.round(sy) - size / 2,
                    0.0F, 0.0F, size, size,
                    MaraudersMapTextures.FOOTPRINT_SIZE, MaraudersMapTextures.FOOTPRINT_SIZE,
                    MaraudersMapTextures.FOOTPRINT_SIZE, MaraudersMapTextures.FOOTPRINT_SIZE,
                    withAlpha(ink, alpha * 0.55F));
        }
    }

    /**
     * Blits a mark rotated to its subject's heading.
     *
     * <p>{@code GuiGraphics.pose()} is a 3x2 matrix stack over the quad's vertices, so the rotation
     * is free — and, importantly, scissor rectangles are screen-space and unaffected by it, so the
     * caller's viewport clip still holds around a rotated sprite.
     */
    private static void drawRotated(GuiGraphics graphics, MapView view, net.minecraft.resources.Identifier tex,
                                    int nativeSize, double screenX, double screenY, int drawSize,
                                    float yaw, int tint) {
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) screenX, (float) screenY);
        // Minecraft yaw is degrees clockwise from south; the sprite is drawn pointing north (up),
        // so the sprite's own frame is 180 degrees from the world's.
        graphics.pose().rotate((float) Math.toRadians(yaw + 180.0F));
        graphics.pose().translate(-drawSize / 2.0F, -drawSize / 2.0F);
        graphics.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0.0F, 0.0F,
                drawSize, drawSize, nativeSize, nativeSize, nativeSize, nativeSize, tint);
        graphics.pose().popMatrix();
    }

    private static float depthAlpha(double subjectY, double viewerY) {
        double delta = Math.abs(subjectY - viewerY);
        float t = (float) Math.clamp(delta / FADE_SPAN, 0.0, 1.0);
        return 1.0F - (1.0F - MIN_ALPHA) * t;
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.clamp((int) ((argb >>> 24) * alpha), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
