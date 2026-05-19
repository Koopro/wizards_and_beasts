package at.koopro.wizardsandbeasts.client.spell.ui;

public final class SpellRejectReasonFormatter {
    private SpellRejectReasonFormatter() {}

    public static String toHudLabel(String reason) {
        if (reason == null || reason.isBlank()) {
            return "No recent spell rejection";
        }
        if (reason.startsWith("obscurial_dark_spell_restricted")) {
            return "Obscurus rejected non-dark spell";
        }
        if (reason.startsWith("obscurial_dark_only_spell_outside_dark_form")) {
            return "Ability requires dark form";
        }
        if (reason.startsWith("cooldown_active")) {
            return "Spell recharging";
        }
        if (reason.startsWith("collapse_instability_fizzle")) {
            return "Collapse instability disrupted cast";
        }
        if (reason.startsWith("obscurial_instability_fizzle")) {
            return "Obscurial instability disrupted cast";
        }
        if (reason.startsWith("requirements_unmet")) {
            return "Spell requirement not met";
        }
        if (reason.startsWith("spell_not_known")) {
            return "Spell not learned";
        }
        if (reason.startsWith("assign_obscurial_ability")) {
            return "Obscurial abilities are not spell slots";
        }
        if (reason.startsWith("ability_not_in_dark_form")) {
            return "Dark form required for ability";
        }
        if (reason.startsWith("ability_cooldown_active")) {
            return "Obscurial ability recharging";
        }
        if (reason.startsWith("ability_requires_ability_input")) {
            return "Use obscurial ability hotkeys";
        }
        if (reason.startsWith("not_holding_wand")) {
            return "Wand required";
        }
        if (reason.startsWith("wand_not_bonded")) {
            return "Wand has not chosen you";
        }
        if (reason.startsWith("wand_wrong_master")) {
            return "Wand serves another";
        }
        return reason;
    }
}
