package at.koopro.wizardsandbeasts.module;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Access state of a {@link Module}. Registration is never gated — content always registers — so these
 * describe reachability only.
 *
 * <ul>
 *   <li>{@link #DISABLED} — off. The feature exists but cannot be reached.</li>
 *   <li>{@link #ENABLED} — on.</li>
 *   <li>{@link #PREVIEW} — on, but flagged as unfinished in player-facing copy.</li>
 *   <li>{@link #COMING_SOON} — a roadmap marker: off, and deliberately <b>not</b> switchable at runtime,
 *       not even by an operator. It says "this is planned", which is a statement about the code, so it
 *       changes in code or in the config seed and nowhere else.</li>
 * </ul>
 *
 * <p>{@code COMING_SOON} is inert at every gate: {@link #grantsAccess()} is false for it exactly as for
 * {@code DISABLED}, so introducing it changes no existing call site's behaviour.
 */
@NullMarked
public enum ModuleState implements StringRepresentable {

    DISABLED("disabled"),
    ENABLED("enabled"),
    PREVIEW("preview"),
    COMING_SOON("coming_soon");

    public static final Codec<ModuleState> CODEC = StringRepresentable.fromEnum(ModuleState::values);

    private final String serializedName;

    ModuleState(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** Whether the feature is reachable. True for {@code ENABLED} and {@code PREVIEW} only. */
    public boolean grantsAccess() {
        return this == ENABLED || this == PREVIEW;
    }

    /**
     * Whether operators may move a module into or out of this state at runtime. False only for
     * {@code COMING_SOON} — a roadmap marker is not an admin toggle.
     */
    public boolean isOperatorSettable() {
        return this != COMING_SOON;
    }

    public Component displayName() {
        return Component.translatable("module.wizards_and_beasts.state." + serializedName);
    }

    @Nullable
    public static ModuleState parse(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        for (ModuleState state : values()) {
            if (state.serializedName.equals(trimmed) || state.name().toLowerCase(Locale.ROOT).equals(trimmed)) {
                return state;
            }
        }
        return null;
    }
}
