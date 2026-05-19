package at.koopro.wizardsandbeasts.event.heritage;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.profession.ProfessionNode;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

public class HeritageEvents {

    /**
     * Fired when a player confirms their heritage / variant selection.
     */
    public static class PlayerHeritageSelectedEvent extends Event {
        private final ServerPlayer player;
        private final Heritage heritage;
        private final HeritageVariant heritageVariant;

        public PlayerHeritageSelectedEvent(ServerPlayer player, Heritage heritage, HeritageVariant heritageVariant) {
            this.player = player;
            this.heritage = heritage;
            this.heritageVariant = heritageVariant;
        }

        public ServerPlayer getPlayer() { return player; }
        public Heritage getHeritage() { return heritage; }
        public HeritageVariant getHeritageVariant() { return heritageVariant; }
    }

    /**
     * Fired when an admin changes a player's heritage.
     */
    public static class PlayerHeritageChangedEvent extends Event {
        private final ServerPlayer player;
        private final Heritage newHeritage;
        private final HeritageVariant newHeritageVariant;

        public PlayerHeritageChangedEvent(ServerPlayer player, Heritage newHeritage, HeritageVariant newHeritageVariant) {
            this.player = player;
            this.newHeritage = newHeritage;
            this.newHeritageVariant = newHeritageVariant;
        }

        public ServerPlayer getPlayer() { return player; }
        public Heritage getNewHeritage() { return newHeritage; }
        public HeritageVariant getNewHeritageVariant() { return newHeritageVariant; }
    }

    /**
     * Fired when a player's heritage is cleared/reset.
     */
    public static class PlayerHeritageResetEvent extends Event {
        private final ServerPlayer player;

        public PlayerHeritageResetEvent(ServerPlayer player) {
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

    private HeritageEvents() {}
}
