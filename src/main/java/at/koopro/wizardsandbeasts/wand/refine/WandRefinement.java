package at.koopro.wizardsandbeasts.wand.refine;

import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.resources.Identifier;
import at.koopro.wizardsandbeasts.wand.bench.BenchTool;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The bench's second role, as rules: what can be done to a finished wand, what each asks of the bench and the
 * wandmaker, and what it changes. Pure, so every rule is unit-tested; {@link WandRefineMenu} gathers the inputs and
 * applies the answer.
 *
 * <p>User decision (2026-10-22): limited customisation, never risky. Nothing is ever destroyed, and wood, core and
 * the wand's bond with its master never change.
 * <ul>
 *   <li><b>Polish</b> restores integrity (honeycomb wax): +0.25, or +0.40 with the varnish pot on the bench. A
 *       snapped wand (integrity 0.10 or less) cannot be polished back; that is the Elder Wand's alone.</li>
 *   <li><b>Re-fit</b> moves flexibility one step stiffer or suppler (a log of the wand's own wood). It needs the
 *       lathe chisel on the bench and knowledge of the grain (Wandlore, "The Grain of the Wood").</li>
 *   <li><b>Re-handle, re-tip, ornament</b> change the look only (any log): the next handle, tip or ornament.</li>
 * </ul>
 * The bench's tools are what it asks for (2026-11-26 remake). Until then a bench was graded by a tier, summed from
 * the bookshelves, candles and cauldrons around it: polish asked tier 2, re-fit tier 4, and a lectern stood in for
 * the grain.
 *
 * <p><b>Whose wand</b> (systems audit B5, Wandmaker, 2026-11-25): a wizard cares only for a wand that answers to
 * them, or to no one. A wand that answers to somebody else is a Wandmaker's work (vocation flag
 * {@link #CARE_FOR_OTHERS}): Ollivander mends Lucius Malfoy's wand and weighs Wormtail's, and Gregorovitch's trade is
 * other people's wands (DH ch.24, ch.14). The bond never changes hands at the bench. A Wandmaker's polish also draws
 * {@link #CLEANSE} of the dark residue out of the wood.
 */
@NullMarked
public final class WandRefinement {

    public static final float POLISH = 0.25f;
    public static final float POLISH_WITH_VARNISH = 0.40f;
    /** At or below this a wand is snapped: polish cannot bring it back. */
    public static final float SNAPPED = 0.10f;

    /** The Wandlore node a re-fit asks for. */
    public static final String GRAIN_NODE = "wandwood_grain";
    /** Vocation flag: may refine a wand that answers to someone else, and polish cleanses. */
    public static final String CARE_FOR_OTHERS = "wand_care_for_others";
    /** Corruption a Wandmaker's polish takes away. */
    public static final float CLEANSE = 0.25f;

    private WandRefinement() {}

    public enum Action {
        POLISH, STIFFER, SUPPLER, HANDLE, ORNAMENT;

        public String key() {
            return "gui.wizards_and_beasts.wand_refine.action." + name().toLowerCase(Locale.ROOT);
        }

        public boolean refit() {
            return this == STIFFER || this == SUPPLER;
        }

        public boolean look() {
            return this == HANDLE || this == ORNAMENT;
        }

        /** The tool this action needs on the bench, or {@code null}. A re-fit re-turns the wood: the chisel. */
        public @Nullable BenchTool requiredTool() {
            return refit() ? BenchTool.CHISEL : null;
        }
    }

    /** What the bench says about the action chosen; READY means the output slot holds the result. */
    public enum Status {
        READY, NO_WAND, NO_MATERIAL, WRONG_MATERIAL, NEEDS_TOOL, NEEDS_GRAIN, SNAPPED, NOTHING_TO_DO, NOT_YOURS;

        public String key() {
            return "gui.wizards_and_beasts.wand_refine.status." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** The bench as the rules see it: the tools on it, as {@link BenchTool#bit()}s. */
    public record Bench(int tools) {
        public static final Bench BARE = new Bench(0);

        public boolean has(BenchTool tool) {
            return (tools & tool.bit()) != 0;
        }
    }

    /** What the material slot holds, as the rules need it. */
    public enum Material { NONE, HONEYCOMB, OWN_WOOD_LOG, OTHER_LOG, OTHER }

    public static float polishAmount(Bench bench) {
        return bench.has(BenchTool.VARNISH) ? POLISH_WITH_VARNISH : POLISH;
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
            case HANDLE, ORNAMENT -> material == Material.OWN_WOOD_LOG || material == Material.OTHER_LOG;
        };
    }

    /**
     * What the bench says. Checked in the order a wandmaker would meet them: a wand, the bench's tools, the
     * knowledge, the material, and whether the action would change anything.
     */
    public static Status check(Action action, Bench bench, boolean wand, boolean knowsGrain, Material material,
                               float integrity, @Nullable WandFlexibility flexibility) {
        return check(action, bench, wand, knowsGrain, material, integrity, flexibility, Hands.OWN);
    }

    /** Who stands at the bench, as the rules need it: whose wand it is, and whether they are a Wandmaker. */
    public record Hands(boolean anothersWand, boolean wandmaker, float corruption) {
        public static final Hands OWN = new Hands(false, false, 0.0f);

        /** Whether this polish also cleanses: a Wandmaker's, on a wand with dark residue in it. */
        public boolean cleanses() {
            return wandmaker && corruption > 0.0f;
        }
    }

    /** As {@link #check(Action, Bench, boolean, boolean, Material, float, WandFlexibility)}, for these hands. */
    public static Status check(Action action, Bench bench, boolean wand, boolean knowsGrain, Material material,
                               float integrity, @Nullable WandFlexibility flexibility, Hands hands) {
        if (!wand) {
            return Status.NO_WAND;
        }
        if (hands.anothersWand() && !hands.wandmaker()) {
            return Status.NOT_YOURS;
        }
        BenchTool tool = action.requiredTool();
        if (tool != null && !bench.has(tool)) {
            return Status.NEEDS_TOOL;
        }
        if (action.refit() && !knowsGrain) {
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
        if (action == Action.POLISH && integrity >= 1.0f && !hands.cleanses()) {
            return Status.NOTHING_TO_DO;
        }
        if (action.refit() && (flexibility == null || refit(flexibility, action).isEmpty())) {
            return Status.NOTHING_TO_DO;
        }
        return Status.READY;
    }
}
