package at.koopro.wizardsandbeasts.bestiary;

import com.mojang.serialization.Codec;

public enum EncounterTrigger { PROXIMITY, KILL, ITEM_USE, LOOT, MANUAL;
    public static final Codec<EncounterTrigger> CODEC = Codec.STRING.xmap(EncounterTrigger::valueOf, EncounterTrigger::name);
}
