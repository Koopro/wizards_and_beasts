package at.koopro.wizardsandbeasts.command.debug;

import at.koopro.wizardsandbeasts.spell.core.Spells;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

public final class ReloadDebugModule implements DebugModule {
    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String summary() {
        return "Runs /reload and reports spell registry count.";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal(name()).executes(ctx -> reload(ctx.getSource()));
    }

    private int reload(CommandSourceStack source) {
        DebugOutput out = new DebugOutput(source);
        out.header("Reload Debug");
        out.info("Starting datapack reload (this also refreshes JSON spells)...");

        MinecraftServer server = source.getServer();
        server.getCommands().performPrefixedCommand(source.withSuppressedOutput(), "reload");

        int loaded = Spells.count();
        out.kv("Spell registry entries", loaded);
        if (loaded <= 0) {
            out.warn("Validation: No spells loaded after reload.");
            return 0;
        }
        out.ok("Reload completed. Spell registry is populated.");
        return 1;
    }
}
