package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.creature.companion.CompanionCommand;
import at.koopro.wizardsandbeasts.creature.companion.CompanionCommands;
import at.koopro.wizardsandbeasts.creature.companion.CompanionData;
import at.koopro.wizardsandbeasts.creature.companion.CompanionMemory;
import at.koopro.wizardsandbeasts.creature.companion.CompanionProfile;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRoster;
import at.koopro.wizardsandbeasts.creature.companion.CompanionRules.Refusal;
import at.koopro.wizardsandbeasts.creature.companion.CompanionStage;
import at.koopro.wizardsandbeasts.creature.companion.Companions;
import at.koopro.wizardsandbeasts.creature.companion.Companions.Outcome;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.network.companion.CompanionRosterS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import org.jspecify.annotations.NullMarked;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * The companion layer end to end, on live creatures: claiming through the real feeding seam, protection through
 * the real damage event, commands and refusals, two species abilities, breeding in and out of habitat, the
 * entity save and the owner's roster across a relog and over the wire.
 *
 * <p>Each scenario keeps its creatures in a {@link CreatureArena} on a height of its own and moves its players up
 * beside them, so one scenario's niffler never fetches another's dropped items.
 */
@NullMarked
final class CompanionTests {

    private CompanionTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("companion_claim_by_feeding_and_strangers_refused",
                "companion: feeding claims a creature with a personality; a stranger's food is refused",
                CompanionTests::claimAndStranger);
        tests.add("companion_protection_follows_relation",
                "companion: a stranger and a trusted friend cannot hurt it; a marked enemy and the owner can",
                CompanionTests::protection);
        tests.add("companion_owned_creatures_never_despawn",
                "companion: the despawn rules leave an owned creature alone and a wild one to vanilla",
                CompanionTests::despawn);
        tests.add("companion_commands_obey_stage_species_and_temperament",
                "companion: only the owner commands; unknown, untrained and temperamental orders are refused",
                CompanionTests::commands);
        tests.add("companion_beast_masters_voice",
                "builds: the Beastmaster capstone moves its owner's creatures a stage on, and nobody else's",
                CompanionTests::beastMastersVoice);
        tests.add("companion_niffler_search_fetches_items_and_trains",
                "companion: a niffler sent to search brings loose items to its owner and earns training",
                CompanionTests::nifflerSearch);
        tests.add("companion_unicorn_assist_heals_then_rests",
                "companion: a unicorn told to assist heals its owner, then waits out its cooldown",
                CompanionTests::unicornAssist);
        tests.add("companion_breeding_in_habitat_inherits_lineage",
                "companion: two bowtruckles among logs raise a juvenile with their traits and a family tree",
                CompanionTests::breedsInHabitat);
        tests.add("companion_breeding_refused_outside_habitat",
                "companion: without wand-trees about, the pair loses the mood and raises nothing",
                CompanionTests::noBreedingOutsideHabitat);
        tests.add("companion_memory_survives_entity_save",
                "companion: personality, mode, training and lineage come back from the entity save",
                CompanionTests::entitySave);
        tests.add("companion_roster_survives_relog_and_syncs_once_per_tick",
                "companion: the roster is saved with the owner and many changes go out as one packet",
                CompanionTests::rosterRelogAndSync);
    }

    // -- helpers ---------------------------------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper helper, String name, CreatureArena arena) {
        return player(helper, name, UUID.randomUUID(), arena);
    }

    private static ServerPlayer player(GameTestHelper helper, String name, UUID id, CreatureArena arena) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, id, GameType.SURVIVAL);
        BlockPos at = arena.absolute(-1, 0);
        player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.setNoGravity(true);
        return player;
    }

    private static BondableBeast beast(PathfinderMob mob) {
        return (BondableBeast) mob;
    }

    /** Gives {@code owner} the creature at {@code bond}, through the bond layer's own claim, and fixes its traits. */
    private static BondableBeast own(PathfinderMob mob, ServerPlayer owner, int bond, List<String> traits) {
        BondableBeast beast = beast(mob);
        beast.increaseBond(owner, bond, false);
        setMemory(beast, Companions.memory(beast).withTraits(traits));
        Companions.afterChange(beast);
        return beast;
    }

    private static void setMemory(BondableBeast beast, CompanionMemory memory) {
        beast.bondState().setCompanion(memory);
    }

    private static void setTraining(BondableBeast beast, int training) {
        CompanionMemory memory = Companions.memory(beast);
        setMemory(beast, memory.withTraining(training, 0, Companions.day(beast)));
        Companions.afterChange(beast);
    }

    private static CompanionProfile profile(BondableBeast beast) {
        CompanionProfile profile = Companions.profile(beast);
        if (profile == null) {
            throw new IllegalStateException(beast.bondSpecies() + " has no companion profile");
        }
        return profile;
    }

    private static boolean hurt(ServerLevel level, PathfinderMob mob, ServerPlayer by) {
        mob.invulnerableTime = 0;
        float before = mob.getHealth();
        mob.hurtServer(level, level.damageSources().playerAttack(by), 1.0f);
        return mob.getHealth() < before;
    }

    // -- scenarios -------------------------------------------------------------------------------------

    private static void claimAndStranger(GameTestHelper helper) {
        Runnable lease = WizardTestSupport.leaseModule(Module.CREATURES);
        CreatureArena arena = CreatureArena.at(helper, 90);
        ServerPlayer owner = player(helper, "NifflerKeeper", arena);
        ServerPlayer stranger = player(helper, "NifflerThief", arena);
        try {
            BondableBeast niffler = beast(arena.creature("niffler", 1, 0));
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT));
            niffler.offerBondFood(owner, InteractionHand.MAIN_HAND);
            check(helper, owner.getUUID().equals(niffler.bondOwner()), () -> "feeding did not claim the niffler");
            CompanionMemory memory = Companions.memory(niffler);
            Set<String> pool = new HashSet<>();
            profile(niffler).traitPool().forEach(t -> pool.add(t.trait()));
            check(helper, memory.traits().size() == profile(niffler).traitsPerCreature()
                            && pool.containsAll(memory.traits()),
                    () -> "the claimed niffler rolled " + memory.traits() + " from " + pool);
            check(helper, Companions.stage(niffler) == CompanionStage.CURIOUS,
                    () -> "one gold ingot should make it curious, not " + Companions.stage(niffler));
            check(helper, owner.getData(ModAttachments.COMPANION_ROSTER.get()).entries()
                    .containsKey(niffler.bondMob().getUUID()), () -> "the claim never reached the owner's roster");

            int bond = niffler.bondLevel();
            stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
            niffler.offerBondFood(stranger, InteractionHand.MAIN_HAND);
            check(helper, niffler.bondLevel() == bond && owner.getUUID().equals(niffler.bondOwner()),
                    () -> "a stranger's diamond moved the bond to " + niffler.bondLevel());
            check(helper, stranger.getMainHandItem().getCount() == 1, () -> "the refused diamond was eaten anyway");
            check(helper, !Companions.mayHandle(niffler, stranger), () -> "a stranger may handle another's niffler");
            helper.succeed();
        } finally {
            arena.clear();
            retire(helper, owner);
            retire(helper, stranger);
            lease.run();
        }
    }

    private static void protection(GameTestHelper helper) {
        Runnable lease = WizardTestSupport.leaseModule(Module.CREATURES);
        CreatureArena arena = CreatureArena.at(helper, 100);
        ServerPlayer owner = player(helper, "UnicornWarden", arena);
        ServerPlayer stranger = player(helper, "Passerby", arena);
        ServerPlayer friend = player(helper, "TrustedFriend", arena);
        try {
            ServerLevel level = helper.getLevel();
            PathfinderMob mob = arena.creature("unicorn", 1, 0);
            BondableBeast unicorn = own(mob, owner, 40, List.of("loyal"));
            check(helper, !hurt(level, mob, stranger), () -> "a stranger hurt an owned unicorn");

            CompanionCommands.setRelation(owner, friend.getUUID(), friend.getGameProfile().name(), true, false);
            CompanionCommands.setRelation(owner, stranger.getUUID(), stranger.getGameProfile().name(), false, true);
            Companions.refreshTrust(unicorn);
            check(helper, !hurt(level, mob, friend), () -> "a trusted friend hurt the unicorn");
            check(helper, Companions.mayHandle(unicorn, friend), () -> "a trusted friend may not handle it");
            check(helper, hurt(level, mob, stranger), () -> "a player the owner marked hostile could not hurt it");
            check(helper, !Companions.mayHandle(unicorn, stranger), () -> "a marked enemy may handle it");

            int bond = unicorn.bondLevel();
            check(helper, hurt(level, mob, owner), () -> "the owner could not hurt their own unicorn");
            check(helper, unicorn.bondLevel() < bond, () -> "striking it cost the owner no bond");
            helper.succeed();
        } finally {
            arena.clear();
            retire(helper, owner);
            retire(helper, stranger);
            retire(helper, friend);
            lease.run();
        }
    }

    private static void despawn(GameTestHelper helper) {
        Runnable lease = WizardTestSupport.leaseModule(Module.CREATURES);
        CreatureArena arena = CreatureArena.at(helper, 110);
        ServerPlayer owner = player(helper, "FarAwayOwner", arena);
        try {
            PathfinderMob wild = arena.creature("niffler", 1, 0);
            PathfinderMob owned = arena.creature("niffler", 3, 0);
            own(owned, owner, 20, List.of("loyal"));
            MobDespawnEvent ownedEvent = NeoForge.EVENT_BUS.post(new MobDespawnEvent(owned, helper.getLevel()));
            MobDespawnEvent wildEvent = NeoForge.EVENT_BUS.post(new MobDespawnEvent(wild, helper.getLevel()));
            check(helper, ownedEvent.getResult() == MobDespawnEvent.Result.DENY,
                    () -> "an owned niffler may despawn: " + ownedEvent.getResult());
            check(helper, wildEvent.getResult() == MobDespawnEvent.Result.DEFAULT,
                    () -> "a wild niffler's despawn was decided by the companion layer: " + wildEvent.getResult());
            helper.succeed();
        } finally {
            arena.clear();
            retire(helper, owner);
            lease.run();
        }
    }

    private static void commands(GameTestHelper helper) {
        Runnable lease = WizardTestSupport.leaseModule(Module.CREATURES);
        CreatureArena arena = CreatureArena.at(helper, 120);
        ServerPlayer owner = player(helper, "BowtruckleTrainer", arena);
        ServerPlayer stranger = player(helper, "BowtruckleStranger", arena);
        try {
            PathfinderMob mob = arena.creature("bowtruckle", 1, 0);
            BondableBeast bowtruckle = own(mob, owner, 100, List.of("loyal"));
            check(helper, Companions.stage(bowtruckle) == CompanionStage.BONDED,
                    () -> "full bond and no training should be bonded, not " + Companions.stage(bowtruckle));

            check(helper, Companions.command(stranger, bowtruckle, CompanionCommand.STAY) == Outcome.NOT_OWNER,
                    () -> "a stranger's order was taken");
            check(helper, Companions.command(owner, bowtruckle, CompanionCommand.STAY) == Outcome.DONE,
                    () -> "the owner could not tell it to stay");
            CompanionMemory stayed = Companions.memory(bowtruckle);
            check(helper, stayed.modeOrFollow() == CompanionCommand.STAY && stayed.anchor().isPresent(),
                    () -> "staying left mode " + stayed.mode() + " and anchor " + stayed.anchor());

            check(helper, Companions.command(owner, bowtruckle, CompanionCommand.ASSIST) == Outcome.REFUSED
                            && Companions.refusal(bowtruckle, CompanionCommand.ASSIST) == Refusal.UNKNOWN_TO_SPECIES,
                    () -> "a bowtruckle took an order its species does not know");
            check(helper, Companions.refusal(bowtruckle, CompanionCommand.DEFEND) == Refusal.NOT_YET
                            && Companions.command(owner, bowtruckle, CompanionCommand.DEFEND) == Outcome.REFUSED,
                    () -> "an untrained bowtruckle took a defend order");

            setTraining(bowtruckle, profile(bowtruckle).trainedAt());
            check(helper, Companions.stage(bowtruckle) == CompanionStage.TRAINED,
                    () -> "enough training should make it trained, not " + Companions.stage(bowtruckle));
            check(helper, !bowtruckle.followsOwner(), () -> "a bowtruckle told to stay still follows");
            check(helper, Companions.command(owner, bowtruckle, CompanionCommand.DEFEND) == Outcome.DONE,
                    () -> "a trained bowtruckle refused to defend");
            check(helper, bowtruckle.followsOwner(), () -> "a defending bowtruckle stays behind its owner");

            setMemory(bowtruckle, Companions.memory(bowtruckle).withTraits(List.of("skittish")));
            check(helper, Companions.refusal(bowtruckle, CompanionCommand.DEFEND) == Refusal.TEMPERAMENT,
                    () -> "a skittish bowtruckle agreed to defend");
            CompanionRoster.Entry entry = owner.getData(ModAttachments.COMPANION_ROSTER.get()).entries()
                    .get(mob.getUUID());
            check(helper, entry != null && entry.commands().contains(CompanionCommand.PATROL)
                            && !entry.commands().contains(CompanionCommand.ASSIST),
                    () -> "the roster offers the wrong commands: " + (entry == null ? null : entry.commands()));
            helper.succeed();
        } finally {
            arena.clear();
            retire(helper, owner);
            retire(helper, stranger);
            lease.run();
        }
    }

    /**
     * The Beastmaster capstone on a live creature: read from the <em>owner</em>, so it moves the bonded bowtruckle a
     * stage on for its keeper and lends nothing to a stranger who holds it.
     */
    private static void beastMastersVoice(GameTestHelper helper) {
        Runnable lease = WizardTestSupport.leaseModule(Module.CREATURES);
        CreatureArena arena = CreatureArena.at(helper, 120);
        ServerPlayer owner = player(helper, "VoiceKeeper", arena);
        ServerPlayer stranger = player(helper, "VoiceStranger", arena);
        try {
            PathfinderMob mob = arena.creature("bowtruckle", 1, 0);
            BondableBeast bowtruckle = own(mob, owner, 100, List.of("skittish"));
            check(helper, Companions.refusal(bowtruckle, CompanionCommand.DEFEND) == Refusal.NOT_YET,
                    () -> "a bonded bowtruckle took a trained order before the capstone");

            at.koopro.wizardsandbeasts.skill.SkillSystemAPI.forceUnlock(stranger, Companions.BEAST_MASTERS_VOICE);
            check(helper, Companions.refusal(bowtruckle, CompanionCommand.DEFEND) == Refusal.NOT_YET,
                    () -> "a stranger's capstone moved someone else's creature");

            at.koopro.wizardsandbeasts.skill.SkillSystemAPI.forceUnlock(owner, Companions.BEAST_MASTERS_VOICE);
            check(helper, Companions.refusal(bowtruckle, CompanionCommand.DEFEND) == Refusal.OK,
                    () -> "the Beast-Master's Voice did not move a bonded, skittish bowtruckle to defend: "
                            + Companions.refusal(bowtruckle, CompanionCommand.DEFEND));
            check(helper, Companions.command(owner, bowtruckle, CompanionCommand.DEFEND) == Outcome.DONE,
                    () -> "the order itself was refused");
            check(helper, Companions.refusal(bowtruckle, CompanionCommand.ASSIST) == Refusal.UNKNOWN_TO_SPECIES,
                    () -> "the voice taught a bowtruckle an order its species does not know");
            helper.succeed();
        } finally {
            arena.clear();
            retire(helper, owner);
            retire(helper, stranger);
            lease.run();
        }
    }

    private static void nifflerSearch(GameTestHelper helper) {
        Runnable lease = WizardTestSupport.leaseModule(Module.CREATURES);
        CreatureArena arena = CreatureArena.at(helper, 130);
        ServerPlayer owner = player(helper, "NifflerSeeker", arena);
        try {
            ServerLevel level = helper.getLevel();
            BondableBeast niffler = own(arena.creature("niffler", 1, 0), owner, 100, List.of("loyal"));
            BlockPos drop = arena.absolute(6, 2);
            ItemEntity item = new ItemEntity(level, drop.getX() + 0.5, drop.getY(), drop.getZ() + 0.5,
                    new ItemStack(Items.GOLD_NUGGET, 3));
            item.setNoGravity(true);
            item.setPickUpDelay(40);
            level.addFreshEntity(item);

            check(helper, Companions.command(owner, niffler, CompanionCommand.SEARCH) == Outcome.DONE,
                    () -> "a bonded niffler would not search");
            check(helper, item.distanceToSqr(owner) < 2.0 * 2.0 || owner.getInventory().countItem(Items.GOLD_NUGGET) == 3,
                    () -> "the nuggets stayed " + Math.sqrt(item.distanceToSqr(owner)) + " blocks from the owner");
            check(helper, Companions.memory(niffler).training() > 0, () -> "a carried-out search earned no training");
            check(helper, Companions.command(owner, niffler, CompanionCommand.SEARCH) == Outcome.ON_COOLDOWN,
                    () -> "a second search straight away was not refused as tired");
            item.discard();
            helper.succeed();
        } finally {
            arena.clear();
            retire(helper, owner);
            lease.run();
        }
    }

    private static void unicornAssist(GameTestHelper helper) {
        Runnable lease = WizardTestSupport.leaseModule(Module.CREATURES);
        CreatureArena arena = CreatureArena.at(helper, 140);
        ServerPlayer owner = player(helper, "UnicornFriend", arena);
        try {
            BondableBeast unicorn = own(arena.creature("unicorn", 1, 0), owner, 100, List.of("loyal"));
            check(helper, Companions.command(owner, unicorn, CompanionCommand.ASSIST) == Outcome.NOTHING_FOUND,
                    () -> "a unicorn healed an owner who was not hurt");
            owner.setHealth(8.0f);
            check(helper, Companions.command(owner, unicorn, CompanionCommand.ASSIST) == Outcome.DONE,
                    () -> "a bonded unicorn would not assist a hurt owner");
            check(helper, owner.getHealth() >= 12.0f, () -> "the unicorn's touch healed to " + owner.getHealth());
            check(helper, Companions.command(owner, unicorn, CompanionCommand.ASSIST) == Outcome.ON_COOLDOWN,
                    () -> "the unicorn healed twice without rest");
            helper.succeed();
        } finally {
            arena.clear();
            retire(helper, owner);
            lease.run();
        }
    }

    /** Two owned, mature bowtruckles side by side and in the mood; {@code logs} lays a floor of them. */
    private static List<BondableBeast> pair(GameTestHelper helper, CreatureArena arena, ServerPlayer owner, boolean logs) {
        if (logs) {
            for (int x = -1; x <= 3; x++) {
                for (int z = -1; z <= 1; z++) {
                    helper.getLevel().setBlockAndUpdate(arena.absolute(x, z).below(), Blocks.OAK_LOG.defaultBlockState());
                }
            }
        }
        BondableBeast mother = own(arena.creature("bowtruckle", 0, 0), owner, 100, List.of("loyal", "curious"));
        BondableBeast father = own(arena.creature("bowtruckle", 2, 0), owner, 100, List.of("territorial", "skittish"));
        mother.bondState().setLoveTicks(600);
        father.bondState().setLoveTicks(600);
        return List.of(mother, father);
    }

    private static List<PathfinderMob> juveniles(GameTestHelper helper, CreatureArena arena) {
        BlockPos centre = arena.absolute(1, 0);
        return helper.getLevel().getEntitiesOfClass(PathfinderMob.class,
                new net.minecraft.world.phys.AABB(centre).inflate(8.0),
                mob -> mob instanceof BondableBeast beast && beast.isJuvenileBeast());
    }

    private static void breedsInHabitat(GameTestHelper helper) {
        Runnable lease = WizardTestSupport.leaseModule(Module.CREATURES);
        CreatureArena arena = CreatureArena.at(helper, 150);
        ServerPlayer owner = player(helper, "GroveKeeper", arena);
        List<BondableBeast> parents = pair(helper, arena, owner, true);
        Set<String> parentTraits = new HashSet<>();
        parents.forEach(p -> parentTraits.addAll(Companions.memory(p).traits()));
        Runnable cleanup = () -> {
            juveniles(helper, arena).forEach(PathfinderMob::discard);
            arena.clear();
            retire(helper, owner);
            lease.run();
        };
        helper.succeedWhen(() -> {
            List<PathfinderMob> young = juveniles(helper, arena);
            if (young.isEmpty()) {
                helper.fail("no juvenile yet");
            }
            BondableBeast baby = (BondableBeast) young.getFirst();
            CompanionMemory memory = Companions.memory(baby);
            try {
                check(helper, owner.getUUID().equals(baby.bondOwner()), () -> "the juvenile is not the keeper's");
                check(helper, memory.generation() == 1 && memory.parents().size() == 2 && memory.birthDay() >= 0,
                        () -> "the juvenile's lineage is gen " + memory.generation() + ", parents " + memory.parents());
                check(helper, memory.traits().size() == 2 && memory.traits().stream().anyMatch(parentTraits::contains),
                        () -> "the juvenile's traits " + memory.traits() + " owe nothing to " + parentTraits);
                check(helper, CompanionData.profile(baby.bondMob().getType()) != null, () -> "lost the companion profile");
                check(helper, parents.stream().allMatch(p -> p.bondState().loveTicks() == 0),
                        () -> "a parent is still in the mood after raising young");
            } finally {
                cleanup.run();
            }
        });
    }

    private static void noBreedingOutsideHabitat(GameTestHelper helper) {
        Runnable lease = WizardTestSupport.leaseModule(Module.CREATURES);
        CreatureArena arena = CreatureArena.at(helper, 160);
        ServerPlayer owner = player(helper, "TreelessKeeper", arena);
        List<BondableBeast> parents = pair(helper, arena, owner, false);
        helper.succeedWhen(() -> {
            if (parents.stream().anyMatch(p -> p.bondState().loveTicks() > 0)) {
                helper.fail("the pair is still in the mood");
            }
            try {
                check(helper, juveniles(helper, arena).isEmpty(), () -> "a pair with no wand-trees raised young");
            } finally {
                juveniles(helper, arena).forEach(PathfinderMob::discard);
                arena.clear();
                retire(helper, owner);
                lease.run();
            }
        });
    }

    private static void entitySave(GameTestHelper helper) {
        Runnable lease = WizardTestSupport.leaseModule(Module.CREATURES);
        CreatureArena arena = CreatureArena.at(helper, 170);
        ServerPlayer owner = player(helper, "SaveKeeper", arena);
        try {
            PathfinderMob mob = arena.creature("bowtruckle", 1, 0);
            BondableBeast bowtruckle = own(mob, owner, 100, List.of("loyal", "curious"));
            setTraining(bowtruckle, 40);
            Companions.command(owner, bowtruckle, CompanionCommand.STAY);
            setMemory(bowtruckle, Companions.memory(bowtruckle).withLineage(2, List.of("Twig", "Bark"), 3L));
            CompanionMemory before = Companions.memory(bowtruckle);

            PathfinderMob loaded = CreatureWildlifeTests.roundTrip(helper, mob);
            check(helper, loaded instanceof BondableBeast, () -> "the bowtruckle did not load");
            BondableBeast copy = (BondableBeast) loaded;
            CompanionMemory after = Companions.memory(copy);
            check(helper, after.equals(before), () -> "saved " + before + " but loaded " + after);
            check(helper, owner.getUUID().equals(copy.bondOwner()) && copy.bondLevel() == 100,
                    () -> "the owner or bond was lost in the save");
            loaded.discard();
            helper.succeed();
        } finally {
            arena.clear();
            retire(helper, owner);
            lease.run();
        }
    }

    private static void rosterRelogAndSync(GameTestHelper helper) {
        Runnable lease = WizardTestSupport.leaseModule(Module.CREATURES);
        CreatureArena arena = CreatureArena.at(helper, 180);
        UUID id = UUID.randomUUID();
        ServerPlayer first = player(helper, "RosterKeeper", id, arena);
        PathfinderMob mob = arena.creature("niffler", 1, 0);
        BondableBeast niffler = own(mob, first, 100, List.of("loyal"));
        retire(helper, first);

        ServerPlayer owner = player(helper, "RosterKeeper", id, arena);
        CompanionRoster.Entry saved = owner.getData(ModAttachments.COMPANION_ROSTER.get()).entries().get(mob.getUUID());
        try {
            check(helper, saved != null && saved.bond() == 100 && saved.traits().equals(List.of("loyal")),
                    () -> "the roster came back from the relog as " + saved);
        } catch (RuntimeException e) {
            arena.clear();
            retire(helper, owner);
            lease.run();
            throw e;
        }
        // Login sends the roster once; start counting from here.
        WizardTestSupport.drainClientboundPayloads(owner);
        Companions.command(owner, niffler, CompanionCommand.STAY);
        Companions.command(owner, niffler, CompanionCommand.REST);
        Companions.command(owner, niffler, CompanionCommand.FOLLOW);
        helper.runAfterDelay(2, () -> {
            try {
                List<CompanionRosterS2CPayload> sent = WizardTestSupport.drainClientboundPayloads(owner).stream()
                        .filter(CompanionRosterS2CPayload.class::isInstance)
                        .map(CompanionRosterS2CPayload.class::cast)
                        .toList();
                check(helper, sent.size() == 1, () -> "three orders in one tick sent " + sent.size() + " rosters");
                CompanionRoster.Entry entry = sent.getFirst().roster().entries().get(mob.getUUID());
                check(helper, entry != null && entry.mode() == CompanionCommand.FOLLOW,
                        () -> "the client was told mode " + (entry == null ? null : entry.mode()));
                helper.succeed();
            } finally {
                arena.clear();
                retire(helper, owner);
                lease.run();
            }
        });
    }
}
