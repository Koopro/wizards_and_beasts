package at.koopro.wizardsandbeasts.item.currency;

import at.koopro.wizardsandbeasts.item.GeoItemRenderers;
import at.koopro.wizardsandbeasts.item.GeoItemBase;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.object.PlayState;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class CoinItem extends GeoItemBase {

    private final String coinName;

    public CoinItem(Properties properties, String coinName) {
        super(properties);
        this.coinName = coinName;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(GeoItemRenderers.lazy(
                "at.koopro.wizardsandbeasts.client.currency.CoinRenderer",
                new Class<?>[] {String.class},
                new Object[] {coinName}));
    }

    /**
     * What this coin is worth, on the coin itself.
     *
     * <p>The three coins had no tooltip at all — they were render wrappers and nothing else — so the
     * only way to learn that a Galleon is seventeen Sickles was to read the source or to find a
     * Gringotts teller. Wizarding money is not decimal, which is exactly why it has to be stated:
     * a player who assumes ten-to-one is wrong twice over.
     *
     * <p>Every coin carries the full scale, not just its own step. Hovering the one Knut you picked
     * up should teach you the whole system, because that Knut may be the only coin you have seen.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable("currency.wizards_and_beasts.worth." + coinName)
                .withStyle(ChatFormatting.GOLD));
        tooltipAdder.accept(Component.translatable("currency.wizards_and_beasts.scale",
                        CurrencyHelper.KNUTS_PER_SICKLE, CurrencyHelper.SICKLES_PER_GALLEON)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CoinItem>(
                "coin_controller", 0,
                state -> PlayState.STOP));
    }
}
