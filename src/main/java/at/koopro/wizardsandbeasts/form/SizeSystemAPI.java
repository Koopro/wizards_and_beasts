package at.koopro.wizardsandbeasts.form;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import org.jspecify.annotations.Nullable;

/**
 * Server-side API for applying size profiles to players via attribute modifiers.
 * <p>
 * Uses vanilla 1.21+ attributes: {@code SCALE}, {@code STEP_HEIGHT},
 * {@code BLOCK_INTERACTION_RANGE}, {@code ENTITY_INTERACTION_RANGE},
 * and {@code KNOCKBACK_RESISTANCE}.
 */
public final class SizeSystemAPI {

    private static final Identifier SIZE_SCALE_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "size_scale");
    private static final Identifier SIZE_STEP_HEIGHT_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "size_step_height");
    private static final Identifier SIZE_REACH_BLOCK_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "size_reach_block");
    private static final Identifier SIZE_REACH_ENTITY_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "size_reach_entity");
    private static final Identifier SIZE_KNOCKBACK_RES_ID =
            Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "size_knockback_res");

    private SizeSystemAPI() {}

    /**
     * Applies the given size profile to the player, replacing any existing size modifiers.
     * This sets attribute modifiers server-side; vanilla automatically recalculates
     * hitbox dimensions and visual scale from {@code Attributes.SCALE}.
     */
    public static void applyProfile(ServerPlayer player, SizeProfile profile) {
        // Scale (ADD_MULTIPLIED_BASE: base * value added, so value = scaleY - 1.0)
        applyModifier(player, Attributes.SCALE, SIZE_SCALE_ID,
                profile.scaleAttributeValue(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        // Step height (ADD_VALUE)
        applyModifier(player, Attributes.STEP_HEIGHT, SIZE_STEP_HEIGHT_ID,
                profile.stepHeight(), AttributeModifier.Operation.ADD_VALUE);

        // Reach — block interaction
        applyModifier(player, Attributes.BLOCK_INTERACTION_RANGE, SIZE_REACH_BLOCK_ID,
                profile.reachBonus(), AttributeModifier.Operation.ADD_VALUE);

        // Reach — entity interaction
        applyModifier(player, Attributes.ENTITY_INTERACTION_RANGE, SIZE_REACH_ENTITY_ID,
                profile.reachBonus(), AttributeModifier.Operation.ADD_VALUE);

        // Knockback resistance
        applyModifier(player, Attributes.KNOCKBACK_RESISTANCE, SIZE_KNOCKBACK_RES_ID,
                profile.knockbackResistance(), AttributeModifier.Operation.ADD_VALUE);

        // Force dimension recalculation
        player.refreshDimensions();
    }

    /**
     * Removes all size-related attribute modifiers from the player.
     */
    public static void removeProfile(ServerPlayer player) {
        removeModifier(player, Attributes.SCALE, SIZE_SCALE_ID);
        removeModifier(player, Attributes.STEP_HEIGHT, SIZE_STEP_HEIGHT_ID);
        removeModifier(player, Attributes.BLOCK_INTERACTION_RANGE, SIZE_REACH_BLOCK_ID);
        removeModifier(player, Attributes.ENTITY_INTERACTION_RANGE, SIZE_REACH_ENTITY_ID);
        removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, SIZE_KNOCKBACK_RES_ID);
        player.refreshDimensions();
    }

    /**
     * Applies a size profile by ID from the registry.
     *
     * @return the applied profile, or null if not found
     */
    @Nullable
    public static SizeProfile applyProfileById(ServerPlayer player, String profileId) {
        SizeProfile profile = SizeProfileRegistry.get(profileId);
        if (profile != null) {
            applyProfile(player, profile);
        }
        return profile;
    }

    private static void applyModifier(ServerPlayer player,
                                       net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                       Identifier id, double amount,
                                       AttributeModifier.Operation operation) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private static void removeModifier(ServerPlayer player,
                                        net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                        Identifier id) {
        var instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }
}
