package at.koopro.wizardsandbeasts.entity.creature;

import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules;
import at.koopro.wizardsandbeasts.entity.flight.FlightBeats;
import at.koopro.wizardsandbeasts.entity.flight.RiddenFlight;
import at.koopro.wizardsandbeasts.entity.flight.WingedWalker;
import at.koopro.wizardsandbeasts.entity.flight.WingedWalkerFlightGoal;
import at.koopro.wizardsandbeasts.entity.flight.WingedWalkerMoveControl;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Hippogriff: eagle in front, horse behind, proud, and to be bowed to.
 *
 * <p>Still a data-driven creature — its stats, abilities (enrage, dive bomb), combat, sounds, bond profile and
 * shared clips come from {@code creatures/hippogriff.json} and {@code creature_bonds/hippogriff.json} through
 * {@link GenericBeastEntity}, the way {@link DragonEntity} does for the dragons. This subclass adds only what the
 * data layer cannot express:
 *
 * <ul>
 *   <li><b>Walking and flying.</b> It lives on the ground ({@link GroundPathNavigation}) and takes to the air in
 *       bounded flights ({@link WingedWalkerFlightGoal}); {@link WingedWalkerMoveControl} hands steering to
 *       vanilla's ground or flying control accordingly. Shared with the Thestral ({@code entity.flight}).</li>
 *   <li><b>Respect</b> (Prisoner of Azkaban). A player who crouches while holding its gaze is bowing; held for
 *       {@link WildlifeRules#BOW_HOLD_TICKS} it bows back and that player is respected — remembered, saved. Only a
 *       respected player may touch, feed or ride it. A stranger who crowds it without bowing is warned off (it
 *       rears); crowding it again is the insult canon warns about, and it attacks. Striking it revokes respect and
 *       it will not bow to that person again for {@link WildlifeRules#GRUDGE_TICKS}. All server-side.</li>
 *   <li><b>Riding</b> through {@link RiddenFlight} (vanilla's ridden contract, as the Happy Ghast flies), for a
 *       respected rider it trusts ({@link WildlifeRules#HIPPOGRIFF_RIDE_BOND}).</li>
 *   <li><b>Coats.</b> Hagrid's herd: storm grey (Buckbeak), bronze, roan, chestnut, inky black. One rig, a synced
 *       and saved variant, rolled when it first spawns.</li>
 * </ul>
 */
public class HippogriffEntity extends GenericFlyingBeastEntity implements WingedWalker {

    public static final String ACTION_CONTROLLER = "hippogriff_action";

    public enum Coat {
        STORM_GREY, BRONZE, ROAN, CHESTNUT, BLACK;

        /** Texture sub-path, or null for the base texture. */
        public @Nullable String textureName() {
            return this == STORM_GREY ? null : "hippogriff/" + name().toLowerCase(Locale.ROOT);
        }

        static Coat byId(int id) {
            Coat[] all = values();
            return id >= 0 && id < all.length ? all[id] : STORM_GREY;
        }
    }

    private static final EntityDataAccessor<Byte> DATA_COAT =
            SynchedEntityData.defineId(HippogriffEntity.class, EntityDataSerializers.BYTE);

    private static final String KEY_COAT = "Coat";
    private static final String KEY_RESPECTED = "Respected";
    private static final int BOW_TICKS = 48;

    /** Those it has bowed back to. Server only; saved. */
    private final Set<UUID> respected = new HashSet<>();
    /** How long each nearby player has been bowing. Server only. */
    private final Map<UUID, Integer> bowing = new HashMap<>();
    /** When each stranger was last warned off, in {@link #tickCount}. Server only. */
    private final Map<UUID, Integer> warned = new HashMap<>();
    /** When each person last struck it. Server only. */
    private final Map<UUID, Integer> grudges = new HashMap<>();
    private boolean flying;
    private int bowTicks;
    private final FlightBeats beats = new FlightBeats();

    public HippogriffEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = new WingedWalkerMoveControl<>(this);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COAT, (byte) 0);
    }

    @Override
    protected void addMovementGoals() {
        goalSelector.addGoal(5, new WingedWalkerFlightGoal<>(this, 900));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
    }

    // ── state ─────────────────────────────────────────────────────────────────

    @Override
    public boolean isFlying() {
        return flying;
    }

    @Override
    public boolean canTakeFlight() {
        return !isBowing();
    }

    @Override
    public void flyRidden(Vec3 input, float speed) {
        travelFlying(input, speed);
    }

    @Override
    public void setFlying(boolean flying) {
        this.flying = flying;
        if (!flying) {
            setNoGravity(false);
        }
    }

    public boolean isBowing() {
        return bowTicks > 0;
    }

    public Coat coat() {
        return Coat.byId(entityData.get(DATA_COAT));
    }

    public void setCoat(Coat coat) {
        entityData.set(DATA_COAT, (byte) coat.ordinal());
    }

    public boolean respects(Player player) {
        return respected.contains(player.getUUID());
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        // Rolled once, here and not in the constructor, which also runs on every chunk load.
        Coat[] coats = Coat.values();
        setCoat(coats[getRandom().nextInt(coats.length)]);
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    // ── respect ───────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        if (bowTicks > 0) {
            bowTicks--;
        }
        if (isVehicle()) {
            flying = false;
        }
        beats.update(this, clip -> triggerAnim(ACTION_CONTROLLER, clip));
        if (tickCount % 4 == 0 && !isVehicle()) {
            watchForRespect(level);
        }
    }

    /** Bows, crowding and warnings, from everyone within sight. */
    private void watchForRespect(ServerLevel level) {
        List<Player> near = level.getEntitiesOfClass(Player.class,
                getBoundingBox().inflate(WildlifeRules.BOW_RANGE), EntitySelector.NO_SPECTATORS);
        Set<UUID> present = new HashSet<>();
        for (Player player : near) {
            UUID id = player.getUUID();
            present.add(id);
            Vec3 toEyes = getEyePosition().subtract(player.getEyePosition());
            double distance = toEyes.length();
            double dot = distance < 1.0e-4 ? 1.0 : player.getViewVector(1.0F).dot(toEyes.normalize());
            Vec3 move = player.getDeltaMovement();
            boolean bows = !respects(player) && WildlifeRules.bowing(player.isShiftKeyDown(), dot,
                    move.x * move.x + move.z * move.z, distance);
            int held = bows ? bowing.getOrDefault(id, 0) + 4 : 0;
            bowing.put(id, held);
            if (held >= WildlifeRules.BOW_HOLD_TICKS) {
                answerBow(level, player);
                bowing.put(id, 0);
                continue;
            }
            Integer warnedAt = warned.get(id);
            int since = warnedAt == null ? -1 : tickCount - warnedAt;
            double body = player.distanceTo(this) - getBbWidth() * 0.5;
            switch (WildlifeRules.crowding(respects(player) || bows, body, since)) {
                case WARN -> warnOff(level, player);
                case ATTACK -> {
                    if (!player.isCreative()) {
                        setTarget(player);
                        warned.remove(id);
                    }
                }
                case NONE -> { }
            }
        }
        bowing.keySet().retainAll(present);
    }

    /** It bows back — or, to someone who struck it, does not. */
    private void answerBow(ServerLevel level, Player player) {
        Integer struck = grudges.get(player.getUUID());
        if (struck != null && tickCount - struck < WildlifeRules.GRUDGE_TICKS) {
            warnOff(level, player);
            return;
        }
        respected.add(player.getUUID());
        warned.remove(player.getUUID());
        getNavigation().stop();
        getLookControl().setLookAt(player, 30.0F, 30.0F);
        bowTicks = BOW_TICKS;
        triggerAnim(ACTION_CONTROLLER, "bow");
        level.playSound(null, blockPosition(), SoundEvents.PARROT_AMBIENT, SoundSource.NEUTRAL, 0.8F, 0.6F);
        if (player instanceof ServerPlayer serverPlayer) {
            BestiaryDiscoveryHandler.witnessedSignature(serverPlayer, this);
            PlayerFeedback.actionBar(serverPlayer, Component.translatable("creature.wizards_and_beasts.hippogriff.bowed"));
        }
    }

    private void warnOff(ServerLevel level, Player player) {
        warned.put(player.getUUID(), tickCount);
        getLookControl().setLookAt(player, 30.0F, 30.0F);
        triggerAnim(ACTION_CONTROLLER, "rear");
        level.playSound(null, blockPosition(), SoundEvents.PARROT_HURT, SoundSource.NEUTRAL, 1.0F, 0.5F);
        Vec3 away = player.position().subtract(position()).multiply(1, 0, 1);
        if (away.lengthSqr() > 1.0e-4) {
            player.knockback(0.5, -away.x, -away.z);
            player.hurtMarked = true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerFeedback.actionBar(serverPlayer, Component.translatable("creature.wizards_and_beasts.hippogriff.warned"));
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && source.getEntity() instanceof Player player) {
            respected.remove(player.getUUID());
            grudges.put(player.getUUID(), tickCount);
            ejectPassengers();
        }
        return hurt;
    }

    // ── interaction and riding ────────────────────────────────────────────────

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) {
            // The server decides; the client only swings the arm for what could be accepted.
            return player.getItemInHand(hand).isEmpty() || bondProfile() != null ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }
        if (!ModuleManager.isEnabled(Module.CREATURES)) {
            return super.mobInteract(player, hand);
        }
        if (!respects(player)) {
            // Walking up and touching it without the courtesy is exactly what canon warns against.
            warnOff((ServerLevel) level(), player);
            return InteractionResult.SUCCESS;
        }
        InteractionResult fed = super.mobInteract(player, hand);
        if (fed != InteractionResult.PASS || !player.getItemInHand(hand).isEmpty() || player.isSecondaryUseActive()) {
            return fed;
        }
        if (bondLevel() < WildlifeRules.HIPPOGRIFF_RIDE_BOND
                || (bondOwner() != null && !bondOwner().equals(player.getUUID()))) {
            PlayerFeedback.actionBar(player, Component.translatable("creature.wizards_and_beasts.hippogriff.not_yet"));
            return InteractionResult.SUCCESS;
        }
        if (isVehicle() || getTarget() != null) {
            return InteractionResult.PASS;
        }
        setFlying(false);
        getNavigation().stop();
        player.startRiding(this);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger();
    }

    /** No thinking or wandering under a rider — the rider steers. Vanilla still runs ridden travel. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || getControllingPassenger() instanceof Player;
    }

    /** The rider sits on the withers, just behind the neck. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0, 1.35 * scale, -0.25 * scale).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        super.tickRidden(rider, input);
        RiddenFlight.tick(this, rider);
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 travel) {
        return RiddenFlight.input(this, rider);
    }

    @Override
    protected float getRiddenSpeed(Player rider) {
        return RiddenFlight.speed(this, rider);
    }

    @Override
    public void travel(Vec3 input) {
        if (!RiddenFlight.travel(this, input)) {
            super.travel(input);
        }
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store(KEY_COAT, Codec.STRING, coat().name());
        output.store(KEY_RESPECTED, UUIDUtil.CODEC.listOf(), new ArrayList<>(respected));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read(KEY_COAT, Codec.STRING).ifPresent(name -> {
            try {
                setCoat(Coat.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                setCoat(Coat.STORM_GREY);
            }
        });
        respected.clear();
        input.read(KEY_RESPECTED, UUIDUtil.CODEC.listOf()).ifPresent(respected::addAll);
    }

    // ── animation ─────────────────────────────────────────────────────────────

    private static RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop("animation.hippogriff." + name);
    }

    private static final RawAnimation IDLE = loop("idle");
    private static final RawAnimation WALK = loop("walk");
    private static final RawAnimation RUN = loop("run");
    private static final RawAnimation FLY = loop("fly");
    private static final RawAnimation GLIDE = loop("glide");

    @Override
    protected void addMovementController(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<HippogriffEntity>("hippogriff_movement", 5, this::movement));
    }

    /** One-shots the shared clip gate has no name for; registered last so they play over everything. */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        controllers.add(new AnimationController<HippogriffEntity>(ACTION_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim("takeoff", RawAnimation.begin().thenPlay("animation.hippogriff.takeoff"))
                .triggerableAnim("land", RawAnimation.begin().thenPlay("animation.hippogriff.land"))
                .triggerableAnim("flap", RawAnimation.begin().thenPlay("animation.hippogriff.flap"))
                .triggerableAnim("bow", RawAnimation.begin().thenPlay("animation.hippogriff.bow"))
                .triggerableAnim("rear", RawAnimation.begin().thenPlay("animation.hippogriff.rear")));
    }

    private PlayState movement(AnimationTest<HippogriffEntity> test) {
        double dx = getX() - xo;
        double dz = getZ() - zo;
        double dy = getY() - yo;
        double horizontal = dx * dx + dz * dz;
        if (onGround() || isInWater()) {
            if (horizontal < 1.0e-4) {
                return test.setAndContinue(IDLE);
            }
            return test.setAndContinue(horizontal > 0.03 ? RUN : WALK);
        }
        return test.setAndContinue(dy < -0.05 && horizontal > 0.01 ? GLIDE : FLY);
    }
}
