package at.koopro.wizardsandbeasts.spell.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.util.WandHelper;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

public final class SpellCommands {

    private SpellCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> registerSpellCommand() {
        return Commands.literal("spell")
                .then(Commands.literal("learn")
                        .then(Commands.argument("spell", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Spells.all().stream().map(Spell::getId), builder))
                                .executes(ctx -> learnSpell(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "spell")))))
                .then(Commands.literal("forget")
                        .then(Commands.argument("spell", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Spells.all().stream().map(Spell::getId), builder))
                                .executes(ctx -> forgetSpell(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "spell")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listSpells(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("info")
                        .then(Commands.argument("spell", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Spells.all().stream().map(Spell::getId), builder))
                                .executes(ctx -> spellInfo(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "spell")))))
                .then(Commands.literal("patronus")
                        .then(Commands.literal("form")
                                .then(Commands.literal("reveal")
                                        .executes(ctx -> revealPatronusForm(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> clearPatronusForm(ctx.getSource().getPlayerOrException())))))
                .then(Commands.literal("reset")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .executes(ctx -> resetSpells(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("learnall")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .executes(ctx -> learnAllSpells(ctx.getSource().getPlayerOrException())));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> registerGiveCommand() {
        return Commands.literal("give")
                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                .then(Commands.argument("wood", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(WandWood.values()).map(WandWood::getSerializedName), builder))
                        .then(Commands.argument("core", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(WandCore.values()).map(WandCore::getSerializedName), builder))
                                .executes(ctx -> giveWand(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "wood"),
                                        StringArgumentType.getString(ctx, "core")))));
    }

    private static int learnSpell(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            player.displayClientMessage(Component.literal("Unknown spell: " + spellId).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        if (data.knowsSpell(spellId)) {
            player.displayClientMessage(Component.translatable("spell.wizards_and_beasts.cmd.already_known", Component.translatable(spell.getDisplayName())).withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        data.learnSpell(spellId);
        PlayerStateSyncService.syncSpells(player);
        player.displayClientMessage(Component.translatable("spell.wizards_and_beasts.cmd.learned", Component.translatable(spell.getDisplayName())).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int forgetSpell(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            player.displayClientMessage(Component.literal("Unknown spell: " + spellId).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        data.forgetSpell(spellId);
        PlayerStateSyncService.syncSpells(player);
        player.displayClientMessage(Component.translatable("spell.wizards_and_beasts.cmd.forgot", Component.translatable(spell.getDisplayName())).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int listSpells(ServerPlayer player) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        if (data.getKnownSpells().isEmpty()) {
            player.displayClientMessage(Component.literal("You don't know any spells.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        player.displayClientMessage(Component.literal("Known Spells:").withStyle(ChatFormatting.GOLD), false);
        SpellCategory currentCat = null;
        for (Spell spell : Spells.all()) {
            if (!data.knowsSpell(spell.getId())) continue;

            if (spell.getCategory() != currentCat) {
                currentCat = spell.getCategory();
                String catName = currentCat.name().replace('_', ' ');
                player.displayClientMessage(Component.literal(" " + catName).withStyle(ChatFormatting.YELLOW), false);
            }

            int casts = data.getSuccessfulHits(spell.getId());
            Proficiency prof = Proficiency.fromCastCount(casts);
            String profIcon = switch (prof) {
                case MASTERED -> "★";
                case PROFICIENT -> "◉";
                default -> "○";
            };
            ChatFormatting profColor = switch (prof) {
                case MASTERED -> ChatFormatting.GOLD;
                case PROFICIENT -> ChatFormatting.YELLOW;
                default -> ChatFormatting.GRAY;
            };
            ChatFormatting statusColor = spell.getProperties() != null ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
            player.displayClientMessage(
                    Component.literal("  " + profIcon + " ").withStyle(profColor)
                            .append(Component.translatable(spell.getDisplayName()).withStyle(statusColor))
                            .append(Component.literal(" (" + casts + " casts)").withStyle(ChatFormatting.DARK_GRAY)),
                    false);
        }
        return 1;
    }

    private static int spellInfo(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            player.displayClientMessage(Component.literal("Unknown spell: " + spellId).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        boolean known = data.knowsSpell(spellId);

        player.displayClientMessage(Component.translatable("spell.wizards_and_beasts.cmd.header", Component.translatable(spell.getDisplayName())).withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.literal("Category: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(spell.getCategory().name().replace('_', ' ')).withStyle(ChatFormatting.WHITE)), false);
        player.displayClientMessage(Component.literal("Cooldown: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%.1fs", spell.getBaseCooldownTicks() / 20.0f)).withStyle(ChatFormatting.WHITE)), false);
        if (spell.getBaseDamage() > 0) {
            player.displayClientMessage(Component.literal("Damage: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format("%.1f", spell.getBaseDamage())).withStyle(ChatFormatting.WHITE)), false);
        }
        player.displayClientMessage(Component.literal("Status: ").withStyle(ChatFormatting.GRAY)
                .append(known
                        ? Component.literal("Learned").withStyle(ChatFormatting.GREEN)
                        : Component.literal("Not learned").withStyle(ChatFormatting.RED)), false);

        if (known) {
            int casts = data.getSuccessfulHits(spellId);
            Proficiency prof = Proficiency.fromCastCount(casts);
            String profName = switch (prof) {
                case MASTERED -> "Mastered";
                case PROFICIENT -> "Proficient";
                default -> "Novice";
            };
            ChatFormatting profColor = switch (prof) {
                case MASTERED -> ChatFormatting.GOLD;
                case PROFICIENT -> ChatFormatting.YELLOW;
                default -> ChatFormatting.GRAY;
            };
            int nextThreshold = switch (prof) {
                case NOVICE -> Proficiency.PROFICIENT.getCastsRequired();
                case PROFICIENT -> Proficiency.MASTERED.getCastsRequired();
                case MASTERED -> casts;
            };
            String progress = prof == Proficiency.MASTERED
                    ? profName + " (" + casts + " casts)"
                    : profName + " (" + casts + "/" + nextThreshold + ")";
            player.displayClientMessage(Component.literal("Proficiency: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(progress).withStyle(profColor)), false);
        }

        SpellRequirement req = spell.getRequirement();
        if (req != null && req != SpellRequirement.NONE) {
            boolean met = req.isMet(player, data);
            player.displayClientMessage(Component.literal("Requirement: ").withStyle(ChatFormatting.GRAY)
                    .append(req.describe().copy().withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        }

        ItemStack wandStack = WandHelper.getWandStack(player);
        if (!wandStack.isEmpty()) {
            WandStats wand = WandStatsResolver.resolve(wandStack, player.registryAccess());
            float skillDamageMult = SkillSystemAPI.getDamageMultiplier(player, spell);
            float skillCooldownMult = SkillSystemAPI.getCooldownMultiplier(player, spell);
            float effectiveDamage = spell.getBaseDamage() * skillDamageMult * wand.damageFor(spell);
            float effectiveCooldownTicks = spell.getBaseCooldownTicks() * skillCooldownMult * wand.cooldownFor(spell);

            player.displayClientMessage(Component.literal("With current wand: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format("%.2f", wand.damageFor(spell)) + "x dmg, "
                            + String.format("%.2f", wand.cooldownFor(spell)) + "x cd, "
                            + String.format("%.2f", wand.rangeFor(spell)) + "x range"
                            + (wand.fizzleChance() > 0
                                ? ", " + String.format("%.0f%%", wand.fizzleChance() * 100f) + " fizzle"
                                : "")).withStyle(ChatFormatting.WHITE)), false);
            if (spell.getBaseDamage() > 0) {
                player.displayClientMessage(Component.literal("Effective damage: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.format("%.1f", effectiveDamage)).withStyle(ChatFormatting.WHITE)), false);
            }
            player.displayClientMessage(Component.literal("Effective cooldown: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format("%.1fs", effectiveCooldownTicks / 20.0f)).withStyle(ChatFormatting.WHITE)), false);
        } else {
            player.displayClientMessage(Component.literal("(hold a wand to see wand-modulated stats)").withStyle(ChatFormatting.DARK_GRAY), false);
        }

        return 1;
    }

    private static int revealPatronusForm(ServerPlayer player) {
        float happiness = player.getData(ModAttachments.HAPPINESS.get());
        net.minecraft.resources.Identifier form =
                at.koopro.wizardsandbeasts.spell.patronus.PatronusFormDeterminer.determine(
                        at.koopro.wizardsandbeasts.heritage.HeritageAPI.getPlayerHeritage(player),
                        at.koopro.wizardsandbeasts.heritage.HeritageAPI.getPlayerHeritageVariant(player),
                        happiness);
        if (form == null) {
            player.displayClientMessage(
                    Component.translatable("spell.wizards_and_beasts.expecto_patronum.reject.heritage")
                            .withStyle(ChatFormatting.GRAY),
                    false);
            return 0;
        }
        String formId = form.toString();
        player.setData(ModAttachments.PATRONUS_FORM.get(), formId);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new at.koopro.wizardsandbeasts.network.spell.PatronusFormSetS2CPayload(formId));
        player.displayClientMessage(
                Component.translatable("spell.wizards_and_beasts.expecto_patronum.form_revealed",
                                formLabel(form)).withStyle(ChatFormatting.AQUA),
                false);
        return 1;
    }

    private static int clearPatronusForm(ServerPlayer player) {
        player.setData(ModAttachments.PATRONUS_FORM.get(), "");
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new at.koopro.wizardsandbeasts.network.spell.PatronusFormSetS2CPayload(""));
        player.displayClientMessage(
                Component.translatable("spell.wizards_and_beasts.expecto_patronum.form_cleared")
                        .withStyle(ChatFormatting.YELLOW),
                false);
        return 1;
    }

    /** {@code minecraft:polar_bear} → {@code Polar Bear} for chat display. */
    private static Component formLabel(net.minecraft.resources.Identifier form) {
        String[] words = form.getPath().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return Component.literal(sb.toString());
    }

    private static int resetSpells(ServerPlayer player) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        data.resetAll();
        PlayerStateSyncService.syncSpells(player);
        player.displayClientMessage(Component.literal("All spell knowledge has been reset.").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int learnAllSpells(ServerPlayer player) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        for (Spell spell : Spells.all()) {
            data.learnSpell(spell.getId());
        }
        PlayerStateSyncService.syncSpells(player);
        player.displayClientMessage(Component.literal("Learned all " + Spells.count() + " spells!").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int giveWand(ServerPlayer player, String woodName, String coreName) {
        WandWood wood = WandWood.byName(woodName);
        WandCore core = WandCore.byName(coreName);

        if (wood == null) {
            player.displayClientMessage(Component.literal("Unknown wood: " + woodName).withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (core == null) {
            player.displayClientMessage(Component.literal("Unknown core: " + coreName).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        WandLength[] lengths = WandLength.values();
        WandFlexibility[] flexes = WandFlexibility.values();
        WandLength length = lengths[player.getRandom().nextInt(lengths.length)];
        WandFlexibility flex = flexes[player.getRandom().nextInt(flexes.length)];

        ItemStack wand = WandItem.createWand(wood, core, length, flex);
        player.getInventory().add(wand);

        player.displayClientMessage(Component.literal(
                "Given " + wood.getDisplayName() + " wand with " + core.getDisplayName() + " core ("
                        + length.getDisplayName() + ", " + flex.getDisplayName() + ")").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
