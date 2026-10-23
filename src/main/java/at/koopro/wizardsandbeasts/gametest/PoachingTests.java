package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.ability.PlayerAbilityHelper;
import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.wildlife.CreatureSign;
import at.koopro.wizardsandbeasts.creature.wildlife.SlayerAtonement;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.Poaching;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * Consequences for harming protected creatures (Phase 6, slice 5): poaching is filed when an official sees it, a
 * slayer cannot glean the hair of the species they slew, and the deep trust of another protected creature lifts the
 * mark.
 */
public final class PoachingTests {

    private PoachingTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("poaching_is_filed_when_an_official_sees_it",
                "ministry: killing a unicorn out of an official's sight files nothing; in sight it is poaching; a "
                        + "ghoul is not a protected species",
                PoachingTests::filedWhenSeen);
        tests.add("poaching_slayer_atones_through_trust",
                "creatures: a unicorn slayer cannot pick up shed unicorn hair; once a thestral trusts them at bond 80 "
                        + "the mark is lifted and they can",
                PoachingTests::slayerAtones);
    }

    private static void filedWhenSeen(GameTestHelper helper) {
        Runnable ministry = WizardTestSupport.leaseModule(Module.MINISTRY);
        ServerPlayer hunter = player(helper, "UnicornHunter");
        Mob official = null;
        try {
            Mob unicorn = (Mob) creature("unicorn").spawn(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 3)),
                    EntitySpawnReason.COMMAND);
            Mob ghoul = (Mob) creature("ghoul").spawn(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)),
                    EntitySpawnReason.COMMAND);
            check(helper, unicorn != null && ghoul != null, () -> "setup: creatures did not spawn");
            unicorn.setNoAi(true);
            ghoul.setNoAi(true);
            check(helper, Poaching.isProtected(unicorn) && !Poaching.isProtected(ghoul),
                    () -> "the protected-species tag is wrong");
            check(helper, !Poaching.check(hunter, unicorn), () -> "poaching was filed with nobody watching");
            check(helper, MinistryRecords.get(hunter).offenceCount(MagicalOffence.POACHING) == 0,
                    () -> "an unseen killing reached the file");

            official = (Mob) ModEntities.GOBLIN_TELLER.get().spawn(helper.getLevel(),
                    helper.absolutePos(new BlockPos(2, 1, 2)), EntitySpawnReason.COMMAND);
            check(helper, official != null, () -> "setup: no official to watch");
            official.setNoAi(true);
            check(helper, Poaching.check(hunter, unicorn), () -> "an official watched a unicorn killed and saw nothing");
            check(helper, MinistryRecords.get(hunter).offenceCount(MagicalOffence.POACHING) == 1,
                    () -> "the poaching was not filed");
            unicorn.discard();
            ghoul.discard();
            helper.succeed();
        } finally {
            if (official != null) {
                official.discard();
            }
            retire(helper, hunter);
            ministry.run();
        }
    }

    private static void slayerAtones(GameTestHelper helper) {
        Runnable creatures = WizardTestSupport.leaseModule(Module.CREATURES);
        ServerPlayer slayer = player(helper, "PenitentSlayer");
        Item hair = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("wizards_and_beasts", "unicorn_hair"));
        try {
            slayer.getInventory().clearContent();
            PlayerAbilityHelper.addAbilityFlag(slayer, "unicorn_slayer");
            Mob unicorn = (Mob) creature("unicorn").spawn(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                    EntitySpawnReason.COMMAND);
            check(helper, unicorn != null, () -> "setup: no unicorn");
            unicorn.setNoAi(true);
            ItemEntity sign = CreatureSign.leave(unicorn, hair, 1);
            check(helper, sign != null, () -> "setup: the unicorn left no hair");
            sign.setNoPickUpDelay();
            sign.playerTouch(slayer);
            check(helper, slayer.getInventory().countItem(hair) == 0, () -> "a unicorn slayer gleaned unicorn hair");

            Mob thestral = (Mob) ModEntities.THESTRAL.get().spawn(helper.getLevel(),
                    helper.absolutePos(new BlockPos(3, 1, 1)), EntitySpawnReason.COMMAND);
            check(helper, thestral instanceof BondableBeast, () -> "setup: no bondable thestral");
            thestral.setNoAi(true);
            ((BondableBeast) thestral).increaseBond(slayer, SlayerAtonement.ATONING_BOND, false);
            check(helper, !PlayerAbilityHelper.hasAbilityFlag(slayer, "unicorn_slayer"),
                    () -> "a thestral's full trust did not lift the slayer's mark");
            sign.playerTouch(slayer);
            check(helper, slayer.getInventory().countItem(hair) >= 1, () -> "the atoned slayer still cannot take the hair");
            unicorn.discard();
            thestral.discard();
            helper.succeed();
        } finally {
            PlayerAbilityHelper.removeAbilityFlag(slayer, "unicorn_slayer");
            retire(helper, slayer);
            creatures.run();
        }
    }

    private static EntityType<?> creature(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("wizards_and_beasts", id));
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        player.setNoGravity(true);
        return player;
    }
}
