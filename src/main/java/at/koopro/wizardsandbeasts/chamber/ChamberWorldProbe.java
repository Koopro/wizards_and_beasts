package at.koopro.wizardsandbeasts.chamber;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.treasure.SiteSearch;
import at.koopro.wizardsandbeasts.treasure.TreasureCharts;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
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
import java.util.Map;
import java.util.TreeMap;

/**
 * Checks Chambers the real world generator made, on a headless dedicated server. Direct template placement proves nothing
 * about natural generation, and the game-test world is flat, so this is the only automatic look at real terrain.
 *
 * <p>Development only, and inert unless {@code WB_CHAMBER_PROBE} is set. The {@code chamberProbe} run configuration
 * gives it its own game directory ({@code build/chamber-probe}), so no save of the developer's is touched.
 * <ul>
 *   <li>{@code generate}: surveys how often the placement grid yields a Chamber, generates each one found and reports what
 *       it holds, its rotation and how it met the terrain. It then opens one Chamber's seal and fills its basin, wakes
 *       its basilisk, kills another's, and shuts the server down.</li>
 *   <li>{@code restart}: run after {@code generate} on the same directory. Reports what came through the restart.</li>
 * </ul>
 * Results go to {@code chamber_probe_report.txt}.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ChamberWorldProbe {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODE = FMLEnvironment.isProduction() ? null : System.getenv("WB_CHAMBER_PROBE");
    private static final Path REPORT = Path.of("chamber_probe_report.txt");
    private static final Path STATE = Path.of("chamber_probe_state.txt");
    private static final int SURVEY_RINGS = 15;
    private static final int MAX_SITES = 10;
    private static final int SETTLE_TICKS = 100;
    /** Collision-free cells reachable from the landing in the pristine template with its doors shut. */
    private static final int SHUT_WALK = 426;

    private static final List<String> lines = new ArrayList<>();
    private static BlockPos worked = BlockPos.ZERO;
    private static BlockPos workedGate = BlockPos.ZERO;
    private static BlockPos workedSeal = BlockPos.ZERO;
    private static BlockPos cleared = BlockPos.ZERO;
    private static int settle = -1;

    private ChamberWorldProbe() {}

    private record Found(ChunkPos start, BlockPos heart, BlockPos seal, BlockPos gate, BlockPos basin) {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (MODE == null) {
            return;
        }
        try {
            if (MODE.equals("restart")) {
                restart(event.getServer());
            } else {
                generate(event.getServer());
            }
        } catch (IOException e) {
            LOGGER.error("Chamber probe failed", e);
            event.getServer().halt(false);
        }
    }

    private static void say(String line) {
        lines.add(line);
        LOGGER.info("[ChamberProbe] {}", line);
    }

    private static void write(boolean append) throws IOException {
        if (append) {
            Files.write(REPORT, lines, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } else {
            Files.write(REPORT, lines);
        }
    }

    // ── generate ──────────────────────────────────────────────────────────

    private static void generate(MinecraftServer server) throws IOException {
        ServerLevel level = server.overworld();
        say("seed " + level.getSeed() + "; module " + ModuleManager.state(Module.CHAMBER_OF_SECRETS));
        Holder<Structure> chamber = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(ChamberBasilisk.CHAMBER);
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        StructureManager manager = level.structureManager();

        List<ChunkPos> candidates = new ArrayList<>();
        int cells = 0;
        long before = System.nanoTime();
        for (StructurePlacement placement : state.getPlacementsForStructure(chamber)) {
            if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
                continue;
            }
            for (int x = -SURVEY_RINGS; x <= SURVEY_RINGS; x++) {
                for (int z = -SURVEY_RINGS; z <= SURVEY_RINGS; z++) {
                    cells++;
                    ChunkPos potential = spread.getPotentialStructureChunk(state.getLevelSeed(),
                            x * spread.spacing(), z * spread.spacing());
                    if (manager.checkStructurePresence(potential, chamber.value(), spread, false)
                            != StructureCheckResult.START_NOT_PRESENT) {
                        candidates.add(potential);
                    }
                }
            }
        }
        say(String.format("survey: %d grid cells, %d passed the biome and height checks (%.1f%%), %.0f ms", cells,
                candidates.size(), 100.0 * candidates.size() / Math.max(1, cells), (System.nanoTime() - before) / 1e6));

        long search = System.nanoTime();
        SiteSearch.Site nearest = TreasureCharts.nearest(level, ChamberCharts.CHAMBERS, level.getRespawnData().pos(),
                ChamberCharts.SEARCH_RINGS, s -> false);
        say(String.format("chart search from spawn: %s, %.0f ms", nearest, (System.nanoTime() - search) / 1e6));

        List<Found> found = new ArrayList<>();
        for (ChunkPos start : candidates) {
            if (found.size() >= MAX_SITES) {
                break;
            }
            ChunkAccess chunk = level.getChunk(start.x, start.z, ChunkStatus.STRUCTURE_STARTS);
            StructureStart structure = manager.getStartForStructure(SectionPos.bottomOf(chunk), chamber.value(), chunk);
            if (structure == null || !structure.isValid()) {
                say("site " + start + ": no start despite the check");
                continue;
            }
            Found site = inspect(level, structure);
            if (site != null) {
                found.add(site);
            }
        }
        if (found.size() < 2) {
            say("too few complete Chambers to work on");
            write(false);
            server.halt(false);
            return;
        }

        Found first = found.get(0);
        Found second = found.get(1);
        forceAround(level, first.heart(), true);
        forceAround(level, second.heart(), true);
        BlockState seal = level.getBlockState(first.seal());
        level.setBlockAndUpdate(first.seal(), seal.cycle(ChamberSealBlock.OPEN));
        say("seal opened by Alohomora's rule; partner block open: " + sealsOpen(level, first.seal()));
        level.setBlockAndUpdate(first.basin(), Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3));
        worked = first.heart();
        workedGate = first.gate();
        workedSeal = first.seal();
        cleared = second.heart();
        heart(level, worked).wake(level, level.getBlockState(worked), List.of());
        say("woke basilisk at " + worked + ": " + heart(level, worked).phase());
        ChamberHeartBlockEntity other = heart(level, cleared);
        LivingEntity snake = other.wake(level, level.getBlockState(cleared), List.of());
        if (snake != null) {
            snake.hurtServer(level, level.damageSources().generic(), 100_000f);
        }
        boolean slain = ChamberBasilisk.Data.get(level).isSlain(second.start());
        say("killed basilisk at " + cleared + ": " + other.phase() + "; recorded against its start: " + slain);
        SiteSearch.Site past = TreasureCharts.nearest(level, ChamberCharts.CHAMBERS, cleared, ChamberCharts.SEARCH_RINGS,
                s -> ChamberBasilisk.Data.get(level).isSlain(new ChunkPos(s.startX(), s.startZ())));
        say("chart search from the cleared Chamber passes over it: " + (past != null
                && !(past.startX() == second.start().x && past.startZ() == second.start().z)) + " -> " + past);
        write(false);
        settle = SETTLE_TICKS;
    }

    private static boolean sealsOpen(ServerLevel level, BlockPos seal) {
        for (BlockPos member : ChamberSeals.group(level, seal, ModBlocks.CHAMBER_SEAL.get())) {
            if (!level.getBlockState(member).getValue(ChamberSealBlock.OPEN)) {
                return false;
            }
        }
        return true;
    }

    /** Generates one Chamber's chunks in full and reports what stands there. Null when a working part is missing. */
    private static Found inspect(ServerLevel level, StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
                level.getChunk(cx, cz);
            }
        }
        Map<String, Integer> count = new TreeMap<>();
        BlockPos heart = null;
        BlockPos seal = null;
        BlockPos gate = null;
        BlockPos basin = null;
        BlockPos hatch = null;
        int signsWorded = 0;
        int chestsWithLoot = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.CHAMBER_HEART.get())) {
                heart = pos.immutable();
            } else if (state.is(ModBlocks.CHAMBER_SEAL.get())) {
                seal = pos.immutable();
            } else if (state.is(ModBlocks.CHAMBER_GATE.get())) {
                gate = pos.immutable();
            } else if (state.is(Blocks.CAULDRON)) {
                basin = pos.immutable();
            } else if (state.is(Blocks.SPRUCE_TRAPDOOR)) {
                hatch = pos.immutable();
            } else if (state.is(Blocks.LAVA)) {
                count.merge("lava", 1, Integer::sum);
            } else if (!(state.is(ModBlocks.CHAMBER_HEART.get())) && !state.is(Blocks.COMPARATOR)
                    && !state.is(Blocks.IRON_DOOR) && !state.is(Blocks.LADDER) && !state.is(Blocks.LEVER)
                    && !state.is(Blocks.CHEST) && !state.is(Blocks.SPRUCE_WALL_SIGN)) {
                continue;
            }
            count.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), 1, Integer::sum);
            if (level.getBlockEntity(pos) instanceof SignBlockEntity sign
                    && sign.getFrontText().getMessage(0, false).getContents()
                    instanceof net.minecraft.network.chat.contents.TranslatableContents) {
                signsWorded++;
            } else if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest && chest.getLootTable() != null) {
                chestsWithLoot++;
            }
        }
        String where = "site " + start.getChunkPos() + " box " + box;
        if (heart == null || seal == null || gate == null || basin == null || hatch == null) {
            say(where + ": INCOMPLETE " + count);
            return null;
        }
        Direction facing = level.getBlockState(heart).getValue(HorizontalDirectionalBlock.FACING);
        boolean heartEntity = level.getBlockEntity(heart) instanceof ChamberHeartBlockEntity;
        BlockPos spawn = BlockPos.containing(ChamberHeartBlockEntity.spawnPoint(heart, facing));
        boolean spawnOpen = level.getBlockState(spawn).isAir() && level.getBlockState(spawn.above()).isAir()
                && !level.getBlockState(spawn.below()).isAir();
        AABB hall = ChamberHeartBlockEntity.hall(heart, facing);
        int surfaceHigh = Integer.MIN_VALUE;
        int surfaceLow = Integer.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx += 3) {
            for (int dz = -3; dz <= 3; dz += 3) {
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, hatch.getX() + dx, hatch.getZ() + dz);
                surfaceHigh = Math.max(surfaceHigh, h);
                surfaceLow = Math.min(surfaceLow, h);
            }
        }
        int wet = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (!level.getFluidState(hatch.offset(dx, 0, dz)).isEmpty()) {
                    wet++;
                }
            }
        }
        int exposed = 0;
        int columns = 0;
        int leastCover = Integer.MAX_VALUE;
        int roof = (int) hall.maxY;
        for (int x = (int) hall.minX; x < hall.maxX; x += 2) {
            for (int z = (int) hall.minZ; z < hall.maxZ; z += 2) {
                int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                columns++;
                leastCover = Math.min(leastCover, top - roof);
                if (top <= roof) {
                    exposed++;
                }
            }
        }
        Map<String, Integer> others = new TreeMap<>();
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (int x = box.minX(); x <= box.maxX(); x += 16) {
            for (int z = box.minZ(); z <= box.maxZ(); z += 16) {
                for (Structure structure : level.structureManager().getAllStructuresAt(new BlockPos(x, 64, z)).keySet()) {
                    Identifier id = structures.getKey(structure);
                    if (id != null && !id.equals(ChamberBasilisk.CHAMBER.identifier())) {
                        others.merge(id.toString(), 1, Integer::sum);
                    }
                }
            }
        }
        say(where + ": facing " + facing + ", hatch y " + hatch.getY() + ", biome "
                + level.getBiome(hatch).unwrapKey().map(k -> k.identifier().getPath()).orElse("?")
                + "; ground around the ring " + surfaceLow + ".." + surfaceHigh + ", wet cells " + wet + "; above hatch "
                + BuiltInRegistries.BLOCK.getKey(level.getBlockState(hatch.above()).getBlock()).getPath()
                + "; hall roof y " + roof + ", least cover " + leastCover + ", exposed columns " + exposed + "/" + columns
                + "; heart entity " + heartEntity + ", spawn open " + spawnOpen + ", signs worded " + signsWorded
                + ", chests with loot " + chestsWithLoot + "; " + count + (others.isEmpty() ? "" : "; overlaps " + others));
        say("  walk from the landing with seal and gate shut: " + walk(level, heart, facing, box));
        say("  walls: " + walls(level, start));
        return new Found(start.getChunkPos(), heart, seal, gate, basin);
    }

    /**
     * Floods the open cells from the landing, inside the Chamber's box, with its doors shut. In the template that is
     * {@link #SHUT_WALK} cells and the basin room and hall are out of reach: more cells means a cave or mineshaft broke
     * through a wall, and reaching either room means a puzzle can be walked past.
     */
    private static String walk(ServerLevel level, BlockPos heart, Direction facing, BoundingBox box) {
        Direction right = facing.getClockWise();
        // Heart-relative, in the template's north-facing frame: heart (32, 8, 37).
        BlockPos landing = at(heart, facing, right, 15, -10, -1);
        BlockPos basinRoom = at(heart, facing, right, 32, 5, -1);
        BlockPos hall = at(heart, facing, right, 12, 0, -1);
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(landing);
        seen.add(landing);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.contains(next) && box.isInside(next)
                        && level.getBlockState(next).getCollisionShape(level, next).isEmpty()) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return seen.size() + " cells (template " + SHUT_WALK + "), basin room reached " + seen.contains(basinRoom)
                + ", hall reached " + seen.contains(hall);
    }

    /** Every wall, floor and ceiling block the template placed, compared with what stands there now. */
    private static String walls(ServerLevel level, StructureStart start) {
        if (!(start.getPieces().get(0) instanceof PoolElementStructurePiece piece)) {
            return "not a pool piece";
        }
        StructureTemplate template = level.getStructureManager().getOrCreate(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "chamber_of_secrets/chamber"));
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.getRotation());
        int total = 0;
        int changed = 0;
        Map<String, Integer> became = new TreeMap<>();
        List<String> samples = new ArrayList<>();
        for (Block wall : List.of(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
                Blocks.POLISHED_ANDESITE, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES,
                Blocks.POLISHED_DEEPSLATE)) {
            for (var info : template.filterBlocks(piece.getPosition(), settings, wall)) {
                total++;
                BlockState now = level.getBlockState(info.pos());
                if (!now.is(wall)) {
                    changed++;
                    String id = BuiltInRegistries.BLOCK.getKey(now.getBlock()).getPath();
                    became.merge(id, 1, Integer::sum);
                    if (samples.size() < 6 && now.getCollisionShape(level, info.pos()).isEmpty()) {
                        samples.add(info.pos().toShortString() + " was " + BuiltInRegistries.BLOCK.getKey(wall).getPath());
                    }
                }
            }
        }
        return total + " placed, " + changed + " changed " + became + (samples.isEmpty() ? "" : " e.g. " + samples);
    }

    private static BlockPos at(BlockPos heart, Direction forward, Direction right, int ahead, int aside, int up) {
        return heart.relative(forward, ahead).relative(right, aside).above(up);
    }

    private static void forceAround(ServerLevel level, BlockPos pos, boolean on) {
        ChunkPos centre = new ChunkPos(pos);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                level.setChunkForced(centre.x + x, centre.z + z, on);
            }
        }
    }

    private static ChamberHeartBlockEntity heart(ServerLevel level, BlockPos pos) {
        return (ChamberHeartBlockEntity) level.getBlockEntity(pos);
    }

    // ── the settle, then the shutdown or the second report ────────────────

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (MODE == null || settle < 0 || --settle > 0) {
            return;
        }
        settle = -1;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        try {
            if (MODE.equals("restart")) {
                reportRestart(level, "after " + SETTLE_TICKS + " ticks");
                write(true);
            } else {
                say("after settling: gate raised " + level.getBlockState(workedGate).getValue(ChamberGateBlock.POWERED));
                write(false);
                Files.write(STATE, List.of(worked.asLong() + "", workedGate.asLong() + "", workedSeal.asLong() + "",
                        cleared.asLong() + ""));
                server.saveEverything(false, true, true);
            }
        } catch (IOException e) {
            LOGGER.error("Chamber probe failed", e);
        }
        server.halt(false);
    }

    private static void restart(MinecraftServer server) throws IOException {
        List<String> state = Files.readAllLines(STATE);
        worked = BlockPos.of(Long.parseLong(state.get(0)));
        workedGate = BlockPos.of(Long.parseLong(state.get(1)));
        workedSeal = BlockPos.of(Long.parseLong(state.get(2)));
        cleared = BlockPos.of(Long.parseLong(state.get(3)));
        ServerLevel level = server.overworld();
        say("--- restart");
        reportRestart(level, "at start");
        settle = SETTLE_TICKS;
    }

    private static void reportRestart(ServerLevel level, String when) {
        ChamberHeartBlockEntity awake = heart(level, worked);
        AABB around = ChamberHeartBlockEntity.hall(worked, ChamberHeartBlockEntity.facing(level.getBlockState(worked)))
                .inflate(8.0);
        List<LivingEntity> bound = level.getEntitiesOfClass(LivingEntity.class, around,
                e -> e.hasData(ModAttachments.CHAMBER_HEART.get()));
        boolean persistent = !bound.isEmpty() && bound.get(0) instanceof Mob mob && mob.isPersistenceRequired();
        say(when + ": seal open " + sealsOpen(level, workedSeal) + ", gate raised "
                + level.getBlockState(workedGate).getValue(ChamberGateBlock.POWERED) + ", heart " + awake.phase()
                + ", bound basilisks " + bound.size() + ", persistent " + persistent + ", same as the heart's "
                + (!bound.isEmpty() && bound.get(0).getUUID().equals(awake.basilisk()))
                + ", cleared Chamber " + heart(level, cleared).phase());
    }
}
