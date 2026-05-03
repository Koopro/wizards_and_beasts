package at.koopro.wizardsandbeasts.command.debug;

import at.koopro.wizardsandbeasts.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.Spells;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public final class SpellDebugModule implements DebugModule {
    @Override
    public String name() {
        return "spells";
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
        PlayerSpellData data = target.getData(ModAttachments.SPELL_DATA.get());

        out.header("Spell Debug :: " + target.getName().getString());
        out.kv("Registry entries", Spells.count());
        out.kv("Known spells", data.getKnownSpells().size());
        out.kv("Active slot", data.getActiveSlot());
        out.kv("Active spell", data.getActiveSpellId() == null ? "(none)" : data.getActiveSpellId());
        out.kv("Sync corrections", data.getSyncCorrections());

        int missingKnown = 0;
        for (String id : data.getKnownSpells()) {
            if (Spells.byId(id) == null) missingKnown++;
        }

        int missingLoadout = 0;
        for (String id : data.getLoadout()) {
            if (id != null && Spells.byId(id) == null) missingLoadout++;
        }

        if (missingKnown > 0) {
            out.warn("Validation: Missing spell IDs in known list = " + missingKnown);
        }
        if (missingLoadout > 0) {
            out.warn("Validation: Missing spell IDs in loadout = " + missingLoadout);
        }
        if (missingKnown == 0 && missingLoadout == 0) {
            out.ok("Validation: Spell IDs are consistent.");
        }
        return 1;
    }
}
