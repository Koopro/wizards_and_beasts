package at.koopro.wizardsandbeasts.entity.azkaban;

import at.koopro.wizardsandbeasts.entity.spell.PatronusEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;

/**
 * What a Dementor does to the living around it, by distance. Pure rules; {@link DementorEntity} applies them.
 *
 * <p>Canon (Prisoner of Azkaban): the cold arrives before the Dementor does — frost, the lights dimming, a chill that
 * reaches the heart — and it deepens the closer one comes, until the victim relives their worst memories. So the
 * aura is a ladder of distance bands, not a flat debuff, and the worst of it is only for those within arm's reach:
 *
 * <table>
 *   <tr><th>distance</th><th>band</th><th>chill amplifier</th><th>what it means</th></tr>
 *   <tr><td>≤ {@value #SENSE_RADIUS}</td><td>sensed</td><td>none</td><td>it feels you; its breath, frost, the voices</td></tr>
 *   <tr><td>≤ {@value #COLD_RADIUS}</td><td>cold</td><td>0</td><td>the cold: no harm, the edges of sight grey</td></tr>
 *   <tr><td>≤ {@value #FEAR_RADIUS}</td><td>fear</td><td>1</td><td>dread: slowed, hungrier</td></tr>
 *   <tr><td>≤ {@value #DRAIN_RADIUS}</td><td>drain</td><td>2</td><td>it feeds: weak, slow, a little hurt</td></tr>
 * </table>
 *
 * <p>Several Dementors close together deepen it by one ({@link #SWARM_SIZE}), capped at 3 — the lake shore. The
 * effect lasts {@link #CHILL_TICKS} and is refreshed while one stays near, so walking away ends it within seconds.
 */
public final class DementorAura {

    public static final double SENSE_RADIUS = 24.0;
    public static final double COLD_RADIUS = 16.0;
    public static final double FEAR_RADIUS = 8.0;
    public static final double DRAIN_RADIUS = 4.0;

    /** How many Dementors within {@link #COLD_RADIUS} of one victim make a swarm. */
    public static final int SWARM_SIZE = 3;
    public static final int MAX_AMPLIFIER = 3;
    /** Refreshed every second; three seconds of grace once the last Dementor is out of range. */
    public static final int CHILL_TICKS = 60;

    /** No chill at this distance. */
    public static final int NONE = -1;

    private DementorAura() {}

    /** The band a victim at {@code distance} from the nearest Dementor is in: {@link #NONE}, or 0..2. */
    public static int band(double distance) {
        if (distance <= DRAIN_RADIUS) return 2;
        if (distance <= FEAR_RADIUS) return 1;
        if (distance <= COLD_RADIUS) return 0;
        return NONE;
    }

    /**
     * The chill amplifier for a victim {@code nearest} blocks from the closest Dementor with {@code nearby} Dementors
     * within {@link #COLD_RADIUS}; {@link #NONE} for no chill.
     */
    public static int amplifier(double nearest, int nearby) {
        int band = band(nearest);
        if (band == NONE) return NONE;
        return Math.min(MAX_AMPLIFIER, band + (nearby >= SWARM_SIZE ? 1 : 0));
    }

    /**
     * Whether a Dementor can feed on this creature at all: anything alive that feels — players (not creative or
     * spectating), people, animals. Not another Dementor, not a Patronus, not the undead (nothing left to take), not an
     * armour stand. Whether a Patronus wards it is a separate question ({@code PatronusDetection}).
     */
    public static boolean canFeedOn(LivingEntity entity) {
        if (!entity.isAlive() || entity instanceof DementorEntity || entity instanceof PatronusEntity
                || entity instanceof ArmorStand || entity.isInvertedHealAndHarm()) {
            return false;
        }
        if (entity instanceof Player player) {
            // The Resurrection Stone's shades: "the dementors' chill did not overcome him" (Deathly Hallows ch. 34).
            return !player.isCreative() && !player.isSpectator()
                    && !at.koopro.wizardsandbeasts.item.hallow.ShadesOfTheDead.walkWith(player);
        }
        return true;
    }

    /** Whether this creature has a soul a Kiss can take: a player or a person, never an animal. */
    public static boolean hasSoul(LivingEntity entity) {
        return entity instanceof Player || entity instanceof Npc || entity instanceof Raider;
    }

    /**
     * Whom a Dementor turns to first: a player over a person over an animal, the nearest within each. Lower is
     * preferred.
     */
    public static int preference(LivingEntity entity) {
        if (entity instanceof Player) return 0;
        if (hasSoul(entity)) return 1;
        return 2;
    }
}
