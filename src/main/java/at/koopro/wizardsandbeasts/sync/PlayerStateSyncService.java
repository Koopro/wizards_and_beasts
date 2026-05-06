package at.koopro.wizardsandbeasts.sync;

import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.network.FormSyncS2CPacket;
import at.koopro.wizardsandbeasts.network.HeritageDataSyncS2CPacket;
import at.koopro.wizardsandbeasts.network.SkillDataSyncS2CPacket;
import at.koopro.wizardsandbeasts.network.SpellDataSyncS2CPacket;
import at.koopro.wizardsandbeasts.network.VaultSyncS2CPacket;
import at.koopro.wizardsandbeasts.network.AbilityDataSyncPayload;
import at.koopro.wizardsandbeasts.network.ApparitionWardsSyncS2CPayload;
import at.koopro.wizardsandbeasts.skill.SkillAttributeApplicator;
import at.koopro.wizardsandbeasts.type.HeritageAPI;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerStateSyncService {
    private PlayerStateSyncService() {
    }

    public static void syncSkills(ServerPlayer player) {
        SkillDataSyncS2CPacket.syncToPlayer(player);
    }

    public static void syncSpells(ServerPlayer player) {
        SpellDataSyncS2CPacket.syncToPlayer(player);
    }

    public static void syncAbilities(ServerPlayer player) {
        AbilityDataSyncPayload.syncToPlayer(player);
        ApparitionWardsSyncS2CPayload.syncToPlayer(player);
    }

    public static void syncFullLoginState(ServerPlayer player, boolean openTypeSelector) {
        SkillAttributeApplicator.applyAll(player);
        syncSpells(player);
        syncSkills(player);
        syncAbilities(player);
        HeritageDataSyncS2CPacket.syncToPlayer(player, openTypeSelector);
        VaultSyncS2CPacket.syncToPlayer(player);
        if (HeritageAPI.hasHeritageSelected(player)) {
            HeritageAPI.applyStats(player);
        }
        FormSystemAPI.reapplyCurrentForm(player);
        FormSyncS2CPacket.syncToTracking(player);
    }
}
