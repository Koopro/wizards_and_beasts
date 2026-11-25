package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.amortentia.Amortentia;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.item.brew.BrewItem;
import at.koopro.wizardsandbeasts.polyjuice.PolyjuiceSample;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/** C1 (systems audit 2026-11-25): an aimed Amortentia spares only its object; an unaimed one spares everyone. */
public final class AmortentiaTests {

    private AmortentiaTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("amortentia_aimed_spares_only_its_object",
                "amortentia: a bottle with Harry's hair in it stops its drinker striking Harry, and nobody else",
                AmortentiaTests::aimedSparesOnlyItsObject);
    }

    private static void aimedSparesOnlyItsObject(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer drinker = WizardTestSupport.placeMockPlayer(helper, "RomildaVane", GameType.SURVIVAL);
        ServerPlayer object = WizardTestSupport.placeMockPlayer(helper, "TheChosenOne", GameType.SURVIVAL);
        ServerPlayer bystander = WizardTestSupport.placeMockPlayer(helper, "RonWeasley", GameType.SURVIVAL);
        try {
            for (ServerPlayer p : new ServerPlayer[]{drinker, object, bystander}) {
                WizardTestSupport.parkAtOrigin(helper, p);
            }
            check(helper, Brews.byId(Amortentia.ID) != null, () -> "no Amortentia brew");
            ItemStack bottle = BrewItem.of(Brews.byId(Amortentia.ID));
            PolyjuiceSample.write(bottle, object.getUUID(), object.getName().getString());

            drinker.addEffect(new MobEffectInstance(ModEffects.INFATUATION, 600));
            Amortentia.onDrunk(drinker, bottle);
            check(helper, Amortentia.object(drinker).filter(object.getUUID()::equals).isPresent(),
                    () -> "the drinker is not besotted with the bottle's object");

            float objectBefore = object.getHealth();
            object.hurtServer(level, level.damageSources().playerAttack(drinker), 4.0f);
            check(helper, object.getHealth() == objectBefore, () -> "the drinker struck their object");

            float bystanderBefore = bystander.getHealth();
            bystander.hurtServer(level, level.damageSources().playerAttack(drinker), 4.0f);
            check(helper, bystander.getHealth() < bystanderBefore, () -> "an aimed potion spared a bystander too");

            // Unaimed: the honest old effect, nobody may be struck.
            Amortentia.onDrunk(drinker, BrewItem.of(Brews.byId(Amortentia.ID)));
            check(helper, Amortentia.object(drinker).isEmpty(), () -> "an unaimed bottle kept the old object");
            check(helper, Amortentia.restrains(drinker, bystander) && Amortentia.restrains(drinker, object),
                    () -> "an unaimed potion let its drinker strike someone");

            drinker.removeEffect(ModEffects.INFATUATION);
            check(helper, !Amortentia.restrains(drinker, object), () -> "the infatuation outlived the potion");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, drinker);
            WizardTestSupport.retire(helper, object);
            WizardTestSupport.retire(helper, bystander);
        }
    }
}
