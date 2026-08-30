package at.koopro.wizardsandbeasts.item.consumable;

import at.koopro.wizardsandbeasts.bertiebotts.BeanFlavour;
import at.koopro.wizardsandbeasts.bertiebotts.BertieBotts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * Bertie Bott's Every-Flavour Beans. Every flavour, and they mean every flavour.
 *
 * <p>The old version was a fifty-fifty between Speed and Poison. That is a fair gamble and a bad
 * joke: after three beans a player knows the whole item, and "every flavour" turns out to mean two.
 * The bag now draws a tier and then a flavour within it — twelve outcomes across five bands, from
 * toffee to a bogey — so what you cannot predict is <em>what</em>, only roughly how bad. See
 * {@link BertieBotts}.
 *
 * <p><b>Every bean says what it was.</b> Always, including the dull ones. A bean that quietly applied
 * Nausea would be an unexplained debuff; a bean that says <i>Earwax…</i> first is a joke the player is
 * in on, and it is the entire reason the item exists.
 */
@NullMarked
public class BertieBottsBeansItem extends ConsumedItem {

    private static final int EAT_TICKS = 16;

    public BertieBottsBeansItem(Properties properties) {
        super(properties, EAT_TICKS, ItemUseAnimation.EAT);
    }

    @Override
    protected void onConsumed(ItemStack stack, Level level, Player player) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }

        // The nutrition is the same whatever the bean turns out to be. Applied before the flavour so
        // a flavour that touches hunger — Vomit — is working against a fed player, not a hungry one.
        player.getFoodData().eat(BertieBotts.NUTRITION, BertieBotts.SATURATION);

        BeanFlavour flavour = BertieBotts.draw(server.getRandom());
        flavour.apply(player, server);

        // The name lands before anything else the flavour did can be noticed.
        player.displayClientMessage(
                Component.translatable("item.wizards_and_beasts.bertie_botts.tasted",
                        flavour.displayName()), true);

        stack.consume(1, player);
        player.getCooldowns().addCooldown(stack, BertieBotts.COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("item.wizards_and_beasts.bertie_botts.warning")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
