package at.koopro.wizardsandbeasts.type.profession;

import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.type.WizType;
import net.minecraft.server.level.ServerPlayer;

public final class ProfessionSystemAPI {

    public record UnlockCheck(boolean allowed, String reason) {}

    private ProfessionSystemAPI() {}

    public static PlayerTypeData getData(ServerPlayer player) {
        return player.getData(ModAttachments.TYPE_DATA.get());
    }

    public static UnlockCheck evaluateUnlock(ServerPlayer player, ProfessionNode node) {
        PlayerTypeData data = getData(player);
        WizType selectedType = data.getSelectedType();
        if (selectedType == null) {
            return new UnlockCheck(false, "type_not_selected");
        }
        if (selectedType != node.getParentType()) {
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
        PlayerTypeData data = getData(player);
        if (!data.spendProfessionPoints(node.getPointCost())) {
            return false;
        }
        data.unlockProfession(node.getId());
        if (data.getSelectedProfessionId() == null) {
            data.setSelectedProfessionId(node.getId());
        }
        return true;
    }

    public static UnlockCheck evaluateSelect(ServerPlayer player, ProfessionNode node) {
        PlayerTypeData data = getData(player);
        WizType selectedType = data.getSelectedType();
        if (selectedType == null) {
            return new UnlockCheck(false, "type_not_selected");
        }
        if (selectedType != node.getParentType()) {
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

    public static void awardPoints(ServerPlayer player, int amount) {
        getData(player).addProfessionPoints(amount);
    }
}
