package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.entity.PartEntity;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Runespoor — a three-headed serpent. Each head is a separate {@link RunespoorHeadPart} hitbox routed
 * through {@link #hurtFromHead}, but all three share one health pool (this entity's own). Bespoke,
 * not datapack-driven: multi-part health/behaviour has no equivalent in the single-hitbox
 * {@code CreatureDefinition}/{@code GenericBeastEntity} model, matching how {@link ThestralEntity} and
 * its siblings opt out of that system for behaviour vanilla can't express generically.
 *
 * <p>The three heads are pure hit-detection hitboxes, not independently rendered — GeckoLib
 * multi-part entities draw once through the parent's own model (a single rig with three head bones);
 * the parts exist only so a player can land a hit on a specific head, which then decides <em>how</em>
 * the shared health pool reacts (mirrors vanilla {@code EnderDragon}/{@code EnderDragonPart}: different
 * defense per part, single point of health).
 */
public class RunespoorEntity extends GeoEntityBase {

    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("runespoor", "idle");
    private static final RawAnimation WALK_ANIM = AnimHelper.loop("runespoor", "walk");

    private final RunespoorHeadPart planHead;
    private final RunespoorHeadPart judgeHead;
    private final RunespoorHeadPart criticHead;
    private final RunespoorHeadPart[] parts;

    public RunespoorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.planHead = new RunespoorHeadPart(this, "plan", 0.6f, 0.6f);
        this.judgeHead = new RunespoorHeadPart(this, "judge", 0.6f, 0.6f);
        this.criticHead = new RunespoorHeadPart(this, "critic", 0.6f, 0.6f);
        this.parts = new RunespoorHeadPart[] {planHead, judgeHead, criticHead};
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            positionParts();
        }
    }

    private void positionParts() {
        positionPart(planHead, 0.0, 0.35);
        positionPart(judgeHead, -0.35, -0.2);
        positionPart(criticHead, 0.35, -0.2);
    }

    private void positionPart(RunespoorHeadPart part, double localX, double localZ) {
        float yawRad = getYRot() * Mth.DEG_TO_RAD;
        double worldX = getX() + (localX * Mth.cos(yawRad) - localZ * Mth.sin(yawRad));
        double worldZ = getZ() + (localX * Mth.sin(yawRad) + localZ * Mth.cos(yawRad));
        part.setPos(worldX, getY() + getBbHeight() * 0.65, worldZ);
    }

    /**
     * Routes damage landed on a specific head. Each head applies its own effect to the attacker before
     * the shared health pool (this entity's own) takes the damage — plan-head marks the attacker
     * (Glowing), judge-head poisons, critic-head weakens and knocks back.
     */
    boolean hurtFromHead(RunespoorHeadPart part, ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker) {
            if (part == judgeHead) {
                attacker.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            } else if (part == criticHead) {
                attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
                attacker.knockback(0.6, getX() - attacker.getX(), getZ() - attacker.getZ());
            } else if (part == planHead) {
                attacker.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
            }
        }
        return this.hurtServer(level, source, amount);
    }

    @Override
    public void die(DamageSource cause) {
        if (!level().isClientSide()) {
            for (RunespoorHeadPart part : parts) {
                part.discard();
            }
        }
        super.die(cause);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<RunespoorEntity>("movement", 5, test ->
                test.setAndContinue(test.isMoving() ? WALK_ANIM : IDLE_ANIM)));
    }
}
