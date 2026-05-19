package at.koopro.wizardsandbeasts.client.spell;

import at.koopro.wizardsandbeasts.event.spell.ProtegoShieldHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public final class ProtegoCubeRenderer {
    private static final float BLUE_R = 0.30f;
    private static final float BLUE_G = 0.60f;
    private static final float BLUE_B = 1.00f;
    private static final float BLUE_A = 0.45f;
    private static final double CUBE_HALF_XZ = 1.05;
    private static final double CUBE_HALF_Y = 1.10;

    private ProtegoCubeRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.lightning());
        Matrix4f matrix = poseStack.last().pose();

        for (Player player : mc.level.players()) {
            if (!hasActiveProtego(player)) {
                continue;
            }
            Vec3 pos = player.getPosition(partialTick);
            double cx = pos.x;
            double cy = pos.y + (player.getBbHeight() * 0.5);
            double cz = pos.z;

            float minX = (float) (cx - CUBE_HALF_XZ);
            float maxX = (float) (cx + CUBE_HALF_XZ);
            float minY = (float) (cy - CUBE_HALF_Y);
            float maxY = (float) (cy + CUBE_HALF_Y);
            float minZ = (float) (cz - CUBE_HALF_XZ);
            float maxZ = (float) (cz + CUBE_HALF_XZ);

            // Front
            quad(matrix, consumer, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ);
            // Back
            quad(matrix, consumer, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ);
            // Left
            quad(matrix, consumer, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ);
            // Right
            quad(matrix, consumer, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ);
            // Top
            quad(matrix, consumer, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
            // Bottom
            quad(matrix, consumer, minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ);
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }

    private static boolean hasActiveProtego(Player player) {
        // Player tags are server-authored and may not always be visible client-side,
        // so use synced effects as the visual source of truth.
        return player.getTags().contains(ProtegoShieldHandler.PROTEGO_ACTIVE_TAG)
                || (player.hasEffect(MobEffects.RESISTANCE) && player.hasEffect(MobEffects.ABSORPTION));
    }

    private static void quad(Matrix4f matrix, VertexConsumer consumer,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4) {
        // Render both windings so the cube remains visible in first person (camera inside shield)
        // and third person (camera outside shield).
        consumer.addVertex(matrix, x1, y1, z1).setColor(BLUE_R, BLUE_G, BLUE_B, BLUE_A);
        consumer.addVertex(matrix, x2, y2, z2).setColor(BLUE_R, BLUE_G, BLUE_B, BLUE_A);
        consumer.addVertex(matrix, x3, y3, z3).setColor(BLUE_R, BLUE_G, BLUE_B, BLUE_A);
        consumer.addVertex(matrix, x4, y4, z4).setColor(BLUE_R, BLUE_G, BLUE_B, BLUE_A);

        consumer.addVertex(matrix, x4, y4, z4).setColor(BLUE_R, BLUE_G, BLUE_B, BLUE_A);
        consumer.addVertex(matrix, x3, y3, z3).setColor(BLUE_R, BLUE_G, BLUE_B, BLUE_A);
        consumer.addVertex(matrix, x2, y2, z2).setColor(BLUE_R, BLUE_G, BLUE_B, BLUE_A);
        consumer.addVertex(matrix, x1, y1, z1).setColor(BLUE_R, BLUE_G, BLUE_B, BLUE_A);
    }
}
