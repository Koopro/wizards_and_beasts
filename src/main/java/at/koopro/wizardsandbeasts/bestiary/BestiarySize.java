package at.koopro.wizardsandbeasts.bestiary;

import com.mojang.serialization.Codec;

public enum BestiarySize { TINY, SMALL, MEDIUM, LARGE, ENORMOUS;
    public static final Codec<BestiarySize> CODEC = Codec.STRING.xmap(BestiarySize::valueOf, BestiarySize::name);
}
