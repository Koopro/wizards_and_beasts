package at.koopro.wizardsandbeasts.brew.command;

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
import at.koopro.wizardsandbeasts.util.ChatReport;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
 * <p>So this prints <b>every field</b>, including the ones that ought to be uninteresting. A debug
 * command that only shows what its author expected to matter is a debug command that cannot surprise
 * you, which is the only thing they are for.
 *
 * <p>It also prints the blockstate's {@code visual} beside the block entity's phase. Those two
 * disagreeing is a specific, findable bug — the projection failing to run — and it is invisible
 * without putting them on adjacent lines.
 */
@NullMarked
public final class CauldronDebugCommands {

    /** How far a player can be from a cauldron and still inspect it. */
    private static final double REACH = 6.0;

    private CauldronDebugCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("cauldron")
                .then(Commands.literal("inspect")
                        .executes(ctx -> inspect(ctx.getSource(), null)))
                .then(Commands.literal("at")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> inspect(ctx.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos")))
                                .then(Commands.literal("fill")
                                        .executes(ctx -> drive(ctx.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"), "fill", null)))
                                .then(Commands.literal("start")
                                        .executes(ctx -> drive(ctx.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos"), "start", null)))
                                ))
                .executes(ctx -> inspect(ctx.getSource(), null));
    }

    /**
     * Perform one step of the ritual from a command instead of a right-click.
     *
     * <p>Exists because the whole flow was unreachable to any automated check: every gesture is a
     * right-click, and a right-click needs a human at a client. A bug that only appears in the
     * interaction path could therefore be neither reproduced nor confirmed fixed except by asking
     * somebody to go and try it — which is exactly how two of them shipped.
     *
     * <p>Drives the <em>same</em> block-entity methods the block does, so a result here is evidence
     * about the real path rather than about a parallel one.
     */
    private static int drive(CommandSourceStack source, BlockPos pos, String action,
                             @Nullable ItemStack item) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!(source.getLevel().getBlockEntity(pos) instanceof CauldronBlockEntity be)) {
            source.sendFailure(Component.literal("No cauldron block entity at " + pos.toShortString())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        BlockState before = source.getLevel().getBlockState(pos);
        String beforeVisual = visualOf(before);

        switch (action) {
            case "fill" -> be.setFilled(true);
            case "start" -> {
                CauldronBlockEntity.StartResult result = be.startBrewing(
                        ((WizardingCauldronBlock) before.getBlock()).tier(), player.getUUID());
                source.sendSuccess(() -> Component.literal("startBrewing -> " + result), false);
            }
            default -> {
                source.sendFailure(Component.literal("unknown action " + action));
                return 0;
            }
        }

        BlockState after = source.getLevel().getBlockState(pos);
        String afterVisual = visualOf(after);
        source.sendSuccess(() -> Component.literal(
                "visual " + beforeVisual + " -> " + afterVisual
                        + "  |  filled=" + be.isFilled()
                        + "  phase=" + be.phase()).withStyle(
                beforeVisual.equals(afterVisual) && !"empty".equals(afterVisual)
                        ? ChatFormatting.YELLOW : ChatFormatting.GREEN), false);
        return 1;
    }

    private static String visualOf(BlockState state) {
        return state.hasProperty(CauldronVisual.PROPERTY)
                ? state.getValue(CauldronVisual.PROPERTY).getSerializedName()
                : "NO PROPERTY";
    }

    private static int inspect(CommandSourceStack source, @Nullable BlockPos explicitPos)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        BlockPos pos;
        if (explicitPos != null) {
            pos = explicitPos;
        } else {
            HitResult hit = player.pick(REACH, 1.0f, false);
            if (hit.getType() != HitResult.Type.BLOCK) {
                source.sendFailure(Component.literal("Look at a cauldron.").withStyle(ChatFormatting.RED));
                return 0;
            }
            pos = ((BlockHitResult) hit).getBlockPos();
        }
        BlockState state = player.level().getBlockState(pos);
        if (!(state.getBlock() instanceof WizardingCauldronBlock cauldron)) {
            source.sendFailure(Component.literal(
                    "That is not a cauldron (" + state.getBlock().getName().getString() + ").")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!(player.level().getBlockEntity(pos) instanceof CauldronBlockEntity be)) {
            source.sendFailure(Component.literal(
                    "Cauldron has NO BLOCK ENTITY — that is the bug.").withStyle(ChatFormatting.RED));
            return 0;
        }

        ChatReport report = ChatReport.of("Cauldron @ " + pos.toShortString());
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
        report.state("blockstate visual", visual, visual.equals(expected) ? 0x55FF55 : 0xFF5555);
        if (!visual.equals(expected)) {
            report.subtitle("MISMATCH — blockstate should be '" + expected + "'");
        }

        report.flag("filled (water)", be.isFilled());
        report.flag("heat below", CauldronHeat.hasHeatSource(player.level(), pos.below()));

        // Contents, one line each. "empty" is printed explicitly rather than omitted, because an
        // absent section reads as "the command did not check" rather than "there is nothing there".
        report.subtitle("contents (" + be.usedSlots() + "/" + CauldronBlockEntity.SLOTS + ")");
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
            report.subtitle("brew");
            Brew brew = Brews.byId(be.brewId());
            report.row("  brewId", be.brewId() == null ? "null" : be.brewId());
            report.row("  resolves", brew == null ? "NO — unknown brew id" : "yes");
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
            report.row("  failure chance", String.format("%.0f%%",
                    BrewFailure.chanceFor(player, base, missed) * 100f));
            if (recipe != null && recipe.catalyst().isPresent()) {
                BrewingRecipe.Catalyst c = recipe.catalyst().get();
                report.row("  catalyst", new ItemStack(c.item()).getHoverName().getString()
                        + " @ " + pct(c.windowStart()) + "–" + pct(c.windowEnd()));
            }
        }

        report.row("brewer", be.brewerId().map(java.util.UUID::toString).orElse("none"));
        report.row("spoil ticks", be.spoilTicksWithoutHeatForDebug() + " / "
                + CauldronBlockEntity.SPOIL_TICKS_WITHOUT_HEAT);

        // What the pot would do next. The single most useful line when a player says "nothing
        // happens": it states, from the server's own view, which gesture is currently live.
        report.subtitle("next action");
        report.row("  empty hand", nextEmptyHand(be));
        report.row("  ingredient", nextIngredient(be));
        report.row("  glass bottle", nextBottle(be));

        report.send(source);
        return 1;
    }

    private static String pct(float f) {
        return Math.round(f * 100f) + "%";
    }

    private static int colourFor(CauldronPhase phase) {
        return switch (phase) {
            case IDLE -> 0xAAAAAA;
            case BREWING -> 0x55FFFF;
            case DONE -> 0x55FF55;
            case SPOILED -> 0xFF5555;
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
