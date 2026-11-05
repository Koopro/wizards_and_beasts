package at.koopro.wizardsandbeasts.client.armor;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.equipment.CapturedBodyPose;
import at.koopro.wizardsandbeasts.equipment.wardrobe.Outfit;
import at.koopro.wizardsandbeasts.equipment.wardrobe.Wardrobe;
import at.koopro.wizardsandbeasts.item.armor.WizardArmorItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
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

    /**
     * The wardrobe rank of the stack being drawn (0..3). Bone groups {@code rank<N>},
     * {@code rank<N>ArmR} and {@code rank<N>ArmL} show when {@code N <= rank}.
     */
    public static final DataTicket<Integer> RANK = DataTicket.create("wardrobe_rank", Integer.class);

    /**
     * The sheet the stack's livery draws, or {@link #BASE_SHEET} for the model's own. Always set, so a
     * render state reused between frames or pieces can never carry another stack's livery.
     */
    public static final DataTicket<Identifier> LIVERY_TEXTURE =
            DataTicket.create("wardrobe_livery_texture", Identifier.class);

    /** Sentinel for {@link #LIVERY_TEXTURE}: draw the model's base sheet. */
    public static final Identifier BASE_SHEET =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "base_sheet");

    /** Whether a cape is drawn over this piece's wearer; a robe then drops its lowered hood or cowl. */
    public static final DataTicket<Boolean> CAPE_WORN = DataTicket.create("wardrobe_cape_worn", Boolean.class);

    /** Whether the wearer has a hat on; a raised hood is then drawn down (the hat wins). */
    public static final DataTicket<Boolean> HAT_WORN = DataTicket.create("wardrobe_hat_worn", Boolean.class);

    /** A robe's hood worn down as a cowl on the back ({@code tools/wardrobe_model.py}). */
    private static final String COWL_BONE = "cowl";

    private static final String[] RANK_SUFFIXES = {"", "ArmR", "ArmL"};

    /** Model name used for livery sheets; {@code null} for a renderer built off a redirected model. */
    private final String assetName;

    /** Defaulted paths off one set name — see {@link WizardArmorGeoModel} for the layout. */
    public WizardArmorRenderer(String assetName) {
        super(new WizardArmorGeoModel<>(assetName));
        this.assetName = assetName;
    }

    /** For a set whose texture is redirected off the shared model, as the mask castings are. */
    protected WizardArmorRenderer(GeoModel<T> model) {
        super(model);
        this.assetName = null;
    }

    /**
     * Reads the stack's wardrobe {@link Outfit} onto the render state: its rank, and the sheet its
     * livery draws. Read here because the stack is gone by bone-adjust time (same as the hood).
     */
    @Override
    public void addRenderData(T animatable, RenderData renderData, R renderState, float partialTick) {
        super.addRenderData(animatable, renderData, renderState, partialTick);

        Outfit outfit = Outfit.of(renderData.itemStack());
        renderState.addGeckolibData(RANK, outfit.rank());
        Identifier sheet = null;
        if (assetName != null && animatable instanceof WizardArmorItem item) {
            sheet = Wardrobe.liveryTexture(assetName, item.defaultLivery(), outfit.livery());
        }
        renderState.addGeckolibData(LIVERY_TEXTURE, sheet == null ? BASE_SHEET : sheet);
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
        } else {
            placeFromCapturedPose(renderState, pose, snapshots);
            moveCloth(pose, snapshots);
        }
        showRanks(renderState, snapshots);
        boolean cowl = !capeWorn(renderState);
        snapshots.ifPresent(COWL_BONE, s -> s.skipRender(!cowl).skipChildrenRender(!cowl));
    }

    /** Whether a hat is worn over this robe this frame (set by {@code WornGeoCurioRenderer}). */
    protected static boolean hatWorn(GeoRenderState renderState) {
        return Boolean.TRUE.equals(renderState.getGeckolibData(HAT_WORN));
    }

    /** Whether a cape is drawn over the wearer this frame (set by {@code WornGeoCurioRenderer}). */
    protected static boolean capeWorn(GeoRenderState renderState) {
        return Boolean.TRUE.equals(renderState.getGeckolibData(CAPE_WORN));
    }

    private void placeFromCapturedPose(R renderState, CapturedBodyPose pose, BoneSnapshots snapshots) {
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

    /**
     * The robe skirt (option B, see {@link ClothMotion}): each lower half turns with its own leg, the
     * hip skirt's front lifts when both legs come forward. Only on the Curios path, the only one that
     * draws a robe body; a model without these bones is untouched.
     */
    private static void moveCloth(CapturedBodyPose pose, BoneSnapshots snapshots) {
        float body = pose.xRot(ArmorSegment.CHEST);
        float right = pose.xRot(ArmorSegment.RIGHT_LEG);
        float left = pose.xRot(ArmorSegment.LEFT_LEG);
        snapshots.ifPresent(ClothMotion.LEG_R, s -> s.setRotX(ClothMotion.lowerHalf(right, body)));
        snapshots.ifPresent(ClothMotion.LEG_L, s -> s.setRotX(ClothMotion.lowerHalf(left, body)));
        snapshots.ifPresent(ClothMotion.HIP_FRONT, s -> s.setRotX(ClothMotion.hipFront(right, left)));
    }

    /**
     * Shows every rank group up to the stack's rank and hides the rest. Both states are set every
     * frame, as {@code HoodedArmorRenderer} does for the hood: a snapshot carrying a stale skip flag
     * would hide a group for good.
     */
    private void showRanks(R renderState, BoneSnapshots snapshots) {
        Integer ticket = renderState.getGeckolibData(RANK);
        int rank = ticket == null ? 0 : ticket;
        for (int n = 1; n <= Outfit.MAX_RANK; n++) {
            boolean visible = n <= rank;
            for (String suffix : RANK_SUFFIXES) {
                snapshots.ifPresent("rank" + n + suffix, s -> s
                        .skipRender(!visible)
                        .skipChildrenRender(!visible));
            }
        }
    }
}
