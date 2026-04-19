package at.koopro.neo.command;

import at.koopro.neo.data.PlayerSkillData;
import at.koopro.neo.network.SkillDataSyncS2CPacket;
import at.koopro.neo.network.SpellDataSyncS2CPacket;
import at.koopro.neo.registry.ModAttachments;
import at.koopro.neo.skill.*;
import at.koopro.neo.util.ChatHelper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;

public final class SkillCommands {

    private SkillCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("skill")
                .then(Commands.literal("points")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> showPoints(EntityArgument.getPlayer(ctx, "player")))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> addPoints(
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setPoints(
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                SkillTrees.allIds(), builder))
                                        .executes(ctx -> forceUnlock(
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "skill"))))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> resetAll(EntityArgument.getPlayer(ctx, "player")))
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                SkillTrees.allIds(), builder))
                                        .executes(ctx -> resetSkill(
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "skill"))))))
                .then(Commands.literal("info")
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        SkillTrees.allIds(), builder))
                                .executes(ctx -> showInfo(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "skill")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listAll(ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("tree", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(SkillTreeId.values()).map(SkillTreeId::getId), builder))
                                .executes(ctx -> listTree(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "tree")))));
    }

    private static int showPoints(ServerPlayer player) {
        PlayerSkillData data = player.getData(ModAttachments.SKILL_DATA.get());
        ChatHelper.send(player, ChatHelper.gold("Skill Points: ")
                .append(ChatHelper.white(String.valueOf(data.getSkillPoints())))
                .append(ChatHelper.dim(" (" + data.getTotalPointsEarned() + " total earned)")));
        return 1;
    }

    private static int addPoints(ServerPlayer player, int amount) {
        SkillSystemAPI.awardPoints(player, amount);
        SkillDataSyncS2CPacket.syncToPlayer(player);
        ChatHelper.sendSuccess(player, "Added " + amount + " skill points.");
        return 1;
    }

    private static int setPoints(ServerPlayer player, int amount) {
        PlayerSkillData data = player.getData(ModAttachments.SKILL_DATA.get());
        data.setSkillPoints(amount);
        SkillDataSyncS2CPacket.syncToPlayer(player);
        ChatHelper.sendSuccess(player, "Set skill points to " + amount + ".");
        return 1;
    }

    private static int forceUnlock(ServerPlayer player, String skillId) {
        Skill skill = SkillTrees.byId(skillId);
        if (skill == null) {
            ChatHelper.sendError(player, "Unknown skill: " + skillId);
            return 0;
        }

        SkillSystemAPI.forceUnlock(player, skillId);
        SkillDataSyncS2CPacket.syncToPlayer(player);
        SpellDataSyncS2CPacket.syncToPlayer(player);
        ChatHelper.sendSuccess(player, "Force-unlocked " + skill.getDisplayName()
                + " (level " + skill.getMaxLevel() + ").");
        return 1;
    }

    private static int resetAll(ServerPlayer player) {
        PlayerSkillData data = player.getData(ModAttachments.SKILL_DATA.get());
        data.resetAll();
        SkillDataSyncS2CPacket.syncToPlayer(player);
        ChatHelper.sendSuccess(player, "All skills reset. Points refunded.");
        return 1;
    }

    private static int resetSkill(ServerPlayer player, String skillId) {
        Skill skill = SkillTrees.byId(skillId);
        if (skill == null) {
            ChatHelper.sendError(player, "Unknown skill: " + skillId);
            return 0;
        }

        PlayerSkillData data = player.getData(ModAttachments.SKILL_DATA.get());
        if (!data.hasSkill(skillId)) {
            ChatHelper.sendError(player, skill.getDisplayName() + " is not unlocked.");
            return 0;
        }

        data.resetSkill(skillId);
        SkillDataSyncS2CPacket.syncToPlayer(player);
        ChatHelper.sendSuccess(player, "Reset " + skill.getDisplayName() + ". Points refunded.");
        return 1;
    }

    private static int showInfo(ServerPlayer player, String skillId) {
        Skill skill = SkillTrees.byId(skillId);
        if (skill == null) {
            ChatHelper.sendError(player, "Unknown skill: " + skillId);
            return 0;
        }

        PlayerSkillData data = player.getData(ModAttachments.SKILL_DATA.get());
        int level = data.getSkillLevel(skillId);

        ChatHelper.send(player, ChatHelper.gold("--- " + skill.getDisplayName() + " ---"));
        ChatHelper.send(player, ChatHelper.info("Tree: ").append(
                ChatHelper.white(skill.getTree().getDisplayName())));
        ChatHelper.send(player, ChatHelper.info("Description: ").append(
                ChatHelper.white(skill.getDescription())));
        ChatHelper.send(player, ChatHelper.info("Cost: ").append(
                ChatHelper.white(skill.getPointCost() + " SP per level")));
        ChatHelper.send(player, ChatHelper.info("Level: ").append(
                ChatHelper.white(level + "/" + skill.getMaxLevel())));

        if (!skill.getPrerequisites().isEmpty()) {
            StringBuilder prereqs = new StringBuilder();
            for (String prereqId : skill.getPrerequisites()) {
                Skill prereq = SkillTrees.byId(prereqId);
                boolean met = data.isMaxed(prereqId);
                prereqs.append(met ? "\u00A7a" : "\u00A7c");
                prereqs.append(prereq != null ? prereq.getDisplayName() : prereqId);
                prereqs.append("\u00A77, ");
            }
            if (prereqs.length() > 4) prereqs.setLength(prereqs.length() - 4);
            ChatHelper.send(player, ChatHelper.info("Prerequisites: ").append(
                    ChatHelper.colored(prereqs.toString())));
        }

        for (SkillEffect effect : skill.getEffects()) {
            String desc = describeEffect(effect);
            ChatHelper.send(player, ChatHelper.dim("  " + desc));
        }

        return 1;
    }

    private static int listAll(ServerPlayer player) {
        PlayerSkillData data = player.getData(ModAttachments.SKILL_DATA.get());
        ChatHelper.send(player, ChatHelper.gold("=== Skill Trees ==="));

        for (SkillTreeId tree : SkillTreeId.values()) {
            List<Skill> skills = SkillTrees.getTree(tree);
            long unlocked = skills.stream()
                    .filter(s -> data.hasSkill(s.getId()))
                    .count();
            ChatHelper.send(player, ChatHelper.info(" " + tree.getDisplayName()
                    + " \u00A78(" + unlocked + "/" + skills.size() + " unlocked)"));
        }

        return 1;
    }

    private static int listTree(ServerPlayer player, String treeIdStr) {
        SkillTreeId treeId = SkillTreeId.byId(treeIdStr);
        if (treeId == null) {
            ChatHelper.sendError(player, "Unknown tree: " + treeIdStr);
            return 0;
        }

        PlayerSkillData data = player.getData(ModAttachments.SKILL_DATA.get());
        List<Skill> skills = SkillTrees.getTree(treeId);

        ChatHelper.send(player, ChatHelper.gold("=== " + treeId.getDisplayName() + " ==="));
        for (Skill skill : skills) {
            int level = data.getSkillLevel(skill.getId());
            String icon;
            if (level >= skill.getMaxLevel()) {
                icon = "\u00A76\u2605"; // gold star = maxed
            } else if (level > 0) {
                icon = "\u00A7e\u25C9"; // yellow circle = in progress
            } else {
                icon = "\u00A77\u25CB"; // gray circle = locked
            }
            String levelStr = skill.getMaxLevel() > 1
                    ? " \u00A78(" + level + "/" + skill.getMaxLevel() + ")"
                    : "";
            ChatHelper.send(player, ChatHelper.colored("  " + icon + " "
                    + (level > 0 ? "\u00A7a" : "\u00A77") + skill.getDisplayName()
                    + levelStr + " \u00A78[" + skill.getPointCost() + " SP]"));
        }

        return 1;
    }

    private static String describeEffect(SkillEffect effect) {
        return switch (effect) {
            case SkillEffect.LearnSpell e -> "Learns spell: " + e.spellId();
            case SkillEffect.SpellDamageBonus e ->
                    String.format("+%.0f%% %s damage per level", e.bonusPerLevel() * 100, e.spellId());
            case SkillEffect.SpellCooldownReduction e ->
                    String.format("-%.0f%% %s cooldown per level", e.reductionPerLevel() * 100, e.spellId());
            case SkillEffect.CategoryDamageBonus e ->
                    String.format("+%.0f%% %s damage per level", e.bonusPerLevel() * 100,
                            e.category().name().replace('_', ' ').toLowerCase());
            case SkillEffect.CategoryCooldownReduction e ->
                    String.format("-%.0f%% %s cooldown per level", e.reductionPerLevel() * 100,
                            e.category().name().replace('_', ' ').toLowerCase());
            case SkillEffect.PassiveAttribute e ->
                    String.format("+%.0f %s per level", e.amountPerLevel(), e.attributeId());
            case SkillEffect.UnlockAbility e -> "Unlocks ability: " + e.abilityId();
        };
    }
}
