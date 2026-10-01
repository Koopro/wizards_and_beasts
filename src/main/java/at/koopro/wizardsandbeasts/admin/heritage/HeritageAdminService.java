package at.koopro.wizardsandbeasts.admin.heritage;

import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.heritage.ConditionOrigin;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageAssignment;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.rules.HeritageRules;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.registry.ModAttributes;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import at.koopro.wizardsandbeasts.stats.PowerBandTable;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Heritages section's player tools: list online players, inspect one, assign a heritage, send one back to
 * the onboarding gate. Shared by the panel's payloads and {@code /wandb admin heritage}.
 *
 * <p><b>Authority.</b> Every entry point takes an {@link AdminContext} built by the caller from the real sender
 * and checks {@link AdminCapability#PLAYERS} itself: reading another player's character is as much a players
 * matter as changing it. The client names a player by UUID and a heritage by id; the server looks both up.
 *
 * <p><b>No second copy.</b> Inspection reads the heritage attachment, {@code PlayerStatsAPI} and the player's own
 * {@link AttributeInstance}s at the moment of asking; assignment and reset are {@link HeritageAssignment}, the
 * routine the gate and the old commands use. There is deliberately no "reset everyone": a destructive action
 * here names exactly one online player and must arrive confirmed.
 */
@NullMarked
public final class HeritageAdminService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String KEY = "admin.wizards_and_beasts.heritage_action.";
    private static final String FACT = "admin.wizards_and_beasts.heritage_fact.";
    public static final int MAX_PLAYERS = 256;

    private HeritageAdminService() {}

    /** One online player, as the Players tab lists them. Empty ids = no heritage yet (onboarding pending). */
    public record PlayerRow(UUID id, String name, String heritageId, String variantId) {}

    /**
     * One player's heritage and what it currently does to them.
     *
     * @param identity heritage, lineage, condition, lock, shape, onboarding
     * @param stats    the five stats as stored ({@code PLAYER_STATS}); Knowledge is computed, POWER carries its band
     * @param derived  live attribute values — computed by the game from base + every modifier, never stored here
     */
    public record Inspection(UUID id, String name, String heritageId, String variantId,
                             List<AdminSpellFact> identity, List<AdminSpellFact> stats, List<AdminSpellFact> derived) {}

    /** What happened; {@code messageKey} is a lang key taking {@code detail} as its argument. */
    public record Outcome(boolean success, String messageKey, String detail) {
        static Outcome refused(String reason, String detail) {
            return new Outcome(false, KEY + reason, detail);
        }
    }

    public static boolean authorised(AdminContext actor) {
        return actor.canRead() && actor.canModify(AdminCapability.PLAYERS);
    }

    public static List<PlayerRow> players(AdminContext actor, MinecraftServer server) {
        if (!authorised(actor)) {
            return List.of();
        }
        List<PlayerRow> rows = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerHeritageData data = HeritageAPI.getData(player);
            rows.add(new PlayerRow(player.getUUID(), player.getName().getString(),
                    data.getSelectedHeritage() == null ? "" : data.getSelectedHeritage().getId(),
                    data.getSelectedHeritageVariant() == null ? "" : data.getSelectedHeritageVariant().getId()));
            if (rows.size() >= MAX_PLAYERS) {
                break;
            }
        }
        rows.sort(Comparator.comparing(row -> row.name().toLowerCase(Locale.ROOT)));
        return List.copyOf(rows);
    }

    public static @Nullable Inspection inspect(AdminContext actor, MinecraftServer server, UUID playerId) {
        if (!authorised(actor)) {
            return null;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(playerId);
        return target == null ? null : inspect(target);
    }

    /**
     * The inspection without the authority check, for a caller that has already made it on the same actor (the
     * Players section's heritage facet). Everyone else goes through the authorised overload.
     */
    public static Inspection inspectUnchecked(ServerPlayer target) {
        return inspect(target);
    }

    /** Package-visible for tests; callers outside go through the authorised overload. */
    static Inspection inspect(ServerPlayer target) {
        PlayerHeritageData data = HeritageAPI.getData(target);
        Heritage heritage = data.getSelectedHeritage();
        HeritageVariant variant = data.getSelectedHeritageVariant();
        ConditionOrigin condition = data.getCondition();

        List<AdminSpellFact> identity = new ArrayList<>();
        // Heritage and lineage names are English display names in this mod (no name lang keys), as on the gate.
        identity.add(heritage == null ? keyed("heritage", FACT + "none") : text("heritage", heritage.getDisplayName()));
        identity.add(variant == null ? keyed("variant", FACT + "none") : text("variant", variant.getDisplayName()));
        identity.add(condition == null ? keyed("condition", FACT + "none")
                : keyed("condition", condition.getTranslationKey()));
        identity.add(keyed("onboarding", FACT + (data.isLocked() ? "onboarding_done" : "onboarding_pending")));
        identity.add(text("transformation", data.getTransformationState().name().toLowerCase(Locale.ROOT)));
        if (heritage != null) {
            identity.add(keyed("selectable", FACT + (HeritageRules.selectable(heritage) ? "yes" : "no")));
        }

        List<AdminSpellFact> stats = new ArrayList<>();
        for (PlayerStat stat : PlayerStat.values()) {
            int value = stat == PlayerStat.KNOWLEDGE ? PlayerStatsAPI.computeKnowledge(target) : PlayerStatsAPI.getStat(target, stat);
            String shown = Integer.toString(value);
            if (stat == PlayerStat.POWER && variant != null) {
                // The band the lineage rolled in and the ceiling training can reach — both read from the table the
                // roll itself uses, so this is what the character was actually rolled against.
                shown += "  [" + PowerBandTable.getBandMin(variant) + "–" + PowerBandTable.getBandMax(variant)
                        + ", cap " + PowerBandTable.getGrowthCap(variant) + (PlayerStatsAPI.isProdigy(target) ? ", prodigy" : "") + "]";
            }
            stats.add(new AdminSpellFact("stat.wizards_and_beasts." + stat.getId(), shown, false));
        }

        List<AdminSpellFact> derived = new ArrayList<>();
        derived.add(attribute(target, Attributes.MAX_HEALTH));
        derived.add(attribute(target, Attributes.ARMOR));
        derived.add(attribute(target, Attributes.MOVEMENT_SPEED));
        derived.add(attribute(target, ModAttributes.WAND_AFFINITY));
        derived.add(attribute(target, ModAttributes.DARK_CORRUPTION));
        derived.add(attribute(target, ModAttributes.BEAST_RESISTANCE));

        return new Inspection(target.getUUID(), target.getName().getString(),
                heritage == null ? "" : heritage.getId(), variant == null ? "" : variant.getId(),
                List.copyOf(identity), List.copyOf(stats), List.copyOf(derived));
    }

    /**
     * Assigns {@code heritageId}/{@code variantId} to one online player. Refused unless confirmed. An administrator
     * may assign a heritage closed to onboarding — that rule governs what new players may choose, not what an
     * operator may set — and the reply says so.
     */
    public static Outcome assign(AdminContext actor, MinecraftServer server, UUID playerId,
                                 String heritageId, String variantId, boolean confirmed) {
        if (!actor.canModify(AdminCapability.PLAYERS)) {
            LOGGER.warn("[Admin] Refused heritage assignment from unauthorised {}", actor.actorName());
            return Outcome.refused("unauthorized", heritageId);
        }
        ServerPlayer target = server.getPlayerList().getPlayer(playerId);
        if (target == null) {
            return Outcome.refused("no_player", playerId.toString());
        }
        Heritage heritage = Heritage.byId(heritageId);
        HeritageVariant variant = HeritageVariant.byId(variantId);
        String problem = HeritageAssignment.checkPairing(heritage, variant);
        if (problem != null || heritage == null || variant == null) {
            return Outcome.refused(problem == null ? "invalid_variant" : problem, heritageId + "/" + variantId);
        }
        if (!confirmed) {
            return Outcome.refused("confirm_required", target.getName().getString());
        }
        HeritageAssignment.assign(target, heritage, variant);
        LOGGER.info("[Admin] {} assigned {} / {} to {}", actor.actorName(), heritage.getId(), variant.getId(),
                target.getName().getString());
        return new Outcome(true, KEY + (HeritageRules.selectable(heritage) ? "assigned" : "assigned_closed"),
                target.getName().getString());
    }

    /** Sends exactly one online player back to the onboarding gate. Refused unless confirmed. */
    public static Outcome resetOnboarding(AdminContext actor, MinecraftServer server, UUID playerId, boolean confirmed) {
        if (!actor.canModify(AdminCapability.PLAYERS)) {
            LOGGER.warn("[Admin] Refused onboarding reset from unauthorised {}", actor.actorName());
            return Outcome.refused("unauthorized", playerId.toString());
        }
        ServerPlayer target = server.getPlayerList().getPlayer(playerId);
        if (target == null) {
            return Outcome.refused("no_player", playerId.toString());
        }
        if (!confirmed) {
            return Outcome.refused("confirm_required", target.getName().getString());
        }
        HeritageAssignment.resetOnboarding(target);
        LOGGER.info("[Admin] {} reset the heritage of {} (back to onboarding)", actor.actorName(),
                target.getName().getString());
        return new Outcome(true, KEY + "reset", target.getName().getString());
    }

    private static AdminSpellFact keyed(String label, String valueKey) {
        return new AdminSpellFact(FACT + label, valueKey, true);
    }

    private static AdminSpellFact text(String label, String value) {
        return new AdminSpellFact(FACT + label, value, false);
    }

    /**
     * The attribute's live value as the game computes it, labelled with the attribute's own name. An attribute the
     * player does not carry (nothing writes Wand Affinity or Beast Resistance yet) says so rather than inventing 0.
     */
    private static AdminSpellFact attribute(ServerPlayer player, Holder<Attribute> attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        String label = attribute.value().getDescriptionId();
        if (instance == null) {
            return new AdminSpellFact(label, FACT + "untracked", true);
        }
        return new AdminSpellFact(label, String.format(Locale.ROOT, "%.3f  (base %.3f)",
                instance.getValue(), instance.getBaseValue()).replace(".000", ""), false);
    }
}
