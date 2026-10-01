package at.koopro.wizardsandbeasts.admin.profile;

import at.koopro.wizardsandbeasts.admin.config.ApplyMode;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * What a profile is validated against: the server's settings as they stand, seen through one actor's authority. The
 * live implementation reads the admin registry ({@link LiveProfileTarget}); tests use a fake, so the validator itself
 * needs no server.
 */
@NullMarked
public interface ProfileTarget {

    /** One setting as it stands. {@code module} is set for a module switch. */
    record Facts(boolean clientOnly, boolean authorised, String current, String defaultText, ApplyMode applyMode,
                 @Nullable Module module) {}

    /** The canonical text of a candidate value, or the reason it is not one ({@code invalid_value}, {@code out_of_range}). */
    record Check(@Nullable String canonical, @Nullable String problem) {
        public static Check ok(String canonical) {
            return new Check(canonical, null);
        }

        public static Check refused(String problem) {
            return new Check(null, problem);
        }
    }

    /** Every server-owned setting a profile covers (client preferences excluded), in a stable order. */
    List<Identifier> serverSettings();

    /** Null when no such setting exists. */
    @Nullable Facts facts(Identifier id);

    Check check(Identifier id, String text);

    Map<Module, ModuleState> moduleStates();
}
