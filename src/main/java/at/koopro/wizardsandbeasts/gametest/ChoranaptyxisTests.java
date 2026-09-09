package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;

/**
 * The Occamy's choranaptyxis, against a real world.
 *
 * <p>Unit tests own the size <em>search</em> ({@code OccamyChoranaptyxisTest} asserts it without a level).
 * What only a running server can answer is the claim the whole feature rests on: that driving
 * {@code Attributes.SCALE} moves the creature's <b>bounding box</b>, not just its picture. The previous
 * implementation was a render-only float and its own javadoc admitted the hitbox never changed, so a
 * grown Occamy blocked nothing and reached no further. A test that only checked the number would have
 * passed just as happily against that.
 *
 * <p>Both scenarios use {@link WizardTestSupport#EMPTY_STRUCTURE} and build the two blocks they need,
 * because what is being measured is a body against geometry.
 */
public final class ChoranaptyxisTests {

    /** The declared body from {@code data/wizards_and_beasts/creatures/occamy.json}. */
    private static final float BODY_WIDTH = 1.7f;
    private static final float BODY_HEIGHT = 1.9f;

    /**
     * Long enough for the shrink to run: it re-decides every 4 ticks and moves {@code rate * 3} per
     * decision, so 1.0 down to about 0.5 is three decisions. Sixty ticks is generous.
     */
    private static final int SETTLE_TICKS = 60;

    private ChoranaptyxisTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("choranaptyxis_scale_moves_the_real_hitbox",
                "occamy: the size attribute resizes the bounding box, not only the model",
                ChoranaptyxisTests::scaleMovesTheRealHitbox);
        tests.add("choranaptyxis_a_low_ceiling_makes_it_shrink",
                "occamy: boxed under a low ceiling it shrinks to a size that fits",
                ChoranaptyxisTests::aLowCeilingMakesItShrink);
    }

    // -- scenarios ---------------------------------------------------------------------------------

    private static void scaleMovesTheRealHitbox(GameTestHelper helper) {
        GenericBeastEntity occamy = spawnOccamy(helper, 1, 1, 1);

        WizardTestSupport.check(helper, near(occamy.getBbWidth(), BODY_WIDTH),
                () -> "an unscaled Occamy is " + occamy.getBbWidth() + " wide, expected " + BODY_WIDTH);

        // Grown. Nothing calls refreshDimensions() here on purpose: vanilla's onAttributeUpdated does
        // it when SCALE changes, and if that ever stopped being true the feature would silently become
        // render-only again — which is exactly the bug this replaced.
        occamy.applySizeScale(2.0f);
        WizardTestSupport.check(helper, near(occamy.getScale(), 2.0f),
                () -> "asked for 2.0, the entity reports scale " + occamy.getScale());
        WizardTestSupport.check(helper, near(occamy.getBbWidth(), BODY_WIDTH * 2.0f),
                () -> "a doubled Occamy is " + occamy.getBbWidth() + " wide, expected " + (BODY_WIDTH * 2));
        WizardTestSupport.check(helper, near(occamy.getBbHeight(), BODY_HEIGHT * 2.0f),
                () -> "a doubled Occamy is " + occamy.getBbHeight() + " tall, expected " + (BODY_HEIGHT * 2));

        // Squeezed.
        occamy.applySizeScale(0.35f);
        WizardTestSupport.check(helper, occamy.getBbWidth() < 1.0f && occamy.getBbHeight() < 1.0f,
                () -> "at its minimum the Occamy is " + occamy.getBbWidth() + " x " + occamy.getBbHeight()
                        + "; it has to fit through a one-block hole");

        // And back, so the ability can always undo itself.
        occamy.applySizeScale(1.0f);
        WizardTestSupport.check(helper, near(occamy.getBbWidth(), BODY_WIDTH),
                () -> "returning to 1.0 left the Occamy " + occamy.getBbWidth() + " wide");
        WizardTestSupport.check(helper, near(occamy.getSizeScale(), 1.0f),
                () -> "the ability reads back " + occamy.getSizeScale() + " after being set to 1.0");

        occamy.discard();
        helper.succeed();
    }

    private static void aLowCeilingMakesItShrink(GameTestHelper helper) {
        WizardTestSupport.check(helper, ModuleManager.isEnabled(Module.CREATURES),
                () -> "Module.CREATURES is off, so no ability ticks and this scenario proves nothing");

        // A 2x2 floor with a ceiling one block above it: a gap a 1.9-tall body cannot stand in.
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            }
        }

        GenericBeastEntity occamy = spawnOccamy(helper, 1, 1, 1);
        WizardTestSupport.check(helper, near(occamy.getSizeScale(), 1.0f),
                () -> "the Occamy did not start at its natural size");

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            float scale = occamy.getSizeScale();
            WizardTestSupport.check(helper, scale < 1.0f,
                    () -> "boxed into a two-block gap the Occamy stayed at " + scale
                            + "; the fit probe never shrank it");
            WizardTestSupport.check(helper, occamy.getBbHeight() < BODY_HEIGHT,
                    () -> "the Occamy's box is still " + occamy.getBbHeight()
                            + " tall, so the scale moved but the hitbox did not");
            WizardTestSupport.check(helper, scale >= 0.35f,
                    () -> "the Occamy shrank to " + scale + ", past the floor its datapack declares");
            occamy.discard();
            helper.succeed();
        });
    }

    // -- helpers -----------------------------------------------------------------------------------

    private static GenericBeastEntity spawnOccamy(GameTestHelper helper, int x, int y, int z) {
        var holder = ModCreatures.ENTITIES.get("occamy");
        if (holder == null) {
            helper.fail("no Occamy entity type is registered; the creature manifest has changed");
        }
        EntityType<GenericBeastEntity> type = holder.get();
        return helper.spawn(type, new BlockPos(x, y, z));
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) < 1.0e-3f;
    }
}
