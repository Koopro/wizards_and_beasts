package at.koopro.wizardsandbeasts.client.form.geo;

import org.jspecify.annotations.NullMarked;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player wearing a GeckoLib form, as something GeckoLib is willing to animate.
 *
 * <p>GeckoLib animates entities, items and block entities. A transformed player is none of those —
 * the vanilla player render has been cancelled and replaced, so there is no {@code GeoEntity} to
 * hang a controller on. {@link GeoAnimatable} is the bare interface underneath all three, and
 * {@code GeoObjectRenderer} is GeckoLib's renderer for exactly this case.
 *
 * <p><b>One instance per player, not one shared singleton.</b> Animation controllers hold a playhead,
 * so a single shared animatable would give every transformed player the same frame of the same clip,
 * and one player transforming would reset another's. Each instance owns its own
 * {@link AnimatableInstanceCache} — the same reasoning that makes {@code PlayerPoseLayer}'s passes
 * key their timers by entity id.
 *
 * <p>Client-side only, and authoritative for nothing: form and movement are pushed in from the
 * synced state before each render.
 */
@NullMarked
public final class PlayerFormAnimatable implements GeoAnimatable {

    /** Live animatables, one per player. Concurrent — written from the render path. */
    private static final Map<UUID, PlayerFormAnimatable> INSTANCES = new ConcurrentHashMap<>();

    private static final String CONTROLLER = "form";

    /**
     * Below this the player is standing still.
     *
     * <p>{@code walkAnimationSpeed} is vanilla's own limb-swing amplitude: it settles to zero when a
     * player stops and grows with stride rather than with velocity, so it stays right for a player
     * being carried, pushed or swept along — where a velocity check would report movement the legs
     * are not producing.
     */
    static final float MOVEMENT_THRESHOLD = 0.01f;

    /**
     * Built once per clip name.
     *
     * <p>{@code setAndContinue} is handed the same instance every frame on purpose. A freshly built
     * {@link RawAnimation} each frame is a different object describing the same clip, and that is the
     * shape of bug that restarts the animation on every frame and leaves the model frozen on frame
     * zero looking like it simply has no animation at all.
     */
    private static final Map<String, RawAnimation> CLIPS = new ConcurrentHashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private PlayerFormRig rig;
    private boolean moving;

    private PlayerFormAnimatable(PlayerFormRig rig) {
        this.rig = rig;
    }

    /** The animatable for a player, created on first sight and retargeted when their form changes. */
    public static PlayerFormAnimatable forPlayer(UUID playerUUID, PlayerFormRig rig, float walkSpeed) {
        PlayerFormAnimatable animatable =
                INSTANCES.computeIfAbsent(playerUUID, uuid -> new PlayerFormAnimatable(rig));
        animatable.rig = rig;
        animatable.moving = walkSpeed > MOVEMENT_THRESHOLD;
        return animatable;
    }

    public static void remove(UUID playerUUID) {
        INSTANCES.remove(playerUUID);
    }

    /** Dropped on disconnect: UUIDs are stable across worlds, so a stale playhead would carry over. */
    public static void clear() {
        INSTANCES.clear();
    }

    public static int liveCount() {
        return INSTANCES.size();
    }

    public PlayerFormRig rig() {
        return rig;
    }

    public boolean moving() {
        return moving;
    }

    /** The clip this animatable would play right now. Split out so a test can assert the choice. */
    public RawAnimation currentClip() {
        return clip(rig.clipFor(moving));
    }

    private static RawAnimation clip(String name) {
        return CLIPS.computeIfAbsent(name, n -> RawAnimation.begin().thenLoop(n));
    }

    /**
     * One controller, because a form has one body doing one thing.
     *
     * <p>The clip is resolved through {@link PlayerFormRig#clipFor} rather than named here: not every
     * rig has a movement clip — {@code goblin_teller} ships an idle and nothing else — and asking
     * GeckoLib for a clip the file does not define throws at render time rather than degrading.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(CONTROLLER, 5,
                state -> state.setAndContinue(currentClip())));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
