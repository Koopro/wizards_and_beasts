package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.equipment.WardrobeLayout;
import at.koopro.wizardsandbeasts.equipment.curios.WardrobeMenu;
import at.koopro.wizardsandbeasts.equipment.curios.WardrobeRenders;
import at.koopro.wizardsandbeasts.registry.ArmorItemRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.NullMarked;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * The wardrobe screen's server rules (UI renaissance P6): it is only a view of Curios, so everything it lets a player
 * do must be what Curios allows, checked on the server.
 */
@NullMarked
final class WardrobeTests {

    /** Menu slot index of place {@code i}'s worn slot, and its look slot one after. */
    private static int worn(int place) {
        return place * 2;
    }

    private static int place(String key) {
        for (int i = 0; i < WardrobeLayout.PLACES.size(); i++) {
            if (WardrobeLayout.PLACES.get(i).key().equals(key)) {
                return i;
            }
        }
        throw new IllegalArgumentException(key);
    }

    private WardrobeTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("wardrobe_admits_by_slot", "wardrobe: each slot takes only what Curios lets into it",
                WardrobeTests::admitsBySlot);
        tests.add("wardrobe_shift_click_wears", "wardrobe: shift-click puts a piece in its worn slot, never a look slot",
                WardrobeTests::shiftClickWears);
        tests.add("wardrobe_look_grants_nothing", "wardrobe: a robe in the look slot gives no armour; worn, it does",
                WardrobeTests::lookGrantsNothing);
        tests.add("wardrobe_binding_curse_holds", "wardrobe: a cursed piece cannot be taken off in survival",
                WardrobeTests::bindingCurseHolds);
        tests.add("wardrobe_creative_lifts_curse", "wardrobe: creative may take a cursed piece off, as with armour",
                WardrobeTests::creativeLiftsCurse);
        tests.add("wardrobe_eye_toggles_own_flag", "wardrobe: the eye flips the player's own render flag and nothing else",
                WardrobeTests::eyeTogglesOwnFlag);
    }

    private static ServerPlayer wearer(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }

    private static void admitsBySlot(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "WardrobeAdmits");
        try {
            WardrobeMenu menu = new WardrobeMenu(0, player.getInventory());
            check(helper, menu.slots.size() == WardrobeLayout.WARDROBE_SLOTS + 36,
                    () -> "expected 56 slots, got " + menu.slots.size());
            ItemStack hat = new ItemStack(ArmorItemRegistry.WIZARD_HAT.get());
            ItemStack mask = new ItemStack(ArmorItemRegistry.DEATH_EATER_MASK.get());
            Slot head = menu.slots.get(worn(place("head")));
            Slot face = menu.slots.get(worn(place("face")));
            Slot ring = menu.slots.get(worn(place("ring")));
            check(helper, head.mayPlace(hat), () -> "the hat was refused by the head slot");
            check(helper, menu.slots.get(worn(place("head")) + 1).mayPlace(hat), () -> "the hat was refused as a look");
            check(helper, !head.mayPlace(mask), () -> "a mask entered the head slot");
            check(helper, face.mayPlace(mask), () -> "the mask was refused by the face slot");
            check(helper, !ring.mayPlace(hat), () -> "a hat entered a ring slot");
            check(helper, !head.mayPlace(new ItemStack(Items.DIRT)), () -> "dirt entered the head slot");
            check(helper, !menu.slots.get(worn(place("body"))).mayPlace(new ItemStack(Items.NETHERITE_CHESTPLATE)),
                    () -> "real armour entered the body clothing slot");
            check(helper, menu.stillValid(player), () -> "the owner's own wardrobe was not valid");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void shiftClickWears(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "WardrobeShift");
        try {
            player.getInventory().setItem(9, new ItemStack(ArmorItemRegistry.WIZARD_HAT.get()));
            WardrobeMenu menu = new WardrobeMenu(0, player.getInventory());
            int invSlot = WardrobeLayout.WARDROBE_SLOTS; // inventory index 9 is the menu's first inventory slot
            menu.quickMoveStack(player, invSlot);
            int head = worn(place("head"));
            check(helper, menu.slots.get(head).getItem().is(ArmorItemRegistry.WIZARD_HAT.get()),
                    () -> "the hat did not land in the worn head slot");
            check(helper, menu.slots.get(head + 1).getItem().isEmpty(), () -> "the hat landed in the look slot");
            check(helper, player.getInventory().getItem(9).isEmpty(), () -> "the hat stayed in the inventory too");
            // and back out again
            menu.quickMoveStack(player, head);
            check(helper, menu.slots.get(head).getItem().isEmpty(), () -> "shift-click did not take the hat off");
            check(helper, player.getInventory().contains(new ItemStack(ArmorItemRegistry.WIZARD_HAT.get())),
                    () -> "the hat vanished on the way back");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void lookGrantsNothing(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "WardrobeLook");
        WardrobeMenu menu = new WardrobeMenu(0, player.getInventory());
        int body = worn(place("body"));
        helper.startSequence()
                .thenExecute(() -> menu.slots.get(body + 1).set(new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get())))
                .thenExecuteFor(3, player::doTick)
                .thenExecute(() -> {
                    double armor = player.getAttributeValue(Attributes.ARMOR);
                    check(helper, armor == 0.0, () -> "a robe worn only as a look gave " + armor + " armour");
                    menu.slots.get(body).set(new ItemStack(ArmorItemRegistry.DEATH_EATER_ROBE_CHEST.get()));
                })
                .thenExecuteFor(3, player::doTick)
                .thenExecute(() -> {
                    try {
                        double armor = player.getAttributeValue(Attributes.ARMOR);
                        check(helper, armor == 6.0, () -> "a worn Death Eater robe should give 6 armour, got " + armor);
                        helper.succeed();
                    } finally {
                        retire(helper, player);
                    }
                });
    }

    private static void bindingCurseHolds(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "WardrobeCurse");
        try {
            ItemStack hat = new ItemStack(ArmorItemRegistry.WIZARD_HAT.get());
            hat.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.BINDING_CURSE), 1);
            WardrobeMenu menu = new WardrobeMenu(0, player.getInventory());
            Slot head = menu.slots.get(worn(place("head")));
            head.set(hat);
            check(helper, !head.mayPickup(player), () -> "a cursed hat could be taken off in survival");
            check(helper, menu.quickMoveStack(player, worn(place("head"))).isEmpty() && head.hasItem(),
                    () -> "shift-click took a cursed hat off");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void creativeLiftsCurse(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "WardrobeCreative", GameType.CREATIVE);
        WizardTestSupport.parkAtOrigin(helper, player);
        try {
            ItemStack hat = new ItemStack(ArmorItemRegistry.WIZARD_HAT.get());
            hat.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.BINDING_CURSE), 1);
            WardrobeMenu menu = new WardrobeMenu(0, player.getInventory());
            Slot head = menu.slots.get(worn(place("head")));
            head.set(hat);
            check(helper, player.isCreative(), () -> "the mock player is not in creative");
            check(helper, head.mayPickup(player), () -> "creative could not take a cursed hat off");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }

    private static void eyeTogglesOwnFlag(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "WardrobeEye");
        try {
            WardrobeMenu menu = new WardrobeMenu(0, player.getInventory());
            int head = place("head");
            boolean before = menu.shown(head);
            WardrobeRenders.Change change = WardrobeRenders.toggle(player, head);
            check(helper, change != null && change.entityId() == player.getId() && change.slotId().equals("head"),
                    () -> "the toggle did not report the player's own head slot: " + change);
            check(helper, menu.shown(head) == !before, () -> "the head slot's flag did not flip");
            check(helper, menu.shown(place("face")), () -> "toggling the head touched the face slot");
            WardrobeRenders.toggle(player, head);
            check(helper, menu.shown(head) == before, () -> "a second toggle did not restore the flag");
            check(helper, WardrobeRenders.toggle(player, -1) == null && WardrobeRenders.toggle(player, 99) == null,
                    () -> "an out-of-range place was accepted");
            helper.succeed();
        } finally {
            retire(helper, player);
        }
    }
}
