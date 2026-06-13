package at.koopro.wizardsandbeasts.entity.azkaban.goal;

import at.koopro.wizardsandbeasts.azkaban.attachment.AzkabanTrespasserData;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.patronus.PatronusDetection;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

public final class DementorPursueGoal extends Goal {

    private static final double PATRONUS_R = 24.0;

    private final DementorEntity dementor;
    private @Nullable Player pursuitTarget;

    public DementorPursueGoal(DementorEntity dementor) {
        this.dementor = dementor;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ModuleManager.isEnabled(Module.AZKABAN)) return false;
        List<Player> felt = dementor.getFeltPlayers(24.0);
        if (felt.isEmpty()) return false;
        pursuitTarget = felt.get(0);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (pursuitTarget == null || !pursuitTarget.isAlive()) return false;
        if (dementor.distanceToSqr(pursuitTarget) > 24.0 * 24.0) return false;
        return !PatronusDetection.isPatronusNearby(dementor.level(), dementor.position(), PATRONUS_R);
    }

    @Override
    public void start() {
        // Tag trespasser on first felt contact
        if (pursuitTarget instanceof net.minecraft.server.level.ServerPlayer sp) {
            AzkabanTrespasserData current = sp.getData(ModAttachments.AZKABAN_TRESPASSER_TAG.get());
            if (!current.tagged()) {
                sp.setData(ModAttachments.AZKABAN_TRESPASSER_TAG.get(), current.withTagged(true));
                // Sync to client
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                        new at.koopro.wizardsandbeasts.network.azkaban.AzkabanTrespasserSyncPayload(true));
            }
        }
    }

    @Override
    public void stop() {
        pursuitTarget = null;
    }

    @Override
    public void tick() {
        if (pursuitTarget == null) return;
        dementor.getLookControl().setLookAt(pursuitTarget);
        dementor.getMoveControl().setWantedPosition(
                pursuitTarget.getX(),
                pursuitTarget.getY() + 1.0,
                pursuitTarget.getZ(),
                0.85);
    }
}
