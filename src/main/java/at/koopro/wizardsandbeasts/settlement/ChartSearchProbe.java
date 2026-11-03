package at.koopro.wizardsandbeasts.settlement;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.settlement.structure.SettlementGround;
import at.koopro.wizardsandbeasts.treasure.SiteSearch;
import at.koopro.wizardsandbeasts.treasure.TreasureCharts;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What a chart's search costs the server: a {@link SiteSearch.Walk} from each of a ring of origins out in land nothing has
 * asked about, advanced by the tick loop {@link HamletCharts} uses, on a running server. It reads the gap between ticks, so a
 * stall counts whichever call it came from, and at 20 ticks a second a gap of 50 ms is no stall at all.
 *
 * <p>Development only, inert unless {@code WB_CHART_PROBE} is set, on the {@code settlementProbe} run. Results go to
 * {@code chart_probe_report.txt}, one row a search and a total.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ChartSearchProbe {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ACTIVE = !FMLEnvironment.isProduction() && System.getenv("WB_CHART_PROBE") != null;
    private static final Path REPORT = Path.of("chart_probe_report.txt");
    private static final long BUDGET_NANOS = 3_000_000L;
    private static final int ORIGINS = 16;
    private static final int ORIGIN_RADIUS = 12_000;
    /** A gap past this is a server a player can feel: a tenth of a second is two whole ticks. */
    private static final long STALL_NANOS = 100_000_000L;
    /** Lets the world settle first, so nothing of the server's own start is counted. */
    private static final int WARM_UP_TICKS = 100;
    /** The control: this many ticks of nothing, so a stall the machine causes on its own is not blamed on a search. */
    private static final int IDLE_TICKS = 1_200;

    private static final List<BlockPos> ORIGIN_LIST = new ArrayList<>();
    private static final List<String> LINES = new ArrayList<>();

    private static int next;
    private static int warmUp = WARM_UP_TICKS;
    private static SiteSearch.Walk walk;
    private static long began;
    private static long lastTick;
    private static long worstGap;
    private static long searchTicks;
    private static long stalls;
    private static long overallWorst;
    private static long overallStalls;
    private static long overallTicks;
    private static long overallMillis;
    private static int found;
    private static int idleLeft = IDLE_TICKS;
    private static long idleWorst;
    private static long idleStalls;

    private ChartSearchProbe() {}

    private static void say(String line) {
        LOGGER.info("[ChartProbe] {}", line);
        LINES.add(line);
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (!ACTIVE) {
            return;
        }
        BlockPos spawn = event.getServer().overworld().getRespawnData().pos();
        for (int i = 0; i < ORIGINS; i++) {
            double angle = 2 * Math.PI * i / ORIGINS;
            ORIGIN_LIST.add(spawn.offset((int) (Math.cos(angle) * ORIGIN_RADIUS), 0, (int) (Math.sin(angle) * ORIGIN_RADIUS)));
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Pre event) {
        if (!ACTIVE || ORIGIN_LIST.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        if (walk != null) {
            long gap = now - lastTick;
            worstGap = Math.max(worstGap, gap);
            stalls += gap > STALL_NANOS ? 1 : 0;
            searchTicks++;
        } else if (warmUp <= 0 && idleLeft > 0 && lastTick != 0) {
            long gap = now - lastTick;
            idleWorst = Math.max(idleWorst, gap);
            idleStalls += gap > STALL_NANOS ? 1 : 0;
            idleLeft--;
        }
        lastTick = now;
    }

    @SubscribeEvent
    public static void onTickPost(ServerTickEvent.Post event) {
        if (!ACTIVE || ORIGIN_LIST.isEmpty() || --warmUp > 0 || idleLeft > 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (walk == null) {
            if (next >= ORIGIN_LIST.size()) {
                finish(server);
                return;
            }
            walk = TreasureCharts.walk(level, SettlementGround.SETTLEMENTS, ORIGIN_LIST.get(next), HamletCharts.SEARCH_RINGS);
            began = System.nanoTime();
            worstGap = 0;
            searchTicks = 0;
            stalls = 0;
            return;
        }
        HamletCharts.advance(walk, System.nanoTime() + BUDGET_NANOS);
        if (walk.finished()) {
            long millis = (System.nanoTime() - began) / 1_000_000;
            SiteSearch.Site site = walk.found();
            boolean real = site == null || level.getChunk(site.startX(), site.startZ(), ChunkStatus.STRUCTURE_STARTS).getAllStarts().keySet().stream()
                    .anyMatch(s -> level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                            .wrapAsHolder(s).is(SettlementGround.SETTLEMENTS));
            say(String.format("%s\t%d ms\t%d ticks\tworst gap %.0f ms\tstalls %d\t%s\t%s", ORIGIN_LIST.get(next).toShortString(), millis,
                    searchTicks, worstGap / 1e6, stalls, site == null ? "none" : site.startX() + "," + site.startZ(), real ? "real" : "NOT A HAMLET"));
            overallWorst = Math.max(overallWorst, worstGap);
            overallStalls += stalls;
            overallTicks += searchTicks;
            overallMillis += millis;
            found += site == null ? 0 : 1;
            next++;
            walk = null;
        }
    }

    private static void finish(MinecraftServer server) {
        say(String.format("IDLE control: %d ticks with no search, worst gap %.0f ms, %d stalled", IDLE_TICKS, idleWorst / 1e6, idleStalls));
        say(String.format("TOTAL %d searches, %d found, %d ms mean, worst gap %.0f ms, %d of %d ticks stalled", ORIGIN_LIST.size(), found,
                overallMillis / ORIGIN_LIST.size(), overallWorst / 1e6, overallStalls, overallTicks));
        try {
            Files.write(REPORT, LINES);
        } catch (IOException e) {
            LOGGER.error("Chart probe: cannot write the report", e);
        }
        ORIGIN_LIST.clear();
        server.halt(false);
    }
}
