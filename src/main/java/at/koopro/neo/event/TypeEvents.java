package at.koopro.neo.event;

import at.koopro.neo.type.WizSubtype;
import at.koopro.neo.type.WizType;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public class TypeEvents {

    /**
     * Fired when a player confirms their type/subtype selection.
     */
    public static class PlayerTypeSelectedEvent extends Event {
        private final ServerPlayer player;
        private final WizType type;
        private final WizSubtype subtype;

        public PlayerTypeSelectedEvent(ServerPlayer player, WizType type, WizSubtype subtype) {
            this.player = player;
            this.type = type;
            this.subtype = subtype;
        }

        public ServerPlayer getPlayer() { return player; }
        public WizType getType() { return type; }
        public WizSubtype getSubtype() { return subtype; }
    }

    /**
     * Fired when an admin changes a player's type.
     */
    public static class PlayerTypeChangedEvent extends Event {
        private final ServerPlayer player;
        private final WizType newType;
        private final WizSubtype newSubtype;

        public PlayerTypeChangedEvent(ServerPlayer player, WizType newType, WizSubtype newSubtype) {
            this.player = player;
            this.newType = newType;
            this.newSubtype = newSubtype;
        }

        public ServerPlayer getPlayer() { return player; }
        public WizType getNewType() { return newType; }
        public WizSubtype getNewSubtype() { return newSubtype; }
    }

    /**
     * Fired when a player's type is cleared/reset.
     */
    public static class PlayerTypeResetEvent extends Event {
        private final ServerPlayer player;

        public PlayerTypeResetEvent(ServerPlayer player) {
            this.player = player;
        }

        public ServerPlayer getPlayer() { return player; }
    }

    private TypeEvents() {}
}
