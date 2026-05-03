package at.koopro.wizardsandbeasts.event;

import at.koopro.wizardsandbeasts.type.WizSubtype;
import at.koopro.wizardsandbeasts.type.WizType;
import at.koopro.wizardsandbeasts.type.profession.ProfessionNode;
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

    /**
     * Fired when a player unlocks a profession node.
     */
    public static class PlayerProfessionUnlockedEvent extends Event {
        private final ServerPlayer player;
        private final ProfessionNode profession;
        private final int remainingPoints;

        public PlayerProfessionUnlockedEvent(ServerPlayer player, ProfessionNode profession, int remainingPoints) {
            this.player = player;
            this.profession = profession;
            this.remainingPoints = remainingPoints;
        }

        public ServerPlayer getPlayer() { return player; }
        public ProfessionNode getProfession() { return profession; }
        public int getRemainingPoints() { return remainingPoints; }
    }

    /**
     * Fired when a player changes active profession.
     */
    public static class PlayerProfessionSelectedEvent extends Event {
        private final ServerPlayer player;
        private final ProfessionNode profession;
        private final int remainingPoints;

        public PlayerProfessionSelectedEvent(ServerPlayer player, ProfessionNode profession, int remainingPoints) {
            this.player = player;
            this.profession = profession;
            this.remainingPoints = remainingPoints;
        }

        public ServerPlayer getPlayer() { return player; }
        public ProfessionNode getProfession() { return profession; }
        public int getRemainingPoints() { return remainingPoints; }
    }

    /**
     * Fired when profession progress is reset/cleared.
     */
    public static class PlayerProfessionResetEvent extends Event {
        private final ServerPlayer player;

        public PlayerProfessionResetEvent(ServerPlayer player) {
            this.player = player;
        }

        public ServerPlayer getPlayer() { return player; }
    }

    private TypeEvents() {}
}
