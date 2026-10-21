package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.ability.AbilityIds;
import at.koopro.wizardsandbeasts.ability.grant.PlayerStatusAbilityGrantSource;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.elf.ElfMagic;
import at.koopro.wizardsandbeasts.heritage.elf.HouseElfBond;
import at.koopro.wizardsandbeasts.heritage.identity.HeritagePractice;
import at.koopro.wizardsandbeasts.heritage.identity.HeritageProfile;
import at.koopro.wizardsandbeasts.heritage.identity.HeritageProfiles;
import at.koopro.wizardsandbeasts.heritage.identity.PracticeKind;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.law.CodeOfWandUse;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.NullMarked;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * The house-elf, end to end — the reference heritage (documentation/HERITAGE_IDENTITY_STATUS.md §House-Elf): service
 * pledged and accepted between two players, the summons, the hand that cannot strike its master, freedom by a
 * garment, wandless magic, the Code of Wand Use, and the profile the Character Sheet reads. Every scenario drives the
 * same server entry points the events and the ability wheel call.
 */
@NullMarked
final class HouseElfTests {

    private HouseElfTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("heritage_elf_pledge_and_accept",
                "heritage: a bound elf's pledge binds only when that player accepts; the master's wheel gains the call",
                HouseElfTests::pledgeAndAccept);
        tests.add("heritage_elf_cannot_harm_master",
                "heritage: a bound elf's blow never lands on its master and costs the elf instead; strangers are fair game",
                HouseElfTests::cannotHarmMaster);
        tests.add("heritage_elf_answers_the_call",
                "heritage: the master's call brings their elf to their side, counted as service and practice",
                HouseElfTests::answersTheCall);
        tests.add("heritage_elf_freed_by_a_garment",
                "heritage: a garment from the master's hand frees the elf for good; POWER untouched; a stranger's does nothing",
                HouseElfTests::freedByGarment);
        tests.add("heritage_elf_magic_snap_and_kitchen",
                "heritage: the snap disarms a mob but not the elf's master; the kitchen charm cooks food and only food",
                HouseElfTests::snapAndKitchen);
        tests.add("heritage_wand_code_for_non_humans",
                "ministry: a goblin carrying a wand in an official's sight is fined once a day; a wizard never is",
                HouseElfTests::wandCode);
        tests.add("heritage_profile_names_what_holds",
                "heritage: the profile lists the elf's service, freedom, wand law and practice; a wizard's lists none of it",
                HouseElfTests::profile);
    }

    // -- helpers ---------------------------------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper helper, String name, Heritage heritage, HeritageVariant variant) {
        return player(helper, name, heritage, variant, GameType.CREATIVE);
    }

    /** Survival for any scenario that measures a hit: a creative player is invulnerable before the damage event fires. */
    private static ServerPlayer player(GameTestHelper helper, String name, Heritage heritage, HeritageVariant variant,
                                       GameType mode) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, mode);
        WizardTestSupport.parkAtOrigin(helper, player);
        player.setNoGravity(true);
        HeritageAPI.commit(player, heritage, variant);
        return player;
    }

    private static ServerPlayer elf(GameTestHelper helper, String name) {
        return player(helper, name, Heritage.HOUSE_ELF, HeritageVariant.ELF_BOUND);
    }

    private static ServerPlayer wizard(GameTestHelper helper, String name) {
        return player(helper, name, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
    }

    private static boolean grants(ServerPlayer player, net.minecraft.resources.Identifier ability) {
        return new PlayerStatusAbilityGrantSource().grantsFor(player).contains(ability.toString());
    }

    private static boolean mentions(HeritageProfile profile, HeritageProfile.Section section, String key) {
        for (HeritageProfile.Line line : profile.section(section)) {
            if (line.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    // -- scenarios -------------------------------------------------------------------------------------

    private static void pledgeAndAccept(GameTestHelper helper) {
        ServerPlayer elf = elf(helper, "Kreacher");
        ServerPlayer master = wizard(helper, "Sirius");
        ServerPlayer stranger = wizard(helper, "Mundungus");
        try {
            check(helper, !HouseElfBond.accept(master, elf), () -> "a bond formed with no pledge at all");
            check(helper, HouseElfBond.pledge(elf, master), () -> "a bound elf could not pledge");
            check(helper, !HouseElfBond.accept(stranger, elf), () -> "a third player accepted a pledge made to another");
            check(helper, HouseElfBond.accept(master, elf), () -> "the pledged master could not accept");
            check(helper, HouseElfBond.serves(elf, master) && !HouseElfBond.serves(elf, stranger),
                    () -> "the bond does not name the right master");
            check(helper, !HouseElfBond.pledge(elf, stranger), () -> "an elf in service pledged to someone else");
            check(helper, grants(master, AbilityIds.CALL_HOUSE_ELF) && !grants(stranger, AbilityIds.CALL_HOUSE_ELF),
                    () -> "the call is on the wrong wheel");
            ServerPlayer elfMaster = elf(helper, "Winky");
            ServerPlayer hokey = elf(helper, "Hokey");
            boolean pledged = HouseElfBond.pledge(hokey, elfMaster);
            retire(helper, elfMaster);
            retire(helper, hokey);
            check(helper, !pledged, () -> "an elf pledged to serve another elf");
            helper.succeed();
        } finally {
            retire(helper, elf);
            retire(helper, master);
            retire(helper, stranger);
        }
    }

    private static void cannotHarmMaster(GameTestHelper helper) {
        ServerPlayer elf = player(helper, "Dobby", Heritage.HOUSE_ELF, HeritageVariant.ELF_BOUND, GameType.SURVIVAL);
        ServerPlayer master = player(helper, "Lucius", Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD, GameType.SURVIVAL);
        ServerPlayer stranger = player(helper, "Gilderoy", Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD,
                GameType.SURVIVAL);
        try {
            HouseElfBond.bind(elf, master);
            float masterBefore = master.getHealth();
            float elfBefore = elf.getHealth();
            master.hurtServer(helper.getLevel(), master.damageSources().playerAttack(elf), 4.0f);
            check(helper, master.getHealth() == masterBefore, () -> "the elf's blow landed on its master");
            check(helper, elf.getHealth() < elfBefore, () -> "the elf did not punish itself");
            stranger.invulnerableTime = 0;
            float strangerBefore = stranger.getHealth();
            stranger.hurtServer(helper.getLevel(), stranger.damageSources().playerAttack(elf), 4.0f);
            check(helper, stranger.getHealth() < strangerBefore, () -> "a bound elf could not strike a stranger");
            helper.succeed();
        } finally {
            retire(helper, elf);
            retire(helper, master);
            retire(helper, stranger);
        }
    }

    private static void answersTheCall(GameTestHelper helper) {
        ServerPlayer elf = elf(helper, "Kreacher2");
        ServerPlayer master = wizard(helper, "Regulus");
        try {
            HouseElfBond.bind(elf, master);
            elf.teleportTo(master.getX() + 12, master.getY(), master.getZ());
            int practiceBefore = HeritagePractice.of(elf).count(PracticeKind.ELF_SUMMONS);
            check(helper, HouseElfBond.call(master) == 1, () -> "the elf did not answer");
            check(helper, elf.distanceTo(master) < 3.0f, () -> "the elf arrived " + elf.distanceTo(master) + " away");
            check(helper, HouseElfBond.bondOf(elf).summons() == 1, () -> "the summons was not counted");
            check(helper, HeritagePractice.of(elf).count(PracticeKind.ELF_SUMMONS) == practiceBefore + 1,
                    () -> "answering did not count as practice");
            ServerPlayer nobody = wizard(helper, "NoElves");
            int called = HouseElfBond.call(nobody);
            retire(helper, nobody);
            check(helper, called == -1, () -> "a player with no elf called one");
            helper.succeed();
        } finally {
            retire(helper, elf);
            retire(helper, master);
        }
    }

    private static void freedByGarment(GameTestHelper helper) {
        ServerPlayer elf = elf(helper, "Dobby2");
        ServerPlayer master = wizard(helper, "Lucius2");
        ServerPlayer stranger = wizard(helper, "Harry");
        try {
            HouseElfBond.bind(elf, master);
            int power = PlayerStatsAPI.getStat(elf, PlayerStat.POWER);
            ItemStack sock = new ItemStack(Items.LEATHER_BOOTS);
            check(helper, !HouseElfBond.present(stranger, elf, sock.copy()),
                    () -> "a stranger's clothes freed someone else's elf");
            check(helper, !HouseElfBond.present(master, elf, new ItemStack(Items.BREAD)),
                    () -> "a loaf of bread freed an elf");
            check(helper, HouseElfBond.present(master, elf, sock), () -> "the master's garment did not free the elf");
            check(helper, sock.isEmpty(), () -> "the garment never left the master's hand");
            check(helper, HeritageAPI.getData(elf).getSelectedHeritageVariant() == HeritageVariant.ELF_FREE,
                    () -> "the elf is still of a serving lineage");
            check(helper, !HouseElfBond.bondOf(elf).bound(), () -> "the bond survived freedom");
            check(helper, PlayerStatsAPI.getStat(elf, PlayerStat.POWER) == power, () -> "freedom re-rolled POWER");
            check(helper, !HouseElfBond.pledge(elf, stranger), () -> "a free elf was bound again");
            check(helper, !grants(master, AbilityIds.CALL_HOUSE_ELF), () -> "the former master can still call");
            check(helper, at.koopro.wizardsandbeasts.heritage.identity.HeritageNodeGates.open("elf_free",
                    HeritageAPI.getData(elf)), () -> "a free elf may not learn the Free Elf node");
            helper.succeed();
        } finally {
            retire(helper, elf);
            retire(helper, master);
            retire(helper, stranger);
        }
    }

    private static void snapAndKitchen(GameTestHelper helper) {
        ServerPlayer elf = elf(helper, "HokeyElf");
        ServerPlayer master = wizard(helper, "Hepzibah");
        try {
            HouseElfBond.bind(elf, master);
            check(helper, grants(elf, AbilityIds.ELF_SNAP) && grants(elf, AbilityIds.ELF_KITCHEN),
                    () -> "elf magic is not on the elf's wheel");
            check(helper, !grants(master, AbilityIds.ELF_SNAP), () -> "a wizard got elf magic");

            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 1));
            zombie.setNoAi(true);
            zombie.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            check(helper, ElfMagic.snapAt(elf, zombie, helper.getLevel()), () -> "the snap did nothing");
            check(helper, zombie.getMainHandItem().isEmpty(), () -> "the zombie kept its sword");

            master.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            check(helper, !ElfMagic.snapAt(elf, master, helper.getLevel()), () -> "an elf snapped at its master");
            check(helper, !master.getMainHandItem().isEmpty(), () -> "the refused snap still disarmed the master");

            elf.getInventory().clearContent();
            elf.getInventory().add(new ItemStack(Items.BEEF, 3));
            elf.getInventory().add(new ItemStack(Items.RAW_IRON, 2));
            check(helper, ElfMagic.kitchen(elf) == 3, () -> "the kitchen charm cooked the wrong amount");
            check(helper, elf.getInventory().countItem(Items.COOKED_BEEF) == 3
                    && elf.getInventory().countItem(Items.BEEF) == 0, () -> "the beef was not cooked");
            check(helper, elf.getInventory().countItem(Items.RAW_IRON) == 2, () -> "the kitchen charm smelted ore");
            check(helper, ElfMagic.kitchen(elf) == 0, () -> "the charm cooked nothing twice");
            helper.succeed();
        } finally {
            retire(helper, elf);
            retire(helper, master);
        }
    }

    private static void wandCode(GameTestHelper helper) {
        Runnable ministry = WizardTestSupport.leaseModule(Module.MINISTRY);
        ServerPlayer goblin = player(helper, "Griphook", Heritage.GOBLIN, HeritageVariant.GOBLIN_COMMON);
        ServerPlayer wizard = wizard(helper, "Ollivander");
        try {
            Mob official = (Mob) ModEntities.GOBLIN_TELLER.get().spawn(helper.getLevel(),
                    helper.absolutePos(new BlockPos(2, 1, 2)), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            check(helper, official != null, () -> "no official to watch");
            official.setNoAi(true);
            check(helper, CodeOfWandUse.forbidden(goblin) && !CodeOfWandUse.forbidden(wizard),
                    () -> "the Code binds the wrong people");
            check(helper, !CodeOfWandUse.check(goblin), () -> "an empty-handed goblin was fined");
            ItemStack wand = WandItem.createWand(at.koopro.wizardsandbeasts.wand.stat.WandWood.HOLLY,
                    at.koopro.wizardsandbeasts.wand.stat.WandCore.PHOENIX_FEATHER,
                    at.koopro.wizardsandbeasts.wand.stat.WandLength.values()[0],
                    at.koopro.wizardsandbeasts.wand.stat.WandFlexibility.values()[0]);
            goblin.setItemInHand(InteractionHand.MAIN_HAND, wand.copy());
            wizard.setItemInHand(InteractionHand.MAIN_HAND, wand.copy());
            check(helper, CodeOfWandUse.check(goblin), () -> "a goblin with a wand in sight of an official was not seen");
            check(helper, MinistryRecords.get(goblin).offenceCount(MagicalOffence.NON_HUMAN_WAND) == 1,
                    () -> "the offence was not filed");
            check(helper, !CodeOfWandUse.check(goblin), () -> "the same wand was reported twice in one day");
            check(helper, !CodeOfWandUse.check(wizard), () -> "a wizard was fined for carrying a wand");
            official.discard();
            helper.succeed();
        } finally {
            retire(helper, goblin);
            retire(helper, wizard);
            ministry.run();
        }
    }

    private static void profile(GameTestHelper helper) {
        ServerPlayer elf = elf(helper, "Winky2");
        ServerPlayer master = wizard(helper, "Crouch");
        try {
            String k = "heritage.wizards_and_beasts.profile.";
            HeritageProfile unsworn = HeritageProfiles.build(elf);
            check(helper, mentions(unsworn, HeritageProfile.Section.SOCIAL, k + "elf.unsworn"),
                    () -> "an unsworn elf's profile does not say so");
            HouseElfBond.bind(elf, master);
            HeritageProfile bound = HeritageProfiles.build(elf);
            check(helper, mentions(bound, HeritageProfile.Section.SOCIAL, k + "elf.serves")
                    && mentions(bound, HeritageProfile.Section.DISADVANTAGES, k + "elf.no_harm")
                    && mentions(bound, HeritageProfile.Section.DISADVANTAGES, k + "wand_code")
                    && mentions(bound, HeritageProfile.Section.PROGRESSION, k + "practice"),
                    () -> "the bound elf's profile is missing service, its cost, the wand law or practice");
            check(helper, !bound.section(HeritageProfile.Section.ACTIVE).isEmpty(), () -> "no elf magic listed");
            HeritageProfile wizardProfile = HeritageProfiles.build(master);
            check(helper, !mentions(wizardProfile, HeritageProfile.Section.DISADVANTAGES, k + "wand_code")
                    && !mentions(wizardProfile, HeritageProfile.Section.PROGRESSION, k + "practice"),
                    () -> "a wizard's profile carries the elf's rules");
            check(helper, mentions(wizardProfile, HeritageProfile.Section.PROGRESSION, k + "points_from_spells"),
                    () -> "a wizard is not told where profession points come from");
            helper.succeed();
        } finally {
            retire(helper, elf);
            retire(helper, master);
        }
    }
}
