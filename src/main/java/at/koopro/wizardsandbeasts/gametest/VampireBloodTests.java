package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionEnforcer;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicy;
import at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicyResolver;
import at.koopro.wizardsandbeasts.heritage.vampire.BloodSourceRules;
import at.koopro.wizardsandbeasts.heritage.vampire.ThirstStage;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodAPI;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodData;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;

/**
 * What being a blood-drinker actually does to a live player.
 *
 * <p>Game tests rather than unit tests for the reason {@code HeritageCommitTests} gives: every claim below
 * is about state only a real {@code ServerPlayer} has — an attachment, a {@code FoodData}, an entity in a
 * level next to them — and the whole point of this system is that four subsystems agree about one player.
 * A pure test of {@code VampireBloodData} would have passed happily while the food bar and the pool
 * disagreed, which is the failure the layer exists to prevent.
 *
 * <p>The mock player is <b>creative</b> ({@code WizardTestSupport.placeMockPlayer} overrides
 * {@code gameMode()}), and the drain tick exempts creative players on purpose, so nothing here asserts the
 * passive drain by waiting for it. That is not a gap being papered over: the drain is one multiplication,
 * and what is worth testing is everything it feeds — the mirror, the bands, and the feed.
 */
public final class VampireBloodTests {

    /** A lineage carrying {@code blood_hunger}. The tag is the source of truth, not the heritage enum. */
    private static final HeritageVariant VAMPIRE_TURNED = HeritageVariant.byId("turned");
    /** A lineage carrying no {@code blood_hunger}, to prove the policy is not "is this a person". */
    private static final HeritageVariant HALF_BLOOD = HeritageVariant.byId("half_blood");

    private VampireBloodTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("vampire_blood_policy_follows_the_lineage_tag",
                "vampire: committing a blood_hunger lineage seeds a full pool; clearing it takes the pool away",
                VampireBloodTests::policyFollowsTheLineageTag);
        tests.add("vampire_blood_food_gives_nothing",
                "vampire: a meal restores no food level and no saturation",
                VampireBloodTests::foodGivesNothing);
        tests.add("vampire_blood_feeding_fills_and_then_refuses",
                "vampire: biting a cow adds blood, and the same cow yields nothing twice",
                VampireBloodTests::feedingFillsAndThenRefuses);
        tests.add("vampire_blood_mirrors_the_hunger_bar",
                "vampire: the hidden food level tracks the pool and never reaches zero",
                VampireBloodTests::mirrorsTheHungerBar);
    }

    // -- scenarios ---------------------------------------------------------------------------------

    private static void policyFollowsTheLineageTag(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "PolicyVampire");
        try {
            WizardTestSupport.check(helper, VAMPIRE_TURNED != null && HALF_BLOOD != null,
                    () -> "the 'turned' or 'half_blood' lineage is gone");

            HeritageAPI.commit(player, Heritage.WIZARDKIND, HALF_BLOOD);
            WizardTestSupport.check(helper,
                    NutritionPolicyResolver.resolve(player) == NutritionPolicy.VANILLA,
                    () -> "a half-blood wizard resolved to something other than the vanilla hunger policy");

            HeritageAPI.commit(player, Heritage.VAMPIRE, VAMPIRE_TURNED);
            WizardTestSupport.check(helper,
                    NutritionPolicyResolver.resolve(player) == NutritionPolicy.BLOOD,
                    () -> "a blood_hunger lineage did not resolve to the blood policy");

            VampireBloodData data = VampireBloodAPI.getData(player);
            WizardTestSupport.check(helper, data.isSeeded(),
                    () -> "committing a vampire lineage left the pool unseeded, so a login would refill it");
            WizardTestSupport.check(helper, data.getBlood() == data.getMaxBlood(),
                    () -> "a freshly committed vampire did not start on a full pool: "
                            + data.getBlood() + "/" + data.getMaxBlood());
            WizardTestSupport.check(helper, data.getThirstStage() == ThirstStage.SATED,
                    () -> "a full pool did not read as SATED");

            // Clearing has to put the policy back, or a former vampire keeps a HUD with no pool behind it.
            HeritageAPI.clear(player, false);
            WizardTestSupport.check(helper,
                    NutritionPolicyResolver.resolve(player) == NutritionPolicy.VANILLA,
                    () -> "clearing the heritage left the player on the blood policy");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void foodGivesNothing(GameTestHelper helper) {
        ServerPlayer player = newVampire(helper, "HungryVampire");
        try {
            FoodData food = player.getFoodData();
            food.setFoodLevel(11);
            food.setSaturation(0f);

            // The two halves of a meal, in the order the events fire them.
            NutritionEnforcer.rememberBeforeMeal(player);
            food.eat(6, 0.6f);
            WizardTestSupport.check(helper, food.getFoodLevel() > 11,
                    () -> "the harness failed to feed the player at all, so the restore proves nothing");
            VampireBloodHandler.denyNutrition(player, true);

            WizardTestSupport.check(helper, food.getFoodLevel() == 11,
                    () -> "eating moved a vampire's food level to " + food.getFoodLevel() + ", expected 11");
            WizardTestSupport.check(helper, food.getSaturationLevel() == 0f,
                    () -> "eating left a vampire with saturation " + food.getSaturationLevel());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void feedingFillsAndThenRefuses(GameTestHelper helper) {
        ServerPlayer player = newVampire(helper, "FeedingVampire");
        try {
            ServerLevel level = helper.getLevel();
            VampireBloodAPI.setBlood(player, 10.0f);
            // The feed clock: a pool that was just seeded reads as "long since fed", but setBlood does not
            // touch it, so this is only making the precondition explicit rather than arranging it.
            VampireBloodAPI.getData(player).setTicksSinceLastFeed(10_000);

            LivingEntity cow = helper.spawn(EntityType.COW, BlockPos.ZERO);
            WizardTestSupport.check(helper, BloodSourceRules.hasBlood(cow),
                    () -> "a cow was judged to have no blood in it");

            float before = VampireBloodAPI.getBlood(player);
            boolean handled = VampireBloodHandler.attemptFeed(player, cow, level);
            WizardTestSupport.check(helper, handled,
                    () -> "the feed did not take the interaction, so vanilla would have handled the click");
            float after = VampireBloodAPI.getBlood(player);
            WizardTestSupport.check(helper, after > before,
                    () -> "feeding on a cow added no blood: " + before + " -> " + after);
            WizardTestSupport.check(helper,
                    BloodSourceRules.isRecentlyDrained(cow, level.getGameTime()),
                    () -> "the cow was not marked drained, so it could be farmed on the next click");

            // Immediately again: refused, and the pool must not move. Both the feed cooldown and the
            // drained window would each refuse this on their own, which is the point — the second bite is
            // unreachable by two independent rules.
            float held = VampireBloodAPI.getBlood(player);
            boolean refusedHandled = VampireBloodHandler.attemptFeed(player, cow, level);
            WizardTestSupport.check(helper, refusedHandled,
                    () -> "a refused feed passed the click through to vanilla instead of consuming it");
            WizardTestSupport.check(helper, VampireBloodAPI.getBlood(player) == held,
                    () -> "a refused feed still moved the pool");

            // And something bloodless is not this system's business at all: the click passes through.
            LivingEntity golem = helper.spawn(EntityType.IRON_GOLEM, BlockPos.ZERO);
            WizardTestSupport.check(helper, !BloodSourceRules.hasBlood(golem),
                    () -> "an iron golem was judged to have blood in it");
            WizardTestSupport.check(helper,
                    !VampireBloodHandler.attemptFeed(player, golem, level),
                    () -> "a bloodless target consumed the interaction instead of passing it through");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void mirrorsTheHungerBar(GameTestHelper helper) {
        ServerPlayer player = newVampire(helper, "MirrorVampire");
        try {
            FoodData food = player.getFoodData();

            NutritionEnforcer.driveVanillaHunger(player, NutritionPolicy.BLOOD, 1.0f);
            WizardTestSupport.check(helper, food.getFoodLevel() == 20,
                    () -> "a full pool mirrored to food level " + food.getFoodLevel() + ", expected 20");

            NutritionEnforcer.driveVanillaHunger(player, NutritionPolicy.BLOOD, 0.5f);
            WizardTestSupport.check(helper, food.getFoodLevel() == 10,
                    () -> "a half pool mirrored to food level " + food.getFoodLevel() + ", expected 10");

            // The load-bearing clamp. At food level 0 vanilla starts its own starvation timer, on top of
            // the one the blood system runs, and the player takes double damage from a single empty pool.
            NutritionEnforcer.driveVanillaHunger(player, NutritionPolicy.BLOOD, 0.0f);
            WizardTestSupport.check(helper, food.getFoodLevel() >= 1,
                    () -> "an empty pool mirrored to food level " + food.getFoodLevel()
                            + ", which hands starvation to vanilla as well");
            WizardTestSupport.check(helper, food.getSaturationLevel() == 0f,
                    () -> "the mirror left saturation at " + food.getSaturationLevel()
                            + ", which would give a vampire vanilla's fast regeneration");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    // -- fixtures ----------------------------------------------------------------------------------

    private static ServerPlayer newPlayer(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }

    private static ServerPlayer newVampire(GameTestHelper helper, String name) {
        ServerPlayer player = newPlayer(helper, name);
        WizardTestSupport.check(helper, VAMPIRE_TURNED != null, () -> "the 'turned' lineage is gone");
        HeritageAPI.commit(player, Heritage.VAMPIRE, VAMPIRE_TURNED);
        return player;
    }
}
