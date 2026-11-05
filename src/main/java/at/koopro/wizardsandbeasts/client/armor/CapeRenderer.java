package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.item.armor.CapeItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * Every cape cut ({@code school_cape}, {@code travel_cloak}, ...), one model per cut, sheets from
 * {@code tools/wardrobe_model.py}. Swings {@code capeRoot} with vanilla's own cape physics and folds
 * {@code capeHem} clear of the robe's trailing half ({@link ClothMotion#capeRoot},
 * {@link ClothMotion#capeHem}); the inputs come from the wearer's state through {@link #MOTION}.
 */
public class CapeRenderer<R extends HumanoidRenderState & GeoRenderState>
        extends WizardArmorRenderer<CapeItem, R> {

    /**
     * {@code {capeLean, capeFlap, strideAmplitude}} for the wearer this frame, set by
     * {@code WornGeoCurioRenderer} from the avatar's render state. Absent (a non-player wearer):
     * the cape hangs at vanilla's resting angle.
     */
    public static final DataTicket<float[]> MOTION = DataTicket.create("wardrobe_cape_motion", float[].class);

    public CapeRenderer(String assetName) {
        super(assetName);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        float[] motion = renderPassInfo.renderState().getGeckolibData(MOTION);
        float lean = motion == null ? 0f : motion[0];
        float flap = motion == null ? 0f : motion[1];
        float stride = motion == null ? 0f : motion[2];
        snapshots.ifPresent(ClothMotion.CAPE_ROOT, s -> s.setRotX(ClothMotion.capeRoot(lean, flap)));
        snapshots.ifPresent(ClothMotion.CAPE_HEM, s -> s.setRotX(ClothMotion.capeHem(lean, flap, stride)));
    }
}
