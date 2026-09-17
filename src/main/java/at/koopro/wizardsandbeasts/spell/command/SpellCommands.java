package at.koopro.wizardsandbeasts.spell.command;

import at.koopro.wizardsandbeasts.command.WizardsAndBeastsCommandPermissions;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.cast.SpellCastService;
import at.koopro.wizardsandbeasts.spell.cast.SpellExecutor;
import at.koopro.wizardsandbeasts.spell.clash.SpellClashLocks;
import at.koopro.wizardsandbeasts.spell.core.CastType;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.util.WandHelper;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

public final class SpellCommands {

    private SpellCommands() {
    }

    /**
     * Bare spell paths — {@code alohomora}, not {@code wizards_and_beasts:alohomora}.
     *
     * <p>These used to suggest {@link Spell#getId()}, which is namespaced, and every one of those
     * suggestions was unparseable by the command offering it: the argument is
     * {@link StringArgumentType#word()}, whose charset has no colon, so accepting a suggestion put a
     * red line under it. Nothing is unreachable as a result — {@code Spells.byId} prefixes a bare id
     * with the mod's namespace — but the completion list and the parser disagreed about what a spell
     * id looks like, and the list was the one that was wrong.
     *
     * <p>A spell whose id belongs to another namespace is suggested in full, since its bare path
     * would resolve to the wrong spell or to nothing.
     */
    private static final SuggestionProvider<CommandSourceStack> SPELL_ID_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    Spells.all().stream().map(SpellCommands::suggestibleId), builder);

    private static String suggestibleId(Spell spell) {
        String id = spell.getId();
        String ownPrefix = WizardsAndBeastsMod.MODID + ":";
        return id.startsWith(ownPrefix) ? id.substring(ownPrefix.length()) : id;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("spell")
                .then(Commands.literal("learn")
                        .then(Commands.argument("spell", StringArgumentType.word())
                                .suggests(SPELL_ID_SUGGESTIONS)
                                .executes(ctx -> learnSpell(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "spell")))))
                .then(Commands.literal("forget")
                        .then(Commands.argument("spell", StringArgumentType.word())
                                .suggests(SPELL_ID_SUGGESTIONS)
                                .executes(ctx -> forgetSpell(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "spell")))))
                .then(Commands.literal("list")
                        .executes(ctx -> listSpells(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("info")
                        .then(Commands.argument("spell", StringArgumentType.word())
                                .suggests(SPELL_ID_SUGGESTIONS)
                                .executes(ctx -> spellInfo(
                                        ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "spell")))))
                .then(Commands.literal("reset")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .executes(ctx -> resetSpells(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("learn_all")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .executes(ctx -> learnAllSpells(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("cast")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.argument("spell", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Spells.all().stream().filter(SpellCommands::castableFromCommand)
                                                .map(Spell::getId), builder))
                                .executes(ctx -> castSpell(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "spell")))))
                .then(Commands.literal("clash")
                        .requires(WizardsAndBeastsCommandPermissions.ADMIN)
                        .then(Commands.literal("hold")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("held", BoolArgumentType.bool())
                                                .executes(ctx -> pinClashHold(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        BoolArgumentType.getBool(ctx, "held")))))));
    }

    /**
     * The Patronus form pair, a sibling of {@code spell} rather than a child of it.
     *
     * <p>Revealing a Patronus is not an operation on the spell registry — it reads happiness and
     * heritage and writes the {@code PATRONUS_FORM} attachment. Sitting it under {@code spell} made
     * the only two commands that touch it four levels deep for no relationship that exists in code.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> registerPatronus() {
        return Commands.literal("patronus")
                .then(Commands.literal("form")
                        .then(Commands.literal("reveal")
                                .executes(ctx -> revealPatronusForm(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearPatronusForm(ctx.getSource().getPlayerOrException()))));
    }

    private static int learnSpell(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            player.displayClientMessage(Component.literal("Unknown spell: " + spellId).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        // The canonical id, never the argument. `Spells.byId` accepts a bare path and a legacy
        // namespace and resolves both, so `spellId` here can be any of three spellings of one spell —
        // and `PlayerSpellData` is a plain string set with no normalisation of its own. Writing the
        // argument stored `alohomora` while every reader in the mod looks up
        // `wizards_and_beasts:alohomora`, which learned the spell and left it uncastable.
        // `SkillSystemAPI.teachSpell` canonicalises for the same reason.
        String canonicalId = spell.getId();
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        if (data.knowsSpell(canonicalId)) {
            player.displayClientMessage(Component.translatable("spell.wizards_and_beasts.cmd.already_known", Component.translatable(spell.getDisplayName())).withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        data.learnSpell(canonicalId);
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
        data.forgetSpell(spell.getId());
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

        String canonicalId = spell.getId();
        PlayerSpellData data = player.getData(ModAttachments.SPELL_DATA.get());
        boolean known = data.knowsSpell(canonicalId);

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
            int casts = data.getSuccessfulHits(canonicalId);
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
            float skillDamageMult = SkillSystemAPI.getSkillDamageMultiplier(player, spell);
            float skillCooldownMult = SkillSystemAPI.getSkillCooldownMultiplier(player, spell);
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

    /**
     * Fires a projectile spell from this player now, as a real cast with the gates in front of it
     * skipped.
     *
     * <p>For anything that needs two casts in the same tick — a spell clash above all, which nobody
     * alt-tabbing between two clients can line up: {@code /execute as @a at @s run wandb magic spell
     * cast stupefy}. Everything past the gates is the real cast: wand, allegiance, proficiency, skills
     * and the rest of the modifier pipeline apply, and a wand can still misfire. Knowing the spell, its
     * requirement, cooldowns and Gamp's Law are skipped, and no cooldown is stamped.
     *
     * <p>Projectile spells only. Beam and channel spells are fed by a held wand every tick, which a
     * command cannot do.
     *
     * <p>Refusals go to the command source rather than the player's chat, so the console running
     * {@code /execute as …} is the one told.
     */
    private static int castSpell(CommandSourceStack source, String spellId) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Spell spell = Spells.byId(spellId);
        if (spell == null) {
            source.sendFailure(Component.literal("Unknown spell: " + spellId));
            return 0;
        }
        if (!castableFromCommand(spell)) {
            source.sendFailure(Component.literal(
                    spell.getId() + " cannot be cast from a command: only implemented projectile spells can."));
            return 0;
        }
        ServerLevel level = player.level();
        SpellExecutor.executeGeneric(SpellCastService.contextFor(player, spell, level), level);
        return 1;
    }

    /**
     * Pins the targets' side of any spell clash as held, or releases the pin.
     *
     * <p>A lock is held with the wand, and one person cannot hold right-click in two game windows at once.
     * Pin one side from the console before firing, then hold the other for real. A pinned player holds
     * every lock they are in until unpinned; it does not start or cast anything on its own.
     */
    private static int pinClashHold(CommandSourceStack source, Collection<ServerPlayer> targets, boolean held) {
        for (ServerPlayer target : targets) {
            SpellClashLocks.pinHold(target.getUUID(), held);
        }
        source.sendSuccess(() -> Component.literal((held ? "Pinned" : "Released") + " the clash hold for "
                + targets.size() + (targets.size() == 1 ? " player" : " players")), true);
        return targets.size();
    }

    private static boolean castableFromCommand(Spell spell) {
        return spell.isImplemented()
                && spell.getProperties() != null
                && spell.getProperties().getCastType() == CastType.PROJECTILE;
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

}
