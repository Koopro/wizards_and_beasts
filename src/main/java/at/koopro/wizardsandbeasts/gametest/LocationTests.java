package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.entity.goblin.GoblinTellerEntity;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.location.LocationDefinition;
import at.koopro.wizardsandbeasts.location.LocationPresence;
import at.koopro.wizardsandbeasts.location.LocationProgression;
import at.koopro.wizardsandbeasts.location.LocationView;
import at.koopro.wizardsandbeasts.location.Locations;
import at.koopro.wizardsandbeasts.location.gringotts.HighSecurityVault;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.ActiveContract;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.TrackRecord;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import at.koopro.wizardsandbeasts.treasure.CurseBreaking;
import at.koopro.wizardsandbeasts.treasure.CurseKind;
import at.koopro.wizardsandbeasts.treasure.Expeditions;
import at.koopro.wizardsandbeasts.treasure.TreasureArtefact;
import at.koopro.wizardsandbeasts.treasure.TreasureArtefacts;
import at.koopro.wizardsandbeasts.treasure.TreasureSites;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * Locations end to end, Gringotts as the reference: the shipped location and treasure data loaded by the server's
 * own reload, a real goblin teller as the host, curse-breaking through the spell hook, the bank's services billing
 * the real vault, and the standing attachment that saves it all.
 */
@NullMarked
final class LocationTests {

    private static final Identifier GRINGOTTS = id("gringotts");
    private static final Identifier OLLIVANDERS = id("ollivanders");
    private static final Identifier DESERT_TOMB = id("desert_tomb");

    private LocationTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("location_gringotts_loads_and_opens_at_the_teller",
                "location: Gringotts loads, a teller admits the player, and the board offers work and services",
                LocationTests::opensAtTeller);
        tests.add("location_board_needs_presence",
                "location: away from the counter a contract cannot be taken or a service bought",
                LocationTests::needsPresence);
        tests.add("location_expedition_loop_end_to_end",
                "location: an artefact's curse is read, broken by counter-spell, surrendered for a fee; "
                        + "the expedition contract completes and standing rises",
                LocationTests::expeditionLoop);
        tests.add("location_goblins_break_curses_for_a_fee",
                "location: the bank's curse-breakers cleanse for the greater of fee and share of worth",
                LocationTests::goblinCurseBreaking);
        tests.add("location_tier_gates_high_security_vault",
                "location: the vault is refused below its tier; above it, rent is charged and items persist",
                LocationTests::vaultTierAndRent);
        tests.add("location_cursed_artefacts_hurt_their_bearer",
                "location: a carried jinxed artefact slows its bearer; a clean one does nothing",
                LocationTests::hazards);
        tests.add("location_ollivanders_host_is_the_wandmaker_only",
                "location: Ollivanders is kept by a wandmaker villager, not by any villager",
                LocationTests::ollivanderHost);
    }

    // -- helpers ---------------------------------------------------------------------------------------

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    private static ServerPlayer customer(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, UUID.randomUUID(), GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        return player;
    }

    private static GoblinTellerEntity teller(GameTestHelper helper) {
        GoblinTellerEntity goblin = helper.spawn(ModEntities.GOBLIN_TELLER.get(), new BlockPos(1, 1, 1));
        goblin.setNoGravity(true);
        goblin.setNoAi(true);
        return goblin;
    }

    private static TrackRecord standing(ServerPlayer player) {
        return LocationProgression.recordOf(player, GRINGOTTS);
    }

    private static void setStanding(ServerPlayer player, TrackRecord record) {
        player.setData(ModAttachments.LOCATION_STANDING.get(),
                player.getData(ModAttachments.LOCATION_STANDING.get()).with(GRINGOTTS.toString(), record));
    }

    private static long today(ServerPlayer player) {
        return SpellPractice.day(player.level().getGameTime());
    }

    private static ItemStack artefact(CurseKind curse, int layers, long value) {
        ItemStack stack = new ItemStack(CurrencyItemRegistry.ANCIENT_GOBLET.get());
        TreasureArtefacts.set(stack, new TreasureArtefact(DESERT_TOMB, Optional.of(curse), layers, false, value));
        return stack;
    }

    private static long vault(ServerPlayer player) {
        return player.getData(ModAttachments.VAULT_DATA.get()).getTotalInKnuts();
    }

    // -- scenarios -------------------------------------------------------------------------------------

    private static void opensAtTeller(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "BankCustomer");
        try {
            LocationDefinition def = Locations.open(GRINGOTTS);
            check(helper, def != null, () -> "the shipped gringotts location did not load or its module is off");
            check(helper, TreasureSites.get(DESERT_TOMB) != null, () -> "the desert tomb treasure site did not load");
            GoblinTellerEntity goblin = teller(helper);
            check(helper, LocationPresence.hostedBy(goblin) != null, () -> "a goblin teller keeps no location");
            check(helper, !LocationPresence.isAt(player, def), () -> "present before speaking to anyone");
            check(helper, LocationProgression.openAtHost(player, goblin), () -> "the teller did not open the board");
            check(helper, LocationPresence.isAt(player, def), () -> "not present after the teller admitted them");
            LocationView view = LocationProgression.view(player, GRINGOTTS);
            check(helper, view != null && view.present(), () -> "the view does not see the player at the counter");
            check(helper, view.tierIndex() == 0 && view.tierCount() == 5, () -> "tier " + view.tierIndex());
            check(helper, view.offers().size() == 3, () -> "a fresh customer was offered " + view.offers().size());
            // appraisal, curse-breaking, surrender, high-security vault — and courier work (travel.courier).
            check(helper, view.services().size() == 5, () -> "services shown: " + view.services().size());
            check(helper, view.services().stream().anyMatch(s -> !s.unlocked()),
                    () -> "the high-security vault should be locked for a customer");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void needsPresence(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "AbsentCustomer");
        try {
            check(helper, LocationProgression.accept(player, GRINGOTTS, "practice_curses")
                            == LocationProgression.Result.NOT_THERE,
                    () -> "a contract was taken with no teller in sight");
            check(helper, LocationProgression.useService(player, GRINGOTTS, id("appraisal"))
                            == LocationProgression.Result.NOT_THERE,
                    () -> "a service was bought away from the bank");
            GoblinTellerEntity goblin = teller(helper);
            LocationProgression.openAtHost(player, goblin);
            check(helper, LocationProgression.accept(player, GRINGOTTS, "practice_curses")
                            == LocationProgression.Result.DONE,
                    () -> "the contract was refused at the counter");
            player.teleportTo(player.getX() + 30, player.getY(), player.getZ());
            check(helper, LocationProgression.turnIn(player, GRINGOTTS, "practice_curses")
                            == LocationProgression.Result.NOT_THERE,
                    () -> "walking away did not leave the counter");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void expeditionLoop(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "CurseBreaker");
        try {
            GoblinTellerEntity goblin = teller(helper);
            LocationProgression.openAtHost(player, goblin);
            setStanding(player, standing(player).withActive(List.of(ActiveContract.taken("desert_expedition",
                    today(player)))));
            check(helper, Expeditions.targets(player).contains(DESERT_TOMB),
                    () -> "the expedition does not count as one to the desert tomb");

            // Found in the tomb: a jinx two layers deep, worth 600 Knuts.
            player.setItemInHand(InteractionHand.OFF_HAND, artefact(CurseKind.JINX, 2, 600));
            CurseBreaking.onSpellCast(player, "wizards_and_beasts:revelio");
            TreasureArtefact read = TreasureArtefacts.of(player.getOffhandItem());
            check(helper, read != null && read.identified(), () -> "Revelio did not read the curse");

            float before = player.getHealth();
            CurseBreaking.onSpellCast(player, "glacius");
            check(helper, player.getHealth() < before, () -> "the wrong counter did not flare");
            check(helper, TreasureArtefacts.of(player.getOffhandItem()).layers() == 2,
                    () -> "the wrong counter lifted a layer");

            CurseBreaking.onSpellCast(player, "finite_incantatem");
            CurseBreaking.onSpellCast(player, "finite_incantatem");
            TreasureArtefact broken = TreasureArtefacts.of(player.getOffhandItem());
            check(helper, broken != null && !broken.cursed(), () -> "two Finite Incantatems left the jinx");
            int afterCurse = standing(player).xp();
            check(helper, afterCurse == 20, () -> "breaking the curse gave " + afterCurse + " standing, not 20");

            long vaultBefore = vault(player);
            check(helper, LocationProgression.useService(player, GRINGOTTS, id("surrender_treasure"))
                    == LocationProgression.Result.DONE, () -> "the bank refused a clean artefact");
            check(helper, player.getOffhandItem().isEmpty(), () -> "the artefact stayed with the finder");
            long paid = vault(player) - vaultBefore;
            // Finder's fee: 600 × 0.5 at customer tier = 300; the expedition contract: 290.
            check(helper, paid == 300 + 290, () -> "surrender paid " + paid + " Knuts, not 590");
            TrackRecord after = standing(player);
            check(helper, after.activeContract("desert_expedition") == null, () -> "the expedition stayed open");
            check(helper, after.contractsCompleted() == 1, () -> "contracts completed " + after.contractsCompleted());
            // 20 (curse) + 30 (treasure returned) + 60 (contract).
            check(helper, after.xp() == 110, () -> "standing " + after.xp() + ", not 110");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void goblinCurseBreaking(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "PayingCustomer");
        try {
            LocationProgression.openAtHost(player, teller(helper));
            PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
            vault.credit(5_000);
            player.setItemInHand(InteractionHand.MAIN_HAND, artefact(CurseKind.FLAGRANTE, 3, 1_000));
            long before = vault(player);
            check(helper, LocationProgression.useService(player, GRINGOTTS, id("curse_breaking"))
                    == LocationProgression.Result.DONE, () -> "the goblins refused to break the curse");
            long charged = before - vault(player);
            // max(87 fee, 1000 × 0.4) = 400.
            check(helper, charged == 400, () -> "charged " + charged + ", not 400");
            TreasureArtefact clean = TreasureArtefacts.of(player.getMainHandItem());
            check(helper, clean != null && !clean.cursed() && clean.identified(), () -> "the artefact is still cursed");
            check(helper, standing(player).xp() == 0, () -> "paying the goblins earned curse-breaking credit");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void vaultTierAndRent(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "VaultRenter");
        try {
            LocationProgression.openAtHost(player, teller(helper));
            player.getData(ModAttachments.VAULT_DATA.get()).credit(2_000);
            check(helper, LocationProgression.useService(player, GRINGOTTS, id("high_security_vault"))
                    == LocationProgression.Result.TIER_TOO_LOW, () -> "a customer rented a high-security vault");

            setStanding(player, standing(player).withXp(150).withRank(1));
            long before = vault(player);
            check(helper, LocationProgression.useService(player, GRINGOTTS, id("high_security_vault"))
                    == LocationProgression.Result.DONE, () -> "an account holder was refused the vault");
            check(helper, before - vault(player) == 290, () -> "rent charged " + (before - vault(player)));
            check(helper, player.containerMenu instanceof ChestMenu, () -> "the vault did not open");
            ChestMenu menu = (ChestMenu) player.containerMenu;
            menu.getContainer().setItem(4, new ItemStack(Items.DIAMOND, 3));
            HighSecurityVault saved = player.getData(ModAttachments.HIGH_SECURITY_VAULT.get());
            check(helper, saved.stored() == 1 && saved.items().getFirst().stack().is(Items.DIAMOND),
                    () -> "the vault did not save what was put in it");
            check(helper, saved.rentedOn(today(player)), () -> "rent paid but not recorded");
            player.closeContainer();

            long again = vault(player);
            LocationProgression.useService(player, GRINGOTTS, id("high_security_vault"));
            check(helper, vault(player) == again, () -> "rent was charged twice in one week");
            player.closeContainer();
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void hazards(GameTestHelper helper) {
        ServerPlayer player = customer(helper, "HoardCarrier");
        try {
            ItemStack clean = artefact(CurseKind.JINX, 1, 100);
            TreasureArtefacts.set(clean, TreasureArtefacts.of(clean).cleansed());
            player.getInventory().add(clean);
            CurseBreaking.tickHazards(player);
            check(helper, !player.hasEffect(MobEffects.SLOWNESS), () -> "a clean artefact laid a curse");
            player.getInventory().add(artefact(CurseKind.JINX, 2, 100));
            CurseBreaking.tickHazards(player);
            check(helper, player.hasEffect(MobEffects.SLOWNESS) && player.hasEffect(MobEffects.MINING_FATIGUE),
                    () -> "a carried jinx did not slow its bearer");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void ollivanderHost(GameTestHelper helper) {
        check(helper, Locations.open(OLLIVANDERS) != null, () -> "the shipped ollivanders location did not load");
        Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        farmer.setNoAi(true);
        check(helper, LocationPresence.hostedBy(farmer) == null, () -> "an unemployed villager keeps Ollivanders");
        farmer.setVillagerData(farmer.getVillagerData().withProfession(helper.getLevel().registryAccess(),
                at.koopro.wizardsandbeasts.registry.ModVillager.WANDMAKER.getKey()));
        check(helper, OLLIVANDERS.equals(LocationPresence.hostedBy(farmer)), () -> "a wandmaker keeps no shop");
        helper.succeed();
    }
}
