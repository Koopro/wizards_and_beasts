package at.koopro.wizardsandbeasts.admin.access;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

import java.util.function.Predicate;

/**
 * The one door an admin payload walks through before it touches anything: the sender is admitted by the
 * owning service's rule, or the request is dropped and the attempt logged in one consistent shape.
 */
@NullMarked
public final class AdminGate {

    private static final Logger LOGGER = LogUtils.getLogger();

    private AdminGate() {
    }

    /** True when {@code rule} admits {@code player}; otherwise logs the dropped {@code what} request. */
    public static boolean admit(ServerPlayer player, Predicate<AdminContext> rule, String what) {
        if (rule.test(AdminContext.of(player))) {
            return true;
        }
        LOGGER.warn("[Admin] Dropped {} request from unauthorised {} ({})", what, player.getName().getString(), player.getUUID());
        return false;
    }
}
