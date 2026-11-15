package at.koopro.wizardsandbeasts.equipment.wardrobe;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * What a robe says about its wearer: the livery it was stitched in and the rank it was issued at
 * (WARDROBE_RENAISSANCE §B3). Stored on the stack as {@code wizards_and_beasts:outfit}.
 *
 * <p>Absent means the cut's default livery at rank 0, which is exactly how every robe saved before
 * the wardrobe existed reads — so no migration is needed. The component is purely visual: no
 * attribute, gate or check reads it ({@code WearableDataTest} pins stats to the item alone).
 *
 * <p>Set only on the server, by an issuing act ({@link Wardrobe#stamp}); the client draws what the
 * stack carries, so every player on screen shows their own robe's rank with no extra sync.
 */
@NullMarked
public record Outfit(Optional<Identifier> livery, int rank, Fit fit) {

    /** Highest rank a cut can show: bone groups {@code rank1..rank3}. */
    public static final int MAX_RANK = 3;

    public static final Outfit DEFAULT = new Outfit(Optional.empty(), 0, Fit.TROUSERS);

    /**
     * How an outfit with legs is cut (Wardrobe 2.0, 2026-11-15): with trousers, or with a skirt and tights (a ball
     * gown's bodice for the dress robes). Anyone may wear either; the wearer switches it by sneaking and using the
     * piece. Absent reads as trousers, so every stack saved before it existed is unchanged.
     */
    public enum Fit implements net.minecraft.util.StringRepresentable {
        TROUSERS("trousers"),
        SKIRT("skirt");

        public static final Codec<Fit> CODEC = net.minecraft.util.StringRepresentable.fromEnum(Fit::values);

        private final String name;

        Fit(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public Fit other() {
            return this == TROUSERS ? SKIRT : TROUSERS;
        }
    }

    public static final Codec<Outfit> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.optionalFieldOf("livery").forGetter(Outfit::livery),
            Codec.intRange(0, MAX_RANK).optionalFieldOf("rank", 0).forGetter(Outfit::rank),
            Fit.CODEC.optionalFieldOf("fit", Fit.TROUSERS).forGetter(Outfit::fit)
    ).apply(i, Outfit::new));

    public static final StreamCodec<ByteBuf, Outfit> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), Outfit::livery,
            ByteBufCodecs.VAR_INT, Outfit::rank,
            ByteBufCodecs.VAR_INT.map(i -> Fit.values()[Math.clamp(i, 0, Fit.values().length - 1)], Fit::ordinal),
            Outfit::fit,
            Outfit::new);

    public Outfit {
        rank = Math.clamp(rank, 0, MAX_RANK);
        fit = fit == null ? Fit.TROUSERS : fit;
    }

    /** A livery and rank at the default fit, trousers. */
    public Outfit(Optional<Identifier> livery, int rank) {
        this(livery, rank, Fit.TROUSERS);
    }

    /** The stack's outfit, or {@link #DEFAULT} when it carries none. */
    public static Outfit of(ItemStack stack) {
        Outfit outfit = stack.get(ModDataComponents.OUTFIT.get());
        return outfit == null ? DEFAULT : outfit;
    }
}
