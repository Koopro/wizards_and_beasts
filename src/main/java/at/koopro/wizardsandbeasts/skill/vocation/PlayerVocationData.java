package at.koopro.wizardsandbeasts.skill.vocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * Per-player declared Vocation. Registered as a {@code Codec}-backed {@code AttachmentType} in
 * {@code ModAttachments}. Absent on existing players → no declaration (backwards compatible).
 *
 * <p>Web rework Phase 3 removed the secondary slot from the schema. The codec still <i>reads</i> a
 * legacy {@code secondary} key into {@link #legacySecondaryPresent} (never re-written) so the
 * login migration can log-and-clear it exactly once; after the next save the key is gone.
 *
 * @param primary                declared Vocation — identity only, zero allocation gating
 * @param legacySecondaryPresent transient: true iff the loaded NBT still carried the removed
 *                               {@code secondary} slot (drives the one-time migration log)
 */
@NullMarked
public record PlayerVocationData(Optional<Identifier> primary, boolean legacySecondaryPresent) {

    public static final PlayerVocationData EMPTY = new PlayerVocationData(Optional.empty(), false);

    public static final Codec<PlayerVocationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("primary").forGetter(PlayerVocationData::primary),
            // Read-only legacy field: presence is remembered for the migration log; encode always omits it.
            Identifier.CODEC.optionalFieldOf("secondary")
                    .forGetter(data -> Optional.<Identifier>empty())
    ).apply(instance, (primary, legacySecondary) ->
            new PlayerVocationData(primary, legacySecondary.isPresent())));

    public PlayerVocationData withPrimary(Optional<Identifier> value) {
        return new PlayerVocationData(value, legacySecondaryPresent);
    }

    /** Same declaration with the legacy-secondary marker cleared (post-migration state). */
    public PlayerVocationData withoutLegacySecondary() {
        return new PlayerVocationData(primary, false);
    }
}
