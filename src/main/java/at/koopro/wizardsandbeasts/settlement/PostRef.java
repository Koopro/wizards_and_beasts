package at.koopro.wizardsandbeasts.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Carried by anyone who keeps a settlement post, whatever their entity type: which site, and which post in it.
 *
 * @param site {@link SettlementData#siteKey the site}
 * @param post the post's block position, as a long
 */
@NullMarked
public record PostRef(String site, long post) {

    public static final PostRef NONE = new PostRef("", 0L);

    public static final Codec<PostRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("site").forGetter(PostRef::site),
            Codec.LONG.fieldOf("post").forGetter(PostRef::post)
    ).apply(i, PostRef::new));
}
