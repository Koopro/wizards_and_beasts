package at.koopro.wizardsandbeasts.diagon;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.floo.FlooNetworkManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * The world's side of Diagon Alley.
 *
 * <p>When the chunk holding the Leaky Cauldron's start first loads, the pub's hearth becomes the Floo Network's
 * "Diagon Alley", a public landmark nobody owns, and its back door is remembered as the way back out of the alley, so
 * both work before anyone has walked through. The pub is a jigsaw piece and may stand turned any way, so both are found
 * from the piece's own position and rotation.
 *
 * <p>In the alley's pocket nobody but a creative player breaks or places a block: the street is a place to shop, and
 * nobody digs or builds their way over the roofs and out of it.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class DiagonAlleyEvents {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final ResourceKey<Structure> PUB = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "leaky_cauldron"));
    /** The Floo address of the pub's hearth. */
    public static final String ADDRESS = "Diagon Alley";
    /** In the pub's template: the hearth, and the back door's lower half (facing into the bar, south). */
    static final BlockPos HEARTH = new BlockPos(5, 1, 1);
    static final BlockPos DOOR = new BlockPos(2, 1, 0);

    private DiagonAlleyEvents() {}

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        Optional<Structure> pub = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOptional(PUB);
        if (pub.isEmpty()) {
            return;
        }
        ChunkAccess chunk = event.getChunk();
        StructureStart start = chunk.getStartForStructure(pub.get());
        if (start == null || !start.isValid()) {
            return;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof PoolElementStructurePiece element) {
                settle(level, element.getPosition(), element.getRotation());
                return;
            }
        }
    }

    /** Registers the pub's hearth with the Floo Network and remembers its back door, once. */
    static void settle(ServerLevel level, BlockPos origin, Rotation rotation) {
        BlockPos hearth = origin.offset(StructureTemplate.transform(HEARTH, Mirror.NONE, rotation, BlockPos.ZERO));
        BlockPos door = origin.offset(StructureTemplate.transform(DOOR, Mirror.NONE, rotation, BlockPos.ZERO));
        Direction facing = rotation.rotate(Direction.SOUTH);
        DiagonAlleyData data = DiagonAlleyData.get(level.getServer());
        if (data.pubDoor() == null) {
            data.rememberPubDoor(door, facing);
        }
        FlooNetworkManager network = FlooNetworkManager.get(level);
        if (network.findByPos(Level.OVERWORLD.identifier(), hearth) != null) {
            return;                                        // already on the network: the chunk has loaded before
        }
        FlooNetworkManager.RegisterResult result = network.register(ADDRESS,
                Level.OVERWORLD.identifier(), hearth, true, Optional.empty());
        if (result != FlooNetworkManager.RegisterResult.REGISTERED) {
            LOGGER.warn("Diagon Alley: the Leaky Cauldron's hearth at {} could not join the Floo Network: {}", hearth,
                    result);
        } else {
            LOGGER.info("Diagon Alley: the Leaky Cauldron's hearth at {} is on the Floo Network", hearth);
        }
    }

    /** Whether {@code pos} stands inside the Leaky Cauldron. */
    public static boolean inPub(ServerLevel level, BlockPos pos) {
        Optional<Structure> pub = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOptional(PUB);
        return pub.isPresent() && level.structureManager().getStructureWithPieceAt(pos, pub.get()).isValid();
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (inPocket(event.getLevel()) && !event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (inPocket(event.getLevel()) && !(event.getEntity() instanceof Player player && player.isCreative())) {
            event.setCanceled(true);
        }
    }

    private static boolean inPocket(net.minecraft.world.level.LevelAccessor level) {
        return level instanceof Level real && real.dimension() == DiagonAlley.LEVEL;
    }
}
