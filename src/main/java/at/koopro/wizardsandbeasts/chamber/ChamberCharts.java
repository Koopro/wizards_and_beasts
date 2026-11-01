package at.koopro.wizardsandbeasts.chamber;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.treasure.SiteSearch;
import at.koopro.wizardsandbeasts.treasure.TreasureCharts;
import at.koopro.wizardsandbeasts.util.InventoryHandover;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.NullMarked;

/**
 * The Serpent Chart: a vanilla explorer's map with an X over the nearest Chamber of Secrets whose basilisk still lives.
 * Handed over once, when the Apprentice's Path is walked; the hidden wizarding cache holds one too. The search is the
 * expedition charts' ({@link TreasureCharts#nearest}), passing over a Chamber already cleared.
 */
@NullMarked
public final class ChamberCharts {

    public static final TagKey<Structure> CHAMBERS = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "chamber_of_secrets"));
    private static final String KEY = "chamber.wizards_and_beasts.";
    /**
     * Grid rings searched: Chambers are spaced 40 chunks apart, so 16 rings reach about 10,000 blocks. Kept well under
     * the expedition charts' 64 because a fruitless search is tried again at every login until a chart is given.
     */
    static final int SEARCH_RINGS = 16;

    private ChamberCharts() {}

    /**
     * Hands {@code player} a chart if they have none yet and there is a Chamber to draw it to. Returns whether one was
     * handed over. Not in the Overworld, or no Chamber within reach: nothing happens, and the offer stands for the
     * next login.
     */
    public static boolean offer(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.CHAMBER_OF_SECRETS) || ChamberQuestService.quest(player).has(ChamberStage.CHARTED)
                || !(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return false;
        }
        ChamberBasilisk.Data cleared = ChamberBasilisk.Data.get(level);
        SiteSearch.Site site = TreasureCharts.nearest(level, CHAMBERS, player.blockPosition(), SEARCH_RINGS,
                s -> cleared.isSlain(new ChunkPos(s.startX(), s.startZ())));
        if (site == null) {
            return false;
        }
        ItemStack chart = TreasureCharts.draw(level, new BlockPos(site.locateX(), 0, site.locateZ()),
                Component.translatable("item.wizards_and_beasts.serpent_chart"));
        InventoryHandover.giveOrDrop(player, chart);
        ChamberQuestService.reach(player, ChamberStage.CHARTED);
        player.sendSystemMessage(Component.translatable(KEY + "chart.given"));
        return true;
    }
}
