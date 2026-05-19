package at.koopro.wizardsandbeasts.spell.gamp;

import at.koopro.wizardsandbeasts.spell.cast.CastContext;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public final class GampViolationEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final GampsLaw.Violation violation;
    private final CastContext ctx;

    public GampViolationEvent(ServerPlayer player, GampsLaw.Violation violation, CastContext ctx) {
        this.player = player;
        this.violation = violation;
        this.ctx = ctx;
    }

    public ServerPlayer player() {
        return player;
    }

    public GampsLaw.Violation violation() {
        return violation;
    }

    public CastContext ctx() {
        return ctx;
    }
}
