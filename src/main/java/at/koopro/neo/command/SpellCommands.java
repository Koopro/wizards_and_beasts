package at.koopro.neo.command;

import at.koopro.neo.data.PlayerSpellData;
import at.koopro.neo.item.WandItem;
import at.koopro.neo.network.SpellDataSyncS2CPacket;
import at.koopro.neo.registry.ModAttachments;

import at.koopro.neo.spell.Proficiency;
import at.koopro.neo.skill.SkillSystemAPI;
import at.koopro.neo.spell.Spell;
import at.koopro.neo.spell.SpellCategory;
import at.koopro.neo.spell.SpellRequirement;
import at.koopro.neo.spell.Spells;
import at.koopro.neo.spell.wand.WandStats;
import at.koopro.neo.spell.wand.WandStatsResolver;
import at.koopro.neo.util.WandHelper;
import at.koopro.neo.item.wand.WandCore;
import at.koopro.neo.item.wand.WandFlexibility;
import at.koopro.neo.item.wand.WandLength;
import at.koopro.neo.item.wand.WandWood;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
                .then(Commands.literal("reset")
                        .executes(ctx -> resetSpells(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("learnall")
                        .executes(ctx -> learnAllSpells(ctx.getSource().getPlayerOrException())));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> registerWandCommand() {
        return Commands.literal("wand")
                .then(Commands.literal("give")
                        .then(Commands.argument("wood", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(WandWood.values()).map(WandWood::getSerializedName), builder))
                                .then(Commands.argument("core", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(WandCore.values()).map(WandCore::getSerializedName), builder))
                                        .executes(ctx -> giveWand(
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "wood"),
                                                StringArgumentType.getString(ctx, "core"))))));
    }

    private static int learnSpell(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            player.displayClientMessage(Component.literal("\u00A7cUnknown spell: " + spellId), false);
            return 0;
        }

        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        if (data.knowsSpell(spellId)) {
            player.displayClientMessage(Component.literal("\u00A7eYou already know " + spell.getDisplayName() + "."), false);
            return 0;
        }

        data.learnSpell(spellId);
        SpellDataSyncS2CPacket.syncToPlayer(player);
        player.displayClientMessage(Component.literal("\u00A7aLearned " + spell.getDisplayName() + "!"), false);
        return 1;
    }

    private static int forgetSpell(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            player.displayClientMessage(Component.literal("\u00A7cUnknown spell: " + spellId), false);
            return 0;
        }

        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        data.forgetSpell(spellId);
        SpellDataSyncS2CPacket.syncToPlayer(player);
        player.displayClientMessage(Component.literal("\u00A7eForgot " + spell.getDisplayName() + "."), false);
        return 1;
    }

    private static int listSpells(ServerPlayer player) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        if (data.getKnownSpells().isEmpty()) {
            player.displayClientMessage(Component.literal("\u00A77You don't know any spells."), false);
            return 0;
        }

        player.displayClientMessage(Component.literal("\u00A76Known Spells:"), false);
        SpellCategory currentCat = null;
        for (Spell spell : Spells.all()) {
            if (!data.knowsSpell(spell.getId())) continue;

            if (spell.getCategory() != currentCat) {
                currentCat = spell.getCategory();
                String catName = currentCat.name().replace('_', ' ');
                player.displayClientMessage(Component.literal(" \u00A7e" + catName), false);
            }

            int casts = data.getCastCount(spell.getId());
            Proficiency prof = Proficiency.fromCastCount(casts);
            String profIcon = switch (prof) {
                case MASTERED -> "\u00A76\u2605";
                case PROFICIENT -> "\u00A7e\u25C9";
                default -> "\u00A77\u25CB";
            };
            String status = spell.getProperties() != null ? "\u00A7a" : "\u00A78";
            player.displayClientMessage(Component.literal("  " + profIcon + " " + status
                    + spell.getDisplayName() + " \u00A78(" + casts + " casts)"), false);
        }
        return 1;
    }

    private static int spellInfo(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            player.displayClientMessage(Component.literal("\u00A7cUnknown spell: " + spellId), false);
            return 0;
        }

        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        boolean known = data.knowsSpell(spellId);

        player.displayClientMessage(Component.literal("\u00A76--- " + spell.getDisplayName() + " ---"), false);
        player.displayClientMessage(Component.literal("\u00A77Category: \u00A7f"
                + spell.getCategory().name().replace('_', ' ')), false);
        player.displayClientMessage(Component.literal("\u00A77Cooldown: \u00A7f"
                + String.format("%.1fs", spell.getBaseCooldownTicks() / 20.0f)), false);
        if (spell.getBaseDamage() > 0) {
            player.displayClientMessage(Component.literal("\u00A77Damage: \u00A7f"
                    + String.format("%.1f", spell.getBaseDamage())), false);
        }
        player.displayClientMessage(Component.literal("\u00A77Status: "
                + (known ? "\u00A7aLearned" : "\u00A7cNot learned")), false);

        if (known) {
            int casts = data.getCastCount(spellId);
            Proficiency prof = Proficiency.fromCastCount(casts);
            String profName = switch (prof) {
                case MASTERED -> "\u00A76Mastered";
                case PROFICIENT -> "\u00A7eProficient";
                default -> "\u00A77Novice";
            };
            int nextThreshold = switch (prof) {
                case NOVICE -> Proficiency.PROFICIENT.getCastsRequired();
                case PROFICIENT -> Proficiency.MASTERED.getCastsRequired();
                case MASTERED -> casts;
            };
            String progress = prof == Proficiency.MASTERED
                    ? profName + " \u00A78(" + casts + " casts)"
                    : profName + " \u00A78(" + casts + "/" + nextThreshold + ")";
            player.displayClientMessage(Component.literal("\u00A77Proficiency: " + progress), false);
        }

        SpellRequirement req = spell.getRequirement();
        if (req != null && req != SpellRequirement.NONE) {
            boolean met = req.isMet(data);
            String reqColor = met ? "\u00A7a" : "\u00A7c";
            player.displayClientMessage(Component.literal("\u00A77Requirement: "
                    + reqColor + req.getDescription()), false);
        }

        ItemStack wandStack = WandHelper.getWandStack(player);
        if (!wandStack.isEmpty()) {
            WandStats wand = WandStatsResolver.resolve(wandStack);
            float skillDamageMult = SkillSystemAPI.getDamageMultiplier(player, spell);
            float skillCooldownMult = SkillSystemAPI.getCooldownMultiplier(player, spell);
            float effectiveDamage = spell.getBaseDamage() * skillDamageMult * wand.damageFor(spell);
            float effectiveCooldownTicks = spell.getBaseCooldownTicks() * skillCooldownMult * wand.cooldownFor(spell);

            player.displayClientMessage(Component.literal(
                    "\u00A77With current wand: \u00A7f" + String.format("%.2f", wand.damageFor(spell))
                            + "x dmg, \u00A7f" + String.format("%.2f", wand.cooldownFor(spell))
                            + "x cd, \u00A7f" + String.format("%.2f", wand.rangeFor(spell)) + "x range"
                            + (wand.fizzleChance() > 0
                                ? "\u00A7c, " + String.format("%.0f%%", wand.fizzleChance() * 100f) + " fizzle"
                                : "")), false);
            if (spell.getBaseDamage() > 0) {
                player.displayClientMessage(Component.literal("\u00A77Effective damage: \u00A7f"
                        + String.format("%.1f", effectiveDamage)), false);
            }
            player.displayClientMessage(Component.literal("\u00A77Effective cooldown: \u00A7f"
                    + String.format("%.1fs", effectiveCooldownTicks / 20.0f)), false);
        } else {
            player.displayClientMessage(Component.literal(
                    "\u00A78(hold a wand to see wand-modulated stats)"), false);
        }

        return 1;
    }

    private static int resetSpells(ServerPlayer player) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        data.resetAll();
        SpellDataSyncS2CPacket.syncToPlayer(player);
        player.displayClientMessage(Component.literal("\u00A7eAll spell knowledge has been reset."), false);
        return 1;
    }

    private static int learnAllSpells(ServerPlayer player) {
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        for (Spell spell : Spells.all()) {
            data.learnSpell(spell.getId());
        }
        SpellDataSyncS2CPacket.syncToPlayer(player);
        player.displayClientMessage(Component.literal("\u00A7aLearned all " + Spells.count() + " spells!"), false);
        return 1;
    }

    private static int giveWand(ServerPlayer player, String woodName, String coreName) {
        WandWood wood = WandWood.byName(woodName);
        WandCore core = WandCore.byName(coreName);

        if (wood == null) {
            player.displayClientMessage(Component.literal("\u00A7cUnknown wood: " + woodName), false);
            return 0;
        }
        if (core == null) {
            player.displayClientMessage(Component.literal("\u00A7cUnknown core: " + coreName), false);
            return 0;
        }

        WandLength[] lengths = WandLength.values();
        WandFlexibility[] flexes = WandFlexibility.values();
        WandLength length = lengths[player.getRandom().nextInt(lengths.length)];
        WandFlexibility flex = flexes[player.getRandom().nextInt(flexes.length)];

        ItemStack wand = WandItem.createWand(wood, core, length, flex);
        player.getInventory().add(wand);

        player.displayClientMessage(Component.literal(
                "\u00A7aGiven " + wood.getDisplayName() + " wand with " + core.getDisplayName() + " core ("
                        + length.getDisplayName() + ", " + flex.getDisplayName() + ")"), false);
        return 1;
    }
}
