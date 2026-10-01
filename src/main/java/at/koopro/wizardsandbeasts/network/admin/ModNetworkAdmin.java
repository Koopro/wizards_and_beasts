package at.koopro.wizardsandbeasts.network.admin;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jspecify.annotations.NullMarked;

/**
 * Payload registration for the Control Center: five requests up, a snapshot and a result down. Registered
 * from {@code ModNetwork#register}, matching every other domain registrar.
 */
@NullMarked
public final class ModNetworkAdmin {

    private ModNetworkAdmin() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(AdminOpenRequestC2SPayload.TYPE, AdminOpenRequestC2SPayload.STREAM_CODEC,
                AdminOpenRequestC2SPayload::handle);
        registrar.playToServer(AdminSectionRequestC2SPayload.TYPE, AdminSectionRequestC2SPayload.STREAM_CODEC,
                AdminSectionRequestC2SPayload::handle);
        registrar.playToServer(AdminRefreshRequestC2SPayload.TYPE, AdminRefreshRequestC2SPayload.STREAM_CODEC,
                AdminRefreshRequestC2SPayload::handle);
        registrar.playToServer(AdminChangeSettingC2SPayload.TYPE, AdminChangeSettingC2SPayload.STREAM_CODEC,
                AdminChangeSettingC2SPayload::handle);
        registrar.playToServer(AdminResetSettingC2SPayload.TYPE, AdminResetSettingC2SPayload.STREAM_CODEC,
                AdminResetSettingC2SPayload::handle);

        registrar.playToClient(AdminSnapshotS2CPayload.TYPE, AdminSnapshotS2CPayload.STREAM_CODEC,
                AdminSnapshotS2CPayload::handleClient);
        registrar.playToClient(AdminSettingResultS2CPayload.TYPE, AdminSettingResultS2CPayload.STREAM_CODEC,
                AdminSettingResultS2CPayload::handleClient);

        // Magic section: browse, inspect, batch-reset and test spells. Edits use the change payload above.
        registrar.playToServer(AdminSpellPayloads.ListRequest.TYPE, AdminSpellPayloads.ListRequest.STREAM_CODEC,
                AdminSpellPayloads.ListRequest::handle);
        registrar.playToServer(AdminSpellPayloads.DetailRequest.TYPE, AdminSpellPayloads.DetailRequest.STREAM_CODEC,
                AdminSpellPayloads.DetailRequest::handle);
        registrar.playToServer(AdminSpellPayloads.ResetRequest.TYPE, AdminSpellPayloads.ResetRequest.STREAM_CODEC,
                AdminSpellPayloads.ResetRequest::handle);
        registrar.playToServer(AdminSpellPayloads.TestRequest.TYPE, AdminSpellPayloads.TestRequest.STREAM_CODEC,
                AdminSpellPayloads.TestRequest::handle);
        registrar.playToClient(AdminSpellPayloads.ListReply.TYPE, AdminSpellPayloads.ListReply.STREAM_CODEC,
                AdminSpellPayloads.ListReply::handleClient);
        registrar.playToClient(AdminSpellPayloads.DetailReply.TYPE, AdminSpellPayloads.DetailReply.STREAM_CODEC,
                AdminSpellPayloads.DetailReply::handleClient);
        registrar.playToClient(AdminSpellPayloads.ActionReply.TYPE, AdminSpellPayloads.ActionReply.STREAM_CODEC,
                AdminSpellPayloads.ActionReply::handleClient);

        // Heritages section: the player tools. Heritage rules are settings and use the change payload above.
        registrar.playToServer(AdminHeritagePayloads.PlayersRequest.TYPE, AdminHeritagePayloads.PlayersRequest.STREAM_CODEC,
                AdminHeritagePayloads.PlayersRequest::handle);
        registrar.playToServer(AdminHeritagePayloads.InspectRequest.TYPE, AdminHeritagePayloads.InspectRequest.STREAM_CODEC,
                AdminHeritagePayloads.InspectRequest::handle);
        registrar.playToServer(AdminHeritagePayloads.AssignRequest.TYPE, AdminHeritagePayloads.AssignRequest.STREAM_CODEC,
                AdminHeritagePayloads.AssignRequest::handle);
        registrar.playToServer(AdminHeritagePayloads.ResetOnboardingRequest.TYPE,
                AdminHeritagePayloads.ResetOnboardingRequest.STREAM_CODEC, AdminHeritagePayloads.ResetOnboardingRequest::handle);
        registrar.playToClient(AdminHeritagePayloads.PlayersReply.TYPE, AdminHeritagePayloads.PlayersReply.STREAM_CODEC,
                AdminHeritagePayloads.PlayersReply::handleClient);
        registrar.playToClient(AdminHeritagePayloads.InspectReply.TYPE, AdminHeritagePayloads.InspectReply.STREAM_CODEC,
                AdminHeritagePayloads.InspectReply::handleClient);
        registrar.playToClient(AdminHeritagePayloads.ActionReply.TYPE, AdminHeritagePayloads.ActionReply.STREAM_CODEC,
                AdminHeritagePayloads.ActionReply::handleClient);

        // Brewing section: brew list and one brew's page. Every edit uses the change payload above.
        registrar.playToServer(AdminBrewPayloads.ListRequest.TYPE, AdminBrewPayloads.ListRequest.STREAM_CODEC,
                AdminBrewPayloads.ListRequest::handle);
        registrar.playToServer(AdminBrewPayloads.DetailRequest.TYPE, AdminBrewPayloads.DetailRequest.STREAM_CODEC,
                AdminBrewPayloads.DetailRequest::handle);
        registrar.playToClient(AdminBrewPayloads.ListReply.TYPE, AdminBrewPayloads.ListReply.STREAM_CODEC,
                AdminBrewPayloads.ListReply::handleClient);
        registrar.playToClient(AdminBrewPayloads.DetailReply.TYPE, AdminBrewPayloads.DetailReply.STREAM_CODEC,
                AdminBrewPayloads.DetailReply::handleClient);

        // Creatures section: roster, one creature's page, test spawns. Rules use the change payload above.
        registrar.playToServer(AdminCreaturePayloads.ListRequest.TYPE, AdminCreaturePayloads.ListRequest.STREAM_CODEC,
                AdminCreaturePayloads.ListRequest::handle);
        registrar.playToServer(AdminCreaturePayloads.DetailRequest.TYPE, AdminCreaturePayloads.DetailRequest.STREAM_CODEC,
                AdminCreaturePayloads.DetailRequest::handle);
        registrar.playToServer(AdminCreaturePayloads.SpawnRequest.TYPE, AdminCreaturePayloads.SpawnRequest.STREAM_CODEC,
                AdminCreaturePayloads.SpawnRequest::handle);
        registrar.playToServer(AdminCreaturePayloads.CleanupRequest.TYPE, AdminCreaturePayloads.CleanupRequest.STREAM_CODEC,
                AdminCreaturePayloads.CleanupRequest::handle);
        registrar.playToClient(AdminCreaturePayloads.ListReply.TYPE, AdminCreaturePayloads.ListReply.STREAM_CODEC,
                AdminCreaturePayloads.ListReply::handleClient);
        registrar.playToClient(AdminCreaturePayloads.DetailReply.TYPE, AdminCreaturePayloads.DetailReply.STREAM_CODEC,
                AdminCreaturePayloads.DetailReply::handleClient);
        registrar.playToClient(AdminCreaturePayloads.ActionReply.TYPE, AdminCreaturePayloads.ActionReply.STREAM_CODEC,
                AdminCreaturePayloads.ActionReply::handleClient);

        // Wands section: catalog, generator preview, test wand. Withdrawals use the change payload above.
        registrar.playToServer(AdminWandPayloads.CatalogRequest.TYPE, AdminWandPayloads.CatalogRequest.STREAM_CODEC,
                AdminWandPayloads.CatalogRequest::handle);
        registrar.playToServer(AdminWandPayloads.PreviewRequest.TYPE, AdminWandPayloads.PreviewRequest.STREAM_CODEC,
                AdminWandPayloads.PreviewRequest::handle);
        registrar.playToServer(AdminWandPayloads.GiveRequest.TYPE, AdminWandPayloads.GiveRequest.STREAM_CODEC,
                AdminWandPayloads.GiveRequest::handle);
        registrar.playToClient(AdminWandPayloads.CatalogReply.TYPE, AdminWandPayloads.CatalogReply.STREAM_CODEC,
                AdminWandPayloads.CatalogReply::handleClient);
        registrar.playToClient(AdminWandPayloads.PreviewReply.TYPE, AdminWandPayloads.PreviewReply.STREAM_CODEC,
                AdminWandPayloads.PreviewReply::handleClient);
        registrar.playToClient(AdminWandPayloads.ActionReply.TYPE, AdminWandPayloads.ActionReply.STREAM_CODEC,
                AdminWandPayloads.ActionReply::handleClient);

        // Travel section: broom roster. Every broom value uses the change payload above.
        registrar.playToServer(AdminBroomPayloads.ListRequest.TYPE, AdminBroomPayloads.ListRequest.STREAM_CODEC,
                AdminBroomPayloads.ListRequest::handle);
        registrar.playToClient(AdminBroomPayloads.ListReply.TYPE, AdminBroomPayloads.ListReply.STREAM_CODEC,
                AdminBroomPayloads.ListReply::handleClient);

        // Visuals section: beam looks and the preset library. Every look value uses the change payload above.
        registrar.playToServer(AdminVisualPayloads.ListRequest.TYPE, AdminVisualPayloads.ListRequest.STREAM_CODEC,
                AdminVisualPayloads.ListRequest::handle);
        registrar.playToServer(AdminVisualPayloads.PresetRequest.TYPE, AdminVisualPayloads.PresetRequest.STREAM_CODEC,
                AdminVisualPayloads.PresetRequest::handle);
        registrar.playToClient(AdminVisualPayloads.ListReply.TYPE, AdminVisualPayloads.ListReply.STREAM_CODEC,
                AdminVisualPayloads.ListReply::handleClient);
        registrar.playToClient(AdminVisualPayloads.ActionReply.TYPE, AdminVisualPayloads.ActionReply.STREAM_CODEC,
                AdminVisualPayloads.ActionReply::handleClient);

        // Performance and Debug sections: metrics, presets, live diagnostics and leased debug tools.
        registrar.playToServer(AdminOpsPayloads.MetricsRequest.TYPE, AdminOpsPayloads.MetricsRequest.STREAM_CODEC,
                AdminOpsPayloads.MetricsRequest::handle);
        registrar.playToServer(AdminOpsPayloads.PresetRequest.TYPE, AdminOpsPayloads.PresetRequest.STREAM_CODEC,
                AdminOpsPayloads.PresetRequest::handle);
        registrar.playToServer(AdminOpsPayloads.DiagnosticsRequest.TYPE, AdminOpsPayloads.DiagnosticsRequest.STREAM_CODEC,
                AdminOpsPayloads.DiagnosticsRequest::handle);
        registrar.playToServer(AdminOpsPayloads.ToggleRequest.TYPE, AdminOpsPayloads.ToggleRequest.STREAM_CODEC,
                AdminOpsPayloads.ToggleRequest::handle);
        registrar.playToServer(AdminOpsPayloads.LeaseRequest.TYPE, AdminOpsPayloads.LeaseRequest.STREAM_CODEC,
                AdminOpsPayloads.LeaseRequest::handle);
        registrar.playToClient(AdminOpsPayloads.MetricsReply.TYPE, AdminOpsPayloads.MetricsReply.STREAM_CODEC,
                AdminOpsPayloads.MetricsReply::handleClient);
        registrar.playToClient(AdminOpsPayloads.PresetReply.TYPE, AdminOpsPayloads.PresetReply.STREAM_CODEC,
                AdminOpsPayloads.PresetReply::handleClient);
        registrar.playToClient(AdminOpsPayloads.DiagnosticsReply.TYPE, AdminOpsPayloads.DiagnosticsReply.STREAM_CODEC,
                AdminOpsPayloads.DiagnosticsReply::handleClient);
        registrar.playToClient(AdminOpsPayloads.ToolReply.TYPE, AdminOpsPayloads.ToolReply.STREAM_CODEC,
                AdminOpsPayloads.ToolReply::handleClient);

        // Players section: search, one facet of one player, one action, the action log, all through PlayerAdminService.
        registrar.playToServer(AdminPlayerPayloads.SearchRequest.TYPE, AdminPlayerPayloads.SearchRequest.STREAM_CODEC,
                AdminPlayerPayloads.SearchRequest::handle);
        registrar.playToServer(AdminPlayerPayloads.FacetRequest.TYPE, AdminPlayerPayloads.FacetRequest.STREAM_CODEC,
                AdminPlayerPayloads.FacetRequest::handle);
        registrar.playToServer(AdminPlayerPayloads.ActionRequest.TYPE, AdminPlayerPayloads.ActionRequest.STREAM_CODEC,
                AdminPlayerPayloads.ActionRequest::handle);
        registrar.playToServer(AdminPlayerPayloads.LogRequest.TYPE, AdminPlayerPayloads.LogRequest.STREAM_CODEC,
                AdminPlayerPayloads.LogRequest::handle);
        registrar.playToClient(AdminPlayerPayloads.SearchReply.TYPE, AdminPlayerPayloads.SearchReply.STREAM_CODEC,
                AdminPlayerPayloads.SearchReply::handleClient);
        registrar.playToClient(AdminPlayerPayloads.FacetReply.TYPE, AdminPlayerPayloads.FacetReply.STREAM_CODEC,
                AdminPlayerPayloads.FacetReply::handleClient);
        registrar.playToClient(AdminPlayerPayloads.ActionReply.TYPE, AdminPlayerPayloads.ActionReply.STREAM_CODEC,
                AdminPlayerPayloads.ActionReply::handleClient);
        registrar.playToClient(AdminPlayerPayloads.LogReply.TYPE, AdminPlayerPayloads.LogReply.STREAM_CODEC,
                AdminPlayerPayloads.LogReply::handleClient);

        // Profiles section: library, preview, history and every profile action, all through ProfileService.
        registrar.playToServer(AdminProfilePayloads.ListRequest.TYPE, AdminProfilePayloads.ListRequest.STREAM_CODEC,
                AdminProfilePayloads.ListRequest::handle);
        registrar.playToServer(AdminProfilePayloads.PreviewRequest.TYPE, AdminProfilePayloads.PreviewRequest.STREAM_CODEC,
                AdminProfilePayloads.PreviewRequest::handle);
        registrar.playToServer(AdminProfilePayloads.ActionRequest.TYPE, AdminProfilePayloads.ActionRequest.STREAM_CODEC,
                AdminProfilePayloads.ActionRequest::handle);
        registrar.playToClient(AdminProfilePayloads.ListReply.TYPE, AdminProfilePayloads.ListReply.STREAM_CODEC,
                AdminProfilePayloads.ListReply::handleClient);
        registrar.playToClient(AdminProfilePayloads.PreviewReply.TYPE, AdminProfilePayloads.PreviewReply.STREAM_CODEC,
                AdminProfilePayloads.PreviewReply::handleClient);
        registrar.playToClient(AdminProfilePayloads.ActionReply.TYPE, AdminProfilePayloads.ActionReply.STREAM_CODEC,
                AdminProfilePayloads.ActionReply::handleClient);
    }
}
