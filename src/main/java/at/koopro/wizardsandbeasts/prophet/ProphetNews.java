package at.koopro.wizardsandbeasts.prophet;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * The Daily Prophet's notebook: the last {@link #KEEP} things that happened on the server worth printing.
 *
 * <p>A story is a lang key and its arguments (already plain strings: names, places, a spell's name), stamped
 * with the in-game day it happened. {@link DailyProphet} prints the last two days of them as today's issue.
 */
@NullMarked
public final class ProphetNews extends SavedData {

    public static final int KEEP = 40;

    public record Story(long day, String key, List<String> args) {
        static final Codec<Story> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Story::day),
                Codec.STRING.fieldOf("key").forGetter(Story::key),
                Codec.STRING.listOf().optionalFieldOf("args", List.of()).forGetter(Story::args)
        ).apply(i, Story::new));
    }

    public static final SavedDataType<ProphetNews> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_prophet_news",
            ProphetNews::new,
            RecordCodecBuilder.create(i -> i.group(
                    Story.CODEC.listOf().optionalFieldOf("stories", List.of()).forGetter(n -> n.stories)
            ).apply(i, ProphetNews::new)));

    private final List<Story> stories;

    public ProphetNews() {
        this(List.of());
    }

    private ProphetNews(List<Story> stories) {
        this.stories = new ArrayList<>(stories);
    }

    public static ProphetNews get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void report(long day, String key, String... args) {
        stories.add(new Story(day, key, List.of(args)));
        while (stories.size() > KEEP) {
            stories.remove(0);
        }
        setDirty();
    }

    /** Stories from {@code sinceDay} on, newest first, each told once: the same story twice is printed once. */
    public List<Story> since(long sinceDay) {
        List<Story> out = new ArrayList<>();
        java.util.Set<String> told = new java.util.HashSet<>();
        for (int i = stories.size() - 1; i >= 0; i--) {
            Story s = stories.get(i);
            if (s.day() >= sinceDay && told.add(s.key() + s.args())) {
                out.add(s);
            }
        }
        return out;
    }
}
