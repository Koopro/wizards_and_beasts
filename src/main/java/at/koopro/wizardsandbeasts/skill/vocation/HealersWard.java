package at.koopro.wizardsandbeasts.skill.vocation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Healer's signature (systems audit B5): ward healing. The mod has no Healer vocation; the Herbology build
 * (capstone Healer's Hands) carries it, and the Scholar's alchemy folds into it, since a Healer brews their own
 * (St Mungo's; Madam Pomfrey's draughts).
 *
 * <ul>
 *   <li>{@link #REACH}: Episkey, which everyone else casts on themself, lands on whoever the Healer looks at within
 *   {@link #RANGE} blocks; Dittany is applied across the same distance rather than at arm's length.</li>
 *   <li>{@link #BREW}: a Healer's bottles never leave the cauldron below {@link #BREW_FLOOR}% (BrewQuality).</li>
 * </ul>
 */
@NullMarked
public final class HealersWard {

    public static final String REACH = "healers_reach";
    public static final String BREW = "healers_brew";
    public static final double RANGE = 8.0;
    public static final int BREW_FLOOR = 110;

    private HealersWard() {}

    /** Whether {@code player} heals at range. */
    public static boolean reaches(ServerPlayer player) {
        return VocationHelper.hasGrantedAbility(player, REACH);
    }

    /** The living thing {@code healer} is looking at within {@link #RANGE} blocks and in plain sight, or null. */
    public static @Nullable LivingEntity patientInView(ServerPlayer healer) {
        Vec3 eye = healer.getEyePosition();
        Vec3 end = eye.add(healer.getLookAngle().scale(RANGE));
        HitResult wall = healer.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, healer));
        if (wall.getType() == HitResult.Type.BLOCK) {
            end = wall.getLocation();
        }
        AABB sweep = healer.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(healer.level(), healer, eye, end, sweep,
                e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator(), 0.3f);
        return hit != null && hit.getEntity() instanceof LivingEntity patient ? patient : null;
    }
}
