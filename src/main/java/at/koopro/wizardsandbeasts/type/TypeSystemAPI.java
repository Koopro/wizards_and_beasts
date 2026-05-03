package at.koopro.wizardsandbeasts.type;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.data.PlayerTypeData;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

public final class TypeSystemAPI {

    private static final Identifier TYPE_HEALTH_ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "type_health");
    private static final Identifier TYPE_SPEED_ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "type_speed");
    private static final Identifier TYPE_ARMOR_ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "type_armor");

    private TypeSystemAPI() {}

    public static PlayerTypeData getData(ServerPlayer player) {
        return player.getData(ModAttachments.TYPE_DATA.get());
    }

    @Nullable
    public static WizType getPlayerType(ServerPlayer player) {
        return getData(player).getSelectedType();
    }

    @Nullable
    public static WizSubtype getPlayerSubtype(ServerPlayer player) {
        return getData(player).getSelectedSubtype();
    }

    public static boolean hasTypeSelected(ServerPlayer player) {
        return getData(player).hasTypeSelected();
    }

    public static boolean isLocked(ServerPlayer player) {
        return getData(player).isLocked();
    }

    public static void applyStats(ServerPlayer player) {
        PlayerTypeData data = getData(player);
        WizType type = data.getSelectedType();
        WizSubtype subtype = data.getSelectedSubtype();

        if (type == null) {
            removeStats(player);
            return;
        }

        double healthMod = type.getBaseHealth();
        double speedMod = type.getBaseSpeed();
        double armorMod = type.getBaseArmor();

        if (subtype != null) {
            healthMod += subtype.getHealthMod();
            speedMod += subtype.getSpeedMod();
            armorMod += subtype.getArmorMod();
        }

        if (type == WizType.OBSCURIAL && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
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
