package at.koopro.wizardsandbeasts.handbook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * A datapack-driven handbook chapter. Loaded from
 * {@code data/<namespace>/handbook/chapters/<id>.json} by {@link HandbookChapterManager}.
 *
 * @param icon item registry key used as the chapter icon in the index list (optional)
 */
public record HandbookChapter(
        Identifier id,
        String title,
        Optional<Identifier> icon,
        int sortIndex,
        List<HandbookPage> pages) {

    public static final Codec<HandbookChapter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(HandbookChapter::id),
            Codec.STRING.fieldOf("title").forGetter(HandbookChapter::title),
            Identifier.CODEC.optionalFieldOf("icon").forGetter(HandbookChapter::icon),
            Codec.INT.fieldOf("sort_index").forGetter(HandbookChapter::sortIndex),
            HandbookPage.CODEC.listOf().fieldOf("pages").forGetter(HandbookChapter::pages)
    ).apply(instance, HandbookChapter::new));
}
