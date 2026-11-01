package at.koopro.wizardsandbeasts.settlement.shop;

import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.location.LocationPresence;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.settlement.PostRef;
import at.koopro.wizardsandbeasts.settlement.SettlementData;
import at.koopro.wizardsandbeasts.settlement.npc.NpcRole;
import at.koopro.wizardsandbeasts.settlement.npc.SettlementNpcEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The shelves behind one settlement counter. A player at any other host of the same location (a wandmaker villager in
 * a player's own shop) has no shelf and trades as before, so existing shops keep working untouched.
 *
 * <p>Everything runs on the server thread, so two players at the last unit are served in turn, and the second finds the
 * shelf empty.
 */
@NullMarked
public final class SettlementShops {

    /** A counter with shelves, for the length of one read or one trade. */
    public record Counter(ServerLevel level, String site, long seed, Identifier location, NpcRole.Shop shop) {

        private SettlementData.@Nullable Shelf shelf(String good) {
            SettlementData.Site found = SettlementData.get(level).site(site);
            return found == null ? null : found.shelves().get(location + "|" + good);
        }

        private int capacity(String good) {
            return ShelfRules.capacity(seed, location.toString(), good, shop.rule(good).stock());
        }

        public boolean carries(String good) {
            return ShelfRules.carries(seed, location.toString(), good, shop.rule(good).carry());
        }

        public int left(String good) {
            return ShelfRules.left(shelf(good), capacity(good), shop.restockDays(), level.getGameTime());
        }

        /** Takes {@code count} off the shelf. Called between the charge and the hand-over, once per trade. */
        public void take(String good, int count) {
            SettlementData.get(level).setShelf(site, location + "|" + good,
                    ShelfRules.after(shelf(good), capacity(good), shop.restockDays(), level.getGameTime(), count));
        }
    }

    private SettlementShops() {}

    /** The shelves {@code player} stands at for this location, or null at any counter that has none. */
    public static @Nullable Counter at(ServerPlayer player, Identifier location, LocationDefinition def) {
        Entity host = LocationPresence.host(player, def);
        if (!(host instanceof SettlementNpcEntity npc) || npc.role() == null || npc.role().shop().isEmpty()
                || !(npc.level() instanceof ServerLevel level) || !npc.hasData(ModAttachments.SETTLEMENT_POST.get())) {
            return null;
        }
        PostRef ref = npc.getData(ModAttachments.SETTLEMENT_POST.get());
        SettlementData.Site site = SettlementData.get(level).site(ref.site());
        return site == null ? null : new Counter(level, ref.site(), site.seed(), location, npc.role().shop().get());
    }
}
