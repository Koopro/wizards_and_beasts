package at.koopro.wizardsandbeasts.command;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.apparition.command.ApparitionCommands;
import at.koopro.wizardsandbeasts.command.debug.DebugModuleRegistry;
import at.koopro.wizardsandbeasts.command.debug.DebugTreeCommand;
import at.koopro.wizardsandbeasts.command.debug.WizMorphCommands;
import at.koopro.wizardsandbeasts.command.debug.inspect.DebugInspectors;
import at.koopro.wizardsandbeasts.pose.command.PoseCommands;
import at.koopro.wizardsandbeasts.wand.command.BlankShapingSelfTest;
import at.koopro.wizardsandbeasts.item.wand.DebugWandState;
import at.koopro.wizardsandbeasts.network.debug.BeamDebugOpenS2CPayload;
import at.koopro.wizardsandbeasts.network.debug.BeamPresetS2CPayload;
import at.koopro.wizardsandbeasts.render.outline.DebugOutlines;
import at.koopro.wizardsandbeasts.render.outline.OutlineStyle;
import at.koopro.wizardsandbeasts.render.outline.SpellOutlines;
import at.koopro.wizardsandbeasts.util.RgbHex;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
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
    private static final int TIMED_GLOW_DEFAULT_SECONDS = 5;
    private static final int HIGHLIGHT_DEFAULT_SECONDS = 4;
    /** Revelio's own colour ({@code "color": 16777130} in {@code spells/revelio.json}), so the test looks like the spell. */
    private static final int HIGHLIGHT_DEFAULT_RGB = 0xFFFFAA;

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
                        // Timed outline on whatever the caller is looking at — any entity, not just
                        // players. Exercises the path Revelio will use.
                        .then(Commands.literal("look")
                                .executes(ctx -> setGlowLook(ctx.getSource(), TIMED_GLOW_DEFAULT_SECONDS, null))
                                .then(Commands.literal("off")
                                        .executes(ctx -> clearGlowLook(ctx.getSource())))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                                        .executes(ctx -> setGlowLook(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "seconds"),
                                                null))
                                        .then(Commands.argument("rgb", StringArgumentType.word())
                                                .executes(ctx -> setGlowLook(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "seconds"),
                                                        StringArgumentType.getString(ctx, "rgb"))))))
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
                // Timed block highlight on the 3x3 under the caller's feet — the path Revelio will use.
                .then(Commands.literal("highlight")
                        .executes(ctx -> highlightFloor(ctx.getSource(), HIGHLIGHT_DEFAULT_SECONDS, null, 1.0f))
                        .then(Commands.literal("off")
                                .executes(ctx -> clearHighlights(ctx.getSource())))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                                .executes(ctx -> highlightFloor(
                                        ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds"), null, 1.0f))
                                .then(Commands.argument("rgb", StringArgumentType.word())
                                        .executes(ctx -> highlightFloor(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "seconds"),
                                                StringArgumentType.getString(ctx, "rgb"),
                                                1.0f))
                                        .then(Commands.argument("alpha", FloatArgumentType.floatArg(0.05f, 1.0f))
                                                .executes(ctx -> highlightFloor(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "seconds"),
                                                        StringArgumentType.getString(ctx, "rgb"),
                                                        FloatArgumentType.getFloat(ctx, "alpha")))))))
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
        DebugOutlines.clear(target);
        source.sendSuccess(() -> Component.literal("Glow debug for ")
                .withStyle(ChatFormatting.GRAY)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("OFF").withStyle(ChatFormatting.RED)), true);
        return 1;
    }

    private static int setGlowHash(CommandSourceStack source, ServerPlayer target) {
        DebugOutlines.setHashColor(target);
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
        DebugOutlines.setColor(target, Integer.parseInt(hex, 16));
        source.sendSuccess(() -> Component.literal("Glow debug for ")
                .withStyle(ChatFormatting.GRAY)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("#" + hex).withStyle(ChatFormatting.GREEN)), true);
        return 1;
    }

    private static int setGlowLook(CommandSourceStack source, int seconds, @Nullable String rgbInput)
            throws CommandSyntaxException {
        Entity target = lookedAtOrFail(source);
        if (target == null) return 0;

        int rgb;
        String label;
        if (rgbInput == null) {
            rgb = DebugOutlines.hashColor(target.getUUID());
            label = "hash";
        } else {
            String hex = RgbHex.normalizeRgbHex(rgbInput);
            if (hex == null) {
                source.sendFailure(Component.literal("Invalid color. Use RRGGBB or #RRGGBB.").withStyle(ChatFormatting.RED));
                return 0;
            }
            rgb = Integer.parseInt(hex, 16);
            label = "#" + hex;
        }

        // The temporary path, exactly as a spell uses it — this command exists to exercise that path.
        SpellOutlines.highlightEntities(List.of(target), new OutlineStyle(rgb, seconds * 20));
        source.sendSuccess(() -> Component.literal("Timed glow on ")
                .withStyle(ChatFormatting.GRAY)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(label).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" for " + seconds + "s").withStyle(ChatFormatting.GRAY)), true);
        return 1;
    }

    private static int clearGlowLook(CommandSourceStack source) throws CommandSyntaxException {
        Entity target = lookedAtOrFail(source);
        if (target == null) return 0;

        SpellOutlines.clearEntity(target);
        source.sendSuccess(() -> Component.literal("Timed glow on ")
                .withStyle(ChatFormatting.GRAY)
                .append(target.getDisplayName().plainCopy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("OFF").withStyle(ChatFormatting.RED)), true);
        return 1;
    }

    private static int highlightFloor(CommandSourceStack source, int seconds, @Nullable String rgbInput, float alpha)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        // By default, the colour Revelio actually draws, not its raw datapack value.
        int rgb = OutlineStyle.vivid(HIGHLIGHT_DEFAULT_RGB);
        if (rgbInput != null) {
            String hex = RgbHex.normalizeRgbHex(rgbInput);
            if (hex == null) {
                source.sendFailure(Component.literal("Invalid color. Use RRGGBB or #RRGGBB.").withStyle(ChatFormatting.RED));
                return 0;
            }
            rgb = Integer.parseInt(hex, 16);
        }

        BlockPos below = player.blockPosition().below();
        List<BlockPos> floor = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                floor.add(below.offset(dx, 0, dz));
            }
        }
        SpellOutlines.highlightBlocks(player, floor, new OutlineStyle(ARGB.color(alpha, rgb), seconds * 20));

        String colour = String.format("#%06X", rgb);
        source.sendSuccess(() -> Component.literal("Highlighted 3x3 under you: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(colour + " @" + Math.round(alpha * 100) + "%").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" for " + seconds + "s").withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int clearHighlights(CommandSourceStack source) throws CommandSyntaxException {
        SpellOutlines.clearBlocks(source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal("Block highlights: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("CLEARED").withStyle(ChatFormatting.RED)), false);
        return 1;
    }

    private static @Nullable Entity lookedAtOrFail(CommandSourceStack source) throws CommandSyntaxException {
        Entity target = DebugInspectors.entityLookedAt(source.getPlayerOrException()).orElse(null);
        if (target == null) {
            source.sendFailure(Component.literal("Not looking at an entity within "
                    + (int) DebugInspectors.REACH + " blocks.").withStyle(ChatFormatting.RED));
        }
        return target;
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
