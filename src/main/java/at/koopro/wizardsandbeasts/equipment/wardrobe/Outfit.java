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
public record Outfit(Optional<Identifier> livery, int rank) {

    /** Highest rank a cut can show: bone groups {@code rank1..rank3}. */
    public static final int MAX_RANK = 3;

    public static final Outfit DEFAULT = new Outfit(Optional.empty(), 0);

    public static final Codec<Outfit> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.optionalFieldOf("livery").forGetter(Outfit::livery),
            Codec.intRange(0, MAX_RANK).optionalFieldOf("rank", 0).forGetter(Outfit::rank)
    ).apply(i, Outfit::new));

    public static final StreamCodec<ByteBuf, Outfit> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), Outfit::livery,
            ByteBufCodecs.VAR_INT, Outfit::rank,
            Outfit::new);

    public Outfit {
        rank = Math.clamp(rank, 0, MAX_RANK);
    }

    /** The stack's outfit, or {@link #DEFAULT} when it carries none. */
    public static Outfit of(ItemStack stack) {
        Outfit outfit = stack.get(ModDataComponents.OUTFIT.get());
        return outfit == null ? DEFAULT : outfit;
    }
}
