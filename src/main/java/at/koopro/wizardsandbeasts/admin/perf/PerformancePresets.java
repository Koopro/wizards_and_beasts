package at.koopro.wizardsandbeasts.admin.perf;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.AdminSettingService;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.admin.config.AdminSettingRegistry;
import at.koopro.wizardsandbeasts.admin.history.AdminChangeRecord;
import at.koopro.wizardsandbeasts.admin.profile.ProfileApplier;
import at.koopro.wizardsandbeasts.admin.profile.ProfileValidator;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LOW / MEDIUM / HIGH: named sets of values for the existing performance settings, nothing more. Applying one changes
 * those settings through {@link AdminSettingService} (all-or-nothing, one history group, revertible like any change);
 * no system reads a preset. CUSTOM is not a preset but a description: the settings match none of them.
 *
 * <p>Also the classification of every setting that costs or saves performance: Gameplay (changes outcomes), Server
 * (server CPU or bandwidth), Client (each player's own machine), Visual (what is drawn).
 */
@NullMarked
public final class PerformancePresets {

    public enum Preset {
        LOW(Map.of("perf_profile", "LOW", "beam_target_scan_interval_ticks", "4", "beam_channel_effect_interval_ticks", "8")),
        MEDIUM(Map.of("perf_profile", "MEDIUM", "beam_target_scan_interval_ticks", "2", "beam_channel_effect_interval_ticks", "5")),
        HIGH(Map.of("perf_profile", "HIGH", "beam_target_scan_interval_ticks", "1", "beam_channel_effect_interval_ticks", "3"));

        private final Map<String, String> values;

        Preset(Map<String, String> values) {
            this.values = values;
        }

        /** Setting path → canonical value. */
        public Map<String, String> settings() {
            return values;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static @Nullable Preset byId(String id) {
            for (Preset preset : values()) {
                if (preset.id().equals(id)) {
                    return preset;
                }
            }
            return null;
        }
    }

    public enum Kind { GAMEPLAY, SERVER, CLIENT, VISUAL }

    /**
     * Every performance-relevant setting (by path, whatever section it is filed under) and what it affects. The
     * Performance → Settings tab shows exactly these.
     */
    public static final Map<String, Set<Kind>> CLASSIFICATION;

    static {
        Map<String, Set<Kind>> map = new LinkedHashMap<>();
        // Server profile: offsets both beam intervals, scales server-sent particles (Protego, clash) and spell trails.
        map.put("perf_profile", Set.of(Kind.SERVER, Kind.VISUAL));
        map.put("beam_target_scan_interval_ticks", Set.of(Kind.SERVER, Kind.GAMEPLAY));
        map.put("beam_channel_effect_interval_ticks", Set.of(Kind.SERVER, Kind.GAMEPLAY));
        // Debug overhead.
        map.put("enable_debug_tools", Set.of(Kind.SERVER, Kind.GAMEPLAY));
        map.put("debug_log_spell_gate_reasons", Set.of(Kind.SERVER));
        // Each player's own rendering.
        map.put("reduce_screen_effects", Set.of(Kind.CLIENT, Kind.VISUAL));
        map.put("broom_speed_particles", Set.of(Kind.CLIENT, Kind.VISUAL));
        map.put("show_spell_hud_overlay", Set.of(Kind.CLIENT, Kind.VISUAL));
        CLASSIFICATION = java.util.Collections.unmodifiableMap(map);
    }

    private PerformancePresets() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    /** The preset the settings match now, or null for CUSTOM. */
    public static @Nullable Preset current(AdminSettingRegistry registry) {
        for (Preset preset : Preset.values()) {
            boolean matches = true;
            for (Map.Entry<String, String> entry : preset.settings().entrySet()) {
                AdminSetting<?> setting = registry.get(id(entry.getKey()));
                if (setting == null || !setting.currentText().equals(entry.getValue())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return preset;
            }
        }
        return null;
    }

    /**
     * Applies {@code preset}: every setting that differs is changed through the service, all or nothing, as one group
     * {@code perf_preset:<id>:<millis>}. Returns the applier's outcome (zero changes when the preset is already in force).
     */
    public static ProfileApplier.Outcome apply(AdminSettingService service, AdminContext actor, Preset preset,
                                               @Nullable MinecraftServer server) {
        List<ProfileValidator.Change> changes = new ArrayList<>();
        for (Map.Entry<String, String> entry : preset.settings().entrySet()) {
            AdminSetting<?> setting = service.registry().get(id(entry.getKey()));
            if (setting != null && !setting.currentText().equals(entry.getValue())) {
                changes.add(new ProfileValidator.Change(setting.id(), setting.currentText(), entry.getValue(),
                        setting.applyMode(), null));
            }
        }
        return ProfileApplier.apply(service, actor, changes, "perf_preset:" + preset.id() + ":" + System.currentTimeMillis(),
                AdminChangeRecord.Kind.PROFILE, server);
    }
}
