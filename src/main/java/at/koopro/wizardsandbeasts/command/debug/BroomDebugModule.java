package at.koopro.wizardsandbeasts.command.debug;

import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public final class BroomDebugModule implements DebugModule {
    @Override
    public String name() {
        return "broom";
    }

    @Override
    public String summary() {
        return "Broom vehicle speed/tilt when mounted.";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal(name())
                .executes(ctx -> inspect(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> inspect(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"))));
    }

    private int inspect(CommandSourceStack source, ServerPlayer target) {
        DebugOutput out = new DebugOutput(source);
        out.header("Broom Debug :: " + target.getName().getString());
        if (!(target.getVehicle() instanceof BroomEntity broom)) {
            out.info("Vehicle: (not on broom)");
            out.ok("Validation: No broom state to inspect.");
            return 1;
        }

        out.kv("Speed", String.format("%.3f", broom.getCurrentSpeed()));
        out.kv("Boosting", broom.isBoosting());
        out.kv("Pitch tilt", String.format("%.3f", broom.getPitchTilt()));
        out.kv("Roll tilt", String.format("%.3f", broom.getRollTilt()));
        out.ok("Validation: Broom rider state present.");
        return 1;
    }
}
