package at.koopro.wizardsandbeasts.demiguise;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;

/**
 * Demiguise Weave: armour trimmed with a Demiguise's hair, which takes the wearer's cue from a
 * crouch.
 *
 * <p><b>A real vanilla armour trim, not a lookalike.</b> The Weave is
 * {@link Demiguise#WEAVE_MATERIAL}, applied at a smithing table with any trim template, and it
 * renders like every other trim. That means it costs the wearer their trim slot — which is the price
 * of the ability, and the reason it needs no other cost.
 *
 * <h2>Crouch is the trigger, and the tell</h2>
 * Sneaking is a gesture a player makes constantly, so this fires on the <em>transition</em> into a
 * crouch rather than while crouched — otherwise it would burn its cooldown the first time somebody
 * ducked under a fence, and re-fire forever while they stayed down. It also means the ability
 * announces itself: everyone nearby sees you crouch a moment before you disappear.
 *
 * <h2>No camouflage</h2>
 * Plain invisibility, deliberately. The hair itself — spent, gone, ninety seconds — is the thing
 * that buys camouflage. A trim that granted the stronger effect for free every minute would make the
 * hair pointless to ever use directly.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class DemiguiseWeaveHandler {

    /** Game time each player's Weave may next answer a crouch. */
    private static final PlayerScopedState<Long> NEXT_READY_TICK =
            PlayerScopedState.create("demiguise_weave_ready");
    /** Whether the player was crouching last tick, so only the transition fires. */
    private static final PlayerScopedState<Boolean> WAS_CROUCHING =
            PlayerScopedState.create("demiguise_weave_crouch");

    private DemiguiseWeaveHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean crouching = player.isShiftKeyDown();
        boolean was = Boolean.TRUE.equals(WAS_CROUCHING.get(player));
        WAS_CROUCHING.put(player, crouching);

        if (!crouching || was) {
            return;
        }
        if (weavePieces(player) < Demiguise.WEAVE_PIECES_REQUIRED) {
            return;
        }

        long now = player.level().getGameTime();
        Long ready = NEXT_READY_TICK.get(player);
        if (ready != null && now < ready) {
            return;
        }
        // Already hidden by something stronger — do not spend the cooldown overwriting it.
        if (player.hasEffect(MobEffects.INVISIBILITY)) {
            return;
        }

        NEXT_READY_TICK.put(player, now + Demiguise.WEAVE_COOLDOWN_TICKS);
        Demiguise.conceal(player, Demiguise.WEAVE_INVISIBILITY_TICKS, 0);
        Demiguise.vanishEffects(player);
        PlayerFeedback.actionBar(player,
                Component.translatable("armor.wizards_and_beasts.demiguise_weave.triggered",
                        Demiguise.WEAVE_INVISIBILITY_TICKS / 20).withStyle(ChatFormatting.GRAY));
    }

    /** How many worn armour pieces carry the Weave. */
    public static int weavePieces(ServerPlayer player) {
        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            if (isWeave(player.getItemBySlot(slot))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether a stack is trimmed in Demiguise.
     *
     * <p>Compares the material's registry key rather than its appearance: the Weave borrows quartz's
     * pale palette so it needs no new art, and a colour comparison would have made every
     * quartz-trimmed helmet in the world into Demiguise Weave.
     */
    public static boolean isWeave(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ArmorTrim trim = stack.get(DataComponents.TRIM);
        return trim != null
                && trim.material().is(Demiguise.WEAVE_MATERIAL);
    }
}
