package at.koopro.wizardsandbeasts.item.trunk;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModDimensions;
import at.koopro.wizardsandbeasts.trunk.ExtensionCharmService;
import at.koopro.wizardsandbeasts.trunk.TrunkAccessMode;
import at.koopro.wizardsandbeasts.trunk.TrunkArchetype;
import at.koopro.wizardsandbeasts.trunk.TrunkRecord;
import at.koopro.wizardsandbeasts.trunk.TrunkRegistryData;
import at.koopro.wizardsandbeasts.trunk.TrunkTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.function.Consumer;

public class MoodysTrunkItem extends Item {

    /** Seven locks, seven compartments — the seventh holds the pit. */
    private static final int LOCK_COUNT = 7;

    public MoodysTrunkItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Seven locks. Seven compartments.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("The seventh contains a man-sized pit.")
                .withStyle(ChatFormatting.DARK_RED));
        int lock = clampLock(stack.getOrDefault(ModDataComponents.MOODYS_TRUNK_ACTIVE_LOCK.get(), 1));
        tooltipAdder.accept(Component.literal("Current lock: " + lock)
                .withStyle(ChatFormatting.GOLD));
        tooltipAdder.accept(Component.literal("Sneak-use to turn the key").withStyle(ChatFormatting.BLUE));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        // Inside any compartment: a right-click climbs back out.
        if (serverPlayer.level().dimension().equals(ModDimensions.EXTENSION_REALM)) {
            ExtensionCharmService.exitPocket(serverPlayer);
            return InteractionResult.SUCCESS;
        }

        int activeLock = clampLock(stack.getOrDefault(ModDataComponents.MOODYS_TRUNK_ACTIVE_LOCK.get(), 1));

        // Shift+right-click turns the key to the next lock (1..7, wraps).
        if (serverPlayer.isShiftKeyDown()) {
            int next = activeLock >= LOCK_COUNT ? 1 : activeLock + 1;
            stack.set(ModDataComponents.MOODYS_TRUNK_ACTIVE_LOCK.get(), next);
            ((ServerLevel) level).playSound(null, serverPlayer.blockPosition(),
                    SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 1.0f, 1.0f);
            serverPlayer.displayClientMessage(
                    Component.literal("Lock " + next + " selected.").withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.SUCCESS;
        }

        // Enter the sub-pocket bound to the current lock. Each lock is its own compartment.
        UUID baseId = ensureBaseId(stack);
        UUID caseId = lockCaseId(baseId, activeLock);
        TrunkArchetype archetype = activeLock == LOCK_COUNT
                ? TrunkArchetype.SAFEHOUSE          // seventh: the man-sized pit
                : TrunkArchetype.MINISTRY_STANDARD; // compartments one through six
        TrunkRecord record = ExtensionCharmService.getOrCreatePocket(
                serverPlayer, caseId,
                archetype,
                "moodys_lock_" + activeLock,
                TrunkAccessMode.SEALED,
                false, false,
                TrunkTier.TIER_1);

        TrunkRegistryData data = TrunkRegistryData.get((ServerLevel) level);
        if (data.isImpounded(record.pocketId())) {
            serverPlayer.displayClientMessage(
                    Component.literal("This compartment has been impounded by the Ministry of Magic.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        ExtensionCharmService.enterPocket(serverPlayer, record);
        return InteractionResult.SUCCESS;
    }

    private static int clampLock(int lock) {
        if (lock < 1) return 1;
        if (lock > LOCK_COUNT) return LOCK_COUNT;
        return lock;
    }

    private static UUID ensureBaseId(ItemStack stack) {
        UUID existing = stack.get(ModDataComponents.MOODYS_TRUNK_BASE_ID.get());
        if (existing != null) return existing;
        UUID created = UUID.randomUUID();
        stack.set(ModDataComponents.MOODYS_TRUNK_BASE_ID.get(), created);
        return created;
    }

    /** Derives a stable per-lock case id from the trunk's base id. */
    private static UUID lockCaseId(UUID base, int lock) {
        return new UUID(base.getMostSignificantBits(), base.getLeastSignificantBits() ^ (long) lock);
    }
}
