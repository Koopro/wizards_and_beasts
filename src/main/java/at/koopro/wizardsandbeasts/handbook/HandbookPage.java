package at.koopro.wizardsandbeasts.handbook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import java.util.Optional;

/**
 * A single page of a {@link HandbookChapter}. Sealed interface with a dispatch codec on the
 * {@code "type"} field, mirroring {@code SkillNodeEffect} / {@code SpellEffectComponent}.
 */
public sealed interface HandbookPage permits
        HandbookPage.Text,
        HandbookPage.Recipe,
        HandbookPage.Image,
        HandbookPage.CrossRef {

    Codec<HandbookPage> CODEC = HandbookPage.Type.CODEC.dispatch(HandbookPage::type, HandbookPage.Type::codec);

    Type type();

    enum Type implements StringRepresentable {
        TEXT("text", Text.CODEC),
        RECIPE("recipe", Recipe.CODEC),
        IMAGE("image", Image.CODEC),
        CROSS_REF("cross_ref", CrossRef.CODEC);

        public static final Codec<Type> CODEC = StringRepresentable.fromValues(Type::values);

        private final String serializedName;
        private final MapCodec<? extends HandbookPage> codec;

        Type(String serializedName, MapCodec<? extends HandbookPage> codec) {
            this.serializedName = serializedName;
            this.codec = codec;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public MapCodec<? extends HandbookPage> codec() {
            return codec;
        }
    }

    /** Plain narrative page. {@code heading} is optional; {@code body} is the wrapped text. */
    record Text(Optional<String> heading, String body) implements HandbookPage {
        public static final MapCodec<Text> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("heading").forGetter(Text::heading),
                Codec.STRING.fieldOf("body").forGetter(Text::body)
        ).apply(instance, Text::new));

        @Override
        public Type type() {
            return Type.TEXT;
        }
    }

    /** Renders a crafting recipe inline, looked up by id from the recipe manager. */
    record Recipe(Identifier recipeId) implements HandbookPage {
        public static final MapCodec<Recipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("recipe_id").forGetter(Recipe::recipeId)
        ).apply(instance, Recipe::new));

        @Override
        public Type type() {
            return Type.RECIPE;
        }
    }

    /** Full-page image with an optional caption. */
    record Image(Identifier texture, Optional<String> caption) implements HandbookPage {
        public static final MapCodec<Image> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("texture").forGetter(Image::texture),
                Codec.STRING.optionalFieldOf("caption").forGetter(Image::caption)
        ).apply(instance, Image::new));

        @Override
        public Type type() {
            return Type.IMAGE;
        }
    }

    /** Styled link-out to another subsystem (currently only {@code "bestiary"}). No inline embed. */
    record CrossRef(String targetType, Identifier entryId) implements HandbookPage {
        public static final MapCodec<CrossRef> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("target_type").forGetter(CrossRef::targetType),
                Identifier.CODEC.fieldOf("entry_id").forGetter(CrossRef::entryId)
        ).apply(instance, CrossRef::new));

        @Override
        public Type type() {
            return Type.CROSS_REF;
        }
    }
}
