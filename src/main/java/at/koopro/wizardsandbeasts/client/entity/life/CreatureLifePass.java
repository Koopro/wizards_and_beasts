package at.koopro.wizardsandbeasts.client.entity.life;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.profile.CreatureActivity;
import at.koopro.wizardsandbeasts.creature.profile.HasActivity;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.event.entity.CompileEntityRenderStateEvent;
import software.bernie.geckolib.event.entity.GeoEntityPreRenderEvent;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Small, always-on life for every creature: the head turns where the creature is looking, lids blink,
 * the chest breathes and ears flick. One pass for the whole roster, run after the animation clips, adding
 * to whatever the clip set this frame.
 *
 * <h2>Why a render event and not a goal or a clip</h2>
 * All of it is cosmetic, so none of it is synced or ticked on the server. The look direction it shows is
 * the one the server's AI already chose ({@code LookAtPlayerGoal}, look control) and vanilla already syncs
 * as head rotation; until now no creature showed it, because GeckoLib 5.4.5 keeps the {@code "head"} name
 * passed to {@code DefaultedEntityGeoModel} but never applies it, and no clip reads the head query.
 * GeckoLib fires {@link GeoEntityPreRenderEvent} for every {@code GeoEntityRenderer}, and a bone updater
 * added there runs after the clips and the renderer's own adjustments, so no renderer changes.
 *
 * <h2>Bone convention (see ENTITY_DESIGN_BIBLE)</h2>
 * {@code head}; neck chain {@code neck}, {@code neck_1..3}, {@code neck_01..02}, {@code neck_lower},
 * {@code neck_upper}; {@code eyelid_left}/{@code eyelid_right} modelled <b>closed</b> with the pivot on the
 * upper edge of the eye (this pass scales them open); {@code ear_left}/{@code ear_right} (or {@code _l/_r});
 * {@code chest}. A rig that lacks a bone simply doesn't get that feature. Clips must not key
 * {@code eyelid_*}: this pass owns them.
 *
 * <h2>Hand-over</h2>
 * The look fades out (over ~7 ticks) while a triggered one-shot plays, while asleep and while dying, so a
 * bite or a roar keeps the head pose its clip was authored with.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class CreatureLifePass {

    private CreatureLifePass() {}

    /** Per-frame inputs gathered from the entity, read by the bone updater. */
    public record Inputs(long seed, float lookWeight, float lidSleep, boolean asleep, float height) {}

    public static final DataTicket<Inputs> INPUTS = DataTicket.create("wizards_and_beasts_creature_life", Inputs.class);

    private static final String[] NECK_CANDIDATES = {
            "neck_lower", "neck_1", "neck_01", "neck", "neck_2", "neck_02", "neck_3", "neck_upper"};
    private static final float LOOK_STEP_PER_TICK = 0.15f;
    private static final float LID_STEP_PER_TICK = 0.08f;

    /** Which life features a baked rig can take; built once per baked model. */
    record RigLayout(List<String> neck, boolean head, boolean lids, String earLeft, String earRight, boolean chest) {
        static RigLayout of(BakedGeoModel model) {
            List<String> neck = new ArrayList<>();
            for (String name : NECK_CANDIDATES) {
                if (model.getBone(name).isPresent()) neck.add(name);
            }
            String earL = model.getBone("ear_left").isPresent() ? "ear_left"
                    : model.getBone("ear_l").isPresent() ? "ear_l" : null;
            String earR = model.getBone("ear_right").isPresent() ? "ear_right"
                    : model.getBone("ear_r").isPresent() ? "ear_r" : null;
            return new RigLayout(List.copyOf(neck), model.getBone("head").isPresent(),
                    model.getBone("eyelid_left").isPresent() || model.getBone("eyelid_right").isPresent(),
                    earL, earR, model.getBone("chest").isPresent());
        }
    }

    // Render-thread only. Weak keys: an unloaded entity or a reloaded model drops out by itself.
    private static final Map<BakedGeoModel, RigLayout> LAYOUTS = new WeakHashMap<>();
    /** {@code {lookWeight, lidSleep, lastAgeTicks}} per creature, eased between frames. */
    private static final Map<Entity, float[]> EASED = new WeakHashMap<>();

    static boolean appliesTo(Entity entity) {
        return entity instanceof Mob
                && WizardsAndBeastsMod.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace());
    }

    @SubscribeEvent
    public static void onCompileRenderState(CompileEntityRenderStateEvent<?, ?> event) {
        Entity entity = event.getAnimatable();
        if (!appliesTo(entity)) return;
        float age = event.getRenderState().ageInTicks;

        CreatureActivity activity = entity instanceof HasActivity has ? has.activity() : CreatureActivity.IDLE;
        boolean asleep = activity == CreatureActivity.SLEEP;
        boolean dying = !((Mob) entity).isAlive() || ((Mob) entity).deathTime > 0;
        boolean oneShot = playingOneShot(event.getAnimatable(), entity.getId());

        float lookTarget = asleep || dying || oneShot ? 0f : 1f;
        float lidTarget = asleep || dying ? 1f : 0f;
        float[] eased = EASED.computeIfAbsent(entity, e -> new float[] {lookTarget, lidTarget, age});
        float dt = Mth.clamp(age - eased[2], 0f, 5f);
        eased[0] = LifeMotion.approach(eased[0], lookTarget, LOOK_STEP_PER_TICK * dt);
        eased[1] = LifeMotion.approach(eased[1], lidTarget, LID_STEP_PER_TICK * dt);
        eased[2] = age;

        long seed = entity.getUUID().getLeastSignificantBits();
        event.addData(INPUTS, new Inputs(seed, eased[0], eased[1], asleep, entity.getBbHeight()));
    }

    private static boolean playingOneShot(GeoAnimatable animatable, long instanceId) {
        AnimatableManager<GeoAnimatable> manager = animatable.getAnimatableInstanceCache().getManagerForId(instanceId);
        for (AnimationController<GeoAnimatable> controller : manager.getAnimationControllers().values()) {
            if (controller.isPlayingTriggeredAnimation()) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onPreRender(GeoEntityPreRenderEvent<?, ?> event) {
        hook(event.getRenderPassInfo(), event.getModel());
    }

    private static <R extends GeoRenderState> void hook(RenderPassInfo<R> info, BakedGeoModel model) {
        Inputs inputs = info.getGeckolibData(INPUTS);
        if (inputs == null || !(info.renderState() instanceof LivingEntityRenderState living)) return;
        RigLayout rig = LAYOUTS.computeIfAbsent(model, RigLayout::of);
        float age = living.ageInTicks;
        float yaw = LifeMotion.clampLook(living.yRot, LifeMotion.MAX_YAW) * inputs.lookWeight();
        float pitch = LifeMotion.clampLook(living.xRot, LifeMotion.MAX_PITCH) * inputs.lookWeight();
        info.addBoneUpdater((pass, bones) -> apply(rig, inputs, age, yaw, pitch, bones));
    }

    static void apply(RigLayout rig, Inputs in, float age, float yawDeg, float pitchDeg, BoneSnapshots bones) {
        if (yawDeg != 0f || pitchDeg != 0f) {
            float yaw = -yawDeg * Mth.DEG_TO_RAD;
            float pitch = -pitchDeg * Mth.DEG_TO_RAD;
            float neck = LifeMotion.neckShare(rig.neck().size());
            for (String name : rig.neck()) {
                bones.ifPresent(name, s -> s.setRotY(s.getRotY() + yaw * neck).setRotX(s.getRotX() + pitch * neck));
            }
            if (rig.head()) {
                float head = LifeMotion.headShare(rig.neck().size());
                bones.ifPresent("head", s -> s.setRotY(s.getRotY() + yaw * head).setRotX(s.getRotX() + pitch * head));
            }
        }
        if (rig.lids()) {
            float closure = Math.max(in.lidSleep(), LifeMotion.blinkClosure(age, in.seed()));
            lid(bones, "eyelid_left", closure);
            lid(bones, "eyelid_right", closure);
        }
        if (rig.earLeft() != null || rig.earRight() != null) {
            float[] flick = LifeMotion.earFlick(age, in.seed());
            if (rig.earLeft() != null) bones.ifPresent(rig.earLeft(), s -> s.setRotZ(s.getRotZ() + flick[0]));
            if (rig.earRight() != null) bones.ifPresent(rig.earRight(), s -> s.setRotZ(s.getRotZ() + flick[1]));
        }
        if (rig.chest()) {
            float k = LifeMotion.breathScale(age, LifeMotion.breathPeriod(in.height(), in.asleep()), in.asleep(), in.seed());
            bones.ifPresent("chest", s -> s.setScale(s.getScaleX() * k, s.getScaleY() * k, s.getScaleZ()));
        }
    }

    private static void lid(BoneSnapshots bones, String name, float closure) {
        bones.ifPresent(name, s -> {
            if (closure < 0.02f) {
                s.skipRender(true);
            } else {
                s.setScaleY(s.getScaleY() * closure);
            }
        });
    }
}
