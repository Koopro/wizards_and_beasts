package at.koopro.wizardsandbeasts.command.debug;

import org.jspecify.annotations.Nullable;

import at.koopro.wizardsandbeasts.wand.elder.ElderWandSavedData;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;


public final class ElderWandDebugModule implements DebugModule {

    @Override
    public String name() {
        return "elder_wand";
    }

    @Override
    public String summary() {
        return "Elder Wand world state: inspect, force_master, clear_master, reset.";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal(name())
                .executes(ctx -> inspect(ctx.getSource()))
                .then(DebugModule.onSelfOrTarget("force_master", ElderWandDebugModule::forceMaster))
                .then(Commands.literal("clear_master")
                        .executes(ctx -> clearMaster(ctx.getSource())))
                .then(Commands.literal("reset")
                        .executes(ctx -> reset(ctx.getSource())));
    }

    private static int inspect(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ElderWandSavedData data = ElderWandSavedData.get(level);
        DebugOutput out = new DebugOutput(source);
        out.header("Elder Wand world state");
        out.kv("Registered", String.valueOf(data.isRegistered()));
        out.kv("Instance ID", data.getInstanceId() != null ? data.getInstanceId().toString() : "(none)");
        out.kv("Current master UUID", data.getMaster() != null ? data.getMaster().toString() : "(none)");
        ServerPlayer masterPlayer = data.getMaster() != null
                ? level.getServer().getPlayerList().getPlayer(data.getMaster()) : null;
        out.kv("Master online as", masterPlayer != null ? masterPlayer.getName().getString() : "(offline or none)");
        return 1;
    }

    private static int forceMaster(CommandSourceStack source, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        DebugOutput out = new DebugOutput(source);
        ServerLevel level = source.getLevel();
        ElderWandSavedData data = ElderWandSavedData.get(level);

        if (!data.isRegistered()) {
            out.warn("No elder wand registered in this world yet.");
            return 0;
        }

        data.setMaster(target.getUUID());

        // The saved master is authoritative; bring a held stack in line now rather than on the next tick.
        ItemStack held = elderWandInHand(target);
        if (held != null) {
            at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService.reconcileElderStack(held, target.getUUID());
        }

        out.ok("Set elder wand master to " + target.getName().getString() + ".");
        return 1;
    }

    private static int clearMaster(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ElderWandSavedData data = ElderWandSavedData.get(level);
        data.setMaster(null);
        new DebugOutput(source).ok("Cleared elder wand master. It is won by the next wizard to defeat another while wielding it.");
        return 1;
    }

    private static int reset(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = source.getLevel();
        // Re-create via a fresh instance; get() returns computeIfAbsent so we need to replace it.
        // Easiest: clear via the existing data object's fields (there's no API to remove SavedData,
        // so we clear the logical state and mark dirty — the wand will re-register on next spawn).
        ElderWandSavedData data = ElderWandSavedData.get(level);
        data.reset();
        new DebugOutput(source).ok("Elder Wand registration and master cleared. Next elder wand item to enter the world will become the canonical one.");
        return 1;
    }

    private static @Nullable ItemStack elderWandInHand(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (ModDataComponents.isElderWand(stack)) return stack;
        }
        return null;
    }
}
