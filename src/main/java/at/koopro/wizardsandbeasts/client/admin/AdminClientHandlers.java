package at.koopro.wizardsandbeasts.client.admin;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.admin.screen.AdminControlCenterScreen;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingResultS2CPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminSnapshotS2CPayload;
import at.koopro.wizardsandbeasts.network.admin.AdminBroomPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminBrewPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminWandPayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminHeritagePayloads;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Where the admin S2C payloads land on the client. Kept out of the payload classes so they stay loadable on
 * a dedicated server, the same split {@code DebugClientPayloadHandlers} makes.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID, value = Dist.CLIENT)
public final class AdminClientHandlers {

    private AdminClientHandlers() {}

    static {
        // The module system's own sync (ModuleStateSyncPayload) also refreshes an open Control Center: the Modules
        // page reads state from it, and a cascade (a dependant closed with its base) arrives only this way.
        at.koopro.wizardsandbeasts.client.module.ClientModuleState.whenApplied(AdminClientHandlers::notifyScreen);
    }

    public static void onSnapshot(AdminSnapshotS2CPayload payload) {
        ClientAdminState.accept(payload);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AdminControlCenterScreen open) {
            open.onServerState();
        } else if (payload.openScreen()) {
            minecraft.setScreen(new AdminControlCenterScreen());
        }
    }

    public static void onResult(AdminSettingResultS2CPayload payload) {
        ClientAdminState.accept(payload);
        if (Minecraft.getInstance().screen instanceof AdminControlCenterScreen open) {
            open.onServerState();
        }
    }

    public static void onSpellList(AdminSpellPayloads.ListReply payload) {
        ClientAdminState.accept(payload);
        notifyScreen();
    }

    public static void onSpellDetail(AdminSpellPayloads.DetailReply payload) {
        ClientAdminState.accept(payload);
        notifyScreen();
    }

    public static void onSpellAction(AdminSpellPayloads.ActionReply payload) {
        ClientAdminState.accept(payload);
        notifyScreen();
    }

    public static void onHeritagePlayers(AdminHeritagePayloads.PlayersReply payload) {
        ClientAdminHeritageState.accept(payload);
        notifyScreen();
    }

    public static void onHeritageInspection(AdminHeritagePayloads.InspectReply payload) {
        ClientAdminHeritageState.accept(payload);
        notifyScreen();
    }

    public static void onHeritageAction(AdminHeritagePayloads.ActionReply payload) {
        ClientAdminHeritageState.accept(payload);
        notifyScreen();
    }

    public static void onCreatureList(AdminCreaturePayloads.ListReply payload) {
        ClientAdminCreatureState.accept(payload);
        notifyScreen();
    }

    public static void onCreatureDetail(AdminCreaturePayloads.DetailReply payload) {
        ClientAdminCreatureState.accept(payload);
        notifyScreen();
    }

    public static void onCreatureAction(AdminCreaturePayloads.ActionReply payload) {
        ClientAdminCreatureState.accept(payload);
        notifyScreen();
    }

    public static void onBrewList(AdminBrewPayloads.ListReply payload) {
        ClientAdminBrewState.accept(payload);
        notifyScreen();
    }

    public static void onBrewDetail(AdminBrewPayloads.DetailReply payload) {
        ClientAdminBrewState.accept(payload);
        notifyScreen();
    }

    public static void onWandCatalog(AdminWandPayloads.CatalogReply payload) {
        ClientAdminWandState.accept(payload);
        notifyScreen();
    }

    public static void onWandPreview(AdminWandPayloads.PreviewReply payload) {
        ClientAdminWandState.accept(payload);
        notifyScreen();
    }

    public static void onWandAction(AdminWandPayloads.ActionReply payload) {
        ClientAdminWandState.accept(payload);
        notifyScreen();
    }

    public static void onBroomList(AdminBroomPayloads.ListReply payload) {
        ClientAdminBroomState.accept(payload);
        notifyScreen();
    }

    public static void onBeamList(at.koopro.wizardsandbeasts.network.admin.AdminVisualPayloads.ListReply payload) {
        ClientAdminVisualState.accept(payload);
        notifyScreen();
    }

    public static void onBeamAction(at.koopro.wizardsandbeasts.network.admin.AdminVisualPayloads.ActionReply payload) {
        ClientAdminVisualState.accept(payload);
        notifyScreen();
    }

    public static void onProfileList(at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.ListReply payload) {
        ClientAdminProfileState.accept(payload);
        notifyScreen();
    }

    public static void onProfilePreview(at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.PreviewReply payload) {
        ClientAdminProfileState.accept(payload);
        notifyScreen();
    }

    public static void onProfileAction(at.koopro.wizardsandbeasts.network.admin.AdminProfilePayloads.ActionReply payload) {
        ClientAdminProfileState.accept(payload);
        if (payload.ok() && payload.changed() > 0) {
            ClientAdminState.invalidatePages();
        }
        notifyScreen();
    }

    public static void onPlayerSearch(at.koopro.wizardsandbeasts.network.admin.AdminPlayerPayloads.SearchReply payload) {
        ClientAdminPlayerState.accept(payload);
        notifyScreen();
    }

    public static void onPlayerFacet(at.koopro.wizardsandbeasts.network.admin.AdminPlayerPayloads.FacetReply payload) {
        ClientAdminPlayerState.accept(payload);
        notifyScreen();
    }

    public static void onPlayerLog(at.koopro.wizardsandbeasts.network.admin.AdminPlayerPayloads.LogReply payload) {
        ClientAdminPlayerState.accept(payload);
        notifyScreen();
    }

    public static void onPlayerAction(at.koopro.wizardsandbeasts.network.admin.AdminPlayerPayloads.ActionReply payload) {
        ClientAdminPlayerState.accept(payload);
        notifyScreen();
    }

    public static void onMetrics(at.koopro.wizardsandbeasts.network.admin.AdminOpsPayloads.MetricsReply payload) {
        ClientAdminOpsState.accept(payload);
        notifyScreen();
    }

    public static void onPresetReply(at.koopro.wizardsandbeasts.network.admin.AdminOpsPayloads.PresetReply payload) {
        ClientAdminOpsState.accept(payload);
        notifyScreen();
    }

    public static void onDiagnostics(at.koopro.wizardsandbeasts.network.admin.AdminOpsPayloads.DiagnosticsReply payload) {
        ClientAdminOpsState.accept(payload);
        notifyScreen();
    }

    public static void onToolReply(at.koopro.wizardsandbeasts.network.admin.AdminOpsPayloads.ToolReply payload) {
        ClientAdminOpsState.accept(payload);
        notifyScreen();
    }

    private static void notifyScreen() {
        if (Minecraft.getInstance().screen instanceof AdminControlCenterScreen open) {
            open.onServerState();
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAdminState.clear();
        ClientAdminHeritageState.clear();
        ClientAdminCreatureState.clear();
        ClientAdminBrewState.clear();
        ClientAdminWandState.clear();
        ClientAdminBroomState.clear();
        ClientAdminVisualState.clear();
        ClientAdminProfileState.clear();
        ClientAdminPlayerState.clear();
        ClientAdminOpsState.clear();
    }
}
