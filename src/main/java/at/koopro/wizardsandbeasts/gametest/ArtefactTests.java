package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.entity.azkaban.DementorAura;
import at.koopro.wizardsandbeasts.item.hallow.ShadesOfTheDead;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleTags;
import at.koopro.wizardsandbeasts.portkey.PortkeyService;
import at.koopro.wizardsandbeasts.registry.DarkArtefactItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import at.koopro.wizardsandbeasts.timeturner.TimeTurnerRules;
import at.koopro.wizardsandbeasts.timeturner.TimeTurnerService;
import at.koopro.wizardsandbeasts.timeturner.TimeTurnerTrail;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Iconic artefacts doing the one thing each is for (documentation/MAGICAL_ARTEFACT_STATUS.md): the Time-Turner takes
 * its wearer back and leaves the world's clock alone, a Portkey carries everyone holding on and is spent, the
 * Resurrection Stone's shades keep Dementors from feeling you, and non-Dark artefacts are no longer Dark Arts.
 */
public final class ArtefactTests {

    private ArtefactTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("artefact_time_turner_takes_the_wearer_back",
                "artefact: a Time-Turner takes its wearer back to where they stood an hour ago, leaves the world's "
                        + "clock alone, spends the hours it went through, and forgets them when set aside",
                ArtefactTests::timeTurnerTakesTheWearerBack);
        tests.add("artefact_portkey_carries_everyone_holding_on",
                "artefact: a Portkey carries its holder and whoever is touching them to where it was set, then is spent",
                ArtefactTests::portkeyCarriesEveryone);
        tests.add("artefact_resurrection_stone_shades_keep_dementors_off",
                "artefact: three turns of the Resurrection Stone call shades no Dementor can feel past; they leave "
                        + "when the Stone does",
                ArtefactTests::shadesKeepDementorsOff);
        tests.add("artefact_non_dark_artefacts_are_not_dark_arts",
                "artefact: the Pensieve, Two-Way Mirror, beaded bag and Foe-Glass are artefacts, not Dark artefacts",
                ArtefactTests::nonDarkArtefactsAreNotDarkArts);
    }

    private static void timeTurnerTakesTheWearerBack(GameTestHelper helper) {
        ServerPlayer wearer = player(helper, "TimeTurnerWearer", GameType.SURVIVAL);
        try {
            ServerLevel level = helper.getLevel();
            wearer.getInventory().add(new ItemStack(TrinketItemRegistry.TIME_TURNER.get()));
            long now = level.getGameTime();
            long dayTime = level.getDayTime();
            Vec3 then = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 1, 0)));
            TimeTurnerTrail trail = wearer.getData(ModAttachments.TIME_TURNER_TRAIL.get());
            trail.record(new TimeTurnerRules.Moment(now - TimeTurnerRules.TICKS_PER_TURN,
                    level.dimension().identifier().toString(), then.x, then.y, then.z));

            check(helper, TimeTurnerService.turnBack(wearer, 2) == TimeTurnerService.Result.NOT_LONG_ENOUGH,
                    () -> "two hours back through one hour of trail");
            TimeTurnerService.Result result = TimeTurnerService.turnBack(wearer, 1);
            check(helper, result == TimeTurnerService.Result.BACK, () -> "one turn did not go back: " + result);
            check(helper, wearer.position().distanceTo(then) < 0.01,
                    () -> "the wearer is at " + wearer.position() + ", not where they stood: " + then);
            check(helper, level.getDayTime() == dayTime,
                    () -> "the world's clock moved: " + dayTime + " -> " + level.getDayTime());
            check(helper, TimeTurnerService.turnBack(wearer, 1) == TimeTurnerService.Result.NOT_LONG_ENOUGH,
                    () -> "the same hour was relived twice");

            trail.record(new TimeTurnerRules.Moment(now, level.dimension().identifier().toString(),
                    then.x, then.y, then.z));
            wearer.getInventory().clearContent();
            TimeTurnerService.record(wearer);
            check(helper, trail.isEmpty(), () -> "the trail outlived the Time-Turner being set aside");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wearer);
        }
    }

    private static void portkeyCarriesEveryone(GameTestHelper helper) {
        ServerPlayer holder = player(helper, "PortkeyHolder", GameType.SURVIVAL);
        ServerPlayer friend = player(helper, "PortkeyFriend", GameType.SURVIVAL);
        ServerPlayer stranger = player(helper, "PortkeyStranger", GameType.SURVIVAL);
        try {
            BlockPos origin = helper.absolutePos(BlockPos.ZERO);
            friend.teleportTo(origin.getX() + 1.2, origin.getY() + 1, origin.getZ() + 0.5);
            stranger.teleportTo(origin.getX() + 6.5, origin.getY() + 1, origin.getZ() + 0.5);
            BlockPos set = helper.absolutePos(new BlockPos(4, 1, 5));
            ItemStack portkey = new ItemStack(TrinketItemRegistry.PORTKEY.get());
            PortkeyService.set(portkey, helper.getLevel(), set);
            holder.setItemInHand(InteractionHand.MAIN_HAND, portkey);

            PortkeyService.Result result = PortkeyService.travel(holder, holder.getMainHandItem());
            check(helper, result == PortkeyService.Result.TRAVELLED, () -> "the Portkey did not go: " + result);
            Vec3 arrival = Vec3.atBottomCenterOf(set.above());
            check(helper, holder.position().distanceTo(arrival) < 0.01, () -> "the holder did not arrive");
            check(helper, friend.position().distanceTo(arrival) < 0.01, () -> "the one holding on was left behind");
            check(helper, stranger.position().distanceTo(arrival) > 3.0, () -> "someone not touching it was taken");
            check(helper, holder.getMainHandItem().isEmpty(), () -> "the Portkey was not spent by its journey");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, holder);
            WizardTestSupport.retire(helper, friend);
            WizardTestSupport.retire(helper, stranger);
        }
    }

    private static void shadesKeepDementorsOff(GameTestHelper helper) {
        ServerPlayer mourner = player(helper, "StoneTurner", GameType.SURVIVAL);
        Runnable darkArts = WizardTestSupport.leaseModule(Module.DARK_ARTS); // the Stone is a Dark artefact, shipped off
        try {
            check(helper, DementorAura.canFeedOn(mourner), () -> "setup: a Dementor could not feel a survival player");
            mourner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DarkArtefactItemRegistry.RESURRECTION_STONE.get()));
            for (int turn = 0; turn < 3; turn++) {
                DarkArtefactItemRegistry.RESURRECTION_STONE.get().use(helper.getLevel(), mourner, InteractionHand.MAIN_HAND);
            }
            check(helper, ShadesOfTheDead.walkWith(mourner), () -> "three turns did not call the shades");
            check(helper, !DementorAura.canFeedOn(mourner), () -> "a Dementor could still feel someone walking with the dead");

            mourner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ShadesOfTheDead.update(mourner);
            check(helper, !ShadesOfTheDead.walkWith(mourner), () -> "the shades stayed after the Stone was let go");
            check(helper, DementorAura.canFeedOn(mourner), () -> "the cold did not return with the shades gone");
            helper.succeed();
        } finally {
            darkArts.run();
            WizardTestSupport.retire(helper, mourner);
        }
    }

    private static void nonDarkArtefactsAreNotDarkArts(GameTestHelper helper) {
        for (var item : java.util.List.of(TrinketItemRegistry.PENSIEVE.get(), TrinketItemRegistry.TWO_WAY_MIRROR.get(),
                TrinketItemRegistry.HERMIONES_BEADED_BAG.get(), TrinketItemRegistry.FOE_GLASS.get())) {
            ItemStack stack = new ItemStack(item);
            check(helper, stack.is(ModuleTags.items(Module.ARTEFACTS)) && !stack.is(ModuleTags.items(Module.DARK_ARTS)),
                    () -> item + " is still filed under the Dark Arts");
        }
        check(helper, new ItemStack(TrinketItemRegistry.HAND_OF_GLORY.get()).is(ModuleTags.items(Module.DARK_ARTS)),
                () -> "the Hand of Glory stopped being a Dark artefact");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name, GameType mode) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, mode);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
