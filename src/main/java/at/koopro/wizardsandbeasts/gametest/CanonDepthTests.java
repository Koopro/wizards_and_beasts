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
        tests.add("canon_depth_howler_seals_and_bursts",
                "canon: a blank Howler seals with its writer's name; in someone else's pocket it opens itself after a "
                        + "minute and burns, and never in its writer's",
                CanonDepthTests::howler);
        tests.add("canon_depth_wheezes_work",
                "canon: a Skiving Snackbox gives four two-ended sweets; the orange end sickens, the purple end cures "
                        + "only that; a Dungbomb makes everyone near it retch",
                CanonDepthTests::wheezes);
        tests.add("canon_depth_remembrall_glows_for_real_things",
                "canon: a Remembrall stays clear for a wizard with nothing owed, glows while a Ministry fine is "
                        + "outstanding, and clears once it is paid",
                CanonDepthTests::remembrall);
    }

    private static void remembrall(GameTestHelper helper) {
        ServerPlayer neville = WizardTestSupport.placeMockPlayer(helper, "Neville", GameType.SURVIVAL);
        try {
            ItemStack ball = new ItemStack(at.koopro.wizardsandbeasts.registry.TrinketItemRegistry.REMEMBRALL.get());
            at.koopro.wizardsandbeasts.item.trinket.RemembrallItem.refresh(ball, neville);
            check(helper, !ball.has(at.koopro.wizardsandbeasts.registry.ModDataComponents.REMEMBRALL_FORGOTTEN.get()),
                    () -> "a fresh wizard's Remembrall glowed: " + at.koopro.wizardsandbeasts.item.trinket.Remembrall.check(neville));
            at.koopro.wizardsandbeasts.ministry.MinistryRecords.mutate(neville, r -> r.withOutstandingFine(500L));
            at.koopro.wizardsandbeasts.item.trinket.RemembrallItem.refresh(ball, neville);
            check(helper, "A_FINE".equals(ball.get(
                            at.koopro.wizardsandbeasts.registry.ModDataComponents.REMEMBRALL_FORGOTTEN.get())),
                    () -> "an unpaid fine did not turn the smoke red");
            at.koopro.wizardsandbeasts.ministry.MinistryRecords.mutate(neville, r -> r.withOutstandingFine(0L));
            at.koopro.wizardsandbeasts.item.trinket.RemembrallItem.refresh(ball, neville);
            check(helper, !ball.has(at.koopro.wizardsandbeasts.registry.ModDataComponents.REMEMBRALL_FORGOTTEN.get()),
                    () -> "the smoke stayed red after the fine was paid");
            helper.succeed();
        } finally {
            retire(helper, neville);
        }
    }

    private static void howler(GameTestHelper helper) {
        ServerPlayer writer = WizardTestSupport.placeMockPlayer(helper, "HowlerWriter", GameType.SURVIVAL);
        ServerPlayer reader = WizardTestSupport.placeMockPlayer(helper, "HowlerReader", GameType.SURVIVAL);
        try {
            var level = helper.getLevel();
            ItemStack letter = new ItemStack(CanonItemRegistry.HOWLER.get());
            check(helper, at.koopro.wizardsandbeasts.item.trinket.HowlerItem.seal(letter,
                    writer.getGameProfile().name(), "you stole the car"), () -> "a blank Howler would not seal");
            check(helper, !at.koopro.wizardsandbeasts.item.trinket.HowlerItem.seal(letter, "x", "again"),
                    () -> "a sealed Howler sealed twice");

            letter.inventoryTick(level, writer, null);
            check(helper, !letter.isEmpty() && !letter.has(
                    at.koopro.wizardsandbeasts.registry.ModDataComponents.HOWLER_ARMED_AT.get()),
                    () -> "a Howler armed in its writer's own pocket");

            letter.inventoryTick(level, reader, null);
            Long armed = letter.get(at.koopro.wizardsandbeasts.registry.ModDataComponents.HOWLER_ARMED_AT.get());
            check(helper, armed != null, () -> "a Howler in the recipient's pocket never armed");
            letter.set(at.koopro.wizardsandbeasts.registry.ModDataComponents.HOWLER_ARMED_AT.get(),
                    level.getGameTime() - at.koopro.wizardsandbeasts.item.trinket.HowlerItem.FUSE_TICKS - 1);
            letter.inventoryTick(level, reader, null);
            check(helper, letter.isEmpty(), () -> "an ignored Howler did not burst");
            helper.succeed();
        } finally {
            retire(helper, writer);
            retire(helper, reader);
        }
    }

    private static void wheezes(GameTestHelper helper) {
        ServerPlayer skiver = WizardTestSupport.placeMockPlayer(helper, "Skiver", GameType.SURVIVAL);
        try {
            var level = helper.getLevel();
            skiver.getInventory().clearContent();
            ItemStack box = new ItemStack(CanonItemRegistry.SKIVING_SNACKBOX.get());
            skiver.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, box);
            box.use(level, skiver, net.minecraft.world.InteractionHand.MAIN_HAND);
            check(helper, skiver.getInventory().countItem(CanonItemRegistry.PUKING_PASTILLES.get()) == 1
                            && skiver.getInventory().countItem(CanonItemRegistry.FEVER_FUDGE.get()) == 1,
                    () -> "the snackbox did not hand over its sweets");

            skiver.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 400, 0));
            new ItemStack(CanonItemRegistry.PUKING_PASTILLES.get()).finishUsingItem(level, skiver);
            check(helper, skiver.hasEffect(MobEffects.NAUSEA), () -> "the orange end did not sicken");
            skiver.setShiftKeyDown(true);
            new ItemStack(CanonItemRegistry.PUKING_PASTILLES.get()).finishUsingItem(level, skiver);
            skiver.setShiftKeyDown(false);
            check(helper, !skiver.hasEffect(MobEffects.NAUSEA), () -> "the purple end did not cure");
            check(helper, skiver.hasEffect(MobEffects.BLINDNESS), () -> "the purple end cured something else");

            skiver.removeAllEffects();
            at.koopro.wizardsandbeasts.item.projectile.WheezesThrown.land(level,
                    at.koopro.wizardsandbeasts.entity.spell.WizardingThrownKind.DUNGBOMB, skiver.position(), null);
            check(helper, skiver.hasEffect(MobEffects.NAUSEA), () -> "a Dungbomb at their feet did not sicken");
            helper.succeed();
        } finally {
            retire(helper, skiver);
        }
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
