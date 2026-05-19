package at.koopro.wizardsandbeasts.command.debug;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class DebugOutput {
    private final CommandSourceStack source;

    public DebugOutput(CommandSourceStack source) {
        this.source = source;
    }

    public void header(String title) {
        info("=== " + title + " ===");
    }

    public void info(String message) {
        source.sendSuccess(() -> Component.literal("[W&B Debug] " + message).withStyle(ChatFormatting.GRAY), false);
    }

    public void kv(String key, Object value) {
        info(key + ": " + value);
    }

    public void ok(String message) {
        source.sendSuccess(() -> Component.literal("[W&B Debug] " + message).withStyle(ChatFormatting.GREEN), false);
    }

    public void warn(String message) {
        source.sendFailure(Component.literal("[W&B Debug] " + message).withStyle(ChatFormatting.YELLOW));
    }
}
