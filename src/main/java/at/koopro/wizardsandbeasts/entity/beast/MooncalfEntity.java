package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.bond.BondState;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.bond.FollowBondedOwnerGoal;
import at.koopro.wizardsandbeasts.creature.wildlife.CreatureSign;
import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.event.heritage.WerewolfMoonHandler;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.EnumSet;
import java.util.List;

/**
 * Mooncalf — a shy, smooth-skinned grey creature with huge upward-staring eyes, which lives in burrows and comes out
 * only at the full moon, when the herd dances on its hind legs in the fields.
 *
 * <p><b>Outside the full moon a wild mooncalf is burrowed.</b> It keeps to its burrow, still and out of sight; nobody
 * sees it, so nobody learns anything about it. A kept mooncalf — one bonded to a wizard — lives above ground with its
 * keeper like any tame animal.
 *
 * <p><b>Under the full moon the herd gathers and dances.</b> Wild mooncalves come up, walk to the middle of their herd,
 * and dance there; a wizard who watches it has seen the thing mooncalves are known for. A dance is broken by anyone who
 * comes at them upright and loud — creep up, or they scatter.
 *
 * <p><b>The dance leaves something behind.</b> At dawn after a dance, each dancer leaves dung where it danced, which is
 * where mooncalf dung comes from in the wild. Killing a mooncalf yields nothing.
 *
 * <p><b>It can be kept.</b> A bonded mooncalf produces the same dung on a timer
 * ({@code data/wizards_and_beasts/creature_bonds/mooncalf.json}), so a herd is worth more alive than dead.
 */
public class MooncalfEntity extends GeoEntityBase implements BondableBeast {

    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("mooncalf", "idle");
    private static final RawAnimation WALK_ANIM = AnimHelper.loop("mooncalf", "walk");
    private static final RawAnimation DANCE_ANIM = AnimHelper.loop("mooncalf", "dance");

    /** True while the moon is full overhead. Synced because the client picks the clip from it. */
    private static final EntityDataAccessor<Boolean> DATA_DANCING =
            SynchedEntityData.defineId(MooncalfEntity.class, EntityDataSerializers.BOOLEAN);

    /** Bond level, synced for the client. Must be defined on the concrete class that uses it. */
    private static final EntityDataAccessor<Integer> DATA_BOND_LEVEL =
            SynchedEntityData.defineId(MooncalfEntity.class, EntityDataSerializers.INT);

    public static final Identifier DUNG = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "mooncalf_dung");

    /** How often the moon is re-checked, in ticks. The phase turns once a day. */
    private static final int MOON_CHECK_INTERVAL = 40;
    /** How far a mooncalf looks for the rest of its herd. */
    public static final double HERD_RADIUS = 24.0;
    /** Close enough to the middle of the herd to dance. */
    public static final double DANCE_SPOT_RADIUS = 2.5;
    /** Anyone who can see a dance from this far has witnessed it. */
    public static final double WITNESS_RADIUS = 20.0;

    private static final String KEY_BURROW = "Burrow";
    private static final String KEY_DANCED = "DancedTonight";

    private final BondState bond = new BondState();
    private @Nullable BlockPos burrow;
    private boolean dancedTonight;

    public MooncalfEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 14.0)
                // Declared so a bred juvenile can be shrunk: BondableBeast scales young through SCALE.
                .add(Attributes.SCALE, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DANCING, false);
        builder.define(DATA_BOND_LEVEL, 0);
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

    @Override
    protected @NonNull InteractionResult mobInteract(@NonNull Player player, @NonNull InteractionHand hand) {
        InteractionResult fed = offerBondFood(player, hand);
        return fed != InteractionResult.PASS ? fed : super.mobInteract(player, hand);
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            onBondedHurt(source);
        }
        return hurt;
    }

    // ── the moon ────────────────────────────────────────────────────────────

    /** A wild mooncalf belongs to no one; a kept one lives with its keeper. */
    public boolean isWild() {
        return bond.ownerUUID() == null;
    }

    public boolean isMoonUp() {
        return this.entityData.get(DATA_DANCING);
    }

    /** Burrowed: wild, and the moon is not full. Unseen, and going nowhere. */
    public boolean isBurrowed() {
        return isWild() && !isMoonUp();
    }

    /** Dancing: the moon is full and it has stopped where it means to be. */
    public boolean isDancing() {
        return isMoonUp() && getDeltaMovement().horizontalDistanceSqr() < 1.0e-4 && getNavigation().isDone();
    }

    public @Nullable BlockPos burrow() {
        return burrow;
    }

    @Override
    public void tick() {
        super.tick();
        tickBond();
        if (!(level() instanceof ServerLevel serverLevel) || !ModuleManager.isEnabled(Module.CREATURES)) {
            return;
        }
        if (burrow == null) {
            burrow = blockPosition();
        }
        if (tickCount % MOON_CHECK_INTERVAL == 0) {
            refreshMoon(serverLevel, WerewolfMoonHandler.moonIsUp(serverLevel));
        }
        setInvisible(isBurrowed());
    }

    /**
     * Brings the mooncalf up to date with the moon: a dancer is noticed by whoever is watching, and the morning after a
     * dance it leaves its dung behind.
     */
    public void refreshMoon(ServerLevel level, boolean moonUp) {
        this.entityData.set(DATA_DANCING, moonUp);
        if (moonUp && isWild() && isDancing() && nearHerdCentre()) {
            dancedTonight = true;
            BestiaryDiscoveryHandler.signatureSeenByNearby(this, WITNESS_RADIUS);
        }
        if (!moonUp && dancedTonight) {
            Item dung = BuiltInRegistries.ITEM.getValue(DUNG);
            if (dung != null) {
                CreatureSign.leave(this, dung, 1);
            }
            dancedTonight = false;
        }
    }

    public boolean dancedTonight() {
        return dancedTonight;
    }

    /** The middle of the wild herd around it, itself included. */
    public Vec3 herdCentre() {
        List<MooncalfEntity> herd = level().getEntitiesOfClass(MooncalfEntity.class,
                getBoundingBox().inflate(HERD_RADIUS), other -> other.isAlive() && other.isWild());
        if (herd.isEmpty()) {
            return position();
        }
        double x = 0;
        double y = 0;
        double z = 0;
        for (MooncalfEntity member : herd) {
            x += member.getX();
            y += member.getY();
            z += member.getZ();
        }
        return new Vec3(x / herd.size(), y / herd.size(), z / herd.size());
    }

    public boolean nearHerdCentre() {
        return position().distanceToSqr(herdCentre()) <= DANCE_SPOT_RADIUS * DANCE_SPOT_RADIUS;
    }

    // ── goals ───────────────────────────────────────────────────────────────

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new StayInBurrowGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.5));
        // A wild herd scatters from anyone who comes at it upright; a creeping watcher is tolerated.
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class,
                living -> isWild() && !living.isShiftKeyDown(), 8.0f, 1.0, 1.4, EntitySelector.NO_SPECTATORS));
        goalSelector.addGoal(3, new FollowBondedOwnerGoal<>(this));
        goalSelector.addGoal(4, new GatherToDanceGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override
            public boolean canUse() {
                // A wild mooncalf out under the moon has somewhere to be; it does not wander off.
                return !(isWild() && isMoonUp()) && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** Holds a burrowed mooncalf at its burrow, taking every other goal's movement away while it is there. */
    static final class StayInBurrowGoal extends Goal {
        private final MooncalfEntity mooncalf;

        StayInBurrowGoal(MooncalfEntity mooncalf) {
            this.mooncalf = mooncalf;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return ModuleManager.isEnabled(Module.CREATURES) && mooncalf.isBurrowed();
        }

        @Override
        public void start() {
            mooncalf.getNavigation().stop();
        }

        @Override
        public void tick() {
            BlockPos home = mooncalf.burrow();
            if (home != null && mooncalf.blockPosition().distSqr(home) > 4) {
                mooncalf.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
            }
        }
    }

    /** Under the full moon, walks to the middle of the herd and stops there to dance. */
    static final class GatherToDanceGoal extends Goal {
        private final MooncalfEntity mooncalf;

        GatherToDanceGoal(MooncalfEntity mooncalf) {
            this.mooncalf = mooncalf;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return ModuleManager.isEnabled(Module.CREATURES) && mooncalf.isWild() && mooncalf.isMoonUp()
                    && !mooncalf.nearHerdCentre();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && !mooncalf.getNavigation().isDone();
        }

        @Override
        public void start() {
            Vec3 centre = mooncalf.herdCentre();
            mooncalf.getNavigation().moveTo(centre.x, centre.y, centre.z, 0.8);
        }
    }

    // ── save ────────────────────────────────────────────────────────────────

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        saveBond(output);
        if (burrow != null) {
            output.store(KEY_BURROW, BlockPos.CODEC, burrow);
        }
        output.store(KEY_DANCED, Codec.BOOL, dancedTonight);
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        loadBond(input);
        burrow = input.read(KEY_BURROW, BlockPos.CODEC).orElse(null);
        dancedTonight = input.read(KEY_DANCED, Codec.BOOL).orElse(false);
    }

    /**
     * Dance while the moon is full and it has stopped moving; otherwise the usual walk/idle split. A mooncalf that keeps
     * dancing while it walks looks broken, and one that stops dancing the instant it is nudged looks alive.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("mooncalf_movement", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(WALK_ANIM);
            }
            return state.setAndContinue(isMoonUp() ? DANCE_ANIM : IDLE_ANIM);
        }));
    }
}
