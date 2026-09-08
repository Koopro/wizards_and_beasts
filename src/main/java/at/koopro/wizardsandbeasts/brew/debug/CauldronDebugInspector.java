package at.koopro.wizardsandbeasts.brew.debug;

import at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity;
import at.koopro.wizardsandbeasts.block.brew.WizardingCauldronBlock;
import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.BrewFailure;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import at.koopro.wizardsandbeasts.brew.CauldronHeat;
import at.koopro.wizardsandbeasts.brew.CauldronPhase;
import at.koopro.wizardsandbeasts.brew.CauldronVisual;
import at.koopro.wizardsandbeasts.command.debug.inspect.DebugInspector;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * What is actually in the pot you are looking at.
 *
 * <h2>Why this exists</h2>
 * <p>Brewing moved onto a block entity, and a block entity is invisible. When a cauldron does not
 * behave, there is no way from inside the game to tell whether the ingredients went in, whether the
 * water registered, whether the phase advanced, or whether the fire underneath counts — and guessing
 * at it from the outside is how the empty-hand interaction bug survived being written, shipped and
 * documented without anybody noticing it did nothing.
 *
 * <p>So this reports <b>every field</b>, including the ones that ought to be uninteresting. A debug
 * dump that only shows what its author expected to matter is a dump that cannot surprise you, which
 * is the only thing they are for.
 *
 * <p>It also reports the blockstate's {@code visual} beside the block entity's phase. Those two
 * disagreeing is a specific, findable bug — the projection failing to run — and it is invisible
 * without putting them on adjacent lines.
 *
 * <p>Used to print into chat from {@code /wandb debug cauldron}. It still can, but the dump is
 * thirty lines and chat is the wrong place to read thirty lines about the thing under your
 * crosshair: it scrolls away the conversation, and by the time you have read it you are no longer
 * looking at the pot. The report is data now, and the panel beside the cauldron draws the same one.
 */
@NullMarked
public final class CauldronDebugInspector implements DebugInspector.OfBlock {

    @Override
    public String id() {
        return "cauldron";
    }

    @Override
    public String summary() {
        return "Phase, heat, contents, timer, catalyst window and what each gesture would do.";
    }

    @Override
    public boolean matches(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof WizardingCauldronBlock;
    }

    @Override
    public DebugReport inspect(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer viewer) {
        DebugReport report = DebugReport.of("Cauldron @ " + pos.toShortString());
        if (!(state.getBlock() instanceof WizardingCauldronBlock cauldron)) {
            return report.warn("not a cauldron");
        }
        if (!(level.getBlockEntity(pos) instanceof CauldronBlockEntity be)) {
            return report.warn("Cauldron has NO BLOCK ENTITY — that is the bug.");
        }
        report.row("tier", cauldron.tier().getSerializedName());

        // Phase and visual side by side. If these disagree the projection is broken, and that is a
        // different bug from anything the phase alone could tell you.
        CauldronPhase phase = be.phase();
        String visual = state.hasProperty(CauldronVisual.PROPERTY)
                ? state.getValue(CauldronVisual.PROPERTY).getSerializedName()
                : "NO PROPERTY";
        String expected = CauldronVisual.of(phase, be.isFilled(), !be.isEmptyOfIngredients())
                .getSerializedName();
        report.state("phase", phase.name(), colourFor(phase));
        report.state("blockstate visual", visual,
                visual.equals(expected) ? ChatPalette.OK : ChatPalette.BAD);
        if (!visual.equals(expected)) {
            report.warn("MISMATCH — blockstate should be '" + expected + "'");
        }

        report.flag("filled (water)", be.isFilled());
        report.flag("heat below", CauldronHeat.hasHeatSource(level, pos.below()));

        // Contents, one line each. "empty" is printed explicitly rather than omitted, because an
        // absent section reads as "the dump did not check" rather than "there is nothing there".
        report.section("contents (" + be.usedSlots() + "/" + CauldronBlockEntity.SLOTS + ")");
        if (be.isEmptyOfIngredients()) {
            report.row("  —", "empty");
        } else {
            for (ItemStack stack : be.contentsForDrop()) {
                if (!stack.isEmpty()) {
                    report.row("  " + stack.getCount() + "x", stack.getHoverName().getString());
                }
            }
        }

        if (phase == CauldronPhase.BREWING || phase == CauldronPhase.DONE) {
            appendBrew(report, level, be, viewer);
        }

        report.row("brewer", be.brewerId().map(UUID::toString).orElse("none"));
        report.row("spoil ticks", be.spoilTicksWithoutHeatForDebug() + " / "
                + CauldronBlockEntity.SPOIL_TICKS_WITHOUT_HEAT);

        // What the pot would do next. The single most useful line when a player says "nothing
        // happens": it states, from the server's own view, which gesture is currently live.
        report.section("next action");
        report.row("  empty hand", nextEmptyHand(be));
        report.row("  ingredient", nextIngredient(be));
        report.row("  glass bottle", nextBottle(be));
        return report;
    }

    private static void appendBrew(DebugReport report, ServerLevel level,
                                   CauldronBlockEntity be, ServerPlayer viewer) {
        report.section("brew");
        Brew brew = Brews.byId(be.brewId());
        report.row("  brewId", be.brewId() == null ? "null" : be.brewId());
        report.state("  resolves", brew == null ? "NO — unknown brew id" : "yes",
                brew == null ? ChatPalette.BAD : ChatPalette.OK);
        report.row("  recipeId", be.recipeId() == null ? "null" : be.recipeId());
        report.row("  ticks", be.remainingTicks() + " / " + be.totalTicks());
        report.bar("  progress", be.progress());
        report.flag("  catalyst added", be.isCatalystAdded());
        report.row("  penalties", String.format("%.2f", be.accumulatedPenalties()));

        BrewingRecipe recipe = be.recipeId() == null ? null : BrewingRecipes.byId(be.recipeId());
        float base = recipe == null ? 0f : recipe.failureChance();
        float missed = be.accumulatedPenalties();
        if (recipe != null && recipe.catalyst().isPresent() && !be.isCatalystAdded()) {
            missed += recipe.catalyst().get().missPenalty();
        }

        // Scored against the brewer, not whoever happens to be looking. Failure chance reads the
        // brewer's potion skill; asking it about a bystander answers a question nobody asked, and
        // quietly reports a number the pot will never use.
        ServerPlayer brewer = resolveBrewer(level, be);
        report.row("  failure chance", String.format("%.0f%%",
                BrewFailure.chanceFor(brewer, base, missed) * 100f));
        if (brewer == null && be.brewerId().isPresent()) {
            report.note("  (brewer offline — chance scored with no skill bonus)");
        }
        if (recipe != null && recipe.catalyst().isPresent()) {
            BrewingRecipe.Catalyst c = recipe.catalyst().get();
            report.row("  catalyst", new ItemStack(c.item()).getHoverName().getString()
                    + " @ " + pct(c.windowStart()) + "–" + pct(c.windowEnd()));
        }
    }

    private static @Nullable ServerPlayer resolveBrewer(ServerLevel level, CauldronBlockEntity be) {
        return be.brewerId().map(id -> level.getServer().getPlayerList().getPlayer(id)).orElse(null);
    }

    private static String pct(float f) {
        return Math.round(f * 100f) + "%";
    }

    private static int colourFor(CauldronPhase phase) {
        return switch (phase) {
            case IDLE -> ChatPalette.MUTED;
            case BREWING -> ChatPalette.ACCENT;
            case DONE -> ChatPalette.OK;
            case SPOILED -> ChatPalette.BAD;
        };
    }

    private static String nextEmptyHand(CauldronBlockEntity be) {
        return switch (be.phase()) {
            case IDLE -> !be.isFilled() ? "refuse: needs water"
                    : be.isEmptyOfIngredients() ? "refuse: pot is empty"
                    : "START BREWING (sneak = take last ingredient back)";
            case BREWING -> "refuse: mid-brew";
            case DONE -> "refuse: needs a glass bottle";
            case SPOILED -> "pour away";
        };
    }

    private static String nextIngredient(CauldronBlockEntity be) {
        return switch (be.phase()) {
            case IDLE -> be.isFilled() ? "add one to the pot" : "refuse: needs water";
            case BREWING -> "timed add (catalyst or contamination)";
            case DONE, SPOILED -> "refuse";
        };
    }

    private static String nextBottle(CauldronBlockEntity be) {
        return switch (be.phase()) {
            case IDLE -> "start brewing";
            case BREWING -> "refuse: mid-brew";
            case DONE -> "BOTTLE IT";
            case SPOILED -> "bottle the sludge";
        };
    }
}
