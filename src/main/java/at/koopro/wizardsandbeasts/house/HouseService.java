package at.koopro.wizardsandbeasts.house;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.corruption.DarkCorruptionService;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.skill.Skill;
import at.koopro.wizardsandbeasts.skill.SkillTrees;
import at.koopro.wizardsandbeasts.skill.vocation.VocationDefinition;
import at.koopro.wizardsandbeasts.skill.vocation.VocationManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Houses in play: who is in which, the Sorting, and the points.
 *
 * <ul>
 *   <li><b>Sorting.</b> An unsorted wizard who puts the Sorting Hat on is offered the Sorting ({@link #onTick}): the
 *       Hat says where it leans, and the wizard lets it decide or pleads against it ({@link #sort}). Once sorted,
 *       always sorted; the house is kept through death.</li>
 *   <li><b>Points.</b> {@link #award} adds to the wizard's house, at most {@link #DAILY_CAP} a day from everyday
 *       work so the Cup cannot be farmed; exams and Ministry trouble go round the cap ({@link #awardUncapped}).</li>
 * </ul>
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class HouseService {

    public static final int DAILY_CAP = 30;
    /** Ticks between Sorting offers to a wizard who keeps the Hat on and says nothing. */
    public static final long OFFER_COOLDOWN = 1200L;

    private static final Map<UUID, Long> LAST_OFFER = new HashMap<>();
    private static final Map<UUID, long[]> TODAY = new HashMap<>();

    private HouseService() {}

    public static @Nullable House houseOf(Player player) {
        return House.byId(player.getData(ModAttachments.HOUSE.get()));
    }

    public static void setHouse(ServerPlayer player, @Nullable House house) {
        player.setData(ModAttachments.HOUSE.get(), house == null ? "" : house.id());
    }

    /** The Hat's lean for this wizard, from what they have studied and become. */
    public static Map<House, Float> lean(ServerPlayer player) {
        Map<String, Integer> trees = new HashMap<>();
        player.getData(ModAttachments.SKILL_DATA.get()).getUnlockedSkills().forEach((node, level) -> {
            Skill skill = SkillTrees.byId(node);
            if (skill != null && level > 0) {
                trees.merge(skill.getTree().getId(), level, Integer::sum);
            }
        });
        VocationDefinition vocation = VocationManager.committed(player);
        String vocationTree = vocation == null ? "" : vocation.tree().getId();
        return SortingRules.lean(player.getUUID(), trees, vocationTree, DarkCorruptionService.get(player));
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || houseOf(player) != null
                || !at.koopro.wizardsandbeasts.artefact.gryffindor.GryffindorService.wearsHat(player)) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_OFFER.get(player.getUUID());
        if (last != null && now - last < OFFER_COOLDOWN) {
            return;
        }
        LAST_OFFER.put(player.getUUID(), now);
        offer(player);
    }

    /** Opens the Sorting for this wizard: the Hat names where it leans and whether it is sure. */
    public static void offer(ServerPlayer player) {
        Map<House, Float> lean = lean(player);
        House[] order = SortingRules.ranked(lean);
        boolean sure = lean.get(order[0]) - lean.get(order[1]) > SortingRules.PLEA_LIMIT;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new SortingOfferS2CPayload(order[0].id(), sure));
        // The Hat murmurs while it considers (audio kit A2): a voice bed under the screen's words.
        player.level().playSound(null, player.blockPosition(),
                at.koopro.wizardsandbeasts.registry.ModSounds.SORTING_HAT_SPEECH.get(), SoundSource.PLAYERS, 0.7f, 1.0f);
    }

    /** The wizard answered the Hat. Does nothing for one already sorted. */
    public static @Nullable House sort(ServerPlayer player, boolean plead) {
        if (houseOf(player) != null) {
            return null;
        }
        House house = SortingRules.decide(lean(player), plead);
        setHouse(player, house);
        LAST_OFFER.remove(player.getUUID());
        ServerLevel level = player.level();
        Component shout = Component.translatable("house.wizards_and_beasts.sorted", player.getDisplayName(),
                Component.translatable(house.nameKey()).withStyle(house.chat(), ChatFormatting.BOLD));
        for (ServerPlayer near : level.getPlayers(p -> p.distanceToSqr(player) < 48 * 48)) {
            near.sendSystemMessage(shout);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.8f);
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(house.primary(), 1.4f),
                player.getX(), player.getEyeY() + 0.6, player.getZ(), 30, 0.4, 0.3, 0.4, 0.0);
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(house.secondary(), 1.2f),
                player.getX(), player.getEyeY() + 0.6, player.getZ(), 20, 0.4, 0.3, 0.4, 0.0);
        return house;
    }

    /** Everyday points for the wizard's house, within the daily cap. Returns what was actually given. */
    public static int award(ServerPlayer player, int points) {
        House house = houseOf(player);
        if (house == null || points <= 0) {
            return 0;
        }
        long day = player.level().getDayTime() / 24000L;
        long[] today = TODAY.computeIfAbsent(player.getUUID(), id -> new long[] {day, 0});
        if (today[0] != day) {
            today[0] = day;
            today[1] = 0;
        }
        int given = (int) Math.min(points, DAILY_CAP - today[1]);
        if (given <= 0) {
            return 0;
        }
        today[1] += given;
        HousePoints.get(player.level().getServer()).add(house, given);
        return given;
    }

    /** Points given or taken outside the daily cap: exam results, Ministry trouble. */
    public static void awardUncapped(MinecraftServer server, @Nullable House house, int delta) {
        if (house != null && delta != 0) {
            HousePoints.get(server).add(house, delta);
        }
    }

    /** The standings as one line, for the Daily Prophet. */
    public static Component standings(MinecraftServer server) {
        HousePoints points = HousePoints.get(server);
        MutableComponent line = Component.translatable("house.wizards_and_beasts.cup").copy();
        boolean first = true;
        for (House house : House.values()) {
            line.append(first ? " " : " · ");
            line.append(Component.translatable(house.nameKey()).withStyle(house.chat()))
                    .append(" " + points.of(house));
            first = false;
        }
        return line;
    }
}
