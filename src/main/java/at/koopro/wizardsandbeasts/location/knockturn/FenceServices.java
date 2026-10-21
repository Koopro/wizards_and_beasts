package at.koopro.wizardsandbeasts.location.knockturn;

import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.location.LocationService;
import at.koopro.wizardsandbeasts.location.LocationServices;
import at.koopro.wizardsandbeasts.market.DealClass;
import at.koopro.wizardsandbeasts.market.IllicitDealing;
import at.koopro.wizardsandbeasts.ministry.MinistryRecords;
import at.koopro.wizardsandbeasts.ministry.licence.LicenceForgery;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseData;
import at.koopro.wizardsandbeasts.ministry.licence.LicenseType;
import at.koopro.wizardsandbeasts.ministry.licence.MinistryLicences;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Magic sold under the counter by a fence. Both are {@link DealClass#SERVICE} deals: buying one may reach the
 * Ministry ({@code procuring_illegal_magic}), on top of whatever it was bought to hide.
 *
 * <ul>
 *   <li><b>memory_charm</b> — a Memory Charm on the people who saw what you did. Takes {@code rate} of the customer's
 *       current heat off ({@code notoriety}); never touches the permanent file or a debt. Priced at the greater of the
 *       fee and {@link #KNUTS_PER_HEAT} per point of heat: the hotter the wizard, the dearer the silence. If the deal
 *       itself is reported, the procuring offence puts heat straight back on.</li>
 *   <li><b>forge_papers</b> — the licence in hand, re-inked one rank higher. Every Ministry inspection has the
 *       existing 15 % chance to spot it ({@code LicenceForgery.inspect}), which files {@code forged_documents}, an
 *       arrestable offence. The fee is per rank reached.</li>
 *   <li><b>bribe_clerk</b> — a case file lost in the Department; see {@link BribeClerk}.</li>
 * </ul>
 */
@NullMarked
public final class FenceServices {

    /** Knuts per point of heat a memory charm takes off: a Sickle a point. */
    public static final long KNUTS_PER_HEAT = 29L;

    private FenceServices() {}

    public static void register(LocationServices.Sink sink) {
        sink.register("memory_charm", new MemoryCharm());
        sink.register("forge_papers", new ForgePapers());
        sink.register("bribe_clerk", new BribeClerk());
    }

    /**
     * <b>bribe_clerk</b> — a clerk in the Department paid to lose a case file. Only while the case is still under
     * investigation and nothing dark is on it ({@code MinistryTrace.fileCanBeLost}); a summons cannot be bought back
     * and no sum buries an Unforgivable. The deal runs the fence's usual risk, at twice the weight of other
     * services; caught, it files {@code bribery_of_official}, the money is gone, and the case goes straight to a
     * summons. Server switch: {@code ministryBribery}.
     */
    static final class BribeClerk implements LocationService {

        static long priceOf(int incidents, long fee) {
            return fee * Math.max(1, incidents);
        }

        private static int incidents(ServerPlayer player) {
            return at.koopro.wizardsandbeasts.ministry.trace.MinistryCaseData.get(player.level().getServer())
                    .dossier(player.getUUID()).openCase().map(open -> open.incidents().size()).orElse(0);
        }

        @Override
        public Quote quote(ServerPlayer player, Use use) {
            if (!at.koopro.wizardsandbeasts.Config.ministryBribery) {
                return Quote.no(Component.translatable("location.wizards_and_beasts.knockturn.bribe_off"));
            }
            if (!at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace.fileCanBeLost(
                    player.level().getServer(), player.getUUID())) {
                return Quote.no(Component.translatable("location.wizards_and_beasts.knockturn.bribe_no_file"));
            }
            return Quote.yes(Component.translatable("location.wizards_and_beasts.knockturn.bribe_quote",
                    CurrencyHelper.formatFromKnuts(Use.billed() ? priceOf(incidents(player), use.fee()) : 0)));
        }

        @Override
        public boolean perform(ServerPlayer player, Use use) {
            if (!at.koopro.wizardsandbeasts.Config.ministryBribery
                    || !at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace.fileCanBeLost(
                            player.level().getServer(), player.getUUID())
                    || !use.charge(player, priceOf(incidents(player), use.fee()))) {
                return false;
            }
            if (IllicitDealing.rollAs(player, use.def(), DealClass.SERVICE, 2,
                    at.koopro.wizardsandbeasts.ministry.law.MagicalOffence.BRIBERY_OF_OFFICIAL)) {
                at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace.escalateAfterBribe(player);
                return true;
            }
            at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace.loseFile(player);
            PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                    Component.translatable("location.wizards_and_beasts.knockturn.bribe_done"),
                    Component.translatable("location.wizards_and_beasts.knockturn.bribe_done.body"));
            return true;
        }
    }

    static final class MemoryCharm implements LocationService {

        static long priceOf(float notoriety, long fee) {
            return Math.max(fee, (long) Math.ceil(notoriety) * KNUTS_PER_HEAT);
        }

        @Override
        public Quote quote(ServerPlayer player, Use use) {
            float heat = MinistryRecords.get(player).notoriety();
            if (heat < 1.0f) {
                return Quote.no(Component.translatable("location.wizards_and_beasts.knockturn.no_heat"));
            }
            return Quote.yes(Component.translatable("location.wizards_and_beasts.knockturn.memory_quote",
                    Math.round(heat * use.rate()), CurrencyHelper.formatFromKnuts(Use.billed() ? priceOf(heat, use.fee()) : 0)));
        }

        @Override
        public boolean perform(ServerPlayer player, Use use) {
            float heat = MinistryRecords.get(player).notoriety();
            if (heat < 1.0f || !use.charge(player, priceOf(heat, use.fee()))) {
                return false;
            }
            float rate = use.rate();
            MinistryRecords.mutate(player, record -> record.withNotoriety(record.notoriety() * (1.0f - rate)));
            PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                    Component.translatable("location.wizards_and_beasts.knockturn.memory_done"),
                    Component.translatable("location.wizards_and_beasts.knockturn.memory_done.body",
                            Math.round(heat * rate)));
            IllicitDealing.roll(player, use.def(), DealClass.SERVICE, 1);
            return true;
        }
    }

    static final class ForgePapers implements LocationService {

        /** The licence in the main hand that can be forged one rank up, or null. */
        static @Nullable LicenseData forgeable(ServerPlayer player) {
            LicenseData data = MinistryLicences.read(player.getMainHandItem());
            if (data == null || data.revoked() || data.forged() || data.rank() >= LicenseType.MAX_RANK
                    || !player.getUUID().equals(data.issuedTo())) {
                return null;
            }
            return data;
        }

        @Override
        public Quote quote(ServerPlayer player, Use use) {
            LicenseData data = forgeable(player);
            if (data == null) {
                return Quote.no(Component.translatable("location.wizards_and_beasts.knockturn.hold_licence"));
            }
            long price = use.fee() * (data.rank() + 1);
            return Quote.yes(Component.translatable("location.wizards_and_beasts.knockturn.forge_quote",
                    data.rank() + 1, CurrencyHelper.formatFromKnuts(Use.billed() ? price : 0)));
        }

        @Override
        public boolean perform(ServerPlayer player, Use use) {
            LicenseData data = forgeable(player);
            if (data == null || !use.charge(player, use.fee() * (data.rank() + 1))) {
                return false;
            }
            ItemStack scroll = player.getMainHandItem();
            scroll.set(ModDataComponents.LICENSE_DATA.get(), data.withRank(data.rank() + 1));
            LicenceForgery.forge(scroll);
            PlayerFeedback.toast(player, NoticeKind.SUCCESS,
                    Component.translatable("location.wizards_and_beasts.knockturn.forged"),
                    Component.translatable("location.wizards_and_beasts.knockturn.forged.body", data.rank() + 1));
            IllicitDealing.roll(player, use.def(), DealClass.SERVICE, 1);
            return true;
        }
    }
}
