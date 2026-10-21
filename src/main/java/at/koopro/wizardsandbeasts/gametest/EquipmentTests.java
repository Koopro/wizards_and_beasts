package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.demiguise.CloakCharges;
import at.koopro.wizardsandbeasts.equipment.EquipmentAPI;
import at.koopro.wizardsandbeasts.equipment.EquipmentMigration;
import at.koopro.wizardsandbeasts.equipment.EquipmentType;
import at.koopro.wizardsandbeasts.equipment.EquipmentVisibility;
import at.koopro.wizardsandbeasts.equipment.curios.CuriosEquipment;
import at.koopro.wizardsandbeasts.heritage.werewolf.WerewolfEquipment;
import at.koopro.wizardsandbeasts.item.armor.RobeHood;
import at.koopro.wizardsandbeasts.registry.ArmorItemRegistry;
import at.koopro.wizardsandbeasts.registry.DarkArtefactItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * The equipment layer end to end, on a real server with real Curios: what can be worn where, that
 * armour keeps working under clothing, that the cloak still behaves like the cloak, and that pieces
 * survive (and do not duplicate across) death, respawn, dimension change and relog.
 *
 * <p>Two tick sources matter for stats. Curios applies its modifiers from {@code EntityTickEvent.Post},
 * which the level fires every tick; vanilla applies armour modifiers from {@code ServerPlayer#doTick},
 * which a real connection drives and a mock one does not. Stat checks therefore wait real ticks
 * <em>and</em> drive {@code doTick} on each ({@code thenExecuteFor}).
 */
@NullMarked
final class EquipmentTests {

    private EquipmentTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("equipment_robe_equips_and_removes", "equipment: robe goes to body slot and comes off",
                EquipmentTests::robeEquipsAndRemoves);
        tests.add("equipment_armor_under_robe_keeps_protection",
                "equipment: netherite chestplate under a robe keeps its armour; robe adds its data-driven stats",
                EquipmentTests::armorUnderRobe);
        tests.add("equipment_robe_hides_chest_visually_only",
                "equipment: a robe hides the chestplate visually, the chestplate stays equipped",
                EquipmentTests::robeHidesChestVisually);
        tests.add("equipment_hood_up_hides_helmet", "equipment: raised hood also hides the helmet",
                EquipmentTests::hoodHidesHelmet);
        tests.add("equipment_slot_validation", "equipment: items only enter the slot their definition names",
                EquipmentTests::slotValidation);
        tests.add("equipment_full_outfit", "equipment: armour + robe + cloak + hat + mask + necklace together",
                EquipmentTests::fullOutfit);
        tests.add("equipment_cloak_effects_from_back_slot",
                "equipment: a cloak in the back slot hides its wearer and spends charge",
                EquipmentTests::cloakEffects);
        tests.add("equipment_deathly_hallow_conceals", "equipment: the Hallow conceals everything and never spends",
                EquipmentTests::deathlyHallow);
        tests.add("equipment_migrates_chest_cloak", "equipment: a saved chest-slot cloak moves to the back slot on login",
                EquipmentTests::migratesChestCloak);
        tests.add("equipment_migration_never_overwrites",
                "equipment: migration stows rather than overwrites, and leaves real armour alone",
                EquipmentTests::migrationNeverOverwrites);
        tests.add("equipment_survives_relog", "equipment: worn pieces are saved and reloaded",
                EquipmentTests::survivesRelog);
        tests.add("equipment_survives_dimension_change", "equipment: worn pieces survive a dimension change",
                EquipmentTests::survivesDimensionChange);
        tests.add("equipment_death_drops_once", "equipment: death drops each worn piece exactly once",
                EquipmentTests::deathDropsOnce);
        tests.add("equipment_werewolf_strips_wearables", "equipment: the werewolf change takes the cloak off",
                EquipmentTests::werewolfStrips);
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static ServerPlayer wearer(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }

    private static boolean wear(ServerPlayer player, ItemStack stack) {
        return EquipmentAPI.tryEquip(player, stack);
    }

    // -- scenarios -------------------------------------------------------------------------------

    private static void robeEquipsAndRemoves(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "RobeWearer");
        try {
            ItemStack robe = new ItemStack(ArmorItemRegistry.STUDENT_ROBE_CHEST.get());
            check(helper, wear(player, robe), () -> "student robe was refused by the body slot");
            check(helper, robe.isEmpty(), () -> "tryEquip did not take the robe out of the source stack");
            check(helper, EquipmentAPI.getEquippedBodyClothing(player).is(ArmorItemRegistry.STUDENT_ROBE_CHEST.get()),
                    () -> "robe not reported as body clothing");
            check(helper, player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                    () -> "robe landed in the vanilla chest slot");

            var removed = EquipmentAPI.stripWearables(player, stack -> false);
            check(helper, removed.size() == 1, () -> "expected one stripped piece, got " + removed.size());
            check(helper, !EquipmentAPI.hasEquipment(player, EquipmentType.BODY_CLOTHING),
                    () -> "robe still worn after removal");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void armorUnderRobe(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "ArmorUnderRobe");
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        helper.startSequence()
                .thenExecuteFor(2, player::doTick)
                .thenExecute(() -> {
                    double bare = player.getAttributeValue(Attributes.ARMOR);
                    check(helper, bare == 8.0, () -> "netherite chestplate alone should give 8 armour, got " + bare);
                    check(helper, wear(player, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get())),
                            () -> "robe refused");
                })
                .thenExecuteFor(3, player::doTick)
                .thenExecute(() -> {
                    double armor = player.getAttributeValue(Attributes.ARMOR);
                    double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
                    // Netherite 8/3 + Death Eater robe 6/1 from wearables.json.
                    check(helper, armor == 14.0, () -> "chestplate + robe armour should be 14, got " + armor);
                    check(helper, toughness == 4.0, () -> "chestplate + robe toughness should be 4, got " + toughness);
                    check(helper, player.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE),
                            () -> "the chestplate left its slot");
                    EquipmentAPI.stripWearables(player, stack -> false);
                })
                .thenExecuteFor(3, player::doTick)
                .thenExecute(() -> {
                    try {
                        double after = player.getAttributeValue(Attributes.ARMOR);
                        check(helper, after == 8.0, () -> "robe stats lingered after removal: " + after);
                        helper.succeed();
                    } finally {
                        retire(helper, player);
                    }
                });
    }

    private static void robeHidesChestVisually(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "RobeHidesChest");
        try {
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            check(helper, EquipmentVisibility.isArmorVisible(player, EquipmentSlot.CHEST),
                    () -> "chestplate hidden with nothing over it");
            wear(player, new ItemStack(ArmorItemRegistry.AUROR_ROBE_CHEST.get()));
            check(helper, !EquipmentVisibility.isArmorVisible(player, EquipmentSlot.CHEST),
                    () -> "robe did not hide the chestplate");
            check(helper, EquipmentVisibility.isArmorVisible(player, EquipmentSlot.HEAD),
                    () -> "robe with hood down hid the helmet");
            check(helper, player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                    () -> "hiding moved the chestplate out of its slot");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void hoodHidesHelmet(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "HoodUp");
        try {
            wear(player, new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get()));
            check(helper, EquipmentVisibility.isArmorVisible(player, EquipmentSlot.HEAD),
                    () -> "hood down hid the helmet");
            Boolean up = RobeHood.toggle(player);
            check(helper, Boolean.TRUE.equals(up), () -> "hood toggle did not find the worn robe");
            check(helper, RobeHood.isUp(EquipmentAPI.getEquippedBodyClothing(player)),
                    () -> "hood state was not written to the worn stack");
            check(helper, !EquipmentVisibility.isArmorVisible(player, EquipmentSlot.HEAD),
                    () -> "hood up did not hide the helmet");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void slotValidation(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "SlotRules");
        try {
            ItemStack mask = new ItemStack(ArmorItemRegistry.DEATH_EATER_MASK.get());
            check(helper, !CuriosEquipment.equip(player, "head", mask.copy()), () -> "a mask entered the head slot");
            check(helper, !CuriosEquipment.equip(player, "ring", new ItemStack(ArmorItemRegistry.WIZARD_HAT.get())),
                    () -> "a hat entered a ring slot");
            check(helper, !CuriosEquipment.equip(player, "back", new ItemStack(Items.NETHERITE_CHESTPLATE)),
                    () -> "real armour entered the back slot");
            check(helper, !EquipmentAPI.tryEquip(player, new ItemStack(Items.DIAMOND)),
                    () -> "an item with no definition was worn");
            check(helper, CuriosEquipment.equip(player, "face", mask.copy()), () -> "the mask was refused by the face slot");
            check(helper, EquipmentAPI.slotCount(player, EquipmentType.RING) == 2,
                    () -> "expected two ring slots, got " + EquipmentAPI.slotCount(player, EquipmentType.RING));
            // A second cloak never knocks the first off.
            check(helper, wear(player, new ItemStack(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get())), () -> "cloak refused");
            ItemStack second = new ItemStack(DarkArtefactItemRegistry.DEATHLY_HALLOW_CLOAK.get());
            check(helper, !wear(player, second) && !second.isEmpty(), () -> "a second cloak displaced the first");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void fullOutfit(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "FullOutfit");
        try {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_LEGS.get()));
            check(helper, wear(player, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_CHEST.get())), () -> "robe refused");
            check(helper, wear(player, new ItemStack(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get())), () -> "cloak refused");
            check(helper, wear(player, new ItemStack(ArmorItemRegistry.WIZARD_HAT.get())), () -> "hat refused");
            check(helper, wear(player, new ItemStack(ArmorItemRegistry.DEATH_EATER_MASK_3.get())), () -> "mask refused");
            check(helper, wear(player, new ItemStack(TrinketItemRegistry.TIME_TURNER.get())), () -> "time-turner refused");

            EquipmentAPI.Snapshot snapshot = EquipmentAPI.snapshot(player);
            check(helper, snapshot.worn().size() == 5, () -> "expected 5 worn pieces, got " + snapshot.worn());
            check(helper, !EquipmentAPI.getEquippedNecklace(player).isEmpty(), () -> "necklace not reported");
            EquipmentVisibility.Result visibility = snapshot.visibility();
            check(helper, !visibility.isArmorVisible(EquipmentSlot.HEAD), () -> "hat/mask did not hide the helmet");
            check(helper, !visibility.isArmorVisible(EquipmentSlot.CHEST), () -> "robe did not hide the chestplate");
            check(helper, visibility.isArmorVisible(EquipmentSlot.LEGS), () -> "robe trousers were hidden");
            check(helper, !visibility.concealed(), () -> "an ordinary cloak concealed the outfit");
            check(helper, player.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET)
                            && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE),
                    () -> "armour left its slots");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void cloakEffects(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "CloakWearer");
        ItemStack cloak = new ItemStack(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get());
        cloak.set(ModDataComponents.CLOAK_CHARGES.get(), 100);
        check(helper, wear(player, cloak), () -> "cloak refused by the back slot");
        helper.startSequence()
                .thenIdle(45)
                .thenExecute(() -> {
                    try {
                        check(helper, player.isInvisible(), () -> "a charged cloak in the back slot did not hide its wearer");
                        int left = CloakCharges.remaining(EquipmentAPI.getEquippedCloak(player));
                        check(helper, left < 100 && left >= 96, () -> "expected 1-4 seconds spent, charge now " + left);

                        EquipmentAPI.stripWearables(player, stack -> false);
                    } catch (RuntimeException e) {
                        retire(helper, player);
                        throw e;
                    }
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    try {
                        check(helper, !player.isInvisible(), () -> "taking the cloak off left the wearer invisible");
                        helper.succeed();
                    } finally {
                        retire(helper, player);
                    }
                });
    }

    private static void deathlyHallow(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "HallowWearer");
        check(helper, wear(player, new ItemStack(DarkArtefactItemRegistry.DEATHLY_HALLOW_CLOAK.get())), () -> "Hallow refused");
        helper.startSequence()
                .thenIdle(25)
                .thenExecute(() -> {
                    try {
                        ItemStack worn = EquipmentAPI.getEquippedCloak(player);
                        check(helper, player.isInvisible(), () -> "the Hallow did not hide its wearer");
                        check(helper, !CloakCharges.isChargeable(worn), () -> "the Hallow became chargeable");
                        check(helper, !worn.has(ModDataComponents.CLOAK_CHARGES.get()),
                                () -> "a charge was written onto the Hallow");
                        check(helper, EquipmentVisibility.resolve(player).concealed(),
                                () -> "the Hallow did not conceal the outfit");
                        helper.succeed();
                    } finally {
                        retire(helper, player);
                    }
                });
    }

    private static void migratesChestCloak(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer before = WizardTestSupport.placeMockPlayer(helper, "LegacyCloak", id, GameType.SURVIVAL);
        ItemStack legacy = new ItemStack(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get());
        legacy.set(ModDataComponents.CLOAK_CHARGES.get(), 777);
        // Placed straight into the chest slot, the way a pre-Curios save holds it.
        before.setItemSlot(EquipmentSlot.CHEST, legacy);
        retire(helper, before); // saves player data

        ServerPlayer after = WizardTestSupport.placeMockPlayer(helper, "LegacyCloak", id, GameType.SURVIVAL);
        try {
            // Login already ran the migration; a second pass must be a no-op.
            check(helper, EquipmentMigration.migrate(after) == 0, () -> "migration was not idempotent");
            check(helper, after.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), () -> "the cloak stayed in the chest slot");
            ItemStack cloak = EquipmentAPI.getEquippedCloak(after);
            check(helper, cloak.is(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get()), () -> "the cloak is not on the back");
            check(helper, CloakCharges.remaining(cloak) == 777, () -> "charge lost in migration: " + CloakCharges.remaining(cloak));
            helper.succeed();
        } finally {
            retire(helper, after);
        }
    }

    private static void migrationNeverOverwrites(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "MigrationRules");
        try {
            // Body slot already holds a robe; a second robe in the chest slot must be stowed, not swapped in.
            wear(player, new ItemStack(ArmorItemRegistry.STUDENT_ROBE_CHEST.get()));
            ItemStack legacyRobe = new ItemStack(ArmorItemRegistry.AUROR_ROBE_CHEST.get());
            RobeHood.setUp(legacyRobe, false);
            player.setItemSlot(EquipmentSlot.CHEST, legacyRobe);
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ArmorItemRegistry.AUROR_ROBE_LEGS.get()));

            int moved = EquipmentMigration.migrate(player);
            check(helper, moved == 1, () -> "expected one stack moved, got " + moved);
            check(helper, EquipmentAPI.getEquippedBodyClothing(player).is(ArmorItemRegistry.STUDENT_ROBE_CHEST.get()),
                    () -> "migration replaced the robe already worn");
            check(helper, player.getInventory().contains(new ItemStack(ArmorItemRegistry.AUROR_ROBE_CHEST.get())),
                    () -> "the displaced robe did not reach the inventory");
            check(helper, player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), () -> "the robe stayed in the chest slot");
            check(helper, player.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS), () -> "real boots were moved");
            check(helper, player.getItemBySlot(EquipmentSlot.LEGS).is(ArmorItemRegistry.AUROR_ROBE_LEGS.get()),
                    () -> "robe trousers (real armour) were moved");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void survivesRelog(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer before = WizardTestSupport.placeMockPlayer(helper, "Relogger", id, GameType.SURVIVAL);
        ItemStack robe = new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get());
        RobeHood.setUp(robe, true);
        wear(before, robe);
        wear(before, new ItemStack(ArmorItemRegistry.WIZARD_HAT.get()));
        retire(helper, before);

        ServerPlayer after = WizardTestSupport.placeMockPlayer(helper, "Relogger", id, GameType.SURVIVAL);
        try {
            ItemStack worn = EquipmentAPI.getEquippedBodyClothing(after);
            check(helper, worn.is(ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get()), () -> "robe lost across relog");
            check(helper, RobeHood.isUp(worn), () -> "hood state lost across relog");
            check(helper, EquipmentAPI.hasEquipment(after, EquipmentType.HEADWEAR), () -> "hat lost across relog");
            check(helper, EquipmentAPI.snapshot(after).worn().size() == 2, () -> "pieces duplicated across relog");
            helper.succeed();
        } finally {
            retire(helper, after);
        }
    }

    private static void survivesDimensionChange(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "DimensionHopper");
        try {
            wear(player, new ItemStack(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get()));
            wear(player, new ItemStack(ArmorItemRegistry.AUROR_ROBE_CHEST.get()));
            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            ServerPlayer moved = player.teleport(new TeleportTransition(nether, player.position(),
                    Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING));
            check(helper, moved != null && moved.level() == nether, () -> "setup: the player did not change dimension");
            check(helper, EquipmentAPI.snapshot(moved).worn().size() == 2,
                    () -> "worn pieces after dimension change: " + EquipmentAPI.snapshot(moved).worn());
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void deathDropsOnce(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "Mortal");
        wear(player, new ItemStack(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get()));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        helper.startSequence()
                .thenExecute(() -> player.kill(helper.getLevel()))
                .thenIdle(2)
                .thenExecute(() -> {
                    ServerPlayer fresh = helper.getLevel().getServer().getPlayerList()
                            .respawn(player, false, Entity.RemovalReason.KILLED);
                    try {
                        boolean keep = helper.getLevel().getGameRules()
                                .get(net.minecraft.world.level.gamerules.GameRules.KEEP_INVENTORY);
                        long dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                                        new AABB(origin).inflate(8),
                                        e -> e.getItem().is(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get()))
                                .size();
                        boolean worn = EquipmentAPI.hasEquipment(fresh, EquipmentType.CLOAK);
                        check(helper, dropped + (worn ? 1 : 0) == 1,
                                () -> "cloak count after death should be 1, dropped=" + dropped + " worn=" + worn);
                        check(helper, keep == worn, () -> "keepInventory=" + keep + " but cloak worn=" + worn);
                        helper.succeed();
                    } finally {
                        retire(helper, fresh);
                    }
                });
    }

    private static void werewolfStrips(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "Wolf");
        try {
            wear(player, new ItemStack(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get()));
            int removed = WerewolfEquipment.stripForTransformation(player);
            check(helper, removed >= 1, () -> "nothing stripped");
            check(helper, !EquipmentAPI.hasEquipment(player, EquipmentType.CLOAK), () -> "the wolf kept its cloak");
            check(helper, player.getInventory().contains(new ItemStack(DarkArtefactItemRegistry.INVISIBILITY_CLOAK.get())),
                    () -> "the stripped cloak was not stowed");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }
}
