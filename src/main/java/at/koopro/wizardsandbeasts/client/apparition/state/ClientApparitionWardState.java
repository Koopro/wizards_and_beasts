package at.koopro.wizardsandbeasts.client.apparition.state;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ClientApparitionWardState {
    public record WardBox(Identifier dimensionId, AABB bounds, String message) {
    }

    private static final List<WardBox> WARDS = new ArrayList<>();

    private ClientApparitionWardState() {
    }

    public static void setAll(List<WardBox> wards) {
        WARDS.clear();
        WARDS.addAll(wards);
    }

    public static boolean isWarded(Identifier dimensionId, Vec3 position) {
        for (WardBox ward : WARDS) {
            if (ward.dimensionId().equals(dimensionId) && ward.bounds().contains(position)) {
                return true;
            }
        }
        return false;
    }

    public static @Nullable String getMessage(Identifier dimensionId, Vec3 position) {
        for (WardBox ward : WARDS) {
            if (ward.dimensionId().equals(dimensionId) && ward.bounds().contains(position)) {
                return ward.message();
            }
        }
        return null;
    }
}
