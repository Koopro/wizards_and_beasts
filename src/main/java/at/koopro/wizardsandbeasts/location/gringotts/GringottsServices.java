package at.koopro.wizardsandbeasts.location.gringotts;

import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.location.LocationService;
import at.koopro.wizardsandbeasts.location.LocationServices;
import at.koopro.wizardsandbeasts.owl.career.track.ActivityType;
import at.koopro.wizardsandbeasts.owl.career.track.CareerProgression;
import at.koopro.wizardsandbeasts.treasure.TreasureArtefact;
import at.koopro.wizardsandbeasts.treasure.TreasureArtefacts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What the goblins sell besides the vault itself. Prices and tiers are {@code locations/gringotts.json}'s; this is
 * only what each service does.
 *
 * <ul>
 *   <li><b>appraisal</b> — a goblin reads the curse and worth of the artefact in hand (flat fee). Revelio does the
 *       same for free, for a wizard who has it.</li>
 *   <li><b>curse_breaking</b> — the bank's own curse-breakers lift the whole curse, for the greater of the flat fee
 *       and {@code rate} of the artefact's worth. No curse-breaking credit: the goblins did it.</li>
 *   <li><b>surrender_treasure</b> — every clean artefact carried is handed over; the finder is paid {@code rate} of
 *       its worth and each counts as {@code treasure_returned} from its site. Cursed ones are refused: no goblin
 *       touches an unbroken curse for free.</li>
 *   <li><b>high_security_vault</b> — rented item storage, kept through death ({@link HighSecurityVault}).</li>
 * </ul>
 */
@NullMarked
public final class GringottsServices {

    private GringottsServices() {}

    public static void register(LocationServices.Sink sink) {
        sink.register("appraisal", new Appraisal());
        sink.register("curse_breaking", new CurseBreakingService());
        sink.register("surrender_treasure", new Surrender());
        sink.register("high_security_vault", new HighSecurityVault.Service());
    }

    private static @Nullable ItemStack held(ServerPlayer player) {
        InteractionHand hand = TreasureArtefacts.handHolding(player);
        return hand == null ? null : player.getItemInHand(hand);
    }

    private static Component price(long knuts) {
        return Component.translatable("location.wizards_and_beasts.price", CurrencyHelper.formatFromKnuts(knuts));
    }

    static final class Appraisal implements LocationService {
        @Override
        public Quote quote(ServerPlayer player, Use use) {
            ItemStack stack = held(player);
            TreasureArtefact artefact = stack == null ? null : TreasureArtefacts.of(stack);
            if (artefact == null) {
                return Quote.no(Component.translatable("location.wizards_and_beasts.gringotts.hold_artefact"));
            }
            if (artefact.identified()) {
                return Quote.no(Component.translatable("location.wizards_and_beasts.gringotts.already_read"));
            }
            return Quote.yes(price(Use.billed() ? use.fee() : 0));
        }

        @Override
        public boolean perform(ServerPlayer player, Use use) {
            ItemStack stack = held(player);
            TreasureArtefact artefact = stack == null ? null : TreasureArtefacts.of(stack);
            if (artefact == null || artefact.identified() || !use.charge(player, use.fee())) {
                return false;
            }
            TreasureArtefact read = artefact.identify();
            TreasureArtefacts.set(stack, read);
            Component verdict = read.cursed()
                    ? Component.translatable("treasure.wizards_and_beasts.revealed",
                            read.curse().orElseThrow().displayName(), read.layers(),
                            CurrencyHelper.formatFromKnuts(read.valueKnuts()))
                    : Component.translatable("treasure.wizards_and_beasts.worth",
                            CurrencyHelper.formatFromKnuts(read.valueKnuts()));
            PlayerFeedback.toast(player, NoticeKind.DISCOVERY,
                    Component.translatable("location.wizards_and_beasts.gringotts.appraised"), verdict);
            return true;
        }
    }

    static final class CurseBreakingService implements LocationService {

        static long priceOf(TreasureArtefact artefact, Use use) {
            return Math.max(use.fee(), Math.round(artefact.valueKnuts() * (double) use.rate()));
        }

        @Override
        public Quote quote(ServerPlayer player, Use use) {
            ItemStack stack = held(player);
            TreasureArtefact artefact = stack == null ? null : TreasureArtefacts.of(stack);
            if (artefact == null || !artefact.cursed()) {
                return Quote.no(Component.translatable("location.wizards_and_beasts.gringotts.hold_cursed"));
            }
            return Quote.yes(price(Use.billed() ? priceOf(artefact, use) : 0));
        }

        @Override
        public boolean perform(ServerPlayer player, Use use) {
            ItemStack stack = held(player);
            TreasureArtefact artefact = stack == null ? null : TreasureArtefacts.of(stack);
            if (artefact == null || !artefact.cursed() || !use.charge(player, priceOf(artefact, use))) {
                return false;
            }
            TreasureArtefacts.set(stack, artefact.cleansed());
            PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                    Component.translatable("location.wizards_and_beasts.gringotts.cleansed"),
                    artefact.curse().orElseThrow().displayName());
            return true;
        }
    }

    static final class Surrender implements LocationService {

        /** Clean artefacts carried, and their total worth to the finder at this rate. */
        record Haul(int clean, int cursed, long payout) {}

        static Haul haul(ServerPlayer player, float rate) {
            int clean = 0;
            int cursed = 0;
            long payout = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                TreasureArtefact artefact = TreasureArtefacts.of(player.getInventory().getItem(slot));
                if (artefact == null) {
                    continue;
                }
                if (artefact.cursed()) {
                    cursed++;
                } else {
                    clean++;
                    payout += Math.round(artefact.valueKnuts() * (double) rate);
                }
            }
            return new Haul(clean, cursed, payout);
        }

        @Override
        public Quote quote(ServerPlayer player, Use use) {
            Haul haul = haul(player, use.rate());
            if (haul.clean() == 0) {
                return Quote.no(Component.translatable(haul.cursed() > 0
                        ? "location.wizards_and_beasts.gringotts.only_cursed"
                        : "location.wizards_and_beasts.gringotts.no_treasure"));
            }
            return Quote.yes(Component.translatable("location.wizards_and_beasts.gringotts.surrender_quote",
                    haul.clean(), CurrencyHelper.formatFromKnuts(Use.billed() ? haul.payout() : 0)));
        }

        @Override
        public boolean perform(ServerPlayer player, Use use) {
            float rate = use.rate();
            int returned = 0;
            long paid = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                TreasureArtefact artefact = TreasureArtefacts.of(stack);
                if (artefact == null || artefact.cursed()) {
                    continue;
                }
                long fee = Math.round(artefact.valueKnuts() * (double) rate);
                if (Use.billed() && fee > 0 && !GringottsBilling.credit(player, MoneyFlow.TREASURE_FINDERS_FEE, fee)) {
                    break; // the vault is full: keep the rest rather than take them unpaid
                }
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                paid += Use.billed() ? fee : 0;
                returned++;
                // Counted where it happened: the bank's standing, its board, and a curse-breaker's career.
                CareerProgression.record(player, ActivityType.TREASURE_RETURNED, artefact.site());
            }
            player.containerMenu.broadcastChanges();
            if (returned == 0) {
                PlayerFeedback.actionBar(player, Component.translatable(
                        "location.wizards_and_beasts.gringotts.only_cursed").withStyle(ChatFormatting.RED));
                return false;
            }
            PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                    Component.translatable("location.wizards_and_beasts.gringotts.surrendered", returned),
                    Component.translatable("location.wizards_and_beasts.gringotts.finders_fee",
                            CurrencyHelper.formatFromKnuts(paid)));
            return true;
        }
    }
}
