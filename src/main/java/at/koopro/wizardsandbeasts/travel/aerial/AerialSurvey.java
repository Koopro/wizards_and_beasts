package at.koopro.wizardsandbeasts.travel.aerial;

import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.travel.TravelRecord;
import at.koopro.wizardsandbeasts.treasure.TreasureCharts;
import at.koopro.wizardsandbeasts.treasure.TreasureSite;
import at.koopro.wizardsandbeasts.treasure.TreasureSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * Aerial exploration: country overflown high on a broom, and treasure sites spotted from above.
 *
 * <p>Checked once a second per flying player, and only while they are on a broom well above the ground — walking,
 * riding a horse or skimming the treetops counts for nothing. Each new survey cell (a {@link #CELL_SHIFT}-block
 * square of one world) records {@link ActivityType#AERIAL_SURVEY} with the biome below as its subject, so a contract
 * can ask for distinct country. A treasure site whose ruins pass beneath records {@link ActivityType#TREASURE_SIGHTED}
 * once, and tells the player what they saw — the Curse-Breaker's reason to own a broom.
 */
@NullMarked
public final class AerialSurvey {

    /** Survey cells are 64 blocks square. */
    public static final int CELL_SHIFT = 6;
    /** Blocks above the ground below that count as surveying. */
    public static final int SURVEY_ALTITUDE = 24;
    /** Blocks above the ground at which ruins below can be made out. */
    public static final int SIGHTING_ALTITUDE = 12;
    /** How far to either side of the flight line ruins are spotted, in blocks. */
    public static final int SIGHTING_REACH = 32;
    /** Ticks between checks. */
    public static final int INTERVAL_TICKS = 20;

    private AerialSurvey() {}

    /** A survey cell key: the world, and the cell's coordinates. Distinct worlds never share a key. */
    public static long cellKey(int worldHash, int blockX, int blockZ) {
        long cx = (blockX >> CELL_SHIFT) & 0xFFFFFL;
        long cz = (blockZ >> CELL_SHIFT) & 0xFFFFFL;
        long world = worldHash & 0xFFFFFFL;
        return (world << 40) | (cx << 20) | cz;
    }

    /** One survey check for {@code player}; cheap no-op unless they are on a broom. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % INTERVAL_TICKS != 0 || !(player.getVehicle() instanceof BroomEntity)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        int altitude = pos.getY() - ground;
        if (altitude < SIGHTING_ALTITUDE) {
            return;
        }
        TravelRecord record = player.getData(ModAttachments.TRAVEL_RECORD.get());
        TravelRecord updated = record;
        if (altitude >= SURVEY_ALTITUDE) {
            long cell = cellKey(level.dimension().identifier().hashCode(), pos.getX(), pos.getZ());
            if (record.isNewCell(cell)) {
                updated = updated.withCell(cell);
                Identifier biome = level.getBiome(new BlockPos(pos.getX(), ground, pos.getZ())).unwrapKey()
                        .map(key -> key.identifier()).orElse(null);
                CareerProgression.record(player, ActivityType.AERIAL_SURVEY, biome);
            }
        }
        updated = sightTreasure(player, level, pos, ground, updated);
        if (updated != record) {
            player.setData(ModAttachments.TRAVEL_RECORD.get(), updated);
        }
    }

    private static TravelRecord sightTreasure(ServerPlayer player, ServerLevel level, BlockPos pos, int ground,
                                              TravelRecord record) {
        Map<Identifier, TreasureSite> sites = TreasureSites.all();
        if (sites.isEmpty()) {
            return record;
        }
        int[][] offsets = {{0, 0}, {SIGHTING_REACH, 0}, {-SIGHTING_REACH, 0}, {0, SIGHTING_REACH}, {0, -SIGHTING_REACH}};
        for (int[] offset : offsets) {
            BlockPos below = new BlockPos(pos.getX() + offset[0], ground, pos.getZ() + offset[1]);
            if (!level.isLoaded(below)) {
                continue;
            }
            for (Map.Entry<Identifier, TreasureSite> site : sites.entrySet()) {
                TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, site.getValue().structures());
                StructureStart start = level.structureManager().getStructureWithPieceAt(below, tag);
                if (!start.isValid()) {
                    continue;
                }
                long key = start.getChunkPos().toLong();
                if (!record.isNewSite(key)) {
                    continue;
                }
                record = record.withSite(key);
                CareerProgression.record(player, ActivityType.TREASURE_SIGHTED, site.getKey());
                PlayerFeedback.toast(player, NoticeKind.DISCOVERY,
                        Component.translatable("travel.wizards_and_beasts.survey.sighted"),
                        TreasureCharts.siteName(site.getKey()));
            }
        }
        return record;
    }
}
