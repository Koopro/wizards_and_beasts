package at.koopro.wizardsandbeasts.ability;

import at.koopro.wizardsandbeasts.ability.data.PlayerAbilityProficiency;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Read/write seam for {@link PlayerAbilityProficiency}, mirroring {@link PlayerAbilityHelper}'s shape so
 * both player-ability stores are reached the same way.
 *
 * <p>Server-side only and unsynced: nothing on the client reads proficiency directly. Where the client needs
 * a derived number — a charge window width, a range — the server sends the derived value in the payload that
 * needs it, so there is no second copy of this map to drift.
 */
@NullMarked
public final class AbilityProficiency {

    private AbilityProficiency() {}

    public static PlayerAbilityProficiency get(Player player) {
        return player.getData(ModAttachments.ABILITY_PROFICIENCY.get());
    }

    /** Proficiency in {@code abilityId}, {@code [0, 1]}. Zero for anything never practised. */
    public static float get(Player player, Identifier abilityId) {
        return get(player).get(abilityId);
    }

    public static void set(Player player, Identifier abilityId, float value) {
        mutate(player, data -> data.with(abilityId, value));
    }

    /** Adds {@code delta} and returns the resulting proficiency after clamping. */
    public static float add(Player player, Identifier abilityId, float delta) {
        mutate(player, data -> data.plus(abilityId, delta));
        return get(player, abilityId);
    }

    public static void clear(Player player, Identifier abilityId) {
        mutate(player, data -> data.without(abilityId));
    }

    /** Immutable view of everything the player has practised — for commands and the character sheet. */
    public static Map<Identifier, Float> all(Player player) {
        return get(player).values();
    }

    private static void mutate(Player player, UnaryOperator<PlayerAbilityProficiency> mutator) {
        PlayerAbilityProficiency previous = get(player);
        PlayerAbilityProficiency next = mutator.apply(previous);
        if (next.equals(previous)) {
            return;
        }
        player.setData(ModAttachments.ABILITY_PROFICIENCY.get(), next);
    }
}
