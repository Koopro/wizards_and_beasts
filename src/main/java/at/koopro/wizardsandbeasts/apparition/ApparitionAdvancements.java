package at.koopro.wizardsandbeasts.apparition;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The two Apparition advancements, granted from code.
 *
 * <p>Every other advancement this mod ships is earned by picking an item up, which
 * {@code minecraft:inventory_changed} awards on its own. Neither of these is an item: one is having travelled
 * and one is having been torn doing it, and no vanilla criterion observes either. So both are declared with
 * {@code minecraft:impossible} — a trigger that never fires by itself — and awarded here, which is the
 * standard way to hang an advancement off a mod's own event.
 *
 * <p>Awarding an already-earned advancement is a no-op in vanilla, so "first" needs no bookkeeping of its
 * own; the advancement <i>is</i> the record.
 */
@NullMarked
public final class ApparitionAdvancements {

    /** Arriving somewhere, whole or not. */
    private static final Identifier APPARATED =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "progression/apparate");
    /** Being torn by it. A goal rather than a task: nobody is aiming for this one. */
    private static final Identifier SPLINCHED =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "progression/splinch");

    private ApparitionAdvancements() {}

    /** Records a jump that put the wizard somewhere else. */
    public static void awardArrival(ServerPlayer player) {
        award(player, APPARATED, "apparated");
    }

    /** Records a jump that took something off them. */
    public static void awardSplinch(ServerPlayer player) {
        award(player, SPLINCHED, "splinched");
    }

    /**
     * Awards one criterion, quietly doing nothing if the advancement is not loaded.
     *
     * <p>A datapack can remove either of these, and a missing advancement is a datapack's decision rather
     * than an error — so this reads as absent, not as broken.
     */
    private static void award(ServerPlayer player, Identifier id, String criterion) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        @Nullable AdvancementHolder holder = server.getAdvancements().get(id);
        if (holder == null) {
            return;
        }
        player.getAdvancements().award(holder, criterion);
    }
}
