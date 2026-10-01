package at.koopro.wizardsandbeasts.admin.debug;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The level of this mod's own loggers — everything under {@code at.koopro.wizardsandbeasts} — and nothing else. The
 * root logger, Minecraft's and every other mod's loggers keep whatever the server's logging configuration says;
 * raising this one to DEBUG never turns on debug output anywhere else.
 *
 * <p>Changed only through {@link DebugLeases}, which restores the level the server started with when the administrator
 * who changed it leaves. Restoring removes the logger entry again if the configuration had none, so the mod goes back to
 * inheriting from the root exactly as before.
 */
@NullMarked
public final class ModLogLevel {

    /** The logger every class of this mod logs under (they are named by class, so all share this prefix). */
    public static final String LOGGER = "at.koopro.wizardsandbeasts";

    public enum Choice {
        OFF(Level.OFF), ERRORS(Level.ERROR), WARNINGS(Level.WARN), INFO(Level.INFO), DEBUG(Level.DEBUG);

        private final Level level;

        Choice(Level level) {
            this.level = level;
        }

        public Level level() {
            return level;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static @Nullable Choice byId(String id) {
            for (Choice choice : values()) {
                if (choice.id().equals(id)) {
                    return choice;
                }
            }
            return null;
        }

        static Choice of(Level level) {
            if (level.isLessSpecificThan(Level.DEBUG)) {
                return DEBUG;
            }
            for (Choice choice : values()) {
                if (choice.level == level) {
                    return choice;
                }
            }
            return level.isMoreSpecificThan(Level.ERROR) ? (level == Level.OFF ? OFF : ERRORS) : INFO;
        }
    }

    /** What to put back: the level in effect, and whether the configuration had an entry of its own for the mod. */
    record Baseline(Level level, boolean ownEntry) {}

    private ModLogLevel() {}

    private static LoggerContext context() {
        return (LoggerContext) LogManager.getContext(false);
    }

    /** The level the mod's loggers log at now (inherited from the root unless an entry of its own exists). */
    public static Choice current() {
        return Choice.of(context().getConfiguration().getLoggerConfig(LOGGER).getLevel());
    }

    static Baseline baseline() {
        LoggerConfig config = context().getConfiguration().getLoggerConfig(LOGGER);
        return new Baseline(config.getLevel(), config.getName().equals(LOGGER));
    }

    static void set(Choice choice) {
        LoggerContext context = context();
        Configuration configuration = context.getConfiguration();
        LoggerConfig existing = configuration.getLoggerConfig(LOGGER);
        if (existing.getName().equals(LOGGER)) {
            existing.setLevel(choice.level());
        } else {
            // additive: messages still reach the root's appenders, exactly as before; only the level differs.
            LoggerConfig own = new LoggerConfig(LOGGER, choice.level(), true);
            own.setParent(existing);
            configuration.addLogger(LOGGER, own);
        }
        context.updateLoggers();
    }

    static void restore(Baseline baseline) {
        LoggerContext context = context();
        Configuration configuration = context.getConfiguration();
        if (baseline.ownEntry()) {
            LoggerConfig existing = configuration.getLoggerConfig(LOGGER);
            if (existing.getName().equals(LOGGER)) {
                existing.setLevel(baseline.level());
            } else {
                LoggerConfig own = new LoggerConfig(LOGGER, baseline.level(), true);
                own.setParent(existing);
                configuration.addLogger(LOGGER, own);
            }
        } else {
            configuration.removeLogger(LOGGER);
        }
        context.updateLoggers();
    }
}
