package at.koopro.wizardsandbeasts.diagon;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Set;

/**
 * Diagon Alley: the Leaky Cauldron in the overworld, everything behind it in a pocket dimension of its own.
 *
 * <p>The pocket ({@code data/wizards_and_beasts/dimension/diagon_alley.json}) is a void with no spawns. The alley is
 * built into it once from its templates, at a fixed place, the first time the server starts with it missing or out of
 * date, and its chunks are kept loaded so stepping through the pub's back door is a short fade and not a wait. Nothing
 * of it exists in the overworld, so nobody can walk, fly or dig their way in, or see it from outside.
 *
 * <p>The way in is the pub's back door ({@link PassageDoorBlock}); the way out is the yard's door, back to the bar.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class DiagonAlley {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "diagon_alley"));
    /** The alley's template: the yard, the wall, the street and its shops, and Gringotts at the end. */
    public static final Identifier ALLEY = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "diagon_alley/alley/a");
    /** London round it: a ring of roofs and chimneys, so from the street there is never an edge or a void. */
    public static final Identifier BACKDROP = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "diagon_alley/backdrop/a");
    /** How far the backdrop's corner stands out from the alley's, on both horizontal axes. */
    static final int BACKDROP_MARGIN = 14;
    /** Bumped whenever the templates change, so a saved world rebuilds its pocket. */
    public static final int LAYOUT = 10;
    /** Where the template's corner stands: its street level (layer 6) lands at y 64. */
    public static final BlockPos ORIGIN = new BlockPos(0, 58, 0);
    /** The yard's door back into the pub, lower half, in template coordinates; it faces into the yard (east). */
    static final BlockPos YARD_DOOR = new BlockPos(0, 7, 17);
    static final Direction YARD_FACING = Direction.EAST;

    private DiagonAlley() {}

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        ServerLevel pocket = event.getServer().getLevel(LEVEL);
        if (pocket != null) {
            ensureBuilt(pocket);
        }
    }

    /** Builds the alley into its pocket if it was never built, or was built from an older layout. */
    static void ensureBuilt(ServerLevel pocket) {
        DiagonAlleyData data = DiagonAlleyData.get(pocket.getServer());
        if (data.built() >= LAYOUT) {
            return;
        }
        Optional<StructureTemplate> found = pocket.getStructureManager().get(ALLEY);
        if (found.isEmpty()) {
            LOGGER.warn("Diagon Alley: template {} missing; the pocket stays empty", ALLEY);
            return;
        }
        StructureTemplate template = found.get();
        Vec3i size = template.getSize();
        keepLoaded(pocket, size);
        if (data.built() > 0) {
            clearOld(pocket, size);
        }
        pocket.getStructureManager().get(BACKDROP).ifPresent(backdrop -> {
            BlockPos corner = ORIGIN.offset(-BACKDROP_MARGIN, 0, -BACKDROP_MARGIN);
            backdrop.placeInWorld(pocket, corner, corner, new StructurePlaceSettings(), pocket.getRandom(),
                    Block.UPDATE_CLIENTS);
        });
        template.placeInWorld(pocket, ORIGIN, ORIGIN, new StructurePlaceSettings(), pocket.getRandom(),
                Block.UPDATE_CLIENTS);
        data.markBuilt(LAYOUT);
        LOGGER.info("Diagon Alley: built layout {} at {} ({})", LAYOUT, ORIGIN, size);
    }

    /** How far past the new backdrop an older layout's blocks are swept away: an older alley may have been larger. */
    private static final int SWEEP = 24;
    /** How high above the template's corner an older layout may have reached (Gringotts' pediment and chimneys). */
    private static final int SWEEP_HEIGHT = 64;

    /**
     * Empties the ground an older layout stood on, so a street that moved or shrank leaves nothing of its old self
     * behind. No drops and no block-entity side effects: the old shops' chests and shelves simply go.
     */
    private static void clearOld(ServerLevel pocket, Vec3i size) {
        int margin = BACKDROP_MARGIN + SWEEP;
        BlockPos from = ORIGIN.offset(-margin, 0, -margin);
        BlockPos to = ORIGIN.offset(size.getX() + margin, SWEEP_HEIGHT, size.getZ() + margin);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
                | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
        int cleared = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (!pocket.getBlockState(pos).isAir()) {
                pocket.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), flags);
                cleared++;
            }
        }
        // The old shops' people go with them: their posts are gone, so their records are, and the new posts staff
        // themselves. A copy still in a region file is refused on joining (SettlementEvents.onJoin).
        // Every site in the pocket, not only today's: posts of older layouts were filed under their own chunks.
        int sites = at.koopro.wizardsandbeasts.settlement.SettlementData.get(pocket).forgetDimension(LEVEL);
        int staff = 0;
        for (net.minecraft.world.entity.Entity entity : pocket.getAllEntities()) {
            if (entity.hasData(at.koopro.wizardsandbeasts.registry.ModAttachments.SETTLEMENT_POST.get())) {
                entity.discard();
                staff++;
            }
        }
        LOGGER.info("Diagon Alley: cleared {} blocks of the old layout, forgot {} sites, sent {} staff away", cleared,
                sites, staff);
    }

    /** Keeps the alley's chunks, and a ring round them, loaded: a traveller arrives to a street already there. */
    private static void keepLoaded(ServerLevel pocket, Vec3i size) {
        int x0 = (ORIGIN.getX() >> 4) - 1;
        int z0 = (ORIGIN.getZ() >> 4) - 1;
        int x1 = ((ORIGIN.getX() + size.getX()) >> 4) + 1;
        int z1 = ((ORIGIN.getZ() + size.getZ()) >> 4) + 1;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                pocket.setChunkForced(cx, cz, true);
            }
        }
    }

    /**
     * Where the Leaky Cauldron stands, as the middle of its chunk at sea level, or {@code null} if the world has none.
     * Loading that chunk, which a caller may do, puts the pub's hearth on the Floo Network ({@link DiagonAlleyEvents}).
     */
    public static @org.jspecify.annotations.Nullable BlockPos locatePub(ServerLevel overworld) {
        var sets = overworld.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
        var set = sets.getOptional(ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE_SET,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "leaky_cauldron")));
        if (set.isEmpty() || !(set.get().placement()
                instanceof at.koopro.wizardsandbeasts.azkaban.structure.OnePerWorldPlacement placement)) {
            return null;
        }
        net.minecraft.world.level.ChunkPos chunk = placement.target(overworld.getChunkSource().getGeneratorState());
        return new BlockPos(chunk.getMiddleBlockX(), overworld.getSeaLevel(), chunk.getMiddleBlockZ());
    }

    /** From the bar, through the back door, into the yard. Remembers the door so the way out leads back to it. */
    static void enter(ServerPlayer player, BlockPos pubDoor, Direction pubFacing) {
        MinecraftServer server = player.level().getServer();
        ServerLevel pocket = server.getLevel(LEVEL);
        if (pocket == null) {
            return;
        }
        ensureBuilt(pocket);
        DiagonAlleyData.get(server).rememberPubDoor(pubDoor, pubFacing);
        BlockPos arrival = ORIGIN.offset(YARD_DOOR).relative(YARD_FACING);
        travel(player, pocket, arrival, YARD_FACING);
    }

    /** From the yard, through the door, back to the bar; to the world's spawn if the pub was never walked through. */
    static void leave(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel overworld = server.overworld();
        DiagonAlleyData data = DiagonAlleyData.get(server);
        BlockPos door = data.pubDoor();
        Direction facing = data.pubFacing();
        if (door == null || facing == null) {
            travel(player, overworld, overworld.getRespawnData().pos(), player.getDirection());
            return;
        }
        // Each passage door faces the room it serves: the pub's faces into the bar, the yard's into the yard.
        travel(player, overworld, door.relative(facing), facing);
    }

    private static void travel(ServerPlayer player, ServerLevel level, BlockPos feet, Direction facing) {
        if (!player.getPassengers().isEmpty()) {
            player.ejectPassengers();
        }
        player.stopRiding();
        player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(feet), Vec3.ZERO, facing.toYRot(),
                player.getXRot(), Set.<Relative>of(), TeleportTransition.DO_NOTHING));
    }
}
