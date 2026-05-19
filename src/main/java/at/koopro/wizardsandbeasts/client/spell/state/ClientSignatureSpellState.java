package at.koopro.wizardsandbeasts.client.spell.state;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Client cache for deep signature-spell sync (Patronus form, Imperio, Crucio HUD).
 */
public final class ClientSignatureSpellState {
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private static String patronusForm = "";
    private static boolean imperioControlled;
    private static UUID imperioController = new UUID(0L, 0L);
    /** Victim UUID when local player is the Imperius caster; cleared with nil UUID. */
    private static @Nullable UUID imperioBoundVictim;
    private static float lastIntentMultiplier;
    private static int imperioScreenShakeTicks;

    private ClientSignatureSpellState() {}

    public static void setPatronusForm(String form) {
        patronusForm = form == null ? "" : form;
    }

    public static String getPatronusForm() {
        return patronusForm;
    }

    public static void setImperioControl(boolean controlled, @Nullable UUID controller) {
        imperioControlled = controlled;
        imperioController = controller != null ? controller : new UUID(0L, 0L);
    }

    public static boolean isImperioControlled() {
        return imperioControlled;
    }

    public static UUID getImperioController() {
        return imperioController;
    }

    public static void setImperioBoundVictim(@Nullable UUID victimUuid) {
        if (victimUuid == null || victimUuid.equals(NIL_UUID)) {
            imperioBoundVictim = null;
        } else {
            imperioBoundVictim = victimUuid;
        }
    }

    public static @Nullable UUID getImperioBoundVictim() {
        return imperioBoundVictim;
    }

    public static void setLastIntentMultiplier(float m) {
        lastIntentMultiplier = m;
    }

    public static float getLastIntentMultiplier() {
        return lastIntentMultiplier;
    }

    public static void triggerImperioScreenShake() {
        imperioScreenShakeTicks = 5;
    }

    public static int getAndDecayScreenShakeTicks() {
        int t = imperioScreenShakeTicks;
        if (t > 0) {
            imperioScreenShakeTicks = t - 1;
        }
        return t;
    }
}
