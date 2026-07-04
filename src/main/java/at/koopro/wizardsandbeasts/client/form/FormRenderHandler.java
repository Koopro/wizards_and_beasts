package at.koopro.wizardsandbeasts.client.form;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.heritage.state.ClientHeritageDataState;
import at.koopro.wizardsandbeasts.form.ModelType;
import at.koopro.wizardsandbeasts.form.SizeProfile;
import at.koopro.wizardsandbeasts.client.form.state.ClientTransitionTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Client-side render handler for the form/size system.
 * <p>
 * Responsibilities after Mixin refactor:
 * <ul>
 *   <li>Client tick: advance lerp + transition trackers</li>
 *   <li>Non-uniform visual scale for HUMANOID forms (PoseStack only)</li>
 * </ul>
 * Non-HUMANOID rendering is handled by {@code LivingEntityRendererMixin}.
 * HUMANOID scale lerp is handled by {@link FormRenderStateModifier} (state.scale).
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public class FormRenderHandler {

    private static int smokeTrailCooldown = 0;
    private static int smokeCloudCooldown = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        SizeLerpTracker.tick();
        ClientTransitionTracker.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            WetShakeTracker.tick(mc.level);
        }
        spawnObscurialSmokeTrail();
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        FormRenderStateModifier.FormRenderData formData = FormRenderStateModifier.getFormData(state);
        if (formData == null) return;

        // Non-HUMANOID forms are fully handled by LivingEntityRendererMixin
        if (formData.modelType() != ModelType.HUMANOID) return;

        // HUMANOID: apply non-uniform visual aspect ratio via PoseStack
        SizeProfile size = formData.sizeProfile();
        if (size.isNonUniform()) {
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            // Y scale is already handled by state.scale (lerped via modelScale).
            // Only apply X/Z aspect ratio correction here.
            poseStack.scale(size.modelAspectX(), 1.0f, size.modelAspectZ());
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        FormRenderStateModifier.FormRenderData formData = FormRenderStateModifier.getFormData(state);
        if (formData == null) return;

        if (formData.modelType() != ModelType.HUMANOID) return;

        SizeProfile size = formData.sizeProfile();
        if (size.isNonUniform()) {
            event.getPoseStack().popPose();
        }

        FormRenderStateModifier.removeFormData(state);
    }

    private static void spawnObscurialSmokeTrail() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        String activeForm = ClientHeritageDataState.get().getActiveFormId();
        if (!"obscurial_dark".equals(activeForm)) return;

        Vec3 velocity = player.getDeltaMovement();
        boolean moving = velocity.lengthSqr() >= 0.0025;
        if (mc.options.getCameraType().isFirstPerson()) {
            // Requested behavior: don't render dark-form particles in first-person view.
            return;
        }

        if (moving && smokeTrailCooldown > 0) {
            smokeTrailCooldown--;
        } else if (moving) {
            smokeTrailCooldown = 2;

            Vec3 pos = player.position();
            double baseY = pos.y + player.getBbHeight() * 0.55;
            for (int i = 0; i < 7; i++) {
                double ox = (mc.level.random.nextDouble() - 0.5) * 0.8;
                double oy = (mc.level.random.nextDouble() - 0.5) * 0.4;
                double oz = (mc.level.random.nextDouble() - 0.5) * 0.8;
                mc.level.addParticle(ParticleTypes.SMOKE,
                        pos.x + ox, baseY + oy, pos.z + oz,
                        -velocity.x * 0.24, 0.02, -velocity.z * 0.24);
                mc.level.addParticle(ParticleTypes.WITCH,
                        pos.x + ox * 0.7, baseY + oy * 0.7, pos.z + oz * 0.7,
                        0.0, 0.01, 0.0);
            }
        }

        if (smokeCloudCooldown > 0) {
            smokeCloudCooldown--;
            return;
        }
        smokeCloudCooldown = 4;

        Vec3 center = player.position().add(0, player.getBbHeight() * 0.6, 0);
        int burstCount = moving ? 10 : 14;
        for (int i = 0; i < burstCount; i++) {
            double angle = (Math.PI * 2.0 * i) / burstCount;
            double radius = moving
                    ? 0.55 + mc.level.random.nextDouble() * 0.35
                    : 0.45 + mc.level.random.nextDouble() * 0.22;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            double y = center.y + (mc.level.random.nextDouble() - 0.5) * (moving ? 0.6 : 0.45);
            mc.level.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0, 0.01, 0.0);
            if (!moving) {
                mc.level.addParticle(ParticleTypes.WITCH, x, y, z, 0.0, 0.005, 0.0);
            }
        }
    }
}
