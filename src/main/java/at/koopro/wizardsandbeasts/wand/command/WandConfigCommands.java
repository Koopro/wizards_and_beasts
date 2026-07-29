package at.koopro.wizardsandbeasts.wand.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.customization.WandConfiguration;
import at.koopro.wizardsandbeasts.wand.customization.WandModule;
import at.koopro.wizardsandbeasts.wand.customization.WandModuleRegistry;
import at.koopro.wizardsandbeasts.wand.customization.WandPreset;
import at.koopro.wizardsandbeasts.wand.customization.WandPresetRegistry;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class WandConfigCommands {

    private WandConfigCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("config")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(buildGet())
                .then(buildSet())
                .then(buildClear())
                .then(buildReset())
                .then(buildList())
                .then(buildPreset())
                .then(buildPresets());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildGet() {
        return Commands.literal("get")
                .executes(ctx -> executeGet(ctx.getSource().getPlayerOrException()));
    }

    private static int executeGet(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WandItem)) {
            player.displayClientMessage(error("Hold a wand in your main hand."), false);
            return 0;
        }
        WandConfiguration config = stack.getOrDefault(WandComponents.WAND_CONFIGURATION.get(), WandConfiguration.DEFAULT);
        player.displayClientMessage(header("Wand configuration:"), false);
        for (WandSlot slot : WandSlot.renderOrder()) {
            Optional<Identifier> moduleId = config.getModule(slot);
            String value = moduleId.map(Identifier::toString).orElse(slot.isRequired() ? "(missing)" : "(none)");
            ChatFormatting color = slot.isRequired() ? ChatFormatting.GOLD : ChatFormatting.GRAY;
            player.displayClientMessage(
                    Component.literal("  " + slot.slotId() + ": ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(value).withStyle(color)),
                    false);
        }
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSet() {
        return Commands.literal("set")
                .then(Commands.argument("slot", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(WandSlot.values()).map(WandSlot::slotId), builder))
                        .then(Commands.argument("module_id", IdentifierArgument.id())
                                .suggests((ctx, builder) -> {
                                    String slotArg = StringArgumentType.getString(ctx, "slot");
                                    return WandSlot.byId(slotArg)
                                            .map(slot -> SharedSuggestionProvider.suggest(
                                                    WandModuleRegistry.getAllForSlot(slot).stream()
                                                            .map(m -> m.id().toString()),
                                                    builder))
                                            .orElse(builder.buildFuture());
                                })
                                .executes(ctx -> executeSet(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "slot"),
                                        IdentifierArgument.getId(ctx, "module_id").toString()))));
    }

    private static int executeSet(ServerPlayer player, String slotArg, String moduleArg) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WandItem)) {
            player.displayClientMessage(error("Hold a wand in your main hand."), false);
            return 0;
        }
        WandSlot slot = WandSlot.byId(slotArg).orElse(null);
        if (slot == null) {
            player.displayClientMessage(error("Unknown slot: " + slotArg), false);
            return 0;
        }
        Identifier moduleId = Identifier.tryParse(moduleArg);
        if (moduleId == null) {
            player.displayClientMessage(error("Invalid identifier: " + moduleArg), false);
            return 0;
        }
        WandModule module = WandModuleRegistry.get(moduleId).orElse(null);
        if (module == null) {
            player.displayClientMessage(error("Unknown module: " + moduleArg), false);
            return 0;
        }
        if (module.slot() != slot) {
            player.displayClientMessage(error("Module '" + moduleArg + "' belongs to slot '"
                    + module.slot().slotId() + "', not '" + slot.slotId() + "'."), false);
            return 0;
        }

        WandConfiguration current = stack.getOrDefault(WandComponents.WAND_CONFIGURATION.get(), WandConfiguration.DEFAULT);
        WandConfiguration updated = current.withModule(slot, moduleId);
        stack.set(WandComponents.WAND_CONFIGURATION.get(), updated);
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(success("Set " + slot.slotId() + " → " + moduleId), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildClear() {
        return Commands.literal("clear")
                .then(Commands.argument("slot", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(WandSlot.values())
                                        .filter(s -> !s.isRequired())
                                        .map(WandSlot::slotId),
                                builder))
                        .executes(ctx -> executeClear(
                                ctx.getSource().getPlayerOrException(),
                                StringArgumentType.getString(ctx, "slot"))));
    }

    private static int executeClear(ServerPlayer player, String slotArg) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WandItem)) {
            player.displayClientMessage(error("Hold a wand in your main hand."), false);
            return 0;
        }
        WandSlot slot = WandSlot.byId(slotArg).orElse(null);
        if (slot == null) {
            player.displayClientMessage(error("Unknown slot: " + slotArg), false);
            return 0;
        }
        if (slot.isRequired()) {
            player.displayClientMessage(error("Slot '" + slotArg + "' is required and cannot be cleared."), false);
            return 0;
        }

        WandConfiguration current = stack.getOrDefault(WandComponents.WAND_CONFIGURATION.get(), WandConfiguration.DEFAULT);
        if (current.getModule(slot).isEmpty()) {
            player.displayClientMessage(error("Slot '" + slotArg + "' is already empty."), false);
            return 0;
        }

        WandConfiguration updated = current.withoutModule(slot);
        stack.set(WandComponents.WAND_CONFIGURATION.get(), updated);
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(success("Cleared " + slot.slotId() + "."), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildReset() {
        return Commands.literal("reset")
                .executes(ctx -> executeReset(ctx.getSource().getPlayerOrException()));
    }

    private static int executeReset(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WandItem)) {
            player.displayClientMessage(error("Hold a wand in your main hand."), false);
            return 0;
        }
        stack.set(WandComponents.WAND_CONFIGURATION.get(), WandConfiguration.DEFAULT);
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(success("Reset wand configuration to defaults."), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildList() {
        return Commands.literal("list")
                .then(Commands.argument("slot", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(WandSlot.values()).map(WandSlot::slotId), builder))
                        .executes(ctx -> executeList(
                                ctx.getSource().getPlayerOrException(),
                                StringArgumentType.getString(ctx, "slot"))));
    }

    private static int executeList(ServerPlayer player, String slotArg) {
        WandSlot slot = WandSlot.byId(slotArg).orElse(null);
        if (slot == null) {
            player.displayClientMessage(error("Unknown slot: " + slotArg), false);
            return 0;
        }
        List<WandModule> modules = WandModuleRegistry.getAllForSlot(slot);
        player.displayClientMessage(header("Modules for slot '" + slot.slotId() + "' (" + modules.size() + "):"), false);
        for (WandModule m : modules) {
            player.displayClientMessage(
                    Component.literal("  " + m.id()).withStyle(ChatFormatting.AQUA),
                    false);
        }
        return 1;
    }


    private static LiteralArgumentBuilder<CommandSourceStack> buildPreset() {
        return Commands.literal("preset")
                .then(Commands.argument("preset_id", IdentifierArgument.id())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                WandPresetRegistry.all().stream().map(p -> p.id().toString()),
                                builder))
                        .executes(ctx -> executePreset(
                                ctx.getSource().getPlayerOrException(),
                                IdentifierArgument.getId(ctx, "preset_id"))));
    }

    private static int executePreset(ServerPlayer player, Identifier presetId) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WandItem)) {
            player.displayClientMessage(error("Hold a wand in your main hand."), false);
            return 0;
        }
        WandPreset preset = WandPresetRegistry.get(presetId).orElse(null);
        if (preset == null) {
            player.displayClientMessage(error("Unknown preset: " + presetId), false);
            return 0;
        }
        // A preset replaces the whole configuration rather than merging into it: half of a
        // recognisable wand plus half of whatever was there before is neither.
        stack.set(WandComponents.WAND_CONFIGURATION.get(), preset.configuration());
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(success("Applied preset: " + preset.displayName()), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPresets() {
        return Commands.literal("presets")
                .executes(ctx -> executePresets(ctx.getSource().getPlayerOrException()));
    }

    private static int executePresets(ServerPlayer player) {
        List<WandPreset> presets = WandPresetRegistry.all();
        player.displayClientMessage(header("Wand presets (" + presets.size() + "):"), false);
        for (WandPreset preset : presets) {
            player.displayClientMessage(
                    Component.literal("  " + preset.id().getPath()).withStyle(ChatFormatting.AQUA)
                            .append(Component.literal("  " + preset.displayName())
                                    .withStyle(ChatFormatting.GRAY)),
                    false);
        }
        return 1;
    }

    private static Component header(String text) {
        return Component.literal("[W&B] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
    }

    private static Component success(String text) {
        return Component.literal("[W&B] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.GREEN));
    }

    private static Component error(String text) {
        return Component.literal("[W&B] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.RED));
    }
}
