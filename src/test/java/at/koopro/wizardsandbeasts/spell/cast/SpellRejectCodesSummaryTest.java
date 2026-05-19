package at.koopro.wizardsandbeasts.spell.cast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpellRejectCodesSummaryTest {

    @Test
    void summaryBucket_wandReleaseCodes_withoutPrefix() {
        assertEquals("wand_release", SpellRejectCodes.summaryBucket(SpellRejectCodes.NOT_HOLDING_WAND));
        assertEquals("wand_release", SpellRejectCodes.summaryBucket(
                SpellRejectCodes.withDetail(SpellRejectCodes.COOLDOWN_ACTIVE, "lumos")));
    }

    @Test
    void summaryBucket_networkGuards_anyPacketPrefix() {
        assertEquals("guard_*", SpellRejectCodes.summaryBucket("cast_type_cannot_use_wand"));
        assertEquals("guard_*", SpellRejectCodes.summaryBucket("select_invalid_slot"));
        assertEquals("guard_*", SpellRejectCodes.summaryBucket("leviosa_adjust_type_cannot_use_wand"));
    }

    @Test
    void summaryBucket_assignSpellReject_notSlotGuard() {
        assertEquals("assign_*", SpellRejectCodes.summaryBucket(SpellRejectCodes.ASSIGN_UNKNOWN_SPELL));
    }

    @Test
    void summaryBucket_obscurusAbilityVsWandAbilityRequires() {
        assertEquals("ability_*", SpellRejectCodes.summaryBucket(SpellRejectCodes.ABILITY_UNKNOWN));
        assertEquals("wand_release", SpellRejectCodes.summaryBucket(SpellRejectCodes.ABILITY_REQUIRES_ABILITY_INPUT));
    }

    @Test
    void baseReason_stripsDetail() {
        assertEquals("cooldown_active", SpellRejectCodes.baseReason("cooldown_active:stupefy"));
        assertEquals("not_holding_wand", SpellRejectCodes.baseReason("not_holding_wand"));
    }
}
