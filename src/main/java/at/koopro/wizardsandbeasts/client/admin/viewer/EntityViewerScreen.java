package at.koopro.wizardsandbeasts.client.admin.viewer;

import at.koopro.wizardsandbeasts.client.admin.widget.AdminButton;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminCheckbox;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminEnumSelector;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminText;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariant;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Creature Lab's Entity Viewer: one creature, full screen, drawn by the renderer the game draws it with.
 *
 * <p>The creature is a {@link PreviewEntity} — a client-side instance never added to any level — extracted into an
 * {@code EntityRenderState} by its production renderer ({@code EntityRenderDispatcher#getRenderer}) and submitted
 * through {@code GuiGraphics#submitEntityRenderState}, exactly as the inventory draws the player. There is no
 * preview model: variant textures, GeckoLib bones, glow layers and animations are the renderer's own.
 *
 * <p>Camera: drag to orbit (the creature turns under a fixed camera, the same transform the inventory uses), scroll
 * to zoom, Reset to return. Animation: any clip in the creature's animation file, looping or once, paused, at a
 * chosen speed — or "its own", the creature's real controllers. Overlays: hitbox (projected with the same transform
 * as the model), name, bone hierarchy (bones the selected clip moves are highlighted), debug readout.
 *
 * <p>Costs nothing while closed and little while open: one instance, advanced one tick per client tick only while
 * playing; the clip list and bone tree are read once. Closing, or the world going away, drops the instance.
 */
@NullMarked
public final class EntityViewerScreen extends Screen {

    private static final String KEY = "admin.wizards_and_beasts.viewer.";
    private static final int PANEL_W = 170;
    private static final int PAD = 8;
    private static final int LINE = 10;
    private static final String OWN = "";
    private static final List<String> SPEEDS = List.of("0.25", "0.5", "1", "1.5", "2");
    private static final float MIN_ZOOM = 0.3f;
    private static final float MAX_ZOOM = 4.0f;

    private final Screen parent;
    private final String creatureId;
    private final String requestedVariant;
    private @Nullable PreviewEntity preview;
    private @Nullable Level createdIn;
    private List<String> clips = List.of();

    // View state, kept across resizes.
    private float yaw = 30.0f;
    private float pitch = -10.0f;
    private float zoom = 1.0f;
    private boolean playing = true;
    private boolean loop = true;
    private boolean showHitbox;
    private boolean showName;
    private boolean showBones;
    private boolean showDebug;
    private String clip = OWN;
    private String speed = "1";
    /** Light the model is drawn under: full daylight down to darkness, so glow layers can be judged. */
    private String light = "full";
    private @Nullable List<PreviewEntity.BoneLine> bones;
    private boolean dragging;

    public EntityViewerScreen(Screen parent, String creatureId, String variant) {
        super(Component.translatable(KEY + "title"));
        this.parent = parent;
        this.creatureId = creatureId;
        this.requestedVariant = variant;
    }

    @Override
    protected void init() {
        if (preview == null) {
            preview = PreviewEntity.create(creatureId);
            createdIn = minecraft == null ? null : minecraft.level;
            if (preview != null) {
                if (!requestedVariant.isEmpty()) {
                    preview.applyVariant(requestedVariant);
                }
                clips = preview.animations();
            }
        }
        int x = PAD;
        int y = 40;
        int w = PANEL_W - 2 * PAD;
        PreviewEntity entity = preview;
        if (entity != null) {
            List<CreatureVariant> variants = CreatureVariants.of(creatureId);
            if (!variants.isEmpty()) {
                List<String> ids = variants.stream().map(CreatureVariant::variantId).toList();
                String current = entity.variantId();
                addRenderableWidget(new AdminEnumSelector(x, y, w, 16, ids, current == null ? ids.get(0) : current,
                        value -> entity.applyVariant(value), EntityViewerScreen::title));
                y += 26;
            }
            if (entity.isGeo()) {
                List<String> options = new ArrayList<>();
                options.add(OWN);
                options.addAll(clips);
                if (!options.contains(clip)) {
                    clip = OWN;
                }
                addRenderableWidget(new AdminEnumSelector(x, y, w, 16, options, clip, value -> {
                    clip = value;
                    entity.play(clip.equals(OWN) ? null : clip, loop);
                    bones = null;
                }, EntityViewerScreen::clipLabel));
                y += 20;
                int half = (w - 4) / 2;
                addRenderableWidget(new AdminButton(x, y, half, 16, Component.translatable(playing ? KEY + "pause" : KEY + "play"),
                        AdminButton.Tone.PRIMARY, () -> {
                            playing = !playing;
                            rebuildWidgets();
                        }));
                addRenderableWidget(new AdminButton(x + half + 4, y, half, 16, Component.translatable(KEY + "restart"),
                        AdminButton.Tone.NEUTRAL, () -> entity.play(clip.equals(OWN) ? null : clip, loop)));
                y += 20;
                addRenderableWidget(new AdminEnumSelector(x, y, w, 16, SPEEDS, speed, value -> {
                    speed = value;
                    entity.setSpeed(Double.parseDouble(value));
                }, value -> Component.translatable(KEY + "speed", value)));
                y += 20;
                addRenderableWidget(new AdminCheckbox(x, y, Component.translatable(KEY + "loop"), loop, checked -> {
                    loop = checked;
                    entity.play(clip.equals(OWN) ? null : clip, loop);
                }));
                y += 16;
            } else {
                y += LINE + 4;
            }
            y += 4;
            addRenderableWidget(new AdminEnumSelector(x, y, w, 16, LIGHTS, light, value -> light = value,
                    value -> Component.translatable(KEY + "light." + value)));
            y += 20;
            addRenderableWidget(new AdminCheckbox(x, y, Component.translatable(KEY + "hitbox"), showHitbox, c -> showHitbox = c));
            y += 16;
            addRenderableWidget(new AdminCheckbox(x, y, Component.translatable(KEY + "name"), showName, c -> showName = c));
            y += 16;
            if (entity.isGeo()) {
                addRenderableWidget(new AdminCheckbox(x, y, Component.translatable(KEY + "bones"), showBones, c -> showBones = c));
                y += 16;
            }
            addRenderableWidget(new AdminCheckbox(x, y, Component.translatable(KEY + "debug"), showDebug, c -> showDebug = c));
            y += 20;
            addRenderableWidget(new AdminButton(x, y, w, 16, Component.translatable(KEY + "reset_camera"),
                    AdminButton.Tone.NEUTRAL, this::resetCamera));
        }
        addRenderableWidget(new AdminButton(x, height - PAD - 16, w, 16, Component.translatable("admin.wizards_and_beasts.button.back"),
                AdminButton.Tone.QUIET, this::onClose));
    }

    private static final List<String> LIGHTS = List.of("full", "torch", "dim", "dark");

    /**
     * Packed light (block in bits 4–7, sky in 20–23, as the lightmap reads it). Only block light below full, so the
     * result does not depend on the world's time of day. Emissive and glow layers ignore it by design — which is what
     * this control is for checking.
     */
    static int lightCoords(String light) {
        int block = switch (light) {
            case "torch" -> 10;
            case "dim" -> 5;
            case "dark" -> 0;
            default -> 15;
        };
        int sky = "full".equals(light) ? 15 : 0;
        return (block << 4) | (sky << 20);
    }

    private static Component title(String id) {
        String text = id.replace('_', ' ');
        return Component.literal(text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1));
    }

    private static Component clipLabel(String name) {
        if (name.equals(OWN)) {
            return Component.translatable(KEY + "own_animation");
        }
        // "animation.hippogriff.fly" → "fly": the prefix is the same for every clip in a file.
        int dot = name.lastIndexOf('.');
        return Component.literal(dot >= 0 ? name.substring(dot + 1) : name);
    }

    private void resetCamera() {
        yaw = 30.0f;
        pitch = -10.0f;
        zoom = 1.0f;
    }

    // ── lifecycle ──

    @Override
    public void tick() {
        if (minecraft == null || minecraft.level == null || minecraft.level != createdIn) {
            // The world this instance was made for is gone: nothing to show, nothing to keep.
            onClose();
            return;
        }
        if (preview != null && playing) {
            preview.tick();
        }
    }

    @Override
    public void onClose() {
        preview = null;
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        // Dropped on any exit, including a disconnect that replaces this screen without onClose.
        preview = null;
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** For tests and the capture harness: the instance currently shown. */
    public @Nullable PreviewEntity preview() {
        return preview;
    }

    // ── input ──

    private int viewX0() {
        return PANEL_W;
    }

    private boolean inViewport(double mouseX, double mouseY) {
        return mouseX >= viewX0() && mouseX < width && mouseY >= 0 && mouseY < height;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (inViewport(event.x(), event.y())) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    /** Turns the camera around the creature: yaw wraps, pitch stays within ±60°. */
    public void orbit(float deltaYaw, float deltaPitch) {
        yaw = Mth.wrapDegrees(yaw + deltaYaw);
        pitch = Mth.clamp(pitch + deltaPitch, -60.0f, 60.0f);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            orbit((float) dragX * 1.5f, (float) -dragY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inViewport(mouseX, mouseY)) {
            zoom = Mth.clamp(zoom * (scrollY > 0 ? 1.1f : 1.0f / 1.1f), MIN_ZOOM, MAX_ZOOM);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_SPACE) {
            playing = !playing;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(event);
    }

    // ── drawing ──

    @Override
    public void renderBackground(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, AdminTheme.FRAME);
        g.fill(0, 0, PANEL_W, height, AdminTheme.PAPER);
        g.fill(PANEL_W - 1, 0, PANEL_W, height, AdminTheme.GOLD_DARK);
    }

    @Override
    public void render(@NonNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        PreviewEntity entity = preview;
        g.drawString(font, getTitle(), PAD, PAD, AdminTheme.RUBRIC, false);
        if (entity == null) {
            g.drawString(font, Component.translatable(KEY + "unavailable", creatureId), viewX0() + PAD, PAD, AdminTheme.FRAME_TEXT, false);
            return;
        }
        g.drawString(font, AdminText.clip(font, entity.entity().getType().getDescription().getString(), PANEL_W - 2 * PAD),
                PAD, PAD + 12, AdminTheme.INK, false);
        g.drawString(font, AdminText.clip(font, Component.translatable(KEY + "renderer",
                entity.renderer().getClass().getSimpleName()).getString(), PANEL_W - 2 * PAD), PAD, PAD + 22, AdminTheme.INK_3, false);

        float pt = playing ? partialTick : 0.0f;
        EntityRenderState state = entity.extract(pt);
        state.lightCoords = lightCoords(light);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        float boxW = entity.entity().getBbWidth();
        float boxH = entity.entity().getBbHeight();
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180.0f + yaw;
            living.yRot = 0.0f;
            living.xRot = 0.0f;
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1.0f;
            boxH = living.boundingBoxHeight;
            boxW = living.boundingBoxWidth;
        }

        int x0 = viewX0();
        int x1 = width;
        int y0 = 0;
        int y1 = height;
        float fit = Math.min((y1 - y0) * 0.6f / Math.max(0.3f, boxH), (x1 - x0) * 0.5f / Math.max(0.3f, Math.max(boxW, boxH)));
        float scale = fit * zoom;
        Quaternionf pitchQ = new Quaternionf().rotateX(pitch * Mth.DEG_TO_RAD);
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(pitchQ);
        Vector3f translation = new Vector3f(0.0f, boxH / 2.0f, 0.0f);
        g.submitEntityRenderState(state, scale, translation, rotation, pitchQ, x0, y0, x1, y1);

        float cx = (x0 + x1) / 2.0f;
        float cy = (y0 + y1) / 2.0f;
        if (showHitbox) {
            drawHitbox(g, cx, cy, scale, rotation, translation, boxW, boxH);
        }
        if (showName) {
            Vector3f top = project(new Vector3f(0.0f, boxH, 0.0f), rotation, translation);
            Component name = entity.entity().getType().getDescription();
            int tx = Math.round(cx + top.x * scale) - font.width(name) / 2;
            int ty = Math.round(cy + top.y * scale) - 14;
            g.fill(tx - 2, ty - 2, tx + font.width(name) + 2, ty + 9, 0x80000000);
            g.drawString(font, name, tx, ty, 0xFFFFFFFF, false);
        }
        if (showBones) {
            drawBones(g, font, entity, state);
        }
        if (showDebug) {
            drawDebug(g, font, entity, boxW, boxH);
        }
        g.drawString(font, Component.translatable(KEY + "hint"), x0 + PAD, height - PAD - 8, AdminTheme.FRAME_TEXT_DIM, false);
    }

    /** A model-space point through the same body turn and view rotation the renderer applies. */
    private Vector3f project(Vector3f point, Quaternionf rotation, Vector3f translation) {
        Vector3f v = new Vector3f(point).rotateY(-yaw * Mth.DEG_TO_RAD);
        rotation.transform(v);
        return v.add(translation);
    }

    private void drawHitbox(GuiGraphics g, float cx, float cy, float scale, Quaternionf rotation, Vector3f translation,
                            float w, float h) {
        float r = w / 2.0f;
        Vector3f[] corners = new Vector3f[8];
        int i = 0;
        for (float yy : new float[] {0.0f, h}) {
            for (float xx : new float[] {-r, r}) {
                for (float zz : new float[] {-r, r}) {
                    Vector3f p = project(new Vector3f(xx, yy, zz), rotation, translation);
                    corners[i++] = new Vector3f(cx + p.x * scale, cy + p.y * scale, 0);
                }
            }
        }
        int[][] edges = {{0, 1}, {0, 2}, {1, 3}, {2, 3}, {4, 5}, {4, 6}, {5, 7}, {6, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] edge : edges) {
            line(g, corners[edge[0]], corners[edge[1]], 0xFFFFFFFF);
        }
    }

    private static void line(GuiGraphics g, Vector3f a, Vector3f b, int color) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        for (int s = 0; s <= steps; s++) {
            int px = Math.round(a.x + dx * s / steps);
            int py = Math.round(a.y + dy * s / steps);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private void drawBones(GuiGraphics g, Font font, PreviewEntity entity, EntityRenderState state) {
        if (bones == null) {
            bones = entity.bones(state);
        }
        Set<String> animated = entity.animatedBones();
        int panelW = 150;
        int x = width - panelW - PAD;
        int y = PAD;
        g.fill(x - 4, y - 4, width - PAD + 4, Math.min(height - PAD, y + (bones.size() + 1) * LINE + 4), 0xA0000000);
        g.drawString(font, AdminText.clip(font, Component.translatable(KEY + "bones_title", bones.size()).getString(), panelW),
                x, y, AdminTheme.FRAME_TEXT, false);
        y += LINE + 2;
        for (PreviewEntity.BoneLine bone : bones) {
            if (y > height - PAD - LINE) {
                g.drawString(font, "…", x, y, AdminTheme.FRAME_TEXT_DIM, false);
                break;
            }
            int color = animated.contains(bone.name()) ? AdminTheme.GOLD_LIGHT : AdminTheme.FRAME_TEXT_DIM;
            g.drawString(font, AdminText.clip(font, bone.name(), panelW - bone.depth() * 6), x + bone.depth() * 6, y, color, false);
            y += LINE;
        }
    }

    private void drawDebug(GuiGraphics g, Font font, PreviewEntity entity, float boxW, float boxH) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "box %.2f × %.2f · eye %.2f", boxW, boxH, entity.entity().getEyeHeight()));
        String variant = entity.variantId();
        if (variant != null) {
            lines.add("variant " + variant);
        }
        lines.add(String.format(Locale.ROOT, "health %.1f / %.1f (preview instance)", entity.entity().getHealth(),
                entity.entity().getMaxHealth()));
        lines.add("age " + entity.entity().tickCount + " ticks · " + (playing ? "playing" : "paused") + " · ×" + speed);
        lines.addAll(entity.controllerStates());
        lines.add("renderer " + entity.renderer().getClass().getName());
        int x = viewX0() + PAD;
        int y = height - PAD - 20 - lines.size() * LINE;
        g.fill(x - 4, y - 4, x + 320, y + lines.size() * LINE + 2, 0xA0000000);
        for (String line : lines) {
            g.drawString(font, AdminText.clip(font, line, 316), x, y, AdminTheme.FRAME_TEXT, false);
            y += LINE;
        }
    }
}
