package at.koopro.wizardsandbeasts.client.ui;

import net.minecraft.client.Minecraft;

/**
 * The Obscurial form/stress-vent predicates that used to live here are gone: those abilities moved onto the
 * ability wheel, where eligibility is decided server-side by {@code ObscurialServerLogic}'s {@code can*}
 * methods via the grant layer. Keeping a client copy would have been a second answer to the same question.
 */
public final class InputPolicy {
    private InputPolicy() {}

    public static boolean canProcessGameplayInput(Minecraft mc) {
        return mc.player != null && mc.screen == null;
    }
}
