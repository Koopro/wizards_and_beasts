package at.koopro.wizardsandbeasts.heritage.werewolf;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The bridge between the world and {@link FeralTargetSelection}: who is here, what band are they in,
 * and are they fair game at all.
 *
 * <p>Everything with a decision in it lives next door in {@link FeralTargetSelection} and
 * {@link FeralTargetPriority}, both free of Minecraft types. What is left here is classification and
 * eligibility — the parts that genuinely need the world.
 */
public final class FeralTargeting {

    private FeralTargeting() {}

    /** A target and the band it was chosen in, so the controller never has to re-classify to compare. */
    public record Acquired(LivingEntity entity, FeralTargetPriority priority) {}

    /**
     * Sorts a living entity into a priority band.
     *
     * <p><b>Order matters twice here.</b> Golems are checked before {@code Animal} because they live in
     * {@code entity.animal.golem} and read as livestock to a naive check — an iron golem is what stands
     * between a wolf and a village, not something it farms. And {@link Enemy} is checked before
     * {@link Animal} because several hostiles extend {@code Animal}: a hoglin is a fight, not a meal.
     */
    public static FeralTargetPriority classify(LivingEntity entity) {
        if (entity instanceof Player) {
            return FeralTargetPriority.PLAYER;
        }
        if (entity instanceof Npc || entity instanceof AbstractGolem) {
            return FeralTargetPriority.VILLAGER_OR_GOLEM;
        }
        if (entity instanceof Enemy) {
            return FeralTargetPriority.HOSTILE;
        }
        if (entity instanceof Animal || entity instanceof WaterAnimal) {
            return FeralTargetPriority.ANIMAL;
        }
        return FeralTargetPriority.OTHER;
    }

    /**
     * The best thing to kill within {@code radius}, or null when the night is empty.
     *
     * <p>Line of sight is required. Without it a wolf pins itself to a cow on the far side of a hill and
     * grinds against the terrain forever, which reads as a bug rather than as a predator. It is checked
     * after eligibility because it is the most expensive test of the three.
     */
    @Nullable
    public static Acquired select(ServerPlayer wolf, ServerLevel level, double radius) {
        AABB box = wolf.getBoundingBox().inflate(radius);
        List<LivingEntity> found = level.getEntitiesOfClass(
                LivingEntity.class, box, candidate -> isEligible(wolf, candidate));

        List<LivingEntity> visible = new ArrayList<>(found.size());
        List<FeralTargetSelection.Candidate> scored = new ArrayList<>(found.size());
        for (LivingEntity candidate : found) {
            if (!wolf.hasLineOfSight(candidate)) {
                continue;
            }
            visible.add(candidate);
            scored.add(candidateFor(wolf, candidate));
        }

        int best = FeralTargetSelection.pickBestIndex(scored);
        if (best < 0) {
            return null;
        }
        return new Acquired(visible.get(best), scored.get(best).priority());
    }

    /** Reduces a live entity to the pure {@link FeralTargetSelection.Candidate} the decision runs on. */
    public static FeralTargetSelection.Candidate candidateFor(ServerPlayer wolf, LivingEntity candidate) {
        return new FeralTargetSelection.Candidate(
                classify(candidate), Math.sqrt(wolf.distanceToSqr(candidate)));
    }

    /**
     * Whether the wolf may take this target at all.
     *
     * <p>The pack rule is the interesting one. Another werewolf is the single thing a feral wolf
     * recognises, so by default it is never a target — the books have Lupin's transformed self ignore
     * Sirius' dog form and go for the humans, and a server where two afflicted players cannot share a
     * night is worse for it. {@link WerewolfConfig#packBetrayal} turns the rule off for servers that
     * want the harsher reading.
     */
    public static boolean isEligible(ServerPlayer wolf, LivingEntity candidate) {
        if (candidate == wolf || !candidate.isAlive() || candidate.isRemoved() || candidate.isDeadOrDying()) {
            return false;
        }
        if (!candidate.isAttackable() || candidate.isInvulnerable()) {
            return false;
        }
        if (candidate instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return WerewolfConfig.packBetrayal || !WerewolfRules.isPackMember(candidate);
    }

    /**
     * True while an acquired target is still worth chasing. Slacker than {@link #isEligible} on distance
     * on purpose: a target that steps one block outside the aggro radius has not escaped, and a wolf
     * that dropped it there would visibly flip between two victims at the boundary.
     */
    public static boolean isStillValid(ServerPlayer wolf, LivingEntity target, double radius) {
        double leash = radius * LEASH_FACTOR;
        return isEligible(wolf, target)
                && target.level() == wolf.level()
                && wolf.distanceToSqr(target) <= leash * leash;
    }

    /** How far past the aggro radius a wolf will follow something it has already committed to. */
    public static final double LEASH_FACTOR = 1.5;
}
