package at.koopro.wizardsandbeasts.apparition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

/**
 * One destination a wizard knows well enough to Apparate to — the "determination" half of the Three Ds.
 * Saved per player (see {@code PlayerApparitionPoints}) by standing somewhere and naming it, because
 * Apparition lore only permits travel to a place you have actually been.
 *
 * @param name      player-supplied label, shown in the selector
 * @param dimension the level it was marked in; Apparition never crosses dimensions
 * @param position  exact world position the player stood at
 * @param yaw       facing at mark time, restored on arrival so you do not land disoriented
 */
@NullMarked
public record ApparitionPoint(String name, ResourceKey<Level> dimension, Vec3 position, float yaw) {

    /** Upper bound on a label, so a hostile client cannot store unbounded strings on the server. */
    public static final int MAX_NAME_LENGTH = 32;

    public ApparitionPoint {
        name = sanitize(name);
    }

    public static final Codec<ApparitionPoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(ApparitionPoint::name),
            Identifier.CODEC.xmap(id -> ResourceKey.create(Registries.DIMENSION, id), ResourceKey::identifier)
                    .fieldOf("dimension").forGetter(ApparitionPoint::dimension),
            Vec3.CODEC.fieldOf("position").forGetter(ApparitionPoint::position),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0f).forGetter(ApparitionPoint::yaw)
    ).apply(instance, ApparitionPoint::new));

    /** Trims, collapses whitespace and truncates — the single place a label is made safe to store. */
    public static String sanitize(String raw) {
        String cleaned = raw.trim().replaceAll("\\s+", " ");
        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH) : cleaned;
    }

    /** Case-insensitive match on the label — how the player addresses a point from a command. */
    public boolean matches(String otherName) {
        return name.equalsIgnoreCase(sanitize(otherName));
    }
}
