package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.network.admin.AdminChangeSettingC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminOpenRequestC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminRefreshRequestC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminResetSettingC2SPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminSectionRequestC2SPayload;
import at.koopro.wizardsandbeasts.admin.spell.SpellTestService;
import at.koopro.wizardsandbeasts.network.admin.AdminBrewPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminBroomPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminWandPayloads;
import at.koopro.wizardsandbeasts.admin.wand.WandAdminService;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminHeritagePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellPayloads;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NullMarked;

/**
 * Everything the Control Center may ask of the server. Requests only — the answer always comes back as a
 * packet, and the UI shows that answer, never its own guess.
 */
@NullMarked
public final class AdminClientRequests {

    private AdminClientRequests() {}

    public static void open() {
        ClientPacketDistributor.sendToServer(AdminOpenRequestC2SPayload.INSTANCE);
    }

    public static void refresh() {
        ClientPacketDistributor.sendToServer(AdminRefreshRequestC2SPayload.INSTANCE);
    }

    public static void section(AdminCategory section) {
        ClientPacketDistributor.sendToServer(new AdminSectionRequestC2SPayload(section.id()));
    }

    /**
     * Asks for a change. Sent unconfirmed first: when the server judges it dangerous it answers
     * CONFIRMATION_REQUIRED with the warning, and the screen resends it with {@code confirmed} after the
     * administrator has read that warning. The client never decides what is dangerous.
     */
    public static void change(Identifier settingId, String value, boolean confirmed) {
        int requestId = ClientAdminState.track(settingId, value);
        ClientPacketDistributor.sendToServer(new AdminChangeSettingC2SPayload(requestId, settingId, value, confirmed));
    }

    public static void reset(Identifier settingId, boolean confirmed) {
        int requestId = ClientAdminState.track(settingId, null);
        ClientPacketDistributor.sendToServer(new AdminResetSettingC2SPayload(requestId, settingId, confirmed));
    }

    // ── magic section ──

    public static void spellList() {
        ClientAdminState.markSpellListRequested();
        ClientPacketDistributor.sendToServer(AdminSpellPayloads.ListRequest.INSTANCE);
    }

    public static void spellDetail(String spellId) {
        ClientPacketDistributor.sendToServer(new AdminSpellPayloads.DetailRequest(spellId));
    }

    public static void resetSpell(String spellId, boolean confirmed) {
        ClientPacketDistributor.sendToServer(new AdminSpellPayloads.ResetRequest(
                AdminSpellPayloads.ResetScope.SPELL, spellId, confirmed));
    }

    public static void resetSpellCategory(String category, boolean confirmed) {
        ClientPacketDistributor.sendToServer(new AdminSpellPayloads.ResetRequest(
                AdminSpellPayloads.ResetScope.CATEGORY, category, confirmed));
    }

    /** Names a kind of target, never an entity: the server picks the entity and checks it. */
    public static void brewList() {
        ClientAdminBrewState.markListRequested();
        ClientPacketDistributor.sendToServer(AdminBrewPayloads.ListRequest.INSTANCE);
    }

    public static void brewDetail(String brewId) {
        ClientPacketDistributor.sendToServer(new AdminBrewPayloads.DetailRequest(brewId));
    }

    public static void wandCatalog() {
        ClientAdminWandState.markRequested();
        ClientPacketDistributor.sendToServer(AdminWandPayloads.CatalogRequest.INSTANCE);
    }

    /** Names ids and numbers only; the server checks them and resolves the wand itself. */
    public static void wandPreview(WandAdminService.Request request) {
        ClientPacketDistributor.sendToServer(new AdminWandPayloads.PreviewRequest(request));
    }

    public static void giveTestWand(WandAdminService.Request request) {
        ClientPacketDistributor.sendToServer(new AdminWandPayloads.GiveRequest(request));
    }

    public static void broomList() {
        ClientAdminBroomState.markRequested();
        ClientPacketDistributor.sendToServer(AdminBroomPayloads.ListRequest.INSTANCE);
    }

    public static void beamList() {
        ClientAdminVisualState.markRequested();
        ClientPacketDistributor.sendToServer(at.koopro.wizardsandbeasts.network.admin.AdminVisualPayloads.ListRequest.INSTANCE);
    }

    /** A preset operation; the server validates everything and answers with the outcome and a fresh listing. */
    public static void beamPreset(at.koopro.wizardsandbeasts.admin.visual.BeamVisualAdminService.PresetOp op,
                                  String presetId, String name, java.util.Map<String, String> values) {
        ClientPacketDistributor.sendToServer(new at.koopro.wizardsandbeasts.network.admin.AdminVisualPayloads.PresetRequest(
                op, presetId, name, values));
    }

    public static void profileList() {
        ClientAdminProfileState.markRequested();
        ClientPacketDistributor.sendToServer(at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.ListRequest.INSTANCE);
    }

    public static void profilePreview(String profileId) {
        ClientPacketDistributor.sendToServer(new at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.PreviewRequest(profileId));
    }

    /**
     * A profile action. Apply, delete and the reverts are sent only after the administrator confirmed in a dialog;
     * the server refuses them unconfirmed and answers every action with its outcome and a fresh listing.
     */
    public static void profileAction(at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.Op op, String target,
                                     String name) {
        ClientPacketDistributor.sendToServer(new at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.ActionRequest(
                op, target, name, op.needsConfirmation()));
    }

    public static void playerSearch() {
        ClientAdminPlayerState.markRequested();
        ClientPacketDistributor.sendToServer(new at.koopro.wizardsandbeasts.network.admin.AdminPlayerPayloads.SearchRequest(""));
    }

    public static void playerFacet(UUID player, at.koopro.wizardsandbeasts.admin.player.PlayerAdminService.Facet facet) {
        ClientPacketDistributor.sendToServer(new at.koopro.wizardsandbeasts.network.admin.AdminPlayerPayloads.FacetRequest(player, facet));
    }

    public static void playerLog(UUID player) {
        ClientPacketDistributor.sendToServer(new at.koopro.wizardsandbeasts.network.admin.AdminPlayerPayloads.LogRequest(player));
    }

    /**
     * One player action. A destructive one is sent only after the administrator confirmed in a dialog; the server
     * checks authority, target, argument and confirmation itself and answers with the outcome.
     */
    public static void playerAction(UUID player, at.koopro.wizardsandbeasts.admin.player.PlayerAdminAction action,
                                    String argument, long amount) {
        ClientPacketDistributor.sendToServer(new at.koopro.wizardsandbeasts.network.admin.AdminPlayerPayloads.ActionRequest(player, action, argument, amount,
                action.destructive()));
    }

    public static void creatureList() {
        ClientAdminCreatureState.markListRequested();
        ClientPacketDistributor.sendToServer(AdminCreaturePayloads.ListRequest.INSTANCE);
    }

    public static void creatureDetail(String creatureId) {
        ClientPacketDistributor.sendToServer(new AdminCreaturePayloads.DetailRequest(creatureId));
    }

    /** The server chooses where; the client names only the creature and, optionally, a variant. */
    public static void spawnTestCreature(String creatureId, String variant, boolean noAi) {
        ClientPacketDistributor.sendToServer(new AdminCreaturePayloads.SpawnRequest(creatureId, variant, noAi));
    }

    public static void cleanupTestCreatures() {
        ClientPacketDistributor.sendToServer(AdminCreaturePayloads.CleanupRequest.INSTANCE);
    }

    public static void heritagePlayers() {
        ClientPacketDistributor.sendToServer(AdminHeritagePayloads.PlayersRequest.INSTANCE);
    }

    public static void inspectPlayer(UUID player) {
        ClientPacketDistributor.sendToServer(new AdminHeritagePayloads.InspectRequest(player));
    }

    /** Sent only after the administrator confirmed in a dialog; the server refuses it otherwise. */
    public static void assignHeritage(UUID player, String heritageId, String variantId) {
        ClientPacketDistributor.sendToServer(new AdminHeritagePayloads.AssignRequest(player, heritageId, variantId, true));
    }

    /** Sent only after the administrator confirmed in a dialog; the server refuses it otherwise. */
    public static void resetOnboarding(UUID player) {
        ClientPacketDistributor.sendToServer(new AdminHeritagePayloads.ResetOnboardingRequest(player, true));
    }

    public static void testSpell(String spellId, SpellTestService.TargetMode mode, @Nullable UUID player) {
        ClientPacketDistributor.sendToServer(new AdminSpellPayloads.TestRequest(spellId, mode, player));
    }
}
