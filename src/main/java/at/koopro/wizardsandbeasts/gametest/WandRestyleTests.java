package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.wand.WandAppearance;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import at.koopro.wizardsandbeasts.wand.style.WandRestyle;
import at.koopro.wizardsandbeasts.wand.style.WandRestyle.Aspect;
import at.koopro.wizardsandbeasts.wand.style.WandRestyleMenu;
import at.koopro.wizardsandbeasts.wand.visual.WandLook;
import at.koopro.wizardsandbeasts.wand.visual.WandLookResolver;
import at.koopro.wizardsandbeasts.wand.visual.WandMetal;
import at.koopro.wizardsandbeasts.wand.visual.WandStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.Objects;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/** Restyling a wand at the bench and at Ollivanders: the look changes as chosen, and nothing else about the wand. */
public final class WandRestyleTests {

    private static final BlockPos BENCH = new BlockPos(2, 1, 2);

    private WandRestyleTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("wand_restyle_bench_changes_looks_only",
                "wands: at the bench a wand is given a new handle, colour, metal and light; the price is taken "
                        + "from the inventory and every stat, the bond and the master are untouched",
                WandRestyleTests::bench);
        tests.add("wand_restyle_ollivander_random_look",
                "wands: Ollivanders' restyle sets every aspect to a real choice, differs from the look it came in "
                        + "with, and keeps the wand's make",
                WandRestyleTests::ollivander);
    }

    private static ItemStack wand() {
        ItemStack wand = WandItem.createWand(WandWood.HOLLY, WandCore.PHOENIX_FEATHER, WandLength.values()[2],
                WandFlexibility.SUPPLE);
        wand.set(WandComponents.WAND_INTEGRITY.get(), 0.7f);
        return wand;
    }

    private static void bench(GameTestHelper helper) {
        ServerPlayer maker = WizardTestSupport.placeMockPlayer(helper, "WandRestyler", GameType.SURVIVAL);
        try {
            WizardTestSupport.parkAtOrigin(helper, maker);
            helper.setBlock(BENCH, ModBlocks.WANDMAKERS_BENCH.get());
            WandmakersBenchBlockEntity bench = (WandmakersBenchBlockEntity) helper.getLevel()
                    .getBlockEntity(helper.absolutePos(BENCH));
            check(helper, bench != null, () -> "setup: no bench");
            WandRestyleMenu menu = new WandRestyleMenu(0, maker.getInventory(), bench);
            ItemStack wand = wand();
            menu.slots.get(0).set(wand.copy());
            check(helper, menu.status() == WandRestyleMenu.Status.NO_CHANGE, () -> "untouched: " + menu.status());

            int handleIndex = 2 + WandRestyle.modules(WandSlot.HANDLE).indexOf(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("wizards_and_beasts", "talon"));
            pick(menu, maker, Aspect.HANDLE, handleIndex);
            pick(menu, maker, Aspect.COLOUR, 2 + 8);                       // ebony
            pick(menu, maker, Aspect.METAL, 2 + WandMetal.GOLD.ordinal());
            pick(menu, maker, Aspect.LIGHT, 2 + 4);                        // storm blue
            check(helper, menu.status() == WandRestyleMenu.Status.CANNOT_AFFORD,
                    () -> "an empty inventory could pay: " + menu.status());

            maker.getInventory().add(new ItemStack(Items.OAK_LOG, 1));
            maker.getInventory().add(new ItemStack(Items.BLACK_DYE, 1));
            maker.getInventory().add(new ItemStack(Items.GOLD_INGOT, 5));
            maker.getInventory().add(new ItemStack(Items.GLOWSTONE_DUST, 1));
            menu.refresh();
            check(helper, menu.status() == WandRestyleMenu.Status.READY, () -> "paid in full, still " + menu.status());

            ItemStack out = menu.slots.get(1).getItem().copy();
            check(helper, menu.slots.get(1).mayPickup(maker), () -> "the result cannot be taken");
            menu.slots.get(1).onTake(maker, out);

            WandStyle style = out.getOrDefault(WandComponents.WAND_STYLE.get(), WandStyle.NONE);
            check(helper, style.tint().equals(java.util.Optional.of(WandRestyle.STAINS.get(8).rgb())),
                    () -> "colour not set: " + style);
            check(helper, style.metal().equals(java.util.Optional.of(WandMetal.GOLD)), () -> "metal not set: " + style);
            check(helper, style.glint().equals(java.util.Optional.of(WandRestyle.LIGHTS.get(4).rgb())),
                    () -> "light not set: " + style);
            check(helper, WandAppearance.configuration(helper.getLevel().registryAccess(), out)
                    .getModule(WandSlot.HANDLE).map(id -> id.getPath().equals("talon")).orElse(false),
                    () -> "handle not set");
            WandLook look = WandLookResolver.resolve(helper.getLevel().registryAccess(), out);
            check(helper, look.metal() == WandMetal.GOLD && (look.tint() & 0xFFFFFF) == WandRestyle.STAINS.get(8).rgb(),
                    () -> "the look did not follow the style: " + look);
            sameMake(helper, wand, out);
            check(helper, maker.getInventory().countItem(Items.GOLD_INGOT) == 1
                            && maker.getInventory().countItem(Items.OAK_LOG) == 0
                            && maker.getInventory().countItem(Items.BLACK_DYE) == 0
                            && maker.getInventory().countItem(Items.GLOWSTONE_DUST) == 0,
                    () -> "the price taken was wrong");
            check(helper, menu.slots.get(0).getItem().isEmpty(), () -> "the wand put in was not spent");
            check(helper, !WandRestyle.changes(menu.selection()), () -> "the choice did not reset after a take");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, maker);
        }
    }

    private static void pick(WandRestyleMenu menu, ServerPlayer maker, Aspect aspect, int index) {
        menu.clickMenuButton(maker, aspect.ordinal() * WandRestyleMenu.STRIDE + index);
    }

    private static void ollivander(GameTestHelper helper) {
        ItemStack wand = wand();
        RandomSource random = RandomSource.create(42);
        for (int round = 0; round < 20; round++) {
            WandStyle before = wand.get(WandComponents.WAND_STYLE.get());
            int[] pick = WandRestyle.random(random, before,
                    WandAppearance.configuration(helper.getLevel().registryAccess(), wand));
            for (Aspect aspect : Aspect.values()) {
                int i = pick[aspect.ordinal()];
                check(helper, i != WandRestyle.KEEP && i < WandRestyle.size(aspect),
                        () -> aspect + " rolled " + i);
            }
            ItemStack out = WandRestyle.apply(helper.getLevel().registryAccess(), wand, pick);
            sameMake(helper, wand, out);
            check(helper, !Objects.equals(before, out.get(WandComponents.WAND_STYLE.get())),
                    () -> "a restyle came back with the look it went in with");
            wand = out;
        }
        helper.succeed();
    }

    /** Every component a stat, a rule or the bond reads is the same before and after. */
    private static void sameMake(GameTestHelper helper, ItemStack before, ItemStack after) {
        List<DataComponentType<?>> stats = List.of(WandComponents.WAND_WOOD.get(), WandComponents.WAND_CORE.get(),
                WandComponents.WAND_LENGTH.get(), WandComponents.WAND_FLEXIBILITY.get(),
                WandComponents.WAND_INTEGRITY.get());
        for (DataComponentType<?> type : stats) {
            check(helper, Objects.equals(before.get(type), after.get(type)),
                    () -> "restyling changed " + type + ": " + before.get(type) + " -> " + after.get(type));
        }
        check(helper, WandComponents.getMaster(before).equals(WandComponents.getMaster(after))
                        && WandComponents.getAllegianceScore(before) == WandComponents.getAllegianceScore(after),
                () -> "restyling changed the bond");
        check(helper, after.getCount() == 1, () -> "restyling made " + after.getCount() + " wands");
    }
}
