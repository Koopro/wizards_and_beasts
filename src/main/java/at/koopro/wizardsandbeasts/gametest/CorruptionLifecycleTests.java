package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.corruption.DarkCorruptionService;
import at.koopro.wizardsandbeasts.corruption.UnforgivableToll;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModAttributes;
import at.koopro.wizardsandbeasts.standing.StandingAxis;
import at.koopro.wizardsandbeasts.standing.StandingService;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.GameType;

/**
 * Corruption is the one number on the character sheet that is meant never to come back down, which
 * makes it the one most likely to be quietly wrong.
 *
 * <p>The attachment is the source of truth and {@link ModAttributes#DARK_CORRUPTION} is the client's
 * copy of it, mirrored only by {@link DarkCorruptionService}. Every path that rebuilds a player's
 * state has to re-mirror, because a freshly spawned player entity starts the attribute at its default
 * while {@code copyOnDeath} carries the real value across — so the failure mode is not a lost value
 * but a sheet quietly disagreeing with the server, which nobody notices until they wonder why the
 * unicorns still run.
 */
public final class CorruptionLifecycleTests {

    private CorruptionLifecycleTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("corruption_survives_the_full_state_resync",
                "corruption: the login, respawn and dimension path leaves no stale sheet value",
                CorruptionLifecycleTests::survivesFullResync);
        tests.add("corruption_is_the_dark_pole_of_alignment",
                "corruption: alignment answers to it, and the light pole is what moves back",
                CorruptionLifecycleTests::alignmentAnswersToBothPoles);
    }

    // ── scenarios ───────────────────────────────────────────────────────────────────────────────

    /**
     * {@code syncFullLoginState} is the shared path behind login, respawn and a dimension change, so
     * this is all three at once. Mirrors the real failure: the attribute is reset the way a rebuilt
     * player entity resets it, and the resync has to put it back.
     */
    private static void survivesFullResync(GameTestHelper helper) {
        ServerPlayer caster = player(helper, "ResyncCaster");
        try {
            UnforgivableToll.charge(caster, 30.0f);
            float stored = DarkCorruptionService.get(caster);
            WizardTestSupport.check(helper, stored > 0.0f, () -> "the charge did not land");

            // The attribute starts at its default on a rebuilt player entity, while the attachment
            // carries across. Reproduce exactly that, then run the path the server runs.
            AttributeInstance mirror = caster.getAttribute(ModAttributes.DARK_CORRUPTION);
            WizardTestSupport.check(helper, mirror != null, () -> "no dark-corruption attribute attached");
            mirror.setBaseValue(0.0);

            PlayerStateSyncService.syncFullLoginState(caster, false);

            WizardTestSupport.check(helper, DarkCorruptionService.get(caster) == stored,
                    () -> "the stored value moved across a resync: " + stored
                            + " -> " + DarkCorruptionService.get(caster));
            WizardTestSupport.check(helper, (float) mirror.getBaseValue() == stored,
                    () -> "the character sheet is stale after a resync: attribute="
                            + mirror.getBaseValue() + " stored=" + stored);

            // And it is still the attachment that persists, not the attribute.
            WizardTestSupport.check(helper,
                    caster.getData(ModAttachments.DARK_CORRUPTION.get()) == stored,
                    () -> "the persisted attachment disagrees with the service");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, caster);
        }
    }

    /**
     * The model, asserted rather than described: corruption is the dark pole of the alignment axis and
     * the light pole is a separate, earnable number. A wizard does not wash corruption off — they
     * outgrow it, which is why {@code StandingService.adjust} refuses a negative alignment delta and
     * why the shipped deeds (Episkey, Protego, a Patronus) only ever push the light pole up.
     */
    private static void alignmentAnswersToBothPoles(GameTestHelper helper) {
        ServerPlayer caster = player(helper, "AlignmentCaster");
        try {
            float clean = StandingService.valueOf(caster, StandingAxis.ALIGNMENT);

            UnforgivableToll.charge(caster, 20.0f);
            float stained = StandingService.valueOf(caster, StandingAxis.ALIGNMENT);
            WizardTestSupport.check(helper, stained < clean,
                    () -> "corruption did not darken alignment: " + clean + " -> " + stained);

            // Protective and restorative magic move the other pole. This is the recovery route, and it
            // leaves the corruption itself exactly where it was.
            float corruptionBefore = DarkCorruptionService.get(caster);
            StandingService.adjust(caster, StandingAxis.ALIGNMENT, 10.0f);
            float recovered = StandingService.valueOf(caster, StandingAxis.ALIGNMENT);

            WizardTestSupport.check(helper, recovered > stained,
                    () -> "earning light did not move alignment back: " + stained + " -> " + recovered);
            WizardTestSupport.check(helper,
                    DarkCorruptionService.get(caster) == corruptionBefore,
                    () -> "recovering alignment washed the corruption off, which is the one thing"
                            + " it must never do: " + corruptionBefore + " -> "
                            + DarkCorruptionService.get(caster));

            // The axis refuses to be darkened from here: corruption owns that direction, and routing a
            // negative delta through standing would skip the vocation scaling its owner applies.
            StandingService.adjust(caster, StandingAxis.ALIGNMENT, -5.0f);
            WizardTestSupport.check(helper,
                    StandingService.valueOf(caster, StandingAxis.ALIGNMENT) == recovered,
                    () -> "standing accepted a darkening it should have refused");

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, caster);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.CREATIVE);
        WizardTestSupport.parkAtOrigin(helper, player);
        HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        return player;
    }
}
