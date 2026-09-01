package at.koopro.wizardsandbeasts.item.currency;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.currency.dragot.DragotRates;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * The Dragot: French wizarding money, and deliberately not a fourth British coin.
 *
 * <p>A Knut, a Sickle and a Galleon are one currency in three denominations — fixed integer ratios,
 * accepted everywhere, interchangeable forever. {@link CoinItem} models that correctly by being a
 * render wrapper with a tooltip. A Dragot is a different thing and needs a different item, because
 * three facts about it are not true of any British coin:
 *
 * <ul>
 *   <li><b>Its worth moves.</b> The tooltip quotes the configured base rate, not a constant, and the
 *       rate a teller actually offers moves inside a spread around it — see {@code DragotQuotes}.</li>
 *   <li><b>It is not accepted everywhere.</b> Most British shopkeepers turn it away; a few Continental
 *       ones take nothing else. {@code DragotAcceptance} owns that, per vendor, by tag.</li>
 *   <li><b>Some of them are bad.</b> One in a hundred found in loot is devalued, and a devalued coin
 *       looks exactly like a sound one until somebody counts it — which is why the flag is a data
 *       component and why a devalued stack cannot merge with a sound one.</li>
 * </ul>
 *
 * <p>The tooltip is the whole of what the item does on its own; every rule above bites somewhere
 * else, at the counter or the till.
 */
@NullMarked
public class DragotItem extends Item {

    public DragotItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        // The base rate, not a quote. The tooltip is drawn client-side and the standing quote is
        // server state pinned per player; printing a number here that the till would not honour is
        // exactly the lie DragotQuotes exists to avoid. "About" is doing real work in this string.
        long baseKnuts = Math.round(Config.dragotGalleonRate * CurrencyHelper.KNUTS_PER_GALLEON);
        tooltipAdder.accept(Component.translatable("currency.wizards_and_beasts.dragot.worth",
                        String.format("%.2f", Config.dragotGalleonRate),
                        CurrencyHelper.formatFromKnuts(baseKnuts))
                .withStyle(ChatFormatting.GOLD));
        tooltipAdder.accept(Component.translatable("currency.wizards_and_beasts.dragot.spread",
                        Math.round(DragotRates.GRINGOTTS_FEE * 100.0f),
                        Math.round(DragotRates.VARIANCE * 100.0f))
                .withStyle(ChatFormatting.DARK_GRAY));

        // Deliberately nothing about the devalued flag. A tooltip that named a bad coin would be a
        // mechanic that never fires: nobody spends money they can see is bad, so the 20% notice roll
        // would be unreachable and the whole counterfeit rule would be decoration. The holder finds
        // out by being caught. The one inference available is that a devalued stack will not merge
        // with a sound one — which is exactly how you would notice a shaved coin in a real purse, by
        // laying it next to a good one.
    }
}
