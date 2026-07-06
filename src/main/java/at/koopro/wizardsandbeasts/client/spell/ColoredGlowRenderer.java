package at.koopro.wizardsandbeasts.client.spell;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import at.koopro.wizardsandbeasts.util.GlowDebugTags;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws non-team, per-entity colored glow halos in world-space.
 * This runs in parallel to vanilla glowing and does not use scoreboard teams.
 */
public final class ColoredGlowRenderer {
    public static final String COLOR_TAG_PREFIX = GlowDebugTags.COLOR_TAG_PREFIX;
    public static final String HASH_COLOR_TAG = GlowDebugTags.HASH_COLOR_TAG;

    private static final double MAX_RENDER_DISTANCE_SQR = 64.0 * 64.0;
    private static final float OUTER_ALPHA = 0.10f;
    private static final float MID_ALPHA = 0.22f;
    private static final float CORE_ALPHA = 0.45f;
    private static final float OUTER_SCALE = 1.18f;
    private static final float MID_SCALE = 1.04f;
    private static final float CORE_SCALE = 0.90f;

    private static final List<GlowColorProvider> PROVIDERS = new ArrayList<>();

    private ColoredGlowRenderer() {}

    static {
        registerProvider(ColoredGlowRenderer::fromEntityGlowTags);
    }

    public static void registerProvider(GlowColorProvider provider) {
        PROVIDERS.add(provider);
    }

    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.lightning());
        Matrix4f matrix = poseStack.last().pose();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvisible()) {
                continue;
            }
            if (living.distanceToSqr(camPos) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }

            Integer color = resolveColor(living, partialTick);
            if (color == null) {
                continue;
            }

            renderEntityGlow(matrix, consumer, living, partialTick, color);
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }

    private static @Nullable Integer resolveColor(LivingEntity entity, float partialTick) {
        for (GlowColorProvider provider : PROVIDERS) {
            Integer color = provider.getColor(entity, partialTick);
            if (color != null) {
                return color;
            }
        }
        return null;
    }

    /**
     * Initial opt-in rule:
     * - "neo_colored_glow" => deterministic color from UUID hash
     * - "neo_colored_glow_RRGGBB" => explicit color override
     */
    private static @Nullable Integer fromEntityGlowTags(LivingEntity entity, float partialTick) {
        if (entity.getTags().contains(HASH_COLOR_TAG)) {
            return colorFromUuid(entity);
        }
        for (String tag : entity.getTags()) {
            if (tag.startsWith(COLOR_TAG_PREFIX) && tag.length() == COLOR_TAG_PREFIX.length() + 6) {
                try {
                    return Integer.parseInt(tag.substring(COLOR_TAG_PREFIX.length()), 16);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static int colorFromUuid(LivingEntity entity) {
        int hash = entity.getUUID().hashCode();
        float hue = (hash & 0xFFFFFF) / (float) 0xFFFFFF;
        return Mth.hsvToRgb(hue, 0.85f, 1.0f) & 0xFFFFFF;
    }

    private static void renderEntityGlow(Matrix4f matrix, VertexConsumer consumer, LivingEntity entity, float partialTick, int color) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        Vec3 pos = entity.getPosition(partialTick);
        float cx = (float) pos.x;
        float cy = (float) (pos.y + entity.getBbHeight() * 0.52);
        float cz = (float) pos.z;

        float radius = (Math.max(entity.getBbWidth(), 0.6f) * 0.92f) + 0.18f;
        float halfHeight = (entity.getBbHeight() * 0.55f) + 0.10f;

        renderLayer(matrix, consumer, cx, cy, cz, radius * OUTER_SCALE, halfHeight * OUTER_SCALE, r, g, b, OUTER_ALPHA);
        renderLayer(matrix, consumer, cx, cy, cz, radius * MID_SCALE, halfHeight * MID_SCALE, r, g, b, MID_ALPHA);
        renderLayer(matrix, consumer, cx, cy, cz, radius * CORE_SCALE, halfHeight * CORE_SCALE, r, g, b, CORE_ALPHA);
    }

    private static void renderLayer(Matrix4f matrix, VertexConsumer consumer,
                                    float cx, float cy, float cz,
                                    float radius, float halfHeight,
                                    float r, float g, float b, float a) {
        // XY
        quad(matrix, consumer,
                cx - radius, cy - halfHeight, cz,
                cx + radius, cy - halfHeight, cz,
                cx + radius, cy + halfHeight, cz,
                cx - radius, cy + halfHeight, cz,
                r, g, b, a);

        // YZ
        quad(matrix, consumer,
                cx, cy - halfHeight, cz - radius,
                cx, cy - halfHeight, cz + radius,
                cx, cy + halfHeight, cz + radius,
                cx, cy + halfHeight, cz - radius,
                r, g, b, a);

        // XZ
        quad(matrix, consumer,
                cx - radius, cy, cz - radius,
                cx + radius, cy, cz - radius,
                cx + radius, cy, cz + radius,
                cx - radius, cy, cz + radius,
                r, g, b, a * 0.70f);
    }

    private static void quad(Matrix4f matrix, VertexConsumer consumer,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float r, float g, float b, float a) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a);
        consumer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a);
    }

    @FunctionalInterface
    public interface GlowColorProvider {
        Integer getColor(LivingEntity entity, float partialTick);
    }
}
