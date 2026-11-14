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
    /** The alley's template: the yard, the wall and the street behind it. */
    public static final Identifier ALLEY = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "diagon_alley/preview/a");
    /** Bumped whenever the template changes, so a saved world rebuilds its pocket. */
    public static final int LAYOUT = 3;
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
        template.placeInWorld(pocket, ORIGIN, ORIGIN, new StructurePlaceSettings(), pocket.getRandom(),
                Block.UPDATE_CLIENTS);
        data.markBuilt(LAYOUT);
        LOGGER.info("Diagon Alley: built layout {} at {} ({})", LAYOUT, ORIGIN, size);
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
