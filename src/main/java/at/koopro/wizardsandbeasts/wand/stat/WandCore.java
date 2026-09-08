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
    THESTRAL_TAIL("thestral_tail", "thestral_tail_hair", "Thestral Tail Hair"),
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
        for (WandCore c : values()) {
            BY_NAME.put(c.name, c);
            BY_NAME.put(c.definitionPath, c);
        }
    }

    private final String name;
    private final String definitionPath;
    private final String displayName;

    WandCore(String name, String displayName) {
        this(name, name, displayName);
    }

    WandCore(String name, String definitionPath, String displayName) {
        this.name = name;
        this.definitionPath = definitionPath;
        this.displayName = displayName;
    }

    /**
     * The persisted form. Backs the {@code wand_core_legacy} data component's codec, so it is frozen:
     * changing one breaks every wand written before the change.
     */
    @Override
    public String getSerializedName() {
        return name;
    }

    /**
     * The id this core is filed under everywhere outside the legacy component — the core material
     * item, its {@code wand_cores} definition, every wandmaking recipe and the lang key.
     *
     * <p>Equal to {@link #getSerializedName()} for nine of the ten. Thestral is the exception: the
     * enum shortened it to {@code thestral_tail} while the rest of the game spells it
     * {@code thestral_tail_hair}. Because {@code WandStatsResolver} looks a core's definition up by
     * this id, a legacy Thestral wand missed the registry entirely and contributed no core modifier
     * at all; the enum fallback that used to absorb that is gone, so this is now the only thing
     * keeping those wands whole.
     */
    public String getDefinitionPath() {
        return definitionPath;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    public static WandCore byName(String name) {
        return BY_NAME.get(name);
    }
}
