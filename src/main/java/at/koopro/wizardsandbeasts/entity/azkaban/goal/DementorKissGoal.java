package at.koopro.wizardsandbeasts.entity.azkaban.goal;

import at.koopro.wizardsandbeasts.azkaban.AzkabanDamageTypes;
import at.koopro.wizardsandbeasts.azkaban.attachment.AzkabanTrespasserData;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.patronus.PatronusDetection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

public final class DementorKissGoal extends Goal {

    private static final int WINDUP_TICKS   = 20; // ~1 s
    private static final int RESOLVE_TICKS  = 10; // ~0.5 s
    private static final int COOLDOWN_TICKS = 300;
    private static final double KISS_REACH  = 1.5;
    private static final double PATRONUS_R  = 24.0;
    // Essentially permanent; use Integer.MAX_VALUE / 20 to stay within int range
    private static final int SOUL_DRAIN_DURATION = Integer.MAX_VALUE / 20;

    private final DementorEntity dementor;
    private @Nullable Player target;
    private int windupTimer  = 0;
    private int resolveTimer = 0;
    private boolean inWindup  = false;
    private boolean inResolve = false;

    public DementorKissGoal(DementorEntity dementor) {
        this.dementor = dementor;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (dementor.getKissCooldown() > 0) return false;
        Player candidate = findKissCandidate();
        if (candidate == null) return false;
        if (PatronusDetection.isPatronusNearby(dementor.level(), dementor.position(), PATRONUS_R)) return false;
        target = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return (inWindup || inResolve) && target != null && target.isAlive();
    }

    @Override
    public void start() {
        inWindup  = true;
        inResolve = false;
        windupTimer  = WINDUP_TICKS;
        resolveTimer = 0;
        dementor.setKissWindup(true);
    }

    @Override
    public void stop() {
        inWindup  = false;
        inResolve = false;
        target = null;
        dementor.setKissWindup(false);
        dementor.setKissResolve(false);
    }

    @Override
    public void tick() {
        if (target == null) return;

        // Cancel if Patronus appears
        if (PatronusDetection.isPatronusNearby(dementor.level(), dementor.position(), PATRONUS_R)) {
            dementor.setRepelled(true);
            stop();
            return;
        }

        if (inWindup) {
            // Drift toward target
            dementor.getMoveControl().setWantedPosition(
                    target.getX(), target.getY() + 1.0, target.getZ(), 0.45);
            if (--windupTimer <= 0) {
                if (dementor.distanceToSqr(target) <= KISS_REACH * KISS_REACH) {
                    inWindup  = false;
                    inResolve = true;
                    resolveTimer = RESOLVE_TICKS;
                    dementor.setKissWindup(false);
                    dementor.setKissResolve(true);
                } else {
                    stop();
                }
            }
        } else if (inResolve) {
            if (--resolveTimer <= 0) {
                applyKiss();
                dementor.resetKissCooldown();
                stop();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void applyKiss() {
        if (target == null || !target.isAlive()) return;
        if (!(dementor.level() instanceof ServerLevel sl)) return;

        target.addEffect(new MobEffectInstance(
                ModEffects.SOUL_DRAINED, SOUL_DRAIN_DURATION, 0, false, true, true));
        target.hurt(sl.damageSources().source(AzkabanDamageTypes.DEMENTOR_KISS), 4.0f);
    }

    private @Nullable Player findKissCandidate() {
        if (!(dementor.level() instanceof ServerLevel sl)) return null;
        List<Player> felt = dementor.getFeltPlayers(KISS_REACH);
        for (Player player : felt) {
            if (isVulnerable(sl, player)) return player;
        }
        return null;
    }

    private boolean isVulnerable(ServerLevel sl, Player player) {
        boolean lowHealth = player.getHealth() <= player.getMaxHealth() * 0.3f;
        boolean heavyChill = player.hasEffect(ModEffects.DEMENTOR_CHILL)
                && player.getEffect(ModEffects.DEMENTOR_CHILL).getAmplifier() >= 3;
        boolean sleeping = player.isSleeping();
        boolean trespasser = player.getData(ModAttachments.AZKABAN_TRESPASSER_TAG.get()).tagged();
        return lowHealth || heavyChill || sleeping || trespasser;
    }
}
