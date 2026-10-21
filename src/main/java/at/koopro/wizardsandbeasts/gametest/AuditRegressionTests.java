package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.currency.dragot.DragotExchange;
import at.koopro.wizardsandbeasts.currency.dragot.DragotPurse;
import at.koopro.wizardsandbeasts.form.constraint.FormConstraintEvents;
import at.koopro.wizardsandbeasts.form.constraint.FormConstraintSet;
import at.koopro.wizardsandbeasts.form.constraint.FormConstraints;
import at.koopro.wizardsandbeasts.mirror.MirrorSessionManager;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.owl.post.OwlParcel;
import at.koopro.wizardsandbeasts.owl.post.OwlPostData;
import at.koopro.wizardsandbeasts.owl.post.OwlPostService;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;

import java.util.Optional;
import java.util.UUID;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Regressions from the release audit (tasks/todo_release_audit_2026_10_15.md): each scenario fails on the code
 * as it stood before the fix.
 *
 * <p>Two of them are the same trap: {@code Inventory.add} returns {@code true} on a <em>partial</em> insert and
 * leaves the remainder in the stack, so they share one "nearly full inventory" fixture.
 */
final class AuditRegressionTests {

    private AuditRegressionTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("audit_owl_partial_delivery_keeps_the_remainder",
                "audit: a parcel that only partly fits is not destroyed; what did not fit keeps flying",
                AuditRegressionTests::owlPartialDelivery);
        tests.add("audit_dragot_caught_returns_sound_coins",
                "audit: a goblin who catches a bad Dragot keeps the bad coin only",
                AuditRegressionTests::dragotCaughtKeepsOnlyTheBadCoin);
        tests.add("audit_form_toss_cancel_keeps_the_remainder",
                "audit: a blocked toss that only partly fits back lets the rest drop instead of deleting it",
                AuditRegressionTests::formTossPartialFit);
        tests.add("audit_mirror_session_survives_a_second_mirror",
                "audit: carrying two mirrors does not end a session opened on the second",
                AuditRegressionTests::mirrorSessionWithTwoMirrors);
    }

    /** Main inventory full of dirt except slot 0: {@code held} of {@code item}, so {@code 64 - held} more fit. */
    private static void nearlyFull(ServerPlayer player, Item item, int held) {
        player.getInventory().clearContent();
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        player.getInventory().setItem(0, new ItemStack(item, held));
        player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.DIRT, 64));
    }

    private static int carried(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // -- owl post ---------------------------------------------------------------------------------

    private static void owlPartialDelivery(GameTestHelper helper) {
        Runnable release = WizardTestSupport.leaseModule(Module.OWLS);
        ServerPlayer recipient = WizardTestSupport.placeMockPlayer(helper, "OwlRecipient", GameType.SURVIVAL);
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        OwlPostData data = OwlPostData.get(overworld);
        UUID parcelId = UUID.randomUUID();
        try {
            nearlyFull(recipient, Items.DIAMOND, 60);
            data.add(new OwlParcel(parcelId, UUID.randomUUID(), recipient.getUUID(), new ItemStack(Items.DIAMOND, 10),
                    overworld.getGameTime() - 1, false));

            OwlPostService.sweep(overworld);

            check(helper, carried(recipient, Items.DIAMOND) == 64,
                    () -> "the part that fits should have arrived, got " + carried(recipient, Items.DIAMOND));
            OwlParcel left = data.parcels().stream().filter(p -> p.id().equals(parcelId)).findFirst().orElse(null);
            check(helper, left != null && left.returning() && left.payload().getCount() == 6,
                    () -> "the six that did not fit must still be in the post, returning; parcel="
                            + (left == null ? "GONE (destroyed)" : left.payload().getCount() + " returning=" + left.returning()));
            helper.succeed();
        } finally {
            data.remove(parcelId);
            release.run();
            WizardTestSupport.retire(helper, recipient);
        }
    }

    // -- dragots ----------------------------------------------------------------------------------

    private static void dragotCaughtKeepsOnlyTheBadCoin(GameTestHelper helper) {
        ServerPlayer seller = WizardTestSupport.placeMockPlayer(helper, "DragotSeller", GameType.SURVIVAL);
        try {
            boolean caught = false;
            // The notice roll is 20%: 150 attempts without one is a 1-in-10^14 miss.
            for (int attempt = 0; attempt < 150 && !caught; attempt++) {
                seller.getInventory().clearContent();
                DragotPurse.give(seller, 3);
                seller.getInventory().add(DragotPurse.devalue(new ItemStack(CurrencyItemRegistry.DRAGOT.get(), 1)));
                DragotExchange.Result result = DragotExchange.sell(seller, 4);
                if (!result.ok()) {
                    caught = true;
                    int total = DragotPurse.total(seller);
                    int bad = DragotPurse.devalued(seller);
                    check(helper, total == 3 && bad == 0,
                            () -> "a caught sale must take only the bad coin; purse now " + total + " (" + bad
                                    + " bad), wanted 3 sound");
                }
            }
            check(helper, caught, () -> "never caught in 150 sales; the roll or the setup is wrong");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, seller);
        }
    }

    // -- form toss --------------------------------------------------------------------------------

    private static void formTossPartialFit(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "TossingBeast", GameType.SURVIVAL);
        String sourceId = "audit_toss_" + player.getUUID();
        FormConstraints.register(sourceId, p -> p.getUUID().equals(player.getUUID())
                ? FormConstraintSet.BEAST_HANDS : FormConstraintSet.NONE);
        try {
            nearlyFull(player, Items.DIAMOND, 60);
            ItemEntity dropped = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(),
                    new ItemStack(Items.DIAMOND, 10));
            ItemTossEvent event = new ItemTossEvent(dropped, player);

            FormConstraintEvents.onItemToss(event);

            int inInventory = carried(player, Items.DIAMOND);
            int inTheToss = event.isCanceled() ? 0 : dropped.getItem().getCount();
            check(helper, inInventory + inTheToss == 70,
                    () -> "items vanished: inventory " + inInventory + " + toss " + inTheToss + " != 70 (cancelled="
                            + event.isCanceled() + ")");
            check(helper, !event.isCanceled(), () -> "a partial fit must let the remainder through, not cancel");
            helper.succeed();
        } finally {
            FormConstraints.unregister(sourceId);
            WizardTestSupport.retire(helper, player);
        }
    }

    // -- mirrors ----------------------------------------------------------------------------------

    private static ItemStack mirror(UUID pairId) {
        ItemStack stack = new ItemStack(TrinketItemRegistry.TWO_WAY_MIRROR.get());
        stack.set(ModDataComponents.MIRROR_PAIR_ID.get(), Optional.of(pairId));
        return stack;
    }

    private static void mirrorSessionWithTwoMirrors(GameTestHelper helper) {
        ServerPlayer a = WizardTestSupport.placeMockPlayer(helper, "MirrorA", GameType.SURVIVAL);
        ServerPlayer b = WizardTestSupport.placeMockPlayer(helper, "MirrorB", GameType.SURVIVAL);
        UUID x = UUID.randomUUID();
        UUID y = UUID.randomUUID();
        a.getInventory().clearContent();
        b.getInventory().clearContent();
        a.getInventory().setItem(0, mirror(x));
        a.getInventory().setItem(1, mirror(y));
        b.getInventory().setItem(0, mirror(y));

        MirrorSessionManager.requestConnect(a, y, b.getName().getString());
        check(helper, MirrorSessionManager.isConnected(a.getUUID()), () -> "setup: the session did not open");
        helper.runAfterDelay(6, () -> {
            try {
                check(helper, MirrorSessionManager.isConnected(a.getUUID()),
                        () -> "the session ended though both still hold the mirror it was opened on");
                helper.succeed();
            } finally {
                MirrorSessionManager.end(a.getUUID(), helper.getLevel().getServer(), null);
                WizardTestSupport.retire(helper, a);
                WizardTestSupport.retire(helper, b);
            }
        });
    }
}
