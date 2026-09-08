package at.koopro.wizardsandbeasts.client.trinket;

import at.koopro.wizardsandbeasts.item.trinket.FamousWizardCardItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Holding a Famous Wizard Card up the way you hold a map.
 *
 * <p>A card is something you <em>read</em>. Vanilla's default hold puts it in a fist off to one
 * side of the screen, pointing away, which is the pose for a tool; the two-handed map pose puts it
 * flat in front of your face where the portrait is actually legible. Vanilla reaches that pose
 * through {@code p_109377_.getItem() instanceof MapItem} in {@code ItemInHandRenderer}, a check no
 * mod item can satisfy without becoming a map, so the pose is reproduced here instead and the
 * default hold is cancelled.
 *
 * <p><b>Only when the other hand is free.</b> That is vanilla's own rule for maps, and it is the
 * one that matters: a player with a wand in the off-hand still needs to see the wand, and a card
 * spread across both hands would hide it.
 *
 * <p>The numbers below are lifted from {@code ItemInHandRenderer#renderTwoHandedMap} and
 * {@code #renderMapHand} unchanged, so a card and a map bob, tilt and sway identically. What
 * differs is the last step: where the map draws its own 128x128 quad, this submits the card's item
 * model, which is what makes each wizard's portrait show.
 */
@NullMarked
public final class WizardCardHandRenderer {

    /**
     * How big the card sits relative to the map that shares its pose. A map fills 0.76 blocks
     * across; a playing card that size would be a poster, so it is brought down to about a
     * hand's width.
     */
    private static final float CARD_SCALE = 0.55f;

    private WizardCardHandRenderer() {}

    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isScoping()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof FamousWizardCardItem) || !player.getOffhandItem().isEmpty()) {
            return;
        }

        event.setCanceled(true);
        renderTwoHandedCard(event, mc, player, stack);
    }

    private static void renderTwoHandedCard(RenderHandEvent event, Minecraft mc, LocalPlayer player,
                                            ItemStack stack) {
        PoseStack pose = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        int light = event.getPackedLight();
        float swing = event.getSwingProgress();
        float equip = event.getEquipProgress();

        pose.pushPose();

        float swingRoot = Mth.sqrt(swing);
        float bobY = -0.2f * Mth.sin(swing * (float) Math.PI);
        float bobZ = -0.4f * Mth.sin(swingRoot * (float) Math.PI);
        pose.translate(0.0f, -bobY / 2.0f, bobZ);

        // Looking down brings the card up into view and flattens it toward horizontal, exactly as
        // a map does — it is the gesture that makes the pose read as "reading something".
        float tilt = mapTilt(event.getInterpolatedPitch());
        pose.translate(0.0f, 0.04f + equip * -1.2f + tilt * -0.5f, -0.72f);
        pose.mulPose(Axis.XP.rotationDegrees(tilt * -85.0f));

        if (!player.isInvisible()) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(90.0f));
            renderHoldingHand(mc, player, pose, collector, light, HumanoidArm.RIGHT);
            renderHoldingHand(mc, player, pose, collector, light, HumanoidArm.LEFT);
            pose.popPose();
        }

        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(swingRoot * (float) Math.PI) * 20.0f));
        pose.scale(2.0f, 2.0f, 2.0f);

        // The camera looks down -Z, so the face turned toward it is the model's south face — the
        // one the portrait is painted on. No flip needed; the card is already the right way round.
        pose.scale(CARD_SCALE, CARD_SCALE, CARD_SCALE);
        ItemStackRenderState renderState = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(renderState, stack, ItemDisplayContext.FIXED,
                player.level(), player, player.getId());
        renderState.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);

        pose.popPose();
    }

    /** One hand cupped under the card. Straight from {@code ItemInHandRenderer#renderMapHand}. */
    private static void renderHoldingHand(Minecraft mc, AbstractClientPlayer player, PoseStack pose,
                                          SubmitNodeCollector collector, int light, HumanoidArm arm) {
        AvatarRenderer<AbstractClientPlayer> renderer =
                mc.getEntityRenderDispatcher().getPlayerRenderer(player);
        pose.pushPose();
        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        pose.mulPose(Axis.YP.rotationDegrees(92.0f));
        pose.mulPose(Axis.XP.rotationDegrees(45.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(side * -41.0f));
        pose.translate(side * 0.3f, -1.1f, 0.45f);
        Identifier skin = player.getSkin().body().texturePath();
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(pose, collector, light, skin,
                    player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE), player);
        } else {
            renderer.renderLeftHand(pose, collector, light, skin,
                    player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE), player);
        }
        pose.popPose();
    }

    /** 0 looking level or up, 1 looking straight down. Vanilla's own curve. */
    private static float mapTilt(float pitchDegrees) {
        float f = Mth.clamp(1.0f - pitchDegrees / 45.0f + 0.1f, 0.0f, 1.0f);
        return -Mth.cos(f * (float) Math.PI) * 0.5f + 0.5f;
    }
}
