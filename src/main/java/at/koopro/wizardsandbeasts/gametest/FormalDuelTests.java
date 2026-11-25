package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.duel.FormalDuels;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.ministry.trace.LegalClass;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.vocation.VocationManager;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.Map;
import java.util.Optional;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * B5 (systems audit 2026-11-25), the duelist's formal duel: only a duelist challenges; nobody strikes before the
 * count of three; a blow never takes the last of the health; legal spells in a duel are kept from the Ministry,
 * Dark ones are not; the one who goes down loses and their wand is contested.
 */
public final class FormalDuelTests {

    private FormalDuelTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("duel_formal_challenge_count_and_fall",
                "duel: a duelist challenges, the count holds blows back, a blow never kills, legal casts are no "
                        + "incident, and the fallen duellist's wand is contested",
                FormalDuelTests::formalDuel);
    }

    private static void formalDuel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer duelist = WizardTestSupport.placeMockPlayer(helper, "DuelChallenger", GameType.SURVIVAL);
        ServerPlayer rival = WizardTestSupport.placeMockPlayer(helper, "DuelRival", GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, duelist);
        WizardTestSupport.parkAtOrigin(helper, rival);
        boolean scheduled = false;
        try {
            check(helper, FormalDuels.challenge(rival, duelist) == FormalDuels.ChallengeResult.NOT_DUELIST,
                    () -> "a wizard who is no duelist issued a formal challenge");

            duelist.setData(ModAttachments.OWL_DATA.get(), new PlayerOWLData(
                    Map.of(OWLSubject.DEFENCE_AGAINST_DARK_ARTS, OWLGrade.E), true, 1, 0L));
            check(helper, VocationManager.commit(duelist, Identifier.fromNamespaceAndPath("wizards_and_beasts",
                    "duelist")) == VocationManager.CommitResult.OK, () -> "setup: could not commit to Duelling");

            ItemStack wand = WandItem.createWand(WandWood.HOLLY, WandCore.PHOENIX_FEATHER, WandLength.values()[0],
                    WandFlexibility.PLIANT);
            wand.set(WandComponents.WAND_MASTER.get(), Optional.of(rival.getUUID()));
            rival.setItemInHand(InteractionHand.MAIN_HAND, wand);
            var historyBefore = WandComponents.getBondHistory(rival.getMainHandItem());

            check(helper, FormalDuels.challenge(duelist, rival) == FormalDuels.ChallengeResult.OK,
                    () -> "the duelist could not challenge");
            check(helper, FormalDuels.accept(rival), () -> "the rival could not accept");
            check(helper, FormalDuels.inDuel(duelist) && FormalDuels.inDuel(rival), () -> "no duel began");
            check(helper, FormalDuels.shieldsFromTrace(duelist, LegalClass.RESTRICTED),
                    () -> "a Stunner in a sanctioned duel was filed with the Ministry");
            check(helper, !FormalDuels.shieldsFromTrace(duelist, LegalClass.DARK),
                    () -> "Dark magic in a duel escaped the Ministry");

            float before = rival.getHealth();
            rival.hurtServer(level, level.damageSources().playerAttack(duelist), 4.0f);
            check(helper, rival.getHealth() == before, () -> "a blow landed before the count of three");

            helper.runAfterDelay(70, () -> {
                rival.invulnerableTime = 0;
                rival.hurtServer(level, level.damageSources().playerAttack(duelist), 1000.0f);
                check(helper, rival.isAlive() && rival.getHealth() >= 1.0f,
                        () -> "a duelling blow killed: health " + rival.getHealth());
            });
            helper.runAfterDelay(80, () -> {
                try {
                    check(helper, !FormalDuels.inDuel(duelist) && !FormalDuels.inDuel(rival),
                            () -> "the duel did not end when the rival went down");
                    check(helper, !WandComponents.getBondHistory(rival.getMainHandItem()).equals(historyBefore)
                                    || !WandComponents.getMaster(rival.getMainHandItem())
                                    .equals(Optional.of(rival.getUUID())),
                            () -> "the fallen duellist's wand was never contested");
                    helper.succeed();
                } finally {
                    FormalDuels.yieldDuel(rival);
                    WizardTestSupport.retire(helper, duelist);
                    WizardTestSupport.retire(helper, rival);
                }
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                FormalDuels.yieldDuel(rival);
                WizardTestSupport.retire(helper, duelist);
                WizardTestSupport.retire(helper, rival);
            }
        }
    }
}
