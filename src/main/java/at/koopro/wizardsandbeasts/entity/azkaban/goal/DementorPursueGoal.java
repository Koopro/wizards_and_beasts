package at.koopro.wizardsandbeasts.entity.azkaban.goal;

import at.koopro.wizardsandbeasts.azkaban.attachment.AzkabanTrespasserData;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorAura;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import at.koopro.wizardsandbeasts.network.azkaban.AzkabanTrespasserSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Drift toward what it feels and feed on it.
 *
 * <p>It closes to {@link #HOVER_DISTANCE} — face height, just out of arm's reach — and hangs there rather than
 * pushing into its victim. Inside {@link DementorAura#DRAIN_RADIUS} it feeds: the {@code DRAINING} state (arms out,
 * hood tilted), a stream of pale wisps drawn from the victim to its hood, a slowing heartbeat. The harm itself is the
 * aura's; this goal is what it looks like. Anything a Patronus wards stops being felt, and it lets go.
 *
 * <p>Inside Azkaban, the first player it feels is marked a trespasser, which is what makes them fair game for a Kiss.
 * Outside Azkaban nobody is — a Dementor loose in Little Whinging is not a prison guard.
 */
public final class DementorPursueGoal extends Goal {

    static final double HOVER_DISTANCE = 2.5;
    static final double HEM_LIFT = 0.3;
    private static final double SPEED = 1.0;
    private static final int WISP_INTERVAL = 4;
    private static final int HEARTBEAT_INTERVAL = 40;

    private final DementorEntity dementor;
    private @Nullable LivingEntity target;
    private int ticks;

    public DementorPursueGoal(DementorEntity dementor) {
        this.dementor = dementor;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        target = dementor.sensed();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && dementor.canFeel(target)
                && dementor.distanceTo(target) <= DementorAura.SENSE_RADIUS;
    }

    @Override
    public void start() {
        ticks = 0;
        if (target instanceof ServerPlayer sp && dementor.insideAzkaban()) {
            AzkabanTrespasserData current = sp.getData(ModAttachments.AZKABAN_TRESPASSER_TAG.get());
            if (!current.tagged()) {
                sp.setData(ModAttachments.AZKABAN_TRESPASSER_TAG.get(), current.withTagged(true));
                PacketDistributor.sendToPlayer(sp, new AzkabanTrespasserSyncPayload(true));
            }
        }
    }

    @Override
    public void stop() {
        target = null;
        dementor.clearState(DementorEntity.State.DRAINING);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = target;
        if (t == null) return;
        // It turns to whatever it feels more strongly: a player walking in draws it off a sheep.
        LivingEntity stronger = dementor.sensed();
        if (stronger != null && stronger != t
                && DementorAura.preference(stronger) < DementorAura.preference(t)) {
            target = t = stronger;
        }
        ticks++;
        dementor.getLookControl().setLookAt(t, 10f, 10f);
        double distance = dementor.distanceTo(t);
        Vec3 hover = hoverPoint(dementor.position(), t.position());
        if (distance > HOVER_DISTANCE + 0.5) {
            dementor.getMoveControl().setWantedPosition(hover.x, hover.y, hover.z, SPEED);
        } else {
            // Hang there. Nudging the wanted point onto itself keeps the hover without drifting into the victim.
            dementor.getMoveControl().setWantedPosition(dementor.getX(), hover.y, dementor.getZ(), 0.3);
        }

        boolean draining = distance <= DementorAura.DRAIN_RADIUS;
        if (draining) {
            dementor.setState(DementorEntity.State.DRAINING);
            if (dementor.level() instanceof ServerLevel sl) {
                if (ticks % WISP_INTERVAL == 0) {
                    wisps(sl, t);
                }
                if (ticks % HEARTBEAT_INTERVAL == 0) {
                    sl.playSound(null, t.getX(), t.getY(), t.getZ(), ModSounds.DEMENTOR_DRAIN.get(),
                            dementor.getSoundSource(), 0.6f, 0.8f);
                }
            }
        } else {
            dementor.clearState(DementorEntity.State.DRAINING);
        }
    }

    /**
     * Where it hangs: {@link #HOVER_DISTANCE} short of the victim, its hem just off their floor — at 3.2 blocks tall
     * the hood then looms over a standing player's face.
     */
    static Vec3 hoverPoint(Vec3 dementor, Vec3 victim) {
        Vec3 back = new Vec3(dementor.x - victim.x, 0, dementor.z - victim.z);
        back = back.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : back.normalize();
        return victim.add(back.scale(HOVER_DISTANCE)).add(0, HEM_LIFT, 0);
    }

    /** Pale wisps pulled from the victim's face toward the hood. Sent once from the server to everyone tracking. */
    private void wisps(ServerLevel sl, LivingEntity victim) {
        Vec3 from = victim.getEyePosition();
        Vec3 to = dementor.position().add(0, 2.6, 0);
        Vec3 step = to.subtract(from);
        double f = 0.15 + dementor.getRandom().nextDouble() * 0.5;
        Vec3 at = from.add(step.scale(f));
        Vec3 v = step.normalize().scale(0.08);
        sl.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 0, v.x, v.y, v.z, 1.0);
    }
}
