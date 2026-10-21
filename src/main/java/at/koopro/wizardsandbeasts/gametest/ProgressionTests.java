package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.heritage.data.PlayerProfessionData;
import at.koopro.wizardsandbeasts.owl.OWLExaminationHandler;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.career.CareerProfile;
import at.koopro.wizardsandbeasts.owl.career.CareerWork;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
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
        tests.add("progression_owl_grades_what_was_practised",
                "progression: the O.W.L. grades practised spells, leaves unexamined subjects ungraded, "
                        + "and a resit waits a week",
                ProgressionTests::owlGradesPractice);
        tests.add("progression_career_is_paid_for_its_work",
                "progression: a career's wage reaches the vault once a working day, only for its own work, "
                        + "through the practice hook",
                ProgressionTests::careerPaysForWork);
        tests.add("progression_heritage_trade_pays_its_own_currency",
                "progression: a heritage profession pays the GameplayStat its trade names and no longer "
                        + "adds max health",
                ProgressionTests::heritageTradePays);
        tests.add("progression_wandlore_vocation_steadies_the_wand",
                "progression: declaring the Wandlore vocation takes misfire off a cast (it boosted an "
                        + "attribute no player carries)",
                ProgressionTests::wandloreSteadies);
    }

    private static void wandloreSteadies(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "WandloreWizard");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            at.koopro.wizardsandbeasts.spell.core.Spell lumos = Spells.byId("lumos");
            float before = misfireAfterVocation(wizard, lumos);
            wizard.setData(ModAttachments.VOCATION_DATA.get(), wizard.getData(ModAttachments.VOCATION_DATA.get())
                    .withPrimary(java.util.Optional.of(Identifier.fromNamespaceAndPath("wizards_and_beasts", "wandlore"))));
            float after = misfireAfterVocation(wizard, lumos);
            float expected = before - at.koopro.wizardsandbeasts.skill.vocation.VocationAbilityHooks.WAND_STEADINESS;
            WizardTestSupport.check(helper, Math.abs(after - expected) < 1e-4f,
                    () -> "Wandlore misfire " + before + " -> " + after + ", expected " + expected);
            helper.succeed();
        } finally {
            wizard.setData(ModAttachments.VOCATION_DATA.get(),
                    at.koopro.wizardsandbeasts.skill.vocation.PlayerVocationData.EMPTY);
            WizardTestSupport.retire(helper, wizard);
        }
    }

    /** A cast's misfire after the vocation hooks, from a 10% starting chance (the total clamps at 0). */
    private static float misfireAfterVocation(ServerPlayer wizard, at.koopro.wizardsandbeasts.spell.core.Spell spell) {
        at.koopro.wizardsandbeasts.spell.cast.ModifierStack stack = new at.koopro.wizardsandbeasts.spell.cast.ModifierStack();
        stack.addMisfireChance(0.10f, "test");
        at.koopro.wizardsandbeasts.skill.vocation.VocationAbilityHooks.applyCastModifiers(stack, wizard, spell);
        return stack.finalMisfireChance();
    }

    private static void heritageTradePays(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "TradeWizard");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            double health = wizard.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            wizard.getData(ModAttachments.HERITAGE_DATA.get()).addProfessionPoints(3);
            boolean bought = at.koopro.wizardsandbeasts.heritage.profession.ProfessionSystemAPI.tryUnlock(wizard, "wizard_apprentice")
                    && at.koopro.wizardsandbeasts.heritage.profession.ProfessionSystemAPI.tryUnlock(wizard, "wizard_wandmaker");
            WizardTestSupport.check(helper, bought, () -> "Apprentice and Wandmaker could not be bought with 3+ points");
            float grip = SkillSystemAPI.getGameplayBonus(wizard,
                    at.koopro.wizardsandbeasts.skill.GameplayStat.ALLEGIANCE_GRIP);
            WizardTestSupport.check(helper, grip >= 1.0f, () -> "a Wandmaker's allegiance grip read " + grip);
            double after = wizard.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            WizardTestSupport.check(helper, after == health,
                    () -> "a heritage trade changed max health " + health + " -> " + after);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wizard);
        }
    }

    private static void owlGradesPractice(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "OwlCandidate");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            wizard.setData(ModAttachments.OWL_DATA.get(), PlayerOWLData.DEFAULT);
            PlayerSpellData data = wizard.getData(ModAttachments.SPELL_DATA.get());
            // Five Utility charms mastered: 10 tier points, an E in Charms (E needs 9, OWLGradeRules.CHARMS).
            for (String id : new String[] {"lumos", "nox", "wingardium_leviosa", "reparo", "alohomora"}) {
                String spell = Spells.byId(id).getId();
                data.learnSpell(spell);
                data.setSuccessfulHits(spell, Proficiency.MASTERED.getCastsRequired());
            }
            OWLExaminationHandler.conductExam(wizard);
            PlayerOWLData owl = wizard.getData(ModAttachments.OWL_DATA.get());
            WizardTestSupport.check(helper, owl.grades().get(OWLSubject.CHARMS) == OWLGrade.E,
                    () -> "five mastered charms graded " + owl.grades().get(OWLSubject.CHARMS) + ", not E");
            WizardTestSupport.check(helper, !owl.grades().containsKey(OWLSubject.ARITHMANCY),
                    () -> "Arithmancy was graded though nothing in the game examines it");
            WizardTestSupport.check(helper, owl.sittings() == 1, () -> "sittings " + owl.sittings());
            long now = wizard.level().getGameTime();
            WizardTestSupport.check(helper, !OWLExaminationHandler.maySit(owl, now),
                    () -> "the exam could be resat the moment it was sat");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wizard);
        }
    }

    private static void careerPaysForWork(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "CareerWizard");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            wizard.setData(ModAttachments.PROFESSION_DATA.get(), PlayerProfessionData.chosen(Profession.AUROR, 1));
            PlayerVaultData vault = wizard.getData(ModAttachments.VAULT_DATA.get());
            long before = vault.getTotalInKnuts();
            int wage = CareerProfile.of(Profession.AUROR).dailyWage();

            boolean brewed = CareerWork.record(wizard, CareerWork.Kind.BREWING);
            WizardTestSupport.check(helper, !brewed && vault.getTotalInKnuts() == before,
                    () -> "an Auror was paid for brewing");

            // Duelling practice through the real seam: a counted Stupefy.
            String stupefy = Spells.byId("stupefy").getId();
            SpellProficiencyTracker.recordSuccessfulHit(wizard, stupefy);
            long afterWork = vault.getTotalInKnuts();
            WizardTestSupport.check(helper, afterWork == before + wage,
                    () -> "a day of duelling paid " + (afterWork - before) + " Knuts, not " + wage);

            SpellProficiencyTracker.recordSuccessfulHit(wizard, stupefy);
            CareerWork.record(wizard, CareerWork.Kind.DUELLING);
            WizardTestSupport.check(helper, vault.getTotalInKnuts() == afterWork,
                    () -> "the same day was paid twice");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wizard);
        }
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
            EntityType<GenericBeastEntity> type = ModCreatures.ENTITIES.get("horklump").get();
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
