package at.koopro.wizardsandbeasts.ability.def;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

/**
 * What an ability needs the client to pick before it can fire. Framework-level taxonomy only — the pick
 * itself happens client-side (crosshair ray) and is re-validated server-side against
 * {@link AbilityInput#range()} before dispatch.
 *
 * <ul>
 *   <li>{@link #NONE} — no target; the trigger carries an empty
 *       {@code at.koopro.wizardsandbeasts.ability.trigger.AbilityTarget}.</li>
 *   <li>{@link #BLOCK} — a world position (block hit, or a ray-end fallback at max range).</li>
 *   <li>{@link #ENTITY} — an entity under the crosshair.</li>
 * </ul>
 */
@NullMarked
public enum AbilityTargeting implements StringRepresentable {
    NONE("none"),
    BLOCK("block"),
    ENTITY("entity");

    public static final Codec<AbilityTargeting> CODEC = StringRepresentable.fromEnum(AbilityTargeting::values);

    private final String serializedName;

    AbilityTargeting(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
