package at.koopro.wizardsandbeasts.command;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.wand.BeamSettings;
import at.koopro.wizardsandbeasts.azkaban.command.AzkabanCommands;
import at.koopro.wizardsandbeasts.command.CharacterCommands;
import at.koopro.wizardsandbeasts.bestiary.command.BestiaryCommands;
import at.koopro.wizardsandbeasts.bloodpact.command.PactCommands;
import at.koopro.wizardsandbeasts.command.debug.DebugModuleRegistry;
import at.koopro.wizardsandbeasts.command.debug.DebugTreeCommand;
import at.koopro.wizardsandbeasts.command.debug.WizMorphCommands;
import at.koopro.wizardsandbeasts.floo.command.FlooCommands;
import at.koopro.wizardsandbeasts.heritage.command.HeritageCommands;
import at.koopro.wizardsandbeasts.command.MinistryCommands;
import at.koopro.wizardsandbeasts.module.command.ModuleCommands;
import at.koopro.wizardsandbeasts.skill.command.SkillCommands;
import at.koopro.wizardsandbeasts.currency.command.VaultCommands;
import at.koopro.wizardsandbeasts.wand.command.WandCommands;
import at.koopro.wizardsandbeasts.command.WorldCommands;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.item.wand.DebugWandState;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import at.koopro.wizardsandbeasts.network.debug.BeamDebugOpenS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.util.GlowDebugTags;
import at.koopro.wizardsandbeasts.util.RgbHex;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
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
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Set;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;

/**
 * Root command registrar for {@code /wandb} and its deprecated alias {@code /wizardsandbeasts}.
 *
 * <p>Call {@link #register(RegisterCommandsEvent)} from the {@code RegisterCommandsEvent} subscriber.
 */
public final class WandbCommands {

    private static final Set<String> TREE_TYPES = Set.of("elder", "yew", "holly", "rowan");

    private WandbCommands() {}

    public static void register(RegisterCommandsEvent event) {
        DebugModuleRegistry.bootstrap();
        event.getDispatcher().register(buildRoot("wandb"));
        /** @deprecated Use /wandb. This alias will be removed in a future version. */
        event.getDispatcher().register(buildRoot("wizardsandbeasts"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String rootLiteral) {
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
                .then(WizMorphCommands.register());

        DebugModuleRegistry.attachTo(debugRoot);

        return Commands.literal(rootLiteral)
                .then(debugRoot)
                .then(FlooCommands.register())
                .then(PactCommands.register())
                .then(HeritageCommands.register())
                .then(BestiaryCommands.register())
                .then(VaultCommands.register())
                .then(SkillCommands.register())
                .then(WandCommands.register())
                .then(ModuleCommands.register())
                .then(MinistryCommands.register())
                .then(WorldCommands.register())
                .then(AzkabanCommands.register())
                .then(CharacterCommands.register())
                .then(StatsCommands.register());
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
        BeamDebugOpenS2CPayload.sendToPlayer(player);
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

    private static int giveDebugWand(ServerPlayer player, String treeType) {
        ItemStack wand = new ItemStack(WandItemRegistry.DEBUG_WAND.get());
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
}
