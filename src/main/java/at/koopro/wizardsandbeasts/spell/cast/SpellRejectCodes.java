package at.koopro.wizardsandbeasts.spell.cast;

public final class SpellRejectCodes {
    public static final String NOT_SERVER_LEVEL = "not_server_level";
    public static final String NOT_HOLDING_WAND = "not_holding_wand";
    public static final String NO_ACTIVE_SPELL = "no_active_spell";
    public static final String UNKNOWN_SPELL = "unknown_spell";
    public static final String SPELL_NOT_KNOWN = "spell_not_known";
    public static final String ABILITY_REQUIRES_ABILITY_INPUT = "ability_requires_ability_input";
    public static final String REQUIREMENTS_UNMET = "requirements_unmet";
    public static final String OBSCURIAL_DARK_ONLY_OUTSIDE_FORM = "obscurial_dark_only_spell_outside_dark_form";
    public static final String OBSCURIAL_DARK_RESTRICTED = "obscurial_dark_spell_restricted";
    public static final String COOLDOWN_ACTIVE = "cooldown_active";
    public static final String COLLAPSE_INSTABILITY_FIZZLE = "collapse_instability_fizzle";
    public static final String OBSCURIAL_INSTABILITY_FIZZLE = "obscurial_instability_fizzle";
    public static final String DUPLICATE_RELEASE_GUARD = "duplicate_release_guard";

    public static final String ASSIGN_UNKNOWN_SPELL = "assign_unknown_spell";
    public static final String ASSIGN_UNLEARNED_SPELL = "assign_unlearned_spell";
    public static final String ASSIGN_OBSCURIAL_ABILITY = "assign_obscurial_ability";
    public static final String ASSIGN_TYPE_RESTRICTED_SPELL = "assign_type_restricted_spell";

    public static final String ABILITY_NOT_IN_DARK_FORM = "ability_not_in_dark_form";
    public static final String ABILITY_UNKNOWN = "ability_unknown";
    public static final String ABILITY_SPELL_MISSING = "ability_spell_missing";
    public static final String ABILITY_COOLDOWN_ACTIVE = "ability_cooldown_active";
    public static final String ABILITY_REQUIREMENTS_UNMET = "ability_requirements_unmet";
    public static final String ABILITY_EXECUTE_FAILED = "ability_execute_failed";

    private SpellRejectCodes() {}

    public static String withDetail(String code, String detail) {
        if (detail == null || detail.isBlank()) return code;
        return code + ":" + detail;
    }
}
