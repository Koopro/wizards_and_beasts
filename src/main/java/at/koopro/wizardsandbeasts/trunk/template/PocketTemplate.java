package at.koopro.wizardsandbeasts.trunk.template;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Datapack pocket-dimension template ({@code data/<ns>/pocket_templates/*.json}).
 * Consumed today as a radius cap on pocket creation ({@code ExtensionCharmService});
 * {@code instabilityCost} and {@code intentTags} are authored ahead of the fuller
 * Room-of-Requirement mechanic and have no consumer yet.
 */
public record PocketTemplate(
        String templateId,
        String description,
        int maxRadius,
        int allowedBuildRadius,
        int instabilityCost,
        List<String> intentTags) {

    public static final Codec<PocketTemplate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("templateId").forGetter(PocketTemplate::templateId),
            Codec.STRING.optionalFieldOf("description", "").forGetter(PocketTemplate::description),
            Codec.intRange(1, 64).fieldOf("maxRadius").forGetter(PocketTemplate::maxRadius),
            Codec.intRange(1, 128).fieldOf("allowedBuildRadius").forGetter(PocketTemplate::allowedBuildRadius),
            Codec.intRange(0, 100).optionalFieldOf("instabilityCost", 0).forGetter(PocketTemplate::instabilityCost),
            Codec.STRING.listOf().optionalFieldOf("intentTags", List.of()).forGetter(PocketTemplate::intentTags)
    ).apply(instance, PocketTemplate::new));
}
