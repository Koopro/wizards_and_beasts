package at.koopro.wizardsandbeasts.entity.beast;

import at.koopro.wizardsandbeasts.entity.GeoEntityBase;
import at.koopro.wizardsandbeasts.entity.beast.ai.HidebehindStalkGoal;
import at.koopro.wizardsandbeasts.util.AnimHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Hidebehind — a forest stalker, invisible to anyone looking straight at it, vulnerable to attacks
 * from its own blind spot.
 *
 * <p>Visibility reuses the exact per-local-viewer hook {@link ThestralEntity} established
 * ({@code isInvisible()} always true, {@code isInvisibleTo(Player)} decides per viewer) but on a purely
 * geometric condition instead of a synced flag: the passed-in {@code player} — confirmed
 * (Phase 1 research) to be the local client viewer when this is consulted client-side — is checked
 * directly against its own eye position/view vector, no network sync needed at all. The gaze-meeting
 * dot-product itself mirrors {@code DeathGazeGoal}'s technique, just inverted (visible when NOT faced).
 */
public class HidebehindEntity extends GeoEntityBase {

    private static final double FACE_DOT_THRESHOLD = 0.75;
    private static final float BEHIND_DAMAGE_MULTIPLIER = 3.0f;

    private static final RawAnimation IDLE_ANIM = AnimHelper.loop("hidebehind", "idle");
    private static final RawAnimation WALK_ANIM = AnimHelper.loop("hidebehind", "walk");

    public HidebehindEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 35.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    public boolean isInvisibleTo(Player player) {
        if (!level().isClientSide()) {
            return true;
        }
        Vec3 toMe = position().add(0, getBbHeight() * 0.5, 0).subtract(player.getEyePosition()).normalize();
        double dot = player.getViewVector(1.0f).dot(toMe);
        return dot > FACE_DOT_THRESHOLD;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float finalAmount = amount;
        if (source.getEntity() instanceof LivingEntity attacker) {
            float yawRad = getYRot() * Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad));
            Vec3 toAttacker = attacker.position().subtract(position()).normalize();
            if (forward.dot(toAttacker) < -0.3) {
                finalAmount *= BEHIND_DAMAGE_MULTIPLIER;
            }
        }
        return super.hurtServer(level, source, finalAmount);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new HidebehindStalkGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<HidebehindEntity>("movement", 5, test ->
                test.setAndContinue(test.isMoving() ? WALK_ANIM : IDLE_ANIM)));
    }
}
