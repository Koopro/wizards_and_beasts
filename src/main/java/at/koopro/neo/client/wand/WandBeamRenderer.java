package at.koopro.neo.client.wand;

import at.koopro.neo.Neo;
import at.koopro.neo.item.WandItem;
import at.koopro.neo.network.ClientSpellDataHolder;
import at.koopro.neo.spell.Spell;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Renders a multi-layered magical beam from the wand tip to the target
 * while the player holds right-click with a wand.
 */
public final class WandBeamRenderer {

    /** Tracks whether the beam was active last frame (for extension animation start). */
    private static boolean wasActive = false;
    /** The tick when the current beam started (for extension animation). */
    private static int beamStartTick = 0;

    private WandBeamRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) { wasActive = false; return; }

        boolean holdingWand = player.getMainHandItem().getItem() instanceof WandItem
                || player.getOffhandItem().getItem() instanceof WandItem;

        boolean shouldRender;
        if (Neo.debugForceBeam) {
            shouldRender = holdingWand;
        } else {
            shouldRender = player.isUsingItem()
                    && player.getUseItem().getItem() instanceof WandItem;
        }

        if (!shouldRender) {
            wasActive = false;
            return;
        }

        // Track when the beam started for extension animation
        if (!wasActive) {
            beamStartTick = player.tickCount;
        }
        wasActive = true;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        // Beam origin: slightly forward from the player's eye
        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 beamStart = eyePos
                .add(look.scale(0.8))
                .add(right.scale(0.35))
                .add(up.scale(-0.35));

        // Beam end: raycast to block or max range
        float range = BeamSettings.range;
        HitResult hit = player.pick(range, partialTick, false);
        Vec3 fullEnd = hit.getType() == HitResult.Type.MISS
                ? eyePos.add(look.scale(range))
                : hit.getLocation();

        // Extension animation: beam extends outward over time
        float elapsed = (player.tickCount - beamStartTick) + partialTick;
        float maxReach = elapsed * BeamSettings.extensionSpeed;
        double fullDist = beamStart.distanceTo(fullEnd);
        Vec3 beamEnd;
        if (maxReach >= fullDist) {
            beamEnd = fullEnd;
        } else {
            Vec3 dir = fullEnd.subtract(beamStart).normalize();
            beamEnd = beamStart.add(dir.scale(maxReach));
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        // Apply active spell color in normal gameplay (debug mode uses manual settings)
        if (!Neo.debugForceBeam) {
            Spell activeSpell = ClientSpellDataHolder.get().getActiveSpell();
            if (activeSpell != null) {
                BeamSettings.applySpellColor(activeSpell.getColor());
            }
        }

        float time = (player.tickCount + partialTick) * BeamSettings.speed;

        // Lightning render type provides additive blending, no depth test, POSITION_COLOR quads
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.lightning());

        Matrix4f matrix = poseStack.last().pose();

        for (int i = 0; i < BeamSettings.layers.length; i++) {
            BeamSettings.LayerSettings layer = BeamSettings.layers[i];
            renderLayer(matrix, consumer, beamStart, beamEnd, camPos, time,
                    layer.width, layer.r, layer.g, layer.b, layer.alpha, layer.noiseAmp, i * 100);
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }

    private static void renderLayer(Matrix4f matrix, VertexConsumer consumer,
                                     Vec3 start, Vec3 end, Vec3 camPos, float time,
                                     float width, float r, float g, float b,
                                     float a, float noiseAmp, int seed) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.1) return;

        int segments = Math.max(8, (int) (length * BeamSettings.segmentsPerUnit));
        Vec3 dir = delta.normalize();

        // Perpendicular axes for noise displacement
        Vec3 midPoint = start.add(end).scale(0.5);
        Vec3 toCamera = camPos.subtract(midPoint).normalize();
        Vec3 perp1 = dir.cross(toCamera);
        if (perp1.lengthSqr() < 0.001) {
            perp1 = dir.cross(new Vec3(0, 1, 0));
        }
        perp1 = perp1.normalize();
        Vec3 perp2 = perp1.cross(dir).normalize();

        // Generate noise-displaced control points
        Vec3[] points = new Vec3[segments + 1];
        float[] alphas = new float[segments + 1];

        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            Vec3 basePos = start.add(delta.scale(t));

            // Multi-frequency sine noise for erratic jitter
            float nx = noise(t, time, seed, 13.7f, 2.3f)
                    + noise(t, time, seed, 29.3f, 4.1f) * 0.5f
                    + noise(t, time, seed, 43.1f, 7.3f) * 0.25f;
            float ny = noise(t, time, seed + 50, 17.1f, 3.1f)
                    + noise(t, time, seed + 50, 31.7f, 5.3f) * 0.5f
                    + noise(t, time, seed + 50, 47.3f, 8.1f) * 0.25f;

            nx *= noiseAmp * 0.015f;
            ny *= noiseAmp * 0.015f;

            points[i] = basePos.add(perp1.scale(nx)).add(perp2.scale(ny));

            // Fade at endpoints
            float fade = 1.0f;
            if (t < 0.05f) fade = t / 0.05f;
            else if (t > 0.95f) fade = (1.0f - t) / 0.05f;
            alphas[i] = a * fade;
        }

        // Draw camera-facing quads between adjacent points
        for (int i = 0; i < segments; i++) {
            Vec3 p1 = points[i];
            Vec3 p2 = points[i + 1];

            Vec3 segDir = p2.subtract(p1);
            if (segDir.lengthSqr() < 1e-8) continue;
            segDir = segDir.normalize();

            Vec3 mid = p1.add(p2).scale(0.5);
            Vec3 toCam = camPos.subtract(mid).normalize();
            Vec3 perp = segDir.cross(toCam);
            if (perp.lengthSqr() < 1e-8) continue;
            perp = perp.normalize().scale(width);

            float a1 = alphas[i];
            float a2 = alphas[i + 1];

            Vec3 p1s = p1.subtract(perp);
            Vec3 p1a = p1.add(perp);
            Vec3 p2a = p2.add(perp);
            Vec3 p2s = p2.subtract(perp);

            consumer.addVertex(matrix, (float) p1s.x, (float) p1s.y, (float) p1s.z)
                    .setColor(r, g, b, a1);
            consumer.addVertex(matrix, (float) p1a.x, (float) p1a.y, (float) p1a.z)
                    .setColor(r, g, b, a1);
            consumer.addVertex(matrix, (float) p2a.x, (float) p2a.y, (float) p2a.z)
                    .setColor(r, g, b, a2);
            consumer.addVertex(matrix, (float) p2s.x, (float) p2s.y, (float) p2s.z)
                    .setColor(r, g, b, a2);
        }
    }

    private static float noise(float t, float time, int seed, float freq, float speed) {
        return (float) Math.sin(t * freq + time * speed + seed * 0.7f);
    }
}
