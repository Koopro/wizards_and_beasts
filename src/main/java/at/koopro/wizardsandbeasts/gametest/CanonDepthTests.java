package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.item.consumable.ElixirOfLife;
import at.koopro.wizardsandbeasts.registry.CanonItemRegistry;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * Canon depth (2026-11-19): objects that were registered and did nothing now do what the books and films
 * have them do. See docs/development/CANON_GAPS_2026_11.md.
 */
public final class CanonDepthTests {

    private CanonDepthTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("canon_depth_remedies_heal_and_cure",
                "canon: Murtlap Essence soothes boils and heals, the Blood-Replenishing Potion gives long regeneration, "
                        + "Essence of Dittany closes wounds harder than the herb",
                CanonDepthTests::remediesWork);
        tests.add("canon_depth_elixir_bottle_daily_rule",
                "canon: a bottled Elixir of Life holds death off for a day, and a second bottle the same day is refused "
                        + "and kept",
                CanonDepthTests::elixirBottle);
    }

    private static void remediesWork(GameTestHelper helper) {
        ServerPlayer patient = WizardTestSupport.placeMockPlayer(helper, "RemedyPatient", GameType.SURVIVAL);
        try {
            var level = helper.getLevel();

            patient.setHealth(8f);
            patient.addEffect(new MobEffectInstance(ModEffects.FURNUNCULUS, 400, 0));
            new ItemStack(CanonItemRegistry.MURTLAP_ESSENCE.get()).finishUsingItem(level, patient);
            check(helper, !patient.hasEffect(ModEffects.FURNUNCULUS), () -> "Murtlap Essence left the boils");
            check(helper, patient.getHealth() > 8f, () -> "Murtlap Essence healed nothing");
            check(helper, patient.hasEffect(MobEffects.REGENERATION), () -> "Murtlap Essence gave no regeneration");

            patient.removeAllEffects();
            patient.setHealth(8f);
            new ItemStack(CanonItemRegistry.BLOOD_REPLENISHING_POTION.get()).finishUsingItem(level, patient);
            MobEffectInstance regen = patient.getEffect(MobEffects.REGENERATION);
            check(helper, regen != null && regen.getDuration() > 600,
                    () -> "the Blood-Replenishing Potion gave no long regeneration");

            patient.removeAllEffects();
            patient.setHealth(4f);
            new ItemStack(ConsumableItemRegistry.DITTANY.get()).finishUsingItem(level, patient);
            float herb = patient.getHealth() - 4f;
            patient.setHealth(4f);
            new ItemStack(CanonItemRegistry.ESSENCE_OF_DITTANY.get()).finishUsingItem(level, patient);
            float essence = patient.getHealth() - 4f;
            check(helper, essence > herb, () -> "Essence of Dittany healed " + essence + ", the herb " + herb);
            helper.succeed();
        } finally {
            retire(helper, patient);
        }
    }

    private static void elixirBottle(GameTestHelper helper) {
        ServerPlayer drinker = WizardTestSupport.placeMockPlayer(helper, "ElixirDrinker", GameType.SURVIVAL);
        try {
            var level = helper.getLevel();
            ItemStack bottles = new ItemStack(CanonItemRegistry.ELIXIR_OF_LIFE.get(), 2);
            bottles.finishUsingItem(level, drinker);
            check(helper, ElixirOfLife.sustains(drinker), () -> "the bottled Elixir did not take");
            check(helper, bottles.getCount() == 1, () -> "the first bottle was not spent");
            bottles.finishUsingItem(level, drinker);
            check(helper, bottles.getCount() == 1, () -> "a second bottle the same day was spent");
            helper.succeed();
        } finally {
            retire(helper, drinker);
        }
    }
}
