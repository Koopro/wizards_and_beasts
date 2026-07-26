package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.spell.state.ClientSpellDataState;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.spell.core.CastType;
import at.koopro.wizardsandbeasts.spell.core.SpellIds;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import at.koopro.wizardsandbeasts.spell.cast.BeamRay;
import at.koopro.wizardsandbeasts.spell.cast.BeamRayResolver;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Renders a multi-layered magical beam from the wand tip to the target
 * while the player holds right-click with a wand.
 * <p>
 * Path and tube geometry follow the same idea as {@link net.minecraft.client.renderer.entity.LightningBoltRenderer}:
 * seeded cumulative random offsets (detrended so both ends stay on the ray) and four quads per segment
 * forming a square tube, rather than a single camera-facing ribbon.
 */
public final class WandBeamRenderer {
    private static final String LEVIOSA_ID = "wingardium_leviosa";

    private static boolean wasActive = false;
    private static int beamStartTick = 0;
    /** Last spell colour pushed into {@link BeamSettings}; {@code -1} = none applied yet. */
    private static int lastAppliedColor = -1;
    /** Stable while the beam is active so the bolt does not swim frame-to-frame. */
    private static long beamPathSeed = 0L;

    private WandBeamRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        // The entity-based beam system draws instead. Gated here rather than by unregistering the
        // event so the two can be compared in-game without a restart.
        if (WizardsAndBeastsMod.useNewBeamSystem) {
            wasActive = false;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            wasActive = false;
            return;
        }

        boolean holdingWand = player.getMainHandItem().getItem() instanceof WandItem
                || player.getOffhandItem().getItem() instanceof WandItem;

        boolean shouldRender;
        if (WizardsAndBeastsMod.debugForceBeam) {
            shouldRender = holdingWand;
        } else {
            Spell activeSpell = ClientSpellDataState.get().getActiveSpell();
            shouldRender = player.isUsingItem()
                    && player.getUseItem().getItem() instanceof WandItem;
            if (shouldRender && !isHeldChannelSpell(activeSpell)) {
                shouldRender = false;
            }
        }

        if (!shouldRender) {
            wasActive = false;
            return;
        }

        // Match server SpellCastC2SPayload: do not show the charge beam while the spell is on cooldown.
        if (!WizardsAndBeastsMod.debugForceBeam) {
            String activeSpellId = ClientSpellDataState.get().getActiveSpellId();
            if (activeSpellId != null) {
                long gameTime = player.level().getGameTime();
                if (ClientSpellDataState.get().isOnCooldown(activeSpellId, gameTime)) {
                    wasActive = false;
                    return;
                }
            }
        }

        if (!wasActive) {
            beamStartTick = player.tickCount;
            beamPathSeed = player.getUUID().getLeastSignificantBits() ^ ((long) beamStartTick << 16);
        }
        wasActive = true;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        Vec3 cachedTip = WandTipWorldCache.getWorldTipOrNull();
        Vec3 beamStart = cachedTip != null ? cachedTip : wandTipWorldPos(mc, player, partialTick, camera);

        // Mirror WandBeamChannelLogic exactly: clamp the range first, then resolve against the clamped
        // reach. Resolving against a fixed 50-block client setting and truncating afterwards made the
        // visual overshoot whatever the server actually hit.
        float elapsed = (player.tickCount - beamStartTick) + partialTick;
        float range = resolveBeamRange(player);
        float maxReach = Math.min(range, elapsed * BeamRayResolver.extensionBlocksPerTick());
        BeamRay ray = BeamRayResolver.resolve(player, partialTick, maxReach, BeamRayResolver.LIVING_FILTER);
        Vec3 beamEnd = ray.end();
        double fullDist = beamStart.distanceTo(beamEnd);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        if (!WizardsAndBeastsMod.debugForceBeam) {
            Spell activeSpell = ClientSpellDataState.get().getActiveSpell();
            // Only on change: applySpellColor writes the shared layer settings, so doing it every frame
            // stomped whatever the player had dialled in via the beam debug screen.
            if (activeSpell != null && activeSpell.getColor() != lastAppliedColor) {
                lastAppliedColor = activeSpell.getColor();
                BeamSettings.applySpellColor(lastAppliedColor);
            }
        }

        float flicker = 0.88f + 0.12f * Mth.sin((player.tickCount + partialTick) * BeamSettings.speed * 18f);

        float pathNoise = Math.max(
                BeamSettings.layers[BeamSettings.OUTER].noiseAmp,
                Math.max(
                        BeamSettings.layers[BeamSettings.MID].noiseAmp,
                        BeamSettings.layers[BeamSettings.CORE].noiseAmp));

        Vec3[] path = WandBeamGeometry.buildBeamPath(beamStart, beamEnd, beamPathSeed, pathNoise);
        if (path == null) {
            poseStack.popPose();
            return;
        }

        float beamScrollU = (player.tickCount + partialTick) * BeamSettings.speed * 2.0f;
        boolean textured = BeamSettings.useTextured;

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer tubeConsumer =
                textured ? null : bufferSource.getBuffer(RenderTypes.lightning());
        VertexConsumer stripConsumer =
                textured ? bufferSource.getBuffer(WandBeamRenderType.beamStrip()) : null;

        Matrix4f matrix = poseStack.last().pose();

        WandBeamGeometry.renderLayers(matrix, tubeConsumer, stripConsumer, camPos, path, flicker,
                beamScrollU, textured);

        if (textured && stripConsumer != null) {
            BeamSettings.LayerSettings core = BeamSettings.layers[BeamSettings.CORE];
            float mu = core.width * 2.1f;
            WandBeamGeometry.renderMuzzleFlash(matrix, stripConsumer, camPos, beamStart, mu,
                    core.r, core.g, core.b, Mth.clamp(core.alpha * 1.15f * flicker, 0f, 1f), beamScrollU);

            double reachTol = 0.06;
            boolean reachedTarget = maxReach + reachTol >= fullDist;
            if (reachedTarget && ray.hitsAnything()) {
                WandBeamGeometry.renderImpactFlash(matrix, stripConsumer, camPos, beamEnd,
                        Math.max(0.09f, core.width * 2.4f),
                        core.r, core.g, core.b, Mth.clamp(core.alpha * 1.05f * flicker, 0f, 1f),
                        beamScrollU, ray.hitsEntity());
            }
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }

    /**
     * The beam's max length, resolved the way {@code WandBeamChannelLogic} resolves it server-side:
     * the spell's range scaled by the wand's range stat, falling back to 32 blocks. Only the debug
     * force-beam path uses the free-floating {@link BeamSettings#range} slider, since it has no spell.
     */
    private static float resolveBeamRange(Player player) {
        if (WizardsAndBeastsMod.debugForceBeam) {
            return BeamSettings.range;
        }
        Spell spell = ClientSpellDataState.get().getActiveSpell();
        SpellProperties props = spell == null ? null : spell.getProperties();
        if (props == null) {
            return 32f;
        }
        float range = props.getRange() * WandStatsResolver.resolve(player.getUseItem()).rangeFor(spell);
        return range <= 0f ? 32f : range;
    }

    /**
     * World-space origin for the beam so it lines up with the held wand model.
     * First person uses the camera frame (same as the on-screen hand); third person uses a shoulder/hand
     * point in front of the body so the bolt does not appear to come from the eyes.
     */
    private static Vec3 wandTipWorldPos(Minecraft mc, Player player, float partialTick, Camera camera) {
        InteractionHand wandHand = player.getMainHandItem().getItem() instanceof WandItem
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
        HumanoidArm arm = wandHand == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        double sideSign = arm == HumanoidArm.RIGHT ? 1.0 : -1.0;

        // Camera has no look accessor here; view vector matches crosshair / pick and tracks view bobbing pitch/yaw.
        Vec3 lookCam = player.getViewVector(partialTick);
        Vec3 right = lookCam.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-8) {
            right = new Vec3(1, 0, 0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(lookCam).normalize();

        if (mc.options.getCameraType().isFirstPerson()) {
            Vec3 eye = camera.position();
            // Vanilla right-hand item sits forward, to the weapon side, and slightly below the lens.
            final double forward = 0.70;
            final double side = 0.30 * sideSign;
            final double alongUp = -0.32;
            return eye.add(lookCam.scale(forward)).add(right.scale(side)).add(up.scale(alongUp));
        }

        Vec3 base = player.getPosition(partialTick);
        double shoulderY = base.y + player.getBbHeight() * 0.86;
        Vec3 aim = player.getViewVector(partialTick);
        Vec3 rightBody = aim.cross(new Vec3(0, 1, 0));
        if (rightBody.lengthSqr() < 1e-8) {
            rightBody = new Vec3(1, 0, 0);
        } else {
            rightBody = rightBody.normalize();
        }
        Vec3 shoulder = new Vec3(base.x, shoulderY, base.z).add(rightBody.scale(0.32 * sideSign));
        final double forward = 0.58;
        final double lift = -0.12;
        return shoulder.add(aim.scale(forward)).add(0, lift, 0);
    }

    private static boolean isHeldChannelSpell(Spell spell) {
        if (spell == null) return false;
        String id = spell.getId();
        if (SpellIds.matches(id, LEVIOSA_ID)) {
            return false;
        }
        SpellProperties props = spell.getProperties();
        if (props == null) return false;
        CastType castType = props.getCastType();
        return castType == CastType.BEAM_LETHAL || castType == CastType.BEAM_CHANNEL;
    }

}
