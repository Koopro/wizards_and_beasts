package at.koopro.wizardsandbeasts.heritage.werewolf;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code werewolf_wolf} combat profile, as attribute modifiers.
 *
 * <p>Five modifiers under their own ids, deliberately separate from the {@code type_health} /
 * {@code type_speed} / {@code type_armor} trio {@code HeritageAPI.applyStats} owns. Sharing those ids
 * would have made the two systems clobber each other: {@code applyStats} runs on a timer and on every
 * heritage change, and whichever wrote last would win. Separate ids compose instead — the werewolf
 * heritage's baseline stays, and the wolf's body is added on top of it and taken off again cleanly.
 *
 * <p><b>Marked for datapack migration.</b> The numbers come from {@link WerewolfConfig} rather than
 * from data because the form system has no per-form stat layer yet — {@code SizeProfile} carries
 * geometry and reach, not combat. When a {@code form_stats} datapack type lands, this class becomes
 * its applier and the five config keys become that file's defaults; nothing else here needs to change.
 */
@NullMarked
public final class WerewolfAttributes {

    private static final Identifier HEALTH_ID = id("werewolf_wolf_health");
    private static final Identifier SPEED_ID = id("werewolf_wolf_speed");
    private static final Identifier ARMOR_ID = id("werewolf_wolf_armor");
    private static final Identifier ATTACK_ID = id("werewolf_wolf_attack");
    private static final Identifier KNOCKBACK_ID = id("werewolf_wolf_knockback");

    private WerewolfAttributes() {}

    /** Puts the wolf's body on. Idempotent — re-applying replaces rather than stacks. */
    public static void apply(ServerPlayer player) {
        applyModifier(player, Attributes.MAX_HEALTH, HEALTH_ID,
                WerewolfConfig.healthBonus, AttributeModifier.Operation.ADD_VALUE);
        applyModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID,
                WerewolfConfig.speedBonus, AttributeModifier.Operation.ADD_VALUE);
        applyModifier(player, Attributes.ARMOR, ARMOR_ID,
                WerewolfConfig.armorBonus, AttributeModifier.Operation.ADD_VALUE);
        applyModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID,
                WerewolfConfig.attackDamageBonus, AttributeModifier.Operation.ADD_VALUE);
        applyModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID,
                WerewolfConfig.knockbackResistanceBonus, AttributeModifier.Operation.ADD_VALUE);
    }

    /**
     * Takes it off again.
     *
     * <p>The health clamp is not optional: removing a {@code +6} max-health modifier from a player
     * sitting at full wolf health leaves them above their new maximum, which vanilla renders as
     * hearts that cannot be lost and which some damage paths read as a negative health pool.
     */
    public static void remove(ServerPlayer player) {
        removeModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
        removeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
        removeModifier(player, Attributes.ARMOR, ARMOR_ID);
        removeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
        removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);

        float max = player.getMaxHealth();
        if (player.getHealth() > max) {
            player.setHealth(max);
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    private static void applyModifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id,
                                      double amount, AttributeModifier.Operation operation) {
        var instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount != 0.0) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private static void removeModifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
        var instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }
}
