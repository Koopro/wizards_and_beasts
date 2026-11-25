package at.koopro.wizardsandbeasts.spell.beam;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Who the Killing Curse actually strikes: the first living body on the line between wand and target.
 *
 * <p>Ruling (systems audit B4, owner OK 2026-11-25): Avada Kedavra stays unblockable — no Shield Charm stops it,
 * in duels or in PvP, because canon never lets one (Moody, GoF ch.14: "There's no countercurse. There's no
 * blocking it."). It is answered three ways instead, all canon: it is <em>dodged</em> (the beam must be held on
 * a target through a telegraphed wind-up, and breaking line of sight cancels it), it is <em>gated</em> (DARK_ARTS
 * module, PROFICIENT Imperio and Crucio, the Unforgivable toll), and it is <em>interposed</em>: a solid block
 * stops it (Dumbledore's conjured and animated statues in the Atrium, OotP ch.36) and so does a body in the way —
 * Fawkes swallowed the jet whole and burst into flame (same chapter). The beam's target is chosen by a scan every
 * few ticks, so without this a creature stepping into the beam after the scan was passed through.
 */
public final class AvadaInterposition {

    private AvadaInterposition() {}

    /**
     * The living entity nearest the caster whose box the wand-to-target line crosses, or {@code target} when
     * nothing living stands in between.
     */
    public static LivingEntity struck(ServerPlayer caster, LivingEntity target) {
        Vec3 start = caster.getEyePosition();
        Vec3 end = target.getBoundingBox().getCenter();
        AABB search = new AABB(start, end).inflate(1.0);
        LivingEntity closest = target;
        double closestDistSqr = start.distanceToSqr(end);
        for (Entity candidate : caster.level().getEntities(caster, search,
                e -> e != target && e instanceof LivingEntity living && living.isAlive() && !e.isSpectator())) {
            AABB box = candidate.getBoundingBox().inflate(Math.max(0.1, candidate.getPickRadius()));
            Optional<Vec3> intercept = box.clip(start, end);
            if (intercept.isEmpty()) {
                continue;
            }
            double distSqr = start.distanceToSqr(intercept.get());
            if (distSqr < closestDistSqr) {
                closestDistSqr = distSqr;
                closest = (LivingEntity) candidate;
            }
        }
        return closest;
    }
}
