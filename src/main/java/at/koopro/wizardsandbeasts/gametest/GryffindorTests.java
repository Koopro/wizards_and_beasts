package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.artefact.gryffindor.GryffindorService;
import at.koopro.wizardsandbeasts.artefact.gryffindor.GryffindorSword;
import at.koopro.wizardsandbeasts.artefact.gryffindor.SwordOfGryffindorItem;
import at.koopro.wizardsandbeasts.item.darkartefact.HorcruxDestruction;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.registry.CanonItemRegistry;
import at.koopro.wizardsandbeasts.registry.DarkArtefactItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The Sword of Gryffindor (Phase 3c): the Sorting Hat gives it up to a wearer standing their ground in mortal danger,
 * once while it is in the world and again once it is destroyed; the blade that kills a basilisk takes in its venom
 * and then ends a Horcrux without being spent.
 */
public final class GryffindorTests {

    private GryffindorTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        // Alone: the sword is one per world, so no other scenario in the batch may present one meanwhile.
        tests.addAlone("artefact_gryffindor_hat_presents_once_and_again",
                "artefact: the Sorting Hat drops the sword to a wearer fighting a worthy foe at low health -- not "
                        + "without the Hat, not twice while it exists, and again once it is destroyed",
                GryffindorTests::presentsOnceAndAgain);
        tests.add("artefact_gryffindor_venom_ends_a_horcrux",
                "artefact: killing a basilisk imbues the sword; the imbued sword ends a Horcrux and is kept",
                GryffindorTests::venomEndsAHorcrux);
    }

    private static void presentsOnceAndAgain(GameTestHelper helper) {
        Runnable artefacts = WizardTestSupport.leaseModule(Module.ARTEFACTS);
        ServerPlayer wearer = WizardTestSupport.placeMockPlayer(helper, "HatWearer", GameType.SURVIVAL);
        Warden foe = null;
        ServerLevel level = helper.getLevel();
        try {
            WizardTestSupport.parkAtOrigin(helper, wearer);
            GryffindorSword.get(level).forget();
            foe = EntityType.WARDEN.create(level, EntitySpawnReason.COMMAND);
            check(helper, foe != null, () -> "setup: no warden");
            foe.setNoAi(true);
            foe.snapTo(wearer.getX() + 2, wearer.getY(), wearer.getZ(), 0, 0);
            level.addFreshEntity(foe);

            // Without the Hat: fighting and hurt, nothing falls.
            strikeAndBeStruck(level, wearer, foe);
            check(helper, swords(helper, wearer).isEmpty(), () -> "the sword came without the Hat");

            wearer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(CanonItemRegistry.SORTING_HAT.get()));
            wearer.setHealth(wearer.getMaxHealth());
            strikeAndBeStruck(level, wearer, foe);
            List<ItemEntity> first = swords(helper, wearer);
            check(helper, first.size() == 1, () -> "expected one sword from the Hat, found " + first.size());
            UUID id = first.get(0).getItem().get(ModDataComponents.GRYFFINDOR_SWORD_ID.get());
            check(helper, id != null && id.equals(GryffindorSword.get(level).present()),
                    () -> "the world does not hold the sword it was given: " + id);

            wearer.setHealth(wearer.getMaxHealth());
            strikeAndBeStruck(level, wearer, foe);
            check(helper, swords(helper, wearer).size() == 1, () -> "a second sword while the first is in the world");

            first.get(0).kill(level);
            check(helper, GryffindorSword.get(level).present() == null, () -> "a destroyed sword was not forgotten");
            wearer.setHealth(wearer.getMaxHealth());
            strikeAndBeStruck(level, wearer, foe);
            check(helper, swords(helper, wearer).size() == 1, () -> "the Hat did not present the sword again");
            helper.succeed();
        } finally {
            swords(helper, wearer).forEach(Entity::discard);
            GryffindorSword.get(level).forget();
            if (foe != null) {
                foe.discard();
            }
            WizardTestSupport.retire(helper, wearer);
            artefacts.run();
        }
    }

    private static void venomEndsAHorcrux(GameTestHelper helper) {
        Runnable darkArts = WizardTestSupport.leaseModule(Module.DARK_ARTS);
        ServerPlayer wielder = WizardTestSupport.placeMockPlayer(helper, "SwordWielder", GameType.SURVIVAL);
        try {
            WizardTestSupport.parkAtOrigin(helper, wielder);
            ServerLevel level = helper.getLevel();
            ItemStack sword = new ItemStack(CanonItemRegistry.SWORD_OF_GRYFFINDOR.get());
            check(helper, !sword.isDamageableItem(), () -> "goblin silver wears");
            wielder.setItemInHand(InteractionHand.MAIN_HAND, sword);
            ItemStack diary = new ItemStack(DarkArtefactItemRegistry.RIDDLES_DIARY.get());
            wielder.setItemInHand(InteractionHand.OFF_HAND, diary);

            CanonItemRegistry.SWORD_OF_GRYFFINDOR.get().use(level, wielder, InteractionHand.MAIN_HAND);
            check(helper, HorcruxDestruction.isIntact(wielder.getItemInHand(InteractionHand.OFF_HAND)),
                    () -> "a sword without venom ended a Horcrux");

            EntityType<?> basiliskType = BuiltInRegistries.ENTITY_TYPE.getValue(
                    Identifier.fromNamespaceAndPath("wizards_and_beasts", "basilisk"));
            Entity basilisk = basiliskType.create(level, EntitySpawnReason.COMMAND);
            check(helper, basilisk instanceof LivingEntity, () -> "setup: no basilisk");
            Entity zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            check(helper, !GryffindorService.killed(wielder, (LivingEntity) zombie, sword),
                    () -> "a zombie's death imbued the sword");
            check(helper, GryffindorService.killed(wielder, (LivingEntity) basilisk, sword),
                    () -> "a basilisk's death did not imbue the sword");
            check(helper, SwordOfGryffindorItem.imbued(wielder.getMainHandItem()), () -> "the venom did not stay");

            CanonItemRegistry.SWORD_OF_GRYFFINDOR.get().use(level, wielder, InteractionHand.MAIN_HAND);
            check(helper, !HorcruxDestruction.isIntact(wielder.getItemInHand(InteractionHand.OFF_HAND)),
                    () -> "the imbued sword did not end the Horcrux");
            check(helper, wielder.getMainHandItem().is(CanonItemRegistry.SWORD_OF_GRYFFINDOR.get()),
                    () -> "the sword was spent like a fang");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, wielder);
            darkArts.run();
        }
    }

    /** The wearer strikes the foe, then the foe strikes the wearer down to a quarter of their health. */
    private static void strikeAndBeStruck(ServerLevel level, ServerPlayer wearer, LivingEntity foe) {
        // Several exchanges land in one tick here; hurt-immunity frames would swallow all but the first.
        foe.invulnerableTime = 0;
        wearer.invulnerableTime = 0;
        foe.hurtServer(level, level.damageSources().playerAttack(wearer), 1.0f);
        wearer.setHealth(wearer.getMaxHealth() * 0.4f);
        wearer.hurtServer(level, level.damageSources().mobAttack(foe), wearer.getMaxHealth() * 0.15f);
    }

    private static List<ItemEntity> swords(GameTestHelper helper, ServerPlayer near) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(near.position()))
                .inflate(6), e -> e.isAlive() && e.getItem().is(CanonItemRegistry.SWORD_OF_GRYFFINDOR.get()));
    }
}
