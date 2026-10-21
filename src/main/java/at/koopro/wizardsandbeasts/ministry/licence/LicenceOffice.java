package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.Config;
import at.koopro.wizardsandbeasts.currency.billing.MoneyFlow;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.registry.TrinketItemRegistry;
import at.koopro.wizardsandbeasts.location.LocationService;
import at.koopro.wizardsandbeasts.location.LocationServices;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCaseData;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.standing.StandingAxis;
import at.koopro.wizardsandbeasts.standing.StandingBand;
import at.koopro.wizardsandbeasts.standing.StandingService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The Ministry's licence office: the lawful way to get the papers a gate asks for, at a clerk's counter, for a
 * fee. One counter service per licence type ({@code licence_<type>}), so the Ministry's location file decides which
 * the office sells and from which tier.
 *
 * <p>The rules are {@link LicenceRules#apply}: a pass in the examined subject, no suspension, no open case, and a
 * Ministry that does not count the applicant a criminal. A held licence that has expired is renewed for the same
 * fee. The fee is {@code ministryLicenceFeeKnuts}, booked as {@link MoneyFlow#LICENCE_FEE}; the term is
 * {@code ministryLicenceTermDays} (0, the default, never expires).
 */
@NullMarked
public final class LicenceOffice implements LocationService {

    private static final String KEY = "ministry.wizards_and_beasts.licence_office.";

    private final LicenseType type;

    private LicenceOffice(LicenseType type) {
        this.type = type;
    }

    public static void register(LocationServices.Sink sink) {
        for (LicenseType type : LicenseType.values()) {
            if (LicenceRules.soldAtOffice(type)) {
                sink.register("licence_" + type.getSerializedName(), new LicenceOffice(type));
            }
        }
    }

    /** What the office would do with this player's application now. */
    public static LicenceRules.Application assess(ServerPlayer player, LicenseType type) {
        long now = MinistryTrace.now(player.level().getServer());
        @Nullable ItemStack document = MinistryLicences.findDocument(player, type);
        @Nullable LicenseData held = document == null ? null : MinistryLicences.read(document);
        boolean mine = held != null && !held.revoked() && held.issuedTo().equals(player.getUUID());
        boolean openCase = MinistryCaseData.get(player.level().getServer()).dossier(player.getUUID())
                .openCase().isPresent();
        boolean distrusted = StandingService.bandOf(player, StandingAxis.MINISTRY) == StandingBand.STRONG_NEGATIVE;
        return LicenceRules.apply(type, LicenceUpgrade.gradeIn(player, type), MinistryLicences.suspended(player, type),
                openCase, distrusted, mine, mine && held.hasExpired(now));
    }

    private static long fee() {
        return LocationService.Use.billed() ? Math.max(0, Config.ministryLicenceFeeKnuts) : 0L;
    }

    @Override
    public Quote quote(ServerPlayer player, Use use) {
        LicenceRules.Application application = assess(player, type);
        return switch (application) {
            case ISSUE, RENEW -> Quote.yes(Component.translatable(KEY + application.name().toLowerCase(Locale.ROOT)
                    + ".quote", type.displayName(), CurrencyHelper.formatFromKnuts(fee())));
            default -> Quote.no(refusal(application));
        };
    }

    @Override
    public boolean perform(ServerPlayer player, Use use) {
        LicenceRules.Application application = assess(player, type);
        if (application != LicenceRules.Application.ISSUE && application != LicenceRules.Application.RENEW) {
            PlayerFeedback.refuse(player, Component.translatable(KEY + "refused"), refusal(application));
            return false;
        }
        if (!use.charge(player, MoneyFlow.LICENCE_FEE, fee())) {
            return false;
        }
        long now = MinistryTrace.now(player.level().getServer());
        long expiry = LicenceRules.expiryFor(now, Config.ministryLicenceTermDays);
        if (application == LicenceRules.Application.RENEW) {
            ItemStack document = MinistryLicences.findDocument(player, type);
            LicenseData held = document == null ? null : MinistryLicences.read(document);
            if (document != null && held != null) {
                // Renewal keeps the endorsements earned on the scroll; only the term starts again.
                document.set(ModDataComponents.LICENSE_DATA.get(), new LicenseData(held.type(), held.rank(),
                        held.issuedTo(), expiry, false, held.forged()));
            }
        } else {
            ItemStack scroll = new ItemStack(TrinketItemRegistry.MINISTRY_LICENSE_SCROLL.get());
            scroll.set(ModDataComponents.LICENSE_DATA.get(),
                    new LicenseData(type, 0, player.getUUID(), expiry, false, false));
            at.koopro.wizardsandbeasts.util.InventoryHandover.giveOrDrop(player, scroll);
        }
        PlayerFeedback.toast(player, NoticeKind.UNLOCK,
                Component.translatable(KEY + (application == LicenceRules.Application.RENEW ? "renewed" : "issued")),
                type.displayName());
        return true;
    }

    private Component refusal(LicenceRules.Application application) {
        if (application == LicenceRules.Application.GRADE) {
            return Component.translatable(KEY + "refused.grade",
                    Component.translatable(type.examinedSubject().translationKey()),
                    Component.translatable(LicenceRules.gradeToApply(type).translationKey()));
        }
        return Component.translatable(KEY + "refused." + application.name().toLowerCase(Locale.ROOT));
    }
}
