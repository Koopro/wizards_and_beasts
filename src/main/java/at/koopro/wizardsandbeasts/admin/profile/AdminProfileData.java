package at.koopro.wizardsandbeasts.admin.profile;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

/**
 * The world's own profiles and snapshots, each stored as its export text (the stable {@link ProfileCodec} schema), so
 * what is saved is exactly what an export would write and a stored entry is re-validated like any import when read.
 */
@NullMarked
public final class AdminProfileData extends SavedData {

    public static final int MAX_PROFILES = 64;
    public static final int MAX_SNAPSHOTS = 32;

    public static final SavedDataType<AdminProfileData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_admin_profiles",
            AdminProfileData::new,
            RecordCodecBuilder.create(i -> i.group(
                    Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("profiles", Map.of()).forGetter(d -> d.profiles),
                    Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("snapshots", Map.of()).forGetter(d -> d.snapshots)
            ).apply(i, AdminProfileData::new)));

    private final Map<String, String> profiles = new TreeMap<>();
    private final Map<String, String> snapshots = new TreeMap<>();

    private AdminProfileData() {
    }

    private AdminProfileData(Map<String, String> profiles, Map<String, String> snapshots) {
        this.profiles.putAll(profiles);
        this.snapshots.putAll(snapshots);
    }

    public static AdminProfileData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private Map<String, String> of(ProfileDocument.Kind kind) {
        return kind == ProfileDocument.Kind.SNAPSHOT ? snapshots : profiles;
    }

    public Map<String, String> all(ProfileDocument.Kind kind) {
        return Map.copyOf(of(kind));
    }

    public @Nullable String text(ProfileDocument.Kind kind, String id) {
        return of(kind).get(id);
    }

    public boolean full(ProfileDocument.Kind kind) {
        return of(kind).size() >= (kind == ProfileDocument.Kind.SNAPSHOT ? MAX_SNAPSHOTS : MAX_PROFILES);
    }

    public void put(ProfileDocument document) {
        of(document.meta().kind()).put(document.meta().id(), ProfileCodec.write(document));
        setDirty();
    }

    public boolean remove(ProfileDocument.Kind kind, String id) {
        boolean removed = of(kind).remove(id) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }
}
