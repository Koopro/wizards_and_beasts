package at.koopro.wizardsandbeasts.client.render.outline;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.render.outline.OutlineEntry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Draws {@link ClientBlockOutlineState} as a thin glowing edge over a faint tint.
 *
 * <h2>Why not the entity outline's post chain</h2>
 *
 * <p>The entity outline is a silhouette traced from an entity's own model in a separate target. A block has
 * no model in that target, and faking one would mean submitting invisible geometry just to be traced —
 * the marker-entity approach by another name. Blocks are boxes, and a box is twelve lines.
 *
 * <h2>The look</h2>
 *
 * <p>Three layers per block, all in the highlight's colour and all scaled by its alpha — so the last-second
 * fade ({@link OutlineEntry#colourAt}, applied at extraction) is only ever a change of alpha:
 * <ol>
 *   <li><b>Tint</b> — the faces at {@link #FILL_ALPHA}, through {@code RenderTypes.debugFilledBox()}.</li>
 *   <li><b>Halo</b> — the edges again, {@link #HALO_WIDTH_SCALE} times wider at {@link #HALO_ALPHA}: the glow.</li>
 *   <li><b>Core</b> — the edges at {@link #CORE_ALPHA} and the window's own outline width: the thin line.</li>
 * </ol>
 * <p>The edges go through {@code RenderTypes.linesTranslucent()}: vanilla's selection-outline pipeline, which
 * takes a per-vertex width. Depth-tested, so a highlight is seen where the block is seen and not through walls.
 *
 * <h2>No z-fighting</h2>
 *
 * <p>Three things keep the tint and lines off the block's own faces. The box is {@link #INFLATE}d outward;
 * both render types use {@code VIEW_OFFSET_Z_LAYERING}, which pulls geometry towards the camera by
 * distance / 4096 so the gap grows as depth precision shrinks; and neither writes depth, so overlapping
 * highlights blend instead of fighting each other.
 *
 * <h2>Extract, then draw</h2>
 *
 * <p>NeoForge's contract for stage rendering: gather in {@link ExtractLevelRenderStateEvent}, store on the
 * {@code LevelRenderState}, draw from that. Culling and shape lookups happen once per frame at extraction,
 * and the draw reads nothing but the extracted boxes.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class BlockOutlineRenderer {

    static final float FILL_ALPHA = 0.10f;
    static final float HALO_ALPHA = 0.22f;
    static final float HALO_WIDTH_SCALE = 2.5f;
    static final float CORE_ALPHA = 0.9f;

    /** Pushes the box just off the block's faces so the tint and edges sit on the surface, not in it. */
    private static final double INFLATE = 0.004;

    private static final AABB FULL_BLOCK = new AABB(0, 0, 0, 1, 1, 1);

    private static final ContextKey<List<Box>> BOXES =
            new ContextKey<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "block_outlines"));

    private BlockOutlineRenderer() {}

    @SubscribeEvent
    public static void onExtract(ExtractLevelRenderStateEvent event) {
        if (ClientBlockOutlineState.isEmpty()) {
            return;
        }
        ClientLevel level = event.getLevel();
        Frustum frustum = event.getFrustum();
        long gameTime = level.getGameTime();
        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Box> boxes = new ArrayList<>();
        for (Map.Entry<BlockPos, OutlineEntry> entry : ClientBlockOutlineState.blocks().entrySet()) {
            // Fully faded is skipped before the shape lookup: the server's REMOVE can land a tick after the
            // fade has already reached zero, and there is no point tracing a box nobody can see.
            int argb = entry.getValue().colourAt(gameTime, partialTick);
            if (argb == 0) {
                continue;
            }
            AABB bounds = boundsOf(level, entry.getKey());
            if (frustum.isVisible(bounds)) {
                boxes.add(new Box(bounds, argb));
            }
        }
        if (!boxes.isEmpty()) {
            event.getRenderState().setRenderData(BOXES, boxes);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        List<Box> boxes = event.getLevelRenderState().getRenderData(BOXES);
        if (boxes == null) {
            return;
        }
        Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth();

        RenderType fillType = RenderTypes.debugFilledBox();
        VertexConsumer fill = buffers.getBuffer(fillType);
        for (Box box : boxes) {
            BlockOutlineGeometry.faces(fill, pose, box.bounds(), cam.x, cam.y, cam.z,
                    ARGB.multiplyAlpha(box.argb(), FILL_ALPHA));
        }
        buffers.endBatch(fillType);

        // Halo before core, in one batch: the core is drawn last so the crisp line sits on top of its glow.
        RenderType lineType = RenderTypes.linesTranslucent();
        VertexConsumer lines = buffers.getBuffer(lineType);
        for (Box box : boxes) {
            BlockOutlineGeometry.edges(lines, pose, box.bounds(), cam.x, cam.y, cam.z,
                    ARGB.multiplyAlpha(box.argb(), HALO_ALPHA), width * HALO_WIDTH_SCALE);
        }
        for (Box box : boxes) {
            BlockOutlineGeometry.edges(lines, pose, box.bounds(), cam.x, cam.y, cam.z,
                    ARGB.multiplyAlpha(box.argb(), CORE_ALPHA), width);
        }
        buffers.endBatch(lineType);
    }

    /**
     * The block's own outline bounds, so a slab or a chest is traced at its real size; a full cube for air
     * and anything shapeless, so a revealed empty space still has a box.
     */
    private static AABB boundsOf(ClientLevel level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        AABB local = shape.isEmpty() ? FULL_BLOCK : shape.bounds();
        return local.move(pos).inflate(INFLATE);
    }

    /** One block to draw this frame, in world space. */
    private record Box(AABB bounds, int argb) {}
}
