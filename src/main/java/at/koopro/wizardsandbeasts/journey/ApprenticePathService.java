package at.koopro.wizardsandbeasts.journey;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.journey.ApprenticePathS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.CurrencyItemRegistry;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Walks a player along the Apprentice's Path: gathers the facts from the systems that own them, records each newly
 * met step, pays it once, says so, and sends the client its view. The server is the only judge; the client is told.
 *
 * <p>Each reward hands over the tool for the next step, so the path is a chain rather than a checklist:
 * <ul>
 *   <li>Arrive: the Ministry Handbook, and the recipes a new wizard needs before anyone tells them (the bench a
 *       wandmaker takes up, the wand blank, the handbook, a blank spellbook, the duelling dummy, a cauldron)</li>
 *   <li>Wand: the starter charm put on the spell wheel</li>
 *   <li>First light: two Sickles. Shops draw on the vault, so coin goes over a goblin teller's counter first</li>
 *   <li>A spell of your own: one skill point and a duelling dummy to practise on</li>
 *   <li>Practice: a Galleon</li>
 *   <li>Field study: five Sickles</li>
 * </ul>
 * No coin buys a spell, and the one skill point is paid once per player, like every milestone point. A part whose
 * system is switched off is not paid ({@link PathReward#of}).
 *
 * <p>A player who had played before the path existed is reconciled once at login: the steps they have already met are
 * recorded and the arrival recipes unlocked, but no coin, item or point is paid for them. A veteran is told where they
 * stand, not paid for the past.
 */
@NullMarked
public final class ApprenticePathService {

    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Recipes unlocked on arrival. Ones a datapack removed are skipped. */
    static final List<String> ARRIVAL_RECIPES = List.of("wandmakers_bench", "wand_blank", "ministry_handbook",
            "blank_book_of_spells", "duelling_dummy", "pewter_cauldron");

    /** More steps than this met at once (an old save, a long absence) get one toast, not a stack of them. */
    private static final int SEPARATE_TOASTS = 2;

    private ApprenticePathService() {}

    public static PathRecord record(ServerPlayer player) {
        return player.getData(ModAttachments.APPRENTICE_PATH.get());
    }

    /**
     * Notices every newly met step, records it done and owed, then pays what is owed. The record is written before
     * anything is handed over, so nothing that goes wrong while paying can lead to a second payment.
     *
     * @return the steps paid this time
     */
    public static List<PathStep> evaluate(ServerPlayer player) {
        PathRecord record = record(player);
        if (!record.guided() || !player.isAlive()) {
            return List.of();
        }
        ApprenticePath.Facts facts = facts(player, record);
        List<PathStep> fresh = ApprenticePath.newlyMet(record, facts);
        if (fresh.isEmpty()) {
            return fresh;
        }
        PathRecord next = record;
        for (PathStep step : fresh) {
            next = next.earned(step);
        }
        player.setData(ModAttachments.APPRENTICE_PATH.get(), next);
        List<PathStep> paid = payOwed(player);
        announce(player, paid, ApprenticePath.current(record(player), facts));
        offerAdventure(player);
        sync(player);
        return paid;
    }

    /**
     * Hands over every reward still owed, striking each debt as soon as it is paid. A reward that throws stays owed
     * and is tried again at the next login rather than every second, so a broken reward cannot flood the log.
     *
     * @return the steps paid
     */
    static List<PathStep> payOwed(ServerPlayer player) {
        List<PathStep> paid = new ArrayList<>();
        PathReward.Systems systems = systems();
        for (PathStep step : PathStep.values()) {
            if (!record(player).owes(step)) {
                continue;
            }
            try {
                for (PathReward part : PathReward.of(step, systems)) {
                    pay(player, part);
                }
            } catch (RuntimeException e) {
                LOGGER.error("Apprentice's Path: paying {} to {} failed; it stays owed until the next login",
                        step.id(), player.getName().getString(), e);
                continue;
            }
            player.setData(ModAttachments.APPRENTICE_PATH.get(), record(player).paid(step));
            paid.add(step);
        }
        return paid;
    }

    /**
     * At login, before anything else reads the record. {@code hadRecord} says whether the player carried a path record
     * into this login: one who did not, but has play time, played before the path existed and is reconciled without
     * pay. Then any reward a failed hand-over left owed is paid, and the client is sent its view.
     */
    public static void login(ServerPlayer player, boolean hadRecord) {
        boolean playedBefore = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0;
        if (!hadRecord && playedBefore) {
            reconcile(player);
        }
        PathRecord record = record(player);
        if (record.guided() && !record.owed().isEmpty()) {
            List<PathStep> paid = payOwed(player);
            announce(player, paid, current(player));
        }
        offerAdventure(player);
        sync(player);
    }

    /** Records the steps an old save has already met, unlocks the arrival recipes, and pays nothing else. */
    static List<PathStep> reconcile(ServerPlayer player) {
        PathRecord record = record(player);
        List<PathStep> met = ApprenticePath.newlyMet(record, facts(player, record));
        for (PathStep step : met) {
            record = record.with(step);
        }
        player.setData(ModAttachments.APPRENTICE_PATH.get(), record);
        if (record.has(PathStep.ARRIVE)) {
            unlockRecipes(player);
        }
        if (!met.isEmpty()) {
            player.sendSystemMessage(Component.translatable("journey.wizards_and_beasts.reconciled", met.size()));
            player.sendSystemMessage(nextLine(current(player)));
        }
        return met;
    }

    /**
     * The path is walked: the wider world is next. A Serpent Chart to the nearest uncleared Chamber and a Wayfarer's
     * Chart to the nearest hamlet are handed over once, as leads and not tasks; each decides whether there is anywhere
     * to draw it to. No path step asks for either.
     */
    private static void offerAdventure(ServerPlayer player) {
        PathRecord record = record(player);
        if (record.guided() && record.has(PathStep.ARRIVE) && current(player) == null) {
            at.koopro.wizardsandbeasts.chamber.ChamberCharts.offer(player);
            at.koopro.wizardsandbeasts.settlement.HamletCharts.offer(player);
        }
    }

    private static PathReward.Systems systems() {
        return new PathReward.Systems(ModuleManager.isEnabled(Module.HANDBOOK),
                ModuleManager.isEnabled(Module.SKILL_TREES));
    }

    /**
     * What is true of the player now. A fact whose step is already done is not looked up: once paid it no longer
     * matters, and the inventory scan is the dearest of them.
     */
    public static ApprenticePath.Facts facts(ServerPlayer player, PathRecord record) {
        PlayerHeritageData heritage = player.getData(ModAttachments.HERITAGE_DATA.get());
        boolean heritageOn = ModuleManager.isEnabled(Module.HERITAGE);
        boolean wandHand = ModuleManager.isEnabled(Module.WANDS_AND_SPELLS)
                && (!heritageOn || (heritage.canUseWand() && heritage.canCast()));
        boolean arrived = !heritageOn || heritage.hasHeritageSelected();
        PlayerSpellData spells = player.getData(ModAttachments.SPELL_DATA.get());
        return new ApprenticePath.Facts(
                wandHand,
                ModuleManager.isEnabled(Module.BESTIARY),
                arrived,
                !record.has(PathStep.WAND) && ownsWand(player),
                !record.has(PathStep.FIRST_LIGHT) && hasCast(spells),
                !record.has(PathStep.OWN_SPELL) && knowsBeyondStarter(spells),
                !record.has(PathStep.PRACTICE) && proficient(spells),
                !record.has(PathStep.FIELD_STUDY) && studied(player));
    }

    /** A wand that has chosen this player, anywhere in their inventory (hands and armour slots included). */
    static boolean ownsWand(ServerPlayer player) {
        UUID self = player.getUUID();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof WandItem) {
                Optional<UUID> master = WandComponents.getMaster(stack);
                if (master.isPresent() && master.get().equals(self)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCast(PlayerSpellData spells) {
        for (int count : spells.getCastCounts().values()) {
            if (count > 0) {
                return true;
            }
        }
        return false;
    }

    /** The starter charms and their companions: known by every wand hand, so knowing them is not a find. */
    static Set<String> starterSet() {
        Set<String> out = new HashSet<>();
        for (String id : SpellLearningService.STARTER_SPELLS) {
            Spell spell = Spells.byId(id);
            out.add(spell != null ? spell.getId() : id);
            out.addAll(SpellLearningService.companionsOf(spell));
        }
        return out;
    }

    private static boolean knowsBeyondStarter(PlayerSpellData spells) {
        Set<String> starter = starterSet();
        for (String id : spells.getKnownSpells()) {
            if (!starter.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean proficient(PlayerSpellData spells) {
        for (String id : spells.getSuccessfulHitCounts().keySet()) {
            if (spells.hasReachedMasteryTier(id, PlayerSpellData.MasteryTier.PROFICIENT)) {
                return true;
            }
        }
        return false;
    }

    private static boolean studied(ServerPlayer player) {
        for (DiscoveryTier tier : BestiaryDataHelper.getData(player).tiers().values()) {
            if (tier.compareTo(DiscoveryTier.OBSERVED) >= 0) {
                return true;
            }
        }
        return false;
    }

    // ── rewards ───────────────────────────────────────────────────────────

    private static void pay(ServerPlayer player, PathReward part) {
        switch (part) {
            case HANDBOOK -> give(player, new ItemStack(MiscItemRegistry.MINISTRY_HANDBOOK.get()));
            case RECIPES -> unlockRecipes(player);
            case STARTER_ON_WHEEL -> putStarterOnWheel(player);
            case TWO_SICKLES -> give(player, new ItemStack(CurrencyItemRegistry.SICKLE.get(), 2));
            case SKILL_POINT -> {
                SkillSystemAPI.awardPoints(player, 1);
                SkillDataSyncS2CPayload.syncToPlayer(player);
            }
            case DUELLING_DUMMY -> give(player, new ItemStack(MiscItemRegistry.DUELLING_DUMMY.get()));
            case GALLEON -> give(player, new ItemStack(CurrencyItemRegistry.GALLEON.get()));
            case FIVE_SICKLES -> give(player, new ItemStack(CurrencyItemRegistry.SICKLE.get(), 5));
        }
    }

    /** Into the inventory, or at the player's feet when it is full. */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private static void unlockRecipes(ServerPlayer player) {
        var recipes = player.level().getServer().getRecipeManager();
        List<ResourceKey<Recipe<?>>> keys = new ArrayList<>();
        for (String path : ARRIVAL_RECIPES) {
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
                    Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path));
            if (recipes.byKey(key).isPresent()) {
                keys.add(key);
            }
        }
        if (!keys.isEmpty()) {
            player.awardRecipesByKey(keys);
        }
    }

    /**
     * The starter charm on the first empty wheel slot, and selected if the selected slot is empty: with a wand in
     * hand, the next step is then one release of the use key. A wheel the player has already filled is left alone.
     */
    static void putStarterOnWheel(ServerPlayer player) {
        PlayerSpellData spells = player.getData(ModAttachments.SPELL_DATA.get());
        for (String id : SpellLearningService.STARTER_SPELLS) {
            Spell spell = Spells.byId(id);
            if (spell == null || !spells.knowsSpell(spell.getId())) {
                continue;
            }
            int empty = -1;
            for (int slot = 0; slot < PlayerSpellData.LOADOUT_SIZE; slot++) {
                String held = spells.getLoadoutSpell(slot);
                if (spell.getId().equals(held)) {
                    return;
                }
                if (held == null && empty < 0) {
                    empty = slot;
                }
            }
            if (empty < 0) {
                return;
            }
            spells.setLoadoutSpell(empty, spell.getId());
            if (spells.getLoadoutSpell(spells.getActiveSlot()) == null) {
                spells.setActiveSlot(empty);
            }
            SpellDataSyncS2CPayload.syncToPlayer(player);
            return;
        }
    }

    // ── feedback and view ─────────────────────────────────────────────────

    private static void announce(ServerPlayer player, List<PathStep> fresh, @Nullable PathStep next) {
        if (fresh.isEmpty()) {
            return;
        }
        if (fresh.size() <= SEPARATE_TOASTS) {
            PathReward.Systems systems = systems();
            for (PathStep step : fresh) {
                PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                        Component.translatable("journey.wizards_and_beasts.toast.done", PathText.title(step)),
                        PathText.paid(PathReward.of(step, systems)));
            }
        } else {
            PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                    Component.translatable("journey.wizards_and_beasts.toast.many", fresh.size()),
                    Component.translatable("journey.wizards_and_beasts.toast.many.body"));
        }
        player.sendSystemMessage(nextLine(next));
    }

    /** "Your path — next: …", or the line that hands the player to their O.W.L.s once it is walked. */
    public static Component nextLine(@Nullable PathStep next) {
        if (next == null) {
            return Component.translatable("journey.wizards_and_beasts.walked");
        }
        return Component.translatable("journey.wizards_and_beasts.next", PathText.title(next), PathText.hint(next));
    }

    /**
     * The step the player should take now, or null when the path is walked, switched off or not begun (the
     * ceremony is its own guide). Read by the Education "what next" list, which leads with it.
     */
    public static @Nullable PathStep current(ServerPlayer player) {
        PathRecord record = record(player);
        if (!record.guided() || !record.has(PathStep.ARRIVE)) {
            return null;
        }
        return ApprenticePath.current(record, facts(player, record));
    }

    public static void sync(ServerPlayer player) {
        PathRecord record = record(player);
        List<String> steps = new ArrayList<>();
        List<String> chamber = new ArrayList<>();
        if (record.guided()) {
            for (PathStep step : ApprenticePath.applicable(facts(player, record))) {
                steps.add(step.id());
            }
            if (ModuleManager.isEnabled(Module.CHAMBER_OF_SECRETS)) {
                chamber.addAll(at.koopro.wizardsandbeasts.chamber.ChamberQuestService.quest(player).reached());
            }
        }
        ApprenticePathS2CPayload.send(player, new ApprenticePathS2CPayload(steps, List.copyOf(record.done()), chamber));
    }

    /** Switches the path on or off for this player and tells their client. Done steps are kept either way. */
    public static void setGuided(ServerPlayer player, boolean on) {
        if (record(player).guided() == on) {
            sync(player);
            return;
        }
        player.setData(ModAttachments.APPRENTICE_PATH.get(), record(player).guided(on));
        sync(player);
        if (on) {
            evaluate(player);
        }
    }
}
