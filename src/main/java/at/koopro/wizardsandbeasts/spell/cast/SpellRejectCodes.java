package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.spell.core.*;

import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

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
    /** Held wand has no bonded master (resonance never matched). */
    public static final String WAND_NOT_BONDED = "wand_not_bonded";
    /** Held wand is bonded to another player. */
    public static final String WAND_WRONG_MASTER = "wand_wrong_master";
    public static final String LANGLOCKED = "langlocked";

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

    /**
     * Reasons recorded on wand spell release / {@link at.koopro.wizardsandbeasts.spell.cast.SpellCastService}
     * and early {@link at.koopro.wizardsandbeasts.network.SpellCastC2SPacket} gates (no {@code cast_} prefix).
     */
    private static final Set<String> WAND_RELEASE_BASES = Set.of(
            NOT_SERVER_LEVEL,
            NOT_HOLDING_WAND,
            NO_ACTIVE_SPELL,
            UNKNOWN_SPELL,
            SPELL_NOT_KNOWN,
            ABILITY_REQUIRES_ABILITY_INPUT,
            REQUIREMENTS_UNMET,
            OBSCURIAL_DARK_ONLY_OUTSIDE_FORM,
            OBSCURIAL_DARK_RESTRICTED,
            COOLDOWN_ACTIVE,
            COLLAPSE_INSTABILITY_FIZZLE,
            OBSCURIAL_INSTABILITY_FIZZLE,
            DUPLICATE_RELEASE_GUARD,
            WAND_NOT_BONDED,
            WAND_WRONG_MASTER,
            LANGLOCKED);

    /** Reasons from {@link at.koopro.wizardsandbeasts.network.ObscurialAbilityUseC2SPacket} only. */
    private static final Set<String> OBSCURUS_ABILITY_PACKET_BASES = Set.of(
            ABILITY_NOT_IN_DARK_FORM,
            ABILITY_UNKNOWN,
            ABILITY_SPELL_MISSING,
            ABILITY_COOLDOWN_ACTIVE,
            ABILITY_REQUIREMENTS_UNMET,
            ABILITY_EXECUTE_FAILED);

    /**
     * Player-facing action-bar message key for each reject code that a cast site does NOT already
     * report with its own (richer) message. Codes with bespoke feedback — bond, requirements, the
     * Obscurial rejects, Gamp — are deliberately absent so the central reject choke point never
     * doubles their text. {@code cooldown_active} covers both the per-spell cooldown and the 5-tick
     * global cooldown (its {@code :global_cooldown} detail collapses to the same "recharging" line).
     */
    private static final Map<String, String> CAST_REJECT_MESSAGE_KEYS = Map.of(
            NOT_HOLDING_WAND, "wandcraft.cast.reject.no_wand",
            LANGLOCKED, "wandcraft.cast.reject.langlocked",
            NO_ACTIVE_SPELL, "wandcraft.cast.reject.no_active_spell",
            UNKNOWN_SPELL, "wandcraft.cast.reject.unknown_spell",
            SPELL_NOT_KNOWN, "wandcraft.cast.reject.not_known",
            COOLDOWN_ACTIVE, "wandcraft.cast.reject.cooldown");

    private SpellRejectCodes() {}

    /**
     * Player-facing action-bar lang key for a stored reject key, or {@code null} when the reject site
     * shows its own message (so the caller stays silent and avoids double-messaging). The {@code :detail}
     * suffix is stripped via {@link #baseReason} before lookup.
     */
    @Nullable
    public static String castRejectMessageKey(String storedKey) {
        return CAST_REJECT_MESSAGE_KEYS.get(baseReason(storedKey));
    }

    /**
     * Strip {@code :detail} suffix from keys produced by {@link #withDetail(String, String)}.
     */
    public static String baseReason(String storedKey) {
        if (storedKey == null || storedKey.isEmpty()) {
            return "";
        }
        int colon = storedKey.indexOf(':');
        return colon < 0 ? storedKey : storedKey.substring(0, colon);
    }

    /**
     * Bucket for {@code /wandb debug spell rejects summary}. Mutually exclusive labels for telemetry UI.
     */
    public static String summaryBucket(String storedKey) {
        String base = baseReason(storedKey);
        if (base.endsWith("_type_cannot_use_wand") || base.endsWith("_invalid_slot")) {
            return "guard_*";
        }
        if (base.startsWith("assign_")) {
            return "assign_*";
        }
        if (OBSCURUS_ABILITY_PACKET_BASES.contains(base)) {
            return "ability_*";
        }
        if (WAND_RELEASE_BASES.contains(base)) {
            return "wand_release";
        }
        return "other";
    }

    public static String withDetail(String code, String detail) {
        if (detail == null || detail.isBlank()) return code;
        return code + ":" + detail;
    }
}
