package at.koopro.wizardsandbeasts.client.admin.viewer;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.variant.VariantHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.GeckoLibResources;
import software.bernie.geckolib.cache.animation.Animation;
import software.bernie.geckolib.cache.animation.BoneAnimation;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * One creature, instantiated on the client purely to be drawn by its own production renderer.
 *
 * <p><b>Never part of a world.</b> It is created with {@code EntityType#create} — the path the client already uses
 * for every entity a server announces — but never added to the level: nothing ticks it, nothing collides with it,
 * nothing sends it anywhere, and no server learns of it. It exists while the Entity Viewer is open and is dropped
 * with it; its GeckoLib animation state lives in the instance's own cache and goes with it.
 *
 * <p><b>Animation control</b> goes through GeckoLib's public controller API on this instance only. The creature's
 * own controllers are left exactly as its class registered them (so "no animation selected" is the creature's real
 * idle); the viewer appends one more controller that plays the chosen clip. GeckoLib applies controllers in order,
 * so the last one owns every bone its clip keys. Animation time follows the instance's age, which the viewer
 * advances only while playing — pausing freezes every controller at once.
 */
@NullMarked
public final class PreviewEntity {

    public static final String PREVIEW_CONTROLLER = WizardsAndBeastsMod.MODID + "_viewer";

    private final String creatureId;
    private final LivingEntity entity;
    private @Nullable RawAnimation forced;
    private @Nullable String forcedName;
    private boolean loop = true;
    private boolean installed;
    private double speed = 1.0;

    private PreviewEntity(String creatureId, LivingEntity entity) {
        this.creatureId = creatureId;
        this.entity = entity;
    }

    /** Null when the id is not a living entity type on this client. */
    public static @Nullable PreviewEntity create(String creatureId) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, creatureId)).orElse(null);
        if (type == null) {
            return null;
        }
        Entity created = type.create(level, EntitySpawnReason.LOAD);
        if (!(created instanceof LivingEntity living)) {
            return null;
        }
        living.setYRot(0.0f);
        living.setYHeadRot(0.0f);
        living.yBodyRot = 0.0f;
        // Standing, so "its own animation" is the idle a creature shows at rest rather than the airborne loop a
        // flyer picks when it finds nothing under its feet — this instance has no world to stand in.
        living.setOnGround(true);
        return new PreviewEntity(creatureId, living);
    }

    public String creatureId() {
        return creatureId;
    }

    public LivingEntity entity() {
        return entity;
    }

    /** The production renderer the entity render dispatcher would use for this entity anywhere in the game. */
    public EntityRenderer<? super LivingEntity, ?> renderer() {
        return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
    }

    public EntityRenderState extract(float partialTick) {
        return renderer().createRenderState(entity, partialTick);
    }

    /** Advances animation time by one tick. */
    public void tick() {
        entity.tickCount++;
    }

    // ── variant ──

    public boolean applyVariant(String variantId) {
        return entity instanceof VariantHolder holder && holder.applyVariant(variantId);
    }

    public @Nullable String variantId() {
        return entity instanceof VariantHolder holder ? holder.variant().variantId() : null;
    }

    // ── GeckoLib ──

    public boolean isGeo() {
        return entity instanceof GeoAnimatable && renderer() instanceof GeoEntityRenderer<?, ?>;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable GeoModel<GeoAnimatable> model() {
        return renderer() instanceof GeoEntityRenderer geo ? (GeoModel<GeoAnimatable>) geo.getGeoModel() : null;
    }

    @SuppressWarnings("unchecked")
    private @Nullable AnimatableManager<GeoAnimatable> manager() {
        if (!(entity instanceof GeoAnimatable animatable)) {
            return null;
        }
        return (AnimatableManager<GeoAnimatable>) animatable.getAnimatableInstanceCache().getManagerForId(entity.getId());
    }

    /** Every clip in the creature's animation file (and its fallbacks), sorted. Empty when not a GeckoLib model. */
    public List<String> animations() {
        GeoModel<GeoAnimatable> model = model();
        if (model == null || !(entity instanceof GeoAnimatable animatable)) {
            return List.of();
        }
        Set<String> names = new TreeSet<>();
        List<Identifier> files = new ArrayList<>();
        files.add(model.getAnimationResource(animatable));
        files.addAll(List.of(model.getAnimationResourceFallbacks(animatable)));
        for (Identifier file : files) {
            BakedAnimations baked = GeckoLibResources.getBakedAnimations().cache().get(file);
            if (baked == null) {
                baked = GeckoLibResources.getBakedAnimations().cache().get(GeckoLibResources.stripPrefixAndSuffix(file));
            }
            if (baked != null) {
                names.addAll(baked.animations().keySet());
            }
        }
        return List.copyOf(names);
    }

    /** The bones the selected clip moves; empty when none is selected. */
    public Set<String> animatedBones() {
        GeoModel<GeoAnimatable> model = model();
        if (model == null || forcedName == null || !(entity instanceof GeoAnimatable animatable)) {
            return Set.of();
        }
        Animation animation = model.getBakedAnimation(animatable, forcedName);
        if (animation == null) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (BoneAnimation bone : animation.boneAnimations()) {
            out.add(bone.boneName());
        }
        return out;
    }

    /** The model's bone hierarchy as (depth, name) lines, top-level bones first. */
    public List<BoneLine> bones(EntityRenderState state) {
        GeoModel<GeoAnimatable> model = model();
        if (model == null || !(state instanceof GeoRenderState geoState)) {
            return List.of();
        }
        List<BoneLine> out = new ArrayList<>();
        try {
            for (GeoBone bone : model.getBakedModel(model.getModelResource(geoState)).topLevelBones()) {
                collect(bone, 0, out);
            }
        } catch (RuntimeException missing) {
            return List.of();
        }
        return List.copyOf(out);
    }

    private static void collect(GeoBone bone, int depth, List<BoneLine> out) {
        out.add(new BoneLine(depth, bone.name()));
        for (GeoBone child : bone.children()) {
            collect(child, depth + 1, out);
        }
    }

    public record BoneLine(int depth, String name) {}

    /**
     * Plays {@code name} on the viewer's controller, looping or once; null returns the creature to its own
     * controllers. Restarts the clip if it is already selected.
     */
    public void play(@Nullable String name, boolean loop) {
        this.forcedName = name;
        this.loop = loop;
        this.forced = name == null ? null : loop ? RawAnimation.begin().thenLoop(name) : RawAnimation.begin().thenPlay(name);
        AnimatableManager<GeoAnimatable> manager = manager();
        if (manager == null) {
            return;
        }
        install(manager);
        AnimationController<GeoAnimatable> controller = manager.getAnimationControllers().get(PREVIEW_CONTROLLER);
        if (controller != null) {
            controller.reset();
        }
    }

    public @Nullable String playing() {
        return forcedName;
    }

    public boolean looping() {
        return loop;
    }

    private void install(AnimatableManager<GeoAnimatable> manager) {
        if (installed || manager.getAnimationControllers().containsKey(PREVIEW_CONTROLLER)) {
            installed = true;
            return;
        }
        manager.addController(new AnimationController<>(PREVIEW_CONTROLLER, 0,
                test -> forced == null ? PlayState.STOP : test.setAndContinue(forced)));
        installed = true;
        applySpeed(manager);
    }

    /** Every controller's playback speed: the creature's own and the viewer's. */
    public void setSpeed(double speed) {
        this.speed = speed;
        AnimatableManager<GeoAnimatable> manager = manager();
        if (manager != null) {
            applySpeed(manager);
        }
    }

    public double speed() {
        return speed;
    }

    private void applySpeed(AnimatableManager<GeoAnimatable> manager) {
        for (AnimationController<GeoAnimatable> controller : manager.getAnimationControllers().values()) {
            controller.setAnimationSpeed(speed);
        }
    }

    /** Each controller and what it is playing right now, for the debug readout. */
    public List<String> controllerStates() {
        AnimatableManager<GeoAnimatable> manager = manager();
        if (manager == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (AnimationController<GeoAnimatable> controller : manager.getAnimationControllers().values()) {
            RawAnimation current = controller.getCurrentRawAnimation();
            out.add(controller.getName() + ": " + (current == null || controller.getCurrentAnimationPoint() == null
                    ? "—" : controller.getCurrentAnimationPoint().animation().name()));
        }
        return out;
    }
}
