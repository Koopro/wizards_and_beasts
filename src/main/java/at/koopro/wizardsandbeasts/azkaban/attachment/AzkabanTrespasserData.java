package at.koopro.wizardsandbeasts.azkaban.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record AzkabanTrespasserData(boolean tagged) {

    public static final AzkabanTrespasserData DEFAULT = new AzkabanTrespasserData(false);

    public static final Codec<AzkabanTrespasserData> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(Codec.BOOL.optionalFieldOf("tagged", false)
                    .forGetter(AzkabanTrespasserData::tagged))
                    .apply(inst, AzkabanTrespasserData::new));

    public AzkabanTrespasserData withTagged(boolean value) {
        return new AzkabanTrespasserData(value);
    }
}
