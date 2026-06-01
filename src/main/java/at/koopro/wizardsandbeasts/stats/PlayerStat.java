package at.koopro.wizardsandbeasts.stats;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public enum PlayerStat {
    POWER("power", false, false, true),
    PRECISION("precision", true, false, false),
    WILLPOWER("willpower", true, false, false),
    REFLEXES("reflexes", true, false, false),
    KNOWLEDGE("knowledge", false, true, false);

    private final String id;
    private final boolean isTrainable;
    private final boolean isDerived;
    private final boolean isHeritageCapped;

    PlayerStat(String id, boolean isTrainable, boolean isDerived, boolean isHeritageCapped) {
        this.id = id;
        this.isTrainable = isTrainable;
        this.isDerived = isDerived;
        this.isHeritageCapped = isHeritageCapped;
    }

    public String getId() { return id; }
    public boolean isTrainable() { return isTrainable; }
    public boolean isDerived() { return isDerived; }
    public boolean isHeritageCapped() { return isHeritageCapped; }

    public Component displayName() {
        return Component.translatable("stat.wizards_and_beasts." + id);
    }

    @Nullable
    public static PlayerStat fromId(String id) {
        for (PlayerStat stat : values()) {
            if (stat.id.equalsIgnoreCase(id)) return stat;
        }
        return null;
    }
}
