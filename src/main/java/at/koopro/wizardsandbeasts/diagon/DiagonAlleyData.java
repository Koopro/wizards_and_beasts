package at.koopro.wizardsandbeasts.diagon;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * What the world remembers about Diagon Alley: whether its pocket has been built, and where the Leaky Cauldron's back
 * door stands in the overworld (learnt the first time anyone walks through it), so the way out leads back to the bar.
 * Kept on the overworld, like Azkaban's.
 */
@NullMarked
public final class DiagonAlleyData extends SavedData {

    public static final SavedDataType<DiagonAlleyData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_diagon_alley",
            DiagonAlleyData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("built", 0).forGetter(d -> d.built),
                    BlockPos.CODEC.optionalFieldOf("pub_door").forGetter(d -> Optional.ofNullable(d.pubDoor)),
                    Direction.CODEC.optionalFieldOf("pub_facing").forGetter(d -> Optional.ofNullable(d.pubFacing))
            ).apply(instance, (built, door, facing) -> new DiagonAlleyData(built, door.orElse(null), facing.orElse(null)))));

    /** The layout version the pocket was built at; 0 when it never was. */
    private int built;
    private @Nullable BlockPos pubDoor;
    private @Nullable Direction pubFacing;

    public DiagonAlleyData() {}

    private DiagonAlleyData(int built, @Nullable BlockPos pubDoor, @Nullable Direction pubFacing) {
        this.built = built;
        this.pubDoor = pubDoor;
        this.pubFacing = pubFacing;
    }

    public static DiagonAlleyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public int built() {
        return built;
    }

    public void markBuilt(int version) {
        built = version;
        setDirty();
    }

    public @Nullable BlockPos pubDoor() {
        return pubDoor;
    }

    public @Nullable Direction pubFacing() {
        return pubFacing;
    }

    public void rememberPubDoor(BlockPos door, Direction facing) {
        if (!door.equals(pubDoor) || facing != pubFacing) {
            pubDoor = door.immutable();
            pubFacing = facing;
            setDirty();
        }
    }
}
