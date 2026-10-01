package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.perf.PerformanceMetrics;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminOpsState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Performance → Live: the server's tick, entities per dimension, the magic running now, network rates, and this
 * client's frame rate and particles. Every number is one the game measured; a metric it does not measure here says so.
 * Asks once a second while shown.
 */
@NullMarked
final class PerfLivePanel extends OpsDocPanel {

    static final String KEY = "admin.wizards_and_beasts.perf.";

    @Override
    public AdminCategory section() {
        return AdminCategory.PERFORMANCE;
    }

    @Override
    protected Component title() {
        return Component.translatable(KEY + "live.title");
    }

    @Override
    protected Component summary() {
        return Component.translatable(KEY + "live.summary");
    }

    @Override
    protected int pollTicks() {
        return 20;
    }

    @Override
    protected void poll() {
        ClientAdminOpsState.requestMetrics();
    }

    private static String ms(double value) {
        return String.format(Locale.ROOT, "%.2f ms", value);
    }

    @Override
    protected List<Line> lines() {
        List<Line> out = new ArrayList<>();
        PerformanceMetrics.Snapshot m = ClientAdminOpsState.metrics();
        out.add(heading(KEY + "server"));
        if (m == null) {
            out.add(note("admin.wizards_and_beasts.status.loading"));
        } else {
            boolean timed = m.tps() != PerformanceMetrics.UNAVAILABLE;
            out.add(timed
                    ? text(KEY + "tps", String.format(Locale.ROOT, "%.1f", m.tps()), String.format(Locale.ROOT, "%.0f", m.targetTps()))
                    : note(KEY + "not_timed"));
            if (timed) {
                out.add(text(KEY + "mspt", ms(m.msptAvg()), ms(m.msptMax()),
                        String.format(Locale.ROOT, "%.0f", 1000.0 / m.targetTps())));
                if (m.tps() < m.targetTps() - 0.5) {
                    out.add(Line.text(Component.translatable(KEY + "behind"), AdminTheme.BAD));
                }
            }

            out.add(heading(KEY + "entities"));
            out.add(text(KEY + "entity_total", m.entities(), m.modEntities(), m.spellEntities(), m.players()));
            for (PerformanceMetrics.Dimension d : m.dimensions()) {
                String tick = d.msptAvg() == PerformanceMetrics.UNAVAILABLE ? Component.translatable(KEY + "unmeasured").getString()
                        : ms(d.msptAvg());
                out.add(plain("• " + d.id() + ": " + Component.translatable(KEY + "dimension", d.entities(), d.modEntities(),
                        d.spellEntities(), d.loadedChunks(), d.players(), tick).getString(), AdminTheme.INK_2));
            }

            out.add(heading(KEY + "magic"));
            out.add(text(KEY + "beams", m.beams()));
            out.add(text(KEY + "sessions", m.castSessions()));
            out.add(text(KEY + "spell_entities", m.spellEntities()));
            out.add(text(KEY + "mod_effects", m.modEffectsOnPlayers()));
            out.add(text(KEY + "intervals", m.beamScanInterval(), m.beamEffectInterval()));

            out.add(heading(KEY + "network"));
            if (m.networkMeasured()) {
                out.add(text(KEY + "packets", String.format(Locale.ROOT, "%.0f", m.packetsSent()),
                        String.format(Locale.ROOT, "%.0f", m.packetsReceived())));
                out.add(text(KEY + "latency", String.format(Locale.ROOT, "%.0f", m.latencyAvgMs())));
            } else {
                out.add(note(KEY + "network_none"));
            }
            out.add(note(KEY + "network_bytes"));
        }

        out.add(heading(KEY + "client"));
        int fps = ClientAdminOpsState.fps();
        int particles = ClientAdminOpsState.particles();
        out.add(fps < 0 ? note("admin.wizards_and_beasts.status.loading") : text(KEY + "fps", fps));
        out.add(particles < 0 ? note(KEY + "particles_unmeasured") : text(KEY + "particles", particles));
        if (ClientAdminOpsState.clientEntities() >= 0) {
            out.add(text(KEY + "client_entities", ClientAdminOpsState.clientEntities()));
        }
        out.add(note(KEY + "client_note"));
        out.add(note(KEY + "live_note"));
        return out;
    }
}
