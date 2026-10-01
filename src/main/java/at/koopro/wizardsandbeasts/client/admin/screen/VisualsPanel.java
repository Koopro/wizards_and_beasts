package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Visuals, as seven tabs: <b>Beams</b> (browser, editor, live preview, presets), <b>Particles</b>, <b>Impacts</b>,
 * <b>HUD</b> (toggle + preview), <b>Screen</b> (screen effects), <b>Entities</b> (the entity viewer) and <b>Debug</b>
 * (this client's beam budget and debug tools). Switching tabs disposes the tab left behind so its
 * previews stop.
 *
 * <p>What is whose: beam looks and impact intensity are server settings every player receives; the HUD, particle and
 * screen-effect rows are each player's own preference (shown read-only here); the Debug tab touches this client only.
 */
@NullMarked
final class VisualsPanel extends TabbedPanel {

    enum Tab { BEAMS, PARTICLES, IMPACTS, HUD, SCREEN, ENTITIES, DEBUG }

    private static final String KEY = "admin.wizards_and_beasts.tab.visuals.";
    private static final Set<String> PARTICLE_SETTINGS = Set.of("broom_speed_particles");
    private static final Set<String> SCREEN_SETTINGS = Set.of("reduce_screen_effects", "broom_fov_effect", "broom_wind_volume");

    static Tab tab = Tab.BEAMS;

    VisualsPanel() {
        super(AdminCategory.VISUALS, tabs(), () -> tab.ordinal(), index -> tab = Tab.values()[index]);
    }

    /** The tab a Visuals setting is shown on, for search. */
    static Tab tabFor(Identifier id) {
        String path = id.getPath();
        if (PARTICLE_SETTINGS.contains(path)) {
            return Tab.PARTICLES;
        }
        if (SCREEN_SETTINGS.contains(path)) {
            return Tab.SCREEN;
        }
        return path.contains("hud") ? Tab.HUD : Tab.BEAMS;
    }

    private static List<TabbedPanel.Tab> tabs() {
        List<TabbedPanel.Tab> out = new ArrayList<>();
        for (Tab target : Tab.values()) {
            out.add(new TabbedPanel.Tab(KEY + target.name().toLowerCase(Locale.ROOT), () -> create(target)));
        }
        return out;
    }

    private static AdminPanel create(Tab tab) {
        return switch (tab) {
            case BEAMS -> new BeamBrowserPanel();
            case PARTICLES -> slice(PARTICLE_SETTINGS, "particles");
            case IMPACTS -> new ImpactsPanel();
            case HUD -> new HudPreviewPanel();
            case SCREEN -> slice(SCREEN_SETTINGS, "screen");
            case ENTITIES -> new EntityRenderingPanel();
            case DEBUG -> new DebugRenderingPanel();
        };
    }

    private static SettingsPanel slice(Set<String> paths, String key) {
        return new SettingsPanel(AdminCategory.VISUALS, (Identifier id) -> paths.contains(id.getPath()),
                Component.translatable(KEY + key + ".title"), Component.translatable(KEY + key + ".summary"));
    }
}
