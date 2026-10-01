package at.koopro.wizardsandbeasts.visual.beam;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.network.visual.BeamVisualSyncS2CPayload;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The server side of beam visuals: the only writer of {@link BeamVisualData}, and the one place the overrides are
 * sent to players. Server thread only.
 *
 * <p>Sync is event-driven — on join, on {@code /reload}, and after each change — never per tick: a beam's look changes
 * when an administrator applies it, not while it is being drawn. Only overrides travel; each client composes them over
 * the authored default with the spell colour it already has.
 *
 * <p>Preset operations return a {@link PresetOutcome}. Authorisation is the admin layer's job
 * ({@code BeamVisualAdminService}); this class only keeps the store consistent.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class BeamVisualService {

    private static final Logger LOGGER = LogUtils.getLogger();

    public enum PresetOutcome { OK, INVALID_NAME, NAME_TAKEN, NOT_FOUND, BUILTIN, LIMIT, INVALID_VALUES }

    public record PresetResult(PresetOutcome outcome, String presetId) {
        public boolean ok() {
            return outcome == PresetOutcome.OK;
        }
    }

    private BeamVisualService() {}

    // ── looks ──

    public static int colorOf(String spellKey) {
        Spell spell = Spells.byId(spellKey);
        return spell == null ? 0x44CCFF : spell.getColor();
    }

    /** The authored look of a beam spell (null for anything else). */
    public static @Nullable BeamVisual authored(String spellKey) {
        return BeamVisualDefaults.forSpell(spellKey, colorOf(spellKey));
    }

    /** The look every player's client draws for a beam spell right now. */
    public static @Nullable BeamVisual effective(MinecraftServer server, String spellKey) {
        BeamVisual base = authored(spellKey);
        return base == null ? null : BeamVisuals.apply(base, BeamVisualData.get(server).overridesFor(spellKey));
    }

    /** Stores or removes one override and resends the table. {@code value} must be canonical, or null to remove. */
    public static void setOverride(MinecraftServer server, String spellKey, BeamVisualProperty property,
                                   @Nullable String value) {
        BeamVisualData.get(server).setOverride(spellKey, property, value);
        publish(server);
    }

    /**
     * Scales an impact burst's particle count by the beam's {@code impact_intensity}. Visual only: the burst decides
     * nothing, it is what players see (and the camera kick it carries). Returns 0 when the burst should be skipped.
     */
    public static int impactCount(MinecraftServer server, @Nullable String spellId, int count) {
        String key = BeamVisualDefaults.key(spellId);
        if (key == null) {
            return count;
        }
        String text = BeamVisualData.get(server).overridesFor(key).get(BeamVisualProperty.IMPACT_INTENSITY.id());
        if (text == null) {
            return count;
        }
        double scale;
        try {
            scale = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return count;
        }
        return (int) Math.round(count * Math.max(0.0, scale));
    }

    // ── presets ──

    /** Built-in presets first (one per beam spell), then custom ones by name. */
    public static List<BeamPreset> presets(MinecraftServer server) {
        List<BeamPreset> out = new ArrayList<>(BeamPreset.builtins(BeamVisualService::colorOf));
        List<BeamPreset> custom = new ArrayList<>();
        BeamVisualData.get(server).presets().forEach((id, stored) ->
                custom.add(new BeamPreset(id, stored.name(), false, stored.values())));
        custom.sort(Comparator.comparing(p -> p.name().toLowerCase(java.util.Locale.ROOT)));
        out.addAll(custom);
        return out;
    }

    public static @Nullable BeamPreset preset(MinecraftServer server, String id) {
        for (BeamPreset preset : presets(server)) {
            if (preset.id().equals(id)) {
                return preset;
            }
        }
        return null;
    }

    /** Saves {@code values} as a new custom preset named {@code name}. */
    public static PresetResult create(MinecraftServer server, String name, Map<String, String> values) {
        BeamVisualData data = BeamVisualData.get(server);
        if (!BeamPreset.validName(name)) {
            return new PresetResult(PresetOutcome.INVALID_NAME, "");
        }
        Map<String, String> clean = BeamVisuals.sanitize(values);
        if (clean.size() != BeamVisualProperty.values().length) {
            return new PresetResult(PresetOutcome.INVALID_VALUES, "");
        }
        if (data.presets().size() >= BeamPreset.MAX_CUSTOM) {
            return new PresetResult(PresetOutcome.LIMIT, "");
        }
        String id = BeamPreset.idFor(name);
        if (data.preset(id) != null || nameTaken(data, name, null)) {
            return new PresetResult(PresetOutcome.NAME_TAKEN, id);
        }
        data.putPreset(id, new BeamVisualData.StoredPreset(name.trim(), clean));
        LOGGER.info("[Visuals] Beam preset '{}' created", id);
        return new PresetResult(PresetOutcome.OK, id);
    }

    /** Overwrites a custom preset's values. A built-in preset is never overwritten. */
    public static PresetResult overwrite(MinecraftServer server, String id, Map<String, String> values) {
        if (id.startsWith(BeamPreset.BUILTIN_PREFIX)) {
            return new PresetResult(PresetOutcome.BUILTIN, id);
        }
        BeamVisualData data = BeamVisualData.get(server);
        BeamVisualData.StoredPreset stored = data.preset(id);
        if (stored == null) {
            return new PresetResult(PresetOutcome.NOT_FOUND, id);
        }
        Map<String, String> clean = BeamVisuals.sanitize(values);
        if (clean.size() != BeamVisualProperty.values().length) {
            return new PresetResult(PresetOutcome.INVALID_VALUES, id);
        }
        data.putPreset(id, new BeamVisualData.StoredPreset(stored.name(), clean));
        LOGGER.info("[Visuals] Beam preset '{}' saved", id);
        return new PresetResult(PresetOutcome.OK, id);
    }

    /** Copies any preset — a built-in one included — into a new custom preset named {@code name}. */
    public static PresetResult duplicate(MinecraftServer server, String sourceId, String name) {
        BeamPreset source = preset(server, sourceId);
        if (source == null) {
            return new PresetResult(PresetOutcome.NOT_FOUND, sourceId);
        }
        return create(server, name, source.values());
    }

    /** Renames a custom preset; its id stays, so nothing that remembers it goes stale. */
    public static PresetResult rename(MinecraftServer server, String id, String name) {
        if (id.startsWith(BeamPreset.BUILTIN_PREFIX)) {
            return new PresetResult(PresetOutcome.BUILTIN, id);
        }
        BeamVisualData data = BeamVisualData.get(server);
        BeamVisualData.StoredPreset stored = data.preset(id);
        if (stored == null) {
            return new PresetResult(PresetOutcome.NOT_FOUND, id);
        }
        if (!BeamPreset.validName(name)) {
            return new PresetResult(PresetOutcome.INVALID_NAME, id);
        }
        if (nameTaken(data, name, id)) {
            return new PresetResult(PresetOutcome.NAME_TAKEN, id);
        }
        data.putPreset(id, new BeamVisualData.StoredPreset(name.trim(), stored.values()));
        LOGGER.info("[Visuals] Beam preset '{}' renamed to '{}'", id, name.trim());
        return new PresetResult(PresetOutcome.OK, id);
    }

    /** Deletes a custom preset. Built-ins cannot be deleted; spells are templates' copies, so none goes stale. */
    public static PresetResult delete(MinecraftServer server, String id) {
        if (id.startsWith(BeamPreset.BUILTIN_PREFIX)) {
            return new PresetResult(PresetOutcome.BUILTIN, id);
        }
        if (!BeamVisualData.get(server).removePreset(id)) {
            return new PresetResult(PresetOutcome.NOT_FOUND, id);
        }
        LOGGER.info("[Visuals] Beam preset '{}' deleted", id);
        return new PresetResult(PresetOutcome.OK, id);
    }

    /**
     * What a spell's browser row calls its look: {@code default} with no overrides, the id of a preset whose values it
     * matches exactly, otherwise {@code custom}.
     */
    public static String presetLabel(MinecraftServer server, String spellKey) {
        if (BeamVisualData.get(server).overridesFor(spellKey).isEmpty()) {
            return "default";
        }
        BeamVisual effective = effective(server, spellKey);
        if (effective == null) {
            return "default";
        }
        Map<String, String> text = BeamVisuals.toText(effective);
        for (BeamPreset preset : presets(server)) {
            if (preset.values().equals(text)) {
                return preset.id();
            }
        }
        return "custom";
    }

    private static boolean nameTaken(BeamVisualData data, String name, @Nullable String exceptId) {
        String wanted = name.trim();
        for (Map.Entry<String, BeamVisualData.StoredPreset> entry : data.presets().entrySet()) {
            if (!entry.getKey().equals(exceptId) && entry.getValue().name().equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    // ── sync ──

    public static void publish(MinecraftServer server) {
        Map<String, Map<String, String>> table = BeamVisualData.get(server).overrides();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BeamVisualSyncS2CPayload.send(player, table);
        }
    }

    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        MinecraftServer server = event.getPlayerList().getServer();
        Map<String, Map<String, String>> table = BeamVisualData.get(server).overrides();
        if (event.getPlayer() != null) {
            BeamVisualSyncS2CPayload.send(event.getPlayer(), table);
            return;
        }
        for (ServerPlayer player : event.getPlayerList().getPlayers()) {
            BeamVisualSyncS2CPayload.send(player, table);
        }
    }
}
