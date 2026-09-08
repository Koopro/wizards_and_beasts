package at.koopro.wizardsandbeasts.skill.debug;

import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.PlayerSkillBonusData;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.skill.vocation.PlayerVocationData;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The skill web: points, unlocked nodes, the vocation framing them, and the bonuses they add up to.
 *
 * <p>The bonus block is the part that cannot be read anywhere else. A skill node's effect is a
 * multiplier folded into a computation somewhere downstream; whether it is actually reaching that
 * computation is the question, and printing the resolved {@link PlayerSkillBonusData} answers it
 * without having to cast anything.
 */
@NullMarked
public final class SkillFeatureDebug implements FeatureDebugSection {

    /** Unlocked nodes listed in full before the section switches to a count. */
    private static final int MAX_NODES = 20;

    @Override
    public String id() {
        return "skills";
    }

    @Override
    public String title() {
        return "Skills";
    }

    @Override
    public String summary() {
        return "Points, unlocked nodes, vocation, and the resolved bonuses they produce.";
    }

    @Override
    public @Nullable Module module() {
        return Module.SKILL_TREES;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        PlayerSkillData data = target.getData(ModAttachments.SKILL_DATA.get());
        PlayerVocationData vocation = target.getData(ModAttachments.VOCATION_DATA.get());

        report.row("  points", data.getSkillPoints() + " (earned " + data.getTotalPointsEarned() + ")");
        report.row("  nodes unlocked", data.getUnlockedSkills().size());
        report.row("  vocation", vocation.primary().map(Object::toString).orElse("(none)"));
        if (vocation.legacySecondaryPresent()) {
            report.warn("  a legacy secondary vocation is still stored on this player");
        }
        if (data.needsWebMigration()) {
            report.warn("  skill data is still on the pre-web layout and wants migrating");
        }
        if (detail == Detail.BRIEF) {
            return;
        }

        report.section("  unlocked");
        Map<String, Integer> unlocked = data.getUnlockedSkills();
        if (unlocked.isEmpty()) {
            report.row("    -", "none");
        } else {
            int shown = 0;
            for (Map.Entry<String, Integer> node : unlocked.entrySet()) {
                if (shown++ >= MAX_NODES) {
                    report.note("    ... " + (unlocked.size() - MAX_NODES) + " more");
                    break;
                }
                report.row("    " + node.getKey(), "level " + node.getValue());
            }
        }

        // What those nodes actually resolve to. See the class note.
        PlayerSkillBonusData bonuses = PlayerSkillBonusData.forPlayer(target);
        report.section("  resolved bonuses");
        report.row("    broom speed", String.format("%+.3f", bonuses.broomSpeedBonus()));
        appendMultipliers(report, "cooldown", bonuses.cooldownMultipliers());
        appendMultipliers(report, "damage", bonuses.damageMultipliers());
        report.row("    spell gate overrides", bonuses.spellGateOverrides().size());
        appendMultipliers(report, "bestiary xp", bonuses.bestiaryXpMultipliers());

        report.section("  tracked activity");
        report.row("    potions brewed (points)", data.getPotionBrewPoints());
        report.row("    plants harvested", data.getPlantsHarvested());
        report.row("    lore items read", data.getLoreItemsRead());
        report.row("    metamorph forms used", data.getMetamorphFormsUsed());
        report.row("    arithmancy / runes", data.getArithmancyInteractions()
                + " / " + data.getRunicInteractions());
        report.row("    divination / astronomy", data.getDivinationEvents()
                + " / " + data.getAstronomyEvents());
        report.row("    muggle items", data.getMuggleItems());
    }

    private static void appendMultipliers(DebugReport report, String label,
                                          Map<?, Float> multipliers) {
        if (multipliers.isEmpty()) {
            report.row("    " + label, "none");
            return;
        }
        multipliers.forEach((key, value) ->
                report.row("    " + label + " " + key, String.format("x%.3f", value)));
    }
}
