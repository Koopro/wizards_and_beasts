package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellPractice;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellProficiencyTracker;
import at.koopro.wizardsandbeasts.spell.resistance.MagicResistance;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondHistory;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Integrations between systems that used to run side by side (documentation/SYSTEM_INTERACTION_MAP.md): one hide rule
 * for creatures and heritage, a wand that bonds through practice, and a creature bond that completes its bestiary
 * page and so pays the skill web.
 */
public final class SystemInteractionTests {

    private SystemInteractionTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("interaction_a_hide_needs_spells_at_once",
                "interaction: a troll's hide turns a lone Stunner aside and yields to two at once; an ordinary mob "
                        + "never resists; a spell of pure force never counts toward the hide",
                SystemInteractionTests::hideNeedsSpellsAtOnce);
        tests.add("interaction_giant_blood_is_a_hide",
                "interaction: a half-giant wizard's spell-resistant hide works like a troll's, a wizard's does not",
                SystemInteractionTests::giantBloodIsAHide);
        tests.add("interaction_the_wand_bonds_through_practice",
                "interaction: a wand deepens its bond while the spell is being practised and not from repetition "
                        + "past the day's practice",
                SystemInteractionTests::wandBondsThroughPractice);
        tests.add("interaction_a_mastered_bond_pays_the_web",
                "interaction: a bond deep enough to complete a species' page awards the page's skill point, once",
                SystemInteractionTests::masteredBondPaysTheWeb);
    }

    private static void hideNeedsSpellsAtOnce(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "HideWizard");
        try {
            Spell stupefy = Spells.byId("wizards_and_beasts:stupefy");
            Spell confringo = Spells.byId("wizards_and_beasts:confringo");
            GenericBeastEntity troll = beast(helper, "troll", new BlockPos(2, 1, 0));
            check(helper, MagicResistance.carriesEnchantment(stupefy), () -> "setup: Stupefy carries no enchantment");
            check(helper, !MagicResistance.carriesEnchantment(confringo),
                    () -> "Confringo is pure force, yet counts as an enchantment");

            check(helper, MagicResistance.takesHold(confringo, troll, wizard),
                    () -> "a blast was turned aside as though it were an enchantment");
            check(helper, !MagicResistance.takesHold(stupefy, troll, wizard),
                    () -> "one Stunner took hold on a troll");
            check(helper, MagicResistance.takesHold(stupefy, troll, wizard),
                    () -> "two Stunners at once did not take hold on a troll");
            check(helper, !MagicResistance.takesHold(stupefy, troll, wizard),
                    () -> "the count did not start again after the hide gave way");

            Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 1, 2));
            zombie.setNoAi(true);
            check(helper, MagicResistance.takesHold(stupefy, zombie, wizard),
                    () -> "an ordinary mob resisted a Stunner");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wizard);
        }
    }

    private static void giantBloodIsAHide(GameTestHelper helper) {
        ServerPlayer caster = player(helper, "HideCaster");
        ServerPlayer halfGiant = player(helper, "HalfGiant");
        ServerPlayer wizard = player(helper, "PlainWizard");
        try {
            Spell stupefy = Spells.byId("wizards_and_beasts:stupefy");
            HeritageAPI.commit(halfGiant, Heritage.GIANT, HeritageVariant.GIANT_HALF);
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            check(helper, !MagicResistance.takesHold(stupefy, halfGiant, caster),
                    () -> "one Stunner took hold on a half-giant");
            check(helper, MagicResistance.takesHold(stupefy, halfGiant, caster),
                    () -> "two Stunners at once did not take hold on a half-giant");
            check(helper, MagicResistance.takesHold(stupefy, wizard, caster),
                    () -> "a wizard without giant blood resisted a Stunner");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, caster);
            WizardTestSupport.retire(helper, halfGiant);
            WizardTestSupport.retire(helper, wizard);
        }
    }

    private static void wandBondsThroughPractice(GameTestHelper helper) {
        ServerPlayer master = player(helper, "PractisingMaster");
        try {
            HeritageAPI.commit(master, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            ItemStack wand = new ItemStack(WandItemRegistry.WAND.get());
            wand.set(WandComponents.WAND_WOOD.get(), Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "holly"));
            wand.set(WandComponents.WAND_CORE.get(), Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "veela_hair"));
            wand.set(WandComponents.WAND_FLEXIBILITY.get(), WandFlexibility.PLIANT);
            wand.set(WandComponents.WAND_LENGTH.get(), 12.5f);
            wand.set(WandComponents.WAND_INTEGRITY.get(), 1.0f);
            ModDataComponents.refreshElderWandMarker(wand);
            wand.set(WandComponents.WAND_MASTER.get(), Optional.of(master.getUUID()));
            wand.set(WandComponents.WAND_ALLEGIANCE_SCORE.get(), 0.5f);
            wand.set(WandComponents.WAND_BOND_HISTORY.get(), WandBondHistory.EMPTY.withFirstMasterIfAbsent(master.getUUID()));
            master.setItemInHand(InteractionHand.MAIN_HAND, wand);

            String lumos = Spells.byId("wizards_and_beasts:lumos").getId();
            for (int i = 0; i < SpellPractice.DAILY_PRACTICE; i++) {
                SpellProficiencyTracker.recordSuccessfulHit(master, lumos);
            }
            float practised = WandComponents.getAllegianceScore(master.getMainHandItem());
            check(helper, practised > 0.5f, () -> "a day's practice did not deepen the bond: " + practised);
            for (int i = 0; i < 20; i++) {
                SpellProficiencyTracker.recordSuccessfulHit(master, lumos);
            }
            float repeated = WandComponents.getAllegianceScore(master.getMainHandItem());
            check(helper, repeated == practised,
                    () -> "repetition past the day's practice still deepened the bond: " + practised + " -> " + repeated);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, master);
        }
    }

    private static void masteredBondPaysTheWeb(GameTestHelper helper) {
        ServerPlayer keeper = player(helper, "BondKeeper");
        try {
            HeritageAPI.commit(keeper, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            GenericBeastEntity hippogriff = beast(helper, "hippogriff", new BlockPos(2, 1, 2));
            Identifier page = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "hippogriff");
            int before = SkillSystemAPI.getSkillData(keeper).getSkillPoints();
            hippogriff.increaseBond(keeper, 100, true);
            check(helper, BestiaryDataHelper.getTier(keeper, page) == DiscoveryTier.KNOWN,
                    () -> "setup: a mastered bond did not complete the page");
            int after = SkillSystemAPI.getSkillData(keeper).getSkillPoints();
            check(helper, after == before + 1,
                    () -> "a bond that completed the page paid " + (after - before) + " skill points, not 1");
            BestiaryDataHelper.setTier(keeper, page, DiscoveryTier.KNOWN);
            int again = SkillSystemAPI.getSkillData(keeper).getSkillPoints();
            check(helper, again == after, () -> "the completed page paid a second time");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, keeper);
        }
    }

    @SuppressWarnings("unchecked")
    private static GenericBeastEntity beast(GameTestHelper helper, String id, BlockPos pos) {
        EntityType<GenericBeastEntity> type = (EntityType<GenericBeastEntity>) ModCreatures.ENTITIES.get(id).get();
        GenericBeastEntity beast = helper.spawn(type, pos);
        beast.setNoAi(true);
        return beast;
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
