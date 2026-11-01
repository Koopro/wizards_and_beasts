package at.koopro.wizardsandbeasts.settlement;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.module.ModuleState;
import at.koopro.wizardsandbeasts.module.data.ModuleStateData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.settlement.structure.SettlementSite;
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
import net.minecraft.world.level.block.state.BlockState;
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
 *   <li>{@code survey}: how often the placement grid yields a hamlet, where and in which biomes, then generates up to
 *       ten and reports what stands there. Results go to {@code settlement_probe_report.txt}.</li>
 *   <li>{@code restart}: run after {@code survey} on the same directory. The survey leaves one hamlet staffed and saved;
 *       this reports whether the same people came back, once each.</li>
 *   <li>{@code gate}: for a world whose config disagrees with the shipped defaults (the run script writes it). Reports
 *       what the module cache said when the first chunk arrived, and whether recipes followed the world's state.</li>
 * </ul>
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SettlementWorldProbe {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODE = FMLEnvironment.isProduction() ? null : System.getenv("WB_SETTLEMENT_PROBE");
    private static final Path REPORT = Path.of("settlement_probe_report.txt");
    private static final Path STATE = Path.of("settlement_probe_state.txt");
    private static final ResourceKey<Structure> HAMLET = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "wizarding_hamlet"));
    private static final TagKey<Structure> SETTLEMENTS = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "settlement"));
    private static final int SURVEY_RINGS = 12;
    private static final int MAX_SITES = 10;
    private static final int SETTLE_TICKS = 100;
    /** Long enough for forced chunks to load their entities, for every post's first look, and for spawns to settle. */
    private static final int STAFF_TICKS = 300;
    /** How far past the footprint a walk may go, and where it counts as having left the hamlet. */
    private static final int WALK_MARGIN = 10;
    /** Floor cells over a cave or ravine a site may have and still count as clean: carvers run after any site check. */
    private static final int FLOATING_ALLOWED = 130;
    /** Blocks whose template positions are compared with what stands there: every wall, floor, roof and working part. */
    private static final List<String> CHECKED = List.of("stone_bricks", "deepslate_bricks", "deepslate_tiles",
            "spruce_planks", "gravel", "bookshelf", "wizards_and_beasts:hogsmeade_stone_bricks",
            "wizards_and_beasts:hogsmeade_worn_stone", "wizards_and_beasts:hogsmeade_roof_tile",
            "wizards_and_beasts:three_broomsticks_timber", "wizards_and_beasts:three_broomsticks_planks",
            "wizards_and_beasts:diagon_cobblestone", "wizards_and_beasts:diagon_street_stone",
            "wizards_and_beasts:gringotts_white_marble", "wizards_and_beasts:settlement_post",
            "wizards_and_beasts:chamber_seal", "wizards_and_beasts:wandmakers_bench",
            "wizards_and_beasts:examination_desk", "wizards_and_beasts:spell_teacher", "minecraft:chest");

    private static final List<String> lines = new ArrayList<>();
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
            if (MODE.equals("gate")) {
                settle = SETTLE_TICKS;
            } else if (MODE.equals("restart")) {
                staffed = BlockPos.of(Long.parseLong(Files.readAllLines(STATE).get(0)));
                settle = STAFF_TICKS;
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
        try {
            write();
        } catch (IOException e) {
            LOGGER.error("Settlement probe failed", e);
        }
        server.halt(false);
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
            lines.addAll(Files.readAllLines(REPORT));
            say("--- restart: " + found.size() + " people, was " + saved.size() + ", same people " + found.equals(saved));
            found.forEach(line -> say("  " + line));
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

    private static void survey(MinecraftServer server) {
        ServerLevel level = server.overworld();
        say("seed " + level.getSeed() + "; module " + ModuleManager.state(Module.SETTLEMENTS));
        Holder<Structure> hamlet = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(HAMLET);
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        StructureManager manager = level.structureManager();

        List<ChunkPos> candidates = new ArrayList<>();
        int cells = 0;
        int spacing = 1;
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
                    if (spread.isStructureChunk(state, potential.x, potential.z)
                            && manager.checkStructurePresence(potential, hamlet.value(), spread, false)
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

        relief(level, hamlet, state);

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
            if (inspect(level, structure)) {
                clean++;
            }
        }
        say(inspected + " inspected, " + clean + " clean");
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

    /** How the rule's relief limit would change the yield, over the same grid, for the biomes the hamlet accepts. */
    private static void relief(ServerLevel level, Holder<Structure> hamlet, ChunkGeneratorStructureState state) {
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        int cells = 0;
        int listed = 0;
        int[] limits = {6, 8, 10, 12, 14, 16, 20};
        int[] passing = new int[limits.length];
        int[] dry = new int[limits.length];
        int[] drier = new int[limits.length];
        int[] free = new int[limits.length];
        for (StructurePlacement placement : state.getPlacementsForStructure(hamlet)) {
            if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
                continue;
            }
            for (int cx = -SURVEY_RINGS; cx <= SURVEY_RINGS; cx++) {
                for (int cz = -SURVEY_RINGS; cz <= SURVEY_RINGS; cz++) {
                    cells++;
                    ChunkPos potential = spread.getPotentialStructureChunk(state.getLevelSeed(),
                            cx * spread.spacing(), cz * spread.spacing());
                    int x = potential.getMiddleBlockX();
                    int z = potential.getMiddleBlockZ();
                    int surface = generator.getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
                    var biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface),
                            QuartPos.fromBlock(z), random.sampler());
                    if (!hamlet.value().biomes().contains(biome)) {
                        continue;
                    }
                    listed++;
                    int lowest = surface;
                    int highest = surface;
                    int wet = 0;
                    boolean wetCentre = !generator.getBaseColumn(x, z, level, random).getBlock(surface - 1).getFluidState().isEmpty();
                    for (int[] sample : SettlementSite.SAMPLES) {
                        int h = generator.getFirstFreeHeight(x + sample[0], z + sample[1], Heightmap.Types.WORLD_SURFACE_WG, level, random);
                        lowest = Math.min(lowest, h);
                        highest = Math.max(highest, h);
                        if (!generator.getBaseColumn(x + sample[0], z + sample[1], level, random).getBlock(h - 1).getFluidState().isEmpty()) {
                            wet++;
                        }
                    }
                    wet += wetCentre ? 1 : 0;
                    boolean placeable = spread.isStructureChunk(state, potential.x, potential.z);
                    for (int i = 0; i < limits.length; i++) {
                        if (highest - lowest <= limits[i]) {
                            passing[i]++;
                            if (wet == 0) {
                                dry[i]++;
                            }
                            if (!wetCentre && wet <= 1) {
                                drier[i]++;
                            }
                            if (wet == 0 && placeable) {
                                free[i]++;
                            }
                        }
                    }
                }
            }
        }
        StringBuilder line = new StringBuilder("relief: " + cells + " cells, biome listed " + listed + ";");
        for (int i = 0; i < limits.length; i++) {
            line.append(" <=").append(limits[i]).append(": ").append(passing[i]);
        }
        say(line.toString());
        say("  with no sampled water: " + java.util.Arrays.toString(dry) + "; at most one wet corner: "
                + java.util.Arrays.toString(drier) + "; dry and outside the village exclusion: "
                + java.util.Arrays.toString(free));
    }

    /** Generates one hamlet's chunks in full and reports what stands there. True when nothing is wrong with it. */
    private static boolean inspect(ServerLevel level, StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        for (int cx = SectionPos.blockToSectionCoord(box.minX()) - 1; cx <= SectionPos.blockToSectionCoord(box.maxX()) + 1; cx++) {
            for (int cz = SectionPos.blockToSectionCoord(box.minZ()) - 1; cz <= SectionPos.blockToSectionCoord(box.maxZ()) + 1; cz++) {
                level.getChunk(cx, cz);
            }
        }
        List<String> roles = new ArrayList<>();
        int placed = 0;
        int changed = 0;
        int airPlaced = 0;
        int intruded = 0;
        int trunks = 0;
        Map<String, Integer> became = new TreeMap<>();
        List<BlockPos> counters = new ArrayList<>();
        Set<BlockPos> meantAir = new HashSet<>();
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
            for (String name : CHECKED) {
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(name));
                for (var info : template.filterBlocks(piece.getPosition(), settings, block)) {
                    placed++;
                    if (block == at.koopro.wizardsandbeasts.registry.ModBlocks.SETTLEMENT_POST.get()) {
                        counters.add(info.pos());
                        if (level.getBlockEntity(info.pos()) instanceof
                                at.koopro.wizardsandbeasts.settlement.post.SettlementPostBlockEntity post) {
                            posts.add(post.role());
                        }
                    }
                    BlockState now = level.getBlockState(info.pos());
                    if (!now.is(block)) {
                        changed++;
                        became.merge(BuiltInRegistries.BLOCK.getKey(block).getPath() + "->"
                                + BuiltInRegistries.BLOCK.getKey(now.getBlock()).getPath(), 1, Integer::sum);
                    }
                }
            }
            for (var info : template.filterBlocks(piece.getPosition(), settings, Blocks.AIR)) {
                airPlaced++;
                meantAir.add(info.pos());
                BlockState now = level.getBlockState(info.pos());
                if (!now.isAir()) {
                    intruded++;
                    trunks += now.is(net.minecraft.tags.BlockTags.LOGS) ? 1 : 0;
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
        Ground ground = ground(level, start, floorY, meantAir);
        Walk walk = walk(level, plaza.getBoundingBox(), box, floorY, counters);
        Map<String, Integer> others = new TreeMap<>();
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (int x = box.minX(); x <= box.maxX(); x += 16) {
            for (int z = box.minZ(); z <= box.maxZ(); z += 16) {
                for (Structure structure : level.structureManager().getAllStructuresAt(new BlockPos(x, 64, z)).keySet()) {
                    Identifier id = structures.getKey(structure);
                    if (id != null && !id.equals(HAMLET.identifier())) {
                        others.merge(id.toString(), 1, Integer::sum);
                    }
                }
            }
        }
        boolean rolesWhole = SettlementSite.complete(roles);
        boolean clean = rolesWhole && posts.size() == 5 && changed == 0 && ground.floating <= FLOATING_ALLOWED && trunks == 0
                && walk.counters == counters.size() && walk.sides == 4 && !others.keySet().toString().contains("village");
        say("site " + start.getChunkPos() + " box " + box.getXSpan() + "x" + box.getZSpan() + "x" + box.getYSpan()
                + " floor y " + floorY + " biome " + level.getBiome(plaza.getBoundingBox().getCenter()).unwrapKey()
                .map(k -> k.identifier().getPath()).orElse("?") + ": pieces " + roles + " rotations " + rotations);
        say("  blocks " + placed + " placed, " + changed + " changed; air " + airPlaced + " placed, " + intruded
                + " intruded " + became);
        say("  ground " + ground + "; walk " + walk + "; counters " + counters.size()
                + "; roles " + posts.size() + (others.isEmpty() ? "" : "; overlaps " + others)
                + (clean ? "" : "  <-- NOT CLEAN"));
        return clean;
    }

    private record Ground(int floating, int above, int below, int steep, int sampled) {
        @Override
        public String toString() {
            return "floor over nothing " + floating + ", edge terrain +" + above + "/-" + below + " of floor, over 3 off "
                    + steep + "/" + sampled;
        }
    }

    /** How the land meets each piece: floor cells with nothing under them, and the terrain just outside each edge. */
    private static Ground ground(ServerLevel level, StructureStart start, int floorY, Set<BlockPos> meantAir) {
        int floating = 0;
        int above = 0;
        int below = 0;
        int steep = 0;
        int sampled = 0;
        List<BoundingBox> boxes = start.getPieces().stream().map(StructurePiece::getBoundingBox).toList();
        for (BoundingBox piece : boxes) {
            for (int x = piece.minX(); x <= piece.maxX(); x++) {
                for (int z = piece.minZ(); z <= piece.maxZ(); z++) {
                    BlockPos floor = new BlockPos(x, floorY, z);
                    BlockPos under = floor.below();
                    if (!level.getBlockState(floor).isAir() && !meantAir.contains(under)
                            && (level.getBlockState(under).isAir() || !level.getFluidState(under).isEmpty())) {
                        floating++;
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
        return new Ground(floating, above, below, steep, sampled);
    }

    private record Walk(int cells, int counters, int sides) {
        @Override
        public String toString() {
            return cells + " cells, counters reached " + counters + ", sides left by " + sides + "/4";
        }
    }

    /**
     * Walks as a player does, one block up or down, from a corner of the plaza. Every counter should be reached, and the
     * walker should be able to leave the footprint on all four sides: a hamlet in a pit or on a shelf fails here.
     */
    private static Walk walk(ServerLevel level, BoundingBox plaza, BoundingBox box, int floorY, List<BlockPos> counters) {
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
        int reached = 0;
        for (BlockPos counter : counters) {
            // A post is under a person behind a counter: reached is near enough to speak to them.
            boolean near = false;
            for (BlockPos cell : BlockPos.betweenClosed(counter.offset(-4, 0, -4), counter.offset(4, 2, 4))) {
                near |= seen.contains(cell);
            }
            reached += near ? 1 : 0;
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
        return new Walk(seen.size(), reached, (west ? 1 : 0) + (east ? 1 : 0) + (north ? 1 : 0) + (south ? 1 : 0));
    }

    private static boolean standable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }
}
