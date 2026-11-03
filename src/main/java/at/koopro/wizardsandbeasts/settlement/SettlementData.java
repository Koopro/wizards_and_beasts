package at.koopro.wizardsandbeasts.settlement;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.settlement.post.PostRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What belongs to a settlement rather than to the world or to a player: who keeps each post, and what is on each
 * shop's shelves. Kept on the overworld for the whole server.
 *
 * <p>The three levels of state are kept apart on purpose:
 * <ul>
 *   <li><b>Global:</b> location files, prices, the price pressure in {@code MarketBook}.</li>
 *   <li><b>Settlement:</b> this class: a person's identity, whether they are here, a site's stock.</li>
 *   <li><b>Player:</b> standing, unlocks, weekly limits and the places they have found, in attachments.</li>
 * </ul>
 *
 * <p>A record is held here and never only on a block entity or an entity, because this is always readable: a post can
 * tell a stale copy of its person from the real one whichever of the two loaded first. Nothing ticks here. A site that
 * is never found costs nothing, and one that is costs a few hundred bytes.
 */
@NullMarked
public final class SettlementData extends SavedData {

    /**
     * One post: who stands at it. {@code name} picks from the role's names, so the same person always returns. Someone
     * away is back at the day time {@code dawn}, or by the game time {@code backAt} whatever the clock did
     * ({@link PostRules#due}). A record saved before {@code dawn} existed reads {@link PostRules#NO_DAWN} and keeps to
     * game time, as it was written to.
     */
    public record Post(String role, PostRules.Status status, Optional<UUID> npc, long backAt, long dawn, int name) {
        static final Codec<PostRules.Status> STATUS = StringRepresentable.fromEnum(StatusName::values)
                .xmap(StatusName::status, StatusName::of);
        static final Codec<Post> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("role").forGetter(Post::role),
                STATUS.optionalFieldOf("status", PostRules.Status.VACANT).forGetter(Post::status),
                UUIDUtil.STRING_CODEC.optionalFieldOf("npc").forGetter(Post::npc),
                Codec.LONG.optionalFieldOf("back_at", 0L).forGetter(Post::backAt),
                Codec.LONG.optionalFieldOf("dawn", PostRules.NO_DAWN).forGetter(Post::dawn),
                Codec.INT.optionalFieldOf("name", 0).forGetter(Post::name)
        ).apply(i, Post::new));

        public static Post vacant(String role, int name) {
            return new Post(role, PostRules.Status.VACANT, Optional.empty(), 0L, PostRules.NO_DAWN, name);
        }

        public Post present(UUID id) {
            return new Post(role, PostRules.Status.PRESENT, Optional.of(id), 0L, PostRules.NO_DAWN, name);
        }

        public Post away(long backAt, long dawn) {
            return new Post(role, PostRules.Status.AWAY, Optional.empty(), backAt, dawn, name);
        }

        /** Whether someone away is due back now; see {@link PostRules#due}. */
        public boolean due(long gameTime, long dayTime) {
            return PostRules.due(gameTime, dayTime, backAt, dawn);
        }
    }

    private enum StatusName implements StringRepresentable {
        VACANT, PRESENT, AWAY;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        PostRules.Status status() {
            return PostRules.Status.valueOf(name());
        }

        static StatusName of(PostRules.Status status) {
            return valueOf(status.name());
        }
    }

    /** What is left of one good at one shop, as of {@code at}. No record means a full shelf. */
    public record Shelf(int left, long at) {
        static final Codec<Shelf> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("left").forGetter(Shelf::left),
                Codec.LONG.fieldOf("at").forGetter(Shelf::at)
        ).apply(i, Shelf::new));
    }

    /** One settlement: {@code seed} decides its name and what its shops carry. */
    public record Site(long seed, Map<String, Post> posts, Map<String, Shelf> shelves) {
        static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("seed").forGetter(Site::seed),
                Codec.unboundedMap(Codec.STRING, Post.CODEC).optionalFieldOf("posts", Map.of()).forGetter(Site::posts),
                Codec.unboundedMap(Codec.STRING, Shelf.CODEC).optionalFieldOf("shelves", Map.of()).forGetter(Site::shelves)
        ).apply(i, (seed, posts, shelves) -> new Site(seed, new HashMap<>(posts), new HashMap<>(shelves))));
    }

    public static final SavedDataType<SettlementData> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_settlements",
            SettlementData::new,
            Codec.unboundedMap(Codec.STRING, Site.CODEC).xmap(SettlementData::new, data -> data.sites));

    private final Map<String, Site> sites;

    SettlementData() {
        this.sites = new HashMap<>();
    }

    private SettlementData(Map<String, Site> stored) {
        this.sites = new HashMap<>(stored);
    }

    public static SettlementData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** A settlement's key: its dimension and the chunk its structure started in. */
    public static String siteKey(ServerLevel level, ChunkPos start) {
        return level.dimension().identifier() + "@" + start.x + "," + start.z;
    }

    private static String postKey(long post) {
        return Long.toString(post);
    }

    /** The site, made on first sight. */
    public Site site(String key, long seed) {
        Site site = sites.get(key);
        if (site == null) {
            site = new Site(seed, new HashMap<>(), new HashMap<>());
            sites.put(key, site);
            setDirty();
        }
        return site;
    }

    /** Drops a site and everything known of it (an operator's reset, a test's clean slate). */
    public void forget(String key) {
        if (sites.remove(key) != null) {
            setDirty();
        }
    }

    public @Nullable Site site(String key) {
        return sites.get(key);
    }

    public @Nullable Post post(PostRef ref) {
        Site site = sites.get(ref.site());
        return site == null ? null : site.posts().get(postKey(ref.post()));
    }

    public void setPost(PostRef ref, Post post) {
        Site site = sites.get(ref.site());
        if (site != null) {
            site.posts().put(postKey(ref.post()), post);
            setDirty();
        }
    }

    public void setShelf(String site, String key, Shelf shelf) {
        Site found = sites.get(site);
        if (found != null) {
            found.shelves().put(key, shelf);
            setDirty();
        }
    }
}
