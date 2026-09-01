package at.koopro.wizardsandbeasts.brew.effect;

import at.koopro.wizardsandbeasts.brew.Brew;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The envelope handed to {@link BrewEffect#apply(BrewEffectContext)}.
 *
 * <p>Mirrors {@code SpellEffectContext}: the subject, the world, and the one multiplier that scales
 * authored numbers. Brewing has a single multiplier rather than three because there is only one thing
 * a potion's strength means — {@code potion_potency} lengthens durations, and always has.
 *
 * <h2>Why the drinker is a {@link LivingEntity}</h2>
 * <p>Not a {@code ServerPlayer}. A brew can be splashed, fed to a beast, or drunk by a villager later,
 * and a component that could only ever act on a player would have to be rewritten for each of those.
 * {@link #player()} is there for the components that genuinely need one — anything touching skills,
 * advancements or a screen — and returns null rather than making every caller cast.
 *
 * @param brew      the brew being applied, so a component can read its own colour or name
 * @param drinker   who it is happening to
 * @param level     the server world
 * @param potency   duration multiplier from {@code potion_potency}; 1.0 for an unskilled drinker
 * @param phase     when this is running — see {@link BrewEffectPhase}
 */
public record BrewEffectContext(
        @NonNull Brew brew,
        @NonNull LivingEntity drinker,
        @NonNull ServerLevel level,
        float potency,
        @NonNull BrewEffectPhase phase,
        @NonNull ItemStack source) {

    /**
     * Five-argument form for components that do not care what they were poured out of.
     *
     * <p>{@code source} is the bottle. Almost nothing needs it — an effect list does not care which
     * glass it came from — but a Polyjuice dose is defined by whose hair is in <em>this</em> bottle,
     * and that cannot be read off the brew, which is shared by every bottle of the same potion.
     */
    public BrewEffectContext(Brew brew, LivingEntity drinker, ServerLevel level, float potency,
                             BrewEffectPhase phase) {
        this(brew, drinker, level, potency, phase, ItemStack.EMPTY);
    }

    /** The drinker as a player, or null when a brew reached something that is not one. */
    public @Nullable Player player() {
        return drinker instanceof Player p ? p : null;
    }

    /** Scale an authored duration. Negative (infinite) durations pass through untouched. */
    public int scaleDuration(int rawDuration) {
        return rawDuration < 0 ? rawDuration : Math.max(1, Math.round(rawDuration * potency));
    }

    public static BrewEffectContext onDrink(Brew brew, LivingEntity drinker, ServerLevel level,
                                            float potency) {
        return onDrink(brew, drinker, level, potency, ItemStack.EMPTY);
    }

    /** The drink path proper, carrying the bottle it came out of. */
    public static BrewEffectContext onDrink(Brew brew, LivingEntity drinker, ServerLevel level,
                                            float potency, ItemStack source) {
        return new BrewEffectContext(brew, drinker, level, potency, BrewEffectPhase.ON_DRINK, source);
    }

    public static BrewEffectContext onBrewComplete(Brew brew, LivingEntity brewer, ServerLevel level) {
        // No potency: nothing has been drunk, so the drinker's skill is not the relevant number and
        // pretending otherwise would scale a duration against the wrong person.
        return new BrewEffectContext(brew, brewer, level, 1.0f, BrewEffectPhase.ON_BREW_COMPLETE);
    }
}
