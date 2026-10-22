package at.koopro.wizardsandbeasts.artefact.gryffindor;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The world's one Sword of Gryffindor: whether it is out in the world, and which stack it is.
 *
 * <p>Set when the Sorting Hat presents it; cleared when that very stack is destroyed (falls into the void, is blown
 * up, despawns), so the Hat can present it again to the next one in need. A sword made in creative mode or by a
 * command carries no id and is not this sword. A sword lost somewhere this record cannot see -- cleared from an
 * inventory by a command -- keeps the record set; that is the operator's to put right.
 */
@NullMarked
public final class GryffindorSword extends SavedData {

    public static final Codec<GryffindorSword> CODEC = UUIDUtil.CODEC.optionalFieldOf("present")
            .codec().xmap(GryffindorSword::new, s -> Optional.ofNullable(s.present));

    public static final SavedDataType<GryffindorSword> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_gryffindor_sword", GryffindorSword::new, CODEC);

    private @Nullable UUID present;

    public GryffindorSword() {}

    private GryffindorSword(Optional<UUID> present) {
        this.present = present.orElse(null);
    }

    public static GryffindorSword get(ServerLevel anyLevel) {
        return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The id of the sword now in the world, or null when the Hat may present it. */
    public @Nullable UUID present() {
        return present;
    }

    void presented(UUID id) {
        present = id;
        setDirty();
    }

    /** Forgets the sword outright, so the Hat may present it again: an operator's (or a test's) reset. */
    public void forget() {
        if (present != null) {
            present = null;
            setDirty();
        }
    }

    /** The stack with {@code id} is gone. Returns whether that was the world's sword. */
    boolean lost(UUID id) {
        if (!id.equals(present)) {
            return false;
        }
        present = null;
        setDirty();
        return true;
    }
}
