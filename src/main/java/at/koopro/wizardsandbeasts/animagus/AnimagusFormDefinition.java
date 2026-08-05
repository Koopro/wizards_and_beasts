package at.koopro.wizardsandbeasts.animagus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * A beast form an Animagus may take, loaded from {@code data/<namespace>/animagus_forms/*.json}.
 * <p>
 * The rig, the physical box, the attribute deltas and the powers all live here rather than in Java,
 * so a form is a data edit. Two invariants are enforced at load rather than at use, because a
 * malformed form that only fails once a player transforms is a far worse failure than a rejected
 * datapack:
 * <ul>
 *   <li>{@link AnimagusCapability#FLIGHT} and the {@code flight} block imply each other. A flier
 *       with no physics would fall out of the sky; physics on a non-flier is dead weight that reads
 *       as a working feature.</li>
 *   <li>{@code animation_map} must name every animation the renderer will ask for, including the
 *       two extra clips a flier needs. The renderer looks animations up by role and never hardcodes
 *       a clip name, so a missing key is a silent T-pose at runtime.</li>
 * </ul>
 */
@NullMarked
public record AnimagusFormDefinition(
        AnimagusCanonTier canonTier,
        Identifier model,
        Identifier texture,
        Identifier animations,
        Map<String, String> animationMap,
        AnimagusHitbox hitbox,
        Map<Identifier, Double> attributes,
        Set<AnimagusCapability> capabilities,
        Optional<AnimagusFlight> flight,
        AnimagusSounds sounds) {

    /** Animation roles every form must supply. */
    public static final List<String> REQUIRED_ANIMATIONS = List.of("idle", "walk", "run", "jump", "hurt");

    /** Additional animation roles a {@link AnimagusCapability#FLIGHT} form must supply. */
    public static final List<String> REQUIRED_FLIGHT_ANIMATIONS = List.of("glide", "flap");

    private static final Codec<AnimagusFormDefinition> RAW = RecordCodecBuilder.create(instance -> instance.group(
            AnimagusCanonTier.CODEC.fieldOf("canon_tier").forGetter(AnimagusFormDefinition::canonTier),
            Identifier.CODEC.fieldOf("model").forGetter(AnimagusFormDefinition::model),
            Identifier.CODEC.fieldOf("texture").forGetter(AnimagusFormDefinition::texture),
            Identifier.CODEC.fieldOf("animations").forGetter(AnimagusFormDefinition::animations),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("animation_map")
                    .forGetter(AnimagusFormDefinition::animationMap),
            AnimagusHitbox.CODEC.fieldOf("hitbox").forGetter(AnimagusFormDefinition::hitbox),
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).fieldOf("attributes")
                    .forGetter(AnimagusFormDefinition::attributes),
            capabilitySetCodec().fieldOf("capabilities").forGetter(AnimagusFormDefinition::capabilities),
            AnimagusFlight.CODEC.optionalFieldOf("flight").forGetter(AnimagusFormDefinition::flight),
            AnimagusSounds.CODEC.optionalFieldOf("sounds", AnimagusSounds.NONE)
                    .forGetter(AnimagusFormDefinition::sounds)
    ).apply(instance, AnimagusFormDefinition::new));

    public static final Codec<AnimagusFormDefinition> CODEC =
            RAW.comapFlatMap(AnimagusFormDefinition::validate, Function.identity());

    private static Codec<Set<AnimagusCapability>> capabilitySetCodec() {
        return AnimagusCapability.CODEC.listOf().xmap(
                list -> list.isEmpty()
                        ? EnumSet.noneOf(AnimagusCapability.class)
                        : EnumSet.copyOf(list),
                List::copyOf);
    }

    private static DataResult<AnimagusFormDefinition> validate(AnimagusFormDefinition def) {
        boolean flies = def.hasCapability(AnimagusCapability.FLIGHT);

        if (flies && def.flight.isEmpty()) {
            return DataResult.error(() ->
                    "form declares the FLIGHT capability but carries no 'flight' block");
        }
        if (!flies && def.flight.isPresent()) {
            return DataResult.error(() ->
                    "form carries a 'flight' block but does not declare the FLIGHT capability");
        }

        for (String role : REQUIRED_ANIMATIONS) {
            if (!def.animationMap.containsKey(role)) {
                return DataResult.error(() -> "animation_map is missing the required role '" + role + "'");
            }
        }
        if (flies) {
            for (String role : REQUIRED_FLIGHT_ANIMATIONS) {
                if (!def.animationMap.containsKey(role)) {
                    return DataResult.error(() ->
                            "a FLIGHT form's animation_map is missing the required role '" + role + "'");
                }
            }
        }

        return DataResult.success(def);
    }

    public boolean hasCapability(AnimagusCapability capability) {
        return capabilities.contains(capability);
    }

    /**
     * Resolves an animation role ({@code "walk"}) to the clip name in this form's animation file.
     * Returns empty rather than a guessed name — the codec has already guaranteed every role the
     * renderer asks for is present, so an empty result means the caller invented a role.
     */
    public Optional<String> animation(String role) {
        return Optional.ofNullable(animationMap.get(role));
    }
}
