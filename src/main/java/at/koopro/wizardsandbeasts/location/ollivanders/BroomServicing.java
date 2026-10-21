package at.koopro.wizardsandbeasts.location.ollivanders;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.broom.BroomItem;
import at.koopro.wizardsandbeasts.item.broom.BroomPolish;
import at.koopro.wizardsandbeasts.location.LocationService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * A full service of the broom in hand: every point of wear made good and the handle polished. Polish alone
 * restores a quarter ({@link BroomPolish#REPAIR_FRACTION}); a worn-out racing broom otherwise needs four tins, and
 * a spent one will not fly at all ({@link BroomItem#isSpent}).
 *
 * <p>Price = the offer's fee × the broom tier's {@code baseRepairCost} (1 School … 12 Legendary) × the share worn,
 * rounded up to a Sickle. Offered at the broom shop ({@code locations/broom_shop.json}, kept by a broommaker at a
 * broom rack); it sat at Ollivanders' workshop until the shop had a keeper.
 */
@NullMarked
public final class BroomServicing implements LocationService {

    /** The price of servicing {@code broom} at {@code fee} per School-broom unit of full wear; 0 for a sound one. */
    public static long priceOf(ItemStack broom, long fee) {
        if (!(broom.getItem() instanceof BroomItem) || broom.getMaxDamage() <= 0 || broom.getDamageValue() <= 0) {
            return 0L;
        }
        BroomDefinition definition = BroomItem.resolveDefinition(broom);
        double wear = Math.min(1.0, broom.getDamageValue() / (double) broom.getMaxDamage());
        long raw = (long) Math.ceil(fee * definition.tier().baseRepairCost() * wear);
        return CurrencyHelper.roundUpToSickle(raw);
    }

    @Override
    public Quote quote(ServerPlayer player, Use use) {
        ItemStack broom = player.getMainHandItem();
        if (!(broom.getItem() instanceof BroomItem)) {
            return Quote.no(Component.translatable("location.wizards_and_beasts.ollivanders.hold_broom"));
        }
        if (broom.getDamageValue() <= 0) {
            return Quote.no(Component.translatable("location.wizards_and_beasts.ollivanders.broom_sound"));
        }
        return Quote.yes(Component.translatable("location.wizards_and_beasts.price",
                CurrencyHelper.formatFromKnuts(Use.billed() ? priceOf(broom, use.fee()) : 0)));
    }

    @Override
    public boolean perform(ServerPlayer player, Use use) {
        ItemStack broom = player.getMainHandItem();
        long price = priceOf(broom, use.fee());
        if (!(broom.getItem() instanceof BroomItem) || broom.getDamageValue() <= 0 || !use.charge(player, price)) {
            return false;
        }
        broom.setDamageValue(0);
        BroomPolish.apply(broom, player.level());
        PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                Component.translatable("location.wizards_and_beasts.ollivanders.broom_serviced"), broom.getHoverName());
        return true;
    }
}
