package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * The two licence gates that are not attached to an item or an entity of their own: walking into the
 * Ministry, and dealing with a merchant who has controlled goods on the counter.
 *
 * <p><b>Ministry premises.</b> Detected by block density
 * ({@link MinistryArea}) rather than by a region, because this mod generates no Ministry — see that
 * class. Being inside without papers is not blocked; it files
 * {@link MagicalOffence#MINISTRY_TRESPASS}, the smallest fine on the books, on a cooldown. Nothing
 * shoves the player, which matters more than it sounds: the detection is a block palette, so a wizard
 * who happens to like Ministry marble at home would otherwise be unable to stand in their own house.
 *
 * <p><b>Controlled ingredients.</b> A merchant with a restricted ingredient in stock will not deal
 * with an unlicensed wizard at all, rather than showing them a filtered list. Filtering the offer list
 * per viewer means mutating a shared {@code MerchantOffers} — the same object every other customer
 * sees, with no hook to put it back — and a shop that quietly forgets its stock for everybody is a
 * worse bug than a shopkeeper who says no. Refusal is total but instantly reversible: come back with
 * papers and the counter opens.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class MinistryLicenceEvents {

    /** Last game time each player was billed for being where they should not be. */
    private static final PlayerScopedState<Long> LAST_TRESPASS_TICK = PlayerScopedState.create("licence_trespass_tick");
    /** Whether a player was inside on the previous check, so entering can be announced once. */
    private static final PlayerScopedState<Boolean> WAS_INSIDE = PlayerScopedState.create("licence_inside_ministry");

    private MinistryLicenceEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !TraceService.isActive()) {
            return;
        }
        if (player.tickCount % LicenceRules.AREA_CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        boolean inside = MinistryArea.isInside(player.level(), player.blockPosition());
        boolean wasInside = Boolean.TRUE.equals(WAS_INSIDE.get(player));
        WAS_INSIDE.put(player, inside);

        if (!inside) {
            return;
        }
        if (MinistryLicences.has(player, LicenseType.MINISTRY_ACCESS)
                || MinistryLicences.has(player, LicenseType.AUROR_TRAINEE)) {
            // An Auror trainee has the run of the building; so does anyone with plain access.
            return;
        }

        if (!wasInside) {
            PlayerFeedback.actionBar(player,
                    Component.translatable(MinistryLicences.REFUSAL_KEY).copy().withStyle(ChatFormatting.RED));
        }

        long now = player.level().getGameTime();
        Long last = LAST_TRESPASS_TICK.get(player);
        if (last != null && now - last < LicenceRules.TRESPASS_COOLDOWN_TICKS) {
            return;
        }
        LAST_TRESPASS_TICK.put(player, now);
        TraceService.report(player, MagicalOffence.MINISTRY_TRESPASS);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !TraceService.isActive()) {
            return;
        }
        if (!(event.getTarget() instanceof Merchant merchant)) {
            return;
        }
        if (!stocksRestrictedGoods(merchant)) {
            return;
        }
        if (MinistryLicenceGate.admit(player, LicenseType.RESTRICTED_SUBSTANCES)) {
            return;
        }
        event.setCanceled(true);
    }

    /** True when any offer on the counter results in a controlled ingredient. */
    private static boolean stocksRestrictedGoods(Merchant merchant) {
        for (MerchantOffer offer : merchant.getOffers()) {
            if (isRestricted(offer.getResult())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRestricted(ItemStack stack) {
        return !stack.isEmpty() && stack.is(MinistryLicenceTags.RESTRICTED_INGREDIENT);
    }
}
