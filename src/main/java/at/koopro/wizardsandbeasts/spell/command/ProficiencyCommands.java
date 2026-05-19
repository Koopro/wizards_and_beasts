package at.koopro.wizardsandbeasts.spell.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellProficiencySyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.proficiency.ProficiencyScaler;
import at.koopro.wizardsandbeasts.spell.proficiency.SpellScalingProfile;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class ProficiencyCommands {
    private ProficiencyCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("proficiency")
                .requires(WizardsAndBeastsCommandPermissions.GAMEMASTER)
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("spell_id", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Spells.all().stream().map(Spell::getId), b))
                                        .executes(ctx -> get(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "spell_id"))))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("spell_id", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Spells.all().stream().map(Spell::getId), b))
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.0f, 1.0f))
                                                .executes(ctx -> set(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "spell_id"),
                                                        FloatArgumentType.getFloat(ctx, "value")))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> resetAll(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                                .then(Commands.argument("spell_id", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Spells.all().stream().map(Spell::getId), b))
                                        .executes(ctx -> resetOne(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "spell_id"))))))
                .then(Commands.literal("setall")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0f, 1.0f))
                                        .executes(ctx -> setAll(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                FloatArgumentType.getFloat(ctx, "value"))))))
                .then(Commands.literal("preview")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("spell_id", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Spells.all().stream().map(Spell::getId), b))
                                        .executes(ctx -> preview(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "spell_id"))))));
    }

    private static int get(CommandSourceStack source, ServerPlayer player, String spellId) {
        String normalizedSpellId = normalizeSpellId(spellId);
        float value = player.getData(ModAttachments.SPELL_DATA.get()).getSpellProficiency(normalizedSpellId);
        source.sendSuccess(() -> Component.literal(String.format("Proficiency %s -> %.3f", normalizedSpellId, value)), false);
        return 1;
    }

    private static int set(CommandSourceStack source, ServerPlayer player, String spellId, float value) {
        String normalizedSpellId = normalizeSpellId(spellId);
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        data.setSpellProficiency(normalizedSpellId, value);
        SpellProficiencySyncS2CPayload.sendTo(player, normalizedSpellId, data.getSpellProficiency(normalizedSpellId));
        source.sendSuccess(() -> Component.literal(String.format("Set %s -> %.3f", normalizedSpellId, data.getSpellProficiency(normalizedSpellId))), true);
        return 1;
    }

    private static int resetOne(CommandSourceStack source, ServerPlayer player, String spellId) {
        String normalizedSpellId = normalizeSpellId(spellId);
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        data.setSpellProficiency(normalizedSpellId, 0.0f);
        SpellProficiencySyncS2CPayload.sendTo(player, normalizedSpellId, 0.0f);
        source.sendSuccess(() -> Component.literal("Reset " + normalizedSpellId + " proficiency."), true);
        return 1;
    }

    private static int resetAll(CommandSourceStack source, ServerPlayer player) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        for (String id : data.getKnownSpells()) {
            data.setSpellProficiency(id, 0.0f);
        }
        SpellDataSyncS2CPayload.syncToPlayer(player);
        source.sendSuccess(() -> Component.literal("Reset all spell proficiencies."), true);
        return 1;
    }

    private static int setAll(CommandSourceStack source, ServerPlayer player, float value) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        for (String id : data.getKnownSpells()) {
            data.setSpellProficiency(id, value);
        }
        SpellDataSyncS2CPayload.syncToPlayer(player);
        source.sendSuccess(() -> Component.literal(String.format("Set all proficiencies to %.3f", value)), true);
        return 1;
    }

    private static int preview(CommandSourceStack source, ServerPlayer player, String spellId) {
        String normalizedSpellId = normalizeSpellId(spellId);
        Identifier key;
        try {
            key = Identifier.parse(normalizedSpellId);
        } catch (Exception ignored) {
            key = Identifier.fromNamespaceAndPath("wizards_and_beasts", normalizedSpellId);
        }
        float value = player.getData(ModAttachments.SPELL_DATA.get()).getSpellProficiency(normalizedSpellId);
        SpellScalingProfile profile = ProficiencyScaler.getProfileForPlayer(player, key);
        source.sendSuccess(() -> Component.literal(String.format("Proficiency for %s: %.3f", normalizedSpellId, value)), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "  damage x%.2f | cooldown x%.2f | duration x%.2f | control x%.2f | accuracy x%.2f",
                profile.damageMult(), profile.cooldownMult(), profile.durationMult(), profile.controlMult(), profile.accuracyMult())), false);
        return 1;
    }

    private static String normalizeSpellId(String spellId) {
        Spell spell = Spells.byId(spellId);
        return spell != null ? spell.getId() : spellId;
    }
}
