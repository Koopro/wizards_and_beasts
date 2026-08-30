package at.koopro.wizardsandbeasts.item.darkartefact;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * Shared behaviour for the five vessels Riddle split his soul into.
 *
 * <p>Every one of them read its soul flag out of the same data component, foiled while that flag
 * held, and gated its right-click behind the Dark Arts module — five verbatim copies of the same
 * three overrides. All of it lives here, along with the {@code [Soul fragment bound]} /
 * {@code [Destroyed — …]} tooltip pair that only ever differed in what did the destroying.
 *
 * <p>Four of the five are passive: the dread aura is applied while carried by
 * {@code HorcruxBearerTickHandler}, so their right-click does nothing beyond the module gate.
 * The diary is the exception and overrides {@link #onDarkArtsUse}.
 */
@NullMarked
public abstract class HorcruxItem extends Item implements IHorcruxVessel {

    private final String destroyedBy;

    /**
     * @param destroyedBy what destroyed this horcrux in canon, e.g. {@code "Basilisk fang, 1993"} —
     *                    shown in the tooltip once the soul fragment is gone
     */
    protected HorcruxItem(Properties properties, String destroyedBy) {
        super(properties);
        this.destroyedBy = destroyedBy;
    }

    @Override
    public final boolean isSoulIntact(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SOUL_FRAGMENT_INTACT.get(), true);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isSoulIntact(stack);
    }

    @Override
    public final InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.DARK_ARTS)) {
            return InteractionResult.FAIL;
        }
        return onDarkArtsUse(level, player, hand);
    }

    /** Right-click behaviour once the Dark Arts module is known to be on. Passive by default. */
    protected InteractionResult onDarkArtsUse(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    /** The standard bound-or-destroyed pair. */
    protected void appendSoulState(ItemStack stack, Consumer<Component> tooltipAdder) {
        tooltipAdder.accept(isSoulIntact(stack) ? soulBound("") : soulDestroyed());
    }

    /**
     * The bound line, with an optional trailing clause — the locket's fragment is the one that
     * "wears heavily on the bearer".
     */
    protected static Component soulBound(String extra) {
        return Component.literal("[Soul fragment bound" + extra + "]")
                .withStyle(ChatFormatting.DARK_PURPLE);
    }

    /** The struck-through epitaph naming whatever ended this horcrux. */
    protected Component soulDestroyed() {
        return Component.literal("[Destroyed — " + destroyedBy + "]")
                .withStyle(ChatFormatting.STRIKETHROUGH, ChatFormatting.DARK_RED);
    }
}
