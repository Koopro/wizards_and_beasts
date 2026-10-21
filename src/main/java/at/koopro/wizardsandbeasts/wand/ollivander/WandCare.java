package at.koopro.wizardsandbeasts.wand.ollivander;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.currency.billing.GringottsBilling;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * Ollivander tending a worn or damaged wand: a paid service, and the mod's answer to "what is money for" that is not
 * a spell (documentation/ECONOMY.md §5).
 *
 * <p>Wands wear only from explosions ({@code WandAllegianceRules#integrityAfterExplosion}), a damaged wand casts
 * weaker, and a snapped one backfires on every cast and can be mended only by the Elder Wand. Ollivander restores
 * anything short of snapped; a snapped wand he cannot help — "a wand that has suffered this degree of damage cannot
 * be repaired by any means that I know of" (<i>Deathly Hallows</i> ch. 24) — so the trial opens instead.
 *
 * <p>Show him a worn wand and he names a price; show it again within {@link #QUOTE_TICKS} to pay from the vault. The
 * second showing must be the same wand in the same state at the same price, so a quote cannot be spent on another
 * wand. Free with the Gringotts module off or a price of 0. Nothing here is routine maintenance: a wand that never
 * meets an explosion never needs him.
 */
@NullMarked
public final class WandCare {

    /** How long a quote stands: five seconds. */
    public static final long QUOTE_TICKS = 100L;

    /** What a wand in a given state gets from Ollivander. */
    public enum Verdict {
        /** Nothing to tend. */
        SOUND,
        /** Worn or damaged: he can restore it. */
        TENDABLE,
        /** Snapped: beyond him. */
        SNAPPED
    }

    /** A standing quote: which wand, in what state, at what price, until when. */
    private record Quote(ItemStack wand, float integrity, long price, long expiresAt) {}

    private static final PlayerScopedState<Quote> QUOTES = PlayerScopedState.create("wand_care_quote");

    private WandCare() {}

    /** What Ollivander can do for a wand at {@code integrity}. Pure. */
    public static Verdict verdict(float integrity) {
        if (integrity >= 1.0f) {
            return Verdict.SOUND;
        }
        return WandAllegianceRules.condition(integrity) == WandAllegianceRules.Condition.BROKEN
                ? Verdict.SNAPPED : Verdict.TENDABLE;
    }

    /**
     * The price of tending a wand at {@code integrity} when a wand on the brink of snapping costs {@code fullPrice}.
     * Scales with the wear, rounded up to a whole Sickle (a wandmaker does not count Knuts); 0 when there is nothing
     * to tend, no price set, or the wand is snapped. Pure.
     */
    public static long price(float integrity, long fullPrice) {
        if (fullPrice <= 0 || verdict(integrity) != Verdict.TENDABLE) {
            return 0L;
        }
        double tendable = 1.0 - WandAllegianceRules.BROKEN_AT;
        double share = Math.min(1.0, Math.max(0.0, (1.0 - integrity) / tendable));
        long raw = (long) Math.ceil(fullPrice * share);
        long sickle = CurrencyHelper.KNUTS_PER_SICKLE;
        return Math.max(sickle, (raw + sickle - 1) / sickle * sickle);
    }

    /** What this player would pay for {@code wand} now: 0 while Gringotts is closed. */
    public static long priceFor(ItemStack wand) {
        if (!ModuleManager.isEnabled(Module.GRINGOTTS)) {
            return 0L;
        }
        return price(WandComponents.getIntegrity(wand), Config.wandCarePriceKnuts);
    }

    /**
     * The player showed Ollivander whatever is in their main hand.
     *
     * @return whether he dealt with it (a quote, a tending, a refusal to pay); {@code false} sends the player on to
     *     the wand trial — no wand, a sound wand, or a snapped one
     */
    public static boolean offer(ServerPlayer player) {
        ItemStack wand = player.getMainHandItem();
        if (!(wand.getItem() instanceof WandItem)) {
            QUOTES.remove(player);
            return false;
        }
        float integrity = WandComponents.getIntegrity(wand);
        switch (verdict(integrity)) {
            case SOUND -> {
                QUOTES.remove(player);
                return false;
            }
            case SNAPPED -> {
                QUOTES.remove(player);
                PlayerFeedback.actionBar(player, Component.translatable("wandcraft.care.snapped")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                return false;
            }
            case TENDABLE -> {
                return tend(player, wand, integrity);
            }
        }
        return false;
    }

    private static boolean tend(ServerPlayer player, ItemStack wand, float integrity) {
        long price = priceFor(wand);
        long now = player.level().getGameTime();
        if (price > 0) {
            Quote quote = QUOTES.get(player);
            boolean confirmed = quote != null && now <= quote.expiresAt() && quote.price() == price
                    && quote.integrity() == integrity && ItemStack.isSameItemSameComponents(quote.wand(), wand);
            if (!confirmed) {
                QUOTES.put(player, new Quote(wand.copy(), integrity, price, now + QUOTE_TICKS));
                PlayerFeedback.actionBar(player, Component.translatable("wandcraft.care.quote",
                        Component.translatable(conditionKey(integrity)), CurrencyHelper.formatFromKnuts(price))
                        .withStyle(ChatFormatting.GOLD));
                return true;
            }
            QUOTES.remove(player);
            if (!GringottsBilling.charge(player, MoneyFlow.WAND_CARE, price)) {
                PlayerFeedback.actionBar(player, Component.translatable("wandcraft.care.short",
                        CurrencyHelper.formatFromKnuts(price)).withStyle(ChatFormatting.RED));
                return true;
            }
        }
        wand.set(WandComponents.WAND_INTEGRITY.get(), 1.0f);
        PlayerFeedback.actionBar(player, (price > 0
                ? Component.translatable("wandcraft.care.done", CurrencyHelper.formatFromKnuts(price))
                : Component.translatable("wandcraft.care.done_free")).withStyle(ChatFormatting.GREEN));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8f, 1.2f);
        return true;
    }

    private static String conditionKey(float integrity) {
        return WandAllegianceRules.condition(integrity) == WandAllegianceRules.Condition.DAMAGED
                ? "wandcraft.care.condition.damaged" : "wandcraft.care.condition.worn";
    }
}
