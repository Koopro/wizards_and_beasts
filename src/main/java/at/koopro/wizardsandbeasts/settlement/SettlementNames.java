package at.koopro.wizardsandbeasts.settlement;

import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NullMarked;

/** What a hamlet is called: rolled once from its site's seed, from original names, and the same for everyone. */
@NullMarked
public final class SettlementNames {

    /** Lang keys {@code settlement.wizards_and_beasts.name.<i>}. */
    public static final int NAME_COUNT = 8;

    private SettlementNames() {}

    public static Component ofSeed(long seed) {
        return Component.translatable("settlement.wizards_and_beasts.name." + Math.floorMod(seed, NAME_COUNT));
    }

    /** The name of the place {@code keeper} keeps a post in. */
    public static Component of(ServerPlayer player, Entity keeper) {
        if (keeper.level() instanceof ServerLevel level && keeper.hasData(ModAttachments.SETTLEMENT_POST.get())) {
            SettlementData.Site site = SettlementData.get(level).site(keeper.getData(ModAttachments.SETTLEMENT_POST.get()).site());
            if (site != null) {
                return ofSeed(site.seed());
            }
        }
        return Component.translatable("settlement.wizards_and_beasts.unnamed");
    }
}
