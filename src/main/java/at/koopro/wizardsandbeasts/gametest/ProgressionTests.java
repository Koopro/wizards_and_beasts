package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.proficiency.ProficiencyScaler;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellProficiencyTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;

/**
 * The progression loop's rules in a live server (documentation/PROGRESSION_MAP.md): practice is spread over days, the
 * tier and the power curve tell the same story, vanilla XP buys no wizardry, and a bestiary page completed pays the
 * skill web.
 */
public final class ProgressionTests {

    private ProgressionTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("progression_practice_is_spread_over_days",
                "progression: one spell takes a limited amount of practice a day; past it the cast teaches nothing",
                ProgressionTests::practiceIsSpread);
        tests.add("progression_mastered_casts_at_full_strength",
                "progression: a mastered spell casts at the ceiling and a proficient one at least at baseline, "
                        + "even from a save written before the curves agreed",
                ProgressionTests::masteredCastsAtFullStrength);
        tests.add("progression_skill_points_come_from_magic_not_xp",
                "progression: vanilla XP levels award no skill points; a bestiary page reaching KNOWN awards one",
                ProgressionTests::skillPointsFromMagic);
    }

    private static void practiceIsSpread(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "PracticeWizard");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            String spell = Spells.byId("wizards_and_beasts:lumos").getId();
            PlayerSpellData data = wizard.getData(ModAttachments.SPELL_DATA.get());
            for (int i = 0; i < SpellPractice.DAILY_PRACTICE + 10; i++) {
                SpellProficiencyTracker.recordSuccessfulHit(wizard, spell);
            }
            int hits = data.getSuccessfulHits(spell);
            WizardTestSupport.check(helper, hits == SpellPractice.DAILY_PRACTICE,
                    () -> "a day's practice counted " + hits + " times, not " + SpellPractice.DAILY_PRACTICE);
            WizardTestSupport.check(helper, data.getSpellProficiency(spell) <= SpellPractice.curve(hits * 1.5f) + 1e-4,
                    () -> "the spell grew past what a day's practice is worth: " + data.getSpellProficiency(spell));
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wizard);
        }
    }

    private static void masteredCastsAtFullStrength(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "MasteryWizard");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            String spell = Spells.byId("wizards_and_beasts:stupefy").getId();
            PlayerSpellData data = wizard.getData(ModAttachments.SPELL_DATA.get());
            // The state an old save is in: 200 hits, and the drifting float's 0.4.
            data.setSuccessfulHits(spell, Proficiency.MASTERED.getCastsRequired());
            data.setSpellProficiency(spell, 0.4f);
            float mastered = ProficiencyScaler.getProfileForPlayer(wizard, Identifier.parse(spell)).damageMult();
            WizardTestSupport.check(helper, mastered >= 1.49f,
                    () -> "a mastered spell cast at " + mastered + "x, not at the ceiling");
            data.setSuccessfulHits(spell, Proficiency.PROFICIENT.getCastsRequired());
            data.setSpellProficiency(spell, 0.1f);
            float proficient = ProficiencyScaler.getProfileForPlayer(wizard, Identifier.parse(spell)).damageMult();
            WizardTestSupport.check(helper, proficient >= 1.0f,
                    () -> "a proficient spell cast weaker than an untrained one: " + proficient + "x");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wizard);
        }
    }

    private static void skillPointsFromMagic(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "PointsWizard");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            int before = SkillSystemAPI.getSkillData(wizard).getSkillPoints();
            wizard.giveExperienceLevels(10);
            int afterXp = SkillSystemAPI.getSkillData(wizard).getSkillPoints();
            WizardTestSupport.check(helper, afterXp == before,
                    () -> "ten XP levels bought " + (afterXp - before) + " skill points");

            @SuppressWarnings("unchecked")
            EntityType<GenericBeastEntity> type = (EntityType<GenericBeastEntity>) ModCreatures.ENTITIES.get("horklump").get();
            GenericBeastEntity horklump = helper.spawn(type, new BlockPos(2, 1, 0));
            horklump.setNoAi(true);
            BestiaryDiscoveryHandler.witnessedSignature(wizard, horklump);
            WizardTestSupport.check(helper, BestiaryDataHelper.getTier(wizard,
                            Identifier.fromNamespaceAndPath("wizards_and_beasts", "horklump")) == DiscoveryTier.KNOWN,
                    () -> "setup: the page did not reach KNOWN");
            int afterKnown = SkillSystemAPI.getSkillData(wizard).getSkillPoints();
            WizardTestSupport.check(helper, afterKnown == afterXp + 1,
                    () -> "a page reaching KNOWN paid " + (afterKnown - afterXp) + " skill points, not 1");
            BestiaryDiscoveryHandler.witnessedSignature(wizard, horklump);
            int again = SkillSystemAPI.getSkillData(wizard).getSkillPoints();
            WizardTestSupport.check(helper, again == afterKnown, () -> "the same page paid twice");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wizard);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
