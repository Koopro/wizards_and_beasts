package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.corruption.DarkCorruptionService;
import at.koopro.wizardsandbeasts.effect.LumosFieldEffect;
import at.koopro.wizardsandbeasts.skill.GameplayStat;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondHistory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * What a skill node <em>does</em>, once it is allocated on a live player.
 *
 * <p>The education rework's premise is that a node buys something a player can point at, so these scenarios
 * check the two new mechanics that are visible in the world rather than in a tooltip: wandlight that reaches
 * further along your gaze, and the Dark Arts curriculum marking the student who takes it.
 *
 * <p>Everything else the rework added is either pure arithmetic ({@code WandAllegianceRules}, checked in unit
 * tests) or datapack shape ({@code SkillNodeJsonTest}, {@code SkillNodeProvenanceTest}).
 */
public final class EducationWebTests {

    /** The Lumos line: each allocated step is one further block of light along the caster's look vector. */
    private static final String LIGHT_STEADY = "lumos_steady";
    private static final String LIGHT_CORRIDOR = "lumos_corridor";
    /** The Dark Arts entry node, which accrues corruption at allocation. */
    private static final String DARK_KNOWLEDGE = "dark_knowledge";
    /** Wandlore's allegiance node: each level is one more defeat a wand survives. */
    private static final String ALLEGIANCE_GRIP = "allegiance_grip";

    private EducationWebTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("education_wandlight_reaches_further",
                "skills: a trained Lumos lights the corridor, not just the hand holding it",
                EducationWebTests::wandlightReachesFurther);
        tests.add("education_dark_study_marks_the_student",
                "skills: studying the Dark Arts accrues corruption that cannot be trained away",
                EducationWebTests::darkStudyMarksTheStudent);
        tests.add("education_allegiance_grip_keeps_the_wand",
                "skills: a Wandlore student's wand survives the defeat that would have taken it",
                EducationWebTests::allegianceGripKeepsTheWand);
    }

    // ── scenarios ───────────────────────────────────────────────────────────────────────────────

    private static void wandlightReachesFurther(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "LumosStudent");
        try {
            WizardTestSupport.parkAtOrigin(helper, player);
            // Look along +Z, level, so the lights land in open air rather than inside the floor.
            player.snapTo(player.getX(), player.getY(), player.getZ(), 0.0f, 0.0f);

            LumosFieldEffect.placeOrUpdateLight(player);
            int untrained = lightsAround(player);
            WizardTestSupport.check(helper, untrained == 1,
                    () -> "an untrained Lumos placed " + untrained + " lights, not the one at the wand");

            SkillSystemAPI.forceUnlock(player, LIGHT_STEADY);
            WizardTestSupport.check(helper,
                    SkillSystemAPI.getGameplayBonus(player, GameplayStat.LIGHT_REACH) >= 1.0f,
                    () -> "Steady Lumos granted no reach at all");
            LumosFieldEffect.placeOrUpdateLight(player);
            int trained = lightsAround(player);
            WizardTestSupport.check(helper, trained == 2,
                    () -> "a steady Lumos placed " + trained + " lights, expected two");

            SkillSystemAPI.forceUnlock(player, LIGHT_CORRIDOR);
            LumosFieldEffect.placeOrUpdateLight(player);
            int corridor = lightsAround(player);
            WizardTestSupport.check(helper, corridor > trained,
                    () -> "Carried Light added nothing: still " + corridor + " lights");

            // And it leaves nothing behind: the whole line is only ever borrowed light.
            LumosFieldEffect.removeLight(player);
            int afterNox = lightsAround(player);
            WizardTestSupport.check(helper, afterNox == 0,
                    () -> afterNox + " light blocks were left in the world after Nox");

            helper.succeed();
        } finally {
            LumosFieldEffect.removeLight(player);
            WizardTestSupport.retire(helper, player);
        }
    }

    /**
     * The consequence half of the Dark Arts web. Corruption is already read by purity-sensitive creatures and by
     * the character sheet, so a node that accrues it is a node with a cost the player will meet later.
     */
    private static void darkStudyMarksTheStudent(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "DarkStudent");
        try {
            WizardTestSupport.parkAtOrigin(helper, player);
            float before = DarkCorruptionService.get(player);

            SkillSystemAPI.forceUnlock(player, DARK_KNOWLEDGE);

            float after = DarkCorruptionService.get(player);
            WizardTestSupport.check(helper, after > before,
                    () -> "studying the Dark Arts left the student unmarked: " + before + " -> " + after);
            WizardTestSupport.check(helper,
                    SkillSystemAPI.getGameplayBonus(player, GameplayStat.CURSE_BACKLASH) > 0f,
                    () -> "the node bought no control over dangerous magic either, so it is pure cost");

            // The control it buys reaches curses and nothing else — that is the whole point of the stat.
            WizardTestSupport.check(helper,
                    SkillSystemAPI.getGameplayBonus(player, GameplayStat.WARD_INTEGRITY) == 0f,
                    () -> "Dark Arts study leaked into shield strength");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    /**
     * Wandlore's allegiance line, on a real pair of duellists.
     *
     * <p>The arithmetic is unit-tested; what this covers is the wiring, which a mutation pass caught as
     * untested: {@code WandAllegianceService.onDefeat} has to read the loser's trained grip and add it to the
     * wins the winner needs. Without that read the rule is correct and never consulted.
     */
    private static void allegianceGripKeepsTheWand(GameTestHelper helper) {
        ServerPlayer victim = WizardTestSupport.placeMockPlayer(helper, "WandloreStudent");
        ServerPlayer victor = WizardTestSupport.placeMockPlayer(helper, "WandloreRival");
        try {
            WizardTestSupport.parkAtOrigin(helper, victim);
            WizardTestSupport.parkAtOrigin(helper, victor);
            WizardTestSupport.check(helper, at.koopro.wizardsandbeasts.Config.enableWandAllegiance,
                    () -> "wand allegiance is switched off, so this scenario proves nothing");

            ItemStack wand = new ItemStack(at.koopro.wizardsandbeasts.registry.WandItemRegistry.WAND.get());
            wand.set(WandComponents.WAND_MASTER.get(), java.util.Optional.of(victim.getUUID()));
            wand.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), 1.0f);
            wand.set(WandComponents.WAND_BOND_HISTORY.get(),
                    WandBondHistory.EMPTY.withFirstMasterIfAbsent(victim.getUUID()));
            victim.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wand);

            SkillSystemAPI.forceUnlock(victim, "wand_study");
            SkillSystemAPI.forceUnlock(victim, "wand_appraisal");
            SkillSystemAPI.forceUnlock(victim, "wandlore_figures");
            SkillSystemAPI.forceUnlock(victim, ALLEGIANCE_GRIP);
            float grip = SkillSystemAPI.getGameplayBonus(victim, GameplayStat.ALLEGIANCE_GRIP);
            WizardTestSupport.check(helper, grip >= 1.0f,
                    () -> "the Allegiance node granted no grip at all: " + grip);

            // One defeat. A wand with no history needs BASE_WINS_TO_TRANSFER, and the grip adds to that, so a
            // trained wizard must still be holding their own wand afterwards.
            int baseWins = at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceRules.BASE_WINS_TO_TRANSFER;
            for (int duel = 0; duel < baseWins; duel++) {
                WandAllegianceService.onDefeat(victim, victor, WandAllegianceService.DefeatKind.DISARM,
                        java.util.List.of(victim.getMainHandItem()));
            }
            java.util.Optional<java.util.UUID> master =
                    WandComponents.getMaster(victim.getMainHandItem());
            WizardTestSupport.check(helper, master.equals(java.util.Optional.of(victim.getUUID())),
                    () -> "after " + baseWins + " defeats the trained wizard's wand had already changed hands");

            // Keep losing and it goes anyway: the grip buys duels, not immunity.
            for (int duel = 0; duel <= Math.round(grip); duel++) {
                WandAllegianceService.onDefeat(victim, victor, WandAllegianceService.DefeatKind.DISARM,
                        java.util.List.of(victim.getMainHandItem()));
            }
            WizardTestSupport.check(helper,
                    WandComponents.getMaster(victim.getMainHandItem())
                            .equals(java.util.Optional.of(victor.getUUID())),
                    () -> "losing repeatedly never lost the wand, which makes the grip immunity");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, victim);
            WizardTestSupport.retire(helper, victor);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** Light blocks within a few blocks of the player — the ones Lumos places and nothing else. */
    private static int lightsAround(ServerPlayer player) {
        BlockPos centre = player.blockPosition();
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-6, -2, -6), centre.offset(6, 4, 6))) {
            if (player.level().getBlockState(pos).is(Blocks.LIGHT)) {
                found++;
            }
        }
        return found;
    }
}
