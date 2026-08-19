package at.koopro.wizardsandbeasts.client.gui.toast;

import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Client entry point for showing a {@link WizardsToast}.
 *
 * <p>Everything server-side goes through {@code PlayerFeedback} and arrives here as a payload; this is
 * also callable directly from client-only code that has no round trip to make.
 */
@NullMarked
public final class WizardsToasts {

    private WizardsToasts() {}

    public static void show(NoticeKind kind, Component title) {
        show(kind, title, null);
    }

    public static void show(NoticeKind kind, Component title, @Nullable Component body) {
        Minecraft mc = Minecraft.getInstance();
        // Font is needed up front to wrap the body, which decides how many slots the toast claims.
        mc.getToastManager().addToast(new WizardsToast(mc.font, kind, title, body));
    }
}
