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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Renders a multi-layered magical beam from the wand tip to the target while the player holds
 * right-click with a wand.
 *
 * <p>This class is the plumbing only: it decides <em>whether</em> a beam is up, where it starts and
 * ends, and which shape frame the animation is on. Everything about how the beam <em>looks</em> comes
 * from the channelled spell's {@link BeamStyle} (see {@link BeamStyles}), and everything about how it
 * is turned into triangles lives in {@link WandBeamGeometry}. Adding a beam to a new spell needs no
 * change here.
 *
 * <p>All server-owned state — whether the spell is active, who is casting, the target, damage — is
 * read, never written. See {@link WandBeamPipelines} for why the render type is additive rather than
 * alpha-blended.
 */
public final class WandBeamRenderer {
    private static final String LEVIOSA_ID = "wingardium_leviosa";

    private static boolean wasActive = false;
    private static int beamStartTick = 0;
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

        // The spell contributes a style; this client's quality budget (and the debug screen, while it
        // has control) is layered on top. The renderer below reads nothing else about the spell.
        Spell activeSpell = WizardsAndBeastsMod.debugForceBeam
                ? null
                : ClientSpellDataState.get().getActiveSpell();
        BeamStyle style = BeamSettings.resolve(BeamStyles.forSpell(activeSpell));
        BeamStyle.Path pathStyle = style.path();
        BeamStyle.Pulse pulse = style.pulse();

        // Shape keyframes on a fixed tick cadence. Every node offset is independent of the previous
        // frame's, so at morph 0 the whole bolt snaps to a fresh shape — that discontinuity is what
        // reads as electrical crackle. At morph 1 the two frames are interpolated instead, which is
        // how a curse writhes and a water jet flows rather than strobes.
        float beamTime = player.tickCount + partialTick;
        double frames = beamTime / Math.max(1, pathStyle.reseedTicks());
        long frameIndex = (long) Math.floor(frames);
        float frac = (float) (frames - frameIndex);
        long seedA = shapeSeed(frameIndex);
        long seedB = shapeSeed(frameIndex + 1);
        float blend = keyframeBlend(frac, pathStyle.morph());

        // Per-strike brightness on top of the continuous shimmer: real arcs are not equally bright
        // from one discharge to the next. Interpolated alongside the shape so a morphing style does
        // not pop at the keyframe boundary.
        float amount = Mth.clamp(pulse.amount(), 0f, 1f);
        float gain = Mth.lerp(blend, strikeGain(seedA, amount), strikeGain(seedB, amount));
        float shimmer = 1f - amount + amount * (0.5f + 0.5f * Mth.sin(beamTime * pulse.speed()));
        float flicker = gain * shimmer;

        WandBeamGeometry.Bolt bolt =
                WandBeamGeometry.buildBolt(beamStart, beamEnd, seedA, seedB, blend, style);
        if (bolt == null) {
            poseStack.popPose();
            return;
        }

        float scrollU = beamTime * pulse.scrollSpeed();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer stripConsumer = bufferSource.getBuffer(WandBeamRenderType.beamStrip());
        Matrix4f matrix = poseStack.last().pose();

        WandBeamGeometry.renderBolt(matrix, stripConsumer, camPos, bolt, style, flicker, scrollU);

        if (BeamSettings.endpointFlashes) {
            VertexConsumer glowConsumer = bufferSource.getBuffer(WandBeamRenderType.beamGlow());
            BeamStyle.Layer halo = style.outer();
            BeamStyle.Flash flash = style.flash();
            float glowAlpha = style.core().alpha();

            WandBeamGeometry.renderMuzzleFlash(matrix, glowConsumer, camPos, beamStart,
                    Math.max(0.10f, halo.width() * flash.muzzleScale()),
                    halo.r(), halo.g(), halo.b(), Mth.clamp(glowAlpha * 0.85f * flicker, 0f, 1f));

            double reachTol = 0.06;
            boolean reachedTarget = maxReach + reachTol >= fullDist;
            if (reachedTarget && ray.hitsAnything()) {
                WandBeamGeometry.renderImpactFlash(matrix, glowConsumer, camPos, beamEnd,
                        Math.max(0.16f, halo.width() * flash.impactScale()),
                        halo.r(), halo.g(), halo.b(), Mth.clamp(glowAlpha * 0.9f * flicker, 0f, 1f),
                        ray.hitsEntity(), flash.impactSparks(), seedA);
            }
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }

    /** Deterministic per-cast, per-keyframe seed: the same frame always rebuilds the same shape. */
    private static long shapeSeed(long frameIndex) {
        return beamPathSeed * 31L + frameIndex;
    }

    private static float strikeGain(long seed, float amount) {
        return 1f - amount + 2f * amount * RandomSource.create(seed).nextFloat();
    }

    /**
     * How far through the current shape keyframe the beam has interpolated.
     *
     * <p>{@code morph} controls <em>when</em> the transition happens inside the window, not how far it
     * gets: the shape holds for the first {@code 1 - morph} of the window and then eases the rest of
     * the way. So {@code morph 0} holds the whole window and snaps at the boundary (crackle),
     * {@code morph 1} eases across the entire window (a continuous writhe), and everything between
     * holds then moves. Scaling the blend by {@code morph} instead — the obvious reading — would leave
     * every intermediate style short of the next keyframe and pop by the remainder each time it rolled
     * over.
     */
    private static float keyframeBlend(float frac, float morph) {
        float m = Mth.clamp(morph, 0f, 1f);
        if (m < 1e-4f) {
            return 0f;
        }
        float t = Mth.clamp((frac - (1f - m)) / m, 0f, 1f);
        return t * t * (3f - 2f * t);
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
