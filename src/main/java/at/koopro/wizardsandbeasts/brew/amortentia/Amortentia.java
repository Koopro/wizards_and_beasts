package at.koopro.wizardsandbeasts.brew.amortentia;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.effect.ModEffects;
import at.koopro.wizardsandbeasts.house.House;
import at.koopro.wizardsandbeasts.house.HouseService;
import at.koopro.wizardsandbeasts.polyjuice.PolyjuiceSample;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Amortentia's canon signature (systems audit C1, scope M, 2026-11-25).
 *
 * <p>Canon (HBP ch.9, Slughorn's first lesson): "the most powerful love potion in the world"; it smells different to
 * each person, of what attracts them. Hermione smells freshly mown grass, new parchment and spearmint toothpaste;
 * Harry, treacle tart, the woody smell of a broomstick handle and something flowery he thinks he smelled at the
 * Burrow. It does not create love, only a powerful infatuation or obsession, and it is aimed: Romilda Vane's
 * chocolates were meant for Harry (HBP ch.18), Merope's potion for Tom Riddle (HBP ch.10).
 *
 * <ul>
 *   <li><b>Scent</b>: sniffing a bottle (use while sneaking) gives three lines personal to the sniffer: the smell of
 *   their own house, then two from a pool, chosen by who they are, so a wizard smells the same thing every time.</li>
 *   <li><b>Aimed</b>: a bottle pressed to someone takes one of their hairs, as a Polyjuice bottle does (the same
 *   {@link PolyjuiceSample} on the stack). Its drinker cannot raise a hand to that one person, sees hearts above
 *   them that nobody else sees, and is told of their longing when near. Anyone else may still be struck. An
 *   unaimed bottle keeps the old, honest, indiscriminate effect: the drinker can strike no one.</li>
 * </ul>
 * The binding lives in the drinker's persistent data for as long as the infatuation lasts.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class Amortentia {

    public static final String ID = "wizards_and_beasts:amortentia";
    private static final String KEY = "amortentia.wizards_and_beasts.";
    private static final String TAG = "wizards_and_beasts_amortentia_object";
    /** Canon and canon-feel scents (HBP ch.9 names the first six). */
    static final int POOL = 30;
    private static final double LONGING_RANGE = 16.0;

    private Amortentia() {}

    /** One line of a scent: a translation key, with an argument when it names something. */
    public record Scent(String key) {}

    /**
     * The three things {@code sniffer} smells: their house first (a wizard not yet sorted smells a third pool line
     * instead), then two from the pool, always the same two for the same wizard.
     */
    public static List<Scent> scents(UUID sniffer, @Nullable House house) {
        Random random = new Random(sniffer.getMostSignificantBits() * 31 + sniffer.getLeastSignificantBits());
        List<Scent> out = new ArrayList<>(3);
        if (house != null) {
            out.add(new Scent(KEY + "scent.house." + house.name().toLowerCase(java.util.Locale.ROOT)));
        }
        List<Integer> taken = new ArrayList<>();
        while (out.size() < 3) {
            int pick = random.nextInt(POOL);
            if (!taken.contains(pick)) {
                taken.add(pick);
                out.add(new Scent(KEY + "scent." + pick));
            }
        }
        return out;
    }

    /** Sniffing a bottle: the drinker's own three scents. */
    public static void sniff(ServerPlayer sniffer) {
        sniffer.sendSystemMessage(Component.translatable(KEY + "sniff").withStyle(ChatFormatting.LIGHT_PURPLE));
        for (Scent scent : scents(sniffer.getUUID(), HouseService.houseOf(sniffer))) {
            sniffer.sendSystemMessage(Component.literal("  ").append(Component.translatable(scent.key()))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    /** Whether {@code brewId} is Amortentia. */
    public static boolean is(@Nullable String brewId) {
        return ID.equals(brewId);
    }

    /** Called when a bottle of Amortentia is drunk: an aimed bottle binds the drinker; an unaimed one clears it. */
    public static void onDrunk(Player drinker, ItemStack bottle) {
        CompoundTag data = drinker.getPersistentData();
        PolyjuiceSample sample = PolyjuiceSample.read(bottle);
        if (sample.isPresent() && !sample.id().get().equals(drinker.getUUID())) {
            CompoundTag object = new CompoundTag();
            object.putString("id", sample.id().get().toString());
            object.putString("name", sample.name());
            data.put(TAG, object);
        } else {
            data.remove(TAG);
        }
    }

    /** Whom an infatuated {@code drinker} is besotted with; empty while unaimed or once the potion has worn off. */
    public static Optional<UUID> object(Player drinker) {
        if (!drinker.hasEffect(ModEffects.INFATUATION)) {
            return Optional.empty();
        }
        return drinker.getPersistentData().getCompound(TAG)
                .flatMap(object -> object.getString("id"))
                .flatMap(raw -> {
                    try {
                        return Optional.of(UUID.fromString(raw));
                    } catch (IllegalArgumentException e) {
                        return Optional.empty();
                    }
                });
    }

    /**
     * Whether an infatuated {@code attacker} is held back from striking {@code victim}: an aimed potion only from its
     * object, an unaimed one from every player.
     */
    public static boolean restrains(Player attacker, Player victim) {
        if (!attacker.hasEffect(ModEffects.INFATUATION)) {
            return false;
        }
        Optional<UUID> object = object(attacker);
        return object.isEmpty() || object.get().equals(victim.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer drinker) || drinker.tickCount % 20 != 0) {
            return;
        }
        if (!drinker.hasEffect(ModEffects.INFATUATION)) {
            if (drinker.getPersistentData().contains(TAG)) {
                drinker.getPersistentData().remove(TAG);
            }
            return;
        }
        Optional<UUID> object = object(drinker);
        if (object.isEmpty()) {
            return;
        }
        ServerPlayer beloved = drinker.level().getServer().getPlayerList().getPlayer(object.get());
        if (beloved == null || beloved.level() != drinker.level()
                || beloved.distanceToSqr(drinker) > LONGING_RANGE * LONGING_RANGE) {
            return;
        }
        // Hearts over the one they long for, sent to the drinker alone: nobody else sees a thing.
        ServerLevel level = drinker.level();
        level.sendParticles(drinker, ParticleTypes.HEART, false, false, beloved.getX(), beloved.getEyeY() + 0.6,
                beloved.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
        if (drinker.tickCount % 200 == 0) {
            drinker.displayClientMessage(Component.translatable(KEY + "longing." + (drinker.tickCount / 200 % 3),
                    beloved.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), true);
        }
    }
}
