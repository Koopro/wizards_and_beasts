package at.koopro.wizardsandbeasts.azkaban.structure;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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
     * Returns true if {@code level} has an Azkaban fortress structure start overlapping
     * the given chunk column at the player's block position.
     */
    public static boolean isInsideAzkabanArea(ServerLevel level, double worldX, double worldZ, int margin) {
        if (cachedFortressCenter == null) return false;
        // Island is 36×36; fortress centered at cachedFortressCenter — use generous bounds
        int range = 80 + margin;
        return Math.abs(worldX - cachedFortressCenter.getX()) <= range
                && Math.abs(worldZ - cachedFortressCenter.getZ()) <= range;
    }

    /** Cached fortress center for spawner use; set when structure generates. Volatile for cross-thread visibility. */
    public static volatile net.minecraft.core.BlockPos cachedFortressCenter = null;
}
