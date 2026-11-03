package at.koopro.wizardsandbeasts.settlement.structure;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Keeps what vegetation brings out of a hamlet's rooms.
 *
 * <p>Trees and mushrooms are decorated after a hamlet is placed, and a tree's crown does not know a wall from a clearing:
 * it fills any air it reaches, and a room is air. No site rule can stop that, and nothing placed earlier can either, since a
 * neighbouring chunk's tree may arrive last. So it is undone once a chunk turns full: by then all eight neighbours have
 * finished their features, which write one chunk out and no further, so nothing more can arrive.
 *
 * <p>A room is a cell its template leaves as air with something of the template above it. Open ground is left alone: a
 * branch over a yard is a tree, and cutting it flat along a box would look worse than the branch.
 *
 * <p>The same pass dries what a template does not hold water in. The hamlet's structure file ignores waterlogging, but a
 * start saved before that setting arrived keeps vanilla's default in its pieces (a piece stores the setting only when it
 * is not the default), so a cellar in a chunk generated after the update would still take the aquifer's water into its
 * ladder and lantern and flood once fluids tick. Only a chunk generating now is touched: no player has stood in it.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SettlementRooms {

    private static final Map<StructureTemplate, List<BlockPos>> ROOMS = new WeakHashMap<>();
    private static final Map<StructureTemplate, List<BlockPos>> DRY = new WeakHashMap<>();

    private SettlementRooms() {}

    @SubscribeEvent
    public static void onChunk(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        var references = chunk.getAllReferences();
        if (references.isEmpty()) {
            return;
        }
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (var entry : references.entrySet()) {
            if (registry.wrapAsHolder(entry.getKey()).is(SettlementGround.SETTLEMENTS)) {
                Set<StructureStart> starts = new HashSet<>();
                level.structureManager().fillStartsForStructure(entry.getKey(), entry.getValue(), starts::add);
                for (StructureStart start : starts) {
                    clear(level, chunk, start);
                }
            }
        }
    }

    /** Empties the rooms of {@code start} that lie in {@code chunk}, and dries every block its template keeps dry. */
    public static void clear(ServerLevel level, LevelChunk chunk, StructureStart start) {
        BoundingBox area = new BoundingBox(chunk.getPos().getMinBlockX(), level.getMinY(), chunk.getPos().getMinBlockZ(),
                chunk.getPos().getMaxBlockX(), level.getMaxY(), chunk.getPos().getMaxBlockZ());
        for (StructurePiece raw : start.getPieces()) {
            if (raw instanceof PoolElementStructurePiece piece && piece.getElement() instanceof SinglePoolElement element
                    && piece.getBoundingBox().intersects(area)) {
                StructureTemplate template = level.getStructureManager().getOrCreate(element.getTemplateLocation());
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.getRotation());
                for (BlockPos room : ROOMS.computeIfAbsent(template, SettlementRooms::rooms)) {
                    BlockPos pos = StructureTemplate.calculateRelativePosition(settings, room).offset(piece.getPosition());
                    if (area.isInside(pos)) {
                        BlockState state = chunk.getBlockState(pos);
                        if (!state.isAir()) {
                            chunk.setBlockState(pos, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                for (BlockPos dry : DRY.computeIfAbsent(template, SettlementRooms::dry)) {
                    BlockPos pos = StructureTemplate.calculateRelativePosition(settings, dry).offset(piece.getPosition());
                    if (area.isInside(pos)) {
                        BlockState state = chunk.getBlockState(pos);
                        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
                            chunk.setBlockState(pos, state.setValue(BlockStateProperties.WATERLOGGED, false));
                        }
                    }
                }
            }
        }
    }

    /** Every block of a template that can hold water and, in the template, does not, in its own coordinates. */
    public static List<BlockPos> dry(StructureTemplate template) {
        StructurePlaceSettings plain = new StructurePlaceSettings();
        List<BlockPos> dry = new ArrayList<>();
        for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (block.defaultBlockState().hasProperty(BlockStateProperties.WATERLOGGED)) {
                for (var info : template.filterBlocks(BlockPos.ZERO, plain, block)) {
                    if (!info.state().getValue(BlockStateProperties.WATERLOGGED)) {
                        dry.add(info.pos());
                    }
                }
            }
        }
        return dry;
    }

    /** Every air cell of a template with something of the template above it, in the template's own coordinates. */
    public static List<BlockPos> rooms(StructureTemplate template) {
        StructurePlaceSettings plain = new StructurePlaceSettings();
        Map<Long, Integer> tops = new java.util.HashMap<>();
        for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (block != Blocks.AIR) {
                for (var info : template.filterBlocks(BlockPos.ZERO, plain, block)) {
                    tops.merge(BlockPos.asLong(info.pos().getX(), 0, info.pos().getZ()), info.pos().getY(), Math::max);
                }
            }
        }
        List<BlockPos> rooms = new ArrayList<>();
        for (var info : template.filterBlocks(BlockPos.ZERO, plain, Blocks.AIR)) {
            Integer top = tops.get(BlockPos.asLong(info.pos().getX(), 0, info.pos().getZ()));
            if (top != null && top > info.pos().getY()) {
                rooms.add(info.pos());
            }
        }
        return rooms;
    }
}
