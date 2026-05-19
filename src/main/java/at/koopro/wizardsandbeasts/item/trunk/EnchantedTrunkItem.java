package at.koopro.wizardsandbeasts.item.trunk;

import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.trunk.ExtensionCharmService;
import at.koopro.wizardsandbeasts.trunk.TrunkAccessMode;
import at.koopro.wizardsandbeasts.trunk.TrunkArchetype;
import at.koopro.wizardsandbeasts.trunk.TrunkRecord;
import at.koopro.wizardsandbeasts.trunk.TrunkRegistryData;
import at.koopro.wizardsandbeasts.trunk.TrunkTier;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
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

public class EnchantedTrunkItem extends Item {
    private final TrunkTier tier;

    public EnchantedTrunkItem(TrunkTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public TrunkTier tier() { return tier; }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        if (!ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        // Shift+right-click outside the realm: cycle item mode (muggle-worthy → locked → normal).
        if (serverPlayer.isShiftKeyDown() && !serverPlayer.level().dimension().equals(ModDimensions.EXTENSION_REALM)) {
            cycleItemMode(stack, serverPlayer, (ServerLevel) level);
            return InteractionResult.SUCCESS;
        }

        UUID caseId = ensureCaseId(stack);
        TrunkArchetype archetype = stack.getOrDefault(ModDataComponents.POCKET_ARCHETYPE.get(), TrunkArchetype.FIELD_CAMP);
        TrunkAccessMode accessMode = stack.getOrDefault(ModDataComponents.POCKET_ACCESS_MODE.get(), TrunkAccessMode.SEALED);
        boolean muggleWorthy = stack.getOrDefault(ModDataComponents.POCKET_MUGGLE_WORTHY.get(), false);
        boolean lockedExternally = stack.getOrDefault(ModDataComponents.POCKET_LOCKED_EXTERNALLY.get(), false);
        String templateId = stack.getOrDefault(ModDataComponents.POCKET_TEMPLATE_ID.get(), "blank_shell");

        TrunkRecord record = ExtensionCharmService.getOrCreatePocket(
                serverPlayer, caseId, archetype, templateId, accessMode, muggleWorthy, lockedExternally, tier);
        stack.set(ModDataComponents.POCKET_ID.get(), record.pocketId());

        if (serverPlayer.level().dimension().equals(ModDimensions.EXTENSION_REALM)) {
            // Exit path — check external lock first.
            if (record.lockedExternally()) {
                serverPlayer.displayClientMessage(
                        Component.literal("The case has been locked from outside.").withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }
            ExtensionCharmService.exitPocket(serverPlayer);
        } else {
            // Priority 1: impound check.
            TrunkRegistryData data = TrunkRegistryData.get((ServerLevel) level);
            if (data.isImpounded(record.pocketId())) {
                serverPlayer.displayClientMessage(
                        Component.literal("This case has been impounded by the Ministry of Magic.").withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }
            // Priority 2: muggle-worthy — blocks non-owner entry.
            if (record.muggleWorthy() && !record.owner().equals(serverPlayer.getUUID())) {
                serverPlayer.displayClientMessage(
                        Component.literal("The case appears to contain only travel supplies."), true);
                return InteractionResult.SUCCESS;
            }
            ExtensionCharmService.enterPocket(serverPlayer, record);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Cycles the item through three states:
     * NORMAL → MUGGLE_WORTHY → LOCKED_EXTERNALLY → NORMAL.
     * Syncs lockedExternally to TrunkRecord immediately so players inside see the change.
     */
    private static void cycleItemMode(ItemStack stack, ServerPlayer player, ServerLevel level) {
        boolean isMuggle = stack.getOrDefault(ModDataComponents.POCKET_MUGGLE_WORTHY.get(), false);
        boolean isLocked = stack.getOrDefault(ModDataComponents.POCKET_LOCKED_EXTERNALLY.get(), false);

        UUID caseId = stack.get(ModDataComponents.POCKET_CASE_ID.get());
        TrunkRegistryData data = caseId != null ? TrunkRegistryData.get(level) : null;

        if (!isMuggle && !isLocked) {
            // NORMAL → MUGGLE_WORTHY
            stack.set(ModDataComponents.POCKET_MUGGLE_WORTHY.get(), true);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Leather Suitcase"));
            if (data != null && caseId != null) {
                data.getCasePocket(caseId).ifPresent(r ->
                        data.updateRecord(r.pocketId(), rec -> rec.withMuggleWorthy(true)));
            }
            player.displayClientMessage(
                    Component.literal("Muggle-Worthy switch engaged.").withStyle(ChatFormatting.DARK_GREEN), true);

        } else if (isMuggle && !isLocked) {
            // MUGGLE_WORTHY → LOCKED_EXTERNALLY
            stack.remove(ModDataComponents.POCKET_MUGGLE_WORTHY.get());
            stack.remove(DataComponents.CUSTOM_NAME);
            stack.set(ModDataComponents.POCKET_LOCKED_EXTERNALLY.get(), true);
            stack.set(ModDataComponents.POCKET_LATCH_SECURED.get(), true);
            if (data != null && caseId != null) {
                data.getCasePocket(caseId).ifPresent(r ->
                        data.updateRecord(r.pocketId(), rec -> rec.withMuggleWorthy(false).withLockedExternally(true)));
            }
            player.displayClientMessage(
                    Component.literal("Case locked from outside.").withStyle(ChatFormatting.RED), true);

        } else {
            // LOCKED or any other → NORMAL
            stack.remove(ModDataComponents.POCKET_MUGGLE_WORTHY.get());
            stack.remove(DataComponents.CUSTOM_NAME);
            stack.remove(ModDataComponents.POCKET_LOCKED_EXTERNALLY.get());
            if (data != null && caseId != null) {
                data.getCasePocket(caseId).ifPresent(r ->
                        data.updateRecord(r.pocketId(), rec -> rec.withMuggleWorthy(false).withLockedExternally(false)));
            }
            player.displayClientMessage(
                    Component.literal("Case unlocked.").withStyle(ChatFormatting.GREEN), true);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static UUID ensureCaseId(ItemStack stack) {
        UUID existing = stack.get(ModDataComponents.POCKET_CASE_ID.get());
        if (existing != null) return existing;
        UUID created = UUID.randomUUID();
        stack.set(ModDataComponents.POCKET_CASE_ID.get(), created);
        return created;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        // In Muggle-Worthy mode show nothing — the case is a mundane suitcase.
        if (stack.getOrDefault(ModDataComponents.POCKET_MUGGLE_WORTHY.get(), false)) return;

        super.appendHoverText(stack, context, display, tooltipAdder, flag);
        String lockText = tier.lockCount() == 1 ? "1 lock" : tier.lockCount() + " locks";
        tooltipAdder.accept(Component.literal(lockText).withStyle(ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal(
                "Ministry-approved. Interior dimensions expanded under license ref. MoM/ExtC/1847.")
                .withStyle(ChatFormatting.DARK_PURPLE));
        TrunkArchetype archetype = stack.getOrDefault(ModDataComponents.POCKET_ARCHETYPE.get(), TrunkArchetype.FIELD_CAMP);
        TrunkAccessMode accessMode = stack.getOrDefault(ModDataComponents.POCKET_ACCESS_MODE.get(), TrunkAccessMode.SEALED);
        tooltipAdder.accept(Component.translatable(archetype.getTranslationKey()).withStyle(ChatFormatting.DARK_AQUA));
        tooltipAdder.accept(Component.translatable(accessMode.getTranslationKey()).withStyle(ChatFormatting.GRAY));
        if (stack.getOrDefault(ModDataComponents.POCKET_LOCKED_EXTERNALLY.get(), false)) {
            tooltipAdder.accept(Component.literal("[Locked]").withStyle(ChatFormatting.RED));
        }
        if (!stack.getOrDefault(ModDataComponents.POCKET_LATCH_SECURED.get(), true)) {
            tooltipAdder.accept(Component.literal("[Latch open]").withStyle(ChatFormatting.YELLOW));
        }
        UUID pocketId = stack.get(ModDataComponents.POCKET_ID.get());
        if (pocketId != null) {
            tooltipAdder.accept(Component.literal("Pocket: " + pocketId.toString().substring(0, 8)).withStyle(ChatFormatting.GOLD));
        } else {
            tooltipAdder.accept(Component.literal("Unbound case").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltipAdder.accept(Component.literal("Sneak-use to toggle mode").withStyle(ChatFormatting.BLUE));
    }
}
