package at.koopro.wizardsandbeasts.trunk;

import java.util.List;

public final class PocketBiomes {

    public static final List<String> SELECTABLE = List.of(
            "minecraft:plains",
            "minecraft:forest",
            "minecraft:birch_forest",
            "minecraft:dark_forest",
            "minecraft:taiga",
            "minecraft:snowy_taiga",
            "minecraft:snowy_plains",
            "minecraft:desert",
            "minecraft:savanna",
            "minecraft:jungle",
            "minecraft:badlands",
            "minecraft:swamp",
            "minecraft:mushroom_fields",
            "minecraft:nether_wastes",
            "minecraft:the_end"
    );

    private PocketBiomes() {}

    public static int indexOf(String biomeKey) {
        int idx = SELECTABLE.indexOf(biomeKey);
        return idx < 0 ? 0 : idx;
    }

    public static String displayName(String biomeKey) {
        String[] parts = biomeKey.split(":");
        String name = parts.length > 1 ? parts[1] : biomeKey;
        String[] words = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
