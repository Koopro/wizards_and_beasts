package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.bench.BenchPart;
import at.koopro.wizardsandbeasts.wand.bench.BenchTool;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlock;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * Draws the whole two-block bench from its working part.
 *
 * <p>A {@link BenchPart#SOLO} bench (no room for its second half yet) is drawn without the {@value #SIDE_BONE} bone:
 * the turning end alone, lathe and treadle, and none of the rack that would stand inside whatever blocks its side.
 * Hidden through the per-frame bone snapshot ({@code skipRender} + {@code skipChildrenRender}), as the hooded robes do:
 * GeckoLib 5.4.5 has no {@code setHidden}, and the model is shared by every bench on screen.
 *
 * <p>Each {@link BenchTool} is drawn only while it lies on the bench ({@link #TOOLS}); the vise, the awl and the
 * calipers are the bench's own and always drawn.
 *
 * <p>The rig is {@code wandmakers_bench_rig}, not {@code wandmakers_bench}: the bare name would resolve its texture to
 * the flat block sprite the item still uses. See {@code tools/bench_model.py}.
 */
@NullMarked
public class WandmakersBenchRenderer<R extends BlockEntityRenderState & GeoRenderState>
        extends GeoBlockRenderer<WandmakersBenchBlockEntity, R> {

    /** The parent of everything on the marrying end: rack, vise, varnish, tools, bottles, lantern. */
    public static final String SIDE_BONE = "side";

    /** Which tools are on the bench being drawn: {@link BenchTool#bit()}s, read off the block entity per frame. */
    public static final DataTicket<Integer> TOOLS = DataTicket.create("wandmakers_bench_tools", Integer.class);

    public WandmakersBenchRenderer() {
        super(new DefaultedBlockGeoModel<>(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wandmakers_bench_rig")));
    }

    @Override
    public void addRenderData(WandmakersBenchBlockEntity bench, @Nullable Void relatedObject, R renderState, float partialTick) {
        renderState.addGeckolibData(TOOLS, bench.toolMask());
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        BlockState state = renderPassInfo.renderState().getGeckolibData(DataTickets.BLOCKSTATE);
        boolean paired = state == null || !state.hasProperty(WandmakersBenchBlock.PART)
                || state.getValue(WandmakersBenchBlock.PART) != BenchPart.SOLO;
        snapshots.ifPresent(SIDE_BONE, snapshot -> snapshot.skipRender(!paired).skipChildrenRender(!paired));
        // Each tool is drawn only while it lies on the bench. Every bone is set every frame, never only the hidden
        // ones: a snapshot keeps a stale skipRender from the bench drawn before it.
        Integer mask = renderPassInfo.renderState().getGeckolibData(TOOLS);
        int tools = mask == null ? 0 : mask;
        for (BenchTool tool : BenchTool.values()) {
            boolean shown = (tools & tool.bit()) != 0;
            snapshots.ifPresent(tool.bone(), snapshot -> snapshot.skipRender(!shown).skipChildrenRender(!shown));
        }
    }

    /** The rig spans two blocks and its rack and lantern rise into a second: two blocks of margin keep it unculled. */
    @Override
    public AABB getRenderBoundingBox(WandmakersBenchBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(2.0);
    }
}
