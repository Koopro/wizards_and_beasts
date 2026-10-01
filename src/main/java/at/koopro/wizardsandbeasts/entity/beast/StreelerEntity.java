package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.wildlife.SignatureRules;
import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.NonNull;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;

/**
 * Streeler — a giant snail that changes colour hourly and leaves a trail so venomous it withers and burns all the
 * vegetation it passes over (Fantastic Beasts). Its venom is one of the few things known to kill Horklumps.
 *
 * <ul>
 *   <li><b>Colour:</b> {@link #colour()} holds for an in-game hour, then changes — read from the day clock both sides
 *       share and an offset taken from the UUID, so nothing is synced and two Streelers rarely match. Whoever is near
 *       when the hour turns has seen its signature.</li>
 *   <li><b>Trail:</b> every block it crawls onto loses its plants (grass, flowers, crops), and grass underneath is
 *       burned to dirt. Only where mobs may grief.</li>
 *   <li><b>Venom:</b> a Horklump it touches is poisoned by the trail and dies of it.</li>
 * </ul>
 *
 * <p>It never attacks: the danger is to gardens, which is why keepers keep it off the beds, and the use is the same
 * venom turned on Horklump infestations.
 */
public class StreelerEntity extends GeoEntityBase {

    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("streeler", "idle");
    private static final RawAnimation WALK_ANIM = AnimHelper.loop("streeler", "walk");
    /** One-shots (hit, death), registered after movement so they override the loop. */
    private static final String ACTION = "streeler_action";

    /** How often the trail bites the Horklumps it touches. */
    static final int VENOM_INTERVAL = 20;
    /** How close a Horklump must be to the Streeler to be on its trail. */
    static final double VENOM_REACH = 1.2;
    /** Damage to a Horklump per bite of venom. */
    static final float VENOM_DAMAGE = 2.0f;

    private static final Identifier HORKLUMP =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "horklump");

    private BlockPos lastTrail = BlockPos.ZERO;
    private long lastHour = Long.MIN_VALUE;

    public StreelerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    /** This hour's colour, ARGB. The same on server and client: day time and the UUID are both shared. */
    public int colour() {
        return SignatureRules.streelerColour(level().getDayTime(), phase());
    }

    private int phase() {
        return (int) (getUUID().getLeastSignificantBits() & 0x7);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level) || !isAlive()) {
            return;
        }
        long hour = Math.floorDiv(level.getDayTime(), SignatureRules.HOUR_TICKS);
        if (lastHour != Long.MIN_VALUE && hour != lastHour) {
            BestiaryDiscoveryHandler.signatureSeenByNearby(this, 16.0);
        }
        lastHour = hour;
        if (onGround() && !blockPosition().equals(lastTrail)) {
            lastTrail = blockPosition();
            witherUnder(level, lastTrail);
        }
        if (tickCount % VENOM_INTERVAL == 0) {
            poisonHorklumps(level);
        }
    }

    /**
     * The trail: plants at {@code pos} die, grass below is burned to dirt. Nothing drops — the venom destroys them.
     *
     * @return how many blocks it withered
     */
    public int witherUnder(ServerLevel level, BlockPos pos) {
        if (!EventHooks.canEntityGrief(level, this)) {
            return 0;
        }
        int withered = 0;
        BlockState plant = level.getBlockState(pos);
        if (isVegetation(plant)) {
            level.destroyBlock(pos, false, this);
            withered++;
        }
        BlockPos below = pos.below();
        if (level.getBlockState(below).is(Blocks.GRASS_BLOCK)) {
            level.setBlockAndUpdate(below, Blocks.DIRT.defaultBlockState());
            withered++;
        }
        if (withered > 0) {
            level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.1, getZ(), 4, 0.3, 0.05, 0.3, 0.0);
        }
        return withered;
    }

    /** Anything growing that a trail of venom would kill: grass and ferns, flowers, saplings, crops. */
    static boolean isVegetation(BlockState state) {
        if (!state.getFluidState().isEmpty() || state.is(BlockTags.LEAVES)) {
            return false; // the tree tag counts water and leaves as replaceable; neither is ground cover
        }
        return state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS);
    }

    /**
     * Poison every Horklump on its trail.
     *
     * @return how many it bit
     */
    public int poisonHorklumps(ServerLevel level) {
        int bitten = 0;
        for (GenericBeastEntity horklump : level.getEntitiesOfClass(GenericBeastEntity.class,
                getBoundingBox().inflate(VENOM_REACH), e -> e.isAlive() && HORKLUMP.equals(e.creatureId()))) {
            horklump.hurtServer(level, level.damageSources().mobAttack(this), VENOM_DAMAGE);
            bitten++;
        }
        return bitten;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.08)
                .add(Attributes.FOLLOW_RANGE, 10.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new RandomStrollGoal(this, 0.6));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 5.0f));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) triggerAnim(ACTION, "hit");
        return hurt;
    }

    @Override
    public void die(@NonNull DamageSource cause) {
        if (!level().isClientSide()) {
            triggerAnim(ACTION, "death");
        }
        super.die(cause);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(AnimHelper.movementController("streeler", 8, IDLE_ANIM, WALK_ANIM));
        controllers.add(new AnimationController<StreelerEntity>(ACTION, 0, test -> PlayState.STOP)
                .triggerableAnim("hit", AnimHelper.playOnce("streeler", "hit"))
                .triggerableAnim("death", AnimHelper.playOnce("streeler", "death")));
    }
}
