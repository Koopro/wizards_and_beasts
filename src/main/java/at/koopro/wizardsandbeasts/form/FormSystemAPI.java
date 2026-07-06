package at.koopro.wizardsandbeasts.form;

import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.event.form.FormEvents;
import at.koopro.wizardsandbeasts.network.form.FormSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.heritage.HeritageFormBridge;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Server-side API for the player form system.
 * <p>
 * All form changes are server-authoritative. The client renders
 * whatever the server tells it via {@link FormSyncS2CPayload}.
 */
public final class FormSystemAPI {

    private FormSystemAPI() {}

    /**
     * Sets the player's active form. Applies the form's size profile,
     * fires a change event, and syncs to all tracking clients.
     *
     * @return the applied form, or null if the formId is invalid
     */
    @Nullable
    public static PlayerForm setPlayerForm(ServerPlayer player, String formId) {
        PlayerForm newForm = FormRegistry.get(formId);
        if (newForm == null) return null;

        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        String oldFormId = data.getActiveFormId();
        PlayerForm oldForm = oldFormId != null ? FormRegistry.get(oldFormId) : null;

        data.setActiveFormId(formId);

        // Apply the form's size profile
        SizeProfile profile = SizeProfileRegistry.getOrDefault(newForm.sizeProfileId());
        SizeSystemAPI.applyProfile(player, profile);

        // Sync to all tracking players
        FormSyncS2CPayload.syncToTracking(player);

        // Fire event
        NeoForge.EVENT_BUS.post(new FormEvents.PlayerFormChangedEvent(player, oldForm, newForm));

        return newForm;
    }

    /**
     * Returns the player's currently active form, or null if none is set.
     */
    @Nullable
    public static PlayerForm getPlayerForm(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        String formId = data.getActiveFormId();
        return formId != null ? FormRegistry.get(formId) : null;
    }

    /**
     * Returns the active form ID, or null.
     */
    @Nullable
    public static String getPlayerFormId(ServerPlayer player) {
        return player.getData(ModAttachments.HERITAGE_DATA.get()).getActiveFormId();
    }

    /**
     * Returns all forms available to the player based on their type.
     */
    public static List<String> getAvailableForms(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        Heritage type = data.getSelectedHeritage();
        if (type == null) return List.of("human_default");
        return HeritageFormBridge.getAvailableFormIds(type);
    }

    /**
     * Resets the player's form to the default for their type/subtype/state.
     * If no type is selected, resets to "human_default".
     */
    public static void resetToDefault(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        Heritage type = data.getSelectedHeritage();

        String defaultFormId;
        if (type != null) {
            defaultFormId = HeritageFormBridge.getDefaultFormId(
                    type, data.getSelectedHeritageVariant(), data.getTransformationState());
        } else {
            defaultFormId = "human_default";
        }

        setPlayerForm(player, defaultFormId);
    }

    /**
     * Clears the player's form and removes all size modifiers.
     */
    public static void clearForm(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        String oldFormId = data.getActiveFormId();
        PlayerForm oldForm = oldFormId != null ? FormRegistry.get(oldFormId) : null;

        data.setActiveFormId(null);
        SizeSystemAPI.removeProfile(player);
        FormSyncS2CPayload.syncToTracking(player);

        if (oldForm != null) {
            NeoForge.EVENT_BUS.post(new FormEvents.PlayerFormChangedEvent(player, oldForm, null));
        }
    }

    /**
     * Re-applies the current form's size profile (e.g. after respawn/relog).
     */
    public static void reapplyCurrentForm(ServerPlayer player) {
        PlayerHeritageData data = player.getData(ModAttachments.HERITAGE_DATA.get());
        String formId = data.getActiveFormId();
        if (formId != null) {
            PlayerForm form = FormRegistry.get(formId);
            if (form != null) {
                SizeProfile profile = SizeProfileRegistry.getOrDefault(form.sizeProfileId());
                SizeSystemAPI.applyProfile(player, profile);
            }
        }
    }
}
