package at.koopro.wizardsandbeasts.item.wizarding;

import at.koopro.wizardsandbeasts.pocket.PocketAccessMode;
import at.koopro.wizardsandbeasts.pocket.PocketArchetype;
import at.koopro.wizardsandbeasts.pocket.PocketDimensionService;
import at.koopro.wizardsandbeasts.pocket.PocketRecord;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.registry.ModDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

public class PocketCaseItem extends Item {
    public PocketCaseItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer)) {
            return InteractionResult.PASS;
        }
        ServerPlayer serverPlayer = (ServerPlayer) player;

        if (serverPlayer.isShiftKeyDown()) {
            cycleCaseMode(stack, serverPlayer);
            return InteractionResult.SUCCESS;
        }

        UUID caseId = ensureCaseId(stack);
        PocketArchetype archetype = stack.getOrDefault(ModDataComponents.POCKET_ARCHETYPE.get(), PocketArchetype.CUSTOM_PLAYER_TEMPLATE);
        PocketAccessMode accessMode = stack.getOrDefault(ModDataComponents.POCKET_ACCESS_MODE.get(), PocketAccessMode.PRIVATE);
        String templateId = stack.getOrDefault(ModDataComponents.POCKET_TEMPLATE_ID.get(), "blank_shell");
        PocketRecord record = PocketDimensionService.getOrCreatePocket(serverPlayer, caseId, archetype, templateId, accessMode);
        stack.set(ModDataComponents.POCKET_ID.get(), record.pocketId());

        if (serverPlayer.level().dimension().equals(ModDimensions.POCKET_REALM)) {
            PocketDimensionService.exitPocket(serverPlayer);
        } else {
            PocketDimensionService.enterPocket(serverPlayer, record);
        }
        return InteractionResult.SUCCESS;
    }

    private static void cycleCaseMode(ItemStack stack, ServerPlayer player) {
        PocketArchetype nextArchetype = stack.getOrDefault(
                ModDataComponents.POCKET_ARCHETYPE.get(),
                PocketArchetype.CUSTOM_PLAYER_TEMPLATE).next();
        stack.set(ModDataComponents.POCKET_ARCHETYPE.get(), nextArchetype);
        stack.set(ModDataComponents.POCKET_TEMPLATE_ID.get(), defaultTemplateFor(nextArchetype));

        PocketAccessMode nextAccess = stack.getOrDefault(
                ModDataComponents.POCKET_ACCESS_MODE.get(),
                PocketAccessMode.PRIVATE).next();
        stack.set(ModDataComponents.POCKET_ACCESS_MODE.get(), nextAccess);

        player.displayClientMessage(Component.literal(
                "Case tuned to " + nextArchetype.getSerializedName() + " / " + nextAccess.getSerializedName()), true);
    }

    private static String defaultTemplateFor(PocketArchetype archetype) {
        return switch (archetype) {
            case SCAMANDER_SANCTUARY -> "sanctuary_habitat";
            case ROOM_OF_REQUIREMENT -> "adaptive_utility";
            case CUSTOM_PLAYER_TEMPLATE -> "blank_shell";
        };
    }

    private static UUID ensureCaseId(ItemStack stack) {
        UUID existing = stack.get(ModDataComponents.POCKET_CASE_ID.get());
        if (existing != null) {
            return existing;
        }
        UUID created = UUID.randomUUID();
        stack.set(ModDataComponents.POCKET_CASE_ID.get(), created);
        return created;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltipAdder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltipAdder, flag);
        UUID pocketId = stack.get(ModDataComponents.POCKET_ID.get());
        PocketArchetype archetype = stack.getOrDefault(ModDataComponents.POCKET_ARCHETYPE.get(), PocketArchetype.CUSTOM_PLAYER_TEMPLATE);
        PocketAccessMode accessMode = stack.getOrDefault(ModDataComponents.POCKET_ACCESS_MODE.get(), PocketAccessMode.PRIVATE);
        tooltipAdder.accept(Component.literal("Archetype: " + archetype.getSerializedName()).withStyle(ChatFormatting.DARK_AQUA));
        tooltipAdder.accept(Component.literal("Access: " + accessMode.getSerializedName()).withStyle(ChatFormatting.GRAY));
        if (pocketId != null) {
            tooltipAdder.accept(Component.literal("Pocket: " + pocketId.toString().substring(0, 8)).withStyle(ChatFormatting.GOLD));
        } else {
            tooltipAdder.accept(Component.literal("Unbound case").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltipAdder.accept(Component.literal("Sneak-use to cycle archetype/access").withStyle(ChatFormatting.BLUE));
    }
}
