package at.koopro.wizardsandbeasts.wand;

import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceRules;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondState;
import at.koopro.wizardsandbeasts.wand.registry.WandTemperament;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A wand's relationship with a wizard, and its condition and character, in words — for tooltips and the
 * character sheet. The one place that wording lives, so the two cannot describe the same wand differently.
 *
 * <p>Words rather than numbers on purpose: "answers to you, grudgingly" is something a player can act on;
 * "Allegiance: 0.23" was not. The numbers remain on the advanced tooltip.
 *
 * <p>Side-safe: reads only network-synchronized data components and, where given, registries.
 */
public final class WandEligibility {

    private WandEligibility() {}

    /**
     * @param eligible    the wand will cast for this wizard at all (however poorly)
     * @param reason      why not, or why it will serve them poorly; {@code null} when it serves them properly
     * @param detailLine1 the relationship
     * @param detailLine2 the condition
     * @param detailLine3 a note on its history, if any
     */
    public record Result(
            boolean eligible,
            @Nullable Component reason,
            @Nullable Component detailLine1,
            @Nullable Component detailLine2,
            @Nullable Component detailLine3
    ) {}

    public static Result evaluate(@NonNull Player player, @NonNull ItemStack wandStack) {
        HolderLookup.Provider registries = player.level().registryAccess();
        WandBondState state = WandAllegianceService.stateFor(player.getUUID(), wandStack);
        boolean answers = WandAllegianceService.answersToSomeone(wandStack);
        boolean backfires = answers && WandAllegianceService.wouldBackfire(player, wandStack, registries);

        Component reason = null;
        if (!answers) {
            reason = Component.translatable("wandcraft.eligibility.reason.not_attuned");
        } else if (backfires) {
            reason = WandComponents.getIntegrity(wandStack) <= WandAllegianceRules.BROKEN_AT
                    ? Component.translatable("wandcraft.eligibility.reason.broken")
                    : Component.translatable("wandcraft.eligibility.reason.backfire");
        } else if (state == WandBondState.UNFAMILIAR) {
            reason = Component.translatable("wandcraft.eligibility.reason.foreign");
        }

        Component history = WandAllegianceService.passedOn(wandStack) && state.isMaster()
                ? Component.translatable("wandcraft.tooltip.bond.passed_on") : null;
        return new Result(answers && !backfires, reason, bondLine(player, wandStack), conditionLine(wandStack), history);
    }

    /** "Answers to you, grudgingly", coloured by how well. */
    public static Component bondLine(@NonNull Player viewer, @NonNull ItemStack wand) {
        if (!WandAllegianceService.answersToSomeone(wand)) {
            return Component.translatable("wandcraft.tooltip.bond.unchosen").withStyle(ChatFormatting.GRAY);
        }
        if (WandComponents.getMaster(wand).isEmpty()) {
            return Component.translatable("wandcraft.tooltip.bond.elder_unmastered").withStyle(ChatFormatting.DARK_GRAY);
        }
        return switch (WandAllegianceService.stateFor(viewer.getUUID(), wand)) {
            case UNFAMILIAR -> Component.translatable("wandcraft.tooltip.bond.unfamiliar").withStyle(ChatFormatting.RED);
            case RELUCTANT -> Component.translatable("wandcraft.tooltip.bond.reluctant").withStyle(ChatFormatting.GOLD);
            case ACCEPTING -> Component.translatable("wandcraft.tooltip.bond.accepting").withStyle(ChatFormatting.YELLOW);
            case LOYAL -> Component.translatable("wandcraft.tooltip.bond.loyal").withStyle(ChatFormatting.GREEN);
            case MASTERED -> Component.translatable("wandcraft.tooltip.bond.mastered").withStyle(ChatFormatting.AQUA);
        };
    }

    /** "Condition: Cracked". */
    public static Component conditionLine(@NonNull ItemStack wand) {
        WandAllegianceRules.Condition condition = WandAllegianceRules.condition(WandComponents.getIntegrity(wand));
        ChatFormatting colour = switch (condition) {
            case SOUND -> ChatFormatting.GREEN;
            case WORN -> ChatFormatting.YELLOW;
            case DAMAGED -> ChatFormatting.GOLD;
            case BROKEN -> ChatFormatting.RED;
        };
        return Component.translatable("wandcraft.tooltip.condition",
                Component.translatable("wandcraft.tooltip.condition." + condition.name().toLowerCase(java.util.Locale.ROOT)))
                .withStyle(colour);
    }

    /** A line only when Dark magic has marked the wand; nothing otherwise. */
    public static @Nullable Component corruptionLine(@NonNull ItemStack wand) {
        float corruption = WandComponents.getCorruption(wand);
        if (corruption >= 1.0f) {
            return Component.translatable("wandcraft.tooltip.corruption.steeped").withStyle(ChatFormatting.DARK_RED);
        }
        if (corruption >= 0.5f) {
            return Component.translatable("wandcraft.tooltip.corruption.tainted").withStyle(ChatFormatting.DARK_RED);
        }
        return null;
    }

    /**
     * The wand's character, read from its temperament: one short line per trait that sets it apart. A reading of
     * the definitions' data, so a datapack wood with a temperament describes itself.
     */
    public static List<Component> characterLines(@NonNull ItemStack wand, HolderLookup.@Nullable Provider registries) {
        List<Component> lines = new ArrayList<>();
        if (WandAllegianceService.isElderWand(wand)) {
            lines.add(trait("elder"));
        }
        WandTemperament t = WandAllegianceService.temperamentOf(wand, registries);
        int wins = WandAllegianceRules.winsToTransfer(t, WandAllegianceService.isElderWand(wand));
        if (t.passedOnPower() < 1.0f) lines.add(trait("clings"));
        if (wins > WandAllegianceRules.BASE_WINS_TO_TRANSFER) lines.add(trait("hard_won"));
        if (wins < WandAllegianceRules.BASE_WINS_TO_TRANSFER && !WandAllegianceService.isElderWand(wand)) lines.add(trait("easily_won"));
        if (t.transferBondBonus() > 0.0f) lines.add(trait("accepts"));
        if (t.bondNeedsDanger()) lines.add(trait("danger"));
        if (t.bondGrowth() < 0.9f) lines.add(trait("slow_bond"));
        if (t.bondGrowth() > 1.1f) lines.add(trait("quick_bond"));
        if (t.darkArtsBondCost() > 0.0f) lines.add(trait("dark"));
        if (t.backfiresInForeignHands()) lines.add(trait("backfire"));
        if (t.masteryNeedsDeathWitness()) lines.add(trait("death"));
        if (t.loyalCooldown() < 1.0f) lines.add(trait("loyal_quick"));
        return lines;
    }

    private static Component trait(String key) {
        return Component.literal("  ").append(Component.translatable("wandcraft.tooltip.trait." + key))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    }
}
