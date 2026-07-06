package at.koopro.wizardsandbeasts.event.form;

import at.koopro.wizardsandbeasts.form.PlayerForm;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import org.jspecify.annotations.Nullable;

/**
 * Events fired by the form system when a player's active form changes.
 */
public class FormEvents {

    /**
     * Fired when a player's form changes (via API, command, or transformation).
     */
    public static class PlayerFormChangedEvent extends Event {
        private final ServerPlayer player;
        @Nullable
        private final PlayerForm oldForm;
        private final PlayerForm newForm;

        public PlayerFormChangedEvent(ServerPlayer player, @Nullable PlayerForm oldForm, PlayerForm newForm) {
            this.player = player;
            this.oldForm = oldForm;
            this.newForm = newForm;
        }

        public ServerPlayer getPlayer() { return player; }
        @Nullable
        public PlayerForm getOldForm() { return oldForm; }
        public PlayerForm getNewForm() { return newForm; }
    }

    /**
     * Fired when a transformation transition begins.
     */
    public static class PlayerFormTransitionStartEvent extends Event {
        private final ServerPlayer player;
        private final String fromFormId;
        private final String toFormId;
        private final int durationTicks;

        public PlayerFormTransitionStartEvent(ServerPlayer player, String fromFormId,
                                               String toFormId, int durationTicks) {
            this.player = player;
            this.fromFormId = fromFormId;
            this.toFormId = toFormId;
            this.durationTicks = durationTicks;
        }

        public ServerPlayer getPlayer() { return player; }
        public String getFromFormId() { return fromFormId; }
        public String getToFormId() { return toFormId; }
        public int getDurationTicks() { return durationTicks; }
    }

    /**
     * Fired when a transformation transition completes.
     */
    public static class PlayerFormTransitionEndEvent extends Event {
        private final ServerPlayer player;
        private final String newFormId;

        public PlayerFormTransitionEndEvent(ServerPlayer player, String newFormId) {
            this.player = player;
            this.newFormId = newFormId;
        }

        public ServerPlayer getPlayer() { return player; }
        public String getNewFormId() { return newFormId; }
    }

    private FormEvents() {}
}
