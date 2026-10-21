package at.koopro.wizardsandbeasts.admin.visual;

import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.visual.beam.BeamPreset;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisual;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualData;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualDefaults;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualProperty;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisualService;
import at.koopro.wizardsandbeasts.visual.beam.BeamVisuals;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads and edits beam visuals for the Visuals section. Values change only through ordinary beam settings
 * ({@link BeamVisualSettingProvider} via {@code AdminSettingService}); this class lists them and runs the preset
 * operations, each behind the {@link AdminCapability#VISUAL} capability.
 */
@NullMarked
public final class BeamVisualAdminService {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private BeamVisualAdminService() {}

    public static boolean authorised(AdminContext actor) {
        return actor.canRead() && actor.canModify(AdminCapability.VISUAL);
    }

    /**
     * One beam spell as the browser shows it.
     *
     * @param preset    {@code default}, {@code custom}, or the id of the preset its look matches exactly
     * @param authored  every property of the code's look, as text — the "default" side of a comparison
     * @param effective every property as players see it now
     */
    public record BeamSummary(String spell, int color, boolean enabled, boolean overridden, String preset,
                              Map<String, String> authored, Map<String, String> effective) {}

    public enum PresetOp { CREATE, SAVE, DUPLICATE, RENAME, DELETE }

    public record Listing(List<BeamSummary> beams, List<BeamPreset> presets, List<AdminSettingDescriptor> settings) {}

    public static Listing list(MinecraftServer server, AdminContext viewer) {
        List<BeamSummary> beams = new ArrayList<>();
        List<AdminSettingDescriptor> settings = new ArrayList<>();
        for (String spell : BeamVisualDefaults.SPELLS) {
            BeamVisual authored = BeamVisualService.authored(spell);
            BeamVisual effective = BeamVisualService.effective(server, spell);
            if (authored == null || effective == null) {
                continue;
            }
            beams.add(new BeamSummary(spell, BeamVisualService.colorOf(spell), effective.enabled(),
                    !BeamVisualData.get(server).overridesFor(spell).isEmpty(),
                    BeamVisualService.presetLabel(server, spell),
                    BeamVisuals.toText(authored), BeamVisuals.toText(effective)));
            for (BeamVisualProperty property : BeamVisualProperty.values()) {
                AdminSetting<?> setting = AdminSettings.registry().get(BeamVisualSettingProvider.id(spell, property));
                if (setting != null) {
                    settings.add(AdminSettingDescriptor.of(setting, viewer));
                }
            }
        }
        return new Listing(List.copyOf(beams), BeamVisualService.presets(server), List.copyOf(settings));
    }

    /**
     * Runs one preset operation. {@code id} names the preset acted on (the source, for a duplicate), {@code name} the
     * new name, {@code values} the look to store (create, save).
     */
    public static BeamVisualService.PresetResult run(MinecraftServer server, PresetOp op, String id, String name,
                                                     Map<String, String> values) {
        return switch (op) {
            case CREATE -> BeamVisualService.create(server, name, values);
            case SAVE -> BeamVisualService.overwrite(server, id, values);
            case DUPLICATE -> BeamVisualService.duplicate(server, id, name);
            case RENAME -> BeamVisualService.rename(server, id, name);
            case DELETE -> BeamVisualService.delete(server, id);
        };
    }

    /** What {@link #perform} did: {@code unauthorized}, or the preset outcome ({@code ok}, {@code name_taken}, …). */
    public record Performed(String outcome, String presetId, boolean ok) {}

    /**
     * The one entry point for a preset operation, used by the Visuals page and {@code /wandb admin visual preset}
     * alike: authority, then the operation, then an audit line. {@link #run} itself checks nothing, so nothing else
     * should call it.
     */
    public static Performed perform(AdminContext actor, MinecraftServer server, PresetOp op, String id, String name,
                                    Map<String, String> values) {
        if (!authorised(actor)) {
            LOGGER.warn("[Admin] Refused beam preset {} {} from unauthorised {}", op, id, actor.actorName());
            return new Performed("unauthorized", "", false);
        }
        BeamVisualService.PresetResult result = run(server, op, id, name, values);
        String outcome = result.outcome().name().toLowerCase(java.util.Locale.ROOT);
        LOGGER.info("[Admin] {} beam preset {} {}{}: {}", actor.actorName(), op.name().toLowerCase(java.util.Locale.ROOT),
                id.isEmpty() ? "" : id, name.isEmpty() ? "" : " -> " + name, outcome);
        at.koopro.wizardsandbeasts.admin.operation.AdminOperationService.record(server, actor, "visuals", "beam_preset_" + op.name().toLowerCase(java.util.Locale.ROOT), id,
                name, result.ok(), outcome);
        return new Performed(outcome, result.presetId(), result.ok());
    }

    /** For the preset list: a built-in preset is named by its spell. */
    public static boolean isBuiltin(String presetId) {
        return presetId.startsWith(BeamPreset.BUILTIN_PREFIX);
    }
}
