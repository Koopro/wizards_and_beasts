package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import at.koopro.wizardsandbeasts.network.heritage.HeritageDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.heritage.HeritageIdentitySyncS2CPayload;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.stats.PlayerStat;
import at.koopro.wizardsandbeasts.stats.PlayerStatsAPI;
import at.koopro.wizardsandbeasts.stats.StatEffects;
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

    public static void applyStats(ServerPlayer player) {
        PlayerHeritageData data = getData(player);
        Heritage heritage = data.getSelectedHeritage();
        HeritageVariant variant = data.getSelectedHeritageVariant();

        if (heritage == null) {
            removeStats(player);
            return;
        }

        // Species Resolve. This is the pool the player starts with, not its ceiling: the ceiling
        // comes from the WILLPOWER trait, so a Vampire opens with more charge than a House-Elf but
        // neither can hold more than their trained trait allows. Clamped rather than trusted,
        // because every seed above 50 exceeds what an untrained trait can carry.
        float seedResolve = switch (heritage) {
            case WIZARDKIND -> 50.0f;
            case WEREWOLF -> 70.0f;
            case GIANT -> 35.0f;
            case VAMPIRE -> 80.0f;
            case GOBLIN -> 60.0f;
            case HOUSE_ELF -> 20.0f;
            case CENTAUR -> 75.0f;
            default -> 50.0f;
        };
        float resolveCeiling = StatEffects.maxResolve(
                PlayerStatsAPI.getStat(player, PlayerStat.WILLPOWER));
        player.setData(ModAttachments.RESOLVE.get(), Math.min(seedResolve, resolveCeiling));

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
