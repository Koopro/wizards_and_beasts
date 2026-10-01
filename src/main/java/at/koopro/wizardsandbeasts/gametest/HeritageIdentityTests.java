package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.apparition.ApparitionServerLogic;
import at.koopro.wizardsandbeasts.brew.silver.SilveredWeapons;
import at.koopro.wizardsandbeasts.creature.wildlife.WildlifeWorld;
import at.koopro.wizardsandbeasts.currency.dragot.DragotExchange;
import at.koopro.wizardsandbeasts.currency.dragot.DragotPurse;
import at.koopro.wizardsandbeasts.currency.dragot.DragotQuotes;
import at.koopro.wizardsandbeasts.currency.dragot.DragotRates;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.TransformationState;
import at.koopro.wizardsandbeasts.heritage.centaur.StarReading;
import at.koopro.wizardsandbeasts.heritage.veela.VeelaAllure;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfRules;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the gated heritages actually <em>do</em>, on a live player.
 *
 * <p>The audit's finding was that most of them did nothing: a goblin's {@code vault_access} tag was read by no code,
 * a centaur's stargazing existed only in the lore blurb, and the house-elf's ability to Apparate where wizards cannot
 * was gated behind a skill node a house-elf has no reason to buy. Each of these scenarios covers one of those wires,
 * because a trait shown to a player and honoured by nothing is worse than no trait at all.
 *
 * <p>The heritages themselves stay unselectable at the gate. That is a separate decision from whether their
 * mechanics work when an operator hands one out, and these are the mechanics.
 */
public final class HeritageIdentityTests {

    private HeritageIdentityTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("heritage_goblin_pays_no_commission_at_gringotts",
                "heritage: Gringotts charges a wizard the fee and a goblin nothing",
                HeritageIdentityTests::goblinPaysNoCommission);
        tests.add("heritage_elf_travels_where_wizards_cannot",
                "heritage: a house-elf's Apparition is elf-magic, wards and all",
                HeritageIdentityTests::elfTravelsWhereWizardsCannot);
        tests.add("heritage_centaur_reads_the_stars",
                "heritage: the star-reading trait is what decides who can read the sky",
                HeritageIdentityTests::centaurReadsTheStars);
        tests.add("heritage_veela_allure_takes_the_fight_out_of_people",
                "heritage: a Veela's allure stops people-like mobs fighting, not beasts; only in human shape, only "
                        + "for a Veela; striking anyone breaks it",
                HeritageIdentityTests::veelaAllureTakesTheFightOut);
        tests.add("heritage_squib_kinship_is_trusted_by_beasts",
                "heritage: a Squib is let near a wary unicorn a wizard who has only met one is not",
                HeritageIdentityTests::squibKinshipIsTrusted);
        tests.add("heritage_silver_bites_the_wolf_not_the_wizard",
                "heritage: silver bites a bitten wizard only while the moon has them in wolf shape",
                HeritageIdentityTests::silverBitesTheWolf);
    }

    // ── scenarios ───────────────────────────────────────────────────────────────────────────────

    /**
     * Gringotts is goblin-run and goblin law holds that goblin-made things stay goblin-owned; the five percent is
     * what the bank charges outsiders. Asserted as a comparison rather than against a constant, so the scenario
     * still means something if the fee is ever retuned.
     */
    private static void goblinPaysNoCommission(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "GringottsWizard");
        ServerPlayer goblin = player(helper, "GringottsGoblin");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.PURE_BLOOD);
            HeritageAPI.commit(goblin, Heritage.GOBLIN, HeritageVariant.GOBLIN_COMMON);

            // Each customer is quoted their own rate, so the comparison is against what their own quote should
            // have paid rather than against each other's Knuts — a rate roll must not be able to fake this pass.
            float wizardRate = DragotQuotes.rateFor(wizard);
            long wizardGain = sellTen(helper, wizard);
            long wizardExpected = DragotRates.dragotsToKnuts(10, wizardRate, DragotRates.GRINGOTTS_FEE);
            WizardTestSupport.check(helper, wizardGain == wizardExpected,
                    () -> "the wizard was paid " + wizardGain + " rather than " + wizardExpected
                            + " Knuts, which is ten Dragots at " + wizardRate + " less the commission");

            float goblinRate = DragotQuotes.rateFor(goblin);
            long goblinGain = sellTen(helper, goblin);
            long goblinExpected = DragotRates.dragotsToKnuts(10, goblinRate, 0.0f);
            WizardTestSupport.check(helper, goblinGain == goblinExpected,
                    () -> "the goblin was paid " + goblinGain + " rather than the full " + goblinExpected
                            + " Knuts their own bank owes them");
            WizardTestSupport.check(helper,
                    goblinExpected > DragotRates.dragotsToKnuts(10, goblinRate, DragotRates.GRINGOTTS_FEE),
                    () -> "waiving the commission changed nothing, so this scenario proves nothing");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wizard);
            WizardTestSupport.retire(helper, goblin);
        }
    }

    /**
     * Dobby came and went inside Hogwarts, which is warded against every wizard in the castle. The ward check asks
     * {@code isElfApparition}, so that is what has to answer to the heritage trait rather than to a bought skill.
     */
    private static void elfTravelsWhereWizardsCannot(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "WardedWizard");
        ServerPlayer elf = player(helper, "WardedElf");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            HeritageAPI.commit(elf, Heritage.HOUSE_ELF, HeritageVariant.ELF_BOUND);

            WizardTestSupport.check(helper, ApparitionServerLogic.isElfMagic(elf),
                    () -> "a house-elf's travel is not elf-magic, so wards still hold them");
            WizardTestSupport.check(helper, !ApparitionServerLogic.isElfMagic(wizard),
                    () -> "an ordinary wizard was granted elf-magic, which would walk them through every ward");
            WizardTestSupport.check(helper, ApparitionServerLogic.canApparate(elf),
                    () -> "a house-elf needs no lesson to Apparate and was refused anyway");
            WizardTestSupport.check(helper, HeritageAPI.getData(elf).hasTrait("innate_apparition"),
                    () -> "the trait behind all of this is missing from the lineage");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wizard);
            WizardTestSupport.retire(helper, elf);
        }
    }

    /** The reading is gated on the trait, not the heritage, so a datapack or a condition could grant it later. */
    private static void centaurReadsTheStars(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "SkyWizard");
        ServerPlayer centaur = player(helper, "SkyCentaur");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.PURE_BLOOD);
            HeritageAPI.commit(centaur, Heritage.CENTAUR, HeritageVariant.CENTAUR_STARGAZER);

            WizardTestSupport.check(helper, StarReading.canRead(centaur),
                    () -> "a stargazer centaur cannot read the stars");
            WizardTestSupport.check(helper, !StarReading.canRead(wizard),
                    () -> "a wizard was handed a centaur's years of watching");

            // A wizard's refusal says why, and it is the trait rather than the weather.
            StarReading.Reading refused = StarReading.read(wizard);
            WizardTestSupport.check(helper, refused instanceof StarReading.Reading.Refused,
                    () -> "a wizard got a reading anyway");

            // The centaur's own answer depends on the sky over the test world, so what is asserted is that it is
            // answered at all — the calendar itself is pinned in StarReadingTest, where the clock is an argument.
            StarReading.Reading reading = StarReading.read(centaur);
            WizardTestSupport.check(helper, reading != null,
                    () -> "the reading returned nothing rather than a reason");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wizard);
            WizardTestSupport.retire(helper, centaur);
        }
    }

    /**
     * The allure takes the fight out of people: a villager entranced cannot take up a target, a zombie is not a
     * person and is untouched. A wizard has no allure to let out; neither has a Veela in harpy shape. Striking anyone
     * lets everyone go.
     */
    private static void veelaAllureTakesTheFightOut(GameTestHelper helper) {
        ServerPlayer veela = player(helper, "AllureVeela");
        ServerPlayer wizard = player(helper, "AllureWizard");
        try {
            HeritageAPI.commit(veela, Heritage.VEELA, HeritageVariant.VEELA_FULL);
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            Villager villager = still(helper, EntityType.VILLAGER, 2);
            Zombie zombie = still(helper, EntityType.ZOMBIE, -2);

            WizardTestSupport.check(helper, VeelaAllure.allure(wizard) == -1 && !VeelaAllure.canAllure(wizard),
                    () -> "a wizard let out an allure");
            WizardTestSupport.check(helper, VeelaAllure.canAllure(veela), () -> "a full Veela has no allure");

            VeelaAllure.allure(veela);
            WizardTestSupport.check(helper, villager.hasEffect(ModEffects.INFATUATION),
                    () -> "a villager beside the Veela was not entranced");
            WizardTestSupport.check(helper, !zombie.hasEffect(ModEffects.INFATUATION),
                    () -> "a zombie was entranced; the undead have no mind to lose");
            villager.setTarget(zombie);
            WizardTestSupport.check(helper, villager.getTarget() == null,
                    () -> "an entranced villager took up a target");

            // Anger breaks it: the Veela strikes the zombie, and the villager is free.
            zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(veela), 1.0f);
            WizardTestSupport.check(helper, !villager.hasEffect(ModEffects.INFATUATION),
                    () -> "the allure outlasted the Veela striking someone");

            // The harpy has fury, not allure.
            HeritageAPI.getData(veela).setTransformationState(TransformationState.TRANSFORMED);
            WizardTestSupport.check(helper, !VeelaAllure.canAllure(veela),
                    () -> "a Veela in harpy shape could still let out the allure");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, veela);
            WizardTestSupport.retire(helper, wizard);
        }
    }

    /** Beasts trust a Squib one step sooner than study alone would earn: met once, a unicorn lets them near. */
    private static void squibKinshipIsTrusted(GameTestHelper helper) {
        ServerPlayer squib = player(helper, "KinshipSquib");
        ServerPlayer wizard = player(helper, "KinshipWizard");
        try {
            HeritageAPI.commit(squib, Heritage.WIZARDKIND, HeritageVariant.SQUIB);
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            GenericBeastEntity unicorn = (GenericBeastEntity) still(helper,
                    ModCreatures.ENTITIES.get("unicorn").get(), 3);
            for (ServerPlayer player : new ServerPlayer[] {squib, wizard}) {
                BestiaryDiscoveryHandler.encountered(player, unicorn);
                player.setShiftKeyDown(true);
            }
            WizardTestSupport.check(helper, WildlifeWorld.letsNear(squib, unicorn.getType(), true, "unicorn_slayer"),
                    () -> "a unicorn would not let a quiet Squib near after one meeting");
            WizardTestSupport.check(helper, !WildlifeWorld.letsNear(wizard, unicorn.getType(), true, "unicorn_slayer"),
                    () -> "a unicorn let a wizard near after one meeting; the kinship proves nothing");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, squib);
            WizardTestSupport.retire(helper, wizard);
        }
    }

    /** The condition, not the person: silver is metal to a bitten wizard until the moon has them. */
    private static void silverBitesTheWolf(GameTestHelper helper) {
        ServerPlayer bitten = player(helper, "SilverBitten");
        try {
            HeritageAPI.commit(bitten, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            HeritageAPI.afflict(bitten, ConditionOrigin.BITTEN);
            WizardTestSupport.check(helper, !SilveredWeapons.isDarkCreature(bitten),
                    () -> "silver bit a werewolf in human shape");
            var data = HeritageAPI.getData(bitten);
            data.setActiveFormId(WerewolfRules.WOLF_FORM);
            data.setTransformationState(TransformationState.TRANSFORMED);
            WizardTestSupport.check(helper, SilveredWeapons.isDarkCreature(bitten),
                    () -> "silver did not bite a werewolf in wolf shape");
            ItemStack blade = new ItemStack(Items.IRON_SWORD);
            SilveredWeapons.silver(blade);
            WizardTestSupport.check(helper, SilveredWeapons.bonusFor(blade, SilveredWeapons.isDarkCreature(bitten)) > 0,
                    () -> "a silvered blade gave no bonus against the wolf");
            data.setTransformationState(TransformationState.NORMAL);
            data.setActiveFormId(null);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, bitten);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** A mob a few blocks along x from the test's origin, placed with no AI and no gravity. */
    private static <T extends net.minecraft.world.entity.Mob> T still(GameTestHelper helper, EntityType<T> type, int x) {
        T mob = helper.spawn(type, new BlockPos(x, 1, 0));
        mob.setNoAi(true);
        mob.setNoGravity(true);
        return mob;
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }

    /** Sells ten sound Dragots at the player's already-pinned quote and returns what landed in the vault. */
    private static long sellTen(GameTestHelper helper, ServerPlayer player) {
        PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
        long before = vault.getTotalInKnuts();
        DragotPurse.give(player, 10);
        DragotExchange.Result result = DragotExchange.sell(player, 10);
        WizardTestSupport.check(helper, result.ok(),
                () -> "the sale was refused: " + result.message().getString());
        return player.getData(ModAttachments.VAULT_DATA.get()).getTotalInKnuts() - before;
    }
}
