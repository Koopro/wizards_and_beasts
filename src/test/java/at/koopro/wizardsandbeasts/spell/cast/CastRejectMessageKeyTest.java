package at.koopro.wizardsandbeasts.spell.cast;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the cast-rejection feedback contract.
 *
 * <p>The rule this locks: <b>a refused cast tells the player which refusal it was.</b> Every code a
 * player can hit maps to its own lang key, no two codes share one, and every key resolves in
 * {@code en_us.json}. The two exceptions are named explicitly rather than left to drift — codes whose
 * text is composed at the reject site, and codes that are diagnostics no player should read.
 *
 * <p>Without these assertions the failure mode is silent and specific: a new gate is added, nobody adds
 * a key, and it refuses casts with no explanation — or worse, six gates converge on one vague sentence
 * and the player cannot tell a cooldown from an unlearned spell.
 */
class CastRejectMessageKeyTest {

    private static final String LANG_RESOURCE = "assets/wizards_and_beasts/lang/en_us.json";

    /** Codes whose text is written at the reject site out of live state; the client stays silent. */
    private static final List<String> SITE_OWNED = List.of(
            SpellRejectCodes.REQUIREMENTS_UNMET,
            SpellRejectCodes.GAMP_HARD_REJECT);

    /** Desync guards and impossible-state checks: diagnostics, never shown. */
    private static final List<String> INTERNAL_ONLY = List.of(
            SpellRejectCodes.NOT_SERVER_LEVEL,
            SpellRejectCodes.DUPLICATE_RELEASE_GUARD,
            SpellRejectCodes.NO_CAST_SESSION,
            SpellRejectCodes.CAST_SESSION_EXPIRED,
            SpellRejectCodes.CASTER_NOT_ALIVE);

    @Test
    void everyPlayerFacingCode_mapsToItsOwnKey() {
        assertEquals("wandcraft.cast.reject.no_wand",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.NOT_HOLDING_WAND));
        assertEquals("wandcraft.cast.reject.langlocked",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.LANGLOCKED));
        assertEquals("wandcraft.cast.reject.no_active_spell",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.NO_ACTIVE_SPELL));
        assertEquals("wandcraft.cast.reject.unknown_spell",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.UNKNOWN_SPELL));
        assertEquals("wandcraft.cast.reject.not_known",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.SPELL_NOT_KNOWN));
        assertEquals("wandcraft.cast.reject.cooldown",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.COOLDOWN_ACTIVE));
        // These four used to be hardcoded English at their reject sites and are the point of the change.
        assertEquals("wandcraft.cast.requires_bond",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.WAND_NOT_BONDED));
        assertEquals("wandcraft.cast.wrong_master",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.WAND_WRONG_MASTER));
        assertEquals("wandcraft.cast.reject.dark_form_required",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.OBSCURIAL_DARK_ONLY_OUTSIDE_FORM));
        assertEquals("wandcraft.cast.reject.obscurus_rejects",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.OBSCURIAL_DARK_RESTRICTED));
    }

    /**
     * A cast that throws inside the executor is a refusal like any other. It used to return silently —
     * no packet, no sound, no counter — so a mod defect looked exactly like a dropped keypress.
     */
    @Test
    void castFailed_isPlayerFacing_soAThrownCastIsNeverSilent() {
        assertEquals("wandcraft.cast.reject.cast_failed",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.CAST_FAILED));
        assertEquals("wandcraft.cast.reject.cast_failed",
                SpellRejectCodes.castRejectMessageKey(
                        SpellRejectCodes.withDetail(SpellRejectCodes.CAST_FAILED, "lumos")));
        assertEquals("wand_release", SpellRejectCodes.summaryBucket(SpellRejectCodes.CAST_FAILED));
    }

    /**
     * The two {@code SpellNetworkGuards} refusals are stored with the calling packet's prefix, so they
     * must resolve by suffix — one key each, whichever packet was refused. Before this they carried
     * hardcoded English at the guard and sent no packet at all, so they were both untranslatable and
     * inaudible.
     */
    @Test
    void guardCodes_resolveUnderEveryCallerPrefix() {
        for (String prefix : List.of("cast", "assign", "select", "leviosa_adjust")) {
            assertEquals("wandcraft.cast.reject.type_cannot_use_wand",
                    SpellRejectCodes.castRejectMessageKey(
                            prefix + SpellRejectCodes.SUFFIX_TYPE_CANNOT_USE_WAND),
                    "the '" + prefix + "' packet's wand-type refusal resolves to no message");
            assertEquals("wandcraft.cast.reject.invalid_slot",
                    SpellRejectCodes.castRejectMessageKey(
                            prefix + SpellRejectCodes.SUFFIX_INVALID_SLOT),
                    "the '" + prefix + "' packet's bad-slot refusal resolves to no message");
        }
    }

    /** Suffix matching must not swallow the telemetry bucket that keys off the same shape. */
    @Test
    void guardCodes_stillBucketAsGuards() {
        assertEquals("guard_*",
                SpellRejectCodes.summaryBucket("cast" + SpellRejectCodes.SUFFIX_TYPE_CANNOT_USE_WAND));
        assertEquals("guard_*",
                SpellRejectCodes.summaryBucket("assign" + SpellRejectCodes.SUFFIX_INVALID_SLOT));
    }

    @Test
    void noTwoCodesShareAKey() {
        Map<String, String> keys = SpellRejectCodes.messageKeys();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            assertTrue(seen.add(entry.getValue()),
                    "reject key '" + entry.getValue() + "' is reused by code '" + entry.getKey()
                            + "' — two different refusals would read identically");
        }
        assertEquals(keys.size(), seen.size());
        for (Map.Entry<String, String> suffix : SpellRejectCodes.suffixMessageKeys().entrySet()) {
            assertTrue(seen.add(suffix.getValue()),
                    "guard suffix '" + suffix.getKey() + "' reuses key '" + suffix.getValue()
                            + "' — two different refusals would read identically");
        }
    }

    /**
     * Suffix matching runs after the exact lookup, so a code that happens to <em>end</em> with a guard
     * suffix would be silently rerouted to the guard's sentence. Nothing does today; this fails the
     * moment one is added.
     */
    @Test
    void noDirectCode_endsWithAGuardSuffix() {
        for (String code : SpellRejectCodes.messageKeys().keySet()) {
            for (String suffix : SpellRejectCodes.suffixMessageKeys().keySet()) {
                assertTrue(!code.endsWith(suffix),
                        "reject code '" + code + "' ends with guard suffix '" + suffix
                                + "' — it would resolve to the guard's message instead of its own");
            }
        }
    }

    @Test
    void detailSuffix_isStrippedBeforeLookup() {
        // The live sites always store the code with a :detail suffix (spell id or "global_cooldown").
        assertEquals("wandcraft.cast.reject.not_known",
                SpellRejectCodes.castRejectMessageKey(
                        SpellRejectCodes.withDetail(SpellRejectCodes.SPELL_NOT_KNOWN, "lumos")));
        assertEquals("wandcraft.cast.reject.cooldown",
                SpellRejectCodes.castRejectMessageKey(
                        SpellRejectCodes.withDetail(SpellRejectCodes.COOLDOWN_ACTIVE, "stupefy")));
        assertEquals("wandcraft.cast.reject.cooldown",
                SpellRejectCodes.castRejectMessageKey(
                        SpellRejectCodes.withDetail(SpellRejectCodes.COOLDOWN_ACTIVE, "global_cooldown")));
        assertEquals("wandcraft.cast.reject.unknown_spell",
                SpellRejectCodes.castRejectMessageKey(
                        SpellRejectCodes.withDetail(SpellRejectCodes.UNKNOWN_SPELL, "not_a_spell")));
    }

    @Test
    void siteOwnedCodes_returnNull_soTheirComposedTextIsNeverDoubled() {
        for (String code : SITE_OWNED) {
            assertNull(SpellRejectCodes.castRejectMessageKey(code),
                    "reject code '" + code + "' composes its own message and must not also map to a generic line");
            assertNull(SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.withDetail(code, "lumos")),
                    "reject code '" + code + "' (with detail) must not map to a generic line");
        }
    }

    @Test
    void internalCodes_returnNull_soNoPlayerReadsADiagnostic() {
        for (String code : INTERNAL_ONLY) {
            assertNull(SpellRejectCodes.castRejectMessageKey(code),
                    "reject code '" + code + "' is a diagnostic and must never reach a player");
        }
    }

    @Test
    void blankAndUnknownReasons_returnNull() {
        assertNull(SpellRejectCodes.castRejectMessageKey(""));
        assertNull(SpellRejectCodes.castRejectMessageKey("something_nobody_registered"));
    }

    @Test
    void everyMappedKey_existsInLang() throws IOException {
        JsonObject lang = loadLang();
        for (Map.Entry<String, String> entry : SpellRejectCodes.messageKeys().entrySet()) {
            assertTrue(lang.has(entry.getValue()),
                    "en_us.json has no entry for '" + entry.getValue()
                            + "' (reject code '" + entry.getKey() + "') — the player would see the raw key");
        }
        for (Map.Entry<String, String> entry : SpellRejectCodes.suffixMessageKeys().entrySet()) {
            assertTrue(lang.has(entry.getValue()),
                    "en_us.json has no entry for '" + entry.getValue()
                            + "' (guard suffix '" + entry.getKey() + "') — the player would see the raw key");
        }
    }

    /**
     * The composed-message sites still need their own keys present; they are just not in the code map.
     * Listed by hand because nothing in Java points at them.
     */
    @Test
    void siteComposedKeys_existInLang() throws IOException {
        JsonObject lang = loadLang();
        for (String key : List.of(
                "wandcraft.cast.reject.mental_misfire",
                "wandcraft.cast.reject.none")) {
            assertTrue(lang.has(key), "en_us.json missing lang key: " + key);
        }
    }

    private static JsonObject loadLang() throws IOException {
        try (InputStream in = CastRejectMessageKeyTest.class.getClassLoader().getResourceAsStream(LANG_RESOURCE)) {
            assertNotNull(in, "could not locate " + LANG_RESOURCE + " on the test classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
