package at.koopro.wizardsandbeasts.settlement;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.data.ModuleStateData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.settlement.post.SettlementPostBlockEntity;
import at.koopro.wizardsandbeasts.settlement.structure.SettlementGround;
import at.koopro.wizardsandbeasts.settlement.structure.SettlementSite;
import at.koopro.wizardsandbeasts.settlement.structure.SettlementStructure;
import at.koopro.wizardsandbeasts.treasure.SiteSearch;
import at.koopro.wizardsandbeasts.treasure.TreasureCharts;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Checks hamlets the real world generator made, on a headless dedicated server: direct template placement proves
 * nothing about natural generation (lessons, 2026-10-31).
 *
 * <p>Development only, and inert unless {@code WB_SETTLEMENT_PROBE} is set. The {@code settlementProbe} run
 * configuration gives it its own game directory, so no save of the developer's is touched.
 * <ul>
 *   <li>{@code survey}: how often the placement grid yields a hamlet and what refused the rest, then generates up to
 *       {@code WB_SETTLEMENT_SITES} and reports what stands there, in {@code settlement_probe_report.txt} and a row per
 *       site in {@code settlement_probe_sites.tsv}.</li>
 *   <li>{@code restart}: run after {@code survey} on the same directory. The survey leaves one hamlet staffed and saved;
 *       this reports whether the same people came back, once each, and checks every site again from the save.</li>
 *   <li>{@code gate}: for a world whose config disagrees with the shipped defaults (the run script writes it). Reports
 *       what the module cache said when the first chunk arrived, and whether recipes followed the world's state.</li>
 *   <li>{@code retro-off}, then {@code retro-on}: an existing world. Given {@code settlement_probe_starts.txt} from a
 *       survey of the same seed and a world configured with the module off, explores around known sites and saves; the
 *       second run turns the module on and reports what appeared in land already explored, and at its edge.</li>
 * </ul>
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SettlementWorldProbe {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODE = FMLEnvironment.isProduction() ? null : System.getenv("WB_SETTLEMENT_PROBE");
    private static final Path REPORT = Path.of("settlement_probe_report.txt");
    private static final Path STATE = Path.of("settlement_probe_state.txt");
    /** One row per site. {@code RESTART_SITES} is the same sites, from what a restart reloaded. */
    private static final Path SITES = Path.of("settlement_probe_sites.tsv");
    private static final Path RESTART_SITES = Path.of("settlement_probe_restart.tsv");
    private static final Path STARTS = Path.of("settlement_probe_starts.txt");
    private static final Path RETRO_TABLE = Path.of("settlement_probe_retro.tsv");
    private static final ResourceKey<Structure> HAMLET = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wizarding_hamlet"));
    private static final TagKey<Structure> SETTLEMENTS = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "settlement"));
    private static final int SURVEY_RINGS = 12;
    /** Sites generated and checked in full; {@code WB_SETTLEMENT_SITES} takes a larger sample. */
    private static final int MAX_SITES = Integer.parseInt(System.getenv().getOrDefault("WB_SETTLEMENT_SITES", "10"));
    private static final int SETTLE_TICKS = 100;
    /** Long enough for forced chunks to load their entities, for every post's first look, and for spawns to settle. */
    private static final int STAFF_TICKS = 300;
    /** How far past the footprint a walk may go, and where it counts as having left the hamlet. */
    private static final int WALK_MARGIN = 10;
    /** Blocks between readings of a shut side's ground. */
    private static final int EDGE_STEP = 8;
    /** A shop's door to the cell a customer stands on across the counter, along the house. */
    private static final int DOOR_TO_COUNTER = 5;
    /** How far down a gap is followed; past this it is simply deep. */
    private static final int VOID_LOOK = 96;
    /** Past the fortress's placement radius and the biome snap around it. */
    private static final int AZKABAN_REACH = 200;
    /** Known sites an existing-world run explores around, half with the start chunk and half beside it. */
    private static final int RETRO_SITES = 16;
    /** Chunks explored around a known start, north and south of it. */
    private static final int RETRO_REACH = 4;
    /** A start is settled well past the land anyone has seen, so only a site this many chunks clear of it can answer. */
    private static final int RETRO_CLEAR = 16;
    private static final Map<Identifier, List<Block>> PALETTES = new HashMap<>();

    private static final List<String> lines = new ArrayList<>();
    private static final List<String> rows = new ArrayList<>();
    private static int settle = -1;
    private static BlockPos firstPlaza;
    private static BlockPos staffed = BlockPos.ZERO;
    private static boolean firstChunkSeen;
    private static ModuleState settlementsAtFirstChunk = ModuleState.DISABLED;
    private static ModuleState chamberAtFirstChunk = ModuleState.DISABLED;

    private SettlementWorldProbe() {}

    private static void say(String line) {
        lines.add(line);
        LOGGER.info("[SettlementProbe] {}", line);
    }

    private static void write() throws IOException {
        Files.write(REPORT, lines);
    }

    /** The cache as the first chunk saw it: a structure gate asks the same cache, at the same moment. */
    @SubscribeEvent
    public static void onChunk(ChunkEvent.Load event) {
        if (MODE != null && !firstChunkSeen && event.getLevel() instanceof ServerLevel) {
            firstChunkSeen = true;
            settlementsAtFirstChunk = ModuleManager.state(Module.SETTLEMENTS);
            chamberAtFirstChunk = ModuleManager.state(Module.CHAMBER_OF_SECRETS);
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (MODE == null) {
            return;
        }
        try {
            if (MODE.equals("gate") || MODE.startsWith("retro")) {
                settle = SETTLE_TICKS;
            } else if (MODE.equals("restart")) {
                staffed = BlockPos.of(Long.parseLong(Files.readAllLines(STATE).get(0)));
                settle = STAFF_TICKS;
                lines.addAll(Files.readAllLines(REPORT));
            } else {
                survey(event.getServer());
                write();
                settle = STAFF_TICKS;
            }
        } catch (IOException e) {
            LOGGER.error("Settlement probe failed", e);
            event.getServer().halt(false);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (MODE == null || settle < 0 || --settle > 0) {
            return;
        }
        settle = -1;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (MODE.startsWith("retro")) {
            try {
                retro(server, level);
            } catch (IOException e) {
                LOGGER.error("Settlement probe failed", e);
            }
            server.halt(false);
            return;
        }
        if (!MODE.equals("gate")) {
            try {
                people(server, level);
            } catch (IOException e) {
                LOGGER.error("Settlement probe failed", e);
            }
            server.halt(false);
            return;
        }
        ModuleStateData data = ModuleStateData.get(level);
        say("seed " + level.getSeed());
        say("first chunk saw: settlements " + settlementsAtFirstChunk + ", chamber " + chamberAtFirstChunk);
        say("world stores: settlements " + data.state(Module.SETTLEMENTS) + ", chamber "
                + data.state(Module.CHAMBER_OF_SECRETS) + ", structures " + data.state(Module.STRUCTURES));
        say("shipped: settlements " + at.koopro.wizardsandbeasts.module.ModuleDefaults.shipped(Module.SETTLEMENTS)
                + ", structures "
                + at.koopro.wizardsandbeasts.module.ModuleDefaults.shipped(Module.STRUCTURES));
        ResourceKey<Recipe<?>> gated = ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "diagon_brick"));
        say("structures-gated recipe loaded after start: " + server.getRecipeManager().byKey(gated).isPresent());
        int starts = 0;
        ChunkPos spawn = new ChunkPos(level.getRespawnData().pos());
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                ChunkAccess chunk = level.getChunk(spawn.x + x, spawn.z + z, ChunkStatus.STRUCTURE_STARTS);
                starts += chunk.getAllStarts().keySet().stream().filter(s -> level.registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE).getKey(s).getNamespace().equals(WizardsAndBeastsMod.MODID))
                        .count();
            }
        }
        say("mod structure starts within 8 chunks of spawn: " + starts);
        azkaban(level);
        try {
            write();
        } catch (IOException e) {
            LOGGER.error("Settlement probe failed", e);
        }
        server.halt(false);
    }

    /** Azkaban stands alone in its world: finds its one chunk the way generation does, and reports whether it starts there. */
    private static void azkaban(ServerLevel level) {
        Holder<Structure> fortress = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(
                Registries.STRUCTURE, Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "azkaban_fortress")));
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        for (StructurePlacement placement : state.getPlacementsForStructure(fortress)) {
            for (int x = -AZKABAN_REACH; x <= AZKABAN_REACH; x++) {
                for (int z = -AZKABAN_REACH; z <= AZKABAN_REACH; z++) {
                    if (placement.isStructureChunk(state, x, z)) {
                        ChunkAccess chunk = level.getChunk(x, z, ChunkStatus.STRUCTURE_STARTS);
                        StructureStart start = chunk.getStartForStructure(fortress.value());
                        say("azkaban: chunk " + x + ", " + z + ", started " + (start != null && start.isValid()) + ", pieces "
                                + (start == null ? 0 : start.getPieces().size()) + ", module "
                                + ModuleManager.state(Module.AZKABAN));
                        return;
                    }
                }
            }
        }
        say("azkaban: no chunk within reach");
    }

    // ── people ────────────────────────────────────────────────────────────

    /** Who stands in the staffed hamlet now. {@code survey} saves them and {@code restart} compares. */
    private static void people(MinecraftServer server, ServerLevel level) throws IOException {
        List<String> found = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, new AABB(staffed).inflate(64.0),
                e -> e.hasData(ModAttachments.SETTLEMENT_POST.get()))) {
            String role = entity instanceof at.koopro.wizardsandbeasts.settlement.npc.SettlementNpcEntity npc
                    ? npc.roleId() : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            found.add(entity.getUUID() + " " + role + " " + entity.getName().getString());
        }
        java.util.Collections.sort(found);
        if (MODE.equals("restart")) {
            List<String> saved = Files.readAllLines(STATE);
            saved = new ArrayList<>(saved.subList(1, saved.size()));
            say("--- restart: " + found.size() + " people, was " + saved.size() + ", same people " + found.equals(saved));
            found.forEach(line -> say("  " + line));
            reinspect(level);
        } else {
            say("staffed hamlet at " + staffed.toShortString() + ": " + found.size() + " people");
            found.forEach(line -> say("  " + line));
            List<String> state = new ArrayList<>(List.of(Long.toString(staffed.asLong())));
            state.addAll(found);
            Files.write(STATE, state);
            server.saveEverything(false, true, true);
        }
        write();
    }

    // ── survey ────────────────────────────────────────────────────────────

    private static void survey(MinecraftServer server) throws IOException {
        ServerLevel level = server.overworld();
        say("seed " + level.getSeed() + "; module " + ModuleManager.state(Module.SETTLEMENTS));
        Holder<Structure> hamlet = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(HAMLET);
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        StructureManager manager = level.structureManager();

        List<ChunkPos> candidates = new ArrayList<>();
        Map<String, Integer> verdicts = new TreeMap<>();
        int cells = 0;
        int spacing = 1;
        long assessing = 0;
        long slowest = 0;
        List<Long> weighed = new ArrayList<>();
        long before = System.nanoTime();
        for (StructurePlacement placement : state.getPlacementsForStructure(hamlet)) {
            if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
                continue;
            }
            spacing = spread.spacing();
            for (int x = -SURVEY_RINGS; x <= SURVEY_RINGS; x++) {
                for (int z = -SURVEY_RINGS; z <= SURVEY_RINGS; z++) {
                    cells++;
                    ChunkPos potential = spread.getPotentialStructureChunk(state.getLevelSeed(),
                            x * spread.spacing(), z * spread.spacing());
                    boolean placeable = spread.isStructureChunk(state, potential.x, potential.z);
                    long start = System.nanoTime();
                    SettlementSite.Verdict verdict = ((SettlementStructure) hamlet.value()).assess(context(level, potential)).verdict();
                    long taken = System.nanoTime() - start;
                    assessing += taken;
                    slowest = Math.max(slowest, taken);
                    if (verdict == SettlementSite.Verdict.STANDS || verdict == SettlementSite.Verdict.OCCUPIED) {
                        weighed.add(taken);
                    }
                    verdicts.merge(placeable ? verdict.name() : "VILLAGE_EXCLUSION", 1, Integer::sum);
                    if (placeable && manager.checkStructurePresence(potential, hamlet.value(), spread, false)
                            != StructureCheckResult.START_NOT_PRESENT) {
                        candidates.add(potential);
                    }
                }
            }
        }
        double millis = (System.nanoTime() - before) / 1e6;
        double yield = (double) candidates.size() / Math.max(1, cells);
        double blocksPerSite = spacing * 16 / Math.sqrt(Math.max(yield, 1e-9));
        say(String.format("survey: %d cells of %d blocks, %d passed (%.1f%%), %.0f ms; one hamlet per %.0f x %.0f blocks",
                cells, spacing * 16, candidates.size(), 100 * yield, millis, blocksPerSite, blocksPerSite));
        say(String.format("verdicts %s; assess mean %.1f ms, slowest %.1f ms", verdicts, assessing / 1e6 / Math.max(1, cells),
                slowest / 1e6));
        if (!weighed.isEmpty()) {
            long first = weighed.get(0);
            List<Long> sorted = weighed.stream().sorted().toList();
            say(String.format("cells that reach the other-structure check: %d, first %.1f ms, median %.1f ms, p95 %.1f ms, max %.1f ms",
                    sorted.size(), first / 1e6, sorted.get(sorted.size() / 2) / 1e6, sorted.get(sorted.size() * 95 / 100) / 1e6,
                    sorted.get(sorted.size() - 1) / 1e6));
        }

        BlockPos spawn = level.getRespawnData().pos();
        double nearestToSpawn = Double.MAX_VALUE;
        List<Double> neighbours = new ArrayList<>();
        Map<String, Integer> biomes = new TreeMap<>();
        var source = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        for (ChunkPos site : candidates) {
            nearestToSpawn = Math.min(nearestToSpawn, Math.hypot(site.getMiddleBlockX() - spawn.getX(),
                    site.getMiddleBlockZ() - spawn.getZ()));
            double nearest = Double.MAX_VALUE;
            for (ChunkPos other : candidates) {
                if (other != site) {
                    nearest = Math.min(nearest, Math.hypot(site.getMiddleBlockX() - other.getMiddleBlockX(),
                            site.getMiddleBlockZ() - other.getMiddleBlockZ()));
                }
            }
            neighbours.add(nearest);
            var biome = source.getNoiseBiome(QuartPos.fromBlock(site.getMiddleBlockX()), QuartPos.fromBlock(70),
                    QuartPos.fromBlock(site.getMiddleBlockZ()), sampler);
            biomes.merge(biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("?"), 1, Integer::sum);
        }
        neighbours.sort(Double::compare);
        say(String.format("nearest to spawn %.0f; neighbour distance min %.0f median %.0f max %.0f; biomes %s",
                nearestToSpawn, neighbours.isEmpty() ? 0 : neighbours.get(0),
                neighbours.isEmpty() ? 0 : neighbours.get(neighbours.size() / 2),
                neighbours.isEmpty() ? 0 : neighbours.get(neighbours.size() - 1), biomes));

        long search = System.nanoTime();
        SiteSearch.Site nearest = TreasureCharts.nearest(level, SETTLEMENTS, spawn, 16, s -> false);
        say(String.format("chart search from spawn: %s, %.0f ms", nearest, (System.nanoTime() - search) / 1e6));

        int inspected = 0;
        int clean = 0;
        List<String> kept = new ArrayList<>();
        for (ChunkPos start : candidates) {
            if (inspected >= MAX_SITES) {
                break;
            }
            ChunkAccess chunk = level.getChunk(start.x, start.z, ChunkStatus.STRUCTURE_STARTS);
            StructureStart structure = manager.getStartForStructure(SectionPos.bottomOf(chunk), hamlet.value(), chunk);
            if (structure == null || !structure.isValid()) {
                say("site " + start + ": no start despite the check");
                continue;
            }
            inspected++;
            kept.add(start.x + " " + start.z);
            if (inspect(level, structure)) {
                clean++;
            }
        }
        say(inspected + " inspected, " + clean + " clean");
        Files.write(STARTS, kept);
        Files.write(SITES, rows);
        if (firstPlaza != null) {
            staffed = firstPlaza;
            ChunkPos centre = new ChunkPos(staffed);
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    level.setChunkForced(centre.x + x, centre.z + z, true);
                }
            }
        }
    }

    private static Structure.GenerationContext context(ServerLevel level, ChunkPos chunk) {
        var generator = level.getChunkSource().getGenerator();
        return new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), chunk, level, biome -> true);
    }

    /** The sites {@code survey} checked, checked again from what a restart reloaded: nothing regenerates, so all must agree. */
    private static void reinspect(ServerLevel level) throws IOException {
        Structure hamlet = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValueOrThrow(HAMLET);
        rows.clear();
        int inspected = 0;
        int clean = 0;
        for (String line : Files.readAllLines(STARTS)) {
            String[] xz = line.split(" ");
            ChunkAccess chunk = level.getChunk(Integer.parseInt(xz[0]), Integer.parseInt(xz[1]), ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = level.structureManager().getStartForStructure(SectionPos.bottomOf(chunk), hamlet, chunk);
            if (start == null || !start.isValid()) {
                say("restart: no start at " + line);
                continue;
            }
            inspected++;
            clean += inspect(level, start) ? 1 : 0;
        }
        say("--- after restart: " + inspected + " inspected, " + clean + " clean");
        Files.write(RESTART_SITES, rows);
    }

    /** Whether a chunk already explored holds anything of a hamlet: a reference, or a start. */
    private static int hamletMarks(ServerLevel level, ChunkAccess chunk) {
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Structure hamlet = structures.getValueOrThrow(HAMLET);
        return chunk.getReferencesForStructure(hamlet).size() + (chunk.getStartForStructure(hamlet) == null ? 0 : 1);
    }

    /** See the class: odd sites have their start chunk explored while the module is off, even sites only land beside it. */
    private static void retro(MinecraftServer server, ServerLevel level) throws IOException {
        boolean on = MODE.equals("retro-on");
        say("seed " + level.getSeed() + "; " + MODE + "; module " + ModuleManager.state(Module.SETTLEMENTS));
        if (on) {
            at.koopro.wizardsandbeasts.module.ModuleStateService.setState(server, Module.SETTLEMENTS, ModuleState.PREVIEW);
            say("module now " + ModuleManager.state(Module.SETTLEMENTS));
        }
        Structure hamlet = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValueOrThrow(HAMLET);
        List<String> starts = Files.readAllLines(STARTS);
        int marks = 0;
        int backdated = 0;
        int cut = 0;
        int whole = 0;
        for (int i = 0; i < Math.min(starts.size(), RETRO_SITES); i++) {
            String[] xz = starts.get(i).split(" ");
            int cx = Integer.parseInt(xz[0]);
            int cz = Integer.parseInt(xz[1]);
            boolean startExplored = i % 2 == 1;
            int west = startExplored ? cx - RETRO_REACH : cx - RETRO_REACH - 1;
            int east = startExplored ? cx : cx - 1;
            for (int x = west; x <= east; x++) {
                for (int z = cz - RETRO_REACH; z <= cz + RETRO_REACH; z++) {
                    marks += hamletMarks(level, level.getChunk(x, z));
                }
            }
            if (!on) {
                continue;
            }
            ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = level.structureManager().getStartForStructure(SectionPos.bottomOf(chunk), hamlet, chunk);
            boolean stands = start != null && start.isValid();
            if (startExplored && stands) {
                backdated++;
            }
            if (stands) {
                boolean clean = inspect(level, start);
                whole += clean ? 1 : 0;
                cut += clean ? 0 : 1;
                say("  site " + starts.get(i) + (startExplored ? " start explored" : " start unexplored") + ": "
                        + (clean ? "whole" : "cut at the old land's edge"));
            } else {
                say("  site " + starts.get(i) + (startExplored ? " start explored" : " start unexplored") + ": none");
            }
        }
        say(MODE + ": hamlet marks in explored land " + marks + "; hamlets started in explored land " + backdated
                + "; beside it, whole " + whole + ", cut " + cut);
        if (on) {
            newLand(level, hamlet, starts);
        }
        if (on) {
            Files.write(RETRO_TABLE, rows);
        }
        server.saveEverything(false, true, true);
        Files.write(REPORT, lines);
    }

    /**
     * The other half of the proof: "nothing came back" means little unless a hamlet can still come at all. Sites clear of
     * everything the off run explored stood in a fresh world, so each must stand here too.
     */
    private static void newLand(ServerLevel level, Structure hamlet, List<String> starts) {
        int asked = 0;
        int stood = 0;
        int clean = 0;
        for (int i = RETRO_SITES; i < starts.size(); i++) {
            String[] xz = starts.get(i).split(" ");
            int cx = Integer.parseInt(xz[0]);
            int cz = Integer.parseInt(xz[1]);
            boolean clear = true;
            for (int j = 0; j < RETRO_SITES; j++) {
                String[] other = starts.get(j).split(" ");
                clear &= Math.max(Math.abs(cx - Integer.parseInt(other[0])), Math.abs(cz - Integer.parseInt(other[1])))
                        > RETRO_REACH + RETRO_CLEAR;
            }
            if (!clear) {
                continue;
            }
            asked++;
            ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = level.structureManager().getStartForStructure(SectionPos.bottomOf(chunk), hamlet, chunk);
            if (start != null && start.isValid()) {
                stood++;
                clean += inspect(level, start) ? 1 : 0;
            } else {
                say("  new land " + starts.get(i) + ": none");
            }
        }
        say(MODE + ": new land, sites clear of the explored " + asked + ", hamlets " + stood + ", clean " + clean);
    }

    /** The whole of a template but its air and its connectors, which are meant to go. */
    private static List<Block> palette(StructureTemplate template, Identifier location) {
        return PALETTES.computeIfAbsent(location, key -> BuiltInRegistries.BLOCK.stream()
                .filter(block -> block != Blocks.AIR && block != Blocks.JIGSAW)
                .filter(block -> !template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block).isEmpty())
                .toList());
    }

    private static BlockPos worldOf(PoolElementStructurePiece piece, StructurePlaceSettings settings, BlockPos relative) {
        return StructureTemplate.calculateRelativePosition(settings, relative).offset(piece.getPosition());
    }

    /** The way into a shop: the door, both sides of it, and where a customer stands to speak across the counter. */
    private record Entrance(String role, BlockPos door, BlockPos outside, BlockPos inside, BlockPos customer) {}

    /** Generates one hamlet's chunks in full and reports what stands there. True when nothing is wrong with it. */
    private static boolean inspect(ServerLevel level, StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        long loading = System.nanoTime();
        for (int cx = SectionPos.blockToSectionCoord(box.minX()) - 1; cx <= SectionPos.blockToSectionCoord(box.maxX()) + 1; cx++) {
            for (int cz = SectionPos.blockToSectionCoord(box.minZ()) - 1; cz <= SectionPos.blockToSectionCoord(box.maxZ()) + 1; cz++) {
                level.getChunk(cx, cz);
            }
        }
        long loadMillis = (System.nanoTime() - loading) / 1_000_000;
        List<String> roles = new ArrayList<>();
        int placed = 0;
        int changed = 0;
        int cellarChanged = 0;
        int airPlaced = 0;
        int intruded = 0;
        int indoors = 0;
        int trunks = 0;
        BlockPos firstChanged = null;
        Map<String, Integer> became = new TreeMap<>();
        List<Entrance> entrances = new ArrayList<>();
        Set<BlockPos> meantAir = new HashSet<>();
        Set<BlockPos> cellarBottom = new HashSet<>();
        Set<String> posts = new java.util.TreeSet<>();
        PoolElementStructurePiece plaza = null;
        Map<String, Integer> rotations = new TreeMap<>();
        for (StructurePiece raw : start.getPieces()) {
            if (!(raw instanceof PoolElementStructurePiece piece) || !(piece.getElement() instanceof SinglePoolElement element)) {
                continue;
            }
            Identifier location = element.getTemplateLocation();
            String role = SettlementSite.roleOf(location.getPath());
            roles.add(role);
            rotations.merge(piece.getRotation().name(), 1, Integer::sum);
            if (role.equals("plaza")) {
                plaza = piece;
            }
            StructureTemplate template = level.getStructureManager().getOrCreate(location);
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.getRotation());
            StructurePlaceSettings plain = new StructurePlaceSettings();
            int doorLayer = 0;
            for (var door : template.filterBlocks(BlockPos.ZERO, plain, Blocks.SPRUCE_DOOR)) {
                if (door.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                    BlockPos at = door.pos();
                    doorLayer = at.getY();
                    entrances.add(new Entrance(role, worldOf(piece, settings, at), worldOf(piece, settings, at.south()),
                            worldOf(piece, settings, at.north()), worldOf(piece, settings, at.north(DOOR_TO_COUNTER))));
                }
            }
            for (Block block : palette(template, location)) {
                for (var info : template.filterBlocks(BlockPos.ZERO, plain, block)) {
                    BlockPos at = worldOf(piece, settings, info.pos());
                    placed++;
                    if (block == ModBlocks.SETTLEMENT_POST.get()
                            && level.getBlockEntity(at) instanceof SettlementPostBlockEntity post) {
                        posts.add(post.role());
                    }
                    BlockState now = level.getBlockState(at);
                    if (!now.is(block)) {
                        changed++;
                        cellarChanged += info.pos().getY() < doorLayer - 1 ? 1 : 0;
                        if (firstChanged == null) {
                            firstChanged = at;
                        }
                        became.merge(BuiltInRegistries.BLOCK.getKey(block).getPath() + "->"
                                + BuiltInRegistries.BLOCK.getKey(now.getBlock()).getPath(), 1, Integer::sum);
                    }
                    if (doorLayer > 1 && info.pos().getY() == 0) {
                        cellarBottom.add(at);
                    }
                }
            }
            for (var info : template.filterBlocks(BlockPos.ZERO, plain, Blocks.AIR)) {
                BlockPos at = worldOf(piece, settings, info.pos());
                airPlaced++;
                meantAir.add(at);
                BlockState now = level.getBlockState(at);
                if (!now.isAir()) {
                    intruded++;
                    trunks += now.is(net.minecraft.tags.BlockTags.LOGS) ? 1 : 0;
                    BlockPos rel = info.pos();
                    if (!role.equals("plaza") && rel.getX() >= 1 && rel.getX() <= 9 && rel.getZ() >= 2 && rel.getZ() <= 11
                            && rel.getY() >= doorLayer && rel.getY() <= doorLayer + 5) {
                        indoors++;
                    }
                    became.merge("air->" + BuiltInRegistries.BLOCK.getKey(now.getBlock()).getPath(), 1, Integer::sum);
                }
            }
        }
        if (plaza == null) {
            say("site " + start.getChunkPos() + ": NO PLAZA " + roles);
            return false;
        }
        int floorY = plaza.getBoundingBox().minY();
        if (firstPlaza == null) {
            firstPlaza = plaza.getBoundingBox().getCenter();
        }
        List<BoundingBox> pieces = start.getPieces().stream().map(StructurePiece::getBoundingBox).toList();
        int collisions = 0;
        for (int i = 0; i < pieces.size(); i++) {
            for (int j = i + 1; j < pieces.size(); j++) {
                collisions += pieces.get(i).intersects(pieces.get(j)) ? 1 : 0;
            }
        }
        Ground ground = ground(level, start, floorY, meantAir, cellarBottom);
        Walk walk = walk(level, plaza.getBoundingBox(), box, floorY, entrances);
        Map<String, Integer> clashes = clashes(level, box, pieces);
        int rooms = monsterRooms(level, box, pieces);
        boolean rolesWhole = SettlementSite.complete(roles);
        boolean clean = rolesWhole && posts.size() == 5 && changed == 0 && collisions == 0 && clashes.isEmpty()
                && ground.floating == 0 && ground.cellarFloating == 0 && indoors == 0
                && walk.counters == entrances.size() && walk.doors == entrances.size() && walk.sides == 4;
        String plazaTurn = plaza.getRotation().name();
        String biome = level.getBiome(plaza.getBoundingBox().getCenter()).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
        say("site " + start.getChunkPos() + " plaza " + plazaTurn + " box " + box.getXSpan() + "x" + box.getZSpan() + "x" + box.getYSpan()
                + " floor y " + floorY + " biome " + biome + ": pieces " + roles + " rotations " + rotations + ", " + loadMillis + " ms");
        say("  blocks " + placed + " placed, " + changed + " changed; air " + airPlaced + " placed, " + intruded
                + " intruded " + became);
        if (firstChanged != null) {
            say("  first changed block at " + firstChanged.toShortString() + ": " + nearby(level, firstChanged));
        }
        say("  ground " + ground + "; walk " + walk + "; entrances " + entrances.size() + "; roles " + posts.size()
                + "; rooms by a piece " + rooms + (clashes.isEmpty() ? "" : "; clashes " + clashes) + (clean ? "" : "  <-- NOT CLEAN"));
        if (rows.isEmpty()) {
            rows.add(String.join("\t", "seed", "cx", "cz", "plaza", "biome", "floor", "ms", "pieces", "posts", "placed", "changed",
                    "cellarChanged", "collisions", "floating", "cellarFloating", "masonry", "deepest", "hanging", "voidColumns",
                    "deepestVoid", "doors", "counters", "entrances", "sides", "indoors", "trunks", "intruded", "rooms", "clashes",
                    "clean"));
        }
        rows.add(String.join("\t", Long.toString(level.getSeed()), Integer.toString(start.getChunkPos().x),
                Integer.toString(start.getChunkPos().z), plazaTurn, biome, Integer.toString(floorY), Long.toString(loadMillis),
                Integer.toString(roles.size()), Integer.toString(posts.size()), Integer.toString(placed), Integer.toString(changed),
                Integer.toString(cellarChanged), Integer.toString(collisions), Integer.toString(ground.floating),
                Integer.toString(ground.cellarFloating), Integer.toString(ground.masonry), Integer.toString(ground.deepest),
                Integer.toString(ground.hanging), Integer.toString(ground.voidColumns), Integer.toString(ground.deepestVoid),
                Integer.toString(walk.doors), Integer.toString(walk.counters), Integer.toString(entrances.size()),
                Integer.toString(walk.sides), Integer.toString(indoors), Integer.toString(trunks), Integer.toString(intruded),
                Integer.toString(rooms), clashes.toString(), Boolean.toString(clean)));
        return clean;
    }

    /** Other structures with a piece that truly meets one of the hamlet's, by volume. A shared chunk is no collision. */
    private static Map<String, Integer> clashes(ServerLevel level, BoundingBox box, List<BoundingBox> pieces) {
        Map<String, Integer> found = new TreeMap<>();
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Set<StructureStart> others = new HashSet<>();
        for (int x = box.minX(); x <= box.maxX() + 15; x += 16) {
            for (int z = box.minZ(); z <= box.maxZ() + 15; z += 16) {
                for (var entry : level.structureManager().getAllStructuresAt(new BlockPos(x, 64, z)).entrySet()) {
                    level.structureManager().fillStartsForStructure(entry.getKey(), entry.getValue(), others::add);
                }
            }
        }
        for (StructureStart other : others) {
            Identifier id = structures.getKey(other.getStructure());
            if (id == null || id.equals(HAMLET.identifier())) {
                continue;
            }
            for (StructurePiece piece : other.getPieces()) {
                for (BoundingBox mine : pieces) {
                    BoundingBox theirs = piece.getBoundingBox();
                    if (mine.intersects(theirs)) {
                        int dx = Math.min(mine.maxX(), theirs.maxX()) - Math.max(mine.minX(), theirs.minX()) + 1;
                        int dy = Math.min(mine.maxY(), theirs.maxY()) - Math.max(mine.minY(), theirs.minY()) + 1;
                        int dz = Math.min(mine.maxZ(), theirs.maxZ()) - Math.max(mine.minZ(), theirs.minZ()) + 1;
                        found.merge(id.getPath(), dx * dy * dz, Integer::sum);
                    }
                }
            }
        }
        return found;
    }

    /** Spawners within a monster room's reach of a piece: one here is a room a wall could have been opened by. */
    private static int monsterRooms(ServerLevel level, BoundingBox box, List<BoundingBox> pieces) {
        int rooms = 0;
        for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
                for (var entity : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (entity instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity
                            && pieces.stream().anyMatch(piece -> piece.inflatedBy(5).isInside(entity.getBlockPos()))) {
                        rooms++;
                    }
                }
            }
        }
        return rooms;
    }

    /** What a breach is made of: a spawner or dungeon cobble within eight blocks names a vanilla monster room. */
    private static String nearby(ServerLevel level, BlockPos at) {
        int spawners = 0;
        int mossy = 0;
        BlockPos spawner = null;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-8, -6, -8), at.offset(8, 6, 8))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.SPAWNER)) {
                spawners++;
                spawner = pos.immutable();
            } else if (state.is(Blocks.MOSSY_COBBLESTONE)) {
                mossy++;
            }
        }
        return spawners + " spawner(s)" + (spawner == null ? "" : " at " + spawner.toShortString()) + ", " + mossy + " mossy cobble";
    }

    private record Ground(int floating, int cellarFloating, int above, int below, int steep, int sampled, int masonry, int deepest,
                          int hanging, int voidColumns, int deepestVoid) {
        @Override
        public String toString() {
            return "floor over nothing " + floating + ", cellar over nothing " + cellarFloating + ", masonry " + masonry
                    + " deepest " + deepest + " hanging " + hanging + ", void columns " + voidColumns + " deepest " + deepestVoid
                    + ", edge terrain +" + above + "/-" + below + " of floor, over 3 off " + steep + "/" + sampled;
        }
    }

    private static boolean masonry(BlockState state) {
        return state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE);
    }

    /** One column below a floor: {masonry cells, void cells past it, capped}. */
    private static int[] beneath(ServerLevel level, int x, int top, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top - 1, z);
        int built = 0;
        while (masonry(level.getBlockState(pos))) {
            built++;
            pos.move(Direction.DOWN);
        }
        int gap = 0;
        while (gap < VOID_LOOK && pos.getY() > level.getMinY() && SettlementGround.open(level.getBlockState(pos))) {
            gap++;
            pos.move(Direction.DOWN);
        }
        return new int[] {built, gap};
    }

    /** How the land meets each piece: floors and cellars with nothing under them, masonry, and terrain just outside each edge. */
    private static Ground ground(ServerLevel level, StructureStart start, int floorY, Set<BlockPos> meantAir, Set<BlockPos> cellarBottom) {
        int floating = 0;
        int cellarFloating = 0;
        int above = 0;
        int below = 0;
        int steep = 0;
        int sampled = 0;
        int masonry = 0;
        int deepest = 0;
        int hanging = 0;
        int voidColumns = 0;
        int deepestVoid = 0;
        List<BoundingBox> boxes = start.getPieces().stream().map(StructurePiece::getBoundingBox).toList();
        for (BoundingBox piece : boxes) {
            for (int x = piece.minX(); x <= piece.maxX(); x++) {
                for (int z = piece.minZ(); z <= piece.maxZ(); z++) {
                    BlockPos floor = new BlockPos(x, floorY, z);
                    BlockPos under = floor.below();
                    if (!level.getBlockState(floor).isAir() && !meantAir.contains(under)) {
                        floating += SettlementGround.open(level.getBlockState(under)) ? 1 : 0;
                        int[] column = beneath(level, x, floorY, z);
                        masonry += column[0];
                        deepest = Math.max(deepest, column[0]);
                        hanging += column[0] > 0 && column[1] > 0 ? 1 : 0;
                        voidColumns += column[0] + column[1] > 0 ? 1 : 0;
                        deepestVoid = Math.max(deepestVoid, column[0] + column[1]);
                    }
                    boolean edge = x == piece.minX() || x == piece.maxX() || z == piece.minZ() || z == piece.maxZ();
                    if (!edge) {
                        continue;
                    }
                    int ox = x == piece.minX() ? x - 2 : x == piece.maxX() ? x + 2 : x;
                    int oz = z == piece.minZ() ? z - 2 : z == piece.maxZ() ? z + 2 : z;
                    final int fx = ox;
                    final int fz = oz;
                    if (boxes.stream().anyMatch(b -> b.minX() <= fx && fx <= b.maxX() && b.minZ() <= fz && fz <= b.maxZ())) {
                        continue;
                    }
                    int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ox, oz) - 1;
                    sampled++;
                    above = Math.max(above, h - floorY);
                    below = Math.max(below, floorY - h);
                    if (Math.abs(h - floorY) > 3) {
                        steep++;
                    }
                }
            }
        }
        for (BlockPos bottom : cellarBottom) {
            cellarFloating += SettlementGround.open(level.getBlockState(bottom.below())) ? 1 : 0;
            int[] column = beneath(level, bottom.getX(), bottom.getY(), bottom.getZ());
            masonry += column[0];
            deepest = Math.max(deepest, column[0]);
            hanging += column[0] > 0 && column[1] > 0 ? 1 : 0;
            voidColumns += column[0] + column[1] > 0 ? 1 : 0;
            deepestVoid = Math.max(deepestVoid, column[0] + column[1]);
        }
        return new Ground(floating, cellarFloating, above, below, steep, sampled, masonry, deepest, hanging, voidColumns, deepestVoid);
    }

    private record Walk(int cells, int doors, int counters, int sides, String shut) {
        @Override
        public String toString() {
            return cells + " cells, doors through " + doors + ", counters reached " + counters + ", sides left by " + sides + "/4"
                    + (shut.isEmpty() ? "" : ", shut" + shut);
        }
    }

    /**
     * Walks as a player does, one block up or down and through doors, from a corner of the plaza. Every door should open
     * both ways, every counter should have its customer's cell, and the walker should be able to leave the footprint on
     * all four sides: a hamlet in a pit or on a shelf fails here.
     */
    private static Walk walk(ServerLevel level, BoundingBox plaza, BoundingBox box, int floorY, List<Entrance> entrances) {
        BlockPos from = new BlockPos(plaza.minX() + 3, floorY + 1, plaza.minZ() + 3);
        BoundingBox limit = box.inflatedBy(WALK_MARGIN);
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = pos.relative(direction).above(dy);
                    if (limit.isInside(next) && !seen.contains(next) && standable(level, next)) {
                        seen.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        int doors = 0;
        int counters = 0;
        for (Entrance entrance : entrances) {
            doors += seen.contains(entrance.door) && seen.contains(entrance.outside) && seen.contains(entrance.inside) ? 1 : 0;
            counters += seen.contains(entrance.customer) ? 1 : 0;
        }
        boolean west = false;
        boolean east = false;
        boolean north = false;
        boolean south = false;
        for (BlockPos pos : seen) {
            west |= pos.getX() < box.minX() - WALK_MARGIN / 2;
            east |= pos.getX() > box.maxX() + WALK_MARGIN / 2;
            north |= pos.getZ() < box.minZ() - WALK_MARGIN / 2;
            south |= pos.getZ() > box.maxZ() + WALK_MARGIN / 2;
        }
        String shut = (west ? "" : " west") + (east ? "" : " east") + (north ? "" : " north") + (south ? "" : " south");
        return new Walk(seen.size(), doors, counters, (west ? 1 : 0) + (east ? 1 : 0) + (north ? 1 : 0) + (south ? 1 : 0),
                shut.isEmpty() ? "" : shut + " " + edge(level, box, floorY, !west, !east, !north, !south));
    }

    /** What stops the walker on a shut side: the ground along that edge of the walk's limit, against the floor. */
    private static String edge(ServerLevel level, BoundingBox box, int floorY, boolean west, boolean east, boolean north, boolean south) {
        Map<String, Integer> ground = new TreeMap<>();
        int outer = WALK_MARGIN / 2 + 1;
        for (int i = box.minX(); i <= box.maxX(); i += EDGE_STEP) {
            if (north) {
                ground.merge(surface(level, i, box.minZ() - outer, floorY), 1, Integer::sum);
            }
            if (south) {
                ground.merge(surface(level, i, box.maxZ() + outer, floorY), 1, Integer::sum);
            }
        }
        for (int i = box.minZ(); i <= box.maxZ(); i += EDGE_STEP) {
            if (west) {
                ground.merge(surface(level, box.minX() - outer, i, floorY), 1, Integer::sum);
            }
            if (east) {
                ground.merge(surface(level, box.maxX() + outer, i, floorY), 1, Integer::sum);
            }
        }
        return ground.toString();
    }

    private static String surface(ServerLevel level, int x, int z, int floorY) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock()).getPath() + "@" + (y - floorY);
    }

    /** No way through for a person, except a door, which they open. */
    private static boolean barred(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty() && !state.is(net.minecraft.tags.BlockTags.DOORS);
    }

    private static boolean standable(ServerLevel level, BlockPos pos) {
        return !barred(level, pos) && !barred(level, pos.above())
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }
}
