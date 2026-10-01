package at.koopro.wizardsandbeasts.admin.player;

import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Everything an administrator may do to a player from the Players section, and nothing else. Each action names the
 * authority it needs and whether it is destructive — a destructive action is refused unless it arrives confirmed.
 * The client sends one of these, a target UUID and a bounded argument; what the action does is decided here, on the
 * server, through each system's own API.
 */
@NullMarked
public enum PlayerAdminAction {
    /** Argument {@code heritage/lineage}. Delegates to {@code HeritageAdminService.assign}. */
    HERITAGE_ASSIGN(AdminCapability.PLAYERS, true),
    /** Back to the onboarding gate. Delegates to {@code HeritageAdminService.resetOnboarding}. */
    HERITAGE_RESET(AdminCapability.PLAYERS, true),
    /** Argument: spell id. Teaches it only if the learning rules allow it (heritage, prerequisites, modules). */
    SPELL_UNLOCK(AdminCapability.PLAYERS, false),
    /** Argument: spell id. Teaches it past the learning rules, as {@code /wandb magic spell learn} does. */
    SPELL_GRANT(AdminCapability.PLAYERS, true),
    /** Argument: spell id. Forgets it (and empties any loadout slot holding it); practice on it is kept. */
    SPELL_REVOKE(AdminCapability.PLAYERS, true),
    /** Clears practice, proficiency, casts and hits for every spell; the spells stay known. */
    SPELL_RESET_PROGRESS(AdminCapability.PLAYERS, true),
    /** Amount: skill points to award, through the same capped path the game uses. */
    SKILL_POINTS_ADD(AdminCapability.PLAYERS, false),
    /** Every node refunded and web-taught spells taken back — the respec, without its fee. */
    SKILL_RESET(AdminCapability.PLAYERS, true),
    /** Argument: effect id. Removes that one effect. */
    EFFECT_REMOVE(AdminCapability.PLAYERS, false),
    /** Removes every active effect, as {@code /effect clear} does. */
    EFFECTS_CLEAR(AdminCapability.PLAYERS, true),
    /**
     * Argument: effect id. Applies it for a few seconds to the <em>administrator</em>, never to the target — a look
     * at what it does, not a way to put effects on players.
     */
    EFFECT_PREVIEW(AdminCapability.PLAYERS, false),
    /** Clears notoriety, wanted level, sentence, fugitive status and the outstanding fine. The file stays. */
    MINISTRY_PARDON(AdminCapability.PLAYERS, true),
    /** Waives the outstanding fine only. */
    MINISTRY_WAIVE_FINE(AdminCapability.PLAYERS, true),
    /** Argument {@code galleons|sickles|knuts}, amount bounded. Creates money: MONEY authority. */
    MONEY_DEPOSIT(AdminCapability.MONEY, true),
    /** Argument {@code galleons|sickles|knuts}, amount bounded. Refused when the vault does not hold it. */
    MONEY_WITHDRAW(AdminCapability.MONEY, true);

    private final AdminCapability capability;
    private final boolean destructive;

    PlayerAdminAction(AdminCapability capability, boolean destructive) {
        this.capability = capability;
        this.destructive = destructive;
    }

    public AdminCapability capability() {
        return capability;
    }

    /** Refused unless confirmed. */
    public boolean destructive() {
        return destructive;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable PlayerAdminAction byId(String id) {
        for (PlayerAdminAction action : values()) {
            if (action.id().equals(id)) {
                return action;
            }
        }
        return null;
    }
}
