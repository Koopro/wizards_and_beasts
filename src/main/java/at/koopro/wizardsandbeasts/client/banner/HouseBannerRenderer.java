package at.koopro.wizardsandbeasts.client.banner;

import at.koopro.wizardsandbeasts.block.HouseBannerBlock;
import at.koopro.wizardsandbeasts.block.HouseBannerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a house banner as cloth that moves: a wooden rod across the top, and hanging from it a
 * sheet with real thickness that leans off the wall and stirs, and that parts around anyone who
 * walks into it.
 *
 * <p>The banner used to be one block-model quad per half — flat, still, and paper-thin. A baked
 * model cannot move, so the block is now {@code RenderShape.INVISIBLE} and this renderer, hung off
 * the lower half's block entity, rebuilds both halves as one mesh every frame.
 *
 * <p>The art is unchanged: the {@code _top} and {@code _bottom} textures read as one 32x64 image.
 * Everything here is authored in that image's texels, facing {@code NORTH} (the identity of the
 * block's own shape rotation), and converted to block units at the vertex. Texel u runs from the
 * east edge westwards, because that is how the old north-facing quad mapped it; v runs down from
 * the top of the upper block. The rod keeps its paint — the 3D rod samples the painted one — but
 * the sheet starts below it, so the painted copy is never drawn flat behind the real one.
 */
public class HouseBannerRenderer implements BlockEntityRenderer<HouseBannerBlockEntity, HouseBannerRenderer.State> {

    private static final float TAU = (float) (Math.PI * 2.0);

    // -- the art, in texels ----------------------------------------------------------------------
    // These repeat tools/banner_textures.py. The silhouette is the textures' alpha, so if that
    // tool moves the cloth these must follow, or the edge strips hang off empty air.

    private static final float TEXELS = 32.0F;          // per block, both ways
    private static final float HALF_V = 32.0F;          // first row of the _bottom texture
    private static final float CLOTH_U0 = 4.0F;
    private static final float CLOTH_U1 = 28.0F;
    private static final float CLOTH_MID_U = 16.0F;
    private static final float CLOTH_V0 = 3.0F;         // the cloth's top edge, just under the rod
    private static final float TAIL_V = 57.0F;          // bottom edge at the two outer tails
    private static final float NOTCH_V = 48.0F;         // bottom edge at the point of the swallowtail

    // Painted texels reused as solid colour: a stretch of rod between the brackets, a finial, and
    // a pixel of the cloth's dark outline for the cut edges.
    private static final float ROD_U0 = 9.0F, ROD_V0 = 1.0F, ROD_U1 = 23.0F, ROD_V1 = 3.0F;
    private static final float FINIAL_U0 = 1.0F, FINIAL_V0 = 1.0F, FINIAL_U1 = 2.0F, FINIAL_V1 = 3.0F;
    private static final float EDGE_U = 4.5F, EDGE_V = 20.5F;

    // -- the mesh, in blocks ---------------------------------------------------------------------

    /** Where the cloth hangs, from the north face. Four pixels off the back face. */
    private static final float PLANE_Z = 12.0F / 16.0F;
    private static final float HALF_THICKNESS = 0.375F / 16.0F;
    private static final float ROD_RADIUS = 0.75F / 16.0F;
    private static final float FINIAL_RADIUS = 1.0F / 16.0F;

    /** The band the cloth may occupy, matching the block's outline shape: never through the wall, never outside it. */
    private static final float Z_MIN = 8.0F / 16.0F + HALF_THICKNESS;
    private static final float Z_MAX = 15.8F / 16.0F;
    /** Pushed cloth may slide sideways, but not out of its own block. */
    private static final float X_MARGIN = 0.02F;

    private static final float[] ROWS = gridLines(CLOTH_V0, TAIL_V);
    private static final float[] COLS = gridLines(CLOTH_U0, CLOTH_U1);
    private static final int HALF_ROW = indexOf(ROWS, HALF_V);

    /** Every period in {@link #offset} divides this, so wrapping game time never makes the cloth jump. */
    private static final long CYCLE = 200L;

    /** Bodies count as slightly wider than their hitbox, so cloth never grazes a shoulder. */
    private static final float PUSH_MARGIN = 0.10F;
    /** How far above a head and below a foot the push fades out. Without it the cloth would tear along the body's ends. */
    private static final float PUSH_FEATHER = 0.35F;
    /** How strongly pushed cloth slides aside rather than stretching flat over whoever is in it. */
    private static final float PUSH_SPREAD = 0.9F;
    /** A crowd bends a banner no differently than the few nearest to it do. */
    private static final int MAX_PUSHERS = 4;

    /** Box corners by bit (1 = max x, 2 = max y, 4 = max z), per face, counter-clockwise from outside. */
    private static final int[][] BOX_FACES = {
            {1, 0, 2, 3}, {4, 5, 7, 6}, {5, 1, 3, 7}, {0, 4, 6, 2}, {6, 7, 3, 2}, {0, 1, 5, 4}};
    private static final float[][] BOX_NORMALS = {
            {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    private static final Pusher[] NOBODY = new Pusher[0];

    private final Map<Block, Textures> textures = new HashMap<>();

    /**
     * Something the cloth has to get out of the way of, as a vertical cylinder in the banner's own
     * north-facing frame: centred at ({@code x}, {@code z}), standing from {@code y0} to {@code y1}.
     */
    record Pusher(float x, float z, float y0, float y1, float radius) {
    }

    @Override
    public @NonNull State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(@NonNull HouseBannerBlockEntity banner, @NonNull State state, float partialTick,
                                   @NonNull Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(banner, state, partialTick, cameraPos, breakProgress);
        Level level = banner.getLevel();
        BlockPos pos = banner.getBlockPos();

        state.yRot = 180.0F - state.blockState.getValueOrElse(HouseBannerBlock.FACING, Direction.NORTH).toYRot();
        // The block entity's own light is the lower block's; the top half is lit where it hangs.
        state.upperLight = level != null ? LevelRenderer.getLightColor(level, pos.above()) : state.lightCoords;
        long gameTime = level != null ? level.getGameTime() : 0L;
        state.time = Math.floorMod(gameTime, CYCLE) + partialTick;
        // Neighbouring banners in a hall must not move in lockstep; same hash vanilla banners use.
        state.seed = Math.floorMod(pos.getX() * 7 + pos.getY() * 9 + pos.getZ() * 13, 100) / 100.0F * TAU;

        // Anyone standing in the cloth bends it. The query is one small box around the two blocks,
        // and it only ever runs for banners already close enough to be drawn.
        state.pushers.clear();
        if (level != null) {
            AABB area = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0).inflate(0.75);
            for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, area, e -> !e.isSpectator())) {
                Vec3 at = body.getPosition(partialTick);
                state.pushers.add(pusherFor(at.x, at.y, at.z, body.getBbWidth(), body.getBbHeight(), pos, state.yRot));
                if (state.pushers.size() == MAX_PUSHERS) {
                    break;
                }
            }
        }

        Textures art = textures.computeIfAbsent(state.blockState.getBlock(), HouseBannerRenderer::texturesFor);
        state.top = art.top();
        state.bottom = art.bottom();
    }

    @Override
    public void submit(@NonNull State state, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector collector,
                       @NonNull CameraRenderState camera) {
        Identifier top = state.top, bottom = state.bottom;
        if (top == null || bottom == null) {
            return;
        }
        float time = state.time, seed = state.seed;
        int upper = state.upperLight, lower = state.lightCoords;
        // Copied out of the render state: the geometry callbacks below run after this method returns.
        Pusher[] pushers = state.pushers.isEmpty() ? NOBODY : state.pushers.toArray(new Pusher[0]);

        // Both passes share one cloth, so the seam at the texture split cannot tear. Each node
        // carries its own x as well: cloth walked into slides aside, it does not only sink back.
        float[][] gx = new float[ROWS.length][COLS.length];
        float[][] gz = new float[ROWS.length][COLS.length];
        float[] point = new float[2];
        for (int i = 0; i < ROWS.length; i++) {
            for (int j = 0; j < COLS.length; j++) {
                clothPoint(COLS[j], ROWS[i], time, seed, pushers, point);
                gx[i][j] = point[0];
                gz[i][j] = point[1];
            }
        }

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(top), (pose, vc) -> {
            sheet(pose, vc, gx, gz, 0, HALF_ROW, 0.0F, upper, lower);
            edges(pose, vc, gx, gz, time, seed, pushers, upper, lower);
            rod(pose, vc, upper);
        });
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCull(bottom), (pose, vc) ->
                sheet(pose, vc, gx, gz, HALF_ROW, ROWS.length - 1, HALF_V, upper, lower));
        poseStack.popPose();
    }

    @Override
    public @NonNull AABB getRenderBoundingBox(@NonNull HouseBannerBlockEntity banner) {
        BlockPos pos = banner.getBlockPos();
        return AABB.encapsulatingFullBlocks(pos, pos.above());
    }

    // -- motion ----------------------------------------------------------------------------------

    /**
     * Where the cloth at texel (u, v) ends up, as {@code {x, z}} in {@code out}: its own slight
     * motion, then bent around whatever is standing in it, then clamped into the band the block's
     * outline allows.
     */
    private static void clothPoint(float u, float v, float t, float seed, Pusher[] pushers, float[] out) {
        float x = 1.0F - u / TEXELS;
        float z = PLANE_Z + offset(u, v, t, seed);
        if (pushers.length > 0) {
            float y = 2.0F - v / TEXELS;
            float back = 0.0F, aside = 0.0F;
            for (Pusher p : pushers) {
                float depth = pushOut(x, y, z, p);
                if (Math.abs(depth) > Math.abs(back)) {
                    back = depth;
                    // Away from the body's axis, most where the cloth is folding around it and
                    // nothing on the axis itself, where there is no side to fall to.
                    aside = PUSH_SPREAD * Math.abs(depth) * (x - p.x()) / p.radius();
                }
            }
            x += aside;
            z += back;
        }
        out[0] = Mth.clamp(x, X_MARGIN, 1.0F - X_MARGIN);
        out[1] = Mth.clamp(z, Z_MIN, Z_MAX);
    }

    /**
     * The cloth's own motion, in blocks, at tick {@code t}: a constant lean off the wall, then a
     * slow swing, a long ripple, and a little cross-drift and tail movement — all scaled by distance
     * from the rod, so the top edge stays pinned. Deliberately small: a banner indoors barely stirs,
     * and vanilla's own flag only rocks about two degrees.
     */
    private static float offset(float u, float v, float t, float seed) {
        float s = (v - CLOTH_V0) / (TAIL_V - CLOTH_V0);              // 0 at the rod, 1 at the tails
        float c = (u - CLOTH_MID_U) / (CLOTH_U1 - CLOTH_MID_U);      // -1..1 across
        float px = -0.45F * s                                        // hangs a little proud of the wall
                + 0.35F * s * Mth.sin(TAU * t / 200.0F + seed)
                + 0.35F * s * (0.35F + 0.65F * s) * Mth.sin(TAU * (0.9F * s - t / 100.0F) + seed)
                + 0.12F * s * Mth.sin(TAU * (0.9F * c + 0.6F * s - t / 50.0F) + 2.0F * seed)
                + 0.10F * s * s * s * Mth.sin(TAU * (1.5F * c - t / 50.0F) + 3.0F * seed);
        return px / 16.0F;
    }

    /**
     * How far the cloth at ({@code x}, {@code y}, {@code z}) has to move to clear {@code p}.
     *
     * <p>It is pushed straight out to the surface of the body's cylinder, on whichever side it
     * already hangs, so the cloth takes the shape of whoever walks into it instead of sliding along
     * them. The push fades over {@link #PUSH_FEATHER} above the head and below the feet: cut off
     * sharply, a passing player would slice the banner in half.
     */
    private static float pushOut(float x, float y, float z, Pusher p) {
        float dx = x - p.x();
        float r = p.radius();
        if (dx <= -r || dx >= r) {
            return 0.0F;
        }
        float half = Mth.sqrt(r * r - dx * dx);
        boolean front = z < p.z();
        float delta = (front ? p.z() - half : p.z() + half) - z;
        if (front ? delta >= 0.0F : delta <= 0.0F) {
            return 0.0F;                                              // already clear of the body
        }
        float fade = 1.0F;
        if (y < p.y0()) {
            fade = Math.max(0.0F, 1.0F - (p.y0() - y) / PUSH_FEATHER);
        } else if (y > p.y1()) {
            fade = Math.max(0.0F, 1.0F - (y - p.y1()) / PUSH_FEATHER);
        }
        return delta * fade * fade * (3.0F - 2.0F * fade);
    }

    /**
     * A body's cylinder in the banner's authored frame — the rotation {@link #submit} applies, undone,
     * so the cloth can be pushed in the same north-facing space it is built in.
     */
    static Pusher pusherFor(double ex, double ey, double ez, float width, float height, BlockPos pos, float yRot) {
        double dx = ex - pos.getX() - 0.5, dz = ez - pos.getZ() - 0.5;
        double radians = Math.toRadians(-yRot);
        double cos = Math.cos(radians), sin = Math.sin(radians);
        float x = (float) (dx * cos + dz * sin + 0.5);
        float z = (float) (-dx * sin + dz * cos + 0.5);
        float y = (float) (ey - pos.getY());
        return new Pusher(x, z, y, y + height, width / 2.0F + PUSH_MARGIN);
    }

    // -- geometry --------------------------------------------------------------------------------

    /**
     * Rows {@code rowFrom..rowTo} of the cloth as two sheets, front and back, a cloth's thickness
     * apart. Both are drawn so each side is lit as the side it is; the nearer sheet hides the other.
     */
    private static void sheet(PoseStack.Pose pose, VertexConsumer vc, float[][] gx, float[][] gz,
                              int rowFrom, int rowTo, float vOrigin, int upper, int lower) {
        for (int i = rowFrom; i < rowTo; i++) {
            for (int j = 0; j + 1 < COLS.length; j++) {
                cloth(pose, vc, gx, gz, i + 1, j, vOrigin, upper, lower, -1.0F);
                cloth(pose, vc, gx, gz, i + 1, j + 1, vOrigin, upper, lower, -1.0F);
                cloth(pose, vc, gx, gz, i, j + 1, vOrigin, upper, lower, -1.0F);
                cloth(pose, vc, gx, gz, i, j, vOrigin, upper, lower, -1.0F);

                cloth(pose, vc, gx, gz, i, j, vOrigin, upper, lower, 1.0F);
                cloth(pose, vc, gx, gz, i, j + 1, vOrigin, upper, lower, 1.0F);
                cloth(pose, vc, gx, gz, i + 1, j + 1, vOrigin, upper, lower, 1.0F);
                cloth(pose, vc, gx, gz, i + 1, j, vOrigin, upper, lower, 1.0F);
            }
        }
    }

    /** One sheet vertex. {@code side} is -1 for the front sheet (facing -z) and +1 for the back. */
    private static void cloth(PoseStack.Pose pose, VertexConsumer vc, float[][] gx, float[][] gz, int i, int j,
                              float vOrigin, int upper, int lower, float side) {
        int up = Math.max(i - 1, 0), down = Math.min(i + 1, ROWS.length - 1);
        int east = Math.max(j - 1, 0), west = Math.min(j + 1, COLS.length - 1);
        // Slope of the surface in block units. Across, that is measured against where the nodes
        // actually are, because a body walking through moves them sideways as well as back.
        float span = gx[i][west] - gx[i][east];
        float slopeX = Math.abs(span) < 1.0E-5F ? 0.0F : (gz[i][west] - gz[i][east]) / span;
        float slopeY = -TEXELS * (gz[down][j] - gz[up][j]) / (ROWS[down] - ROWS[up]);
        float n = -side / Mth.sqrt(slopeX * slopeX + slopeY * slopeY + 1.0F);
        vertex(pose, vc, gx[i][j], 2.0F - ROWS[i] / TEXELS, gz[i][j] + side * HALF_THICKNESS,
                COLS[j] / TEXELS, (ROWS[i] - vOrigin) / TEXELS, light(ROWS[i], upper, lower),
                n * slopeX, n * slopeY, -n);
    }

    /**
     * The cloth's cut edges — both sides and the swallowtail — as strips joining the front sheet to
     * the back. Without them a banner seen edge-on is two sheets of paper with air between.
     */
    private static void edges(PoseStack.Pose pose, VertexConsumer vc, float[][] gx, float[][] gz,
                              float time, float seed, Pusher[] pushers, int upper, int lower) {
        float u = EDGE_U / TEXELS, v = EDGE_V / TEXELS;
        int lastCol = COLS.length - 1;

        for (int side = 0; side < 2; side++) {
            int j = side == 0 ? 0 : lastCol;
            float nx = side == 0 ? 1.0F : -1.0F;                         // column 0 is the east edge
            for (int i = 0; i + 1 < ROWS.length; i++) {
                float y0 = 2.0F - ROWS[i] / TEXELS, y1 = 2.0F - ROWS[i + 1] / TEXELS;
                int l0 = light(ROWS[i], upper, lower), l1 = light(ROWS[i + 1], upper, lower);
                vertex(pose, vc, gx[i + 1][j], y1, gz[i + 1][j] - HALF_THICKNESS, u, v, l1, nx, 0.0F, 0.0F);
                vertex(pose, vc, gx[i + 1][j], y1, gz[i + 1][j] + HALF_THICKNESS, u, v, l1, nx, 0.0F, 0.0F);
                vertex(pose, vc, gx[i][j], y0, gz[i][j] + HALF_THICKNESS, u, v, l0, nx, 0.0F, 0.0F);
                vertex(pose, vc, gx[i][j], y0, gz[i][j] - HALF_THICKNESS, u, v, l0, nx, 0.0F, 0.0F);
            }
        }

        // The swallowtail: a V from each outer tail up to the notch, off the mesh's rows, so the
        // cloth is sampled where the cut actually is.
        float[] a = new float[2], b = new float[2];
        for (int j = 0; j < lastCol; j++) {
            float ua = COLS[j], ub = COLS[j + 1];
            float va = hemV(ua), vb = hemV(ub);
            clothPoint(ua, va, time, seed, pushers, a);
            clothPoint(ub, vb, time, seed, pushers, b);
            float ya = 2.0F - va / TEXELS, yb = 2.0F - vb / TEXELS;
            // Perpendicular to the cut, on the side the air is — which is always below.
            float len = Mth.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (yb - ya) * (yb - ya));
            float nx = (yb - ya) / len, ny = -(b[0] - a[0]) / len;
            if (ny > 0.0F) {
                nx = -nx;
                ny = -ny;
            }
            vertex(pose, vc, a[0], ya, a[1] - HALF_THICKNESS, u, v, lower, nx, ny, 0.0F);
            vertex(pose, vc, b[0], yb, b[1] - HALF_THICKNESS, u, v, lower, nx, ny, 0.0F);
            vertex(pose, vc, b[0], yb, b[1] + HALF_THICKNESS, u, v, lower, nx, ny, 0.0F);
            vertex(pose, vc, a[0], ya, a[1] + HALF_THICKNESS, u, v, lower, nx, ny, 0.0F);
        }
    }

    /** The rod the cloth's top edge is tucked into, with a finial at each end. */
    private static void rod(PoseStack.Pose pose, VertexConsumer vc, int light) {
        float rodTop = 2.0F - 0.5F / TEXELS, rodBottom = 2.0F - 3.5F / TEXELS;
        box(pose, vc, 1.0F / TEXELS, rodBottom, PLANE_Z - ROD_RADIUS, 31.0F / TEXELS, rodTop, PLANE_Z + ROD_RADIUS,
                ROD_U0, ROD_V0, ROD_U1, ROD_V1, light);
        float finialBottom = 2.0F - 4.5F / TEXELS;
        box(pose, vc, 0.0F, finialBottom, PLANE_Z - FINIAL_RADIUS, 1.5F / TEXELS, 2.0F, PLANE_Z + FINIAL_RADIUS,
                FINIAL_U0, FINIAL_V0, FINIAL_U1, FINIAL_V1, light);
        box(pose, vc, 30.5F / TEXELS, finialBottom, PLANE_Z - FINIAL_RADIUS, 1.0F, 2.0F, PLANE_Z + FINIAL_RADIUS,
                FINIAL_U0, FINIAL_V0, FINIAL_U1, FINIAL_V1, light);
    }

    /** An axis-aligned box, every face showing the same texel rectangle. */
    private static void box(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float z0,
                            float x1, float y1, float z1, float u0, float v0, float u1, float v1, int light) {
        for (int f = 0; f < BOX_FACES.length; f++) {
            float[] n = BOX_NORMALS[f];
            for (int k = 0; k < 4; k++) {
                int c = BOX_FACES[f][k];
                vertex(pose, vc, (c & 1) != 0 ? x1 : x0, (c & 2) != 0 ? y1 : y0, (c & 4) != 0 ? z1 : z0,
                        (k == 0 || k == 3 ? u0 : u1) / TEXELS, (k < 2 ? v1 : v0) / TEXELS, light, n[0], n[1], n[2]);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v,
                               int light, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    // -- layout ----------------------------------------------------------------------------------

    /** The texel row where the swallowtail is cut at column {@code u}. */
    private static float hemV(float u) {
        float t = 1.0F - Math.abs(u - CLOTH_MID_U) / (CLOTH_U1 - CLOTH_MID_U);
        return TAIL_V - (TAIL_V - NOTCH_V) * t;
    }

    private static int light(float v, int upper, int lower) {
        return v < HALF_V ? upper : lower;
    }

    /**
     * Mesh lines from {@code from} to {@code to}: both ends, and every even texel between. Two texels
     * is fine enough for a fold to read as a curve, and it lands a line exactly on {@link #HALF_V}
     * where the texture changes.
     */
    private static float[] gridLines(float from, float to) {
        int first = (int) Math.floor(from / 2.0F) + 1;
        int last = (int) Math.ceil(to / 2.0F) - 1;
        float[] lines = new float[last - first + 3];
        lines[0] = from;
        for (int k = first; k <= last; k++) {
            lines[k - first + 1] = k * 2.0F;
        }
        lines[lines.length - 1] = to;
        return lines;
    }

    private static int indexOf(float[] lines, float value) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] == value) {
                return i;
            }
        }
        throw new IllegalStateException("no mesh line at texel " + value);
    }

    private static Textures texturesFor(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return new Textures(id.withPath(p -> "textures/block/" + p + "_top.png"),
                id.withPath(p -> "textures/block/" + p + "_bottom.png"));
    }

    private record Textures(Identifier top, Identifier bottom) {
    }

    public static final class State extends BlockEntityRenderState {
        final List<Pusher> pushers = new ArrayList<>();
        float yRot;
        float time;
        float seed;
        int upperLight;
        @Nullable Identifier top;
        @Nullable Identifier bottom;
    }
}
