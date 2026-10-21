package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerProfessionData;
import at.koopro.wizardsandbeasts.owl.OWLExaminationHandler;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.career.CareerProfile;
import at.koopro.wizardsandbeasts.owl.career.CareerWork;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.owl.career.track.CareerTracks;
import at.koopro.wizardsandbeasts.owl.career.track.CareerView;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.ActiveContract;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.TrackRecord;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.NullMarked;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * Career tracks end to end, through the real seams: the O.W.L. career choice, bestiary pages deepened by
 * {@link BestiaryDataHelper#setTier}, Gringotts billing, and the attachment that saves it all. The
 * Magizoologist track is the shipped data, loaded by the server's own datapack reload.
 */
@NullMarked
final class CareerTests {

    private static final String MAGIZOOLOGIST = "magizoologist";

    private CareerTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("career_track_loads_and_opens_on_choice",
                "career: choosing Magizoologist opens a rank-0 record and a board of offers",
                CareerTests::opensOnChoice);
        tests.add("career_survey_contract_pays_through_real_hooks",
                "career: a survey contract is completed by bestiary pages and pays the vault and experience",
                CareerTests::surveyContract);
        tests.add("career_delivery_contract_takes_items_only_when_complete",
                "career: a delivery is refused short, then takes exactly the items and pays",
                CareerTests::deliveryContract);
        tests.add("career_promotion_needs_xp_and_milestones",
                "career: experience alone does not promote; with the milestone met the rank and wage rise",
                CareerTests::promotion);
        tests.add("career_board_refuses_unoffered_and_overfull",
                "career: a contract not on today's board, or past the hand limit, is refused",
                CareerTests::boardRules);
        tests.add("career_progress_survives_relog", "career: rank, experience and contracts are saved",
                CareerTests::survivesRelog);
        tests.add("career_without_track_is_unchanged",
                "career: a career with no track keeps its flat wage and records nothing",
                CareerTests::flatCareerUnchanged);
    }

    // -- helpers ---------------------------------------------------------------------------------------

    private static ServerPlayer wizard(GameTestHelper helper, String name) {
        return wizard(helper, name, UUID.randomUUID());
    }

    private static ServerPlayer wizard(GameTestHelper helper, String name, UUID id) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, id, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        return player;
    }

    /** Gives the player the grades a Magizoologist needs and takes the career through the real choice. */
    private static void becomeMagizoologist(ServerPlayer player) {
        Map<OWLSubject, OWLGrade> grades = new EnumMap<>(OWLSubject.class);
        grades.put(OWLSubject.CARE_OF_MAGICAL_CREATURES, OWLGrade.E);
        grades.put(OWLSubject.CHARMS, OWLGrade.A);
        player.setData(ModAttachments.OWL_DATA.get(), new PlayerOWLData(grades, true, 1, 1L));
        OWLExaminationHandler.chooseProfession(player, Profession.MAGIZOOLOGIST);
    }

    private static TrackRecord record(ServerPlayer player) {
        return player.getData(ModAttachments.CAREER_PROGRESS.get()).track(MAGIZOOLOGIST);
    }

    private static void setRecord(ServerPlayer player, TrackRecord record) {
        player.setData(ModAttachments.CAREER_PROGRESS.get(),
                player.getData(ModAttachments.CAREER_PROGRESS.get()).with(MAGIZOOLOGIST, record));
    }

    private static long today(ServerPlayer player) {
        return SpellPractice.day(player.level().getGameTime());
    }

    private static Identifier creature(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    // -- scenarios -------------------------------------------------------------------------------------

    private static void opensOnChoice(GameTestHelper helper) {
        ServerPlayer player = wizard(helper, "CareerChooser");
        try {
            check(helper, CareerTracks.get(Profession.MAGIZOOLOGIST) != null,
                    () -> "the shipped magizoologist track did not load");
            becomeMagizoologist(player);
            check(helper, player.getData(ModAttachments.PROFESSION_DATA.get()).profession() == Profession.MAGIZOOLOGIST,
                    () -> "the career choice was refused");
            CareerView view = CareerProgression.view(player);
            check(helper, view.state() == CareerView.TRACK, () -> "the view is not a track view: " + view.state());
            check(helper, view.rankIndex() == 0 && view.rankCount() == 5,
                    () -> "rank " + view.rankIndex() + " of " + view.rankCount());
            check(helper, !view.offers().isEmpty(), () -> "a fresh Magizoologist was offered no contracts");
            check(helper, !view.goals().isEmpty(), () -> "the next rank names no milestone");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void surveyContract(GameTestHelper helper) {
        ServerPlayer player = wizard(helper, "Surveyor");
        try {
            becomeMagizoologist(player);
            setRecord(player, record(player).withActive(List.of(ActiveContract.taken("local_survey", today(player)))));
            // Today's wage already paid, so the vault moves by the contract alone (the same pages are also a
            // working day, which pays the wage — ProgressionTests cover that).
            player.setData(ModAttachments.PROFESSION_DATA.get(),
                    player.getData(ModAttachments.PROFESSION_DATA.get()).paidOn(today(player)));
            PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
            long before = vault.getTotalInKnuts();

            // Pages taken before the contract was in hand would not count; these are taken now, through the
            // real bestiary seam. A second look at one creature is not a second creature.
            BestiaryDataHelper.setTier(player, creature("niffler"), DiscoveryTier.OBSERVED);
            BestiaryDataHelper.setTier(player, creature("niffler"), DiscoveryTier.STUDIED);
            check(helper, record(player).activeContract("local_survey") != null
                            && record(player).activeContract("local_survey").progress() == 1,
                    () -> "one creature should count once toward a distinct survey");
            BestiaryDataHelper.setTier(player, creature("bowtruckle"), DiscoveryTier.OBSERVED);
            BestiaryDataHelper.setTier(player, creature("mooncalf"), DiscoveryTier.OBSERVED);

            TrackRecord after = record(player);
            check(helper, after.activeContract("local_survey") == null, () -> "the survey stayed open after three creatures");
            check(helper, after.contractsCompleted() == 1, () -> "completed " + after.contractsCompleted());
            // First field records also earn the Beast Division's finder's fee (ExplorationFees); that is not the survey.
            long fees = player.getData(ModAttachments.MONEY_JOURNAL.get()).entries().stream()
                    .filter(e -> e.flow().equals("exploration_fee")).mapToLong(e -> e.knuts()).sum();
            long paid = vault.getTotalInKnuts() - before - fees;
            check(helper, fees > 0, () -> "three first records paid no finder's fee");
            check(helper, paid == 435, () -> "the survey paid " + paid + " Knuts, not 435");
            // 4 page changes × experience (10 or 20, within caps) + 60 for the contract.
            check(helper, after.xp() >= 60 + 40, () -> "career experience " + after.xp());
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void deliveryContract(GameTestHelper helper) {
        ServerPlayer player = wizard(helper, "HairCourier");
        try {
            becomeMagizoologist(player);
            setRecord(player, record(player).withActive(List.of(ActiveContract.taken("unicorn_hair", today(player)))));
            player.getInventory().add(new ItemStack(WandItemRegistry.UNICORN_HAIR.get(), 1));

            check(helper, CareerProgression.turnIn(player, "unicorn_hair") == CareerProgression.Result.NOT_ENOUGH,
                    () -> "one hair was accepted for a two-hair contract");
            check(helper, player.getInventory().countItem(WandItemRegistry.UNICORN_HAIR.get()) == 1,
                    () -> "a refused hand-in still took the hair");

            player.getInventory().add(new ItemStack(WandItemRegistry.UNICORN_HAIR.get(), 2));
            PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
            long before = vault.getTotalInKnuts();
            check(helper, CareerProgression.turnIn(player, "unicorn_hair") == CareerProgression.Result.DONE,
                    () -> "a full delivery was refused");
            check(helper, player.getInventory().countItem(WandItemRegistry.UNICORN_HAIR.get()) == 1,
                    () -> "the hand-in did not take exactly two hairs");
            check(helper, vault.getTotalInKnuts() - before == 638, () -> "the delivery paid the wrong amount");
            check(helper, record(player).activeContract("unicorn_hair") == null, () -> "the delivery stayed in hand");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void promotion(GameTestHelper helper) {
        ServerPlayer player = wizard(helper, "Promotee");
        try {
            becomeMagizoologist(player);
            // Experience to spare but only four of the five Observed pages the rank asks for.
            setRecord(player, record(player).withXp(500));
            for (String species : List.of("niffler", "bowtruckle", "mooncalf", "hippogriff")) {
                BestiaryDataHelper.setTier(player, creature(species), DiscoveryTier.OBSERVED);
            }
            check(helper, record(player).rank() == 0, () -> "promoted without the milestone");

            BestiaryDataHelper.setTier(player, creature("thestral"), DiscoveryTier.OBSERVED);
            check(helper, record(player).rank() == 1, () -> "not promoted with experience and five pages");
            float multiplier = CareerProgression.wageMultiplier(player, Profession.MAGIZOOLOGIST);
            check(helper, Math.abs(multiplier - 1.25f) < 1e-4, () -> "rank 1 wage multiplier " + multiplier);

            PlayerVaultData vault = player.getData(ModAttachments.VAULT_DATA.get());
            long before = vault.getTotalInKnuts();
            player.setData(ModAttachments.PROFESSION_DATA.get(),
                    player.getData(ModAttachments.PROFESSION_DATA.get()).paidOn(-1));
            CareerWork.record(player, CareerWork.Kind.CREATURE_CARE);
            long expected = Math.round(CareerProfile.of(Profession.MAGIZOOLOGIST).dailyWage() * 1.25);
            check(helper, vault.getTotalInKnuts() - before == expected,
                    () -> "the day's wage at rank 1 was " + (vault.getTotalInKnuts() - before) + ", not " + expected);
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void boardRules(GameTestHelper helper) {
        ServerPlayer player = wizard(helper, "BoardReader");
        try {
            becomeMagizoologist(player);
            check(helper, CareerProgression.accept(player, "rare_research") == CareerProgression.Result.NOT_OFFERED,
                    () -> "a master's contract was taken by a trainee");
            List<CareerView.ContractLine> offers = CareerProgression.view(player).offers();
            check(helper, !offers.isEmpty(), () -> "no offers");
            String first = offers.getFirst().id();
            check(helper, CareerProgression.accept(player, first) == CareerProgression.Result.DONE,
                    () -> "an offered contract was refused");
            check(helper, record(player).activeContract(first) != null, () -> "the accepted contract is not in hand");
            check(helper, CareerProgression.view(player).offers().stream().noneMatch(o -> o.id().equals(first)),
                    () -> "a contract in hand is still on the board");

            // Fill the hand, then try one more.
            setRecord(player, record(player).withActive(List.of(
                    ActiveContract.taken("feeding_rounds", today(player)),
                    ActiveContract.taken("local_survey", today(player)))));
            List<CareerView.ContractLine> remaining = CareerProgression.view(player).offers();
            if (!remaining.isEmpty()) {
                check(helper, CareerProgression.accept(player, remaining.getFirst().id())
                                == CareerProgression.Result.BOARD_FULL,
                        () -> "a third contract was taken");
            }
            check(helper, CareerProgression.abandon(player, "feeding_rounds") == CareerProgression.Result.DONE
                            && record(player).activeContract("feeding_rounds") == null,
                    () -> "abandoning failed");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void survivesRelog(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer before = wizard(helper, "CareerRelog", id);
        becomeMagizoologist(before);
        TrackRecord saved = record(before).withXp(321).withRank(2)
                .withActive(List.of(ActiveContract.taken("feeding_rounds", today(before))));
        setRecord(before, saved);
        retire(helper, before);

        ServerPlayer after = WizardTestSupport.placeMockPlayer(helper, "CareerRelog", id, GameType.SURVIVAL);
        try {
            TrackRecord loaded = record(after);
            check(helper, loaded.xp() == 321 && loaded.rank() == 2, () -> "loaded " + loaded);
            check(helper, loaded.activeContract("feeding_rounds") != null, () -> "the contract in hand was lost");
            helper.succeed();
        } finally {
            retire(helper, after);
        }
    }

    private static void flatCareerUnchanged(GameTestHelper helper) {
        // A profession with no career_tracks/ file. (The Auror was this example until the Auror Office got a track.)
        ServerPlayer player = wizard(helper, "FlatJournalist");
        try {
            player.setData(ModAttachments.PROFESSION_DATA.get(), PlayerProfessionData.chosen(Profession.JOURNALIST, 1));
            CareerProgression.record(player, ActivityType.CREATURE_FED, creature("niffler"));
            check(helper, player.getData(ModAttachments.CAREER_PROGRESS.get()).equals(PlayerCareerProgress.EMPTY),
                    () -> "a career without a track recorded progress");
            check(helper, CareerProgression.wageMultiplier(player, Profession.JOURNALIST) == 1.0f,
                    () -> "a career without a track has a wage multiplier");
            check(helper, CareerProgression.view(player).state() == CareerView.FLAT_CAREER,
                    () -> "the Journalist's view is not the flat view");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }
}
