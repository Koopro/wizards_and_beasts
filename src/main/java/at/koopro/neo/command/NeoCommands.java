package at.koopro.neo.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import at.koopro.neo.Neo;
import at.koopro.neo.form.FormSystemAPI;
import at.koopro.neo.item.DebugWandState;
import at.koopro.neo.network.BeamDebugOpenS2CPacket;
import at.koopro.neo.network.FormSyncS2CPacket;
import at.koopro.neo.network.SkillDataSyncS2CPacket;
import at.koopro.neo.network.SpellDataSyncS2CPacket;
import at.koopro.neo.network.TypeDataSyncS2CPacket;
import at.koopro.neo.registry.ModItems;
import at.koopro.neo.type.TypeSystemAPI;

import java.util.Set;

@EventBusSubscriber(modid = Neo.MODID)
public class NeoCommands {

    private static final Set<String> TREE_TYPES = Set.of("elder", "yew", "holly", "rowan");

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("neo")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("debug")
                        .then(DebugTreeCommand.register())
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
                        )
                )
                .then(SpellCommands.registerSpellCommand())
                .then(SpellCommands.registerWandCommand())
                .then(WizTypeCommands.register())
                .then(WizFormCommands.register())
                .then(WizSizeCommands.register())
                .then(WizMorphCommands.register())
                .then(SkillCommands.register())
        );
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpellDataSyncS2CPacket.syncToPlayer(player);
            SkillDataSyncS2CPacket.syncToPlayer(player);

            // Sync type data; open selector if no type chosen yet
            boolean needsSelection = !TypeSystemAPI.hasTypeSelected(player);
            TypeDataSyncS2CPacket.syncToPlayer(player, needsSelection);

            // Re-apply stat modifiers (transient, lost on relog)
            if (TypeSystemAPI.hasTypeSelected(player)) {
                TypeSystemAPI.applyStats(player);
            }

            // Re-apply form size profile and sync form data
            FormSystemAPI.reapplyCurrentForm(player);
            FormSyncS2CPacket.syncToTracking(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (TypeSystemAPI.hasTypeSelected(player)) {
                TypeSystemAPI.applyStats(player);
            }
            FormSystemAPI.reapplyCurrentForm(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DebugWandState.cleanup(player.getUUID(), (ServerLevel) player.level());
        }
    }

    private static int toggleBeamDebug(CommandSourceStack source) {
        Neo.debugForceBeam = !Neo.debugForceBeam;
        String state = Neo.debugForceBeam ? "\u00A7aON" : "\u00A7cOFF";
        source.sendSuccess(() -> Component.literal("\u00A77Beam debug: " + state), false);
        return 1;
    }

    private static int openBeamEditor(ServerPlayer player) {
        Neo.debugForceBeam = true;
        BeamDebugOpenS2CPacket.sendToPlayer(player);
        player.displayClientMessage(Component.literal("\u00A77Beam debug: \u00A7aON \u00A77(editor opened)"), true);
        return 1;
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

    private NeoCommands() {
    }
}
