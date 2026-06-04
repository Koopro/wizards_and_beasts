package at.koopro.wizardsandbeasts.item.trinket;

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

public class NewtsCaseItem extends Item {

    public NewtsCaseItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.literal("Muggle-worthy switch on the lock.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
        boolean muggleWorthy = stack.getOrDefault(ModDataComponents.NEWTS_CASE_MUGGLE_WORTHY.get(), true);
        if (muggleWorthy) {
            tooltipAdder.accept(Component.literal("[Muggle-worthy mode ON]")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltipAdder.accept(Component.literal("[Interior accessible]")
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltipAdder.accept(Component.literal("Contains entire ecosystems.")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_PURPLE));
        tooltipAdder.accept(Component.literal("Sneak-use to toggle the lock").withStyle(ChatFormatting.BLUE));
        UUID pocketId = stack.get(ModDataComponents.POCKET_ID.get());
        if (pocketId != null) {
            tooltipAdder.accept(Component.literal("Habitat: " + pocketId.toString().substring(0, 8))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ModuleManager.isEnabled(Module.POCKET_DIMENSIONS)) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        // Inside the habitat: a right-click on the case latches it shut and returns you.
        if (serverPlayer.level().dimension().equals(ModDimensions.EXTENSION_REALM)) {
            ExtensionCharmService.exitPocket(serverPlayer);
            return InteractionResult.SUCCESS;
        }

        boolean muggleWorthy = stack.getOrDefault(ModDataComponents.NEWTS_CASE_MUGGLE_WORTHY.get(), true);

        // Sneak-use toggles the Muggle-Worthy switch both ways (re-lock or open without entering).
        if (serverPlayer.isShiftKeyDown()) {
            boolean next = !muggleWorthy;
            stack.set(ModDataComponents.NEWTS_CASE_MUGGLE_WORTHY.get(), next);
            ((ServerLevel) level).playSound(null, serverPlayer.blockPosition(),
                    SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 1.0f, 1.0f);
            serverPlayer.displayClientMessage(
                    next ? Component.literal("Muggle-Worthy switch engaged.").withStyle(ChatFormatting.DARK_GREEN)
                         : Component.literal("The lock clicks over. The case is open.").withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.SUCCESS;
        }

        if (muggleWorthy) {
            // First flip the Muggle-Worthy switch — the case is a mundane suitcase until then.
            stack.set(ModDataComponents.NEWTS_CASE_MUGGLE_WORTHY.get(), false);
            serverPlayer.displayClientMessage(
                    Component.literal("The lock clicks over. The case is open.").withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.SUCCESS;
        }

        // Interior accessible: descend into the Scamander sanctuary habitat.
        UUID caseId = ensureCaseId(stack);
        TrunkRecord record = ExtensionCharmService.getOrCreatePocket(
                serverPlayer, caseId,
                TrunkArchetype.SCAMANDER_SANCTUARY,
                "newts_case",
                TrunkAccessMode.SEALED,
                false, false,
                TrunkTier.TIER_3);
        stack.set(ModDataComponents.POCKET_ID.get(), record.pocketId());

        TrunkRegistryData data = TrunkRegistryData.get((ServerLevel) level);
        if (data.isImpounded(record.pocketId())) {
            serverPlayer.displayClientMessage(
                    Component.literal("This case has been impounded by the Ministry of Magic.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }

        ExtensionCharmService.enterPocket(serverPlayer, record);
        return InteractionResult.SUCCESS;
    }

    private static UUID ensureCaseId(ItemStack stack) {
        UUID existing = stack.get(ModDataComponents.POCKET_CASE_ID.get());
        if (existing != null) return existing;
        UUID created = UUID.randomUUID();
        stack.set(ModDataComponents.POCKET_CASE_ID.get(), created);
        return created;
    }
}
