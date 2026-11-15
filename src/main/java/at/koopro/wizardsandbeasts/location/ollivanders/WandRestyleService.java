package at.koopro.wizardsandbeasts.location.ollivanders;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.location.LocationService;
import at.koopro.wizardsandbeasts.wand.WandAppearance;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.refine.WandRefineMenu;
import at.koopro.wizardsandbeasts.wand.style.WandRestyle;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * Ollivanders' restyle: hand over the wand in your main hand and a fee, and take it back with a whole new look of
 * Mr Ollivander's choosing -- shape, wood colour, grain, metal and light all at random ({@link WandRestyle#random}),
 * never the look it came in with. Only looks change; it is the same wand, bond and history and all.
 */
@NullMarked
final class WandRestyleService implements LocationService {

    private static final String KEY = "location.wizards_and_beasts.ollivanders.restyle.";

    @Override
    public Quote quote(ServerPlayer player, Use use) {
        if (!WandRefineMenu.isFinishedWand(player.getMainHandItem())) {
            return Quote.no(Component.translatable(KEY + "hold"));
        }
        return Quote.yes(Component.translatable("location.wizards_and_beasts.price",
                CurrencyHelper.formatFromKnuts(Use.billed() ? use.fee() : 0)));
    }

    @Override
    public boolean perform(ServerPlayer player, Use use) {
        ItemStack wand = player.getMainHandItem();
        if (!WandRefineMenu.isFinishedWand(wand) || !use.charge(player, use.fee())) {
            return false;
        }
        int[] pick = WandRestyle.random(player.getRandom(), wand.get(WandComponents.WAND_STYLE.get()),
                WandAppearance.configuration(player.registryAccess(), wand));
        player.setItemInHand(InteractionHand.MAIN_HAND, WandRestyle.apply(player.registryAccess(), wand, pick));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.6f, 1.4f);
        PlayerFeedback.actionBar(player, Component.translatable(KEY + "done"));
        return true;
    }
}
