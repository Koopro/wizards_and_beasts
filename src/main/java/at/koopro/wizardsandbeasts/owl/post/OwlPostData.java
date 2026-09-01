package at.koopro.wizardsandbeasts.owl.post;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parcels in flight, saved with the world.
 *
 * <p>Stored on the overworld's data storage — one queue for the server, not one per dimension —
 * because a parcel is addressed to a player, and a player can walk through a portal while their owl
 * is out. Mirrors {@code TrunkRegistryData}, which anchors to the overworld for the same reason.
 */
@NullMarked
public final class OwlPostData extends SavedData {

    public static final SavedDataType<OwlPostData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_owl_post",
            OwlPostData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    OwlParcel.CODEC.listOf().optionalFieldOf("parcels", List.of())
                            .forGetter(data -> List.copyOf(data.parcels))
            ).apply(instance, OwlPostData::new)));

    private final List<OwlParcel> parcels;

    public OwlPostData() {
        this(List.of());
    }

    private OwlPostData(List<OwlParcel> parcels) {
        this.parcels = new ArrayList<>(parcels);
    }

    public static OwlPostData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<OwlParcel> parcels() {
        return List.copyOf(parcels);
    }

    public void add(OwlParcel parcel) {
        parcels.add(parcel);
        setDirty();
    }

    public void remove(UUID parcelId) {
        if (parcels.removeIf(parcel -> parcel.id().equals(parcelId))) {
            setDirty();
        }
    }

    public void replace(OwlParcel parcel) {
        remove(parcel.id());
        add(parcel);
    }

    /** Parcels addressed to {@code player} that are still in the air. */
    public List<OwlParcel> inboundFor(UUID player) {
        return parcels.stream().filter(parcel -> parcel.currentTarget().equals(player)).toList();
    }

    /** Parcels {@code player} sent that have not been delivered or returned yet. */
    public List<OwlParcel> sentBy(UUID player) {
        return parcels.stream()
                .filter(parcel -> parcel.sender().equals(player) && !parcel.returning())
                .toList();
    }
}
