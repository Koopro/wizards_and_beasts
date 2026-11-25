package at.koopro.wizardsandbeasts.education;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.prophet.DailyProphet;
import at.koopro.wizardsandbeasts.prophet.ProphetNews;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Exam week: the server-wide window in which the examination desk seats candidates (systems audit D2, 2026-11-25).
 *
 * <p>Canon: O.W.L.s are sat by the whole year together, over a fortnight in June, before Ministry examiners who
 * arrive at the castle for them (OotP ch.31, Professor Marchbanks); nobody sits one on a private timetable. Here the
 * year is a cycle of {@link Rules#cycleDays} in-game days (14 by default, {@code exam_week/rules.json}). The Daily
 * Prophet announces the sitting {@link Rules#noticeDays} days ahead and again on its first day; the desk then seats
 * candidates for {@link Rules#windowDays} days. The personal resit wait ({@link ExamRules#RESIT_INTERVAL_TICKS})
 * still applies inside the window, so a wizard sits once per exam week.
 *
 * <p>Days are the overworld's day count, the same day the Prophet dates its issues by. Within each cycle the
 * notice comes first, then the window: days {@code [notice, notice + window)} of the cycle are open.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ExamWeek {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL = 100;
    private static final String STORY = "prophet.wizards_and_beasts.story.";

    /** The datapack's calendar. */
    public record Rules(int cycleDays, int windowDays, int noticeDays) {
        public static final Rules DEFAULT = new Rules(14, 3, 3);
        /** Every day open: for scenarios about the sitting, not the calendar. */
        public static final Rules ALWAYS = new Rules(1, 1, 0);
        public static final Codec<Rules> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 3650).optionalFieldOf("cycle_days", 14).forGetter(Rules::cycleDays),
                Codec.intRange(1, 3650).optionalFieldOf("window_days", 3).forGetter(Rules::windowDays),
                Codec.intRange(0, 3650).optionalFieldOf("notice_days", 3).forGetter(Rules::noticeDays)
        ).apply(i, Rules::new));

        public Rules {
            // The window and its notice must fit in one cycle; a pack that asks for more is trimmed, not refused.
            windowDays = Math.max(1, Math.min(windowDays, cycleDays));
            noticeDays = Math.max(0, Math.min(noticeDays, cycleDays - windowDays));
        }
    }

    private static volatile Rules rules = Rules.DEFAULT;

    private ExamWeek() {}

    public static Rules rules() {
        return rules;
    }

    /** Replaces the calendar (reload, tests). */
    public static void setRules(Rules loaded) {
        rules = loaded;
    }

    /** Whether the desk seats candidates on {@code day}. */
    public static boolean isOpen(long day) {
        return isOpen(rules, day);
    }

    static boolean isOpen(Rules r, long day) {
        long inCycle = Math.floorMod(day, (long) r.cycleDays());
        return inCycle >= r.noticeDays() && inCycle < r.noticeDays() + r.windowDays();
    }

    /** The first day of the next window that has not yet begun on {@code day} (later than {@code day}). */
    public static long nextOpening(long day) {
        return nextOpening(rules, day);
    }

    static long nextOpening(Rules r, long day) {
        long cycleStart = Math.floorDiv(day, (long) r.cycleDays()) * r.cycleDays();
        long opening = cycleStart + r.noticeDays();
        return opening > day ? opening : opening + r.cycleDays();
    }

    /** Whether the desk seats candidates now on this server. */
    public static boolean isOpen(MinecraftServer server) {
        return isOpen(DailyProphet.day(server));
    }

    /** Game time at which the desk next opens, or 0 when it is open now. */
    public static long nextOpeningTick(MinecraftServer server) {
        long day = DailyProphet.day(server);
        return isOpen(day) ? 0L : nextOpening(day) * 24000L;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL != 0 || !ModuleManager.isEnabled(Module.OWLS)) {
            return;
        }
        announce(server, DailyProphet.day(server));
    }

    /**
     * Prints the day's exam-week story, once: the notice {@link Rules#noticeDays} days ahead and the opening on the
     * first day. A notice of zero days means the opening is the only story.
     */
    static void announce(MinecraftServer server, long day) {
        Rules r = rules;
        long inCycle = Math.floorMod(day, (long) r.cycleDays());
        if (r.noticeDays() > 0 && inCycle == 0) {
            ProphetNews.get(server).reportOnce(day, STORY + "exam_week_notice", Integer.toString(r.noticeDays()));
        } else if (inCycle == r.noticeDays()) {
            ProphetNews.get(server).reportOnce(day, STORY + "exam_week_open", Integer.toString(r.windowDays()));
        }
    }

    /** Loads {@code exam_week/rules.json}; a pack without it keeps the fortnightly default. */
    public static final class Loader extends SimpleJsonResourceReloadListener<Rules> {

        private static final Identifier RULES = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "rules");

        public Loader() {
            super(Rules.CODEC, FileToIdConverter.json("exam_week"));
        }

        @Override
        protected void apply(Map<Identifier, Rules> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
            setRules(entries.getOrDefault(RULES, Rules.DEFAULT));
            LOGGER.info("[ExamWeek] every {} day(s), {} day(s) open, {} day(s) notice",
                    rules.cycleDays(), rules.windowDays(), rules.noticeDays());
        }
    }
}
