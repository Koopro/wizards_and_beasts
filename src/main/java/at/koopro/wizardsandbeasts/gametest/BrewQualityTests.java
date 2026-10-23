package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity;
import at.koopro.wizardsandbeasts.brew.BrewPotency;
import at.koopro.wizardsandbeasts.brew.BrewQuality;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.CauldronTier;
import at.koopro.wizardsandbeasts.item.brew.BrewItem;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.data.PlayerOWLData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Brew quality (Phase 5, slice 3): the brewer's skill goes into the pot as the brew starts and onto the bottle, and
 * the bottle -- not the drinker -- decides how long a drink lasts.
 */
public final class BrewQualityTests {

    private static final String PEPPERUP = "wizards_and_beasts:pepperup_potion";

    private BrewQualityTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("brew_quality_is_the_brewers",
                "brewing: a brewer with three levels of potion_potency and an Outstanding in Potions starts a "
                        + "batch at 130%; an untrained one at 85%",
                BrewQualityTests::qualityIsTheBrewers);
        tests.add("brew_quality_is_in_the_bottle",
                "brewing: a 130% Pepperup drunk by an untrained player lasts 30% longer than a plain one drunk by a "
                        + "master",
                BrewQualityTests::qualityIsInTheBottle);
    }

    private static void qualityIsTheBrewers(GameTestHelper helper) {
        ServerPlayer master = player(helper, "QualityMaster");
        ServerPlayer novice = player(helper, "QualityNovice");
        try {
            makeMaster(master);
            makeNovice(novice);
            CauldronBlockEntity good = pepperupPot(helper, new BlockPos(0, 1, 0));
            check(helper, good.startBrewing(CauldronTier.PEWTER, master) == CauldronBlockEntity.StartResult.STARTED,
                    () -> "setup: the master's Pepperup did not start");
            check(helper, good.quality() == BrewQuality.MAX, () -> "the master's batch is " + good.quality() + "%");
            CauldronBlockEntity thin = pepperupPot(helper, new BlockPos(3, 1, 0));
            check(helper, thin.startBrewing(CauldronTier.PEWTER, novice) == CauldronBlockEntity.StartResult.STARTED,
                    () -> "setup: the novice's Pepperup did not start");
            check(helper, thin.quality() == BrewQuality.MIN, () -> "the novice's batch is " + thin.quality() + "%");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, master);
            WizardTestSupport.retire(helper, novice);
        }
    }

    private static void qualityIsInTheBottle(GameTestHelper helper) {
        ServerPlayer novice = player(helper, "QualityDrinkerA");
        ServerPlayer master = player(helper, "QualityDrinkerB");
        try {
            makeNovice(novice);
            makeMaster(master);
            int fine = speedAfterDrinking(helper, novice,
                    BrewQuality.stamp(BrewItem.of(Brews.byId(PEPPERUP)), BrewQuality.MAX));
            int plain = speedAfterDrinking(helper, master, BrewItem.of(Brews.byId(PEPPERUP)));
            check(helper, plain > 0 && Math.abs(fine - Math.round(plain * 1.3f)) <= 2,
                    () -> "a 130% bottle gave " + fine + " ticks of speed, a plain one " + plain);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, novice);
            WizardTestSupport.retire(helper, master);
        }
    }

    // ── setup ───────────────────────────────────────────────────────────

    private static int speedAfterDrinking(GameTestHelper helper, ServerPlayer player, ItemStack bottle) {
        player.removeAllEffects();
        player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
        bottle.getItem().finishUsingItem(bottle, helper.getLevel(), player);
        MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
        return speed == null ? 0 : speed.getDuration();
    }

    private static void makeMaster(ServerPlayer player) {
        SkillSystemAPI.getSkillData(player).setSkillLevel(BrewPotency.SKILL_ID, 3);
        player.setData(ModAttachments.OWL_DATA.get(), PlayerOWLData.DEFAULT.withGrades(Map.of(OWLSubject.POTIONS, OWLGrade.O)));
    }

    private static void makeNovice(ServerPlayer player) {
        SkillSystemAPI.getSkillData(player).setSkillLevel(BrewPotency.SKILL_ID, 0);
        player.setData(ModAttachments.OWL_DATA.get(), PlayerOWLData.DEFAULT);
    }

    private static CauldronBlockEntity pepperupPot(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(at, ModBlocks.PEWTER_CAULDRON.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof CauldronBlockEntity pot)) {
            throw new IllegalStateException("no cauldron block entity at " + at);
        }
        pot.setFilled(true);
        pot.addIngredient(new ItemStack(Items.BLAZE_POWDER));
        pot.addIngredient(new ItemStack(Items.SUGAR));
        return pot;
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
