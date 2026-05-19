package at.koopro.wizardsandbeasts.azkaban.structure;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

public final class AzkabanStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, WizardsAndBeastsMod.MODID);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, WizardsAndBeastsMod.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<AzkabanFortressStructure>> AZKABAN_FORTRESS =
            STRUCTURE_TYPES.register("azkaban_fortress", () -> () -> AzkabanFortressStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> AZKABAN_FORTRESS_PIECE =
            STRUCTURE_PIECE_TYPES.register("azkaban_fortress_piece",
                    () -> AzkabanFortressPiece::load);

    public static final ResourceKey<Structure> AZKABAN_FORTRESS_KEY = ResourceKey.create(
            Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "azkaban_fortress"));

    private AzkabanStructures() {}

    /**
     * Returns true if the given world position is within {@code margin} blocks of the Azkaban area.
     * Reads from {@code AzkabanWorldData} when a ServerLevel is available; falls back to the
     * in-memory cache that is set when the structure generates.
     */
    public static boolean isInsideAzkabanArea(ServerLevel level, double worldX, double worldZ, int margin) {
        BlockPos center = getCenter(level);
        if (center == null) return false;
        int range = 80 + margin;
        return Math.abs(worldX - center.getX()) <= range
                && Math.abs(worldZ - center.getZ()) <= range;
    }

    /** Returns the cached center for use by spawner and nearby-area checks. */
    public static @Nullable BlockPos getCenter(@Nullable ServerLevel level) {
        if (level != null) {
            // Prefer the persistent saved-data record so we survive world restarts
            at.koopro.wizardsandbeasts.azkaban.data.AzkabanWorldData data =
                    at.koopro.wizardsandbeasts.azkaban.data.AzkabanWorldData.get(level);
            if (data.isGenerated()) return data.getOrComputeCenter(level);
        }
        return cachedFortressCenter;
    }

    /** In-memory cache set when the piece first generates. Volatile for cross-thread visibility. */
    public static volatile @Nullable BlockPos cachedFortressCenter = null;
}
