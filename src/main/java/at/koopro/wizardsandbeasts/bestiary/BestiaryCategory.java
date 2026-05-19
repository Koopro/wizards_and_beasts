package at.koopro.wizardsandbeasts.bestiary;

import com.mojang.serialization.Codec;

public enum BestiaryCategory {
    DRAGON, WINGED_BEAST, EQUINE, LARGE_BEAST, MAGICAL_BIRD,
    SMALL_CREATURE, SERPENTINE, ARACHNID, SPECTRAL,
    CURSED_BEING, AQUATIC, FELINE, MAGICAL_CREATURE;

    public static final Codec<BestiaryCategory> CODEC = Codec.STRING.xmap(BestiaryCategory::valueOf, BestiaryCategory::name);
}
