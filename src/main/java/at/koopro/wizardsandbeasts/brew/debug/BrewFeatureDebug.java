package at.koopro.wizardsandbeasts.brew.debug;

import at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The brewing catalogue, and any pot this wizard has left on the boil nearby.
 *
 * <p>Two questions, both awkward from anywhere else. Whether the datapack content loaded at all —
 * an empty catalogue makes every cauldron refuse for a reason that looks like a cauldron bug — and
 * whether one of their own pots is about to spoil somewhere behind them.
 */
@NullMarked
public final class BrewFeatureDebug implements FeatureDebugSection {

    /** How far around the player to sweep for their own cauldrons. */
    private static final int SEARCH_RADIUS = 16;
    private static final int MAX_LISTED = 6;

    @Override
    public String id() {
        return "brewing";
    }

    @Override
    public String title() {
        return "Brewing";
    }

    @Override
    public String summary() {
        return "Loaded brew/recipe counts, and this player's own cauldrons within 16 blocks.";
    }

    @Override
    public @Nullable Module module() {
        return Module.MAGIZOOLOGY;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        int brews = Brews.all().size();
        int recipes = BrewingRecipes.all().size();
        report.state("  brews loaded", String.valueOf(brews),
                brews == 0 ? ChatPalette.BAD : ChatPalette.OK);
        report.state("  recipes loaded", String.valueOf(recipes),
                recipes == 0 ? ChatPalette.BAD : ChatPalette.OK);
        if (brews == 0 || recipes == 0) {
            report.warn("  datapack brewing content is missing - every cauldron will refuse");
        }
        if (detail == Detail.BRIEF) {
            return;
        }

        report.section("  your cauldrons within " + SEARCH_RADIUS + " blocks");
        int found = 0;
        ServerLevel level = target.level();
        BlockPos origin = target.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            // getBlockEntity on a loaded position only; betweenClosed can walk out of the loaded
            // area near a chunk border and asking there would force-load it from a debug dump.
            if (!level.isLoaded(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof CauldronBlockEntity be)) continue;
            if (be.brewerId().filter(target.getUUID()::equals).isEmpty()) continue;
            if (found++ >= MAX_LISTED) {
                report.note("    ... more not listed");
                break;
            }
            report.row("    " + pos.immutable().toShortString(),
                    be.phase() + ", " + Math.round(be.progress() * 100f) + "%");
        }
        if (found == 0) {
            report.row("    -", "none");
        }
    }
}
