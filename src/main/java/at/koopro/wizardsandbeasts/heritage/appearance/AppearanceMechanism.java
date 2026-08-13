package at.koopro.wizardsandbeasts.heritage.appearance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.StringRepresentable;

/**
 * One way a heritage expresses itself on the player's body.
 *
 * <p>A <b>tagged union</b>, not a numeric intensity. The three arms are different mechanisms with
 * different render paths, and collapsing them into a "manifestation level 0–3" was the mistake the
 * superseded draft made: a proportion change and a model swap have nothing in common except that
 * both are visible.
 *
 * <ul>
 *   <li>{@link Proportion} — the player keeps their own skin and their own model; scale and per-bone
 *       offsets only. Canon treats part-giants and the like as differently-proportioned humans.</li>
 *   <li>{@link FormAppearance} — the player model is replaced outright while a trigger holds. The
 *       player's skin does not render, which is correct: they are not a person at that moment.</li>
 *   <li>{@link OverlayAppearance} — composites <em>on top of</em> whichever of the other two is
 *       beneath. Ordering is a correctness requirement, not a preference.</li>
 * </ul>
 *
 * <p>Sealed interface plus a {@link Type}-keyed dispatch {@link Codec}, mirroring
 * {@code SkillNodeEffect} and {@code CreatureAbility} exactly — the dispatch key is a stable
 * serialized string and each arm supplies its own {@link MapCodec}.
 *
 * <p><b>Data layer only.</b> Nothing here may import a client-only type. That is why
 * {@link Proportion} keys its bone offsets by {@code String} rather than by
 * {@code client.pose.PlayerModelPart}: the record is decoded on a dedicated server too, and the
 * name-to-part resolution belongs on the client side of the fence.
 */
public sealed interface AppearanceMechanism permits Proportion, FormAppearance, OverlayAppearance {

    /** Dispatch codec keyed by {@link Type}, mirroring {@code SkillNodeEffect.CODEC}. */
    Codec<AppearanceMechanism> CODEC = Type.CODEC.dispatch(AppearanceMechanism::type, Type::codec);

    Type type();

    /**
     * True for the arms that replace or reshape the body itself, of which an entry may hold at most
     * one. Overlays are additive and sit outside that constraint.
     */
    default boolean isBodyArm() {
        return type() != Type.OVERLAY;
    }

    enum Type implements StringRepresentable {
        PROPORTION("proportion", Proportion.CODEC),
        FORM("form", FormAppearance.CODEC),
        OVERLAY("overlay", OverlayAppearance.CODEC);

        public static final Codec<Type> CODEC = StringRepresentable.fromValues(Type::values);

        private final String serializedName;
        private final MapCodec<? extends AppearanceMechanism> codec;

        Type(String serializedName, MapCodec<? extends AppearanceMechanism> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        public MapCodec<? extends AppearanceMechanism> codec() {
            return codec;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
