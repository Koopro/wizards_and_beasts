package at.koopro.wizardsandbeasts.skill.vocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * Per-player Vocation commitment state. Registered as a {@code Codec}-backed {@code AttachmentType} in
 * {@code ModAttachments} — fully separate from {@code PlayerSkillData}. Absent on existing players → both
 * slots empty (backwards compatible).
 *
 * @param primary   committed primary Vocation (carries the commitment stat profile)
 * @param secondary committed secondary Vocation (Mastery access only — no stat profile in v1)
 */
@NullMarked
public record PlayerVocationData(Optional<Identifier> primary, Optional<Identifier> secondary) {

    public static final PlayerVocationData EMPTY = new PlayerVocationData(Optional.empty(), Optional.empty());

    public static final Codec<PlayerVocationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("primary").forGetter(PlayerVocationData::primary),
            Identifier.CODEC.optionalFieldOf("secondary").forGetter(PlayerVocationData::secondary)
    ).apply(instance, PlayerVocationData::new));

    public PlayerVocationData withPrimary(Optional<Identifier> value) {
        return new PlayerVocationData(value, secondary);
    }

    public PlayerVocationData withSecondary(Optional<Identifier> value) {
        return new PlayerVocationData(primary, value);
    }
}
