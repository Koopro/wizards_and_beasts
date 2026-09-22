package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.form.sense.FormSense;
import at.koopro.wizardsandbeasts.form.sense.FormSenseService;
import at.koopro.wizardsandbeasts.form.sense.FormSenses;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

/**
 * A Merperson can breathe in the water it lives in.
 *
 * <p>Before this, {@code MerpeopleHeritageHandler} changed a submerged Merperson's shape and nothing
 * else: the {@code water_dwelling} trait was read by no code anywhere, so the lake people drowned in
 * lakes while any wizard with a sprig of Gillyweed did not. The grant now rides
 * {@link FormSense#WATER_BREATHING} off that trait, through the same {@link FormSenseService} that
 * already carries the werewolf's nose and the Animagus's night eyes.
 *
 * <p>What these scenarios are really protecting is the choice to key it to the <em>trait</em> rather
 * than to the merfolk form. Only the Selkie changes shape at all; a Merrow and a Siren are aquatic
 * standing still, so a form-gated grant would have looked correct in a test that only ever submerged
 * a Selkie and left the other two lineages exactly as broken as before.
 */
public final class MerpeopleBreathTests {

    private MerpeopleBreathTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("merpeople_breathe_water_without_changing_shape",
                "merpeople: a Merrow breathes underwater standing still, no transformation involved",
                MerpeopleBreathTests::merrowBreathesUnchanged);
        tests.add("merpeople_breath_is_not_handed_to_wizards",
                "merpeople: the water-breathing grant reaches Merpeople and nobody else",
                MerpeopleBreathTests::wizardStillDrowns);
        tests.add("merpeople_breath_survives_a_relog",
                "merpeople: the grant is re-applied by the login path, not only by the slow sweep",
                MerpeopleBreathTests::breathSurvivesResync);
    }

    // ── scenarios ───────────────────────────────────────────────────────────────────────────────

    /**
     * The Merrow never transforms, which is exactly the case a form-gated grant would have missed.
     */
    private static void merrowBreathesUnchanged(GameTestHelper helper) {
        ServerPlayer merrow = player(helper, "Merrow");
        try {
            HeritageAPI.commit(merrow, Heritage.MERPEOPLE, HeritageVariant.MERPEOPLE_MERROW);

            WizardTestSupport.check(helper,
                    FormSenses.of(merrow).contains(FormSense.WATER_BREATHING),
                    () -> "a Merrow was not granted water breathing: " + FormSenses.of(merrow));

            FormSenseService.apply(merrow, FormSenses.of(merrow));
            WizardTestSupport.check(helper, merrow.hasEffect(MobEffects.WATER_BREATHING),
                    () -> "the sense resolved but no water breathing landed on the Merrow");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, merrow);
        }
    }

    /**
     * The trait is the whole gate, so the obvious way to get this wrong is to grant it to everyone.
     * Also checks the revert path: a wizard who somehow held the effect has it taken back off.
     */
    private static void wizardStillDrowns(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "DryWizard");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);

            WizardTestSupport.check(helper,
                    !FormSenses.of(wizard).contains(FormSense.WATER_BREATHING),
                    () -> "a half-blood wizard was handed gills: " + FormSenses.of(wizard));

            // Hand it over by force, then let the service settle: an empty sense set is the revert
            // path, and a stale grant left behind is the other half of this bug.
            FormSenseService.apply(wizard, java.util.Set.of(FormSense.WATER_BREATHING));
            WizardTestSupport.check(helper, wizard.hasEffect(MobEffects.WATER_BREATHING),
                    () -> "the forced grant did not land, so the revert below proves nothing");

            FormSenseService.apply(wizard, FormSenses.of(wizard));
            WizardTestSupport.check(helper, !wizard.hasEffect(MobEffects.WATER_BREATHING),
                    () -> "water breathing outlived the sense that granted it");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wizard);
        }
    }

    /**
     * Login, respawn and a dimension change all run through {@code PlayerStateSyncService}, which is
     * why the grant is applied there as well as on the periodic sweep. A sense you merely see with
     * can wait a hundred ticks for the sweep; the one you breathe with cannot.
     */
    private static void breathSurvivesResync(GameTestHelper helper) {
        ServerPlayer selkie = player(helper, "Selkie");
        try {
            HeritageAPI.commit(selkie, Heritage.MERPEOPLE, HeritageVariant.MERPEOPLE_SELKIE);

            // Strip everything the way a fresh player entity arrives, then run the login path.
            FormSenseService.clear(selkie);
            WizardTestSupport.check(helper, !selkie.hasEffect(MobEffects.WATER_BREATHING),
                    () -> "the clear did not take, so the re-apply below proves nothing");

            at.koopro.wizardsandbeasts.sync.PlayerStateSyncService.syncFullLoginState(selkie, false);
            WizardTestSupport.check(helper, selkie.hasEffect(MobEffects.WATER_BREATHING),
                    () -> "a Selkie came back from the login path without gills");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, selkie);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.CREATIVE);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
