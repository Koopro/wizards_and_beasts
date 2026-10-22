package at.koopro.wizardsandbeasts.wand.refine;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The bench's second role, as rules: what can be done to a finished wand, what each asks of the bench and the
 * wandmaker, and what it changes. Pure, so every rule is unit-tested; {@link WandRefineMenu} gathers the inputs and
 * applies the answer.
 *
 * <p>User decision (2026-10-22): limited customisation, never risky. Nothing is ever destroyed, and wood, core and
 * the wand's bond with its master never change.
 * <ul>
 *   <li><b>Polish</b> restores integrity (honeycomb wax): +0.25, or +0.40 with a pewter cauldron among the bench's
 *       enhancers. A snapped wand (integrity 0.10 or less) cannot be polished back; that is the Elder Wand's
 *       alone.</li>
 *   <li><b>Re-fit</b> moves flexibility one step stiffer or suppler (a log of the wand's own wood). It needs a
 *       well-furnished bench and knowledge of the grain (Wandlore, "The Grain of the Wood") -- or a spell
 *       teacher's lectern at the bench, whose texts stand in for it.</li>
 *   <li><b>Re-handle, re-tip, ornament</b> change the look only (any log): the next handle, tip or ornament.</li>
 * </ul>
 * Tier unlocks: looks at any bench of tier 1, polish at 2, re-fit at 4.
 */
@NullMarked
public final class WandRefinement {

    public static final float POLISH_TIER = 2.0f;
    public static final float REFIT_TIER = 4.0f;
    public static final float LOOK_TIER = 1.0f;
    public static final float POLISH = 0.25f;
    public static final float POLISH_WITH_CAULDRON = 0.40f;
    /** At or below this a wand is snapped: polish cannot bring it back. */
    public static final float SNAPPED = 0.10f;

    /** The enhancer whose presence makes polish stronger: a bath of varnish. */
    public static final Identifier CAULDRON = id("pewter_cauldron");
    /** The enhancer whose texts stand in for knowing the grain. */
    public static final Identifier LECTERN = id("spell_teacher");
    /** The Wandlore node a re-fit asks for. */
    public static final String GRAIN_NODE = "wandwood_grain";

    private WandRefinement() {}

    public enum Action {
        POLISH, STIFFER, SUPPLER, HANDLE, TIP, ORNAMENT;

        public String key() {
            return "gui.wizards_and_beasts.wand_refine.action." + name().toLowerCase(Locale.ROOT);
        }

        public boolean refit() {
            return this == STIFFER || this == SUPPLER;
        }

        public boolean look() {
            return this == HANDLE || this == TIP || this == ORNAMENT;
        }

        public float requiredTier() {
            return this == POLISH ? POLISH_TIER : refit() ? REFIT_TIER : LOOK_TIER;
        }
    }

    /** What the bench says about the action chosen; READY means the output slot holds the result. */
    public enum Status {
        READY, NO_WAND, NO_MATERIAL, WRONG_MATERIAL, TOO_PLAIN, NEEDS_GRAIN, SNAPPED, NOTHING_TO_DO;

        public String key() {
            return "gui.wizards_and_beasts.wand_refine.status." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** The bench as the rules see it: its tier and which enhancer blocks stand around it. */
    public record Bench(float tier, Set<Identifier> enhancers) {}

    /** What the material slot holds, as the rules need it. */
    public enum Material { NONE, HONEYCOMB, OWN_WOOD_LOG, OTHER_LOG, OTHER }

    public static float polishAmount(Bench bench) {
        return bench.enhancers().contains(CAULDRON) ? POLISH_WITH_CAULDRON : POLISH;
    }

    public static boolean needsGrain(Bench bench) {
        return !bench.enhancers().contains(LECTERN);
    }

    /** One step stiffer or suppler, or empty at the end of the scale (or for an action that is not a re-fit). */
    public static Optional<WandFlexibility> refit(WandFlexibility from, Action action) {
        int next = from.ordinal() + (action == Action.STIFFER ? -1 : action == Action.SUPPLER ? 1 : 0);
        if (!action.refit() || next < 0 || next >= WandFlexibility.values().length) {
            return Optional.empty();
        }
        return Optional.of(WandFlexibility.values()[next]);
    }

    /**
     * The next module after {@code current} in {@code choices} (sorted by id), wrapping; for an optional slot
     * ({@code allowNone}) "none" sits after the last.
     */
    public static Optional<Identifier> nextModule(List<Identifier> choices, @Nullable Identifier current, boolean allowNone) {
        if (choices.isEmpty()) {
            return Optional.empty();
        }
        List<Identifier> sorted = choices.stream().sorted(java.util.Comparator.comparing(Identifier::toString)).toList();
        int at = current == null ? -1 : sorted.indexOf(current);
        if (current == null) {
            return Optional.of(sorted.get(0));
        }
        if (at == sorted.size() - 1 && allowNone) {
            return Optional.empty();
        }
        return Optional.of(sorted.get((at + 1) % sorted.size()));
    }

    /** Whether the material slot holds what this action is paid with. */
    public static boolean pays(Action action, Material material) {
        return switch (action) {
            case POLISH -> material == Material.HONEYCOMB;
            case STIFFER, SUPPLER -> material == Material.OWN_WOOD_LOG;
            case HANDLE, TIP, ORNAMENT -> material == Material.OWN_WOOD_LOG || material == Material.OTHER_LOG;
        };
    }

    /**
     * What the bench says. Checked in the order a wandmaker would meet them: a wand, the bench's furnishing, the
     * knowledge, the material, and whether the action would change anything.
     */
    public static Status check(Action action, Bench bench, boolean wand, boolean knowsGrain, Material material,
                               float integrity, @Nullable WandFlexibility flexibility) {
        if (!wand) {
            return Status.NO_WAND;
        }
        if (bench.tier() < action.requiredTier()) {
            return Status.TOO_PLAIN;
        }
        if (action.refit() && needsGrain(bench) && !knowsGrain) {
            return Status.NEEDS_GRAIN;
        }
        if (action == Action.POLISH && integrity <= SNAPPED) {
            return Status.SNAPPED;
        }
        if (material == Material.NONE) {
            return Status.NO_MATERIAL;
        }
        if (!pays(action, material)) {
            return Status.WRONG_MATERIAL;
        }
        if (action == Action.POLISH && integrity >= 1.0f) {
            return Status.NOTHING_TO_DO;
        }
        if (action.refit() && (flexibility == null || refit(flexibility, action).isEmpty())) {
            return Status.NOTHING_TO_DO;
        }
        return Status.READY;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }
}
