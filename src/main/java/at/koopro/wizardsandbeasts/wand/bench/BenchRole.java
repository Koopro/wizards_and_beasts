package at.koopro.wizardsandbeasts.wand.bench;

import at.koopro.wizardsandbeasts.wand.gui.WandmakersBenchMenu;
import at.koopro.wizardsandbeasts.wand.refine.WandRefineMenu;
import at.koopro.wizardsandbeasts.wand.style.WandRestyleMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * What the bench is open for: making a wand, mending one, or restyling one. One leather tag each on the bench screen
 * (P5, 2026-11-26); pressing a tag opens that role's menu on the same bench. Whatever lay in the role being left goes
 * back to the player, as when any bench screen closes; a making in progress stays on the bench.
 */
@NullMarked
public enum BenchRole {
    MAKE, MEND, RESTYLE;

    public String key() {
        return "wandcraft.bench.tab." + name().toLowerCase(Locale.ROOT);
    }

    /** Opens this role's menu on the bench for the player. */
    public void open(ServerPlayer player, WandmakersBenchBlockEntity bench) {
        switch (this) {
            case MAKE -> player.openMenu(new SimpleMenuProvider((id, inv, p) -> new WandmakersBenchMenu(id, inv, bench),
                    Component.translatable("gui.wizards_and_beasts.wandmakers_bench")), buf -> buf.writeBlockPos(bench.getBlockPos()));
            case MEND -> player.openMenu(new SimpleMenuProvider((id, inv, p) -> new WandRefineMenu(id, inv, bench),
                    Component.translatable("gui.wizards_and_beasts.wand_refine")), buf -> buf.writeBlockPos(bench.getBlockPos()));
            case RESTYLE -> WandRestyleMenu.open(player, bench);
        }
    }

    /** The bench behind an open bench menu, and that menu's role; {@code null} for any other menu. */
    public static @Nullable WandmakersBenchBlockEntity benchOf(AbstractContainerMenu menu) {
        if (menu instanceof WandmakersBenchMenu make) {
            return make.getBench();
        }
        if (menu instanceof WandRefineMenu mend) {
            return mend.bench();
        }
        if (menu instanceof WandRestyleMenu restyle) {
            return restyle.bench();
        }
        return null;
    }
}
