package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.event.heritage.WerewolfMoonHandler;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Mooncalf — a shy, smooth-skinned grey creature with huge upward-staring eyes.
 *
 * <p>Skittish; flees from harm and wanders calmly otherwise. Ground quadruped.
 *
 * <p><b>It dances.</b> Mooncalves emerge only at the full moon and dance on their hind legs, and
 * the patterns they tread into crops are where the wizarding world says crop circles come from —
 * it is the whole of the creature's character, and until now nothing in the mod played it. The
 * dance runs whenever the moon is full and the mooncalf has stopped moving.
 */
public class MooncalfEntity extends GeoEntityBase {

    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("mooncalf", "idle");
    private static final RawAnimation WALK_ANIM = AnimHelper.loop("mooncalf", "walk");
    private static final RawAnimation DANCE_ANIM = AnimHelper.loop("mooncalf", "dance");

    /**
     * True while the moon is full overhead. Synced because the client picks the clip from it, and
     * read only through the animation controller — the same synced-render-flag shape as
     * {@code GenericBeastEntity.DATA_DISGUISED} and {@code GoblinTellerEntity}'s variant.
     */
    private static final EntityDataAccessor<Boolean> DATA_DANCING =
            SynchedEntityData.defineId(MooncalfEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * How often the moon is re-checked, in ticks.
     *
     * <p>The phase turns over once a day, so asking every tick would put a dimension lookup and a
     * clock read on every mooncalf in the world for an answer that changes twice a night.
     * {@code WerewolfMoonHandler} throttles its own scan for exactly this reason.
     */
    private static final int MOON_CHECK_INTERVAL = 40;

    public MooncalfEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 14.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DANCING, false);
    }

    public boolean isDancing() {
        return this.entityData.get(DATA_DANCING);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel && tickCount % MOON_CHECK_INTERVAL == 0) {
            // Reuses the werewolf's moon rules rather than restating them: full moon is phase 0,
            // and the Nether and the End are excluded because their clocks keep ticking under a
            // ceiling. Two different answers to "is the moon up" in one mod is a bug waiting for a
            // report nobody can reproduce.
            this.entityData.set(DATA_DANCING, WerewolfMoonHandler.moonIsUp(serverLevel));
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.5));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /**
     * Dance while the moon is full and it has stopped moving; otherwise the usual walk/idle split.
     *
     * <p>The movement check is deliberately part of the condition rather than a separate controller:
     * a mooncalf that keeps dancing while it walks looks broken, and one that stops dancing the
     * instant it is nudged looks alive.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("mooncalf_movement", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(WALK_ANIM);
            }
            return state.setAndContinue(isDancing() ? DANCE_ANIM : IDLE_ANIM);
        }));
    }
}
