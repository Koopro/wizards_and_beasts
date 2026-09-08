package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.item.armor.HoodedRobe;
import at.koopro.wizardsandbeasts.item.armor.RobeHood;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

import java.util.List;

/**
 * Base for a robe that carries its hood twice — once up over the head, once bunched on the
 * shoulders — and shows one of them per frame.
 *
 * <p><b>Why two bones rather than one animated one.</b> A hood pulled up is not the down hood
 * rotated: it is a shell around the head with an open front, and the down one is a roll of cloth
 * behind the neck. No rotation takes one to the other, so both are modelled and the renderer picks.
 *
 * <p><b>How the picking works.</b> {@code GeoBone} has no {@code setHidden} in GeckoLib 5.4.5, and
 * the model instance is shared by every wearer on screen, so the switch cannot be stored on the
 * model. It goes through the per-frame {@link software.bernie.geckolib.animation.state.BoneSnapshot}
 * instead — {@code skipRender} for the bone itself and {@code skipChildrenRender} for anything
 * parented under it — which is the same mechanism GeckoLib's own {@code DyeableGeoArmorRenderer}
 * uses. Both bones are set explicitly every frame, never just the hidden one: a snapshot that
 * carried a stale {@code skipRender} from the previous pass would leave the hood invisible in both
 * states.
 *
 * <p>The state itself is read off the {@link net.minecraft.world.item.ItemStack} in
 * {@link #addRenderData} and parked on the render state. Reading it at bone-adjust time is not an
 * option: the animatable and its stack are discarded after the render state is filled.
 *
 * <p><b>Why the chest piece claims the head bone.</b> A raised hood has to turn with the head, so it
 * hangs off {@code armorHead} in the model. By default GeckoLib only asks for that bone when it is
 * drawing a <em>helmet</em>, and a chest piece's head-bone geometry is dropped without a word — so
 * {@link #getSegmentsForSlot} adds {@code HEAD} to the chest slot. That also snaps the bone onto the
 * vanilla head {@code ModelPart} every frame, which is what makes it follow the wearer's look.
 *
 * <p>The lowered hood stays on {@code armorBody}: a hood on your shoulders does not turn when you do.
 */
public abstract class HoodedArmorRenderer<T extends Item & GeoItem, R extends HumanoidRenderState & GeoRenderState>
        extends WizardArmorRenderer<T, R> {

    /** Whether the hood on the stack being drawn is up. Absent is treated as down. */
    public static final DataTicket<Boolean> HOOD_UP =
            DataTicket.create("robe_hood_up", Boolean.class);

    protected HoodedArmorRenderer(String assetName) {
        super(assetName);
    }

    /**
     * The chest slot draws the head bone too, so the raised hood turns with the wearer.
     *
     * <p>Only for the chest: the legs and boots of the same set run through this renderer as well,
     * and a hood on the boots would be drawn twice.
     */
    @Override
    public List<ArmorSegment> getSegmentsForSlot(R renderState, EquipmentSlot slot) {
        if (slot != EquipmentSlot.CHEST) {
            return super.getSegmentsForSlot(renderState, slot);
        }

        List<ArmorSegment> base = super.getSegmentsForSlot(renderState, slot);
        List<ArmorSegment> withHead = new java.util.ArrayList<>(base.size() + 1);

        withHead.addAll(base);
        withHead.add(ArmorSegment.HEAD);

        return withHead;
    }

    @Override
    public void addRenderData(T animatable, RenderData renderData, R renderState, float partialTick) {
        super.addRenderData(animatable, renderData, renderState, partialTick);

        renderState.addGeckolibData(HOOD_UP, RobeHood.isUp(renderData.itemStack()));
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);

        boolean up = Boolean.TRUE.equals(renderPassInfo.renderState().getGeckolibData(HOOD_UP));

        setVisible(snapshots, HoodedRobe.HOOD_UP_BONE, up);
        setVisible(snapshots, HoodedRobe.HOOD_DOWN_BONE, !up);
    }

    /**
     * A bone with no cubes of its own still needs {@code skipChildrenRender}: hiding a parent only
     * hides its own geometry, and every cube of both hoods hangs off these two bones.
     */
    private static void setVisible(BoneSnapshots snapshots, String boneName, boolean visible) {
        snapshots.ifPresent(boneName, snapshot -> snapshot
                .skipRender(!visible)
                .skipChildrenRender(!visible));
    }
}
