package at.koopro.wizardsandbeasts.sync;

import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.network.form.FormSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.heritage.BloodDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.AbilityGrantsSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.VocationDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.currency.VaultSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.ministry.MinistryRecordSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.standing.StandingSyncS2CPayload;
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

    /** Pushes the derived source-tracked ability-grant snapshot (heritage/vocation/skill-node). */
    public static void syncAbilityGrants(ServerPlayer player) {
        AbilityGrantsSyncS2CPayload.syncToPlayer(player);
    }

    public static void syncFullLoginState(ServerPlayer player, boolean openTypeSelector) {
        SkillAttributeApplicator.applyAll(player);
        syncSpells(player);
        syncSkills(player);
        syncVocations(player);
        syncAbilities(player);
        syncAbilityGrants(player); // derived from heritage+vocation+skills, all synced above; relog-safe

        HeritageDataSyncS2CPayload.syncToPlayer(player, openTypeSelector);
        // After the heritage: the blood payload carries the nutrition policy, which is derived from the
        // variant the line above just sent. Sending it first would have told a client to draw a blood
        // meter for a heritage it did not yet know it had.
        BloodDataSyncS2CPayload.syncToPlayer(player);
        VaultSyncS2CPayload.syncToPlayer(player);
        MinistryRecordSyncS2CPayload.syncToPlayer(player);
        // After the Ministry record and the vault: two of the three standing axes are composed from
        // state synced above, and sending standing first would put a correct number next to a stale one.
        StandingSyncS2CPayload.syncToPlayer(player);
        AzkabanTrespasserSyncPayload.syncToPlayer(player);
        NifflerCarrySyncS2CPayload.sendToPlayer(player);
        if (HeritageAPI.hasHeritageSelected(player)) {
            HeritageAPI.applyStats(player);
        }
        FormSystemAPI.reapplyCurrentForm(player);
        FormSyncS2CPayload.syncToTracking(player);
    }
}
