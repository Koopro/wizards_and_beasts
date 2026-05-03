package at.koopro.wizardsandbeasts.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import at.koopro.wizardsandbeasts.network.TypeDataSyncS2CPacket;
import at.koopro.wizardsandbeasts.network.VaultSyncS2CPacket;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModItems;
import at.koopro.wizardsandbeasts.spell.WandBeamChannelLogic;
import at.koopro.wizardsandbeasts.type.TypeSystemAPI;
import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.client.spell.ColoredGlowRenderer;
import at.koopro.wizardsandbeasts.client.wand.BeamSettings;
import at.koopro.wizardsandbeasts.command.debug.DebugModuleRegistry;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;

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
                .then(Commands.literal("wand")
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
                .then(Commands.literal("spellstats")
                        .executes(ctx -> showSpellTelemetry(ctx.getSource().getPlayerOrException())))
                .then(WizMorphCommands.register());

        DebugModuleRegistry.attachTo(debugRoot);

        return Commands.literal(rootLiteral)
                .then(debugRoot)
                .then(SpellCommands.registerSpellCommand())
                .then(SpellCommands.registerWandCommand())
                .then(WizTypeCommands.register("type"))
                .then(WizTypeCommands.register("wiztype"))
                .then(WizFormCommands.register())
                .then(WizSizeCommands.register())
                .then(SkillCommands.register());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            boolean needsSelection = !TypeSystemAPI.hasTypeSelected(player);
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
        SkillSystemAPI.reconcileDerivedEffects(player);
        SpellDataSyncS2CPacket.syncToPlayer(player);
        SkillDataSyncS2CPacket.syncToPlayer(player);
        TypeDataSyncS2CPacket.syncToPlayer(player, openTypeSelector);
        VaultSyncS2CPacket.syncToPlayer(player);
        if (TypeSystemAPI.hasTypeSelected(player)) {
            TypeSystemAPI.applyStats(player);
        }
        FormSystemAPI.reapplyCurrentForm(player);
        FormSyncS2CPacket.syncToTracking(player);
    }

    private static int toggleBeamDebug(CommandSourceStack source) {
        WizardsAndBeastsMod.debugForceBeam = !WizardsAndBeastsMod.debugForceBeam;
        String state = WizardsAndBeastsMod.debugForceBeam ? "\u00A7aON" : "\u00A7cOFF";
        source.sendSuccess(() -> Component.literal("\u00A77Beam debug: " + state), false);
        return 1;
    }

    private static int openBeamEditor(ServerPlayer player) {
        WizardsAndBeastsMod.debugForceBeam = true;
        BeamDebugOpenS2CPacket.sendToPlayer(player);
        player.displayClientMessage(Component.literal("\u00A77Beam debug: \u00A7aON \u00A77(editor opened)"), true);
        return 1;
    }

    private static int showSpellTelemetry(ServerPlayer player) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        player.displayClientMessage(Component.literal("\u00A76[W&B]\u00A7r Sync corrections: " + data.getSyncCorrections()), false);
        if (data.getRejectCounts().isEmpty()) {
            player.displayClientMessage(Component.literal("\u00A77No rejection telemetry recorded yet."), false);
            return 1;
        }
        player.displayClientMessage(Component.literal("\u00A76[W&B]\u00A7r Reject counters:"), false);
        data.getRejectCounts().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(8)
                .forEach(e -> player.displayClientMessage(
                        Component.literal("\u00A77- " + e.getKey() + ": \u00A7f" + e.getValue()), false));
        return 1;
    }

    private static int setBeamPreset(CommandSourceStack source, BeamSettings.PerformancePreset preset) {
        BeamSettings.applyPerformancePreset(preset);
        source.sendSuccess(() -> Component.literal("\u00A77Beam preset set to \u00A7a" + preset.name().toLowerCase()), false);
        return 1;
    }

    private static int setGlowOff(CommandSourceStack source, ServerPlayer target) {
        clearGlowTags(target);
        source.sendSuccess(() -> Component.literal("\u00A77Glow debug for \u00A7f" + target.getName().getString() + "\u00A77: \u00A7cOFF"), true);
        return 1;
    }

    private static int setGlowHash(CommandSourceStack source, ServerPlayer target) {
        clearGlowTags(target);
        target.addTag(ColoredGlowRenderer.HASH_COLOR_TAG);
        source.sendSuccess(() -> Component.literal("\u00A77Glow debug for \u00A7f" + target.getName().getString() + "\u00A77: \u00A7aHASH"), true);
        return 1;
    }

    private static int setGlowColor(CommandSourceStack source, ServerPlayer target, String rgbInput) {
        String hex = normalizeRgb(rgbInput);
        if (hex == null) {
            source.sendFailure(Component.literal("\u00A7cInvalid color. Use RRGGBB or #RRGGBB."));
            return 0;
        }

        clearGlowTags(target);
        target.addTag(ColoredGlowRenderer.COLOR_TAG_PREFIX + hex);
        source.sendSuccess(() -> Component.literal("\u00A77Glow debug for \u00A7f" + target.getName().getString()
                + "\u00A77: \u00A7a#" + hex), true);
        return 1;
    }

    private static void clearGlowTags(ServerPlayer target) {
        target.removeTag(ColoredGlowRenderer.HASH_COLOR_TAG);
        target.getTags().stream()
                .filter(tag -> tag.startsWith(ColoredGlowRenderer.COLOR_TAG_PREFIX))
                .toList()
                .forEach(target::removeTag);
    }

    private static String normalizeRgb(String input) {
        String value = input.startsWith("#") ? input.substring(1) : input;
        if (value.length() != 6) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hexDigit = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!hexDigit) {
                return null;
            }
        }
        return value.toUpperCase();
    }

    private static int giveDebugWand(ServerPlayer player, String treeType) {
        ItemStack wand = new ItemStack(ModItems.DEBUG_WAND.get());
        player.getInventory().add(wand);

        if (treeType != null && TREE_TYPES.contains(treeType)) {
            DebugWandState.get(player.getUUID()).setTreeType(treeType);
        }

        String type = DebugWandState.get(player.getUUID()).getTreeType();
        player.displayClientMessage(Component.literal(
                "\u00A7aDebug Wand \u00A77given \u00A77(\u00A7b" + type + "\u00A77)"), true);
        return 1;
    }

    private WizardsAndBeastsCommands() {
    }
}
