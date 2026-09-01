package at.koopro.wizardsandbeasts.brew.silver;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Pouring pure silver over a blade.
 *
 * <p>The gesture is the one the mod already uses for applying one held thing to another: the bottle
 * in one hand, the weapon in the other, right-click — the same idiom as ink onto a Ministry licence.
 *
 * <p>Lives in an event handler rather than inside {@code BrewItem} on purpose. {@code BrewItem} is
 * the single generic potion for every datapack brew there will ever be, and teaching it that one
 * particular brew id does something no other brew does would be the first crack in that.
 *
 * <h2>What counts as pure silver</h2>
 * Not a flag and not a name: a brew is pure silver when some other loaded brew names it as its
 * {@code silverVariant}. That is derived from the data rather than declared twice, so a datapack that
 * adds a silver brew gets a working silvering agent in the same file, and the two can never disagree
 * about which brew is which.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class SilveringHandler {

    private SilveringHandler() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack bottle = event.getItemStack();
        if (!isPureSilverBottle(bottle)) {
            return;
        }
        InteractionHand other = event.getHand() == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        ItemStack weapon = player.getItemInHand(other);
        if (weapon.isEmpty()) {
            return;
        }

        // From here the click is ours: the player is plainly trying to silver something, so cancel
        // the drink rather than letting a rare bottle be swallowed by a mis-click.
        event.setCanceled(true);

        if (SilveredWeapons.isSilvered(weapon)) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("brew.wizards_and_beasts.silver.already")
                            .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!SilveredWeapons.canBeSilvered(weapon)) {
            PlayerFeedback.actionBar(player,
                    Component.translatable("brew.wizards_and_beasts.silver.cannot",
                            weapon.getHoverName()).withStyle(ChatFormatting.GRAY));
            return;
        }

        SilveredWeapons.silver(weapon);
        bottle.shrink(1);
        if (!player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
            player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.2f);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.3, 0.3, 0.3, 0.02);
        }
        PlayerFeedback.actionBar(player,
                Component.translatable("brew.wizards_and_beasts.silver.applied", weapon.getHoverName())
                        .withStyle(ChatFormatting.AQUA));
    }

    /** Whether this stack is a bottle of a brew that some silver-based brew refines into. */
    public static boolean isPureSilverBottle(ItemStack stack) {
        if (!stack.is(ConsumableItemRegistry.BREW.get())) {
            return false;
        }
        String brewId = stack.get(ModDataComponents.BREW_ID.get());
        return brewId != null && isPureSilverBrew(brewId);
    }

    /**
     * Whether {@code brewId} is the refined end of some silver-based brew.
     *
     * <p>A linear scan of the brew registry, which holds a handful of entries and is only consulted
     * on a right-click with a potion in hand. Deriving it beats a second declaration that could rot.
     */
    public static boolean isPureSilverBrew(@Nullable String brewId) {
        if (brewId == null) {
            return false;
        }
        for (Brew brew : Brews.all()) {
            if (brew.isSilverBased() && brewId.equals(brew.silverVariant())) {
                return true;
            }
        }
        return false;
    }
}
