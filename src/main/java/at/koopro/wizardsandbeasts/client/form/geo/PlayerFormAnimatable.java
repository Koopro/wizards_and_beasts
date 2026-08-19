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
    private boolean attacking;
    private boolean hurt;

    private PlayerFormAnimatable(PlayerFormRig rig) {
        this.rig = rig;
    }

    /** The animatable for a player, created on first sight and retargeted when their form changes. */
    public static PlayerFormAnimatable forPlayer(UUID playerUUID, PlayerFormRig rig, float walkSpeed) {
        return forPlayer(playerUUID, rig, walkSpeed, false, false);
    }

    /**
     * As above, plus the two reaction states.
     *
     * <p>Both are pushed in from the render path rather than synced, because both are already on the
     * player's render state: {@code attackTime} is vanilla's swing progress and {@code hasRedOverlay}
     * is its damage flash. Nothing new crosses the network for this.
     */
    public static PlayerFormAnimatable forPlayer(UUID playerUUID, PlayerFormRig rig, float walkSpeed,
                                                 boolean attacking, boolean hurt) {
        PlayerFormAnimatable animatable =
                INSTANCES.computeIfAbsent(playerUUID, uuid -> new PlayerFormAnimatable(rig));
        animatable.rig = rig;
        animatable.moving = walkSpeed > MOVEMENT_THRESHOLD;
        animatable.attacking = attacking;
        animatable.hurt = hurt;
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

    public boolean attacking() {
        return attacking;
    }

    public boolean hurt() {
        return hurt;
    }

    /** The clip this animatable would play right now. Split out so a test can assert the choice. */
    public RawAnimation currentClip() {
        return clip(rig.clipFor(moving, attacking, hurt));
    }

    private static RawAnimation clip(String name) {
        return CLIPS.computeIfAbsent(name, n -> RawAnimation.begin().thenLoop(n));
    }

    /**
     * One controller, because a form has one body doing one thing.
     *
     * <p>The clip is resolved through {@link PlayerFormRig#clipFor} rather than named here: not every
     * rig has a movement, attack or hurt clip, and asking GeckoLib for a clip the file does not
     * define throws at render time rather than degrading.
     *
     * <p>One controller still, even with reactions in the mix. {@code setAndContinue} is handed the
     * cached {@link RawAnimation} for whichever clip wins, and the reaction states are timed by
     * vanilla — a swing lasts about six ticks, a damage flash ten — so the clip holds for that
     * window and falls back on its own. A second, triggered controller would need an edge detector
     * and a playhead this class does not otherwise have to keep.
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
