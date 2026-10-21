package at.koopro.wizardsandbeasts.broom.fitting;

import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.item.broom.BroomFittingItem;
import at.koopro.wizardsandbeasts.item.broom.BroomItem;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.util.InventoryHandover;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Fitting, stripping and dyeing a broom — the one place the stack components are written, for both ways a broom can
 * be worked on (in the other hand, or parked in the world, through {@code BroomEntity.interact}).
 *
 * <p>Server only. Nothing here trusts a client: the broom, the fitting and the player's inventory are all read on
 * the server, and the item that goes on the broom is taken in the same call that writes the component, so there is
 * no window in which a fitting is both on the broom and in a hand.
 */
@NullMarked
public final class BroomWorkshop {

    private BroomWorkshop() {}

    /** The fittings on {@code broom}; {@link BroomFittings#NONE} when it carries none. */
    public static BroomFittings fittingsOf(ItemStack broom) {
        return broom.getOrDefault(ModDataComponents.BROOM_FITTINGS.get(), BroomFittings.NONE);
    }

    /**
     * Puts the fitting in {@code fittingStack} on {@code broom}, taking one from the stack. A fitting already in that
     * slot comes off and is handed back — replacing is not destroying.
     *
     * @return whether the broom changed
     */
    public static boolean fit(ServerPlayer player, ItemStack broom, ItemStack fittingStack) {
        if (!(broom.getItem() instanceof BroomItem) || !(fittingStack.getItem() instanceof BroomFittingItem item)) {
            return false;
        }
        BroomFitting fitting = item.fitting();
        BroomDefinition definition = BroomItem.resolveDefinition(broom);
        BroomFittings current = fittingsOf(broom);
        BroomFittings.Refusal refusal = current.check(fitting, definition.tier());
        if (refusal != BroomFittings.Refusal.OK) {
            PlayerFeedback.refuse(player, Component.translatable(fitting.translationKey()),
                    refusalReason(refusal, definition));
            return false;
        }
        @Nullable BroomFitting replaced = current.in(fitting.slot()).orElse(null);
        broom.set(ModDataComponents.BROOM_FITTINGS.get(), current.with(fitting));
        if (!player.getAbilities().instabuild) {
            fittingStack.shrink(1);
        }
        if (replaced != null) {
            InventoryHandover.giveOrDrop(player, BroomFittingItem.stackOf(replaced));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS,
                0.6f, 1.3f);
        PlayerFeedback.actionBar(player, Component.translatable("broom.wizards_and_beasts.fitting.fitted",
                Component.translatable(fitting.translationKey())).withStyle(ChatFormatting.GREEN));
        return true;
    }

    /**
     * Takes every fitting off {@code broom} and hands them back, and clears its trail dye.
     *
     * @return whether anything came off
     */
    public static boolean strip(ServerPlayer player, ItemStack broom) {
        BroomFittings current = fittingsOf(broom);
        boolean dyed = broom.has(ModDataComponents.BROOM_TRAIL_DYE.get());
        if (current.isEmpty() && !dyed) {
            PlayerFeedback.actionBar(player, Component.translatable("broom.wizards_and_beasts.fitting.nothing_to_strip"));
            return false;
        }
        List<BroomFitting> removed = new ArrayList<>(current.all());
        broom.remove(ModDataComponents.BROOM_FITTINGS.get());
        broom.remove(ModDataComponents.BROOM_TRAIL_DYE.get());
        for (BroomFitting fitting : removed) {
            InventoryHandover.giveOrDrop(player, BroomFittingItem.stackOf(fitting));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS,
                0.6f, 1.2f);
        PlayerFeedback.actionBar(player, Component.translatable("broom.wizards_and_beasts.fitting.stripped",
                removed.size()));
        return true;
    }

    /**
     * Dyes {@code broom}'s slipstream with {@code colour}, taking one dye. Purely cosmetic: the trail is drawn on the
     * clients and nothing about the flight reads it.
     */
    public static boolean dye(ServerPlayer player, ItemStack broom, ItemStack dyeStack, DyeColor colour) {
        int rgb = colour.getTextureDiffuseColor() & 0x00FFFFFF;
        Integer current = broom.get(ModDataComponents.BROOM_TRAIL_DYE.get());
        if (current != null && current == rgb) {
            return false;
        }
        broom.set(ModDataComponents.BROOM_TRAIL_DYE.get(), rgb);
        if (!player.getAbilities().instabuild) {
            dyeStack.shrink(1);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        PlayerFeedback.actionBar(player, Component.translatable("broom.wizards_and_beasts.fitting.dyed",
                Component.translatable("color.minecraft." + colour.getName())));
        return true;
    }

    private static Component refusalReason(BroomFittings.Refusal refusal, BroomDefinition definition) {
        return switch (refusal) {
            case TIER_TOO_LOW -> Component.translatable("broom.wizards_and_beasts.fitting.tier_too_low",
                    definition.displayName());
            case NO_ROOM -> Component.translatable("broom.wizards_and_beasts.fitting.no_room",
                    definition.displayName(), BroomFitting.capacity(definition.tier()));
            case ALREADY_FITTED -> Component.translatable("broom.wizards_and_beasts.fitting.already");
            case OK -> Component.empty();
        };
    }
}
