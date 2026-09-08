package at.koopro.wizardsandbeasts.command.debug.dev;

import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Every dev kit, and the three verbs that run them.
 *
 * <p>Registration order is run order for {@code all} and {@code setup}, and it matters more here
 * than it does for the read-only sections: heritage first, because half the other subsystems refuse
 * to do anything for a player who has not been through the selection ceremony, and modules before
 * anything that a disabled module would silently swallow.
 */
@NullMarked
public final class FeatureDevKits {

    private static final Map<String, FeatureDevKit> KITS = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private FeatureDevKits() {}

    public static void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        // Switches first: a disabled module swallows everything below it, and opening a gate inside
        // one would report success for a feature that still cannot run.
        register(new at.koopro.wizardsandbeasts.module.debug.ModuleDevKit());
        // Then who the player is, because the rest is gated on having been through selection.
        register(new at.koopro.wizardsandbeasts.heritage.debug.HeritageDevKit());
        register(new at.koopro.wizardsandbeasts.stats.debug.StatsDevKit());
        // Then what they can do.
        register(new at.koopro.wizardsandbeasts.spell.debug.SpellDevKit());
        register(new at.koopro.wizardsandbeasts.wand.debug.WandDevKit());
        register(new at.koopro.wizardsandbeasts.skill.debug.SkillDevKit());
        register(new at.koopro.wizardsandbeasts.ability.debug.AbilityDevKit());
        // Then the world they do it in.
        register(new at.koopro.wizardsandbeasts.ministry.debug.MinistryDevKit());
        register(new at.koopro.wizardsandbeasts.currency.debug.GringottsDevKit());
        register(new at.koopro.wizardsandbeasts.floo.debug.FlooDevKit());
        // Then the things they carry, ride and brew with.
        register(new at.koopro.wizardsandbeasts.brew.debug.BrewDevKit());
        register(new at.koopro.wizardsandbeasts.entity.debug.BroomDevKit());
        register(new at.koopro.wizardsandbeasts.item.armor.debug.ArmorDevKit());
        register(new at.koopro.wizardsandbeasts.bestiary.debug.BestiaryDevKit());
    }

    public static void register(FeatureDevKit kit) {
        KITS.put(kit.id(), kit);
    }

    public static List<FeatureDevKit> all() {
        bootstrap();
        return List.copyOf(KITS.values());
    }

    public static List<String> ids() {
        bootstrap();
        return new ArrayList<>(KITS.keySet());
    }

    public static @Nullable FeatureDevKit byId(String id) {
        bootstrap();
        return KITS.get(id);
    }

    /** One kit, one verb. */
    public static DebugReport run(FeatureDevKit kit, ServerPlayer target, Verb verb) {
        DebugReport report = DebugReport.of(verb.title + " " + kit.title()
                + " for " + target.getName().getString());
        DevLog log = new DevLog(report);
        apply(kit, target, verb, log);
        summarise(report, log);
        return report;
    }

    /**
     * Every kit, one verb.
     *
     * <p>A kit that throws is reported and the run continues. Setting up a test world is the moment
     * you least want a half-applied state and no idea which half — and these run against whatever
     * the player's data happens to be, which during development is routinely something no code path
     * expected.
     */
    public static DebugReport runAll(ServerPlayer target, Verb verb) {
        bootstrap();
        DebugReport report = DebugReport.of(verb.title + " everything for "
                + target.getName().getString());
        DevLog log = new DevLog(report);
        for (FeatureDevKit kit : KITS.values()) {
            report.section(kit.title());
            try {
                apply(kit, target, verb, log);
            } catch (RuntimeException ex) {
                log.warn("kit '" + kit.id() + "' threw: " + ex);
            }
        }
        summarise(report, log);
        return report;
    }

    /**
     * Open everything, then hand over everything: one command to become a test wizard.
     *
     * <p>Deliberately not a third verb on {@link Verb}. It is {@code open all} followed by
     * {@code kit all}, in that order because a kit for a gated feature is items you cannot use.
     */
    public static DebugReport setup(ServerPlayer target) {
        bootstrap();
        DebugReport report = DebugReport.of("Dev setup for " + target.getName().getString());
        DevLog log = new DevLog(report);
        for (Verb verb : new Verb[]{Verb.OPEN, Verb.KIT}) {
            report.section(verb.title);
            for (FeatureDevKit kit : KITS.values()) {
                try {
                    apply(kit, target, verb, log);
                } catch (RuntimeException ex) {
                    log.warn("kit '" + kit.id() + "' threw during " + verb.literal + ": " + ex);
                }
            }
        }
        summarise(report, log);
        return report;
    }

    private static void apply(FeatureDevKit kit, ServerPlayer target, Verb verb, DevLog log) {
        verb.action.accept(kit, new Target(target, log));
    }

    private static void summarise(DebugReport report, DevLog log) {
        report.section("result");
        report.row("  changed", log.changes());
        report.row("  skipped", log.skips());
    }

    /** Paired player and log, so {@link Verb} can hold a two-argument method reference. */
    private record Target(ServerPlayer player, DevLog log) {}

    public enum Verb {
        OPEN("open", "Open", (kit, t) -> kit.open(t.player(), t.log())),
        KIT("kit", "Kit", (kit, t) -> kit.kit(t.player(), t.log())),
        RESET("reset", "Reset", (kit, t) -> kit.reset(t.player(), t.log()));

        private final String literal;
        private final String title;
        private final BiConsumer<FeatureDevKit, Target> action;

        Verb(String literal, String title, BiConsumer<FeatureDevKit, Target> action) {
            this.literal = literal;
            this.title = title;
            this.action = action;
        }

        public String literal() {
            return literal;
        }
    }
}
