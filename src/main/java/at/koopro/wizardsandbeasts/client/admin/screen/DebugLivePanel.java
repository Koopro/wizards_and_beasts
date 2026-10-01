package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.admin.AdminCategory;
import at.koopro.wizardsandbeasts.admin.debug.CastDiagnostics;
import at.koopro.wizardsandbeasts.admin.debug.LiveDiagnostics;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminOpsState;
import at.koopro.wizardsandbeasts.client.admin.widget.AdminTheme;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Debug → Live: what the casting and beam machinery holds right now — every beam being channelled, every open wand
 * hold with its release flags (the session a release is matched against), the last cast events and how often each
 * refusal happened, this mod's entities by type, every module's state, each connection's packet rate and latency.
 * Read from the server once a second while shown; reading it renews this administrator's debug leases.
 */
@NullMarked
final class DebugLivePanel extends OpsDocPanel {

    private static final String KEY = "admin.wizards_and_beasts.debug_live.";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    @Override
    public AdminCategory section() {
        return AdminCategory.DEBUG;
    }

    @Override
    protected Component title() {
        return Component.translatable(KEY + "title");
    }

    @Override
    protected Component summary() {
        return Component.translatable(KEY + "summary");
    }

    @Override
    protected int pollTicks() {
        return 20;
    }

    @Override
    protected void poll() {
        ClientAdminOpsState.requestDiagnostics();
    }

    private static String flag(boolean value, String key) {
        return value ? Component.translatable(KEY + key).getString() : "";
    }

    @Override
    protected List<Line> lines() {
        List<Line> out = new ArrayList<>();
        LiveDiagnostics.Snapshot d = ClientAdminOpsState.diagnostics();
        if (d == null) {
            out.add(note("admin.wizards_and_beasts.status.loading"));
            return out;
        }
        out.add(heading(KEY + "beams", d.beams().size()));
        if (d.beams().isEmpty()) {
            out.add(note(KEY + "none"));
        }
        for (LiveDiagnostics.Beam beam : d.beams()) {
            out.add(plain("• " + beam.player() + " · " + beam.spell() + " · " + beam.ticks() + "t"
                    + (beam.hasTarget() ? " · " + Component.translatable(KEY + "target").getString() : ""), AdminTheme.INK_2));
        }

        out.add(heading(KEY + "sessions", d.sessions().size()));
        if (d.sessions().isEmpty()) {
            out.add(note(KEY + "none"));
        }
        for (LiveDiagnostics.Session session : d.sessions()) {
            String flags = List.of(flag(session.releaseConsumed(), "released"), flag(session.vanillaRelease(), "vanilla_release"),
                    flag(session.clashHold(), "clash_hold")).stream().filter(s -> !s.isEmpty()).collect(Collectors.joining(", "));
            out.add(plain("• " + session.player() + " · #" + session.id() + " · " + (session.spell().isEmpty() ? "—" : session.spell())
                    + " · " + session.ageTicks() + "t" + (flags.isEmpty() ? "" : " · " + flags), AdminTheme.INK_2));
        }
        out.add(note(KEY + "sessions_note"));

        out.add(heading(KEY + "casts", d.recentCasts().size()));
        if (d.recentCasts().isEmpty()) {
            out.add(note(KEY + "none"));
        }
        for (CastDiagnostics.Event event : d.recentCasts()) {
            out.add(plain(TIME.format(Instant.ofEpochMilli(event.timeMillis())) + "  " + event.player() + "  "
                    + event.event().replace("cast_", "") + "  " + event.detail(), event.failure() ? AdminTheme.BAD : AdminTheme.INK_2));
        }

        out.add(heading(KEY + "counts"));
        if (d.castCounts().isEmpty()) {
            out.add(note(KEY + "none"));
        }
        for (LiveDiagnostics.Count count : d.castCounts()) {
            out.add(plain("• " + count.key().replace("cast_", "") + " ×" + count.count(),
                    count.key().equals("cast_success") ? AdminTheme.GOOD : AdminTheme.INK_2));
        }

        out.add(heading(KEY + "entities", d.modEntities()));
        for (LiveDiagnostics.Count count : d.entityTypes()) {
            out.add(plain("• " + count.key() + " ×" + count.count(), AdminTheme.INK_2));
        }

        out.add(heading(KEY + "modules"));
        String modules = d.modules().stream().map(m -> m.module() + " " + Component.translatable(
                "admin.wizards_and_beasts.module_state." + m.state()).getString()).collect(Collectors.joining(" · "));
        out.add(plain(modules, AdminTheme.INK_2));

        out.add(heading(KEY + "network"));
        if (d.connections().isEmpty()) {
            out.add(note(KEY + "none"));
        }
        for (LiveDiagnostics.Connection connection : d.connections()) {
            out.add(plain(String.format(Locale.ROOT, "• %s · %d ms · %.0f out/s · %.0f in/s", connection.player(),
                    connection.latencyMs(), connection.sentPerSecond(), connection.receivedPerSecond()), AdminTheme.INK_2));
        }
        out.add(note(KEY + "network_note"));
        return out;
    }
}
