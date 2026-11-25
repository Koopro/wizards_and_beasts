package at.koopro.wizardsandbeasts.duel;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.ministry.trace.LegalClass;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCasework;
import at.koopro.wizardsandbeasts.skill.vocation.VocationHelper;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Formal duels: the duelist's signature (systems audit B5, Auror, 2026-11-25).
 *
 * <p>A duelist (vocation flag {@link FormalDuelRules#CHALLENGE_FLAG}) challenges another wizard; once accepted the
 * two bow and a count of three is called (CoS ch.11). Inside the duel:
 * <ul>
 *   <li>the opponent's blows never take the last of a duellist's health: a duel is fought to the fall, not the
 *   death;</li>
 *   <li>nothing either casts is a Ministry incident unless it is Dark ({@link #shieldsFromTrace}): sanctioned
 *   duels are no crime, Dark magic in one still is;</li>
 *   <li>the one who goes down (stunned, petrified or badly hurt) loses, and the winner has defeated them: their
 *   held wands' allegiance is contested as a stun contests it. A stun already did that the moment it landed, so a
 *   stunned loser is not counted twice.</li>
 * </ul>
 * Duels live only in memory: a restart, a logout, walking apart or five minutes without a winner ends one.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class FormalDuels {

    private static final String KEY = "duel.wizards_and_beasts.";

    /** One duel; {@code accepted} is the server tick it was accepted on. */
    public static final class Duel {
        final UUID first;
        final UUID second;
        final long accepted;

        Duel(UUID first, UUID second, long accepted) {
            this.first = first;
            this.second = second;
            this.accepted = accepted;
        }

        public UUID opponentOf(UUID player) {
            return player.equals(first) ? second : first;
        }
    }

    private record Challenge(UUID challenger, long expires) {}

    /** Why a challenge was refused, or OK. */
    public enum ChallengeResult { OK, NOT_DUELIST, SELF, TOO_FAR, BUSY }

    private static final Map<UUID, Duel> DUELS = new ConcurrentHashMap<>();
    /** Keyed by the one challenged. */
    private static final Map<UUID, Challenge> CHALLENGES = new ConcurrentHashMap<>();

    private FormalDuels() {}

    // ── reading ──

    public static boolean inDuel(ServerPlayer player) {
        return DUELS.containsKey(player.getUUID());
    }

    public static Optional<UUID> opponent(ServerPlayer player) {
        return Optional.ofNullable(DUELS.get(player.getUUID())).map(d -> d.opponentOf(player.getUUID()));
    }

    /** Whether a cast of this class by {@code caster} is kept from the Ministry: in a duel, and not Dark. */
    public static boolean shieldsFromTrace(ServerPlayer caster, LegalClass legalClass) {
        return inDuel(caster) && !legalClass.leavesResidue();
    }

    // ── the challenge ──

    public static ChallengeResult challenge(ServerPlayer challenger, ServerPlayer target) {
        if (!VocationHelper.hasGrantedAbility(challenger, FormalDuelRules.CHALLENGE_FLAG)) {
            return ChallengeResult.NOT_DUELIST;
        }
        if (challenger.getUUID().equals(target.getUUID())) {
            return ChallengeResult.SELF;
        }
        if (!inReach(challenger, target)) {
            return ChallengeResult.TOO_FAR;
        }
        if (inDuel(challenger) || inDuel(target)) {
            return ChallengeResult.BUSY;
        }
        long now = challenger.level().getServer().getTickCount();
        CHALLENGES.put(target.getUUID(), new Challenge(challenger.getUUID(), now + FormalDuelRules.CHALLENGE_TICKS));
        target.sendSystemMessage(Component.translatable(KEY + "challenged", challenger.getDisplayName())
                .withStyle(ChatFormatting.GOLD));
        challenger.sendSystemMessage(Component.translatable(KEY + "challenge_sent", target.getDisplayName())
                .withStyle(ChatFormatting.GRAY));
        return ChallengeResult.OK;
    }

    /** The challenged wizard accepts the challenge standing against them; false when there is none to accept. */
    public static boolean accept(ServerPlayer target) {
        Challenge challenge = CHALLENGES.remove(target.getUUID());
        MinecraftServer server = target.level().getServer();
        if (challenge == null || challenge.expires() < server.getTickCount()) {
            return false;
        }
        ServerPlayer challenger = server.getPlayerList().getPlayer(challenge.challenger());
        if (challenger == null || inDuel(challenger) || inDuel(target) || !inReach(challenger, target)) {
            return false;
        }
        Duel duel = new Duel(challenger.getUUID(), target.getUUID(), server.getTickCount());
        DUELS.put(duel.first, duel);
        DUELS.put(duel.second, duel);
        for (ServerPlayer duellist : List.of(challenger, target)) {
            duellist.sendSystemMessage(Component.translatable(KEY + "bow").withStyle(ChatFormatting.GOLD));
        }
        return true;
    }

    /** Declines the standing challenge; false when there was none. */
    public static boolean decline(ServerPlayer target) {
        Challenge challenge = CHALLENGES.remove(target.getUUID());
        if (challenge == null) {
            return false;
        }
        ServerPlayer challenger = target.level().getServer().getPlayerList().getPlayer(challenge.challenger());
        if (challenger != null) {
            challenger.sendSystemMessage(Component.translatable(KEY + "declined", target.getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    /** Yields the duel: the opponent wins it. False when not in one. */
    public static boolean yieldDuel(ServerPlayer player) {
        Duel duel = DUELS.get(player.getUUID());
        if (duel == null) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        end(server, duel, FormalDuelRules.End.YIELDED,
                server.getPlayerList().getPlayer(duel.opponentOf(player.getUUID())), player);
        return true;
    }

    private static boolean inReach(ServerPlayer a, ServerPlayer b) {
        double range = FormalDuelRules.CHALLENGE_RANGE;
        return a.level() == b.level() && a.distanceToSqr(b) <= range * range;
    }

    // ── the duel ──

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        CHALLENGES.values().removeIf(c -> c.expires() < now);
        if (DUELS.isEmpty()) {
            return;
        }
        List<Duel> duels = new ArrayList<>();
        for (Duel duel : DUELS.values()) {
            if (!duels.contains(duel)) {
                duels.add(duel);
            }
        }
        for (Duel duel : duels) {
            tick(server, duel, now);
        }
    }

    private static void tick(MinecraftServer server, Duel duel, long now) {
        ServerPlayer first = server.getPlayerList().getPlayer(duel.first);
        ServerPlayer second = server.getPlayerList().getPlayer(duel.second);
        long elapsed = now - duel.accepted;
        if (first == null || second == null) {
            end(server, duel, FormalDuelRules.End.ABANDONED, null, null);
            return;
        }
        int count = FormalDuelRules.count(elapsed);
        if (count > 0) {
            for (ServerPlayer duellist : List.of(first, second)) {
                PlayerFeedback.actionBar(duellist, Component.translatable(KEY + "count." + count)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
        } else if (elapsed == FormalDuelRules.COUNTDOWN_TICKS) {
            for (ServerPlayer duellist : List.of(first, second)) {
                PlayerFeedback.actionBar(duellist, Component.translatable(KEY + "begin")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
            first.level().playSound(null, first.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(),
                    SoundSource.PLAYERS, 0.8f, 1.2f);
        }
        boolean firstDown = MinistryCasework.downed(first);
        boolean secondDown = MinistryCasework.downed(second);
        FormalDuelRules.End end = FormalDuelRules.check(elapsed, first.isAlive() && second.isAlive(),
                first.level() == second.level(), first.distanceToSqr(second), firstDown, secondDown);
        switch (end) {
            case NONE -> { }
            case WON -> {
                ServerPlayer loser = firstDown ? first : second;
                end(server, duel, end, loser == first ? second : first, loser);
            }
            default -> end(server, duel, end, null, null);
        }
    }

    private static void end(MinecraftServer server, Duel duel, FormalDuelRules.End end, @Nullable ServerPlayer winner,
                            @Nullable ServerPlayer loser) {
        DUELS.remove(duel.first, duel);
        DUELS.remove(duel.second, duel);
        String key = KEY + "end." + end.name().toLowerCase(Locale.ROOT);
        Component line = winner != null && loser != null
                ? Component.translatable(key, winner.getDisplayName(), loser.getDisplayName())
                : Component.translatable(key);
        for (UUID id : List.of(duel.first, duel.second)) {
            ServerPlayer duellist = server.getPlayerList().getPlayer(id);
            if (duellist != null) {
                duellist.sendSystemMessage(line.copy().withStyle(ChatFormatting.GOLD));
            }
        }
        // The winner has defeated the loser. A stun already contested the wands as it landed; do not count it twice.
        if (winner != null && loser != null && !loser.hasEffect(ModEffects.STUPEFY)) {
            WandAllegianceService.onDefeat(loser, winner, WandAllegianceService.DefeatKind.STUN,
                    WandAllegianceService.heldWands(loser));
        }
    }

    /** Before the count of three nobody may strike; after it, never the last of the opponent's health. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        Duel duel = DUELS.get(victim.getUUID());
        if (duel == null || !duel.opponentOf(victim.getUUID()).equals(attacker.getUUID())) {
            return;
        }
        long elapsed = victim.level().getServer().getTickCount() - duel.accepted;
        if (!FormalDuelRules.started(elapsed)) {
            event.setCanceled(true);
            return;
        }
        event.setAmount(FormalDuelRules.cap(event.getAmount(), victim.getHealth()));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHALLENGES.remove(event.getEntity().getUUID());
        Duel duel = DUELS.get(event.getEntity().getUUID());
        if (duel != null && event.getEntity() instanceof ServerPlayer player) {
            end(player.level().getServer(), duel, FormalDuelRules.End.ABANDONED, null, null);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DUELS.clear();
        CHALLENGES.clear();
    }
}
