package at.koopro.wizardsandbeasts.treasure;

import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.owl.career.track.ContractDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The chart that comes with an expedition: a filled map to the nearest site of the kind the contract names, the
 * way a cartographer sells an explorer's map. It is the vanilla map, marked with an X — the mod draws no map of its
 * own and generates no tomb: the world already has them.
 */
@NullMarked
public final class TreasureCharts {

    /** Chunks searched for a site; the vanilla explorer maps use 50–100. */
    static final int SEARCH_RADIUS_CHUNKS = 64;

    private TreasureCharts() {}

    /** A contract was taken: if it is treasure work, hand over a chart to each site it names. */
    public static void onContractAccepted(ServerPlayer player, ContractDefinition contract) {
        for (Identifier siteId : Expeditions.sitesOf(contract)) {
            TreasureSite site = TreasureSites.get(siteId);
            if (site == null) {
                continue;
            }
            ItemStack chart = chart(player, siteId, site);
            if (chart == null) {
                PlayerFeedback.actionBar(player, Component.translatable("treasure.wizards_and_beasts.no_chart",
                        siteName(siteId)));
                continue;
            }
            at.koopro.wizardsandbeasts.util.InventoryHandover.giveOrDrop(player, chart);
        }
    }

    /** The chart, or {@code null} when the player is not in the Overworld or no such site is near. */
    public static @Nullable ItemStack chart(ServerPlayer player, Identifier siteId, TreasureSite site) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return null;
        }
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, site.structures());
        BlockPos target = level.findNearestMapStructure(tag, player.blockPosition(), SEARCH_RADIUS_CHUNKS, false);
        if (target == null) {
            return null;
        }
        ItemStack map = MapItem.create(level, target.getX(), target.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, target, "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME, Component.translatable("item.wizards_and_beasts.gringotts_chart",
                siteName(siteId)));
        return map;
    }

    public static Component siteName(Identifier siteId) {
        return Component.translatable("subject." + siteId.getNamespace() + "." + siteId.getPath());
    }
}
