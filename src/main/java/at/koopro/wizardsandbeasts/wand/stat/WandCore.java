package at.koopro.wizardsandbeasts.wand.stat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

public enum WandCore implements StringRepresentable {
    PHOENIX_FEATHER("phoenix_feather", "Phoenix Feather"),
    DRAGON_HEARTSTRING("dragon_heartstring", "Dragon Heartstring"),
    UNICORN_HAIR("unicorn_hair", "Unicorn Hair"),
    THESTRAL_TAIL("thestral_tail", "Thestral Tail Hair"),
    VEELA_HAIR("veela_hair", "Veela Hair"),
    TROLL_WHISKER("troll_whisker", "Troll Whisker"),
    WAMPUS_CAT_HAIR("wampus_cat_hair", "Wampus Cat Hair"),
    THUNDERBIRD_TAIL_FEATHER("thunderbird_tail_feather", "Thunderbird Tail Feather"),
    ROUGAROU_HAIR("rougarou_hair", "Rougarou Hair"),
    WHITE_RIVER_MONSTER_SPINE("white_river_monster_spine", "White River Monster Spine");

    public static final com.mojang.serialization.Codec<WandCore> CODEC =
            StringRepresentable.fromEnum(WandCore::values);
    public static final StreamCodec<ByteBuf, WandCore> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], WandCore::ordinal);

    private static final Map<String, WandCore> BY_NAME = new HashMap<>();

    static {
        for (WandCore c : values()) BY_NAME.put(c.name, c);
        // The core material item, the datapack definition, every wandmaking recipe and the lang key
        // all spell this core "thestral_tail_hair"; only this enum shortened it. Since wands store
        // the item's id and the cast path looks the core up by that id, a Thestral wand resolved to
        // null and silently contributed no core modifier at all. Alias instead of renaming the
        // constant: getSerializedName() backs the persistent wand_core_legacy codec, so the stored
        // "thestral_tail" form still has to parse.
        BY_NAME.put("thestral_tail_hair", THESTRAL_TAIL);
    }

    private final String name;
    private final String displayName;

    WandCore(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    public static WandCore byName(String name) {
        return BY_NAME.get(name);
    }
}
