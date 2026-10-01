package at.koopro.wizardsandbeasts.wand.rules;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Which woods, cores and wood × core pairings an administrator has withdrawn from wandmaking. Pure server state,
 * published from {@link WandRulesData}.
 *
 * <p>What a pairing <em>is</em> stays data: a wood and a core can be made into a wand exactly when a
 * {@code wandmaking} recipe names them. These rules only take pairings away — a withdrawn wood, a withdrawn core, or
 * one withdrawn pair — so the bench, the Ollivander trial and the admin test wand all answer "may this be made?" the
 * same way. Wands already made are untouched: withdrawing a wood stops new ones, it does not unmake a wizard's wand.
 */
@NullMarked
public final class WandRules {

    private static volatile Set<String> disabledWoods = Set.of();
    private static volatile Set<String> disabledCores = Set.of();
    private static volatile Set<String> disabledPairs = Set.of();

    private WandRules() {}

    public static synchronized void publish(Set<String> woods, Set<String> cores, Set<String> pairs) {
        disabledWoods = Set.copyOf(woods);
        disabledCores = Set.copyOf(cores);
        disabledPairs = Set.copyOf(pairs);
    }

    public static String pairKey(Identifier wood, Identifier core) {
        return wood + "|" + core;
    }

    public static boolean woodEnabled(Identifier wood) {
        return !disabledWoods.contains(wood.toString());
    }

    public static boolean coreEnabled(Identifier core) {
        return !disabledCores.contains(core.toString());
    }

    public static boolean pairEnabled(Identifier wood, Identifier core) {
        return !disabledPairs.contains(pairKey(wood, core));
    }

    /** Whether a new wand of this wood and core may be made, before asking whether a recipe pairs them. */
    public static boolean mayMake(@Nullable Identifier wood, @Nullable Identifier core) {
        return wood != null && core != null && woodEnabled(wood) && coreEnabled(core) && pairEnabled(wood, core);
    }
}
