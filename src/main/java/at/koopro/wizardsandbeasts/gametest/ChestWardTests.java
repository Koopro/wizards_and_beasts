package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.vocation.VocationManager;
import at.koopro.wizardsandbeasts.treasure.ChestWards;
import at.koopro.wizardsandbeasts.treasure.CurseBreaking;
import at.koopro.wizardsandbeasts.treasure.CurseKind;
import at.koopro.wizardsandbeasts.treasure.TreasureArtefact;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.Map;
import java.util.Optional;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * B5 (systems audit 2026-11-25), the Curse-Breaker: a tomb chest that gives up a cursed artefact is warded. Read it,
 * then counter it in time; a blind counter or the clock fires it. A Curse-Breaker reads it on sight.
 */
public final class ChestWardTests {

    private static final Identifier SITE = Identifier.fromNamespaceAndPath("wizards_and_beasts", "desert_tomb");

    private ChestWardTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("curse_ward_read_counter_blind_and_clock",
                "curse wards: Revelio then the counter disarms; a blind counter fires; the clock fires; a "
                        + "Curse-Breaker reads the ward on sight",
                ChestWardTests::wards);
    }

    private static TreasureArtefact cursed(CurseKind curse, int layers) {
        return new TreasureArtefact(SITE, Optional.of(curse), layers, false, 100);
    }

    private static void wards(GameTestHelper helper) {
        ServerPlayer novice = WizardTestSupport.placeMockPlayer(helper, "WardNovice", GameType.SURVIVAL);
        ServerPlayer breaker = WizardTestSupport.placeMockPlayer(helper, "WardBreaker", GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, novice);
        WizardTestSupport.parkAtOrigin(helper, breaker);
        BlockPos chest = helper.absolutePos(new BlockPos(1, 1, 1));
        boolean scheduled = false;
        try {
            // Read, then the right counter: disarmed, unhurt.
            float health = novice.getHealth();
            ChestWards.raise(novice, cursed(CurseKind.JINX, 2), chest);
            check(helper, ChestWards.wardOn(novice).map(w -> !w.read()).orElse(false),
                    () -> "an ordinary wizard read the ward without Revelio");
            CurseBreaking.onSpellCast(novice, "wizards_and_beasts:revelio");
            CurseBreaking.onSpellCast(novice, "finite_incantatem");
            check(helper, ChestWards.wardOn(novice).isEmpty() && novice.getHealth() == health,
                    () -> "Revelio and Finite did not disarm the jinx ward cleanly");

            // A counter cast blind: it fires.
            ChestWards.raise(novice, cursed(CurseKind.JINX, 2), chest);
            CurseBreaking.onSpellCast(novice, "finite_incantatem");
            check(helper, ChestWards.wardOn(novice).isEmpty() && novice.getHealth() < health,
                    () -> "a blind counter did not fire the ward");

            // A Curse-Breaker reads it on sight and has twice as long.
            breaker.setData(ModAttachments.OWL_DATA.get(), new PlayerOWLData(
                    Map.of(OWLSubject.TRANSFIGURATION, OWLGrade.E), true, 1, 0L));
            check(helper, VocationManager.commit(breaker, Identifier.fromNamespaceAndPath("wizards_and_beasts",
                    "explorer")) == VocationManager.CommitResult.OK, () -> "setup: could not commit to Exploring");
            float breakerHealth = breaker.getHealth();
            ChestWards.raise(breaker, cursed(CurseKind.FLAGRANTE, 1), chest);
            check(helper, ChestWards.wardOn(breaker).map(ChestWards.Ward::read).orElse(false),
                    () -> "the Curse-Breaker did not read the ward on sight");
            CurseBreaking.onSpellCast(breaker, "glacius");
            check(helper, ChestWards.wardOn(breaker).isEmpty() && breaker.getHealth() == breakerHealth,
                    () -> "the Curse-Breaker's Glacius did not disarm the Flagrante ward");

            // The clock: left alone, the ward fires after ten seconds.
            novice.invulnerableTime = 0;
            novice.setHealth(novice.getMaxHealth());
            ChestWards.raise(novice, cursed(CurseKind.SEALING, 1), chest);
            helper.runAfterDelay(ChestWards.WARD_TICKS + 15, () -> {
                try {
                    check(helper, ChestWards.wardOn(novice).isEmpty(), () -> "the ward never fired on the clock");
                    check(helper, novice.getHealth() < novice.getMaxHealth(),
                            () -> "the ward fired on the clock but did no harm");
                    helper.succeed();
                } finally {
                    WizardTestSupport.retire(helper, novice);
                    WizardTestSupport.retire(helper, breaker);
                }
            });
            scheduled = true;
        } finally {
            if (!scheduled) {
                WizardTestSupport.retire(helper, novice);
                WizardTestSupport.retire(helper, breaker);
            }
        }
    }
}
