package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.apparition.ApparitionService;
import at.koopro.wizardsandbeasts.apparition.ApparitionStartResult;
import at.koopro.wizardsandbeasts.apparition.charge.ApparitionChargeManager;
import at.koopro.wizardsandbeasts.currency.vault.GringottsCounter;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.entity.goblin.GoblinTellerEntity;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.item.trinket.HermionesBagMenu;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.network.currency.VaultActionC2SPayload;
import at.koopro.wizardsandbeasts.network.owl.RequestOWLExamPacket;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import at.koopro.wizardsandbeasts.standing.deed.DeedService;
import at.koopro.wizardsandbeasts.standing.deed.DeedTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Server authority (documentation/MULTIPLAYER_AUDIT.md): each scenario is one path a client could use to get something
 * the server never granted, and pins the fix.
 */
public final class AuthorityTests {

    private AuthorityTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("authority_vault_only_at_the_tellers_counter",
                "authority: a vault action is refused away from the teller that admitted the player",
                AuthorityTests::vaultOnlyAtCounter);
        tests.add("authority_beaded_bag_cannot_duplicate",
                "authority: the open bag's own slot is pinned, and every change is written into the bag at once",
                AuthorityTests::beadedBagCannotDuplicate);
        tests.add("authority_apparition_charge_dies_with_the_wizard",
                "authority: an Apparition attempt in flight ends when the wizard dies",
                AuthorityTests::apparitionChargeDiesWithTheWizard);
        tests.add("authority_owl_exam_needs_a_desk",
                "authority: the OWL exam is sat at an examination desk, not wherever a packet is sent from",
                AuthorityTests::owlExamNeedsADesk);
        tests.add("authority_deed_cooldown_survives_a_relog",
                "authority: a magical deed's cooldown is still running after the player logs out and back in",
                AuthorityTests::deedCooldownSurvivesARelog);
    }

    private static void vaultOnlyAtCounter(GameTestHelper helper) {
        ServerPlayer customer = player(helper, "VaultCustomer");
        try {
            customer.getInventory().add(new ItemStack(CurrencyItemRegistry.KNUT.get(), 10));
            PlayerVaultData vault = customer.getData(ModAttachments.VAULT_DATA.get());
            long before = vault.getKnuts();
            VaultActionC2SPayload deposit = new VaultActionC2SPayload(
                    VaultActionC2SPayload.Action.DEPOSIT_KNUT.ordinal(), 5);

            VaultActionC2SPayload.perform(customer, deposit);
            check(helper, vault.getKnuts() == before, () -> "a vault deposit went through with no teller in sight");

            GoblinTellerEntity teller = helper.spawn(ModEntities.GOBLIN_TELLER.get(), new BlockPos(2, 1, 0));
            teller.setNoAi(true);
            GringottsCounter.admit(customer, teller);
            VaultActionC2SPayload.perform(customer, deposit);
            check(helper, vault.getKnuts() == before + 5, () -> "a deposit at the counter did not go through");

            BlockPos far = helper.absolutePos(new BlockPos(0, 1, 0)).offset(40, 0, 0);
            customer.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
            VaultActionC2SPayload.perform(customer, deposit);
            check(helper, vault.getKnuts() == before + 5, () -> "a deposit went through forty blocks from the teller");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, customer);
        }
    }

    private static void beadedBagCannotDuplicate(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "BagOwner");
        try {
            owner.getInventory().setSelectedSlot(0);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TrinketItemRegistry.HERMIONES_BEADED_BAG.get()));
            ItemStack bag = owner.getMainHandItem();
            HermionesBagMenu menu = new HermionesBagMenu(1, owner.getInventory(), InteractionHand.MAIN_HAND);

            menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 3));
            check(helper, bag.get(ModDataComponents.HERMIONES_BAG_INVENTORY.get()) != null,
                    () -> "a change to the bag's contents was not written into the bag at once");

            int bagSlot = HermionesBagMenu.SIZE + 27; // hotbar slot 0, where the open bag sits
            menu.clicked(bagSlot, 0, ClickType.PICKUP, owner);
            check(helper, menu.getCarried().isEmpty() && owner.getMainHandItem() == bag,
                    () -> "the open bag was picked up out of its own slot");
            menu.clicked(HermionesBagMenu.SIZE, 0, ClickType.SWAP, owner);
            check(helper, owner.getMainHandItem() == bag, () -> "a hotbar swap moved the open bag");
            check(helper, menu.quickMoveStack(owner, bagSlot).isEmpty() && owner.getMainHandItem() == bag,
                    () -> "a shift-click moved the open bag");

            // Take the diamonds out; the bag must forget them the same moment.
            menu.quickMoveStack(owner, 0);
            ItemStack reopened = owner.getMainHandItem();
            HermionesBagMenu second = new HermionesBagMenu(2, owner.getInventory(), InteractionHand.MAIN_HAND);
            check(helper, second.getSlot(0).getItem().isEmpty(),
                    () -> "the bag still held the diamonds after they were taken out: " + second.getSlot(0).getItem());
            check(helper, reopened == bag, () -> "setup: the bag in hand changed");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, owner);
        }
    }

    private static void apparitionChargeDiesWithTheWizard(GameTestHelper helper) {
        ServerPlayer wizard = WizardTestSupport.placeMockPlayer(helper, "ChargingWizard", GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, wizard);
        Runnable apparition = WizardTestSupport.leaseModule(Module.APPARITION);
        Runnable abilities = WizardTestSupport.leaseModule(Module.PLAYER_ABILITIES);
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            PlayerAbilityHelper.setApparitionUnlocked(wizard, true);
            ApparitionStartResult started = ApparitionService.tryStart(wizard);
            check(helper, started == ApparitionStartResult.STARTED, () -> "setup: the attempt did not start: " + started);
            check(helper, ApparitionChargeManager.isCharging(wizard), () -> "setup: no charge in flight");

            wizard.kill(helper.getLevel());
            check(helper, !ApparitionChargeManager.isCharging(wizard),
                    () -> "the Apparition attempt outlived the wizard who was holding it");
            helper.succeed();
        } finally {
            apparition.run();
            abilities.run();
            WizardTestSupport.retire(helper, wizard);
        }
    }

    private static void owlExamNeedsADesk(GameTestHelper helper) {
        ServerPlayer candidate = player(helper, "OwlCandidate");
        try {
            check(helper, !RequestOWLExamPacket.atExaminationDesk(candidate),
                    () -> "an examination desk was found in an empty room");
            helper.setBlock(new BlockPos(2, 1, 1), ModBlocks.EXAMINATION_DESK.get());
            check(helper, RequestOWLExamPacket.atExaminationDesk(candidate),
                    () -> "a desk two blocks away was not found");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, candidate);
        }
    }

    private static void deedCooldownSurvivesARelog(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer first = WizardTestSupport.placeMockPlayer(helper, "DeedDoer", id, GameType.SURVIVAL);
        ServerPlayer[] again = {null};
        try {
            DeedService.clearCooldowns(first);
            int scored = DeedService.fire(first, DeedTrigger.SPELL_CAST, "expecto_patronum", null);
            check(helper, scored == 1, () -> "setup: the Patronus deed did not score: " + scored);
            check(helper, DeedService.fire(first, DeedTrigger.SPELL_CAST, "expecto_patronum", null) == 0,
                    () -> "the deed scored twice inside its cooldown");

            // A relog: the logout every per-session state listens for, then the same profile placed again.
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(first));
            WizardTestSupport.retire(helper, first);
            again[0] = WizardTestSupport.placeMockPlayer(helper, "DeedDoer", id, GameType.SURVIVAL);
            int afterRelog = DeedService.fire(again[0], DeedTrigger.SPELL_CAST, "expecto_patronum", null);
            check(helper, afterRelog == 0, () -> "a relog reset the deed's cooldown: it scored " + afterRelog);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, again[0]);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
