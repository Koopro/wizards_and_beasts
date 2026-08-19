package at.koopro.wizardsandbeasts.wand.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * {@code /wandb item wand …} — the wand as an <em>object</em>: spawn one, configure its modules.
 *
 * <p>What a wand lets you <em>do</em> lives under {@code /wandb magic} instead ({@code spell},
 * {@code proficiency}, {@code patronus}). The old {@code /wandb wand} node mixed both, plus a
 * self-test, which is why the split runs along this line.
 */
public final class WandCommands {

    private WandCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("wand")
                .then(WandGiveCommands.register())
                .then(WandConfigCommands.register());
    }
}
