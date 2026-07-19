package at.koopro.wizardsandbeasts.ability.select;

import at.koopro.wizardsandbeasts.network.ability.AbilitySelectionSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * Server-side accessor/mutator for {@link AbilitySelectionState}. All mutations funnel through
 * {@link #mutate} which change-detects and re-syncs the owning client, keeping the client mirror authoritative.
 */
@NullMarked
public final class AbilitySelectionHelper {

    private AbilitySelectionHelper() {}

    public static AbilitySelectionState get(ServerPlayer player) {
        return player.getData(ModAttachments.ABILITY_SELECTION.get());
    }

    public static void select(ServerPlayer player, @Nullable Identifier id) {
        mutate(player, state -> state.withSelected(id));
    }

    public static void setQuickSlot(ServerPlayer player, int slot, @Nullable Identifier id) {
        mutate(player, state -> state.withQuickSlot(slot, id));
    }

    public static void setToggle(ServerPlayer player, Identifier id, boolean on) {
        mutate(player, state -> state.withToggle(id, on));
    }

    public static void setCooldown(ServerPlayer player, Identifier id, long expiryGameTime) {
        mutate(player, state -> state.withCooldown(id, expiryGameTime));
    }

    public static void clearCooldowns(ServerPlayer player) {
        mutate(player, AbilitySelectionState::withoutCooldowns);
    }

    /** Applies {@code mutator}; if the state changed, persists it and re-syncs the client. */
    public static void mutate(ServerPlayer player, UnaryOperator<AbilitySelectionState> mutator) {
        AbilitySelectionState previous = get(player);
        AbilitySelectionState next = mutator.apply(previous);
        if (next.equals(previous)) {
            return;
        }
        player.setData(ModAttachments.ABILITY_SELECTION.get(), next);
        AbilitySelectionSyncS2CPayload.syncToPlayer(player);
    }

    /** Force a re-sync without mutating (login/respawn/grant-change seams). */
    public static void sync(ServerPlayer player) {
        AbilitySelectionSyncS2CPayload.syncToPlayer(player);
    }
}
