package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.bond.BondState;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.bond.FollowBondedOwnerGoal;
import at.koopro.wizardsandbeasts.creature.wildlife.CreatureSign;
import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules;
import at.koopro.wizardsandbeasts.entity.BeastNavigation;
import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.particle.MagicSmoke;
import at.koopro.wizardsandbeasts.particle.SpellTintParticleOptions;
import at.koopro.wizardsandbeasts.registry.ModParticles;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import at.koopro.wizardsandbeasts.spell.core.MagicColours;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;

import java.util.Optional;

/**
 * The Phoenix — Fantastic Beasts' "magnificent, swan-sized, scarlet bird", Fawkes in the books.
 *
 * <p><b>It does not die.</b> A blow that would kill it sets it alight: {@link PhoenixRebirth} takes it through
 * ashes and a rising, and a weak chick ({@link WildlifeRules#REBORN_HEALTH_FRACTION} of its health, small) grows back
 * over {@link WildlifeRules#REBIRTH_GROWTH_TICKS}. Nothing is dropped, no experience is given, the entity is never
 * removed, and the death event never fires, because nothing has died. While burning it cannot be hurt and does
 * nothing; only damage nothing survives (the void, {@code /kill}) ends it.
 *
 * <p><b>It is loyal to those who show loyalty</b> (Chamber of Secrets), through the bond system
 * ({@code creature_bonds/phoenix.json}): killing what just hurt it earns its notice; hurting it breaks it. A
 * phoenix that trusts its person ({@link #TEARS_BOND}) weeps over them ({@link PhoenixTears}), sings when they are in
 * danger ({@link PhoenixSong}), and flies at what hurts them ({@link PhoenixDefendOwnerGoal}) — wounding and blinding,
 * never killing. One that follows its person ({@code followThreshold}) comes by flame when they are far away and
 * catches them if they fall.
 *
 * <p><b>It travels by flame</b> ({@link PhoenixFlameTravel}): out of reach when cornered, to its person when far.
 *
 * <p><b>It sheds.</b> A calm phoenix leaves a feather about once a day, so a wand core is found where phoenixes live.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public class PhoenixEntity extends GeoEntityBase implements BondableBeast {

    public static final String ACTION_CONTROLLER = "phoenix_action";

    private static final RawAnimation IDLE = anim("idle", true);
    private static final RawAnimation WALK = anim("walk", true);
    private static final RawAnimation FLY = anim("fly", true);
    private static final RawAnimation GLIDE = anim("glide", true);
    private static final RawAnimation BURN =
            RawAnimation.begin().thenPlay("animation.phoenix.burst").thenLoop("animation.phoenix.ashes");
    private static final RawAnimation RISE = anim("rise", false);

    private static final EntityDataAccessor<Integer> DATA_BOND_LEVEL =
            SynchedEntityData.defineId(PhoenixEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_PHASE =
            SynchedEntityData.defineId(PhoenixEntity.class, EntityDataSerializers.BYTE);

    public static final Identifier FEATHER = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "phoenix_feather");

    /** Bond at which a phoenix weeps for, sings for and defends its person. */
    public static final int TEARS_BOND = 60;
    /** Bond earned by killing what just hurt it. */
    public static final int DEFENDED_BOND = 20;
    /** A calm phoenix sheds a feather this often. */
    public static final int SHED_TICKS = 24000;
    /** How near its person must be for its tears to reach them. */
    public static final double TEARS_RANGE = 16.0;
    private static final int CARRY_LIMIT_TICKS = 400;

    private static final String KEY_REBIRTH = "RebirthTick";
    private static final String KEY_PHASE = "RebirthPhase";
    private static final String KEY_PHASE_TICKS = "RebirthPhaseTicks";
    private static final String KEY_SHED = "ShedTicks";
    private static final String KEY_TEARS = "TearsCooldown";
    private static final String KEY_FLAME = "FlameCooldown";
    private static final String KEY_SONG = "SongCooldown";

    private final BondState bond = new BondState();
    private final PhoenixRebirth rebirth = new PhoenixRebirth();
    /** Age in ticks at the last rebirth while the chick is still growing, or -1. */
    private long rebirthAge = -1L;
    private int shedTicks = SHED_TICKS;
    private int tearsCooldown;
    private int flameCooldown;
    private int songCooldown;
    /** Ticks into the current song, or -1 when not singing. */
    private int songTick = -1;
    private boolean catching;
    private int carryTicks;
    private boolean wasOnGround = true;
    private int airTicks;

    public PhoenixEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.SCALE, 1.0);
    }

    private static RawAnimation anim(String name, boolean loop) {
        String id = "animation.phoenix." + name;
        return loop ? RawAnimation.begin().thenLoop(id) : RawAnimation.begin().thenPlay(id);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return BeastNavigation.flying(this, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BOND_LEVEL, 0);
        builder.define(DATA_PHASE, (byte) PhoenixRebirth.Phase.ALIVE.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new PhoenixFlameEscapeGoal(this));
        goalSelector.addGoal(2, new PhoenixDefendOwnerGoal(this));
        // Keeps well away from whoever last hurt it.
        goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class,
                living -> living == getLastHurtByMob(), 16.0f, 1.2, 1.5, EntitySelector.NO_SPECTATORS));
        goalSelector.addGoal(4, new FollowBondedOwnerGoal<>(this));
        // Parrot-style wandering: prefers to land on leaves and logs, which is how it perches.
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, @NonNull DamageSource source) {
        return false;
    }

    // ── bond ────────────────────────────────────────────────────────────────

    @Override
    public @NonNull BondState bondState() {
        return bond;
    }

    @Override
    public void setSyncedBondLevel(int level) {
        entityData.set(DATA_BOND_LEVEL, level);
    }

    @Override
    public int getSyncedBondLevel() {
        return entityData.get(DATA_BOND_LEVEL);
    }

    // ── damage and rebirth ──────────────────────────────────────────────────

    public PhoenixRebirth rebirth() {
        return rebirth;
    }

    /** The phase as the client sees it. */
    public PhoenixRebirth.Phase syncedPhase() {
        byte id = entityData.get(DATA_PHASE);
        PhoenixRebirth.Phase[] phases = PhoenixRebirth.Phase.values();
        return id >= 0 && id < phases.length ? phases[id] : PhoenixRebirth.Phase.ALIVE;
    }

    private void syncPhase() {
        entityData.set(DATA_PHASE, (byte) rebirth.phase().ordinal());
    }

    /** Whether a phoenix rises again from this: anything short of the void or a command. */
    public static boolean rebornFrom(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isInvulnerableTo(@NonNull ServerLevel level, @NonNull DamageSource source) {
        return (rebirth.burning() && rebornFrom(source)) || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            onBondedHurt(source);
            if (!rebirth.burning() && isAlive()) {
                playFlap();
            }
        }
        return hurt;
    }

    @Override
    public void die(@NonNull DamageSource cause) {
        if (level() instanceof ServerLevel level && rebornFrom(cause) && ModuleManager.isEnabled(Module.CREATURES)
                && rebirth.begin()) {
            burn(level, cause);
            return;
        }
        super.die(cause);
    }

    /** Bursts into flame. Only ever entered through {@link PhoenixRebirth#begin()}, so only once per death. */
    private void burn(ServerLevel level, DamageSource cause) {
        setHealth(1.0f);
        ejectPassengers();
        catching = false;
        songTick = -1;
        removeAllEffects();
        clearFire();
        getNavigation().stop();
        setTarget(null);
        setNoGravity(false);
        setDeltaMovement(0.0, Math.min(0.0, getDeltaMovement().y), 0.0);
        syncPhase();
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.5, getZ(), 70, 0.5, 0.7, 0.5, 0.06);
        // The mod's embers, not vanilla LAVA: each lava pop trails its own black vanilla smoke for its
        // whole life, which drew a black column through the burst.
        level.sendParticles(new SpellTintParticleOptions(ModParticles.FIRE_EMBER.get(), MagicColours.FIRE),
                getX(), getY() + 0.4, getZ(), 12, 0.4, 0.3, 0.4, 0.02);
        // Fire's own smoke: the burst's ash in the fire colour, as pixel puffs.
        MagicSmoke.burst(level, getX(), getY() + 0.3, getZ(), MagicColours.FIRE, 10, 0.4, 0.2);
        level.playSound(null, blockPosition(), ModSounds.PHOENIX_BURST.get(), SoundSource.NEUTRAL, 1.2f, 1.0f);
        if (cause.getEntity() instanceof ServerPlayer player) {
            BestiaryDiscoveryHandler.encountered(player, this);
        }
        BestiaryDiscoveryHandler.signatureSeenByNearby(this, 32.0);
    }

    private void tickBurning(ServerLevel level) {
        setNoGravity(false);
        setDeltaMovement(0.0, Math.min(0.0, getDeltaMovement().y), 0.0);
        switch (rebirth.tick()) {
            case RISE -> {
                applyScale(WildlifeRules.REBORN_SCALE);
                rebirthAge = tickCount;
                level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.3, getZ(), 40, 0.25, 0.9, 0.25, 0.04);
                level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.5, getZ(), 12, 0.3, 0.6, 0.3, 0.02);
                level.playSound(null, blockPosition(), ModSounds.PHOENIX_RISE.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
            }
            case REBORN -> setHealth(getMaxHealth() * WildlifeRules.REBORN_HEALTH_FRACTION);
            case NONE -> {
                if (rebirth.phase() == PhoenixRebirth.Phase.ASHES && tickCount % 6 == 0) {
                    MagicSmoke.burst(level, getX(), getY() + 0.2, getZ(), MagicColours.FIRE, 1, 0.25, 0.05);
                    level.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.15, getZ(), 1, 0.2, 0.02, 0.2, 0.0);
                }
            }
        }
        syncPhase();
    }

    /** True while the reborn chick is still growing back. */
    public boolean isReborn() {
        return rebirthAge >= 0L && tickCount - rebirthAge < WildlifeRules.REBIRTH_GROWTH_TICKS;
    }

    private void applyScale(float scale) {
        AttributeInstance attribute = getAttribute(Attributes.SCALE);
        if (attribute != null && attribute.getBaseValue() != scale) {
            attribute.setBaseValue(scale);
        }
    }

    /**
     * Nothing thinks, moves or steers while it is ashes or while it carries someone down: vanilla's own gate,
     * which skips the goals, navigation and controls and zeroes input (`LivingEntity.aiStep`).
     */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || rebirth.burning() || catching;
    }

    // ── tick ────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        if (rebirth.burning()) {
            tickBurning(level);
            return;
        }
        tickBond();
        if (rebirthAge >= 0L) {
            float scale = WildlifeRules.rebirthScale(tickCount - rebirthAge);
            applyScale(scale);
            if (scale >= 1.0f) {
                rebirthAge = -1L;
            }
        }
        if (tearsCooldown > 0) tearsCooldown--;
        if (flameCooldown > 0) flameCooldown--;
        if (songCooldown > 0) songCooldown--;
        if (shedTicks > 0) shedTicks--;
        if (songTick >= 0) {
            PhoenixSong.note(level, this, songTick);
            if (++songTick >= WildlifeRules.SONG_TICKS) {
                songTick = -1;
            }
        }
        if (catching) {
            tickCarry(level);
        } else {
            catchFallingOwner(level);
        }
        tickFlightBeats();
        if (tickCount % 20 == 0) {
            weepIfNeeded(level);
            weepForItself(level);
            singIfNeeded(level);
            shedIfDue();
        }
        if (tickCount % 40 == 0) {
            returnToOwner(level);
        }
    }

    /** Takeoff and landing, read off the server's own ground state and played to everyone. */
    private void tickFlightBeats() {
        boolean ground = onGround();
        if (wasOnGround && !ground && getDeltaMovement().y > 0.02 && !catching) {
            triggerAnim(ACTION_CONTROLLER, "takeoff");
        } else if (!wasOnGround && ground && airTicks > 20) {
            triggerAnim(ACTION_CONTROLLER, "land");
        }
        airTicks = ground ? 0 : airTicks + 1;
        wasOnGround = ground;
    }

    void playFlap() {
        triggerAnim(ACTION_CONTROLLER, "flap");
    }

    // ── tears ───────────────────────────────────────────────────────────────

    /** A loyal phoenix weeps over its person when they are badly hurt or poisoned. */
    public boolean weepIfNeeded(ServerLevel level) {
        if (tearsCooldown > 0 || bondLevel() < TEARS_BOND) {
            return false;
        }
        Player owner = resolveBondOwner();
        if (owner == null || !owner.isAlive() || owner.level() != level
                || owner.distanceToSqr(this) > TEARS_RANGE * TEARS_RANGE) {
            return false;
        }
        boolean wounded = owner.getHealth() <= owner.getMaxHealth() * WildlifeRules.TEARS_HEALTH_FRACTION;
        if (!wounded && !PhoenixTears.poisoned(owner)) {
            return false;
        }
        PhoenixTears.weepOver(level, this, owner);
        tearsCooldown = WildlifeRules.TEARS_COOLDOWN_TICKS;
        return true;
    }

    /** A hurt phoenix that is left alone long enough heals itself the same way. */
    public boolean weepForItself(ServerLevel level) {
        if (tearsCooldown > 0 || getHealth() >= getMaxHealth() * 0.5f
                || (getLastHurtByMob() != null && tickCount - getLastHurtByMobTimestamp() < 200)) {
            return false;
        }
        PhoenixTears.weepOver(level, this, this);
        tearsCooldown = WildlifeRules.TEARS_COOLDOWN_TICKS;
        return true;
    }

    // ── song ────────────────────────────────────────────────────────────────

    private void singIfNeeded(ServerLevel level) {
        if (songCooldown > 0 || songTick >= 0 || bondLevel() < TEARS_BOND) {
            return;
        }
        Player owner = resolveBondOwner();
        if (owner == null || !owner.isAlive() || owner.level() != level
                || owner.distanceToSqr(this) > WildlifeRules.SONG_RANGE * WildlifeRules.SONG_RANGE) {
            return;
        }
        int sinceHurt = owner.getLastHurtByMob() == null ? Integer.MAX_VALUE
                : owner.tickCount - owner.getLastHurtByMobTimestamp();
        if (WildlifeRules.inDanger(owner.getHealth(), owner.getMaxHealth(), sinceHurt)) {
            sing(level);
        }
    }

    /** Starts a song. False when one is already playing or the last one was too recent. */
    public boolean sing(ServerLevel level) {
        if (songCooldown > 0 || songTick >= 0 || rebirth.burning()) {
            return false;
        }
        triggerAnim(ACTION_CONTROLLER, "sing");
        PhoenixSong.begin(level, this);
        songTick = 0;
        songCooldown = WildlifeRules.SONG_COOLDOWN_TICKS;
        return true;
    }

    public boolean singing() {
        return songTick >= 0;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return singing() || rebirth.burning() ? null : ModSounds.PHOENIX_CRY.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    // ── flame travel ────────────────────────────────────────────────────────

    public boolean canFlameTravel() {
        return !rebirth.burning() && flameCooldown <= 0 && !isPassenger() && !isVehicle();
    }

    /** Vanishes here and reappears at {@code destination}, which must come from {@link PhoenixFlameTravel#landingNear}. */
    public void flameTravel(ServerLevel level, Vec3 destination) {
        PhoenixFlameTravel.travel(level, this, destination);
        flameCooldown = WildlifeRules.FLAME_TRAVEL_COOLDOWN_TICKS;
    }

    /** A phoenix that follows its person comes by flame when they are far off in the same world. */
    private void returnToOwner(ServerLevel level) {
        if (!followsOwner() || !canFlameTravel()) {
            return;
        }
        Player owner = resolveBondOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.level() != level
                || owner.distanceToSqr(this) < WildlifeRules.FLAME_RETURN_DISTANCE * WildlifeRules.FLAME_RETURN_DISTANCE) {
            return;
        }
        PhoenixFlameTravel.landingNear(level, this, owner.position().add(0, 2, 0), 3)
                .ifPresent(target -> flameTravel(level, target));
    }

    // ── carrying ────────────────────────────────────────────────────────────

    /**
     * Catches its person out of a fall and lowers them to the ground.
     *
     * <p>Canon: phoenixes "can carry immensely heavy loads" (Fantastic Beasts); Fawkes lifts Harry, Ron, Ginny and
     * Lockhart out of the Chamber of Secrets. The Minecraft reading is narrow on purpose: only a phoenix that follows
     * its person, only for a real fall, only same world and within reach of its flame; it arrives by flame above
     * them, they hang from it as an ordinary passenger (no steering), and it sinks with them until their feet are
     * near the ground. There is no new mount system — vanilla riding, driven by the phoenix.
     */
    private void catchFallingOwner(ServerLevel level) {
        if (!followsOwner() || isVehicle() || rebirth.burning()) {
            return;
        }
        Player owner = resolveBondOwner();
        if (owner == null || !owner.isAlive() || owner.level() != level || owner.isPassenger()
                || owner.onGround() || owner.isFallFlying() || owner.getAbilities().flying
                || owner.fallDistance < WildlifeRules.CATCH_FALL_DISTANCE || owner.getDeltaMovement().y > -0.5
                || owner.distanceToSqr(this) > 48.0 * 48.0) {
            return;
        }
        boolean close = owner.distanceToSqr(this) < 4.0 * 4.0;
        if (!close) {
            if (flameCooldown > 0) {
                return;
            }
            Optional<Vec3> above = PhoenixFlameTravel.landingNear(level, this,
                    owner.position().add(0, owner.getBbHeight() + 0.3, 0), 1);
            if (above.isEmpty()) {
                return;
            }
            flameTravel(level, above.get());
        }
        catching = true;
        if (!owner.startRiding(this, false, true)) {
            catching = false;
            return;
        }
        carryTicks = 0;
        owner.resetFallDistance();
        setNoGravity(true);
        getNavigation().stop();
    }

    private void tickCarry(ServerLevel level) {
        Entity rider = getFirstPassenger();
        if (!(rider instanceof LivingEntity carried) || ++carryTicks > CARRY_LIMIT_TICKS) {
            endCarry();
            return;
        }
        carried.resetFallDistance();
        setDeltaMovement(0.0, -0.18, 0.0);
        BlockPos below = BlockPos.containing(getX(), getY() - carried.getBbHeight() - 0.6, getZ());
        boolean nearGround = !level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                || !level.getFluidState(below).isEmpty() || onGround();
        if (nearGround) {
            endCarry();
        }
    }

    private void endCarry() {
        ejectPassengers();
        catching = false;
        carryTicks = 0;
        setNoGravity(false);
    }

    @Override
    protected boolean canAddPassenger(@NonNull Entity passenger) {
        return catching && getPassengers().isEmpty();
    }

    /** Its person hangs beneath it, holding on. */
    @Override
    protected void positionRider(@NonNull Entity passenger, @NonNull MoveFunction move) {
        move.accept(passenger, getX(), getY() - passenger.getBbHeight() + 0.25, getZ());
    }

    // ── shedding ────────────────────────────────────────────────────────────

    private void shedIfDue() {
        boolean calm = getLastHurtByMob() == null || tickCount - getLastHurtByMobTimestamp() > 600;
        Item feather = BuiltInRegistries.ITEM.getValue(FEATHER);
        if (calm && feather != null && WildlifeRules.shedDue(shedTicks, CreatureSign.lyingNearby(this, feather))) {
            CreatureSign.leave(this, feather, 1);
            shedTicks = SHED_TICKS;
        }
    }

    // ── loyalty shown ───────────────────────────────────────────────────────

    /** A player who kills what just hurt a phoenix has shown it loyalty. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(event.getEntity().level() instanceof ServerLevel level)
                || !ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        LivingEntity slain = event.getEntity();
        for (PhoenixEntity phoenix : level.getEntitiesOfClass(PhoenixEntity.class,
                player.getBoundingBox().inflate(TEARS_RANGE))) {
            if (phoenix.getLastHurtByMob() == slain && phoenix.tickCount - phoenix.getLastHurtByMobTimestamp() < 200) {
                phoenix.defendedBy(player);
            }
        }
    }

    public void defendedBy(Player player) {
        increaseBond(player, DEFENDED_BOND, true);
    }

    // ── save ────────────────────────────────────────────────────────────────

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        saveBond(output);
        output.store(KEY_SHED, Codec.INT, shedTicks);
        output.store(KEY_TEARS, Codec.INT, tearsCooldown);
        output.store(KEY_FLAME, Codec.INT, flameCooldown);
        output.store(KEY_SONG, Codec.INT, songCooldown);
        if (rebirth.burning()) {
            output.store(KEY_PHASE, Codec.STRING, rebirth.phase().name());
            output.store(KEY_PHASE_TICKS, Codec.INT, rebirth.ticksLeft());
        }
        if (rebirthAge >= 0L) {
            // Stored as growth elapsed, because tickCount restarts at zero when the entity is loaded.
            output.store(KEY_REBIRTH, Codec.LONG, tickCount - rebirthAge);
        }
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        loadBond(input);
        shedTicks = input.read(KEY_SHED, Codec.INT).orElse(SHED_TICKS);
        tearsCooldown = input.read(KEY_TEARS, Codec.INT).orElse(0);
        flameCooldown = input.read(KEY_FLAME, Codec.INT).orElse(0);
        songCooldown = input.read(KEY_SONG, Codec.INT).orElse(0);
        PhoenixRebirth.Phase phase = input.read(KEY_PHASE, Codec.STRING)
                .map(name -> {
                    try {
                        return PhoenixRebirth.Phase.valueOf(name);
                    } catch (IllegalArgumentException e) {
                        return PhoenixRebirth.Phase.ALIVE;
                    }
                }).orElse(PhoenixRebirth.Phase.ALIVE);
        rebirth.restore(phase, input.read(KEY_PHASE_TICKS, Codec.INT).orElse(0));
        syncPhase();
        @Nullable Long grown = input.read(KEY_REBIRTH, Codec.LONG).orElse(null);
        rebirthAge = grown == null ? -1L : tickCount - grown;
        if (grown != null) {
            applyScale(WildlifeRules.rebirthScale(grown));
        }
    }

    // ── animation ───────────────────────────────────────────────────────────

    /**
     * Movement first, one-shots second, rebirth last: GeckoLib gives the shared bones to whichever controller comes
     * last, so a triggered beat plays over the loop, and the burning plays over everything.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<PhoenixEntity>("phoenix_movement", 4, this::movement));
        controllers.add(new AnimationController<PhoenixEntity>(ACTION_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim("takeoff", anim("takeoff", false))
                .triggerableAnim("land", anim("land", false))
                .triggerableAnim("flap", anim("flap", false))
                .triggerableAnim("sing", anim("sing", false)));
        controllers.add(new AnimationController<PhoenixEntity>("phoenix_rebirth", 0, this::burning));
    }

    private PlayState movement(AnimationTest<PhoenixEntity> test) {
        if (syncedPhase() != PhoenixRebirth.Phase.ALIVE) {
            return PlayState.STOP;
        }
        double dx = getX() - xo;
        double dy = getY() - yo;
        double dz = getZ() - zo;
        double horizontal = dx * dx + dz * dz;
        if (isVehicle()) {
            return test.setAndContinue(FLY);   // carrying someone down
        }
        if (onGround()) {
            return test.setAndContinue(horizontal > 1.0e-4 ? WALK : IDLE);
        }
        return test.setAndContinue(dy < -0.04 && horizontal > 0.0064 ? GLIDE : FLY);
    }

    private PlayState burning(AnimationTest<PhoenixEntity> test) {
        return switch (syncedPhase()) {
            case ASHES -> test.setAndContinue(BURN);
            case RISING -> test.setAndContinue(RISE);
            case ALIVE -> PlayState.STOP;
        };
    }
}
