package at.koopro.wizardsandbeasts.admin.access;

import at.koopro.wizardsandbeasts.command.AdminAccess;
import net.minecraft.commands.CommandSourceStack;
import org.jspecify.annotations.NullMarked;

import java.util.EnumSet;
import java.util.Set;

/**
 * Maps a command source to the {@link AdminCapability capabilities} it holds.
 *
 * <p>Phase 1 is all-or-nothing on top of {@link AdminAccess}: the allow-list when one is configured,
 * operator permission when it is not, and the console always. That rule is not restated here — this class
 * only asks it. A role system replaces {@link #capabilitiesOf} (for example with one PermissionAPI node per
 * {@link AdminCapability#node()}) and every caller keeps working unchanged.
 */
@NullMarked
public final class AdminPolicy {

    private AdminPolicy() {}

    public static Set<AdminCapability> capabilitiesOf(CommandSourceStack source) {
        return forVerdict(AdminAccess.allows(source));
    }

    /** The Phase-1 mapping from the single admin verdict to a capability set. Pure, for tests. */
    public static Set<AdminCapability> forVerdict(boolean isAdmin) {
        return isAdmin ? EnumSet.allOf(AdminCapability.class) : EnumSet.noneOf(AdminCapability.class);
    }
}
