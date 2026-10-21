package at.koopro.wizardsandbeasts.treasure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * What a recovered artefact is, held on the stack as a data component: where it was found, the curse on it and
 * how deep, whether anyone has read that curse yet, and what Gringotts will pay for it.
 *
 * @param site       the treasure site it came from — the subject of {@code treasure_returned}
 * @param curse      the curse laid on it; empty once broken (or for a clean find)
 * @param layers     layers of curse left; 0 when clean
 * @param identified whether the curse has been read (Revelio, or an appraisal)
 * @param valueKnuts its worth to the bank, before the finder's share
 */
@NullMarked
public record TreasureArtefact(Identifier site, Optional<CurseKind> curse, int layers, boolean identified,
                               long valueKnuts) {

    public static final Codec<TreasureArtefact> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("site").forGetter(TreasureArtefact::site),
            CurseKind.CODEC.optionalFieldOf("curse").forGetter(TreasureArtefact::curse),
            Codec.intRange(0, 16).optionalFieldOf("layers", 0).forGetter(TreasureArtefact::layers),
            Codec.BOOL.optionalFieldOf("identified", false).forGetter(TreasureArtefact::identified),
            Codec.LONG.optionalFieldOf("value", 0L).forGetter(TreasureArtefact::valueKnuts)
    ).apply(i, TreasureArtefact::new));

    public static final StreamCodec<ByteBuf, TreasureArtefact> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public TreasureArtefact {
        if (curse.isEmpty() || layers <= 0) {
            curse = Optional.empty();
            layers = 0;
        }
        valueKnuts = Math.max(0L, valueKnuts);
    }

    public boolean cursed() {
        return curse.isPresent();
    }

    public TreasureArtefact identify() {
        return new TreasureArtefact(site, curse, layers, true, valueKnuts);
    }

    /** One layer lifted; the last one leaves a clean artefact. */
    public TreasureArtefact stripLayer() {
        return new TreasureArtefact(site, curse, layers - 1, true, valueKnuts);
    }

    /** Broken by the goblins for a fee: clean, and known. */
    public TreasureArtefact cleansed() {
        return new TreasureArtefact(site, Optional.empty(), 0, true, valueKnuts);
    }
}
