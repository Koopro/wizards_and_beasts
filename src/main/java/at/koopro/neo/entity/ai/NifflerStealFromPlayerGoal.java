package at.koopro.neo.entity.ai;

import at.koopro.neo.item.currency.CurrencyHelper;
import at.koopro.neo.entity.NifflerEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class NifflerStealFromPlayerGoal extends Goal {

    private final NifflerEntity niffler;
    private final double stealRange;
    @Nullable
    private Player targetPlayer;
    private int cooldownTicks;

    public NifflerStealFromPlayerGoal(NifflerEntity niffler, double stealRange) {
        this.niffler = niffler;
        this.stealRange = stealRange;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return false;
        }
        targetPlayer = findNearestPlayerWithCoins();
        return targetPlayer != null;
    }

    @Override
    public boolean canContinueToUse() {
        return targetPlayer != null && targetPlayer.isAlive()
                && niffler.distanceToSqr(targetPlayer) < (stealRange + 2) * (stealRange + 2);
    }

    @Override
    public void tick() {
        if (targetPlayer == null) return;

        niffler.getLookControl().setLookAt(targetPlayer, 30f, 30f);
        niffler.getNavigation().moveTo(targetPlayer, 1.0);

        if (niffler.distanceToSqr(targetPlayer) < stealRange * stealRange) {
            tryStealCoin();
            cooldownTicks = 60;
            stop();
        }
    }

    @Override
    public void stop() {
        targetPlayer = null;
        niffler.getNavigation().stop();
    }

    private void tryStealCoin() {
        if (targetPlayer == null) return;

        var inventory = targetPlayer.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && CurrencyHelper.isCoin(stack)) {
                ItemStack stolen = stack.split(1);
                niffler.storeItem(stolen);
                return;
            }
        }
    }

    @Nullable
    private Player findNearestPlayerWithCoins() {
        AABB area = niffler.getBoundingBox().inflate(stealRange + 4);
        List<Player> players = niffler.level().getEntitiesOfClass(Player.class, area,
                player -> player.isAlive() && !player.isCreative() && !player.isSpectator() && hasCoins(player));
        return players.stream()
                .min(Comparator.comparingDouble(niffler::distanceToSqr))
                .orElse(null);
    }

    private boolean hasCoins(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (CurrencyHelper.isCoin(player.getInventory().getItem(i))) return true;
        }
        return false;
    }
}
