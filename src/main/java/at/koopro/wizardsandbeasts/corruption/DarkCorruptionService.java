package at.koopro.wizardsandbeasts.corruption;

import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModAttributes;
import at.koopro.wizardsandbeasts.skill.vocation.VocationAbilityHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;

/**
 * The one way Dark Corruption moves. Every source — a Horcrux worn too long, the Resurrection Stone, ink in
 * Riddle's diary, an Unforgivable Curse — goes through {@link #accrue}, so the 0–100 clamp and the vocation
 * scaling hook are applied once instead of being re-typed at each call site (where one site had already
 * drifted and was skipping the vocation scaling entirely).
 */
@NullMarked
public final class DarkCorruptionService {

    public static final float MAX = 100.0f;

    private DarkCorruptionService() {}

    public static float get(Player player) {
        return player.getData(ModAttachments.DARK_CORRUPTION.get());
    }

    /**
     * Adds {@code baseAmount} of corruption, after vocation scaling, clamped to {@code [0, MAX]}.
     *
     * @return the player's corruption after the change
     */
    public static float accrue(ServerPlayer player, float baseAmount) {
        float current = get(player);
        if (current >= MAX) {
            return MAX;
        }
        float scaled = VocationAbilityHooks.scaleCorruptionGain(player, baseAmount);
        float next = Math.max(0.0f, Math.min(MAX, current + scaled));
        apply(player, next);
        return next;
    }

    /** Relieves corruption (cleansing rites, remorse). Clamped at zero; never vocation-scaled. */
    public static float relieve(ServerPlayer player, float amount) {
        float next = Math.max(0.0f, get(player) - Math.max(0.0f, amount));
        apply(player, next);
        return next;
    }

    /**
     * Pushes the persisted attachment value onto the display {@link ModAttributes#DARK_CORRUPTION}
     * Attribute. The attachment is the source of truth; the syncable Attribute is only a client-visible
     * mirror the character sheet reads. Call on login and respawn, since a freshly spawned player entity
     * starts the Attribute at its default (0) while {@code copyOnDeath} carries the real value across.
     */
    public static void syncDisplay(ServerPlayer player) {
        mirrorToAttribute(player, get(player));
    }

    private static void apply(ServerPlayer player, float value) {
        player.setData(ModAttachments.DARK_CORRUPTION.get(), value);
        mirrorToAttribute(player, value);
    }

    private static void mirrorToAttribute(ServerPlayer player, float value) {
        AttributeInstance instance = player.getAttribute(ModAttributes.DARK_CORRUPTION);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
