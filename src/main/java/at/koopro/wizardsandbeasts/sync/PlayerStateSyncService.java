package at.koopro.wizardsandbeasts.sync;

import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.network.form.FormSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.VocationDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.currency.VaultSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.ability.AbilityDataSyncPayload;
import at.koopro.wizardsandbeasts.network.apparition.ApparitionWardsSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.azkaban.AzkabanTrespasserSyncPayload;
import at.koopro.wizardsandbeasts.network.bestiary.niffler.NifflerCarrySyncS2CPayload;
import at.koopro.wizardsandbeasts.skill.SkillAttributeApplicator;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerStateSyncService {
    private PlayerStateSyncService() {
    }

    public static void syncSkills(ServerPlayer player) {
        SkillDataSyncS2CPayload.syncToPlayer(player);
    }

    public static void syncVocations(ServerPlayer player) {
        VocationDataSyncS2CPayload.syncToPlayer(player);
    }

    public static void syncSpells(ServerPlayer player) {
        SpellDataSyncS2CPayload.syncToPlayer(player);
    }

    public static void syncAbilities(ServerPlayer player) {
        AbilityDataSyncPayload.syncToPlayer(player);
        ApparitionWardsSyncS2CPayload.syncToPlayer(player);
    }

    public static void syncFullLoginState(ServerPlayer player, boolean openTypeSelector) {
        SkillAttributeApplicator.applyAll(player);
        syncSpells(player);
        syncSkills(player);
        syncVocations(player);
        syncAbilities(player);
        HeritageDataSyncS2CPayload.syncToPlayer(player, openTypeSelector);
        VaultSyncS2CPayload.syncToPlayer(player);
        AzkabanTrespasserSyncPayload.syncToPlayer(player);
        NifflerCarrySyncS2CPayload.sendToPlayer(player);
        if (HeritageAPI.hasHeritageSelected(player)) {
            HeritageAPI.applyStats(player);
        }
        FormSystemAPI.reapplyCurrentForm(player);
        FormSyncS2CPayload.syncToTracking(player);
    }
}
