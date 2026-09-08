package at.koopro.wizardsandbeasts.spell.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.cast.WandCastSessions;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * What this wizard knows, what is on their bar, and what is currently refusing to cast.
 *
 * <p>The cooldown and reject counters are the useful half. "Nothing happens when I cast" has about
 * six causes — global cooldown, per-spell cooldown, an empty slot, a spell that is known but gated,
 * a rejected packet — and the counters distinguish them without having to reproduce the cast.
 */
@NullMarked
public final class SpellFeatureDebug implements FeatureDebugSection {

    /** Cooldowns and reject entries listed before the section prints a count instead. */
    private static final int MAX_ENTRIES = 10;

    @Override
    public String id() {
        return "spells";
    }

    @Override
    public String title() {
        return "Spells";
    }

    @Override
    public String summary() {
        return "Known spells, loadout, live cooldowns, cast/hit counts and cast rejections.";
    }

    @Override
    public @Nullable Module module() {
        return Module.WANDS_AND_SPELLS;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        PlayerSpellData data = target.getData(ModAttachments.SPELL_DATA.get());
        long now = target.level().getGameTime();

        report.row("  known", data.getKnownSpells().size());
        report.row("  active slot", data.getActiveSlot());
        report.row("  active spell", data.getActiveSpellId() == null
                ? "(empty)" : data.getActiveSpellId());
        report.state("  global cooldown",
                data.isGlobalCooldownActive(now)
                        ? (data.getGlobalCooldownEndTick() - now) + "t left" : "clear",
                data.isGlobalCooldownActive(now) ? ChatPalette.WARN : ChatPalette.OK);

        // The cast session is the server's own answer to "is this player mid-cast right now", and it
        // is the first thing to look at when a release does nothing: a spent token or no session at
        // all means the packet arrived and was refused before any spell gate was ever consulted.
        WandCastSessions.Session session = WandCastSessions.peek(target);
        if (session == null) {
            report.state("  cast session", "idle", ChatPalette.OK);
        } else {
            report.state("  cast session",
                    "#" + session.id() + " held " + (now - session.startGameTick()) + "t"
                            + (session.releaseConsumed() ? ", released" : ", open"),
                    session.releaseConsumed() ? ChatPalette.WARN : ChatPalette.OK);
        }

        if (detail == Detail.BRIEF) {
            return;
        }

        report.section("  loadout");
        String[] loadout = data.getLoadout();
        for (int slot = 0; slot < loadout.length; slot++) {
            report.row("    " + slot, loadout[slot] == null ? "(empty)" : loadout[slot]);
        }

        report.section("  cooldowns");
        int shown = 0;
        for (Map.Entry<String, Long> entry : data.getCooldowns().entrySet()) {
            long remaining = entry.getValue() - now;
            if (remaining <= 0) continue;
            if (shown++ >= MAX_ENTRIES) {
                report.note("    … more not shown");
                break;
            }
            report.row("    " + entry.getKey(), remaining + "t");
        }
        if (shown == 0) {
            report.row("    —", "none active");
        }

        report.section("  practice");
        report.row("    spells cast", data.getCastCounts().values().stream()
                .mapToInt(Integer::intValue).sum());
        report.row("    successful hits", data.getSuccessfulHitCounts().values().stream()
                .mapToInt(Integer::intValue).sum());
        report.row("    combat casts", data.getCombatSpellCasts());
        report.row("    proficiencies held", data.getSpellProficiencies().size());

        // Rejections are counted server-side when a cast packet is refused, so a non-zero entry here
        // is the server saying no to something the client believed it could do.
        report.section("  cast rejections");
        Map<String, Integer> rejects = data.getRejectCounts();
        if (rejects.isEmpty()) {
            report.row("    —", "none");
        } else {
            rejects.forEach((reason, count) -> report.state("    " + reason, String.valueOf(count),
                    ChatPalette.WARN));
        }
        report.row("  sync corrections", data.getSyncCorrections());
    }
}
