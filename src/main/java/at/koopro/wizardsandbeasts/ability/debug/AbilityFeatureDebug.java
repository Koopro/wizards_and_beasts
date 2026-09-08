package at.koopro.wizardsandbeasts.ability.debug;

import at.koopro.wizardsandbeasts.ability.data.PlayerAbilityData;
import at.koopro.wizardsandbeasts.ability.data.PlayerAbilityProficiency;
import at.koopro.wizardsandbeasts.ability.grant.AbilityGrantService;
import at.koopro.wizardsandbeasts.ability.grant.AbilityGrants;
import at.koopro.wizardsandbeasts.ability.select.AbilitySelectionState;
import at.koopro.wizardsandbeasts.command.debug.feature.FeatureDebugSection;
import at.koopro.wizardsandbeasts.command.debug.report.DebugReport;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.util.ChatPalette;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The ability framework: what this player has been granted, by what, and what they have selected.
 *
 * <p>Grants are <em>computed</em>, not stored — heritage, vocation, skill nodes, a debug grant and a
 * status effect can each hand out the same ability. So "why can they Apparate" has five possible
 * answers, and the source set is the only thing that distinguishes them. Printing the sources beside
 * each key is the point of this section.
 */
@NullMarked
public final class AbilityFeatureDebug implements FeatureDebugSection {

    @Override
    public String id() {
        return "abilities";
    }

    @Override
    public String title() {
        return "Abilities";
    }

    @Override
    public String summary() {
        return "Granted abilities and where each grant comes from, plus slots and cooldowns.";
    }

    @Override
    public @Nullable Module module() {
        return Module.PLAYER_ABILITIES;
    }

    @Override
    public void append(DebugReport report, ServerPlayer target, Detail detail) {
        AbilityGrants grants = AbilityGrantService.compute(target);
        AbilitySelectionState selection = target.getData(ModAttachments.ABILITY_SELECTION.get());
        long now = target.level().getGameTime();

        report.row("  granted", grants.keys().size());
        report.row("  selected", selection.selected() == null
                ? "(none)" : selection.selected().toString());
        if (detail == Detail.BRIEF) {
            return;
        }

        report.section("  grants");
        if (grants.isEmpty()) {
            report.row("    -", "none");
        } else {
            grants.keys().forEach(key ->
                    report.row("    " + key, grants.sourcesOf(key).toString()));
        }

        report.section("  quick slots");
        for (int slot = 0; slot < AbilitySelectionState.QUICK_SLOT_COUNT; slot++) {
            Identifier bound = selection.quickSlot(slot);
            report.row("    " + slot, bound == null ? "(empty)" : bound.toString());
        }

        report.section("  toggles");
        if (selection.toggles().isEmpty()) {
            report.row("    -", "none on");
        } else {
            selection.toggles().forEach(id -> report.state("    " + id, "on", ChatPalette.OK));
        }

        report.section("  cooldowns");
        boolean anyCooldown = false;
        for (Map.Entry<Identifier, Long> entry : selection.cooldowns().entrySet()) {
            long remaining = selection.cooldownRemaining(entry.getKey(), now);
            if (remaining <= 0) continue;
            anyCooldown = true;
            report.row("    " + entry.getKey(), remaining + "t");
        }
        if (!anyCooldown) {
            report.row("    -", "none active");
        }

        report.section("  proficiency");
        PlayerAbilityProficiency proficiency = target.getData(ModAttachments.ABILITY_PROFICIENCY.get());
        if (proficiency.values().isEmpty()) {
            report.row("    -", "nothing practised");
        } else {
            proficiency.values().forEach((id, value) -> report.bar("    " + id, value));
        }

        // The legacy per-ability fields. Still the authority for Apparition's licence and splinch
        // state, so they belong in the same dump as the framework that superseded the rest of them.
        PlayerAbilityData data = target.getData(ModAttachments.PLAYER_ABILITY_DATA.get());
        report.section("  apparition");
        report.flag("    unlocked", data.apparitionUnlocked());
        report.flag("    licensed", data.apparitionLicensed());
        report.row("    cooldown", data.apparitionCooldownTicks() + "t");
        report.row("    splinch", "severity " + data.splinchSeverity()
                + ", " + data.splinchTicksRemaining() + "t left");

        report.section("  mind");
        report.bar("    occlumency", data.occlumencyLevel());
        report.row("    legilimency cooldown", data.legilimencyCooldownTicks() + "t");
        report.bar("    wandless casting", data.wandlessCastingLevel());
        report.flag("    parseltongue", data.parseltongueSpeaker());
        report.row("    parseltongue source", data.parseltongueSource() == null
                ? "(none)" : data.parseltongueSource());
        report.row("  ability flags", data.abilityFlags().isEmpty()
                ? "none" : String.join(", ", data.abilityFlags()));
    }
}
