package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.client.equipment.CapturedBodyPose;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * Shared base for the wardrobe's armour renderers — the counterpart of {@code GeoRendererHelper} for
 * worn gear, kept as a class rather than a factory method because {@code GeoArmorRenderer} is bound
 * by class name from the item (see {@code WizardArmorItem#rendererClassName}).
 *
 * <p>The {@code R} type parameter cannot be filled in with a concrete class. GeckoLib declares it as
 * {@code HumanoidRenderState & GeoRenderState}, an intersection satisfied only at runtime — its
 * mixin casts the vanilla render state to it — so every renderer below has to keep R open too.
 *
 * <p>Nothing here needs registering in {@code ClientSetup}. GeckoLib's {@code HumanoidArmorLayer}
 * mixin asks each worn item for its renderer as it draws.
 */
public class WizardArmorRenderer<T extends Item & GeoItem, R extends HumanoidRenderState & GeoRenderState>
        extends GeoArmorRenderer<T, R> {

    /** Defaulted paths off one set name — see {@link WizardArmorGeoModel} for the layout. */
    public WizardArmorRenderer(String assetName) {
        super(new WizardArmorGeoModel<>(assetName));
    }

    /** For a set whose texture is redirected off the shared model, as the mask castings are. */
    protected WizardArmorRenderer(GeoModel<T> model) {
        super(model);
    }

    /**
     * Places the armour bones on the wearer's limbs.
     *
     * <p>Worn as vanilla armour, GeckoLib does this itself by re-posing the armour model at flush
     * time. Worn as a Curios piece there is no such model to re-pose, so
     * {@code WornGeoCurioRenderer} hands over a {@link CapturedBodyPose} and the same placement is
     * done from it — identical arithmetic, including GeckoLib's per-segment pivot offsets, so a robe
     * body sits exactly where its chestplate form did.
     */
    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
        R renderState = renderPassInfo.renderState();
        CapturedBodyPose pose = renderState.getGeckolibData(CapturedBodyPose.TICKET);
        if (pose == null) {
            super.adjustModelBonesForRender(renderPassInfo, snapshots);
            return;
        }
        EquipmentSlot slot = renderState.getGeckolibData(DataTickets.EQUIPMENT_SLOT);
        if (slot == null) {
            return;
        }
        for (ArmorSegment segment : getSegmentsForSlot(renderState, slot)) {
            snapshots.ifPresent(getBoneNameForSegment(renderState, segment), snapshot -> {
                Vector3f bonePos = segment.modelPartMatcher.apply(
                        new Vector3f(pose.x(segment), pose.y(segment), pose.z(segment)));
                snapshot.setRotX(-pose.xRot(segment))
                        .setRotY(-pose.yRot(segment))
                        .setRotZ(pose.zRot(segment))
                        .setTranslateX(bonePos.x)
                        .setTranslateY(bonePos.y)
                        .setTranslateZ(bonePos.z);
            });
        }
    }
}
