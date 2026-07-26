package at.koopro.wizardsandbeasts.heritage.profession;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillAttributeApplicator;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import net.minecraft.server.level.ServerPlayer;

public final class ProfessionSystemAPI {

    public record UnlockCheck(boolean allowed, String reason) {}

    private ProfessionSystemAPI() {}

    public static PlayerHeritageData getData(ServerPlayer player) {
        return player.getData(ModAttachments.HERITAGE_DATA.get());
    }

    public static UnlockCheck evaluateUnlock(ServerPlayer player, ProfessionNode node) {
        PlayerHeritageData data = getData(player);
        Heritage selectedHeritage = data.getSelectedHeritage();
        if (selectedHeritage == null) {
            return new UnlockCheck(false, "type_not_selected");
        }
        if (selectedHeritage != node.getParentHeritage()) {
            return new UnlockCheck(false, "wrong_type");
        }
        if (data.hasUnlockedProfession(node.getId())) {
            return new UnlockCheck(false, "already_unlocked");
        }
        if (data.getProfessionPoints() < node.getPointCost()) {
            return new UnlockCheck(false, "not_enough_points");
        }
        for (String prereq : node.getPrerequisites()) {
            if (!data.hasUnlockedProfession(prereq)) {
                return new UnlockCheck(false, "missing_prerequisite:" + prereq);
            }
        }
        return new UnlockCheck(true, "ok");
    }

    public static boolean tryUnlock(ServerPlayer player, String professionId) {
        ProfessionNode node = ProfessionNode.byId(professionId);
        if (node == null) {
            return false;
        }
        UnlockCheck check = evaluateUnlock(player, node);
        if (!check.allowed()) {
            return false;
        }
        PlayerHeritageData data = getData(player);
        if (!data.spendProfessionPoints(node.getPointCost())) {
            return false;
        }
        data.unlockProfession(node.getId());
        if (data.getSelectedProfessionId() == null) {
            data.setSelectedProfessionId(node.getId());
        }
        SkillAttributeApplicator.applyAll(player);
        return true;
    }

    public static UnlockCheck evaluateSelect(ServerPlayer player, ProfessionNode node) {
        PlayerHeritageData data = getData(player);
        Heritage selectedHeritage = data.getSelectedHeritage();
        if (selectedHeritage == null) {
            return new UnlockCheck(false, "type_not_selected");
        }
        if (selectedHeritage != node.getParentHeritage()) {
            return new UnlockCheck(false, "wrong_type");
        }
        if (!data.hasUnlockedProfession(node.getId())) {
            return new UnlockCheck(false, "not_unlocked");
        }
        return new UnlockCheck(true, "ok");
    }

    public static boolean trySelect(ServerPlayer player, String professionId) {
        ProfessionNode node = ProfessionNode.byId(professionId);
        if (node == null) {
            return false;
        }
        UnlockCheck check = evaluateSelect(player, node);
        if (!check.allowed()) {
            return false;
        }
        getData(player).setSelectedProfessionId(node.getId());
        return true;
    }

    /**
     * Grants career progress and pushes it to the client.
     *
     * <p>Syncing here rather than at each call site is what makes this usable from gameplay: the client
     * profession screen reads its point total from {@code HeritageDataSyncS2CPayload}, so an unsynced
     * award is invisible until the next unrelated heritage sync.
     */
    public static void awardPoints(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return;
        }
        getData(player).addProfessionPoints(amount);
        at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload.syncToPlayer(player, false);
    }
}
