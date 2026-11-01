package at.koopro.wizardsandbeasts.settlement;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The Wayfarer's Chart: a vanilla explorer's map with an X over the nearest hamlet. Handed over once, when the
 * Apprentice's Path is walked, as a lead and not a task; caches hold one too.
 *
 * <p>Real terrain put the search at 0.6 to 1.4 seconds, because a site is only known by reading the ground and placing
 * its pieces. So it runs a few milliseconds a tick, over as many ticks as it needs, and never holds up the server.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class HamletCharts {

    public static final TagKey<Structure> HAMLETS = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "settlement"));
    /** Grid rings searched: hamlets are 40 chunks apart, so 12 rings reach about 7,500 blocks. */
    static final int SEARCH_RINGS = 12;
    private static final long BUDGET_NANOS = 3_000_000L;

    private static final Map<UUID, SiteSearch.Walk> SEARCHES = new HashMap<>();

    private HamletCharts() {}

    /**
     * Starts drawing {@code player} a chart if they have none. Not in the Overworld, or no hamlet in reach: nothing is
     * given, and the offer stands for their next login.
     */
    public static void offer(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.SETTLEMENTS) || player.getData(ModAttachments.KNOWN_PLACES.get()).charted()
                || SEARCHES.containsKey(player.getUUID()) || !(player.level() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) {
            return;
        }
        SiteSearch.Walk walk = TreasureCharts.walk(level, HAMLETS, player.blockPosition(), SEARCH_RINGS);
        if (walk != null) {
            SEARCHES.put(player.getUUID(), walk);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (SEARCHES.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + BUDGET_NANOS;
        Iterator<Map.Entry<UUID, SiteSearch.Walk>> searches = SEARCHES.entrySet().iterator();
        while (searches.hasNext() && System.nanoTime() < deadline) {
            Map.Entry<UUID, SiteSearch.Walk> entry = searches.next();
            SiteSearch.Walk walk = entry.getValue();
            while (!walk.finished() && System.nanoTime() < deadline) {
                walk.step();
            }
            if (walk.finished()) {
                searches.remove();
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player != null && walk.found() != null) {
                    give(player, walk.found());
                }
            }
        }
    }

    private static void give(ServerPlayer player, SiteSearch.Site site) {
        ServerLevel level = player.level();
        InventoryHandover.giveOrDrop(player, TreasureCharts.draw(level, new BlockPos(site.locateX(), 0, site.locateZ()),
                Component.translatable("item.wizards_and_beasts.wayfarers_chart")));
        player.setData(ModAttachments.KNOWN_PLACES.get(), player.getData(ModAttachments.KNOWN_PLACES.get()).withChart());
        player.sendSystemMessage(Component.translatable("settlement.wizards_and_beasts.chart.given"));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SEARCHES.remove(event.getEntity().getUUID());
    }
}
