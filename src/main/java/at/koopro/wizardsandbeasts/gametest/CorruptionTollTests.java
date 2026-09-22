package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.corruption.DarkCorruptionService;
import at.koopro.wizardsandbeasts.corruption.UnforgivableToll;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.registry.ModAttributes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.GameType;

/**
 * An Unforgivable is paid for once, and the character sheet knows about it.
 *
 * <p>Two faults, one cause. Imperio charged 6 at cast and another 8 when the control landed; Crucio
 * charged 8 at cast and another 5 per channel tick. Neither of the second charges went through
 * {@link DarkCorruptionService}, so besides double-billing they wrote the attachment raw and skipped
 * the mirror onto {@link ModAttributes#DARK_CORRUPTION} — which is what the character sheet reads.
 * A player could torture someone for twenty seconds and watch the sheet insist nothing had happened
 * until they next logged in.
 *
 * <p>The repair was not to halve the numbers. The two charges price different moments, and for both
 * curses the meaningful moment is the second one: Imperio's weight is in seizing a will rather than
 * waving a wand, and Crucio's whole design is escalation with {@code crucioHoldTicks}. So the flat
 * cast toll is skipped for those two and their own call sites pay, through {@link UnforgivableToll}.
 */
public final class CorruptionTollTests {

    private CorruptionTollTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("corruption_unforgivable_cast_toll_skips_self_billing_curses",
                "corruption: Crucio and Imperio are not charged twice for one act",
                CorruptionTollTests::castTollSkipsSelfBillingCurses);
        tests.add("corruption_reaches_the_character_sheet_immediately",
                "corruption: every charge mirrors onto the attribute the sheet reads",
                CorruptionTollTests::chargeMirrorsToTheSheet);
        tests.add("corruption_accumulates_across_repeated_charges",
                "corruption: repeated charges stack and clamp at the ceiling",
                CorruptionTollTests::repeatedChargesAccumulate);
    }

    // ── scenarios ───────────────────────────────────────────────────────────────────────────────

    /**
     * The cast path must leave Crucio and Imperio alone and still charge the Killing Curse, which has
     * no second moment to be billed at.
     */
    private static void castTollSkipsSelfBillingCurses(GameTestHelper helper) {
        ServerPlayer caster = player(helper, "TollCaster");
        try {
            for (String selfBilling : new String[]{"crucio", "imperio"}) {
                float before = DarkCorruptionService.get(caster);
                UnforgivableToll.onCast(caster, selfBilling);
                float after = DarkCorruptionService.get(caster);
                WizardTestSupport.check(helper, after == before,
                        () -> selfBilling + " was charged at cast as well as where it lands: "
                                + before + " -> " + after);
            }

            float beforeKilling = DarkCorruptionService.get(caster);
            UnforgivableToll.onCast(caster, "avada_kedavra");
            float afterKilling = DarkCorruptionService.get(caster);
            WizardTestSupport.check(helper, afterKilling > beforeKilling,
                    () -> "the Killing Curse left its caster unmarked: "
                            + beforeKilling + " -> " + afterKilling);

            // An ordinary spell is still free.
            float beforeLumos = DarkCorruptionService.get(caster);
            UnforgivableToll.onCast(caster, "lumos");
            WizardTestSupport.check(helper, DarkCorruptionService.get(caster) == beforeLumos,
                    () -> "a light charm stained its caster");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, caster);
        }
    }

    /**
     * The attachment is the source of truth and the attribute is the sheet's copy of it. This is the
     * half that was silently broken: the numbers moved and the sheet did not.
     */
    private static void chargeMirrorsToTheSheet(GameTestHelper helper) {
        ServerPlayer caster = player(helper, "MirrorCaster");
        try {
            UnforgivableToll.charge(caster, 8.0f);

            float stored = DarkCorruptionService.get(caster);
            AttributeInstance mirror = caster.getAttribute(ModAttributes.DARK_CORRUPTION);
            WizardTestSupport.check(helper, mirror != null,
                    () -> "the player carries no dark-corruption attribute to mirror onto");
            WizardTestSupport.check(helper, mirror != null && (float) mirror.getBaseValue() == stored,
                    () -> "the character sheet disagrees with the server: attribute="
                            + (mirror == null ? "absent" : mirror.getBaseValue()) + " stored=" + stored);
            WizardTestSupport.check(helper, stored > 0.0f,
                    () -> "the charge did not land at all");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, caster);
        }
    }

    /**
     * Crucio bills per channel tick, so charges land repeatedly on one hold. They must accumulate,
     * stay mirrored throughout, and stop at the ceiling rather than running past it.
     */
    private static void repeatedChargesAccumulate(GameTestHelper helper) {
        ServerPlayer caster = player(helper, "ChannelCaster");
        try {
            float previous = DarkCorruptionService.get(caster);
            for (int tick = 0; tick < 4; tick++) {
                final float before = previous;
                UnforgivableToll.charge(caster, 5.0f);
                final float now = DarkCorruptionService.get(caster);
                WizardTestSupport.check(helper, now > before,
                        () -> "a channel tick charged nothing: " + before + " -> " + now);
                previous = now;
            }

            // Past the ceiling and stop there, with the mirror still agreeing.
            for (int tick = 0; tick < 40; tick++) {
                UnforgivableToll.charge(caster, 5.0f);
            }
            float capped = DarkCorruptionService.get(caster);
            AttributeInstance mirror = caster.getAttribute(ModAttributes.DARK_CORRUPTION);
            WizardTestSupport.check(helper, capped == DarkCorruptionService.MAX,
                    () -> "corruption did not settle at the ceiling: " + capped);
            WizardTestSupport.check(helper, mirror != null && (float) mirror.getBaseValue() == capped,
                    () -> "the sheet lost track of a clamped value");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, caster);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.CREATIVE);
        WizardTestSupport.parkAtOrigin(helper, player);
        HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        return player;
    }
}
