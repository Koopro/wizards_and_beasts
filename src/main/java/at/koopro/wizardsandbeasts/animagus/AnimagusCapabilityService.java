package at.koopro.wizardsandbeasts.animagus;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.Set;

/**
 * Makes an {@link AnimagusFormDefinition} do something.
 *
 * <p>Before this, the datapack type was <b>inert</b>. {@code hitbox()}, {@code attributes()},
 * {@code flight()}, {@code sounds()}, {@code animationMap()} and {@code animations()} had zero readers
 * anywhere in the source tree; the whole file was loaded, validated against invariants, synced to every
 * client, and then consulted by nothing. The behaviour players actually got came from a hardcoded
 * {@code switch} on form id in {@code AnimagusAbilityService} — keyed on the <em>other</em> of the mod's
 * two Animagus id vocabularies.
 *
 * <p>This class closes the two halves that are reachable today: the attribute block, and the
 * {@link AnimagusCapability#CLIMB} capability. See the class javadoc on {@link AnimagusFormLoader} for
 * which forms those actually reach, and {@code documentation/TRANSFORMED_PLAYER_CONTRACT.md} §7 for what
 * is still inert.
 *
 * <h2>Why scale and step height are skipped</h2>
 * They are already owned by {@code SizeProfileRegistry}, which registers a hand-written profile per
 * {@code animagus_*} form and applies {@code SCALE} / {@code STEP_HEIGHT} through {@code SizeSystemAPI}.
 * Applying the definition's values on top would stack a second modifier on the same attribute and scale
 * the player twice.
 *
 * <p>That the two disagree is a real problem and not one this class should decide: {@code cat.json} says
 * scale 0.55 where the profile says 1.0, and {@code dog.json}'s hitbox (0.8 × 0.9) is not the profile's
 * (0.60 × 0.85). Two parallel descriptions of one form, already drifted. Unifying them means making the
 * size profile derive from the definition, which is a change to the form system rather than to this file.
 */
@NullMarked
public final class AnimagusCapabilityService {

    /**
     * Attributes the size system owns. Never applied from a definition — see the class javadoc.
     */
    private static final Set<String> SIZE_OWNED = Set.of(
            "minecraft:scale", "minecraft:step_height");

    /** Upward speed while climbing, in blocks per tick. Vanilla's own ladder speed. */
    private static final double CLIMB_SPEED = 0.2;

    private AnimagusCapabilityService() {}

    // ── the attribute block ────────────────────────────────────────────

    /**
     * Applies the form's {@code attributes} map as transient modifiers.
     *
     * <p>Each gets its own id derived from the attribute's registry key, so removal is exact and nothing
     * here can collide with the heritage stats, the size profile or the werewolf's body. Unknown
     * attribute ids are skipped rather than fatal: a datapack naming an attribute from a mod that is not
     * installed should cost that one line, not the transformation.
     *
     * <p>{@code ADD_VALUE} throughout, matching how the values read in the shipped files — {@code dog}'s
     * {@code max_health: -4.0} means "four half-hearts fewer", not "×-4".
     */
    public static void applyAttributes(ServerPlayer player, AnimagusFormDefinition definition) {
        for (Map.Entry<Identifier, Double> entry : definition.attributes().entrySet()) {
            Identifier attributeId = entry.getKey();
            if (SIZE_OWNED.contains(attributeId.toString())) {
                continue;
            }
            Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.get(attributeId).orElse(null);
            if (attribute == null) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            Identifier modifierId = modifierId(attributeId);
            instance.removeModifier(modifierId);
            if (entry.getValue() != 0.0) {
                instance.addTransientModifier(new AttributeModifier(
                        modifierId, entry.getValue(), AttributeModifier.Operation.ADD_VALUE));
            }
        }
        clampHealth(player);
    }

    /**
     * Removes everything {@link #applyAttributes} added for this form.
     *
     * <p>Takes the definition rather than sweeping every attribute, so a form that stops declaring an
     * attribute across a {@code /reload} still has its old modifier removed on the next revert — the ids
     * are derived from the attribute, not stored per player.
     */
    public static void removeAttributes(ServerPlayer player, AnimagusFormDefinition definition) {
        for (Identifier attributeId : definition.attributes().keySet()) {
            Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.get(attributeId).orElse(null);
            if (attribute == null) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(modifierId(attributeId));
            }
        }
        clampHealth(player);
    }

    /**
     * {@code minecraft:max_health} → {@code wizards_and_beasts:animagus_form_minecraft.max_health}.
     *
     * <p>Namespace folded into the path because an {@link Identifier} path may not contain a colon, and
     * the attribute's own namespace has to survive so that two mods' identically-pathed attributes get
     * different modifier ids.
     */
    private static Identifier modifierId(Identifier attributeId) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID,
                "animagus_form_" + attributeId.getNamespace() + "." + attributeId.getPath());
    }

    /** A negative max-health modifier can leave current health above the new maximum. */
    private static void clampHealth(ServerPlayer player) {
        float max = player.getMaxHealth();
        if (player.getHealth() > max) {
            player.setHealth(max);
        }
    }

    // ── CLIMB ──────────────────────────────────────────────────────────

    /**
     * Ascend by walking into a wall.
     *
     * <p>Called each server tick while transformed. Deliberately not the vanilla ladder path
     * ({@code onClimbable} is driven by block state and cannot be granted to a player), so this is the
     * spider's rule instead: pressed against something solid and still pushing into it, you go up.
     *
     * <p>Reads the client's own input rather than velocity, because a body pinned against a wall has
     * almost no horizontal velocity left to test — the same reason {@code BubbleFloatHandler} reads
     * {@code getLastClientInput}.
     */
    public static void tickClimb(ServerPlayer player, AnimagusFormDefinition definition) {
        if (!definition.hasCapability(AnimagusCapability.CLIMB)) {
            return;
        }
        if (!player.horizontalCollision || player.getAbilities().flying || player.isPassenger()) {
            return;
        }
        if (!player.getLastClientInput().forward()) {
            return;
        }
        Vec3 delta = player.getDeltaMovement();
        player.setDeltaMovement(delta.x, CLIMB_SPEED, delta.z);
        player.resetFallDistance();
        player.hurtMarked = true;
    }
}
