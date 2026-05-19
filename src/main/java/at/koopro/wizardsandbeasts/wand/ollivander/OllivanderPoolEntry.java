package at.koopro.wizardsandbeasts.wand.ollivander;

import net.minecraft.resources.Identifier;

public record OllivanderPoolEntry(
        float weight,
        Identifier woodKey,
        Identifier coreKey,
        String flexibility,
        int minimumPlayerLevel) {
}
