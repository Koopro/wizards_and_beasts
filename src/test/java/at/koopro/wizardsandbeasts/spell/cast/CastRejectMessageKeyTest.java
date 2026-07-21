package at.koopro.wizardsandbeasts.spell.cast;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the cast-rejection feedback contract (F1). {@link SpellCastService#debugReject} shows a generic
 * action-bar line only for codes that lack their own richer message; every other reject site keeps its
 * bespoke text. These assertions lock that split so a future code change can't silently (a) leave a
 * common reject silent again or (b) double-message a site that already speaks.
 */
class CastRejectMessageKeyTest {

    private static final String LANG_RESOURCE = "assets/wizards_and_beasts/lang/en_us.json";

    @Test
    void silentCodes_mapToExpectedKeys() {
        assertEquals("wandcraft.cast.reject.no_wand",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.NOT_HOLDING_WAND));
        assertEquals("wandcraft.cast.reject.langlocked",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.LANGLOCKED));
        assertEquals("wandcraft.cast.reject.no_active_spell",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.NO_ACTIVE_SPELL));
        assertEquals("wandcraft.cast.reject.not_known",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.SPELL_NOT_KNOWN));
        assertEquals("wandcraft.cast.reject.cooldown",
                SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.COOLDOWN_ACTIVE));
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
    void richMessageCodes_returnNull_soTheirBespokeTextIsNeverDoubled() {
        for (String code : List.of(
                SpellRejectCodes.WAND_NOT_BONDED,
                SpellRejectCodes.WAND_WRONG_MASTER,
                SpellRejectCodes.REQUIREMENTS_UNMET,
                SpellRejectCodes.ABILITY_REQUIRES_ABILITY_INPUT,
                SpellRejectCodes.OBSCURIAL_DARK_ONLY_OUTSIDE_FORM,
                SpellRejectCodes.OBSCURIAL_DARK_RESTRICTED,
                SpellRejectCodes.COLLAPSE_INSTABILITY_FIZZLE,
                SpellRejectCodes.OBSCURIAL_INSTABILITY_FIZZLE)) {
            assertNull(SpellRejectCodes.castRejectMessageKey(code),
                    "reject code '" + code + "' has its own message and must not map to a generic line");
            assertNull(SpellRejectCodes.castRejectMessageKey(SpellRejectCodes.withDetail(code, "lumos")),
                    "reject code '" + code + "' (with detail) must not map to a generic line");
        }
    }

    @Test
    void blankReason_returnsNull() {
        assertNull(SpellRejectCodes.castRejectMessageKey(""));
    }

    @Test
    void everyMappedKey_existsInLang() throws IOException {
        String lang = loadLang();
        for (String key : List.of(
                "wandcraft.cast.reject.no_wand",
                "wandcraft.cast.reject.langlocked",
                "wandcraft.cast.reject.no_active_spell",
                "wandcraft.cast.reject.unknown_spell",
                "wandcraft.cast.reject.not_known",
                "wandcraft.cast.reject.cooldown")) {
            assertTrue(lang.contains("\"" + key + "\""), "en_us.json missing lang key: " + key);
        }
    }

    private static String loadLang() throws IOException {
        try (InputStream in = CastRejectMessageKeyTest.class.getClassLoader().getResourceAsStream(LANG_RESOURCE)) {
            assertNotNull(in, "could not locate " + LANG_RESOURCE + " on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
