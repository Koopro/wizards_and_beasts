package at.koopro.wizardsandbeasts.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.item.DebugWandState;
import at.koopro.wizardsandbeasts.network.BeamDebugOpenS2CPacket;
import at.koopro.wizardsandbeasts.network.FormSyncS2CPacket;
import at.koopro.wizardsandbeasts.network.SkillDataSyncS2CPacket;
import at.koopro.wizardsandbeasts.network.SpellDataSyncS2CPacket;
import at.koopro.wizardsandbeasts.network.HeritageDataSyncS2CPacket;
import at.koopro.wizardsandbeasts.network.VaultSyncS2CPacket;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModItems;
import at.koopro.wizardsandbeasts.spell.WandBeamChannelLogic;
import at.koopro.wizardsandbeasts.type.HeritageAPI;
import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.client.wand.BeamSettings;
import at.koopro.wizardsandbeasts.command.debug.DebugModuleRegistry;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillAttributeApplicator;
import at.koopro.wizardsandbeasts.util.GlowDebugTags;
import at.koopro.wizardsandbeasts.util.RgbHex;

import java.util.Set;

@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public class WizardsAndBeastsCommands {

    private static final Set<String> TREE_TYPES = Set.of("elder", "yew", "holly", "rowan");

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DebugModuleRegistry.bootstrap();
        event.getDispatcher().register(buildRootCommand("wandb"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRootCommand(String rootLiteral) {
        LiteralArgumentBuilder<CommandSourceStack> debugRoot = Commands.literal("debug")
                .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                .then(DebugTreeCommand.register())
                .then(Commands.literal("glow")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("off")
                                        .executes(ctx -> setGlowOff(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"))))
                                .then(Commands.literal("hash")
                                        .executes(ctx -> setGlowHash(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"))))
                                .then(Commands.literal("color")
                                        .then(Commands.argument("rgb", StringArgumentType.word())
                                                .executes(ctx -> setGlowColor(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "rgb")))))))
                .then(Commands.literal("wandtool")
                        .executes(ctx -> giveDebugWand(ctx.getSource().getPlayerOrException(), null))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TREE_TYPES, builder))
                                .executes(ctx -> giveDebugWand(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "type")))
                        )
                )
                .then(Commands.literal("beam")
                        .executes(ctx -> toggleBeamDebug(ctx.getSource()))
                        .then(Commands.literal("edit")
                                .executes(ctx -> openBeamEditor(ctx.getSource().getPlayerOrException()))
                        )
                        .then(Commands.literal("preset")
                                .then(Commands.literal("low").executes(ctx -> setBeamPreset(ctx.getSource(), BeamSettings.PerformancePreset.LOW)))
                                .then(Commands.literal("medium").executes(ctx -> setBeamPreset(ctx.getSource(), BeamSettings.PerformancePreset.MEDIUM)))
                                .then(Commands.literal("high").executes(ctx -> setBeamPreset(ctx.getSource(), BeamSettings.PerformancePreset.HIGH)))
                        )
                )
                .then(Commands.literal("stats")
                        .executes(ctx -> showSpellTelemetry(ctx.getSource().getPlayerOrException())))
                .then(WizMorphCommands.register())
                .then(Commands.literal("module")
                        .then(Commands.argument("module", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(Module.values()).map(Enum::name), builder))
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.List.of("DISABLED", "ENABLED", "PREVIEW"), builder))
                                        .executes(ctx -> setModuleState(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "module"),
                                                StringArgumentType.getString(ctx, "state"))))));

        DebugModuleRegistry.attachTo(debugRoot);

        return Commands.literal(rootLiteral)
                .then(debugRoot)
                .then(SpellCommands.registerSpellCommand())
                .then(ProficiencyCommands.register())
                .then(SpellCommands.registerWandCommand())
                .then(HeritageCommands.register("type"))
                .then(HeritageCommands.register("wiztype"))
                .then(WizFormCommands.register())
                .then(WizSizeCommands.register())
                .then(SkillCommands.register())
                .then(BestiaryCommands.register());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            boolean needsSelection = !HeritageAPI.hasHeritageSelected(player);
            resyncPlayerState(player, needsSelection);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WandBeamChannelLogic.endChannel(player);
            resyncPlayerState(player, false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WandBeamChannelLogic.endChannel(player);
            DebugWandState.cleanup(player.getUUID(), (ServerLevel) player.level());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WandBeamChannelLogic.endChannel(player);
            resyncPlayerState(player, false);
        }
    }

    private static void resyncPlayerState(ServerPlayer player, boolean openTypeSelector) {
        SkillAttributeApplicator.applyAll(player);
        SpellDataSyncS2CPacket.syncToPlayer(player);
        SkillDataSyncS2CPacket.syncToPlayer(player);
        HeritageDataSyncS2CPacket.syncToPlayer(player, openTypeSelector);
        VaultSyncS2CPacket.syncToPlayer(player);
        if (HeritageAPI.hasHeritageSelected(player)) {
            HeritageAPI.applyStats(player);
        }
        FormSystemAPI.reapplyCurrentForm(player);
        FormSyncS2CPacket.syncToTracking(player);
    }

    private static int toggleBeamDebug(CommandSourceStack source) {
        WizardsAndBeastsMod.debugForceBeam = !WizardsAndBeastsMod.debugForceBeam;
        boolean on = WizardsAndBeastsMod.debugForceBeam;
        source.sendSuccess(() -> Component.literal("Beam debug: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(on ? "ON" : "OFF")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        return 1;
    }

    private static int openBeamEditor(ServerPlayer player) {
        WizardsAndBeastsMod.debugForceBeam = true;
        BeamDebugOpenS2CPacket.sendToPlayer(player);
        player.displayClientMessage(
                Component.literal("Beam debug: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("ON").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" (editor opened)").withStyle(ChatFormatting.GRAY)),
                true);
        return 1;
    }

    private static int showSpellTelemetry(ServerPlayer player) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        player.displayClientMessage(
                Component.literal("[W&B]").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(" Sync corrections: " + data.getSyncCorrections())
                                .withStyle(ChatFormatting.RESET)),
                false);
        if (data.getRejectCounts().isEmpty()) {
            player.displayClientMessage(
                    Component.literal("No rejection telemetry recorded yet.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        player.displayClientMessage(
                Component.literal("[W&B]").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(" Reject counters:").withStyle(ChatFormatting.RESET)),
                false);
        data.getRejectCounts().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(8)
                .forEach(e -> player.displayClientMessage(
                        Component.literal("- " + e.getKey() + ": ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(e.getValue()))
                                        .withStyle(ChatFormatting.WHITE)),
                        false));
        return 1;
    }

    private static int setBeamPreset(CommandSourceStack source, BeamSettings.PerformancePreset preset) {
        BeamSettings.applyPerformancePreset(preset);
        source.sendSuccess(() -> Component.literal("Beam preset set to ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(preset.name().toLowerCase()).withStyle(ChatFormatting.GREEN)), false);
        return 1;
    }

    private static int setGlowOff(CommandSourceStack source, ServerPlayer target) {
        clearGlowTags(target);
        source.sendSuccess(() -> Component.literal("Glow debug for ")
                .withStyle(ChatFormatting.GRAY)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("OFF").withStyle(ChatFormatting.RED)), true);
        return 1;
    }

    private static int setGlowHash(CommandSourceStack source, ServerPlayer target) {
        clearGlowTags(target);
        target.addTag(GlowDebugTags.HASH_COLOR_TAG);
        source.sendSuccess(() -> Component.literal("Glow debug for ")
                .withStyle(ChatFormatting.GRAY)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("HASH").withStyle(ChatFormatting.GREEN)), true);
        return 1;
    }

    private static int setGlowColor(CommandSourceStack source, ServerPlayer target, String rgbInput) {
        String hex = RgbHex.normalizeRgbHex(rgbInput);
        if (hex == null) {
            source.sendFailure(Component.literal("Invalid color. Use RRGGBB or #RRGGBB.").withStyle(ChatFormatting.RED));
            return 0;
        }

        clearGlowTags(target);
        target.addTag(GlowDebugTags.COLOR_TAG_PREFIX + hex);
        source.sendSuccess(() -> Component.literal("Glow debug for ")
                .withStyle(ChatFormatting.GRAY)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("#" + hex).withStyle(ChatFormatting.GREEN)), true);
        return 1;
    }

    private static void clearGlowTags(ServerPlayer target) {
        target.removeTag(GlowDebugTags.HASH_COLOR_TAG);
        target.getTags().stream()
                .filter(tag -> tag.startsWith(GlowDebugTags.COLOR_TAG_PREFIX))
                .toList()
                .forEach(target::removeTag);
    }

    private static int setModuleState(CommandSourceStack source, String moduleName, String stateName) {
        Module module;
        try {
            module = Module.valueOf(moduleName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown module: " + moduleName).withStyle(ChatFormatting.RED));
            return 0;
        }
        ModuleManager.State state;
        try {
            state = ModuleManager.State.valueOf(stateName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown state: " + stateName
                    + " (use DISABLED, ENABLED, or PREVIEW).").withStyle(ChatFormatting.RED));
            return 0;
        }
        ModuleManager.setState(module, state);
        source.sendSuccess(() -> Component.literal("Module ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(module.name()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(state.name()).withStyle(ChatFormatting.GREEN)), true);
        return 1;
    }

    private static int giveDebugWand(ServerPlayer player, String treeType) {
        ItemStack wand = new ItemStack(ModItems.DEBUG_WAND.get());
        player.getInventory().add(wand);

        if (treeType != null && TREE_TYPES.contains(treeType)) {
            DebugWandState.get(player.getUUID()).setTreeType(treeType);
        }

        String type = DebugWandState.get(player.getUUID()).getTreeType();
        player.displayClientMessage(
                Component.literal("Debug Wand ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal("given ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("(").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(type).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(")").withStyle(ChatFormatting.GRAY)),
                true);
        return 1;
    }

    private WizardsAndBeastsCommands() {
    }
}
