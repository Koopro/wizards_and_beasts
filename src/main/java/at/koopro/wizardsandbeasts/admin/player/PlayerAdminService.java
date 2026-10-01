package at.koopro.wizardsandbeasts.admin.player;

import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.heritage.HeritageAdminService;
import at.koopro.wizardsandbeasts.command.debug.DebugModeService;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.data.PlayerMinistryRecord;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.MinistryFines;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCaseData;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.network.currency.VaultSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.stats.PlayerStatsSyncPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.skill.SkillTreeId;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import at.koopro.wizardsandbeasts.stats.PlayerStatsData;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Players section's server side: who is online, what each facet of one player's character currently is, and
 * every administrative action on a player.
 *
 * <p><b>No second copy.</b> Every facet is read from the system that owns it at the moment of asking (heritage
 * attachment, {@code PlayerStatsAPI}, {@code PlayerSpellData}, {@code PlayerSkillData}, the Ministry record, the vault,
 * the player's own effects and inventory). Nothing here stores player state; the only thing this class keeps is
 * the {@link PlayerActionLog}.
 *
 * <p><b>One pipeline for every mutation</b> ({@link #perform}): authority for that action → the target is an online
 * player → the operation and its argument are valid → a destructive action is confirmed → the parts of the
 * character the action can touch are snapshotted → the action runs through the owning system's API → on any
 * exception the snapshot is restored → the attempt is logged with its result. The client sends ids and a bounded
 * argument; it never sends state.
 */
@NullMarked
public final class PlayerAdminService {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MAX_PLAYERS = HeritageAdminService.MAX_PLAYERS;
    /** Largest single deposit or withdrawal, in coins of the chosen kind. */
    public static final long MAX_COINS = 10_000L;
    /** Largest single skill-point award (the web's own cap applies on top). */
    public static final int MAX_SKILL_POINTS = 60;
    /** How long a previewed effect lasts on the administrator. */
    public static final int PREVIEW_TICKS = 100;
    private static final String FACT = "admin.wizards_and_beasts.player_fact.";

    /**
     * GameTests only: when set, {@link #perform} throws after the action ran and before it is reported, to prove the
     * rollback. Never set in play.
     */
    public static volatile boolean failAfterMutationForTest;

    private PlayerAdminService() {}

    // ── shapes ──

    /** One online player in the search list. {@code location} is empty unless the viewer may see positions. */
    public record PlayerRow(UUID id, String name, String heritage, String lineage, float health, float maxHealth,
                            int effects, String wanted, String location, String gameMode) {}

    /** The facets of one player's page. */
    public enum Facet {
        OVERVIEW, HERITAGE, SKILLS, SPELLS, EFFECTS, MINISTRY, ECONOMY, DEBUG;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static @Nullable Facet byId(String id) {
            for (Facet facet : values()) {
                if (facet.id().equals(id)) {
                    return facet;
                }
            }
            return null;
        }
    }

    /**
     * A list entry on a facet: a known spell, an active effect, an unlocked skill, a carried wand, an offence.
     *
     * @param labelKey   whether {@code label} is a translation key
     * @param actionable whether the page offers the facet's per-item action (revoke, remove)
     */
    public record Item(String id, String label, boolean labelKey, String value, boolean actionable) {}

    /** A choice the page may offer (a spell to unlock, an effect to preview, a lineage to assign). */
    public record Option(String id, String label, boolean labelKey) {}

    /** One facet of one player, read now. */
    public record FacetView(Facet facet, UUID player, String playerName, List<AdminSpellFact> facts, List<Item> items,
                            List<Option> options) {}

    /** What one action asked for. {@code argument} and {@code amount} are interpreted per action. */
    public record Request(UUID player, PlayerAdminAction action, String argument, long amount, boolean confirmed) {}

    /**
     * What happened. {@code code}: {@code ok}, or why not ({@code unauthorized}, {@code no_player},
     * {@code invalid_argument}, {@code confirm_required}, {@code not_eligible}, {@code nothing_to_do},
     * {@code insufficient}, {@code failed}); {@code detail} says what changed or why.
     */
    public record Outcome(boolean success, String code, String detail, long logSequence) {
        static Outcome refused(String code, String detail) {
            return new Outcome(false, code, detail, 0);
        }
    }

    // ── authority ──

    public static boolean authorised(AdminContext actor) {
        return actor.canRead() && actor.canModify(AdminCapability.PLAYERS);
    }

    /** Positions are a world matter: shown only to those who may act on the world. */
    public static boolean maySeeLocation(AdminContext actor) {
        return actor.canModify(AdminCapability.WORLD);
    }

    // ── search ──

    /** Online players whose name or UUID contains {@code query} (case-insensitive; empty = everyone). */
    public static List<PlayerRow> search(AdminContext actor, MinecraftServer server, String query) {
        if (!authorised(actor)) {
            return List.of();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        boolean location = maySeeLocation(actor);
        List<PlayerRow> rows = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String name = player.getName().getString();
            if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)
                    && !player.getUUID().toString().contains(needle)) {
                continue;
            }
            PlayerHeritageData heritage = HeritageAPI.getData(player);
            rows.add(new PlayerRow(player.getUUID(), name,
                    heritage.getSelectedHeritage() == null ? "" : heritage.getSelectedHeritage().getDisplayName(),
                    heritage.getSelectedHeritageVariant() == null ? "" : heritage.getSelectedHeritageVariant().getDisplayName(),
                    player.getHealth(), player.getMaxHealth(), player.getActiveEffects().size(),
                    MinistryRecords.get(player).wantedLevel().name().toLowerCase(Locale.ROOT),
                    location ? where(player) : "", player.gameMode().getName()));
            if (rows.size() >= MAX_PLAYERS) {
                break;
            }
        }
        rows.sort(Comparator.comparing(row -> row.name().toLowerCase(Locale.ROOT)));
        return List.copyOf(rows);
    }

    private static String where(ServerPlayer player) {
        return player.level().dimension().identifier() + " " + player.blockPosition().getX() + " "
                + player.blockPosition().getY() + " " + player.blockPosition().getZ();
    }

    // ── facets ──

    /** One facet of one online player, or null when the viewer may not read players or the player is not online. */
    public static @Nullable FacetView facet(AdminContext actor, MinecraftServer server, UUID playerId, Facet facet) {
        if (!authorised(actor)) {
            return null;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(playerId);
        if (target == null) {
            return null;
        }
        List<AdminSpellFact> facts = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        List<Option> options = new ArrayList<>();
        switch (facet) {
            case OVERVIEW -> overview(actor, target, facts, items);
            case HERITAGE -> heritage(target, facts, options);
            case SKILLS -> skills(target, facts, items);
            case SPELLS -> spells(target, facts, items, options);
            case EFFECTS -> effects(target, facts, items, options);
            case MINISTRY -> ministry(server, target, facts, items);
            case ECONOMY -> economy(actor, target, facts);
            case DEBUG -> debug(target, facts, items);
        }
        return new FacetView(facet, target.getUUID(), target.getName().getString(), List.copyOf(facts),
                List.copyOf(items), List.copyOf(options));
    }

    private static void overview(AdminContext actor, ServerPlayer target, List<AdminSpellFact> facts, List<Item> items) {
        PlayerHeritageData heritage = HeritageAPI.getData(target);
        facts.add(text("uuid", target.getUUID().toString()));
        facts.add(heritage.getSelectedHeritage() == null ? keyed("heritage", FACT + "none")
                : text("heritage", heritage.getSelectedHeritage().getDisplayName()));
        facts.add(heritage.getSelectedHeritageVariant() == null ? keyed("lineage", FACT + "none")
                : text("lineage", heritage.getSelectedHeritageVariant().getDisplayName()));
        facts.add(keyed("onboarding", FACT + (heritage.isLocked() ? "onboarding_done" : "onboarding_pending")));
        facts.add(text("power", Integer.toString(PlayerStatsAPI.getStat(target, PlayerStat.POWER))));
        PlayerSkillData skills = SkillSystemAPI.getSkillData(target);
        facts.add(text("progression", skills.getUnlockedSkills().size() + " nodes · " + skills.getSkillPoints()
                + " SP free · " + skills.getTotalPointsEarned() + " SP earned"));
        facts.add(text("spells_known", Integer.toString(spellData(target).getKnownSpells().size())));
        facts.add(text("health", String.format(Locale.ROOT, "%.1f / %.1f", target.getHealth(), target.getMaxHealth())));
        facts.add(text("food", Integer.toString(target.getFoodData().getFoodLevel())));
        facts.add(text("xp_level", Integer.toString(target.experienceLevel)));
        facts.add(text("game_mode", target.gameMode().getName()));
        facts.add(maySeeLocation(actor) ? text("location", where(target)) : keyed("location", FACT + "location_hidden"));
        facts.add(keyed("wanted", wantedKey(MinistryRecords.get(target))));
        facts.add(text("balance", money(vault(target).getTotalInKnuts())));
        facts.add(text("effects", Integer.toString(target.getActiveEffects().size())));
        facts.add(keyed("debug_mode", FACT + (DebugModeService.isEnabled(target) ? "on" : "off")));
        for (int slot = 0; slot < target.getInventory().getContainerSize(); slot++) {
            ItemStack stack = target.getInventory().getItem(slot);
            if (stack.getItem() instanceof WandItem) {
                items.add(new Item("slot_" + slot, stack.getHoverName().getString(), false,
                        WandAllegianceService.stateFor(target.getUUID(), stack).name().toLowerCase(Locale.ROOT), false));
            }
        }
    }

    private static void heritage(ServerPlayer target, List<AdminSpellFact> facts, List<Option> options) {
        HeritageAdminService.Inspection inspection = HeritageAdminService.inspectUnchecked(target);
        facts.addAll(inspection.identity());
        facts.addAll(inspection.stats());
        facts.addAll(inspection.derived());
        for (Heritage heritage : Heritage.values()) {
            for (HeritageVariant variant : heritage.getSubtypes()) {
                options.add(new Option(heritage.getId() + "/" + variant.getId(),
                        heritage.getDisplayName() + " · " + variant.getDisplayName(), false));
            }
        }
    }

    private static void skills(ServerPlayer target, List<AdminSpellFact> facts, List<Item> items) {
        PlayerSkillData data = SkillSystemAPI.getSkillData(target);
        int cap = SkillSystemAPI.pointCapFor(SkillTreeId.audienceForHeritage(
                HeritageAPI.getPlayerHeritage(target), HeritageAPI.getPlayerHeritageVariant(target)));
        facts.add(text("skill_points", Integer.toString(data.getSkillPoints())));
        facts.add(text("skill_points_earned", data.getTotalPointsEarned() + " / " + cap));
        facts.add(text("skill_nodes", Integer.toString(data.getUnlockedSkills().size())));
        PlayerStatsData stats = PlayerStatsAPI.getData(target);
        facts.add(text("power_growth", stats.powerGrowthAccumulated() + " (" + PlayerStatsAPI.getRemainingPowerGrowth(target)
                + " left)"));
        for (PlayerStat stat : PlayerStat.values()) {
            Float progress = stats.trainingProgress().get(stat);
            if (progress != null) {
                facts.add(new AdminSpellFact(FACT + "training", "stat.wizards_and_beasts." + stat.getId(), true));
                facts.add(new AdminSpellFact("stat.wizards_and_beasts." + stat.getId(),
                        String.format(Locale.ROOT, "%d  (training %.2f)", PlayerStatsAPI.getStat(target, stat), progress), false));
            }
        }
        data.getUnlockedSkills().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Skill skill = SkillTrees.byId(entry.getKey());
            String name = skill == null ? entry.getKey() : skill.getDisplayName();
            String max = skill == null ? "?" : Integer.toString(skill.getMaxLevel());
            items.add(new Item(entry.getKey(), name, false, "level " + entry.getValue() + " / " + max, false));
        });
    }

    private static void spells(ServerPlayer target, List<AdminSpellFact> facts, List<Item> items, List<Option> options) {
        PlayerSpellData data = spellData(target);
        facts.add(text("spells_known", Integer.toString(data.getKnownSpells().size())));
        facts.add(text("loadout", String.join(", ", java.util.Arrays.stream(data.getLoadout())
                .map(id -> id == null ? "—" : id.substring(id.indexOf(':') + 1)).toList())));
        data.getKnownSpells().stream().sorted().forEach(id -> {
            Spell spell = Spells.byId(id);
            String value = String.format(Locale.ROOT, "%.0f%% · %s · %d casts", data.getSpellProficiency(id) * 100f,
                    data.getMasteryTier(id).name().toLowerCase(Locale.ROOT), data.getCastCount(id));
            items.add(spell == null ? new Item(id, id, false, value, true)
                    : new Item(id, spell.getDisplayName(), true, value, true));
        });
        Spells.all().stream().filter(spell -> !data.knowsSpell(spell.getId()))
                .sorted(Comparator.comparing(Spell::getId))
                .forEach(spell -> options.add(new Option(spell.getId(), spell.getDisplayName(), true)));
    }

    private static void effects(ServerPlayer target, List<AdminSpellFact> facts, List<Item> items, List<Option> options) {
        facts.add(text("effects", Integer.toString(target.getActiveEffects().size())));
        for (MobEffectInstance instance : target.getActiveEffects()) {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value());
            String duration = instance.isInfiniteDuration() ? "∞" : (instance.getDuration() / 20) + "s";
            items.add(new Item(id == null ? "" : id.toString(), instance.getEffect().value().getDescriptionId(), true,
                    "level " + (instance.getAmplifier() + 1) + " · " + duration + (instance.isAmbient() ? " · ambient" : ""),
                    id != null));
        }
        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (id != null && !effect.isInstantenous()) {
                options.add(new Option(id.toString(), effect.getDescriptionId(), true));
            }
        }
        options.sort(Comparator.comparing(Option::id));
    }

    private static void ministry(MinecraftServer server, ServerPlayer target, List<AdminSpellFact> facts, List<Item> items) {
        PlayerMinistryRecord record = MinistryRecords.get(target);
        facts.add(keyed("ministry_module", FACT + (ModuleManager.isEnabled(Module.MINISTRY) ? "on" : "off")));
        facts.add(keyed("trace_active", FACT + (TraceService.isActive() ? "on" : "off")));
        facts.add(keyed("wanted", wantedKey(record)));
        facts.add(text("notoriety", String.format(Locale.ROOT, "%.1f / %.0f", record.notoriety(),
                PlayerMinistryRecord.MAX_NOTORIETY)));
        facts.add(keyed("rank", keyOf(record.rank().displayName())));
        facts.add(keyed("fugitive", FACT + (record.fugitive() ? "yes" : "no")));
        facts.add(text("sentence", record.isServingSentence() ? (record.sentenceTicks() / 20) + "s" : "—"));
        facts.add(text("fine", money(MinistryFines.owed(target))));
        facts.add(keyed("underage", FACT + (MinistryTrace.isUnderage(target) ? "yes" : "no")));
        facts.add(keyed("wand_confiscated", FACT + (MinistryTrace.wandConfiscated(target) ? "yes" : "no")));
        facts.add(keyed("open_case", FACT + (MinistryCaseData.get(server).dossier(target.getUUID()).openCase().isPresent()
                ? "yes" : "no")));
        for (Map.Entry<MagicalOffence, Integer> offence : record.offencesByWeight().entrySet()) {
            items.add(new Item(offence.getKey().name().toLowerCase(Locale.ROOT), keyOf(offence.getKey().displayName()), true,
                    "×" + offence.getValue(), false));
        }
    }

    private static void economy(AdminContext actor, ServerPlayer target, List<AdminSpellFact> facts) {
        PlayerVaultData vault = vault(target);
        facts.add(keyed("gringotts_module", FACT + (ModuleManager.isEnabled(Module.GRINGOTTS) ? "on" : "off")));
        facts.add(text("galleons", Long.toString(vault.getGalleons())));
        facts.add(text("sickles", Long.toString(vault.getSickles())));
        facts.add(text("knuts", Long.toString(vault.getKnuts())));
        facts.add(text("balance", money(vault.getTotalInKnuts())));
        facts.add(text("fine", money(MinistryFines.owed(target))));
        facts.add(keyed("money_authority", FACT + (actor.canModify(AdminCapability.MONEY) ? "yes" : "no")));
    }

    private static void debug(ServerPlayer target, List<AdminSpellFact> facts, List<Item> items) {
        PlayerSpellData spells = spellData(target);
        PlayerHeritageData heritage = HeritageAPI.getData(target);
        facts.add(text("uuid", target.getUUID().toString()));
        facts.add(keyed("debug_mode", FACT + (DebugModeService.isEnabled(target) ? "on" : "off")));
        facts.add(text("active_form", heritage.getActiveFormId() == null ? "—" : heritage.getActiveFormId()));
        facts.add(keyed("consistency", FACT + (HeritageAPI.hasHeritageSelected(target) == heritage.hasHeritageSelected()
                ? "consistent" : "mismatch")));
        facts.add(text("sync_corrections", Integer.toString(spells.getSyncCorrections())));
        facts.add(text("cooldowns", Integer.toString(spells.getCooldowns().size())));
        facts.add(text("web_taught", Integer.toString(SkillSystemAPI.getSkillData(target).getWebTaughtSpells().size())));
        spells.getRejectCounts().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                items.add(new Item(entry.getKey(), entry.getKey(), false, "×" + entry.getValue(), false)));
    }

    // ── actions ──

    /**
     * Runs one action. See the class comment for the pipeline. Every attempt from an authorised administrator is
     * written to the {@link PlayerActionLog}; an attempt without authority only to the server log.
     */
    public static Outcome perform(AdminContext actor, MinecraftServer server, Request request) {
        PlayerAdminAction action = request.action();
        if (!actor.canRead() || !actor.canModify(action.capability())) {
            LOGGER.warn("[Admin] Refused player action {} on {} from unauthorised {}", action.id(), request.player(),
                    actor.actorName());
            return Outcome.refused("unauthorized", action.id());
        }
        // The previewed effect lands on the administrator, so that is the "target" the action needs online.
        UUID targetId = action == PlayerAdminAction.EFFECT_PREVIEW ? actor.actorId() : request.player();
        ServerPlayer target = targetId == null ? null : server.getPlayerList().getPlayer(targetId);
        if (target == null) {
            return log(actor, server, request.player(), request.player().toString(), request,
                    Outcome.refused("no_player", action == PlayerAdminAction.EFFECT_PREVIEW ? "console" : ""));
        }
        String problem = validate(request, target);
        if (problem != null) {
            return log(actor, server, target.getUUID(), target.getName().getString(), request,
                    Outcome.refused("invalid_argument", problem));
        }
        if (action.destructive() && !request.confirmed()) {
            return log(actor, server, target.getUUID(), target.getName().getString(), request,
                    Outcome.refused("confirm_required", target.getName().getString()));
        }
        Snapshot before = Snapshot.of(target);
        Outcome outcome;
        try {
            outcome = execute(actor, server, request, target);
            if (failAfterMutationForTest) {
                throw new IllegalStateException("injected failure (game test)");
            }
        } catch (RuntimeException e) {
            LOGGER.error("[Admin] Player action {} on {} failed; restoring", action.id(), target.getName().getString(), e);
            before.restore(target);
            outcome = Outcome.refused("failed", e.getClass().getSimpleName());
        }
        if (outcome.success()) {
            LOGGER.info("[Admin] {} {} {} ({}): {}", actor.actorName(), action.id(), target.getName().getString(),
                    request.argument(), outcome.detail());
        }
        return log(actor, server, target.getUUID(), target.getName().getString(), request, outcome);
    }

    /** Null when the request is well-formed for its action; otherwise what is wrong with it. */
    static @Nullable String validate(Request request, ServerPlayer target) {
        String argument = request.argument();
        return switch (request.action()) {
            case HERITAGE_ASSIGN -> argument.contains("/") ? null : "heritage/lineage expected";
            case SPELL_UNLOCK, SPELL_GRANT, SPELL_REVOKE -> Spells.byId(argument) == null ? "unknown spell " + argument : null;
            case SKILL_POINTS_ADD -> request.amount() >= 1 && request.amount() <= MAX_SKILL_POINTS ? null
                    : "amount 1–" + MAX_SKILL_POINTS;
            case EFFECT_REMOVE, EFFECT_PREVIEW -> effect(argument) == null ? "unknown effect " + argument
                    : request.action() == PlayerAdminAction.EFFECT_PREVIEW && effect(argument).value().isInstantenous()
                    ? "instant effects cannot be previewed" : null;
            case MONEY_DEPOSIT, MONEY_WITHDRAW -> Coin.byId(argument) == null ? "galleons, sickles or knuts"
                    : request.amount() >= 1 && request.amount() <= MAX_COINS ? null : "amount 1–" + MAX_COINS;
            default -> null;
        };
    }

    private static Outcome execute(AdminContext actor, MinecraftServer server, Request request, ServerPlayer target) {
        String argument = request.argument();
        return switch (request.action()) {
            case HERITAGE_ASSIGN -> {
                String[] parts = argument.split("/", 2);
                HeritageAdminService.Outcome result = HeritageAdminService.assign(actor, server, target.getUUID(),
                        parts[0], parts[1], true);
                yield result.success() ? ok(parts[0] + " / " + parts[1])
                        : Outcome.refused("refused", result.messageKey().substring(result.messageKey().lastIndexOf('.') + 1));
            }
            case HERITAGE_RESET -> {
                HeritageAdminService.Outcome result = HeritageAdminService.resetOnboarding(actor, server, target.getUUID(), true);
                yield result.success() ? ok("back to onboarding")
                        : Outcome.refused("refused", result.messageKey().substring(result.messageKey().lastIndexOf('.') + 1));
            }
            case SPELL_UNLOCK -> {
                Spell spell = Spells.byId(argument);
                PlayerSpellData data = spellData(target);
                if (data.knowsSpell(spell.getId())) {
                    yield Outcome.refused("nothing_to_do", spell.getId());
                }
                SpellLearningService.LearnResult eligible = SpellLearningService.validateLearnAttempt(target, spell);
                if (!eligible.success()) {
                    yield Outcome.refused("not_eligible", eligible.message());
                }
                data.learnSpell(spell.getId());
                syncSpells(target);
                yield ok(spell.getId());
            }
            case SPELL_GRANT -> {
                Spell spell = Spells.byId(argument);
                PlayerSpellData data = spellData(target);
                if (data.knowsSpell(spell.getId())) {
                    yield Outcome.refused("nothing_to_do", spell.getId());
                }
                data.learnSpell(spell.getId());
                syncSpells(target);
                yield ok(spell.getId() + " (past the learning rules)");
            }
            case SPELL_REVOKE -> {
                Spell spell = Spells.byId(argument);
                PlayerSpellData data = spellData(target);
                if (!data.knowsSpell(spell.getId())) {
                    yield Outcome.refused("nothing_to_do", spell.getId());
                }
                data.forgetSpell(spell.getId());
                syncSpells(target);
                yield ok(spell.getId());
            }
            case SPELL_RESET_PROGRESS -> {
                int spells = spellData(target).resetProgression();
                syncSpells(target);
                yield ok(spells + " spell(s) back to unpractised");
            }
            case SKILL_POINTS_ADD -> {
                PlayerSkillData data = SkillSystemAPI.getSkillData(target);
                int before = data.getSkillPoints();
                SkillSystemAPI.awardPoints(target, (int) request.amount());
                int gained = data.getSkillPoints() - before;
                PlayerStateSyncService.syncSkills(target);
                yield gained > 0 ? ok("+" + gained + " SP (now " + data.getSkillPoints() + ")")
                        : Outcome.refused("nothing_to_do", "at the cap");
            }
            case SKILL_RESET -> {
                PlayerSkillData data = SkillSystemAPI.getSkillData(target);
                int nodes = data.getUnlockedSkills().size();
                data.resetAll();
                int forgotten = SkillSystemAPI.revokeWebTaughtSpells(target);
                SkillSystemAPI.reconcileDerivedEffects(target);
                PlayerStateSyncService.syncSkills(target);
                PlayerStateSyncService.syncAbilityGrants(target);
                syncSpells(target);
                yield ok(nodes + " node(s) refunded, " + forgotten + " web-taught spell(s) forgotten");
            }
            case EFFECT_REMOVE -> {
                Holder<MobEffect> effect = effect(argument);
                yield target.removeEffect(effect) ? ok(argument) : Outcome.refused("nothing_to_do", argument);
            }
            case EFFECTS_CLEAR -> {
                int count = target.getActiveEffects().size();
                if (count == 0) {
                    yield Outcome.refused("nothing_to_do", "");
                }
                target.removeAllEffects();
                yield ok(count + " effect(s) cleared");
            }
            case EFFECT_PREVIEW -> {
                target.addEffect(new MobEffectInstance(effect(argument), PREVIEW_TICKS, 0));
                yield ok(argument + " for " + PREVIEW_TICKS / 20 + "s on " + target.getName().getString());
            }
            case MINISTRY_PARDON -> {
                PlayerMinistryRecord record = MinistryRecords.get(target);
                if (record.pardoned().equals(record)) {
                    yield Outcome.refused("nothing_to_do", "");
                }
                MinistryRecords.mutate(target, PlayerMinistryRecord::pardoned);
                yield ok("notoriety " + String.format(Locale.ROOT, "%.1f", record.notoriety()) + " → 0, fine "
                        + money(record.outstandingFineKnuts()) + " → 0");
            }
            case MINISTRY_WAIVE_FINE -> {
                long owed = MinistryFines.owed(target);
                if (owed <= 0) {
                    yield Outcome.refused("nothing_to_do", "");
                }
                MinistryFines.waive(target);
                yield ok(money(owed) + " waived");
            }
            case MONEY_DEPOSIT, MONEY_WITHDRAW -> {
                Coin coin = Coin.byId(argument);
                PlayerVaultData vault = vault(target);
                long before = coin.count(vault);
                boolean done = request.action() == PlayerAdminAction.MONEY_DEPOSIT
                        ? coin.deposit(vault, request.amount()) : coin.withdraw(vault, request.amount());
                VaultSyncS2CPayload.syncToPlayer(target);
                yield done ? ok(coin.id + " " + before + " → " + coin.count(vault))
                        : Outcome.refused("insufficient", coin.id + " " + before);
            }
        };
    }

    private static Outcome ok(String detail) {
        return new Outcome(true, "ok", detail, 0);
    }

    private static Outcome log(AdminContext actor, MinecraftServer server, UUID playerId, String playerName,
                               Request request, Outcome outcome) {
        String result = outcome.success() ? "ok" : (outcome.code().equals("failed") ? "failed:" : "refused:") + outcome.code();
        String argument = request.argument() + (request.amount() != 0 ? " ×" + request.amount() : "");
        PlayerActionLog.Entry entry = PlayerActionLog.get(server).append(actor.actorId(), actor.actorName(), playerId,
                playerName, request.action().id(), argument, outcome.success() ? "ok" : result, outcome.detail());
        return new Outcome(outcome.success(), outcome.code(), outcome.detail(), entry.sequence());
    }

    // ── rollback ──

    /**
     * The parts of a character an action here can write, taken before it runs: spells, skills, vault, stats, Ministry
     * record, effects. Restored as a whole if the action throws. Heritage assignment is the heritage system's own
     * routine and is not covered — it either completes or reports why it did not.
     */
    private record Snapshot(CompoundTag spells, CompoundTag skills, CompoundTag vault, PlayerStatsData stats,
                            PlayerMinistryRecord ministry, List<MobEffectInstance> effects) {

        static Snapshot of(ServerPlayer player) {
            List<MobEffectInstance> effects = new ArrayList<>();
            for (MobEffectInstance instance : player.getActiveEffects()) {
                effects.add(new MobEffectInstance(instance));
            }
            return new Snapshot(spellData(player).save(), SkillSystemAPI.getSkillData(player).save(), PlayerAdminService.vault(player).save(),
                    PlayerStatsAPI.getData(player), MinistryRecords.get(player), effects);
        }

        void restore(ServerPlayer player) {
            PlayerSpellData spellData = new PlayerSpellData();
            spellData.load(spells.copy());
            player.setData(ModAttachments.SPELL_DATA.get(), spellData);
            PlayerSkillData skillData = new PlayerSkillData();
            skillData.load(skills.copy());
            player.setData(ModAttachments.SKILL_DATA.get(), skillData);
            PlayerVaultData vaultData = new PlayerVaultData();
            vaultData.load(vault.copy());
            player.setData(ModAttachments.VAULT_DATA.get(), vaultData);
            PlayerStatsAPI.setAndSync(player, stats);
            MinistryRecords.set(player, ministry);
            player.removeAllEffects();
            for (MobEffectInstance instance : effects) {
                player.addEffect(new MobEffectInstance(instance));
            }
            SkillSystemAPI.reconcileDerivedEffects(player);
            PlayerStateSyncService.syncSkills(player);
            PlayerStateSyncService.syncAbilityGrants(player);
            syncSpells(player);
            VaultSyncS2CPayload.syncToPlayer(player);
        }
    }

    // ── helpers ──

    /** The three coins, as the vault command names them. */
    enum Coin {
        GALLEONS("galleons"), SICKLES("sickles"), KNUTS("knuts");

        final String id;

        Coin(String id) {
            this.id = id;
        }

        static @Nullable Coin byId(String id) {
            for (Coin coin : values()) {
                if (coin.id.equals(id)) {
                    return coin;
                }
            }
            return null;
        }

        long count(PlayerVaultData vault) {
            return switch (this) {
                case GALLEONS -> vault.getGalleons();
                case SICKLES -> vault.getSickles();
                case KNUTS -> vault.getKnuts();
            };
        }

        boolean deposit(PlayerVaultData vault, long amount) {
            switch (this) {
                case GALLEONS -> vault.depositGalleons(amount);
                case SICKLES -> vault.depositSickles(amount);
                case KNUTS -> vault.depositKnuts(amount);
            }
            return true;
        }

        boolean withdraw(PlayerVaultData vault, long amount) {
            return switch (this) {
                case GALLEONS -> vault.withdrawGalleons(amount);
                case SICKLES -> vault.withdrawSickles(amount);
                case KNUTS -> vault.withdrawKnuts(amount);
            };
        }
    }

    private static @Nullable Holder<MobEffect> effect(String id) {
        Identifier parsed = Identifier.tryParse(id);
        MobEffect effect = parsed == null ? null : BuiltInRegistries.MOB_EFFECT.getValue(parsed);
        return effect == null ? null : BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }

    private static void syncSpells(ServerPlayer player) {
        SpellDataSyncS2CPayload.syncToPlayer(player);
        PlayerStatsSyncPayload.syncToPlayer(player); // KNOWLEDGE derives from spells known
    }

    private static PlayerSpellData spellData(ServerPlayer player) {
        return player.getData(ModAttachments.SPELL_DATA.get());
    }

    private static PlayerVaultData vault(ServerPlayer player) {
        return player.getData(ModAttachments.VAULT_DATA.get());
    }

    static String money(long knuts) {
        long[] coins = CurrencyHelper.fromKnuts(Math.max(0, knuts));
        return CurrencyHelper.formatCurrency(coins[0], coins[1], coins[2]);
    }

    private static String wantedKey(PlayerMinistryRecord record) {
        return keyOf(record.wantedLevel().displayName());
    }

    private static String keyOf(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey()
                : component.getString();
    }

    private static AdminSpellFact keyed(String label, String valueKey) {
        return new AdminSpellFact(FACT + label, valueKey, true);
    }

    private static AdminSpellFact text(String label, String value) {
        return new AdminSpellFact(FACT + label, value, false);
    }
}
