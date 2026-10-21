package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.GringottsLedger;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.item.trinket.MinistryLicenseScrollItem;
import at.koopro.wizardsandbeasts.ministry.licence.LicenceUpgrade;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicences;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.ollivander.OllivanderPrice;
import at.koopro.wizardsandbeasts.wand.ollivander.WandCare;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.Map;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The economy's transaction safety and its two new sinks against the live server (documentation/ECONOMY.md §5–6):
 * a refused charge leaves every coin where it was, Ollivander's wand care quotes then bills once, the Ministry's
 * endorsement fee is taken only on success, and the ledger books each flow.
 *
 * <p>Every scenario restores the config values it changes in {@code finally}.
 */
final class EconomyTests {

    private EconomyTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("economy_refused_charge_leaves_vault_identical",
                "economy: a short vault keeps its exact coins through the wand price, wand care and an endorsement",
                EconomyTests::refusedChargesLeaveTheVault);
        tests.add("economy_wand_care",
                "economy: Ollivander quotes, then tends a worn wand for one charge; a snapped wand is refused",
                EconomyTests::wandCare);
        tests.add("economy_endorsement_fee",
                "economy: the endorsement fee is taken only on success; a short vault keeps parchment, ink and coins",
                EconomyTests::endorsementFee);
        tests.add("economy_ledger_records_flows",
                "economy: wages are sources, services sinks, a refund cancels its charge, and the vault is synced",
                EconomyTests::ledger);
    }

    private static PlayerVaultData vault(ServerPlayer player) {
        return player.getData(ModAttachments.VAULT_DATA.get());
    }

    private static long[] coins(PlayerVaultData vault) {
        return new long[]{vault.getGalleons(), vault.getSickles(), vault.getKnuts()};
    }

    private static boolean same(long[] coins, PlayerVaultData vault) {
        return coins[0] == vault.getGalleons() && coins[1] == vault.getSickles() && coins[2] == vault.getKnuts();
    }

    private static String show(PlayerVaultData vault) {
        return vault.getGalleons() + "G " + vault.getSickles() + "S " + vault.getKnuts() + "K";
    }

    private static ItemStack wornWand(float integrity) {
        ItemStack wand = new ItemStack(WandItemRegistry.WAND.get());
        wand.set(WandComponents.WAND_INTEGRITY.get(), integrity);
        return wand;
    }

    private static void requireGringotts(GameTestHelper helper) {
        check(helper, ModuleManager.isEnabled(Module.GRINGOTTS), () -> "setup: the Gringotts module is off");
    }

    private static void refusedChargesLeaveTheVault(GameTestHelper helper) {
        requireGringotts(helper);
        ServerPlayer customer = WizardTestSupport.placeMockPlayer(helper, "ShortCustomer", GameType.SURVIVAL);
        int carePrice = Config.wandCarePriceKnuts;
        int fee = Config.licenceEndorsementFeeKnuts;
        try {
            Config.wandCarePriceKnuts = 50_000;
            Config.licenceEndorsementFeeKnuts = 50_000;
            PlayerVaultData vault = vault(customer);
            vault.depositGalleons(3);
            vault.depositSickles(5);
            vault.depositKnuts(7);
            long[] before = coins(vault);

            // A second wand: seven Galleons against three.
            OllivanderPrice.recordSale(customer);
            check(helper, !OllivanderPrice.pay(customer) && same(before, vault),
                    () -> "a refused wand purchase changed the vault: " + show(vault));

            // Wand care: quote, then confirm against a vault that cannot pay.
            ItemStack wand = wornWand(0.4f);
            customer.setItemInHand(InteractionHand.MAIN_HAND, wand);
            WandCare.offer(customer);
            WandCare.offer(customer);
            check(helper, same(before, vault) && WandComponents.getIntegrity(wand) == 0.4f,
                    () -> "a refused wand care changed the vault or the wand: " + show(vault));

            // An endorsement the customer qualifies for, but cannot pay for.
            prepareEndorsement(customer);
            LicenceUpgrade.Outcome outcome = LicenceUpgrade.attempt(customer, scroll(customer));
            check(helper, !outcome.upgraded() && same(before, vault),
                    () -> "a refused endorsement changed the vault: " + show(vault) + " / " + outcome.message().getString());
            helper.succeed();
        } finally {
            Config.wandCarePriceKnuts = carePrice;
            Config.licenceEndorsementFeeKnuts = fee;
            WizardTestSupport.retire(helper, customer);
        }
    }

    private static void wandCare(GameTestHelper helper) {
        requireGringotts(helper);
        ServerPlayer owner = WizardTestSupport.placeMockPlayer(helper, "WornWandOwner", GameType.SURVIVAL);
        MinecraftServer server = helper.getLevel().getServer();
        int carePrice = Config.wandCarePriceKnuts;
        try {
            Config.wandCarePriceKnuts = 986;
            PlayerVaultData vault = vault(owner);
            vault.depositGalleons(5);
            long start = vault.getTotalInKnuts();
            long tended = GringottsLedger.get(server).totals(MoneyFlow.WAND_CARE).count();

            ItemStack wand = wornWand(0.4f);
            owner.setItemInHand(InteractionHand.MAIN_HAND, wand);
            long price = WandCare.priceFor(wand);
            check(helper, price > 0 && price % CurrencyHelper.KNUTS_PER_SICKLE == 0, () -> "price " + price);

            check(helper, WandCare.offer(owner) && vault.getTotalInKnuts() == start
                            && WandComponents.getIntegrity(wand) == 0.4f,
                    () -> "the first showing did more than quote: " + show(vault));
            check(helper, WandCare.offer(owner) && WandComponents.getIntegrity(wand) == 1.0f
                            && vault.getTotalInKnuts() == start - price,
                    () -> "the second showing did not tend for exactly the price: " + show(vault));
            check(helper, GringottsLedger.get(server).totals(MoneyFlow.WAND_CARE).count() == tended + 1,
                    () -> "wand care was not booked as a sink");
            check(helper, !WandCare.offer(owner) && vault.getTotalInKnuts() == start - price,
                    () -> "a sound wand was tended again, or billed");

            // A quote is for the wand it was given on: another worn wand needs its own.
            ItemStack other = wornWand(0.6f);
            owner.setItemInHand(InteractionHand.MAIN_HAND, other);
            WandCare.offer(owner);
            owner.setItemInHand(InteractionHand.MAIN_HAND, wornWand(0.3f));
            WandCare.offer(owner);
            check(helper, WandComponents.getIntegrity(owner.getMainHandItem()) == 0.3f
                            && vault.getTotalInKnuts() == start - price,
                    () -> "a quote for one wand paid for another");

            // Snapped: beyond him, and nothing billed; the trial is where the player goes instead.
            ItemStack snapped = wornWand(0.05f);
            owner.setItemInHand(InteractionHand.MAIN_HAND, snapped);
            check(helper, !WandCare.offer(owner) && !WandCare.offer(owner)
                            && WandComponents.getIntegrity(snapped) == 0.05f && vault.getTotalInKnuts() == start - price,
                    () -> "a snapped wand was mended or billed");
            helper.succeed();
        } finally {
            Config.wandCarePriceKnuts = carePrice;
            WizardTestSupport.retire(helper, owner);
        }
    }

    private static void prepareEndorsement(ServerPlayer player) {
        ItemStack scroll = MinistryLicenseScrollItem.issue(
                new ItemStack(TrinketItemRegistry.MINISTRY_LICENSE_SCROLL.get()), LicenseType.APPARITION, player.getUUID());
        player.setItemInHand(InteractionHand.OFF_HAND, scroll);
        PlayerOWLData owls = player.getData(ModAttachments.OWL_DATA.get());
        player.setData(ModAttachments.OWL_DATA.get(), owls.withGrades(Map.of(
                LicenseType.APPARITION.examinedSubject(), OWLGrade.O)));
        player.getInventory().add(new ItemStack(MiscItemRegistry.PARCHMENT.get()));
        player.getInventory().add(new ItemStack(MiscItemRegistry.INK_BOTTLE.get()));
    }

    private static ItemStack scroll(ServerPlayer player) {
        return player.getOffhandItem();
    }

    private static int count(ServerPlayer player, Item item) {
        return CurrencyHelper.countItem(player.getInventory(), item);
    }

    private static void endorsementFee(GameTestHelper helper) {
        requireGringotts(helper);
        ServerPlayer applicant = WizardTestSupport.placeMockPlayer(helper, "Endorsee", GameType.SURVIVAL);
        MinecraftServer server = helper.getLevel().getServer();
        int fee = Config.licenceEndorsementFeeKnuts;
        try {
            Config.licenceEndorsementFeeKnuts = 986;
            prepareEndorsement(applicant);
            PlayerVaultData vault = vault(applicant);
            vault.depositGalleons(1);
            long[] before = coins(vault);
            int parchment = count(applicant, MiscItemRegistry.PARCHMENT.get());
            int ink = count(applicant, MiscItemRegistry.INK_BOTTLE.get());

            // Short: one Galleon against two.
            LicenceUpgrade.Outcome refused = LicenceUpgrade.attempt(applicant, scroll(applicant));
            check(helper, !refused.upgraded() && same(before, vault)
                            && count(applicant, MiscItemRegistry.PARCHMENT.get()) == parchment
                            && count(applicant, MiscItemRegistry.INK_BOTTLE.get()) == ink
                            && MinistryLicences.read(scroll(applicant)).rank() == 0,
                    () -> "a short vault lost something: " + show(vault) + " / " + refused.message().getString());

            long endorsements = GringottsLedger.get(server).totals(MoneyFlow.LICENCE_ENDORSEMENT).count();
            vault.depositGalleons(1);
            long total = vault.getTotalInKnuts();
            LicenceUpgrade.Outcome done = LicenceUpgrade.attempt(applicant, scroll(applicant));
            check(helper, done.upgraded() && MinistryLicences.read(scroll(applicant)).rank() == 1
                            && vault.getTotalInKnuts() == total - LicenceUpgrade.fee(1, 986)
                            && count(applicant, MiscItemRegistry.PARCHMENT.get()) == parchment - 1
                            && count(applicant, MiscItemRegistry.INK_BOTTLE.get()) == ink - 1,
                    () -> "the endorsement did not take exactly its fee and stationery: " + show(vault)
                            + " / " + done.message().getString());
            check(helper, GringottsLedger.get(server).totals(MoneyFlow.LICENCE_ENDORSEMENT).count() == endorsements + 1,
                    () -> "the endorsement was not booked as a sink");
            helper.succeed();
        } finally {
            Config.licenceEndorsementFeeKnuts = fee;
            WizardTestSupport.retire(helper, applicant);
        }
    }

    private static void ledger(GameTestHelper helper) {
        ServerPlayer wizard = WizardTestSupport.placeMockPlayer(helper, "LedgerWizard", GameType.SURVIVAL);
        MinecraftServer server = helper.getLevel().getServer();
        try {
            GringottsLedger book = GringottsLedger.get(server);
            long sources = book.knutsOf(MoneyFlow.Kind.SOURCE);
            long sinks = book.knutsOf(MoneyFlow.Kind.SINK);
            PlayerVaultData vault = vault(wizard);

            check(helper, GringottsBilling.credit(wizard, MoneyFlow.CAREER_WAGE, 500)
                            && vault.getGalleons() == 1 && vault.getKnuts() == 7,
                    () -> "a wage was not paid in the largest coins: " + show(vault));
            check(helper, book.knutsOf(MoneyFlow.Kind.SOURCE) == sources + 500, () -> "the wage was not a source");

            check(helper, GringottsBilling.charge(wizard, MoneyFlow.FLOO_REGISTRATION, 493), () -> "could not pay");
            check(helper, book.knutsOf(MoneyFlow.Kind.SINK) == sinks + 493, () -> "the fee was not a sink");
            GringottsBilling.refund(wizard, MoneyFlow.FLOO_REGISTRATION, 493);
            check(helper, book.knutsOf(MoneyFlow.Kind.SINK) == sinks && vault.getTotalInKnuts() == 500,
                    () -> "a refund did not cancel its charge: " + show(vault));

            check(helper, !GringottsBilling.charge(wizard, MoneyFlow.WAND_CARE, 501)
                            && book.knutsOf(MoneyFlow.Kind.SINK) == sinks && vault.getTotalInKnuts() == 500,
                    () -> "a refused charge was booked or taken");
            boolean wrongWay;
            try {
                GringottsBilling.credit(wizard, MoneyFlow.WAND_CARE, 1);
                wrongWay = false;
            } catch (IllegalArgumentException expected) {
                wrongWay = true;
            }
            check(helper, wrongWay && vault.getTotalInKnuts() == 500, () -> "a sink was usable as a credit");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wizard);
        }
    }
}
