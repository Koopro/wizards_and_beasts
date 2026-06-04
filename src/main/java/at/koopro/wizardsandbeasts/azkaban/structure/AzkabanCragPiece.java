package at.koopro.wizardsandbeasts.azkaban.structure;

import at.koopro.wizardsandbeasts.azkaban.data.AzkabanWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.jspecify.annotations.NonNull;

import java.util.Random;

/**
 * Procedural bare-rock crag with a level platform on top.
 *
 * <p>A solid rock column rises from the sea floor to the platform top
 * ({@code seaLevel + cragHeight}). The central rectangle (template footprint +
 * margin) is flat and fully solid — the level base the template sits on. Beyond
 * it the rock falls away with a sheer ~2:1 slope plus light per-column jitter so
 * it reads as a natural North-Sea crag rather than a cuboid. Lower section is the
 * palette {@code lower} (deepslate), the body is {@code body} (stone), with
 * sparse {@code accent} (gravel) flecks on the slopes. Surrounding water is left
 * intact; the crag simply rises through it.
 *
 * <p>This piece owns all terrain shaping — the structure declares
 * {@code terrain_adaptation: none}.
 */
public final class AzkabanCragPiece extends StructurePiece {

    /** Sea-floor depth: how far below sea level the crag base reaches. */
    private static final int FLOOR_DEPTH = 32;
    /** Sheer slope: vertical drop per horizontal block beyond the platform. */
    private static final int SLOPE = 2;

    private final BlockPos center;       // platform-top center (y = platformTopY)
    private final int halfPlatformX;
    private final int halfPlatformZ;
    private final int cragHeight;
    private final int seaLevel;
    private final Block body;
    private final Block lower;
    private final Block accent;

    public AzkabanCragPiece(@NonNull BlockPos center,
                            int halfPlatformX,
                            int halfPlatformZ,
                            int cragHeight,
                            int seaLevel,
                            AzkabanStructure.@NonNull Palette palette) {
        super(AzkabanStructures.AZKABAN_CRAG_PIECE.get(), 0,
                makeBoundingBox(center, halfPlatformX, halfPlatformZ, cragHeight, seaLevel));
        this.center = center;
        this.halfPlatformX = halfPlatformX;
        this.halfPlatformZ = halfPlatformZ;
        this.cragHeight = cragHeight;
        this.seaLevel = seaLevel;
        this.body = palette.body();
        this.lower = palette.lower();
        this.accent = palette.accent();
        AzkabanStructures.cachedFortressCenter = center;
    }

    private AzkabanCragPiece(@NonNull CompoundTag tag) {
        super(AzkabanStructures.AZKABAN_CRAG_PIECE.get(), tag);
        this.center = BlockPos.of(tag.getLong("Center").orElse(0L));
        this.halfPlatformX = tag.getInt("HPX").orElse(8);
        this.halfPlatformZ = tag.getInt("HPZ").orElse(8);
        this.cragHeight = tag.getInt("Crag").orElse(10);
        this.seaLevel = tag.getInt("Sea").orElse(63);
        this.body = readBlock(tag, "Body", Blocks.STONE);
        this.lower = readBlock(tag, "Lower", Blocks.DEEPSLATE);
        this.accent = readBlock(tag, "Accent", Blocks.GRAVEL);
        AzkabanStructures.cachedFortressCenter = center;
    }

    /** Deserialization entry-point registered as the piece type's loader. */
    public static AzkabanCragPiece load(@NonNull StructurePieceSerializationContext ctx,
                                        @NonNull CompoundTag tag) {
        return new AzkabanCragPiece(tag);
    }

    private static BoundingBox makeBoundingBox(BlockPos center, int halfPX, int halfPZ,
                                               int cragHeight, int seaLevel) {
        int platformTopY = seaLevel + cragHeight;
        int boxMinY = seaLevel - FLOOR_DEPTH;
        int slopeRun = (platformTopY - boxMinY) / SLOPE + 2;
        int halfBoxX = halfPX + slopeRun;
        int halfBoxZ = halfPZ + slopeRun;
        return BoundingBox.fromCorners(
                new BlockPos(center.getX() - halfBoxX, boxMinY, center.getZ() - halfBoxZ),
                new BlockPos(center.getX() + halfBoxX, platformTopY + 1, center.getZ() + halfBoxZ));
    }

    @Override
    protected void addAdditionalSaveData(@NonNull StructurePieceSerializationContext ctx,
                                         @NonNull CompoundTag tag) {
        tag.putLong("Center", center.asLong());
        tag.putInt("HPX", halfPlatformX);
        tag.putInt("HPZ", halfPlatformZ);
        tag.putInt("Crag", cragHeight);
        tag.putInt("Sea", seaLevel);
        tag.putString("Body", BuiltInRegistries.BLOCK.getKey(body).toString());
        tag.putString("Lower", BuiltInRegistries.BLOCK.getKey(lower).toString());
        tag.putString("Accent", BuiltInRegistries.BLOCK.getKey(accent).toString());
    }

    @Override
    public void postProcess(@NonNull WorldGenLevel level,
                            @NonNull StructureManager structureManager,
                            @NonNull ChunkGenerator generator,
                            @NonNull RandomSource random,
                            @NonNull BoundingBox writableArea,
                            @NonNull ChunkPos chunkPos,
                            @NonNull BlockPos pivot) {
        BoundingBox box = this.boundingBox;
        int platformTopY = center.getY();
        int boxMinY = box.minY();

        BlockState bodyState = body.defaultBlockState();
        BlockState lowerState = lower.defaultBlockState();
        BlockState accentState = accent.defaultBlockState();

        for (int worldX = box.minX(); worldX <= box.maxX(); worldX++) {
            for (int worldZ = box.minZ(); worldZ <= box.maxZ(); worldZ++) {
                int dx = worldX - center.getX();
                int dz = worldZ - center.getZ();
                int exX = Math.max(0, Math.abs(dx) - halfPlatformX);
                int exZ = Math.max(0, Math.abs(dz) - halfPlatformZ);
                int ex = Math.max(exX, exZ);

                Random colRng = new Random(center.asLong() ^ (dx * 1000003L + dz * 999983L));

                int topY;
                if (ex == 0) {
                    topY = platformTopY; // flat platform
                } else {
                    int drop = ex * SLOPE + colRng.nextInt(3) - 1;
                    topY = platformTopY - drop;
                }
                if (topY <= boxMinY) continue;

                int localX = worldX - box.minX();
                int localZ = worldZ - box.minZ();
                for (int wy = boxMinY; wy <= topY; wy++) {
                    BlockState state;
                    if (wy <= seaLevel - 6) {
                        state = lowerState;
                    } else if (ex > 0 && colRng.nextInt(12) == 0) {
                        state = accentState;
                    } else {
                        state = bodyState;
                    }
                    placeBlock(level, state, localX, wy - box.minY(), localZ, writableArea);
                }
            }
        }

        persistCenter(level);
    }

    private void persistCenter(@NonNull WorldGenLevel level) {
        AzkabanStructures.cachedFortressCenter = center;
        if (level instanceof ServerLevelAccessor sla && sla.getLevel() instanceof ServerLevel sl) {
            AzkabanWorldData data = AzkabanWorldData.get(sl);
            if (!data.isGenerated()) {
                data.setFortressCenter(center);
            }
        }
    }

    private static Block readBlock(@NonNull CompoundTag tag, @NonNull String key, @NonNull Block fallback) {
        String s = tag.getString(key).orElse("");
        int i = s.indexOf(':');
        if (i < 0) return fallback;
        Identifier id = Identifier.fromNamespaceAndPath(s.substring(0, i), s.substring(i + 1));
        return BuiltInRegistries.BLOCK.get(id).map(Holder::value).orElse(fallback);
    }
}
