package at.koopro.wizardsandbeasts.network.admin;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
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
        toServer(registrar, AdminOpenRequestC2SPayload.TYPE, AdminOpenRequestC2SPayload.STREAM_CODEC,
                AdminOpenRequestC2SPayload::handle);
        toServer(registrar, AdminSectionRequestC2SPayload.TYPE, AdminSectionRequestC2SPayload.STREAM_CODEC,
                AdminSectionRequestC2SPayload::handle);
        toServer(registrar, AdminRefreshRequestC2SPayload.TYPE, AdminRefreshRequestC2SPayload.STREAM_CODEC,
                AdminRefreshRequestC2SPayload::handle);
        toServer(registrar, AdminChangeSettingC2SPayload.TYPE, AdminChangeSettingC2SPayload.STREAM_CODEC,
                AdminChangeSettingC2SPayload::handle);
        toServer(registrar, AdminResetSettingC2SPayload.TYPE, AdminResetSettingC2SPayload.STREAM_CODEC,
                AdminResetSettingC2SPayload::handle);

        registrar.playToClient(AdminSnapshotS2CPayload.TYPE, AdminSnapshotS2CPayload.STREAM_CODEC,
                AdminSnapshotS2CPayload::handleClient);
        registrar.playToClient(AdminSettingResultS2CPayload.TYPE, AdminSettingResultS2CPayload.STREAM_CODEC,
                AdminSettingResultS2CPayload::handleClient);

        // Magic section: browse, inspect, batch-reset and test spells. Edits use the change payload above.
        toServer(registrar, AdminSpellPayloads.ListRequest.TYPE, AdminSpellPayloads.ListRequest.STREAM_CODEC,
                AdminSpellPayloads.ListRequest::handle);
        toServer(registrar, AdminSpellPayloads.DetailRequest.TYPE, AdminSpellPayloads.DetailRequest.STREAM_CODEC,
                AdminSpellPayloads.DetailRequest::handle);
        toServer(registrar, AdminSpellPayloads.ResetRequest.TYPE, AdminSpellPayloads.ResetRequest.STREAM_CODEC,
                AdminSpellPayloads.ResetRequest::handle);
        toServer(registrar, AdminSpellPayloads.TestRequest.TYPE, AdminSpellPayloads.TestRequest.STREAM_CODEC,
                AdminSpellPayloads.TestRequest::handle);
        registrar.playToClient(AdminSpellPayloads.ListReply.TYPE, AdminSpellPayloads.ListReply.STREAM_CODEC,
                AdminSpellPayloads.ListReply::handleClient);
        registrar.playToClient(AdminSpellPayloads.DetailReply.TYPE, AdminSpellPayloads.DetailReply.STREAM_CODEC,
                AdminSpellPayloads.DetailReply::handleClient);
        registrar.playToClient(AdminSpellPayloads.ActionReply.TYPE, AdminSpellPayloads.ActionReply.STREAM_CODEC,
                AdminSpellPayloads.ActionReply::handleClient);

        // Heritages section: the player tools. Heritage rules are settings and use the change payload above.
        toServer(registrar, AdminHeritagePayloads.PlayersRequest.TYPE, AdminHeritagePayloads.PlayersRequest.STREAM_CODEC,
                AdminHeritagePayloads.PlayersRequest::handle);
        toServer(registrar, AdminHeritagePayloads.InspectRequest.TYPE, AdminHeritagePayloads.InspectRequest.STREAM_CODEC,
                AdminHeritagePayloads.InspectRequest::handle);
        toServer(registrar, AdminHeritagePayloads.AssignRequest.TYPE, AdminHeritagePayloads.AssignRequest.STREAM_CODEC,
                AdminHeritagePayloads.AssignRequest::handle);
        toServer(registrar, AdminHeritagePayloads.ResetOnboardingRequest.TYPE,
                AdminHeritagePayloads.ResetOnboardingRequest.STREAM_CODEC, AdminHeritagePayloads.ResetOnboardingRequest::handle);
        registrar.playToClient(AdminHeritagePayloads.PlayersReply.TYPE, AdminHeritagePayloads.PlayersReply.STREAM_CODEC,
                AdminHeritagePayloads.PlayersReply::handleClient);
        registrar.playToClient(AdminHeritagePayloads.InspectReply.TYPE, AdminHeritagePayloads.InspectReply.STREAM_CODEC,
                AdminHeritagePayloads.InspectReply::handleClient);
        registrar.playToClient(AdminHeritagePayloads.ActionReply.TYPE, AdminHeritagePayloads.ActionReply.STREAM_CODEC,
                AdminHeritagePayloads.ActionReply::handleClient);

        // Brewing section: brew list and one brew's page. Every edit uses the change payload above.
        toServer(registrar, AdminBrewPayloads.ListRequest.TYPE, AdminBrewPayloads.ListRequest.STREAM_CODEC,
                AdminBrewPayloads.ListRequest::handle);
        toServer(registrar, AdminBrewPayloads.DetailRequest.TYPE, AdminBrewPayloads.DetailRequest.STREAM_CODEC,
                AdminBrewPayloads.DetailRequest::handle);
        registrar.playToClient(AdminBrewPayloads.ListReply.TYPE, AdminBrewPayloads.ListReply.STREAM_CODEC,
                AdminBrewPayloads.ListReply::handleClient);
        registrar.playToClient(AdminBrewPayloads.DetailReply.TYPE, AdminBrewPayloads.DetailReply.STREAM_CODEC,
                AdminBrewPayloads.DetailReply::handleClient);

        // Creatures section: roster, one creature's page, test spawns. Rules use the change payload above.
        toServer(registrar, AdminCreaturePayloads.ListRequest.TYPE, AdminCreaturePayloads.ListRequest.STREAM_CODEC,
                AdminCreaturePayloads.ListRequest::handle);
        toServer(registrar, AdminCreaturePayloads.DetailRequest.TYPE, AdminCreaturePayloads.DetailRequest.STREAM_CODEC,
                AdminCreaturePayloads.DetailRequest::handle);
        toServer(registrar, AdminCreaturePayloads.SpawnRequest.TYPE, AdminCreaturePayloads.SpawnRequest.STREAM_CODEC,
                AdminCreaturePayloads.SpawnRequest::handle);
        toServer(registrar, AdminCreaturePayloads.CleanupRequest.TYPE, AdminCreaturePayloads.CleanupRequest.STREAM_CODEC,
                AdminCreaturePayloads.CleanupRequest::handle);
        registrar.playToClient(AdminCreaturePayloads.ListReply.TYPE, AdminCreaturePayloads.ListReply.STREAM_CODEC,
                AdminCreaturePayloads.ListReply::handleClient);
        registrar.playToClient(AdminCreaturePayloads.DetailReply.TYPE, AdminCreaturePayloads.DetailReply.STREAM_CODEC,
                AdminCreaturePayloads.DetailReply::handleClient);
        registrar.playToClient(AdminCreaturePayloads.ActionReply.TYPE, AdminCreaturePayloads.ActionReply.STREAM_CODEC,
                AdminCreaturePayloads.ActionReply::handleClient);

        // Wands section: catalog, generator preview, test wand. Withdrawals use the change payload above.
        toServer(registrar, AdminWandPayloads.CatalogRequest.TYPE, AdminWandPayloads.CatalogRequest.STREAM_CODEC,
                AdminWandPayloads.CatalogRequest::handle);
        toServer(registrar, AdminWandPayloads.PreviewRequest.TYPE, AdminWandPayloads.PreviewRequest.STREAM_CODEC,
                AdminWandPayloads.PreviewRequest::handle);
        toServer(registrar, AdminWandPayloads.GiveRequest.TYPE, AdminWandPayloads.GiveRequest.STREAM_CODEC,
                AdminWandPayloads.GiveRequest::handle);
        registrar.playToClient(AdminWandPayloads.CatalogReply.TYPE, AdminWandPayloads.CatalogReply.STREAM_CODEC,
                AdminWandPayloads.CatalogReply::handleClient);
        registrar.playToClient(AdminWandPayloads.PreviewReply.TYPE, AdminWandPayloads.PreviewReply.STREAM_CODEC,
                AdminWandPayloads.PreviewReply::handleClient);
        registrar.playToClient(AdminWandPayloads.ActionReply.TYPE, AdminWandPayloads.ActionReply.STREAM_CODEC,
                AdminWandPayloads.ActionReply::handleClient);

        // Travel section: broom roster. Every broom value uses the change payload above.
        toServer(registrar, AdminBroomPayloads.ListRequest.TYPE, AdminBroomPayloads.ListRequest.STREAM_CODEC,
                AdminBroomPayloads.ListRequest::handle);
        registrar.playToClient(AdminBroomPayloads.ListReply.TYPE, AdminBroomPayloads.ListReply.STREAM_CODEC,
                AdminBroomPayloads.ListReply::handleClient);

        // Visuals section: beam looks and the preset library. Every look value uses the change payload above.
        toServer(registrar, AdminVisualPayloads.ListRequest.TYPE, AdminVisualPayloads.ListRequest.STREAM_CODEC,
                AdminVisualPayloads.ListRequest::handle);
        toServer(registrar, AdminVisualPayloads.PresetRequest.TYPE, AdminVisualPayloads.PresetRequest.STREAM_CODEC,
                AdminVisualPayloads.PresetRequest::handle);
        registrar.playToClient(AdminVisualPayloads.ListReply.TYPE, AdminVisualPayloads.ListReply.STREAM_CODEC,
                AdminVisualPayloads.ListReply::handleClient);
        registrar.playToClient(AdminVisualPayloads.ActionReply.TYPE, AdminVisualPayloads.ActionReply.STREAM_CODEC,
                AdminVisualPayloads.ActionReply::handleClient);

        // Economy section: the ledger and every trader's book, read-only (AdminEconomyPayloads).
        toServer(registrar, AdminEconomyPayloads.Request.TYPE, AdminEconomyPayloads.Request.STREAM_CODEC,
                AdminEconomyPayloads.Request::handle);
        registrar.playToClient(AdminEconomyPayloads.Reply.TYPE, AdminEconomyPayloads.Reply.STREAM_CODEC,
                AdminEconomyPayloads.Reply::handleClient);

        // Ministry section: every case, unsolved incident and posted case with its evidence (AdminMinistryPayloads).
        toServer(registrar, AdminMinistryPayloads.Request.TYPE, AdminMinistryPayloads.Request.STREAM_CODEC,
                AdminMinistryPayloads.Request::handle);
        registrar.playToClient(AdminMinistryPayloads.Reply.TYPE, AdminMinistryPayloads.Reply.STREAM_CODEC,
                AdminMinistryPayloads.Reply::handleClient);

        // Performance and Debug sections: metrics, presets, live diagnostics and leased debug tools.
        toServer(registrar, AdminOpsPayloads.MetricsRequest.TYPE, AdminOpsPayloads.MetricsRequest.STREAM_CODEC,
                AdminOpsPayloads.MetricsRequest::handle);
        toServer(registrar, AdminOpsPayloads.PresetRequest.TYPE, AdminOpsPayloads.PresetRequest.STREAM_CODEC,
                AdminOpsPayloads.PresetRequest::handle);
        toServer(registrar, AdminOpsPayloads.DiagnosticsRequest.TYPE, AdminOpsPayloads.DiagnosticsRequest.STREAM_CODEC,
                AdminOpsPayloads.DiagnosticsRequest::handle);
        toServer(registrar, AdminOpsPayloads.ToggleRequest.TYPE, AdminOpsPayloads.ToggleRequest.STREAM_CODEC,
                AdminOpsPayloads.ToggleRequest::handle);
        toServer(registrar, AdminOpsPayloads.LeaseRequest.TYPE, AdminOpsPayloads.LeaseRequest.STREAM_CODEC,
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
        toServer(registrar, AdminPlayerPayloads.SearchRequest.TYPE, AdminPlayerPayloads.SearchRequest.STREAM_CODEC,
                AdminPlayerPayloads.SearchRequest::handle);
        toServer(registrar, AdminPlayerPayloads.FacetRequest.TYPE, AdminPlayerPayloads.FacetRequest.STREAM_CODEC,
                AdminPlayerPayloads.FacetRequest::handle);
        toServer(registrar, AdminPlayerPayloads.ActionRequest.TYPE, AdminPlayerPayloads.ActionRequest.STREAM_CODEC,
                AdminPlayerPayloads.ActionRequest::handle);
        toServer(registrar, AdminPlayerPayloads.LogRequest.TYPE, AdminPlayerPayloads.LogRequest.STREAM_CODEC,
                AdminPlayerPayloads.LogRequest::handle);
        registrar.playToClient(AdminPlayerPayloads.SearchReply.TYPE, AdminPlayerPayloads.SearchReply.STREAM_CODEC,
                AdminPlayerPayloads.SearchReply::handleClient);
        registrar.playToClient(AdminPlayerPayloads.FacetReply.TYPE, AdminPlayerPayloads.FacetReply.STREAM_CODEC,
                AdminPlayerPayloads.FacetReply::handleClient);
        registrar.playToClient(AdminPlayerPayloads.ActionReply.TYPE, AdminPlayerPayloads.ActionReply.STREAM_CODEC,
                AdminPlayerPayloads.ActionReply::handleClient);
        registrar.playToClient(AdminPlayerPayloads.LogReply.TYPE, AdminPlayerPayloads.LogReply.STREAM_CODEC,
                AdminPlayerPayloads.LogReply::handleClient);

        // Reports and operations: every read-only report page and one-shot operation, whatever its section
        // (AdminReportService / AdminOperationService). A new report or operation adds no payload.
        toServer(registrar, AdminReportPayloads.ReportRequest.TYPE, AdminReportPayloads.ReportRequest.STREAM_CODEC,
                AdminReportPayloads.ReportRequest::handle);
        toServer(registrar, AdminReportPayloads.OperationRequest.TYPE, AdminReportPayloads.OperationRequest.STREAM_CODEC,
                AdminReportPayloads.OperationRequest::handle);
        registrar.playToClient(AdminReportPayloads.ReportReply.TYPE, AdminReportPayloads.ReportReply.STREAM_CODEC,
                AdminReportPayloads.ReportReply::handleClient);
        registrar.playToClient(AdminReportPayloads.OperationReply.TYPE, AdminReportPayloads.OperationReply.STREAM_CODEC,
                AdminReportPayloads.OperationReply::handleClient);

        // Profiles section: library, preview, history and every profile action, all through ProfileService.
        toServer(registrar, AdminProfilePayloads.ListRequest.TYPE, AdminProfilePayloads.ListRequest.STREAM_CODEC,
                AdminProfilePayloads.ListRequest::handle);
        toServer(registrar, AdminProfilePayloads.PreviewRequest.TYPE, AdminProfilePayloads.PreviewRequest.STREAM_CODEC,
                AdminProfilePayloads.PreviewRequest::handle);
        toServer(registrar, AdminProfilePayloads.ActionRequest.TYPE, AdminProfilePayloads.ActionRequest.STREAM_CODEC,
                AdminProfilePayloads.ActionRequest::handle);
        registrar.playToClient(AdminProfilePayloads.ListReply.TYPE, AdminProfilePayloads.ListReply.STREAM_CODEC,
                AdminProfilePayloads.ListReply::handleClient);
        registrar.playToClient(AdminProfilePayloads.PreviewReply.TYPE, AdminProfilePayloads.PreviewReply.STREAM_CODEC,
                AdminProfilePayloads.PreviewReply::handleClient);
        registrar.playToClient(AdminProfilePayloads.ActionReply.TYPE, AdminProfilePayloads.ActionReply.STREAM_CODEC,
                AdminProfilePayloads.ActionReply::handleClient);
    }

    /**
     * Every request up goes through {@link AdminRequestLimiter} before its handler: one place, so a request type
     * added later cannot be registered without it.
     */
    private static <T extends CustomPacketPayload> void toServer(PayloadRegistrar registrar, CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                                 IPayloadHandler<T> handler) {
        registrar.playToServer(type, codec, (payload, context) -> {
            if (AdminRequestLimiter.admit(context.player())) {
                handler.handle(payload, context);
            }
        });
    }
}
