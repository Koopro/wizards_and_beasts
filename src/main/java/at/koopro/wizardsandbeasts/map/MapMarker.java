package at.koopro.wizardsandbeasts.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A named place on the map: a discovered structure, a wizarding landmark, a Floo hearth, a grave,
 * or a waypoint someone pushed a pin into.
 *
 * <p>What a marker deliberately does <em>not</em> carry is how to draw it. {@link #type} is an id;
 * the icon, tint and legend grouping for that id live in {@code assets/.../map_marker_style/} on
 * the client. The server decides what is there, the resource pack decides what it looks like, and
 * the network ships neither icons nor colours.
 *
 * <p>{@link #label} is a raw string rather than a {@code Component} because the two kinds of label
 * this holds want opposite treatment: a discovered landmark's name is a translation key that must
 * render in each viewer's own language, while a waypoint's name is text a player typed and must
 * render back exactly as typed. {@link #displayLabel} resolves the distinction at draw time via the
 * {@code translatable} flag, which is set once at creation by whoever knew which kind it was.
 */
public record MapMarker(
        UUID id,
        Identifier type,
        Identifier dimension,
        int x,
        int z,
        String label,
        boolean translatable,
        MapMarkerSource source,
        /** Who may see and edit this. Empty means everyone holding the map. */
        Optional<UUID> owner,
        /** Hidden markers stay in the list and off the parchment — the player's own choice. */
        boolean hidden,
        /** Game time this was first recorded, which drives the brief reveal shimmer. */
        long discoveredAt
) {

    /** Longest label the server will store. Long enough for a sentence, short enough not to be one. */
    public static final int MAX_LABEL_LENGTH = 48;

    public static final Codec<MapMarker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(MapMarker::id),
            Identifier.CODEC.fieldOf("type").forGetter(MapMarker::type),
            Identifier.CODEC.fieldOf("dimension").forGetter(MapMarker::dimension),
            Codec.INT.fieldOf("x").forGetter(MapMarker::x),
            Codec.INT.fieldOf("z").forGetter(MapMarker::z),
            Codec.STRING.optionalFieldOf("label", "").forGetter(MapMarker::label),
            Codec.BOOL.optionalFieldOf("translatable", false).forGetter(MapMarker::translatable),
            MapMarkerSource.CODEC.optionalFieldOf("source", MapMarkerSource.DISCOVERY)
                    .forGetter(MapMarker::source),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(MapMarker::owner),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(MapMarker::hidden),
            Codec.LONG.optionalFieldOf("discovered_at", 0L).forGetter(MapMarker::discoveredAt)
    ).apply(instance, MapMarker::new));

    public MapMarker {
        if (label.length() > MAX_LABEL_LENGTH) {
            label = label.substring(0, MAX_LABEL_LENGTH);
        }
    }

    /** A landmark the map found on its own. */
    public static MapMarker discovered(Identifier type, Identifier dimension, BlockPos pos,
                                       String translationKey, long gameTime) {
        return new MapMarker(UUID.randomUUID(), type, dimension, pos.getX(), pos.getZ(),
                translationKey, true, MapMarkerSource.DISCOVERY, Optional.empty(), false, gameTime);
    }

    /** A pin a player pushed in. */
    public static MapMarker waypoint(Identifier type, Identifier dimension, BlockPos pos,
                                     String name, @Nullable UUID owner, long gameTime) {
        return new MapMarker(UUID.randomUUID(), type, dimension, pos.getX(), pos.getZ(),
                name, false, MapMarkerSource.WAYPOINT, Optional.ofNullable(owner), false, gameTime);
    }

    /** Where the holder last died. There is only ever one of these per player. */
    public static MapMarker death(Identifier dimension, BlockPos pos, UUID owner, long gameTime) {
        return new MapMarker(UUID.randomUUID(), MapMarkerTypes.DEATH, dimension,
                pos.getX(), pos.getZ(), "", false, MapMarkerSource.DEATH,
                Optional.of(owner), false, gameTime);
    }

    /** Same place, same kind — what deduplicates a re-discovery of a landmark already on the map. */
    public boolean sameSpot(Identifier otherType, Identifier otherDimension, int otherX, int otherZ,
                           int tolerance) {
        return type.equals(otherType)
                && dimension.equals(otherDimension)
                && Math.abs(x - otherX) <= tolerance
                && Math.abs(z - otherZ) <= tolerance;
    }

    /** True when {@code viewer} may see this marker at all. */
    public boolean visibleTo(UUID viewer) {
        return owner.isEmpty() || owner.get().equals(viewer);
    }

    public MapMarker withLabel(String newLabel) {
        return new MapMarker(id, type, dimension, x, z, newLabel, false, source, owner, hidden,
                discoveredAt);
    }

    public MapMarker withType(Identifier newType) {
        return new MapMarker(id, newType, dimension, x, z, label, translatable, source, owner,
                hidden, discoveredAt);
    }

    public MapMarker withHidden(boolean newHidden) {
        return new MapMarker(id, type, dimension, x, z, label, translatable, source, owner,
                newHidden, discoveredAt);
    }
}
