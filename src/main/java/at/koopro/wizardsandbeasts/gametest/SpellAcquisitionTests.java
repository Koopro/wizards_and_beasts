package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * How spells are acquired (Phase 7): the starter charm every wand hand knows.
 */
public final class SpellAcquisitionTests {

    private static final String LUMOS = "wizards_and_beasts:lumos";
    private static final String NOX = "wizards_and_beasts:nox";

    private SpellAcquisitionTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("spell_acquisition_starter_charm",
                "spells: a wizard knows Lumos and Nox without a book, kept out of the web ledger so a respec cannot "
                        + "take them; a goblin, who cannot use a wand, is taught nothing",
                SpellAcquisitionTests::starterCharm);
    }

    private static void starterCharm(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "FirstLight");
        ServerPlayer goblin = player(helper, "NoWandHand");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            HeritageAPI.commit(goblin, Heritage.GOBLIN, HeritageVariant.GOBLIN_COMMON);
            wizard.getData(ModAttachments.SPELL_DATA.get()).forgetSpell(LUMOS);
            wizard.getData(ModAttachments.SPELL_DATA.get()).forgetSpell(NOX);

            var taught = SpellLearningService.teachStarter(wizard);
            PlayerSpellData spells = wizard.getData(ModAttachments.SPELL_DATA.get());
            check(helper, spells.knowsSpell(LUMOS) && spells.knowsSpell(NOX),
                    () -> "the wizard was not taught the starter charms: " + taught);
            check(helper, !wizard.getData(ModAttachments.SKILL_DATA.get()).getWebTaughtSpells().contains(LUMOS),
                    () -> "the starter charm went into the web ledger, where a respec would take it back");
            check(helper, SpellLearningService.teachStarter(wizard).isEmpty(), () -> "the starter was taught twice");

            check(helper, SpellLearningService.teachStarter(goblin).isEmpty()
                            && !goblin.getData(ModAttachments.SPELL_DATA.get()).knowsSpell(LUMOS),
                    () -> "a goblin, who cannot use a wand, was taught Lumos");
            helper.succeed();
        } finally {
            retire(helper, wizard);
            retire(helper, goblin);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
