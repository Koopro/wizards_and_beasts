package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.module.Module;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * The non-lethal routes to a creature's materials (Phase 6, slice 1): a bonded creature's gift is handed to its
 * owner when they are near, and an owner who has finished studying the species waits half as long for the next.
 */
public final class HarvestRouteTests {

    private static final int ARENA_Y = 415;

    private HarvestRouteTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("harvest_gift_goes_to_a_researched_owner",
                "creatures: a bonded mooncalf's dung is handed to its owner standing by, and an owner whose research "
                        + "on mooncalves is complete gets the next one in half the time",
                HarvestRouteTests::giftToResearchedOwner);
    }

    private static void giftToResearchedOwner(GameTestHelper helper) {
        Runnable lease = WizardTestSupport.leaseModule(Module.CREATURES);
        CreatureArena arena = CreatureArena.at(helper, ARENA_Y);
        ServerPlayer owner = WizardTestSupport.placeMockPlayer(helper, "DungKeeper", GameType.SURVIVAL);
        BlockPos at = arena.absolute(-1, 0);
        owner.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        owner.setNoGravity(true);
        owner.getInventory().clearContent();
        BondableBeast mooncalf = (BondableBeast) arena.creature("mooncalf", 1, 0);
        mooncalf.increaseBond(owner, 60, false);
        mooncalf.bondState().setGiftCooldown(0);
        for (BestiaryEntry entry : BestiaryDiscoveryHandler.entriesFor(mooncalf.bondMob().getType())) {
            BestiaryDataHelper.markResearchComplete(owner, entry.id());
        }
        Item dung = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("wizards_and_beasts", "mooncalf_dung"));
        int fullWait = 600 * 20;

        helper.startSequence()
                .thenWaitUntil(() -> check(helper, owner.getInventory().countItem(dung) > 0,
                        () -> "no dung reached the owner; cooldown " + mooncalf.bondState().giftCooldown()))
                .thenExecute(() -> {
                    int wait = mooncalf.bondState().giftCooldown();
                    check(helper, wait > 0 && wait <= fullWait / 2,
                            () -> "a researched owner waits " + wait + " ticks, not half of " + fullWait);
                })
                .thenExecute(() -> {
                    arena.clear();
                    retire(helper, owner);
                    lease.run();
                })
                .thenSucceed();
    }
}
