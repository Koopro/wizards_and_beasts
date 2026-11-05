package at.koopro.wizardsandbeasts.client.equipment.curios;

import at.koopro.wizardsandbeasts.client.equipment.CapturedBodyPose;
import at.koopro.wizardsandbeasts.client.equipment.EquipmentRenderStateModifier;
import at.koopro.wizardsandbeasts.client.armor.CapeRenderer;
import at.koopro.wizardsandbeasts.client.armor.WizardArmorRenderer;
import at.koopro.wizardsandbeasts.equipment.EquipmentAPI;
import at.koopro.wizardsandbeasts.item.armor.CapeItem;
import at.koopro.wizardsandbeasts.item.armor.WizardArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Draws any GeckoLib wardrobe piece worn in a Curios slot — robe, hat, mask — with no per-item code.
 *
 * <p><b>One model, one renderer, two paths.</b> Each {@link WizardArmorItem} already binds a
 * {@link GeoArmorRenderer} (its model, texture, hood logic, mask casting). This class asks the item
 * for that very renderer and runs a normal GeckoLib render pass with it, so a robe looks identical
 * whether it is drawn from an armour slot or a Curios slot, and a new robe needs a model and a
 * data-map entry — nothing here.
 *
 * <p><b>What it supplies that the armour layer would.</b>
 * <ul>
 *   <li>A fresh {@code HumanoidRenderState} per piece to carry GeckoLib's data — the wearer's own state
 *       is never written to, so two pieces on one player cannot overwrite each other's tickets.</li>
 *   <li>{@link CapturedBodyPose}: the limb transforms, copied now (see that class for why).</li>
 *   <li>The slot the model was authored for ({@link WizardArmorItem#armorType()}), which picks the
 *       bone group: a robe body draws torso and sleeves (and its hood), a hat or mask the head.</li>
 * </ul>
 *
 * <p>Only the piece's slot segments are drawn, so a robe in the body slot never also draws its own
 * trousers; those are the separate {@code _legs} armour item.
 *
 * <p>First person: nothing, matching vanilla, which draws no armour on the first-person arm either.
 */
@NullMarked
public final class WornGeoCurioRenderer implements ICurioRenderer {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <S extends LivingEntityRenderState, M extends EntityModel<? super S>> void render(
            ItemStack stack, SlotContext slotContext, PoseStack poseStack, SubmitNodeCollector collector,
            int packedLight, S renderState, RenderLayerParent<S, M> renderLayerParent,
            EntityRendererProvider.Context context, float yRotation, float xRotation) {
        if (!(stack.getItem() instanceof WizardArmorItem item)
                || !(renderState instanceof HumanoidRenderState)
                || !(renderLayerParent.getModel() instanceof HumanoidModel<?> parentModel)
                || EquipmentRenderStateModifier.isConcealed(renderState)) {
            return;
        }
        if (item instanceof CapeItem && wearsGlider(renderState)) {
            // Vanilla draws no cape over an elytra either; the wings take the back.
            return;
        }
        EquipmentSlot slot = item.armorType().getSlot();
        GeoArmorRenderer renderer = GeoRenderProvider.of(stack).getGeoArmorRenderer(stack, slot);
        if (renderer == null) {
            return;
        }

        HumanoidRenderState pieceState = new HumanoidRenderState();
        pieceState.entityType = renderState.entityType;
        pieceState.x = renderState.x;
        pieceState.y = renderState.y;
        pieceState.z = renderState.z;
        pieceState.ageInTicks = renderState.ageInTicks;
        pieceState.partialTick = renderState.partialTick;
        pieceState.lightCoords = packedLight;
        pieceState.outlineColor = renderState.outlineColor;
        pieceState.isInvisible = renderState.isInvisible;

        GeoRenderState geoState = (GeoRenderState) pieceState;
        renderer.fillRenderState(item,
                new GeoArmorRenderer.RenderData(stack, slot, slotContext.entity(), parentModel),
                geoState, renderState.partialTick);
        geoState.addGeckolibData(CapturedBodyPose.TICKET, CapturedBodyPose.capture(parentModel));
        if (item instanceof CapeItem) {
            geoState.addGeckolibData(CapeRenderer.MOTION, capeMotion(renderState));
        } else {
            // A robe under a drawn cape drops its lowered hood: the cape's collar takes that place.
            boolean capeDrawn = EquipmentAPI.getEquippedCloak(slotContext.entity()).getItem() instanceof CapeItem
                    && !wearsGlider(renderState);
            geoState.addGeckolibData(WizardArmorRenderer.CAPE_WORN, capeDrawn);
            geoState.addGeckolibData(WizardArmorRenderer.HAT_WORN,
                    !EquipmentAPI.getEquippedHat(slotContext.entity()).isEmpty());
        }

        renderer.performRenderPass(geoState, poseStack, collector,
                Minecraft.getInstance().levelRenderer.levelRenderState.cameraRenderState);
    }

    private static boolean wearsGlider(LivingEntityRenderState renderState) {
        return renderState instanceof HumanoidRenderState humanoid
                && humanoid.chestEquipment.has(DataComponents.GLIDER);
    }

    /**
     * {@code {capeLean, capeFlap, strideAmplitude}}: vanilla's own cape physics for a player, and the
     * leg swing amplitude {@code HumanoidModel#setupAnim} uses ({@code 1.4 * walkAnimationSpeed / speedValue}).
     */
    private static float[] capeMotion(LivingEntityRenderState renderState) {
        float lean = 0f;
        float flap = 0f;
        if (renderState instanceof AvatarRenderState avatar) {
            lean = avatar.capeLean;
            flap = avatar.capeFlap;
        }
        float speedValue = renderState instanceof HumanoidRenderState humanoid && humanoid.speedValue > 0f
                ? humanoid.speedValue : 1f;
        return new float[]{lean, flap, 1.4f * renderState.walkAnimationSpeed / speedValue};
    }
}
