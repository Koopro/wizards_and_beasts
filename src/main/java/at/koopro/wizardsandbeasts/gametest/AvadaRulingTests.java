package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.spell.beam.AvadaInterposition;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * B4 (systems audit 2026-11-25): the Killing Curse stays unblockable, but a body in its path takes it.
 * {@code protego_never_stops_the_killing_curse} already pins the shield half of the ruling.
 */
public final class AvadaRulingTests {

    private AvadaRulingTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("avada_strikes_the_first_body_in_its_path",
                "avada kedavra: unblockable, but a body stepping into the jet takes it (Fawkes, OotP ch.36)",
                AvadaRulingTests::strikesTheFirstBody);
    }

    private static void strikesTheFirstBody(GameTestHelper helper) {
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, "KillingCurseCaster", GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, caster);
        try {
            Spell avada = Spells.byId("avada_kedavra");
            check(helper, avada != null && avada.isUnblockable(), () -> "Avada Kedavra is not unblockable");

            var target = helper.spawn(EntityType.COW, new BlockPos(6, 1, 0));
            target.setNoAi(true);
            check(helper, AvadaInterposition.struck(caster, target) == target,
                    () -> "with nothing in between, the curse struck someone other than its target");

            var aside = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
            aside.setNoAi(true);
            check(helper, AvadaInterposition.struck(caster, target) == target,
                    () -> "a villager standing off the line took the curse");

            var between = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 0));
            between.setNoAi(true);
            LivingEntity struck = AvadaInterposition.struck(caster, target);
            check(helper, struck == between,
                    () -> "a villager standing in the jet's path did not take it; struck " + struck.getType());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, caster);
        }
    }
}
