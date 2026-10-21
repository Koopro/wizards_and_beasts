package at.koopro.wizardsandbeasts.location.apothecary;

import at.koopro.wizardsandbeasts.creature.bond.BondableBeast;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.location.LocationService;
import at.koopro.wizardsandbeasts.location.LocationServices;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * What the apothecary does besides trade.
 *
 * <ul>
 *   <li><b>creature_treatment</b> — every creature bonded to the customer and brought to the counter is dressed,
 *       dosed and set right: health restored and every lingering effect drawn out. Priced per creature: the
 *       offer's fee for the visit plus {@link #KNUTS_PER_HEALTH} for each point of health restored. Dittany does
 *       part of this for free in the field; this is the full cure, for the wizard who would rather pay than
 *       wait.</li>
 * </ul>
 */
@NullMarked
public final class ApothecaryServices {

    /** Knuts per point of health restored, on top of the visit fee. */
    public static final long KNUTS_PER_HEALTH = 3L;
    /** How close a creature must be to the counter (blocks from the customer). */
    public static final double REACH = 10.0;

    private ApothecaryServices() {}

    public static void register(LocationServices.Sink sink) {
        sink.register("creature_treatment", new Treatment());
    }

    /** The customer's bonded creatures at the counter that need anything. */
    static List<Mob> patients(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(REACH);
        return player.level().getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive()
                && mob instanceof BondableBeast beast
                && player.getUUID().equals(beast.bondState().ownerUUID())
                && (mob.getHealth() < mob.getMaxHealth() || !mob.getActiveEffects().isEmpty()));
    }

    /** The price of treating {@code patients} at {@code visitFee} each. */
    static long priceOf(List<Mob> patients, long visitFee) {
        long total = 0L;
        for (Mob mob : patients) {
            long missing = (long) Math.ceil(Math.max(0.0f, mob.getMaxHealth() - mob.getHealth()));
            total += visitFee + missing * KNUTS_PER_HEALTH;
        }
        return CurrencyHelper.roundUpToSickle(total);
    }

    static final class Treatment implements LocationService {
        @Override
        public Quote quote(ServerPlayer player, Use use) {
            List<Mob> patients = patients(player);
            if (patients.isEmpty()) {
                return Quote.no(Component.translatable("location.wizards_and_beasts.apothecary.no_patients"));
            }
            return Quote.yes(Component.translatable("location.wizards_and_beasts.apothecary.treatment_quote",
                    patients.size(), CurrencyHelper.formatFromKnuts(Use.billed() ? priceOf(patients, use.fee()) : 0)));
        }

        @Override
        public boolean perform(ServerPlayer player, Use use) {
            List<Mob> patients = patients(player);
            if (patients.isEmpty() || !use.charge(player, priceOf(patients, use.fee()))) {
                return false;
            }
            for (Mob mob : patients) {
                mob.removeAllEffects();
                mob.setHealth(mob.getMaxHealth());
            }
            PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                    Component.translatable("location.wizards_and_beasts.apothecary.treated", patients.size()));
            return true;
        }
    }
}
