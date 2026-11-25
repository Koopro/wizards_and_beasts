package at.koopro.wizardsandbeasts.treasure;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.skill.vocation.VocationHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.NullMarked;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Warded treasure chests: the Curse-Breaker's signature (systems audit B5, 2026-11-25; owner chose wards on the
 * existing tomb chests over a new side-room, which keeps its own look gate for later).
 *
 * <p>Canon: Bill Weasley breaks curses on Egyptian tombs for Gringotts (PoA ch.1, GoF ch.5); the tombs were laid
 * with "curses on them", mutant skeletons for the careless, and a curse-breaker's job is to read a curse before
 * lifting it. Here a chest that gives up a cursed artefact is warded with the same curse. Its opener has
 * {@link #WARD_TICKS} to read the ward ({@code Revelio}) and then cast its counter ({@link CurseKind#counterSpell()}).
 * A counter cast blind, the wrong counter, or running out of time fires the curse: a sting that grows with its
 * depth, and its hazard at once.
 *
 * <p>A Curse-Breaker (vocation flag {@link #FLAG}, the explorer) reads a ward at a glance and has twice as long.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class ChestWards {

    /** How long an ordinary wizard has to disarm a ward: ten seconds. */
    public static final int WARD_TICKS = 200;
    /** How long a Curse-Breaker has. */
    public static final int CURSE_BREAKER_TICKS = 400;
    /** Vocation flag: reads wards on sight, and has {@link #CURSE_BREAKER_TICKS}. */
    public static final String FLAG = "curse_breaker";

    private static final String KEY = "treasure.wizards_and_beasts.ward.";

    /** What a cast did to a ward. */
    public enum Step { NONE, READ, DISARMED, FIRED }

    /** A ward waiting on its opener. */
    public record Ward(CurseKind curse, int layers, BlockPos chest, long deadline, boolean read) {
        Ward reading() {
            return new Ward(curse, layers, chest, deadline, true);
        }
    }

    private static final Map<UUID, Ward> WARDS = new ConcurrentHashMap<>();

    private ChestWards() {}

    /** The pure rule: {@code spellId} cast at a ward of {@code curse}, read or not yet. */
    public static Step judge(boolean read, CurseKind curse, String spellId) {
        String bare = CurseKind.bare(spellId);
        if (bare.equals("revelio") || bare.equals("specialis_revelio")) {
            return read ? Step.NONE : Step.READ;
        }
        CurseKind countered = CurseKind.counteredBy(bare);
        if (countered == null) {
            return Step.NONE;
        }
        return read && countered == curse ? Step.DISARMED : Step.FIRED;
    }

    public static Optional<Ward> wardOn(ServerPlayer player) {
        return Optional.ofNullable(WARDS.get(player.getUUID()));
    }

    /** The chest at {@code chest} has given {@code opener} a cursed artefact: its ward rises against them. */
    public static void raise(ServerPlayer opener, TreasureArtefact artefact, BlockPos chest) {
        if (artefact.curse().isEmpty()) {
            return;
        }
        boolean curseBreaker = VocationHelper.hasGrantedAbility(opener, FLAG);
        int ticks = curseBreaker ? CURSE_BREAKER_TICKS : WARD_TICKS;
        CurseKind curse = artefact.curse().get();
        WARDS.put(opener.getUUID(), new Ward(curse, artefact.layers(), chest.immutable(),
                opener.level().getServer().getTickCount() + ticks, curseBreaker));
        opener.level().playSound(null, chest, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.BLOCKS, 0.7f, 0.8f);
        net.minecraft.network.chat.MutableComponent line = curseBreaker
                ? Component.translatable(KEY + "raised_read", curse.displayName(),
                        Component.translatable("spell.wizards_and_beasts." + curse.counterSpell() + ".name"), ticks / 20)
                : Component.translatable(KEY + "raised", ticks / 20);
        opener.sendSystemMessage(line.withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Spell hook, from {@link CurseBreaking#onSpellCast}: a cast at the ward standing against the caster. */
    public static void onSpellCast(ServerPlayer player, String spellId) {
        Ward ward = WARDS.get(player.getUUID());
        if (ward == null) {
            return;
        }
        switch (judge(ward.read(), ward.curse(), spellId)) {
            case READ -> {
                WARDS.put(player.getUUID(), ward.reading());
                PlayerFeedback.actionBar(player, Component.translatable(KEY + "read", ward.curse().displayName(),
                        ward.layers()).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            case DISARMED -> {
                WARDS.remove(player.getUUID());
                player.level().playSound(null, ward.chest(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS,
                        1.0f, 1.2f);
                PlayerFeedback.actionBar(player, Component.translatable(KEY + "disarmed", ward.curse().displayName())
                        .withStyle(ChatFormatting.AQUA));
            }
            case FIRED -> fire(player, ward);
            case NONE -> { }
        }
    }

    /** The ward goes off: the curse's sting and hazard on the one it was raised against. */
    static void fire(ServerPlayer player, Ward ward) {
        WARDS.remove(player.getUUID());
        ServerLevel level = player.level();
        level.playSound(null, ward.chest(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.BLOCKS, 0.9f, 0.6f);
        if (!player.isCreative() && !player.isSpectator()) {
            player.hurtServer(level, level.damageSources().magic(), 1.0f + ward.layers());
            CurseBreaking.hazard(player, ward.curse());
        }
        PlayerFeedback.actionBar(player, Component.translatable(KEY + "fired", ward.curse().displayName())
                .withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (WARDS.isEmpty() || server.getTickCount() % 5 != 0) {
            return;
        }
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, Ward>> it = WARDS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Ward> entry = it.next();
            if (entry.getValue().deadline() > now) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
            } else {
                fire(player, entry.getValue());
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WARDS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WARDS.clear();
    }
}
