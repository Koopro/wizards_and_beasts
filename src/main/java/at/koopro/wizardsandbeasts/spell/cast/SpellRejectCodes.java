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
    /**
     * The spell is registered but its behaviour is not written — {@code COMING_SOON}. Distinct from
     * {@link #UNKNOWN_SPELL} (id resolves to nothing) and {@link #SPELL_NOT_KNOWN} (the caster has
     * not learned it) because it is neither a desync nor anything the player can act on: the spell
     * exists, is spelled correctly, and simply does not work yet for anybody.
     */
    public static final String SPELL_NOT_IMPLEMENTED = "spell_not_implemented";
    public static final String SPELL_NOT_KNOWN = "spell_not_known";
    public static final String ABILITY_REQUIRES_ABILITY_INPUT = "ability_requires_ability_input";
    public static final String REQUIREMENTS_UNMET = "requirements_unmet";
    public static final String OBSCURIAL_DARK_ONLY_OUTSIDE_FORM = "obscurial_dark_only_spell_outside_dark_form";
    public static final String OBSCURIAL_DARK_RESTRICTED = "obscurial_dark_spell_restricted";
    /** Three Firewhiskies in two minutes. The wand will not hold still. */
    public static final String TOO_DRUNK = "too_drunk";
    public static final String COOLDOWN_ACTIVE = "cooldown_active";
    public static final String COLLAPSE_INSTABILITY_FIZZLE = "collapse_instability_fizzle";
    public static final String OBSCURIAL_INSTABILITY_FIZZLE = "obscurial_instability_fizzle";
    public static final String DUPLICATE_RELEASE_GUARD = "duplicate_release_guard";
    /** Held wand has no bonded master (resonance never matched). */
    public static final String WAND_NOT_BONDED = "wand_not_bonded";
    /** Held wand is bonded to another player. */
    public static final String WAND_WRONG_MASTER = "wand_wrong_master";
    public static final String LANGLOCKED = "langlocked";
    /**
     * Gamp's Law refused the cast outright. Not stored in the reject counters — the Gamp path predates
     * them and reports through {@code DebugHooks} — so it exists purely so the denial packet can name a
     * reason rather than send an empty one.
     */
    public static final String GAMP_HARD_REJECT = "gamp_hard_reject";
    /**
     * The cast passed every gate and then threw on its way through {@link SpellExecutor}.
     *
     * <p>This is a mod defect, not a player mistake, and it used to be the one refusal that said
     * nothing at all: the exception was logged server-side and the method returned, so the player
     * pressed cast, paid no cooldown, and got no sound, no text and no counter — indistinguishable
     * from a dropped input. It is player-facing precisely <em>because</em> it is a bug; a player who
     * can say "it fizzled with a message" reports something actionable.
     *
     * <p>No stress penalty is applied for it, unlike the gates above. Nothing the player did caused it.
     */
    public static final String CAST_FAILED = "cast_failed";

    /**
     * Suffixes for the two {@code SpellNetworkGuards} refusals. Both are stored with a caller prefix
     * naming the packet that hit them ({@code cast_}, {@code assign_}, {@code select_},
     * {@code leviosa_adjust_}), so they are matched by suffix rather than looked up whole — the same
     * shape {@link #summaryBucket} already uses to bucket them.
     */
    public static final String SUFFIX_TYPE_CANNOT_USE_WAND = "_type_cannot_use_wand";
    public static final String SUFFIX_INVALID_SLOT = "_invalid_slot";

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
            SPELL_NOT_IMPLEMENTED,
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
            LANGLOCKED,
            CAST_FAILED);

    /** Reasons from {@link at.koopro.wizardsandbeasts.network.ObscurialAbilityUseC2SPacket} only. */
    private static final Set<String> OBSCURUS_ABILITY_PACKET_BASES = Set.of(
            ABILITY_NOT_IN_DARK_FORM,
            ABILITY_UNKNOWN,
            ABILITY_SPELL_MISSING,
            ABILITY_COOLDOWN_ACTIVE,
            ABILITY_REQUIREMENTS_UNMET,
            ABILITY_EXECUTE_FAILED);

    /**
     * One distinct lang key per reject code — the whole player-facing vocabulary of refusal.
     *
     * <p>Two codes are absent on purpose and live in {@link #SITE_OWNED} instead: their text is
     * <em>composed</em> at the reject site out of live state (the unmet requirement describing itself,
     * Gamp's per-domain lore line), so a fixed key could only make the message vaguer.
     *
     * <p>Distinctness is the point. One "you can't cast that" covering six different reasons is the
     * failure this map exists to prevent, and {@code CastRejectMessageKeyTest} asserts that no two
     * codes share a key. Flavour colour lives in the lang <em>value</em> (a leading section-sign code
     * on the Obscurial lines, matching what those sites used to hardcode in Java) so translators keep
     * control of it and no Java class holds a colour for a sentence.
     */
    private static final Map<String, String> REJECT_MESSAGE_KEYS = Map.ofEntries(
            Map.entry(NOT_HOLDING_WAND, "wandcraft.cast.reject.no_wand"),
            Map.entry(LANGLOCKED, "wandcraft.cast.reject.langlocked"),
            Map.entry(NO_ACTIVE_SPELL, "wandcraft.cast.reject.no_active_spell"),
            Map.entry(UNKNOWN_SPELL, "wandcraft.cast.reject.unknown_spell"),
            Map.entry(SPELL_NOT_IMPLEMENTED, "wandcraft.cast.reject.not_implemented"),
            Map.entry(SPELL_NOT_KNOWN, "wandcraft.cast.reject.not_known"),
            Map.entry(COOLDOWN_ACTIVE, "wandcraft.cast.reject.cooldown"),
            Map.entry(WAND_NOT_BONDED, "wandcraft.cast.requires_bond"),
            Map.entry(WAND_WRONG_MASTER, "wandcraft.cast.wrong_master"),
            Map.entry(ABILITY_REQUIRES_ABILITY_INPUT, "wandcraft.cast.reject.ability_input"),
            Map.entry(OBSCURIAL_DARK_ONLY_OUTSIDE_FORM, "wandcraft.cast.reject.dark_form_required"),
            Map.entry(OBSCURIAL_DARK_RESTRICTED, "wandcraft.cast.reject.obscurus_rejects"),
            Map.entry(COLLAPSE_INSTABILITY_FIZZLE, "wandcraft.cast.reject.collapse_fizzle"),
            Map.entry(OBSCURIAL_INSTABILITY_FIZZLE, "wandcraft.cast.reject.obscurus_fizzle"),
            Map.entry(CAST_FAILED, "wandcraft.cast.reject.cast_failed"),

            Map.entry(ASSIGN_UNKNOWN_SPELL, "wandcraft.assign.reject.unknown_spell"),
            Map.entry(ASSIGN_UNLEARNED_SPELL, "wandcraft.assign.reject.not_known"),
            Map.entry(ASSIGN_OBSCURIAL_ABILITY, "wandcraft.assign.reject.obscurial_ability"),
            Map.entry(ASSIGN_TYPE_RESTRICTED_SPELL, "wandcraft.assign.reject.heritage_restricted"),

            Map.entry(ABILITY_NOT_IN_DARK_FORM, "wandcraft.ability.reject.dark_form_required"),
            Map.entry(ABILITY_UNKNOWN, "wandcraft.ability.reject.unknown"),
            Map.entry(ABILITY_SPELL_MISSING, "wandcraft.ability.reject.spell_missing"),
            Map.entry(ABILITY_COOLDOWN_ACTIVE, "wandcraft.ability.reject.cooldown"),
            Map.entry(ABILITY_REQUIREMENTS_UNMET, "wandcraft.ability.reject.requirements"),
            Map.entry(ABILITY_EXECUTE_FAILED, "wandcraft.ability.reject.execute_failed"));

    /**
     * The prefixed guard codes, keyed by suffix. Same contract as {@link #REJECT_MESSAGE_KEYS} — one
     * distinct key each, resolved on the client — but matched by suffix because the stored code carries
     * the calling packet's name in front of it and there is no fixed set of prefixes to enumerate.
     *
     * <p>These two used to hold hardcoded English at the guard itself, which meant a French player was
     * told "Your type cannot use wand spells." and no deny sound played, because the refusal never
     * travelled as a packet at all.
     */
    private static final Map<String, String> SUFFIX_MESSAGE_KEYS = Map.of(
            SUFFIX_TYPE_CANNOT_USE_WAND, "wandcraft.cast.reject.type_cannot_use_wand",
            SUFFIX_INVALID_SLOT, "wandcraft.cast.reject.invalid_slot");

    /**
     * Codes whose player-facing text is written at the reject site because it is composed from live
     * state. The denial still travels — the sound plays and the counter ticks — but the client renders
     * no text of its own for these, so the site's richer sentence is never doubled by a generic one.
     */
    private static final Set<String> SITE_OWNED = Set.of(REQUIREMENTS_UNMET, GAMP_HARD_REJECT);

    /**
     * Codes never shown to a player at all: desync guards and impossible-state checks. They are
     * diagnostics, and a player who reads "duplicate release guard" has learned nothing.
     */
    private static final Set<String> INTERNAL_ONLY = Set.of(NOT_SERVER_LEVEL, DUPLICATE_RELEASE_GUARD);

    private SpellRejectCodes() {}

    /**
     * The lang key the <b>client</b> should render for a stored reject key, or {@code null} when it
     * should stay silent — either the site already said something better ({@link #SITE_OWNED}) or the
     * code is a diagnostic no player should read ({@link #INTERNAL_ONLY}).
     *
     * <p>The {@code :detail} suffix is stripped via {@link #baseReason} before lookup.
     */
    @Nullable
    public static String castRejectMessageKey(String storedKey) {
        String base = baseReason(storedKey);
        if (SITE_OWNED.contains(base) || INTERNAL_ONLY.contains(base)) {
            return null;
        }
        String direct = REJECT_MESSAGE_KEYS.get(base);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> suffix : SUFFIX_MESSAGE_KEYS.entrySet()) {
            if (base.endsWith(suffix.getKey())) {
                return suffix.getValue();
            }
        }
        return null;
    }

    /**
     * The suffix-matched half of the vocabulary, for the tests that assert every key resolves in lang.
     * Keyed by suffix, not by a whole code — see {@link #SUFFIX_MESSAGE_KEYS}.
     */
    public static Map<String, String> suffixMessageKeys() {
        return SUFFIX_MESSAGE_KEYS;
    }

    /**
     * Every code that carries a key, for the tests that assert the vocabulary is complete and
     * collision-free. Not a rendering path — use {@link #castRejectMessageKey} for that.
     */
    public static Map<String, String> messageKeys() {
        return REJECT_MESSAGE_KEYS;
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
