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
        tests.add("harvest_kill_takes_parts_only_from_a_student",
                "creatures: a thestral killed by someone who never studied thestrals yields no tail hair; one who "
                        + "has studied them takes it, and then not again until the harvest cooldown has passed",
                HarvestRouteTests::killNeedsStudy);
    }

    private static void killNeedsStudy(GameTestHelper helper) {
        Runnable lease = WizardTestSupport.leaseModule(Module.CREATURES);
        CreatureArena arena = CreatureArena.at(helper, ARENA_Y + 10);
        ServerPlayer stranger = WizardTestSupport.placeMockPlayer(helper, "HairPoacher", GameType.SURVIVAL);
        ServerPlayer student = WizardTestSupport.placeMockPlayer(helper, "HairStudent", GameType.SURVIVAL);
        Item hair = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("wizards_and_beasts", "thestral_tail_hair"));
        try {
            for (BestiaryEntry entry : BestiaryDiscoveryHandler.entriesFor(
                    at.koopro.wizardsandbeasts.registry.ModEntities.THESTRAL.get())) {
                BestiaryDataHelper.setTier(student, entry.id(), at.koopro.wizardsandbeasts.bestiary.DiscoveryTier.STUDIED);
            }
            for (int i = 0; i < 6; i++) {
                check(helper, killFor(helper, arena, stranger, hair) == 0, () -> "a thestral gave its hair to a stranger");
            }
            int tries = 0;
            while (tries < 40 && killFor(helper, arena, student, hair) == 0) {
                tries++;
            }
            int taken = tries;
            check(helper, taken < 40, () -> "forty studied kills yielded no hair");
            for (int i = 0; i < 6; i++) {
                check(helper, killFor(helper, arena, student, hair) == 0,
                        () -> "a second hair came before the harvest cooldown");
            }
            helper.succeed();
        } finally {
            arena.clear();
            retire(helper, stranger);
            retire(helper, student);
            lease.run();
        }
    }

    /** Spawns a thestral, has {@code killer} strike it dead, and returns how much hair fell (picked up again). */
    private static int killFor(GameTestHelper helper, CreatureArena arena, ServerPlayer killer, Item hair) {
        net.minecraft.world.entity.PathfinderMob thestral = arena.creature("thestral", 2, 0);
        thestral.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(killer), 10000f);
        int found = 0;
        for (net.minecraft.world.entity.item.ItemEntity drop : helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, thestral.getBoundingBox().inflate(4))) {
            if (drop.getItem().is(hair)) {
                found += drop.getItem().getCount();
            }
            drop.discard();
        }
        return found;
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
