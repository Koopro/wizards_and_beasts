package at.koopro.wizardsandbeasts.command.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

public interface DebugModule {
    String name();

    LiteralArgumentBuilder<CommandSourceStack> register();
}
