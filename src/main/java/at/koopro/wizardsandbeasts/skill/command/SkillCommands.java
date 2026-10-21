package at.koopro.wizardsandbeasts.skill.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillEffect;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.build.BuildProfile;
import at.koopro.wizardsandbeasts.skill.build.BuildTier;
import at.koopro.wizardsandbeasts.skill.build.Builds;
import at.koopro.wizardsandbeasts.skill.build.SkillRespecService;
import at.koopro.wizardsandbeasts.skill.vocation.VocationDefinition;
import at.koopro.wizardsandbeasts.skill.vocation.VocationManager;
import at.koopro.wizardsandbeasts.skill.vocation.VocationRegistry;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import at.koopro.wizardsandbeasts.util.ChatHelper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;

public final class SkillCommands {

    private SkillCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("skill")
                .then(Commands.literal("points")
                        .executes(ctx -> showPoints(ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(WizardsAndBeastsCommandPermissions.ADMIN)
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
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        SkillTrees.allIds(), builder))
                                .executes(ctx -> tryUnlockSkill(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "skill")))))
                .then(Commands.literal("force_unlock")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                SkillTrees.allIds(), builder))
                                        .executes(ctx -> forceUnlock(
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "skill"))))))
                .then(Commands.literal("reset")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> resetAll(EntityArgument.getPlayer(ctx, "player")))
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                SkillTrees.allIds(), builder))
                                        .executes(ctx -> resetSkill(
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "skill"))))))
                .then(Commands.literal("respec")
                        .executes(ctx -> respec(ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("preview")
                                .executes(ctx -> previewRespec(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("unlearn")
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        ctx.getSource().getPlayer() == null ? List.<String>of()
                                                : ctx.getSource().getPlayer().getData(ModAttachments.SKILL_DATA.get())
                                                        .getUnlockedSkills().keySet(), builder))
                                .executes(ctx -> unlearn(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "skill"), false))
                                .then(Commands.literal("preview")
                                        .executes(ctx -> unlearn(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "skill"), true)))))
                .then(Commands.literal("build")
                        .executes(ctx -> showBuild(ctx.getSource().getPlayerOrException())))
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

    // ── Vocation specialization sub-tree (Skill System v2) ──
    // A sibling of `skill`, not a child: a vocation is a lifelong declaration, not one more
    // thing you spend skill points on, and burying it cost it a level of tab-completion depth.

    public static LiteralArgumentBuilder<CommandSourceStack> registerVocation() {
        return Commands.literal("vocation")
                .then(Commands.literal("info")
                        .executes(ctx -> vocationInfo(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("set")
                        .then(Commands.literal("primary")
                                .then(Commands.argument("vocation", StringArgumentType.string())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                vocationIdSuggestions(), builder))
                                        .executes(ctx -> setVocation(
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "vocation"))))))
                .then(Commands.literal("clear")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .executes(ctx -> clearVocation(ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> clearVocation(EntityArgument.getPlayer(ctx, "player")))));
    }

    private static Iterable<String> vocationIdSuggestions() {
        return VocationRegistry.all().stream().map(def -> def.id().toString()).toList();
    }

    private static Identifier parseVocationId(String raw) {
        int colon = raw.indexOf(':');
        if (colon < 0) {
            return Identifier.fromNamespaceAndPath("wizards_and_beasts", raw);
        }
        return Identifier.fromNamespaceAndPath(raw.substring(0, colon), raw.substring(colon + 1));
    }

    private static final String L = "command.wizards_and_beasts.skill.vocation.";

    private static boolean requireSkillTrees(ServerPlayer player) {
        if (!ModuleManager.isEnabled(Module.SKILL_TREES)) {
            ChatHelper.send(player, Component.translatable(L + "module_disabled").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    private static int vocationInfo(ServerPlayer player) {
        if (!requireSkillTrees(player)) {
            return 0;
        }
        ChatHelper.send(player, Component.translatable(L + "info.header").withStyle(ChatFormatting.GOLD));

        VocationDefinition declared = VocationManager.committed(player);
        Component value = declared != null
                ? declared.displayName()
                : Component.translatable(L + "info.none");
        ChatHelper.send(player, Component.translatable(L + "info.primary", value).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int setVocation(ServerPlayer player, String rawId) {
        if (!requireSkillTrees(player)) {
            return 0;
        }
        Identifier id = parseVocationId(rawId);
        VocationManager.CommitResult result = VocationManager.commit(player, id);
        switch (result) {
            case OK -> {
                ChatHelper.send(player, Component.translatable(L + "set.primary_ok", localizedOrId(id))
                        .withStyle(ChatFormatting.GREEN));
                return 1;
            }
            case MODULE_DISABLED -> ChatHelper.send(player,
                    Component.translatable(L + "module_disabled").withStyle(ChatFormatting.RED));
            case UNKNOWN_VOCATION -> ChatHelper.send(player,
                    Component.translatable(L + "set.unknown", id.toString()).withStyle(ChatFormatting.RED));
            case DARK_ARTS_DISABLED -> ChatHelper.send(player,
                    Component.translatable(L + "set.dark_arts_disabled").withStyle(ChatFormatting.RED));
            case BUILD_SEALED -> ChatHelper.send(player,
                    Component.translatable("vocation.wizards_and_beasts.commit.build_sealed").withStyle(ChatFormatting.RED));
            case MASTERY_HELD -> ChatHelper.send(player,
                    Component.translatable("vocation.wizards_and_beasts.commit.mastery_held").withStyle(ChatFormatting.RED));
        }
        return 0;
    }

    private static Component localizedOrId(Identifier id) {
        VocationDefinition def = VocationRegistry.get(id);
        return def != null ? def.displayName() : Component.literal(id.toString());
    }

    private static int clearVocation(ServerPlayer player) {
        VocationManager.clear(player);
        ChatHelper.send(player, Component.translatable(L + "clear.ok", player.getName())
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int tryUnlockSkill(ServerPlayer player, String skillId) {
        Skill skill = SkillTrees.byId(skillId);
        if (skill == null) {
            ChatHelper.sendError(player, "Unknown skill: " + skillId);
            return 0;
        }

        if (SkillSystemAPI.tryUnlock(player, skillId)) {
            ChatHelper.sendSuccess(player, net.minecraft.network.chat.Component.translatable(
                    "skill.wizards_and_beasts.unlock.success",
                    net.minecraft.network.chat.Component.translatable(skill.getDisplayName())));
            PlayerStateSyncService.syncSkills(player);
            return 1;
        }

        ChatHelper.sendError(player, "Cannot unlock " + skill.getDisplayName() + ".");
        return 0;
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
        PlayerStateSyncService.syncSkills(player);
        ChatHelper.sendSuccess(player, "Added " + amount + " skill points.");
        return 1;
    }

    private static int setPoints(ServerPlayer player, int amount) {
        PlayerSkillData data = player.getData(ModAttachments.SKILL_DATA.get());
        data.setSkillPoints(amount);
        SkillSystemAPI.reconcileDerivedEffects(player);
        PlayerStateSyncService.syncSkills(player);
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
        PlayerStateSyncService.syncSkills(player);
        ChatHelper.sendSuccess(player, "Force-unlocked " + skill.getDisplayName()
                + " (level " + skill.getMaxLevel() + ").");
        return 1;
    }

    private static int resetAll(ServerPlayer player) {
        PlayerSkillData data = player.getData(ModAttachments.SKILL_DATA.get());
        data.resetAll();
        SkillSystemAPI.reconcileDerivedEffects(player);
        PlayerStateSyncService.syncSkills(player);
        PlayerStateSyncService.syncAbilityGrants(player); // refund strips SKILL_NODE-source grants
        ChatHelper.sendSuccess(player, "All skills reset. Points refunded.");
        return 1;
    }

    private static final String R = "command.wizards_and_beasts.skill.respec.";

    /** Self-serve respec: any player may refund and clear their own skills. Priced by {@code RespecPricing}. */
    private static int respec(ServerPlayer player) {
        SkillRespecService.Outcome outcome = SkillRespecService.respec(player);
        if (!outcome.done()) {
            ChatHelper.send(player, refusal(outcome.result(), outcome.quote()));
            return 0;
        }
        SkillRespecService.Quote quote = outcome.quote();
        ChatHelper.send(player, Component.translatable(R + "done", quote.refundPoints(), fee(quote))
                .withStyle(ChatFormatting.GREEN));
        if (!quote.spellsForgotten().isEmpty()) {
            ChatHelper.send(player, Component.translatable(R + "forgot", spellList(quote.spellsForgotten()))
                    .withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    /** What a full respec would do, without doing it. */
    private static int previewRespec(ServerPlayer player) {
        SkillRespecService.Quote quote = SkillRespecService.quoteRespec(player);
        if (!quote.allowed()) {
            ChatHelper.send(player, refusal(quote.reason(), quote));
            return 0;
        }
        ChatHelper.send(player, Component.translatable(R + "preview.header").withStyle(ChatFormatting.GOLD));
        describeQuote(player, quote);
        ChatHelper.send(player, Component.translatable(R + "preview.confirm").withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    /** Unlearn one level of one node, or (preview) say what that would do. */
    private static int unlearn(ServerPlayer player, String skillId, boolean preview) {
        Skill skill = SkillTrees.byId(skillId);
        Component name = skill == null ? Component.literal(skillId) : Component.translatable(skill.getDisplayName());
        if (preview) {
            SkillRespecService.Quote quote = SkillRespecService.quoteUnlearn(player, skillId);
            if (!quote.allowed()) {
                ChatHelper.send(player, refusal(quote.reason(), quote));
                return 0;
            }
            ChatHelper.send(player, Component.translatable(R + "unlearn.preview", name).withStyle(ChatFormatting.GOLD));
            describeQuote(player, quote);
            return 1;
        }
        SkillRespecService.Outcome outcome = SkillRespecService.unlearn(player, skillId);
        if (!outcome.done()) {
            ChatHelper.send(player, refusal(outcome.result(), outcome.quote()));
            return 0;
        }
        ChatHelper.send(player, Component.translatable(R + "unlearn.done", name, outcome.quote().refundPoints(),
                fee(outcome.quote())).withStyle(ChatFormatting.GREEN));
        if (!outcome.quote().spellsForgotten().isEmpty()) {
            ChatHelper.send(player, Component.translatable(R + "forgot", spellList(outcome.quote().spellsForgotten()))
                    .withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static void describeQuote(ServerPlayer player, SkillRespecService.Quote quote) {
        ChatHelper.send(player, Component.translatable(R + "line.refund", quote.refundPoints())
                .withStyle(ChatFormatting.GRAY));
        ChatHelper.send(player, Component.translatable(R + "line.fee", fee(quote)).withStyle(ChatFormatting.GRAY));
        ChatHelper.send(player, Component.translatable(R + "line.spells",
                quote.spellsForgotten().isEmpty() ? Component.translatable(R + "line.none")
                        : spellList(quote.spellsForgotten())).withStyle(ChatFormatting.GRAY));
        if (quote.corruptionKept() > 0f) {
            ChatHelper.send(player, Component.translatable(R + "line.corruption",
                    String.format(java.util.Locale.ROOT, "%.0f", quote.corruptionKept()))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private static Component fee(SkillRespecService.Quote quote) {
        if (quote.feeKnuts() <= 0) {
            return Component.translatable(quote.free() ? R + "fee.first_free" : R + "fee.free");
        }
        return Component.literal(CurrencyHelper.formatFromKnuts(quote.feeKnuts()));
    }

    private static Component spellList(List<String> spellIds) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < spellIds.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            String id = spellIds.get(i);
            String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            out.append(Component.translatable("spell.wizards_and_beasts." + path + ".name"));
        }
        return out;
    }

    private static Component refusal(String reason, SkillRespecService.Quote quote) {
        if ("would_strand".equals(reason)) {
            net.minecraft.network.chat.MutableComponent names = Component.empty();
            int i = 0;
            for (String id : quote.stranded()) {
                if (i++ > 0) {
                    names.append(", ");
                }
                Skill stranded = SkillTrees.byId(id);
                names.append(stranded == null ? Component.literal(id) : Component.translatable(stranded.getDisplayName()));
            }
            return Component.translatable(R + "refused.would_strand", names).withStyle(ChatFormatting.RED);
        }
        if ("cannot_afford".equals(reason)) {
            return Component.translatable(R + "refused.cannot_afford", fee(quote)).withStyle(ChatFormatting.RED);
        }
        return Component.translatable(R + "refused." + reason).withStyle(ChatFormatting.RED);
    }

    /** The player's build in chat: the same profile the character sheet draws. */
    private static int showBuild(ServerPlayer player) {
        BuildProfile profile = Builds.profileOf(player);
        String b = "command.wizards_and_beasts.skill.build.";
        Component current = profile.current() == null
                ? Component.translatable(b + "none")
                : Component.translatable(profile.current().nameKey());
        ChatHelper.send(player, Component.translatable(b + "status."
                + profile.status().name().toLowerCase(java.util.Locale.ROOT), current).withStyle(ChatFormatting.GOLD));
        for (BuildProfile.PathwayProgress p : profile.pathways()) {
            ChatHelper.send(player, Component.translatable(b + "pathway",
                    Component.translatable(p.archetype().nameKey()),
                    p.count(BuildTier.EARLY), p.size(BuildTier.EARLY),
                    p.count(BuildTier.MID), p.size(BuildTier.MID),
                    p.count(BuildTier.ADVANCED), p.size(BuildTier.ADVANCED),
                    Component.translatable(p.capstoneHeld() ? b + "capstone.held" : b + "capstone.open"))
                    .withStyle(ChatFormatting.GRAY));
            for (String next : p.nextChoices()) {
                Skill s = SkillTrees.byId(next);
                if (s != null) {
                    ChatHelper.send(player, Component.translatable(b + "next",
                            Component.translatable(s.getDisplayName()), s.getPointCost())
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        if (profile.pathways().isEmpty()) {
            ChatHelper.send(player, Component.translatable(b + "empty").withStyle(ChatFormatting.GRAY));
        }
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
        // Same reason as the full respec: resetting one node has to take back that node's lesson too.
        int forgotten = SkillSystemAPI.revokeWebTaughtSpells(player);
        SkillSystemAPI.reconcileDerivedEffects(player);
        PlayerStateSyncService.syncSkills(player);
        PlayerStateSyncService.syncAbilityGrants(player); // refund strips SKILL_NODE-source grants
        ChatHelper.sendSuccess(player, forgotten > 0
                ? "Reset " + skill.getDisplayName() + ". Points refunded, "
                        + forgotten + " web-taught spell(s) forgotten."
                : "Reset " + skill.getDisplayName() + ". Points refunded.");
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

        var neighbors = SkillTrees.neighbors(skillId);
        if (!neighbors.isEmpty()) {
            StringBuilder connected = new StringBuilder();
            for (String neighborId : neighbors) {
                Skill neighbor = SkillTrees.byId(neighborId);
                boolean allocated = data.hasSkill(neighborId);
                connected.append(allocated ? "§a" : "§c");
                connected.append(neighbor != null ? neighbor.getDisplayName() : neighborId);
                connected.append("§7, ");
            }
            if (connected.length() > 4) connected.setLength(connected.length() - 4);
            ChatHelper.send(player, ChatHelper.info("Connected: ").append(
                    ChatHelper.colored(connected.toString())));
        }
        if (skill.isRoot()) {
            ChatHelper.send(player, ChatHelper.dim("  Web root — always allocatable."));
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
                    + " §8(" + unlocked + "/" + skills.size() + " unlocked)"));
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
                icon = "§6★";
            } else if (level > 0) {
                icon = "§e◉";
            } else {
                icon = "§7○";
            }
            String levelStr = skill.getMaxLevel() > 1
                    ? " §8(" + level + "/" + skill.getMaxLevel() + ")"
                    : "";
            ChatHelper.send(player, ChatHelper.colored("  " + icon + " "
                    + (level > 0 ? "§a" : "§7") + skill.getDisplayName()
                    + levelStr + " §8[" + skill.getPointCost() + " SP]"));
        }

        return 1;
    }

    private static String describeEffect(SkillEffect effect) {
        return switch (effect) {
            case SkillEffect.LearnSpell e -> "Spell study path: " + e.spellId();
            case SkillEffect.DarkStudy e ->
                    String.format("+%.0f dark corruption per level, on allocation", e.corruption());
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
            case SkillEffect.GameplayBonus e ->
                    String.format("+%.0f%% %s per level", e.perLevel() * 100,
                            e.stat().name().replace('_', ' ').toLowerCase());
            case SkillEffect.GrantAbility e -> "Grants ability: " + e.ability().id();
            case SkillEffect.AbilityRefinement e ->
                    String.format("Refines %s (%s) +%.2f per level",
                            e.ability().id(), e.axis().getSerializedName(), e.magnitudePerLevel());
        };
    }
}
