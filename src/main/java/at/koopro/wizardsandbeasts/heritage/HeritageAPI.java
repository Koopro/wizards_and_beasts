package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.heritage.vampire.VampireBloodAPI;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.heritage.HeritageIdentitySyncS2CPayload;
import at.koopro.wizardsandbeasts.form.FormSystemAPI;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import at.koopro.wizardsandbeasts.stats.StatResistModifiers;
import at.koopro.wizardsandbeasts.sync.PlayerStateSyncService;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;

public final class HeritageAPI {

    private static final Identifier TYPE_HEALTH_ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "type_health");
    private static final Identifier TYPE_SPEED_ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "type_speed");
    private static final Identifier TYPE_ARMOR_ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "type_armor");

    private HeritageAPI() {}

    public static PlayerHeritageData getData(ServerPlayer player) {
        return player.getData(ModAttachments.HERITAGE_DATA.get());
    }

    @Nullable
    public static Heritage getPlayerHeritage(ServerPlayer player) {
        return getData(player).getSelectedHeritage();
    }

    @Nullable
    public static HeritageVariant getPlayerHeritageVariant(ServerPlayer player) {
        return getData(player).getSelectedHeritageVariant();
    }

    public static boolean hasHeritageSelected(ServerPlayer player) {
        return getData(player).hasHeritageSelected();
    }

    public static boolean isLocked(ServerPlayer player) {
        return getData(player).isLocked();
    }

    /**
     * Publishes a transformation the player has just undergone: to themselves, and to everyone who can
     * see them.
     *
     * <p>Both halves matter and they are easy to get half-right. {@code HeritageDataSyncS2CPayload}
     * goes to one player, which is what the HUD needs; the identity payload goes to every tracker,
     * which is what a renderer needs. Every site that changed {@code transformationState} used to send
     * only the first, so a transformed werewolf knew they were a wolf and nobody else on the server
     * did — correct in single-player, wrong the moment a second person is watching.
     *
     * <p>Call this instead of the payloads directly whenever the shape a player is in changes, so the
     * next transformation trigger cannot repeat that.
     */
    public static void syncTransformation(ServerPlayer player) {
        HeritageDataSyncS2CPayload.syncToPlayer(player, false);
        HeritageIdentitySyncS2CPayload.syncToTracking(player);
    }

    /**
     * Everything that has to happen when a player's heritage becomes, or becomes something else.
     *
     * <p>This exists because there were three of these. Committing at the first-join gate
     * ({@code HeritageSelectC2SPayload}) did eight things; {@code /wandb player heritage set} did four of
     * them; {@code /wandb debug dev kit heritage} did three. The four the admin path was missing are the
     * four a player would notice — so switching someone from Wizardkind to Giant left them with the
     * Wizardkind body, the Wizardkind POWER band, stale heritage-source ability grants, and an appearance
     * only they could see, because the identity payload that reaches other clients was never sent.
     *
     * <p>One ordering here is load-bearing: {@code FormSystemAPI.resetToDefault} reads the heritage and
     * lineage fields back off the attachment to work out which body to assign, so it has to run after they
     * are written and not alongside them.
     *
     * <p><b>Not gated on {@link at.koopro.wizardsandbeasts.module.Module#PLAYER_STATS}, deliberately.</b>
     * Every other stat write in the mod is — training, the cast modifiers, the tuition discount — because
     * each of those is an ongoing effect nobody can see with the module off. The POWER roll is not an
     * effect; it is the character, made once at a gate that is passed once. Skipping it would leave every
     * player who joined while the module was off permanently at POWER 0 with nothing able to roll for them
     * afterwards. The roll is always recorded; whether it does anything is {@code StatCastModifiers}' gate.
     *
     * <p>Profession points are deliberately not here. The gate awards three and the dev kit tops up to ten;
     * that is the callers' business, and folding it in would make one of them wrong.
     *
     * <p>The blood pool <em>is</em> here, and for the opposite reason: it is not a reward, it is part of
     * the body. A heritage whose nutrition policy is BLOOD arrives with a full pool the way it arrives
     * with a size profile, and one that is not simply carries an unused one. See
     * {@link at.koopro.wizardsandbeasts.heritage.nutrition.NutritionPolicyResolver}.
     *
     * @param heritage the heritage to commit; the player is locked to it
     * @param variant  a lineage of {@code heritage} — callers validate the pairing before calling
     */
    public static void commit(ServerPlayer player, Heritage heritage, HeritageVariant variant) {
        PlayerHeritageData data = getData(player);
        HeritageVariant previous = data.getSelectedHeritageVariant();

        data.setSelectedHeritage(heritage);
        data.setSelectedHeritageVariant(variant);
        data.setLocked(true);

        // A change of lineage re-rolls against the new band; anything else only fills in a block that was
        // never rolled at all. The two are separate calls because only the second may refuse to run — see
        // PlayerStatsAPI.rerollHeritagePower for why re-rolling through the new-player initialiser used to
        // delete every trained stat.
        //
        // Committing the lineage a player already has does NOT re-roll. Re-running the admin set command,
        // or a duplicate packet from the gate, must not shuffle a number the player has already been shown;
        // /wandb player stats reroll_power is the command that exists to do that on purpose.
        if (previous != null && previous != variant) {
            PlayerStatsAPI.rerollHeritagePower(player, variant, player.getRandom());
        } else if (PlayerStatsAPI.getData(player).isEmpty()) {
            // isEmpty() rather than a bare call: the initialiser logs a warning when it declines, and a
            // same-lineage commit declining is normal rather than something an operator should see.
            PlayerStatsAPI.initializeStatsForNewPlayer(player, variant, player.getRandom());
        }

        applyStats(player);
        seedResolve(player, heritage);
        // The pool that goes with the body. Unconditional rather than gated on the blood policy: a player
        // leaving a blood heritage needs the pool cleared just as much as one arriving at it needs it
        // filled, and VampireBloodAPI.seed is the same call for both.
        VampireBloodAPI.seed(player);
        // The body that goes with the heritage. Without it the commit sets a heritage, a variant and a stat
        // spread and leaves activeFormId pointing at whatever the player was before, so the size profile
        // never changes and every heritage after the first looks like the first one.
        FormSystemAPI.resetToDefault(player);

        HeritageDataSyncS2CPayload.syncToPlayer(player, false);
        // ...and to everyone who can see them. The line above reaches only this player, which is enough for
        // their own HUD and useless for rendering: a visible heritage is one other people can see.
        HeritageIdentitySyncS2CPayload.syncToTracking(player);
        // Committing a heritage sets the variant tags that back HERITAGE-source ability grants.
        PlayerStateSyncService.syncAbilityGrants(player);
    }

    /**
     * Takes a heritage back off a player, all the way — the inverse of {@link #commit}.
     *
     * <p>{@code data.reset()} clears the fields and nothing else, so the admin reset used to leave the
     * player wearing the old heritage's size profile, holding its POWER roll, and still granted its
     * abilities. The POWER roll was the worst of the three and the least visible: it is what makes
     * {@code initializeStatsForNewPlayer} decide the block is not fresh, so the heritage chosen next never
     * rolled at all and kept the old lineage's number on the new lineage's band.
     *
     * <p>Trained stats survive. Losing a heritage is not losing the practice you did with it.
     *
     * @param openSelector whether the client should be put back in front of the first-join gate. Resetting
     *                     without it leaves a player with no heritage and no way to pick one.
     */
    public static void clear(ServerPlayer player, boolean openSelector) {
        getData(player).reset();

        removeStats(player);
        PlayerStatsAPI.clearHeritageRoll(player);
        FormSystemAPI.clearForm(player);
        VampireBloodAPI.clear(player);

        HeritageDataSyncS2CPayload.syncToPlayer(player, openSelector);
        HeritageIdentitySyncS2CPayload.syncToTracking(player);
        PlayerStateSyncService.syncAbilityGrants(player);
    }

    public static void applyStats(ServerPlayer player) {
        PlayerHeritageData data = getData(player);
        Heritage heritage = data.getSelectedHeritage();
        HeritageVariant variant = data.getSelectedHeritageVariant();

        if (heritage == null) {
            removeStats(player);
            return;
        }

        // Resolve is only ever clamped here, never seeded. Seeding is a heritage's opening charge and
        // belongs to the one moment a heritage is taken on, which is what seedResolve() is called from.
        // This method also runs on every login, respawn and dimension change (PlayerStateSyncService),
        // so seeding here quietly defeated the attachment's own persistence: a pool that survives death
        // by copyOnDeath was refilled to the species opening on the next login anyway, and an Imperius
        // victim three failures from empty could reconnect for a full one.
        //
        // The clamp still has to happen, because the ceiling is trait-derived and can move under a
        // stored pool. It is the same settle the Resolve tick makes; doing it here too means a player
        // whose trait dropped while logged out is correct on the frame they log back in.
        float resolveCeiling = StatResistModifiers.maxResolve(player);
        float resolve = player.getData(ModAttachments.RESOLVE.get());
        if (resolve > resolveCeiling) {
            player.setData(ModAttachments.RESOLVE.get(), resolveCeiling);
        }

        double healthMod = heritage.getBaseHealth();
        double speedMod = heritage.getBaseSpeed();
        double armorMod = heritage.getBaseArmor();

        if (variant != null) {
            healthMod += variant.getHealthMod();
            speedMod += variant.getSpeedMod();
            armorMod += variant.getArmorMod();
        }

        if (heritage == Heritage.OBSCURIAL && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            healthMod += ObscurialRules.getHealthBonus(data);
            speedMod += ObscurialRules.getSpeedBonus(data, level, player);
            armorMod += ObscurialRules.getArmorBonus(data);
        }

        applyModifier(player, Attributes.MAX_HEALTH, TYPE_HEALTH_ID, healthMod);
        applyModifier(player, Attributes.MOVEMENT_SPEED, TYPE_SPEED_ID, speedMod);
        applyModifier(player, Attributes.ARMOR, TYPE_ARMOR_ID, armorMod);

        // Cap health to new max if health was decreased
        if (healthMod < 0) {
            float maxHealth = player.getMaxHealth();
            if (player.getHealth() > maxHealth) {
                player.setHealth(maxHealth);
            }
        }
    }

    /**
     * Fills the Resolve pool to the opening charge this heritage is born with, clamped to what the
     * WILLPOWER trait can hold.
     *
     * <p>A species trait rather than a stat: a Vampire opens with more fight in them than a House-Elf, and
     * neither can hold more than they have trained for. Every seed above 50 exceeds an untrained ceiling,
     * so the clamp is doing real work rather than guarding against a typo.
     *
     * <p>Called from {@link #commit} alone. See {@link #applyStats} for why it is not called from there.
     */
    private static void seedResolve(ServerPlayer player, Heritage heritage) {
        float seed = switch (heritage) {
            case WIZARDKIND -> 50.0f;
            case WEREWOLF -> 70.0f;
            case GIANT -> 35.0f;
            case VAMPIRE -> 80.0f;
            case GOBLIN -> 60.0f;
            case HOUSE_ELF -> 20.0f;
            case CENTAUR -> 75.0f;
            default -> 50.0f;
        };
        player.setData(ModAttachments.RESOLVE.get(),
                Math.min(seed, StatResistModifiers.maxResolve(player)));
    }

    public static void removeStats(ServerPlayer player) {
        var health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.removeModifier(TYPE_HEALTH_ID);

        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(TYPE_SPEED_ID);

        var armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.removeModifier(TYPE_ARMOR_ID);
    }

    private static void applyModifier(ServerPlayer player,
                                       net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                       Identifier id, double amount) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(id, amount,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
