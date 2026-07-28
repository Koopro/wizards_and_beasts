package at.koopro.wizardsandbeasts.client.wand;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.beam.BeamChannelClient;
import at.koopro.wizardsandbeasts.network.debug.BeamPresetS2CPayload;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Client-side handlers for beam-debug S2C payloads. Bodies may reference client classes;
 * the common registrar references these via method ref (NeoForge-documented dist-safe pattern:
 * client references live only in method bodies, never executed on the dedicated server).
 */
@NullMarked
public final class BeamClientPayloadHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BeamClientPayloadHandlers() {}

    public static void handleBeamPreset(BeamPresetS2CPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            BeamSettings.PerformancePreset preset;
            try {
                preset = BeamSettings.PerformancePreset.valueOf(payload.presetName());
            } catch (IllegalArgumentException ex) {
                LOGGER.warn("Ignoring unknown beam preset '{}' from server", payload.presetName());
                return;
            }
            BeamSettings.applyPerformancePreset(preset);
        });
    }

}
