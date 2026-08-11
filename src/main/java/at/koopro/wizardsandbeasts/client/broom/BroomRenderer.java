package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.BroomSlot;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

import java.util.Map;

/**
 * Renders the master broom rig, showing one variant per slot.
 *
 * <p>Until now this renderer only applied tilt. The rig holds every part of every broom at once —
 * five shafts, five bundles, four caps and so on — and nothing ever hid the ones a given broom does
 * not use, so every broom in the world drew all 24 variants stacked inside each other. The data
 * layer had shipped {@code model_slots} and the model had shipped the bones; the piece that reads
 * one and hides the other was missing.
 *
 * <p>Selection is captured into a {@link DataTicket} on the render state during
 * {@link #addRenderData}, which runs with the entity in hand, and read back in
 * {@link #adjustModelBonesForRender}, which does not touch the entity at all.
 */
public class BroomRenderer<R extends EntityRenderState & GeoRenderState>
        extends GeoEntityRenderer<BroomEntity, R> {

    public static final DataTicket<Float> PITCH_TILT =
            DataTicket.create("broom_pitch_tilt", Float.class);
    public static final DataTicket<Float> ROLL_TILT =
            DataTicket.create("broom_roll_tilt", Float.class);
    public static final DataTicket<Float> FORWARD_LEAN =
            DataTicket.create("broom_forward_lean", Float.class);

    /**
     * The slot → variant map this broom draws.
     *
     * <p>Carried as data rather than resolved at render time because the render pass has no entity:
     * by then the broom is a render state, and {@code BroomDefinitionRegistry} is server-synced data
     * that must be read while the entity is still reachable.
     */
    @SuppressWarnings("unchecked")
    public static final DataTicket<Map<BroomSlot, Identifier>> MODEL_SLOTS =
            DataTicket.create("broom_model_slots", (Class<Map<BroomSlot, Identifier>>) (Class<?>) Map.class);

    public BroomRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "broom")));
    }

    @Override
    public void addRenderData(BroomEntity broom, Void unused, R renderState, float partialTick) {
        super.addRenderData(broom, unused, renderState, partialTick);

        renderState.addGeckolibData(PITCH_TILT,
                Mth.lerp(partialTick, broom.getPrevPitchTilt(), broom.getPitchTilt()));
        renderState.addGeckolibData(ROLL_TILT,
                Mth.lerp(partialTick, broom.getPrevRollTilt(), broom.getRollTilt()));
        renderState.addGeckolibData(FORWARD_LEAN,
                Mth.lerp(partialTick, broom.getPrevForwardLean(), broom.getForwardLean()));
        renderState.addGeckolibData(MODEL_SLOTS, broom.resolveDefinition().modelSlots());
    }

    /**
     * Multiplies the definition's {@code wood_tint} into the render colour.
     *
     * <p>Mirrors {@code ScaledBeastRenderer}: an opaque white tint is the no-op, anything else is
     * multiplied in. Note this tints the <em>whole</em> model — GeckoLib's render colour is one value
     * per pass, so the binding band and the bristles take the wood colour too. Exempting the band
     * needs a second render layer; logged rather than bodged.
     */
    @Override
    public int getRenderColor(@NonNull BroomEntity broom, Void unused, float partialTick) {
        int base = super.getRenderColor(broom, unused, partialTick);
        int tint = broom.resolveDefinition().woodTint();
        return tint == BroomDefinition.UNTINTED ? base : ARGB.multiply(base, tint);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<R> info, BoneSnapshots bones) {
        super.adjustModelBonesForRender(info, bones);

        float pitchTilt   = info.getOrDefaultGeckolibData(PITCH_TILT, 0f);
        float rollTilt    = info.getOrDefaultGeckolibData(ROLL_TILT, 0f);
        float forwardLean = info.getOrDefaultGeckolibData(FORWARD_LEAN, 0f);

        bones.ifPresent("broom_body", snapshot -> {
            snapshot.setRotX((pitchTilt + forwardLean) * Mth.DEG_TO_RAD);
            snapshot.setRotZ(rollTilt * Mth.DEG_TO_RAD);
        });

        applyBoneVisibility(bones,
                info.getOrDefaultGeckolibData(MODEL_SLOTS, BroomSlot.defaults()));
    }

    /**
     * Shows the selected variant in each slot and hides every other.
     *
     * <p>Every variant is addressed explicitly rather than by hiding the slot's container bone and
     * re-showing one child: {@code skipChildrenRender} stops the renderer descending into a bone at
     * all, so a hidden container cannot have one child brought back. The container bones stay
     * visible because the animations key off them — {@code fly_forward} rotates {@code bristles},
     * not {@code bristles_birch}, which is what lets one clip cover every variant of a slot.
     *
     * <p>An unset optional slot hides all of its variants and draws nothing, which is the point of
     * {@link BroomSlot#isRequired()} being false for four of the six.
     */
    private static void applyBoneVisibility(BoneSnapshots bones, Map<BroomSlot, Identifier> slots) {
        for (BroomSlot slot : BroomSlot.renderOrder()) {
            Identifier selected = slots.get(slot);
            String selectedBone = selected == null ? null : slot.boneName(selected.getPath());

            for (String variant : slot.variants()) {
                String boneName = slot.boneName(variant);
                boolean visible = boneName.equals(selectedBone);
                bones.ifPresent(boneName, snapshot -> {
                    snapshot.skipRender(!visible);
                    snapshot.skipChildrenRender(!visible);
                });
            }
        }
    }
}
