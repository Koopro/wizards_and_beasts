package at.koopro.wizardsandbeasts.command;

import org.jspecify.annotations.NullMarked;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.command.ApparitionCommands;
import at.koopro.wizardsandbeasts.command.debug.DebugModuleRegistry;
import at.koopro.wizardsandbeasts.command.debug.DebugTreeCommand;
import at.koopro.wizardsandbeasts.command.debug.WizMorphCommands;
import at.koopro.wizardsandbeasts.pose.command.PoseCommands;
import at.koopro.wizardsandbeasts.wand.command.BlankShapingSelfTest;
import at.koopro.wizardsandbeasts.item.wand.DebugWandState;
import at.koopro.wizardsandbeasts.network.debug.BeamDebugOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.debug.BeamPresetS2CPayload;
import at.koopro.wizardsandbeasts.render.outline.EntityOutlineService;
import at.koopro.wizardsandbeasts.util.RgbHex;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Set;
import at.koopro.wizardsandbeasts.registry.WandItemRegistry;

/**
 * Root command registrar for {@code /wandb}.
 *
 * <p>The tree below this is eight <em>categories</em>, never a bare verb: {@code player},
 * {@code magic}, {@code item}, {@code world}, {@code beast}, {@code ministry}, {@code admin},
 * {@code debug}. It grew to nineteen flat top-level nodes one system at a time before that rule
 * existed, so keep it — a new feature belongs inside whichever category already describes it, and
 * a feature that fits none of them is a sign the category list needs revisiting, not that the top
 * level needs a twentieth entry.
 *
 * <p>The {@code /wizardsandbeasts} alias root is gone. It registered the entire tree a second time,
 * and the full command tree is serialised to every client that connects.
 *
 * <p>Call {@link #register(RegisterCommandsEvent)} from the {@code RegisterCommandsEvent} subscriber.
 */
@NullMarked
public final class WandbCommands {

    private static final Set<String> TREE_TYPES = Set.of("elder", "yew", "holly", "rowan");

    private WandbCommands() {}

    public static void register(RegisterCommandsEvent event) {
        DebugModuleRegistry.bootstrap();
        event.getDispatcher().register(buildRoot("wandb"));
    }

    /** Visible for {@code CommandTreeShapeTest}, which asserts the category list above. */
    static LiteralArgumentBuilder<CommandSourceStack> buildRoot(String rootLiteral) {
        LiteralArgumentBuilder<CommandSourceStack> debugRoot = Commands.literal("debug")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
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
                                .then(Commands.literal("low").executes(ctx -> setBeamPreset(ctx.getSource(), "LOW")))
                                .then(Commands.literal("medium").executes(ctx -> setBeamPreset(ctx.getSource(), "MEDIUM")))
                                .then(Commands.literal("high").executes(ctx -> setBeamPreset(ctx.getSource(), "HIGH")))
                        )
                )
                // No `debug stats` node: it printed sync corrections and reject counters, which is
                // the strict subset of `debug spell` / `debug spell rejects` that also collided by
                // name with the player attribute tree now at `player stats`.
                .then(WizMorphCommands.register())
                .then(PoseCommands.register())
                .then(ApparitionCommands.registerTest())
                .then(BlankShapingSelfTest.register());

        DebugModuleRegistry.attachTo(debugRoot);

        return Commands.literal(rootLiteral)
                .then(PlayerCommands.register())
                .then(MagicCommands.register())
                .then(ItemCommands.register())
                .then(WorldCommands.register())
                .then(BeastCommands.register())
                .then(MinistryCommands.register())
                .then(AdminCommands.register())
                .then(debugRoot);
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

    private static int setBeamPreset(CommandSourceStack source, String presetName) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            source.sendFailure(Component.literal("Beam presets are client settings — run as a player."));
            return 0;
        }
        BeamPresetS2CPayload.sendToPlayer(player, presetName);
        source.sendSuccess(() -> Component.literal("Beam preset set to ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(presetName.toLowerCase()).withStyle(ChatFormatting.GREEN)), false);
        return 1;
    }

    /** Picks the beam renderer on the invoking player's client. Both stay wired; no restart needed. */

    private static int setGlowOff(CommandSourceStack source, ServerPlayer target) {
        EntityOutlineService.clear(target);
        source.sendSuccess(() -> Component.literal("Glow debug for ")
                .withStyle(ChatFormatting.GRAY)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("OFF").withStyle(ChatFormatting.RED)), true);
        return 1;
    }

    private static int setGlowHash(CommandSourceStack source, ServerPlayer target) {
        EntityOutlineService.setHashColor(target);
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
        EntityOutlineService.setColor(target, Integer.parseInt(hex, 16));
        source.sendSuccess(() -> Component.literal("Glow debug for ")
                .withStyle(ChatFormatting.GRAY)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("#" + hex).withStyle(ChatFormatting.GREEN)), true);
        return 1;
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
