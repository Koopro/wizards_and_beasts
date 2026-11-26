package at.koopro.wizardsandbeasts.client.floo;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlock;
import at.koopro.wizardsandbeasts.block.floo.FlooGrateBlockEntity;
import at.koopro.wizardsandbeasts.block.floo.HearthFire;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Draws the fire on a Floo grate. An ordinary fire is vanilla's own fire, crossed over the logs. The green roar is the
 * same fire recoloured to emerald (tools/floo_hearth_art.py): a tapered column rising from the grate to its full
 * height in {@link #RISE_TICKS} — the flames "roared and turned emerald green, rising higher than Fred" (CoS ch. 4).
 *
 * <p>Sprites come from the block atlas, like vanilla's burning-entity flame, so the frames animate. Every tile is one
 * block square: one texel per model pixel (MOJANG_STYLE.md); a rising roar slides up out of the floor, never stretched.
 */
@NullMarked
public class FlooGrateRenderer implements BlockEntityRenderer<FlooGrateBlockEntity, FlooGrateRenderer.State> {

    static final int RISE_TICKS = 8;
    /**
     * The roar's shape: a tapered column rising from the grate, not a wall filling the opening (owner 11-26: filling the
     * whole opening "looks a bit weird"). Each sheet is {depth z in the cell (0 = room side), centre offset from the
     * grate across the opening in thirds of a 3-wide opening, height as a fraction of the opening height}. Tallest behind
     * the traveller (higher than a person: "rising higher than Fred", CoS ch. 4), lower toward the sides so stone shows
     * at the edges, and only a low fringe in front licking round the legs, so the traveller stays visible.
     */
    private static final float[][] SHEETS = {
            {0.90F, 0.00F, 0.92F}, {0.90F, -0.55F, 0.70F}, {0.90F, 0.55F, 0.70F},
            {0.72F, -0.30F, 0.82F}, {0.72F, 0.30F, 0.82F}, {0.72F, -0.85F, 0.42F}, {0.72F, 0.85F, 0.42F},
            {0.12F, -0.40F, 0.30F}, {0.12F, 0.40F, 0.30F},
    };
    /** Vertical step between stacked fire tiles: half a tile, so each overlaps the one below by half. */
    private static final float STEP = 0.5F;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final Material FLOO_0 = Sheets.BLOCKS_MAPPER.apply(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "floo/floo_fire_0"));
    private static final Material FLOO_1 = Sheets.BLOCKS_MAPPER.apply(
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "floo/floo_fire_1"));

    private final MaterialSet materials;

    public FlooGrateRenderer(BlockEntityRendererProvider.Context context) {
        this.materials = context.materials();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FlooGrateBlockEntity grate, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(grate, state, partialTick, cameraPos, breakProgress);
        state.fire = state.blockState.getValueOrElse(FlooGrateBlock.FIRE, HearthFire.NONE);
        state.yRot = 180.0F - state.blockState.getValueOrElse(FlooGrateBlock.FACING, Direction.NORTH).toYRot();
        state.width = grate.openingWidth();
        state.height = grate.openingHeight();
        long now = grate.getLevel() != null ? grate.getLevel().getGameTime() : 0L;
        state.roarAge = Math.max(0.0F, now - grate.roarSince() + partialTick);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.fire.burning()) {
            return;
        }
        boolean floo = state.fire == HearthFire.FLOO;
        TextureAtlasSprite a = materials.get(floo ? FLOO_0 : ModelBakery.FIRE_0);
        TextureAtlasSprite b = materials.get(floo ? FLOO_1 : ModelBakery.FIRE_1);
        int width = state.width, height = state.height;
        float rise = Math.min(1.0F, state.roarAge / RISE_TICKS);
        float eased = 1.0F - (1.0F - rise) * (1.0F - rise);

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        collector.submitCustomGeometry(poseStack, Sheets.cutoutBlockSheet(), (pose, vc) -> {
            if (!floo) {
                // On the logs: vanilla's fire, two planes crossed, one block tall.
                crossed(pose, vc, 0.0F, 5.0F / 16.0F, 1.0F, a, b, false);
                return;
            }
            // Each sheet is a stack of whole fire tiles overlapping by half (as vanilla's burning-entity flame
            // overlaps), counted down from its top so the top edge is always flame tips. While the roar rises the
            // stack slides up out of the hearth floor, which hides what is still below it: nothing is ever clipped.
            float across = width / 3.0F;
            for (int i = 0; i < SHEETS.length; i++) {
                float z = SHEETS[i][0], x = SHEETS[i][1] * across, h = SHEETS[i][2] * height * eased;
                int n = 0;
                for (float y = h - 1.0F; y > -1.0F; y -= STEP, n++) {
                    plane(pose, vc, x, y, 1.0F, 0.0F, z, 1.0F, z, ((n + i) & 1) == 0 ? a : b, ((n + i * 3) & 2) != 0);
                }
            }
        });
        poseStack.popPose();
    }

    /** Two diagonal planes through the cell at column {@code col}, from {@code y} up {@code tile} blocks. */
    private static void crossed(PoseStack.Pose pose, VertexConsumer vc, float col, float y, float tile,
                                TextureAtlasSprite a, TextureAtlasSprite b, boolean mirror) {
        plane(pose, vc, col, y, tile, 0.0F, 0.0F, 1.0F, 1.0F, a, mirror);
        plane(pose, vc, col, y, tile, 0.0F, 1.0F, 1.0F, 0.0F, b, !mirror);
    }

    /** One double-sided fire sheet from (x0, z0) to (x1, z1) inside the cell, clipped (not stretched) at the top. */
    private static void plane(PoseStack.Pose pose, VertexConsumer vc, float col, float y, float tile,
                              float x0, float z0, float x1, float z1, TextureAtlasSprite sprite, boolean mirror) {
        float u0 = mirror ? sprite.getU1() : sprite.getU0();
        float u1 = mirror ? sprite.getU0() : sprite.getU1();
        float vBottom = sprite.getV1();
        float vTop = sprite.getV1() - (sprite.getV1() - sprite.getV0()) * tile;
        float ax = col + x0, az = z0, bx = col + x1, bz = z1, y1 = y + tile;
        vertex(pose, vc, ax, y, az, u0, vBottom);
        vertex(pose, vc, bx, y, bz, u1, vBottom);
        vertex(pose, vc, bx, y1, bz, u1, vTop);
        vertex(pose, vc, ax, y1, az, u0, vTop);
        vertex(pose, vc, ax, y1, az, u0, vTop);
        vertex(pose, vc, bx, y1, bz, u1, vTop);
        vertex(pose, vc, bx, y, bz, u1, vBottom);
        vertex(pose, vc, ax, y, az, u0, vBottom);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v) {
        vc.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public AABB getRenderBoundingBox(FlooGrateBlockEntity grate) {
        BlockPos pos = grate.getBlockPos();
        int reach = grate.openingWidth() / 2 + 1;
        return new AABB(pos).inflate(reach, 0, reach).expandTowards(0, grate.openingHeight(), 0);
    }

    public static final class State extends BlockEntityRenderState {
        HearthFire fire = HearthFire.NONE;
        float yRot;
        int width = FlooGrateBlockEntity.DEFAULT_OPENING;
        int height = FlooGrateBlockEntity.DEFAULT_OPENING;
        float roarAge;
    }
}
