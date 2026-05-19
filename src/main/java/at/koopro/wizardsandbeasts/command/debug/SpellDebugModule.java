package at.koopro.wizardsandbeasts.command.debug;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.cast.SpellRejectCodes;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.Map;

public final class SpellDebugModule implements DebugModule {
    @Override
    public String name() {
        return "spell";
    }

    @Override
    public String summary() {
        return "Spell data, loadout, reject telemetry (sub: rejects).";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal(name())
                .executes(ctx -> inspect(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("rejects")
                        .executes(ctx -> dumpRejects(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("summary")
                                .executes(ctx -> summaryRejects(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> summaryRejects(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearRejects(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> clearRejects(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> dumpRejects(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
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

        Map<String, Integer> rejects = data.getRejectCounts();
        if (rejects.isEmpty()) {
            out.kv("Top reject", "(none)");
        } else {
            Map.Entry<String, Integer> top = rejects.entrySet().stream()
                    .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                            .thenComparing(Map.Entry::getKey))
                    .orElseThrow();
            out.kv("Top reject", top.getKey() + " = " + top.getValue());
        }

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

    private int dumpRejects(CommandSourceStack source, ServerPlayer target) {
        DebugOutput out = new DebugOutput(source);
        PlayerSpellData data = target.getData(ModAttachments.SPELL_DATA.get());
        Map<String, Integer> rejects = data.getRejectCounts();

        out.header("Spell reject telemetry :: " + target.getName().getString());
        if (rejects.isEmpty()) {
            out.info("(no reject counters)");
            return 1;
        }

        int total = rejects.values().stream().mapToInt(Integer::intValue).sum();
        Map.Entry<String, Integer> top = rejects.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .orElseThrow();
        out.kv("Total rejects", total);
        out.kv("Top reason", top.getKey() + " = " + top.getValue());

        rejects.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(e -> out.kv("  " + e.getKey(), e.getValue()));
        return 1;
    }

    private int summaryRejects(CommandSourceStack source, ServerPlayer target) {
        DebugOutput out = new DebugOutput(source);
        Map<String, Integer> rejects = target.getData(ModAttachments.SPELL_DATA.get()).getRejectCounts();

        out.header("Spell reject summary :: " + target.getName().getString());
        if (rejects.isEmpty()) {
            out.info("(no reject counters)");
            return 1;
        }

        int guard = 0;
        int assign = 0;
        int ability = 0;
        int wandRelease = 0;
        int other = 0;
        for (Map.Entry<String, Integer> e : rejects.entrySet()) {
            int v = e.getValue();
            switch (SpellRejectCodes.summaryBucket(e.getKey())) {
                case "guard_*" -> guard += v;
                case "assign_*" -> assign += v;
                case "ability_*" -> ability += v;
                case "wand_release" -> wandRelease += v;
                default -> other += v;
            }
        }
        int total = guard + assign + ability + wandRelease + other;
        out.kv("Total", total);
        out.kv("guard_*", guard);
        out.kv("assign_*", assign);
        out.kv("ability_*", ability);
        out.kv("wand_release", wandRelease);
        out.kv("other", other);
        return 1;
    }

    private int clearRejects(CommandSourceStack source, ServerPlayer target) {
        PlayerSpellData data = target.getData(ModAttachments.SPELL_DATA.get());
        data.clearRejectCounts();
        SpellDataSyncS2CPayload.syncToPlayer(target);
        new DebugOutput(source).ok("Cleared spell reject counters for " + target.getName().getString());
        return 1;
    }
}
