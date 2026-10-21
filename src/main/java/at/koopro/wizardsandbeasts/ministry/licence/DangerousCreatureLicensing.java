package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.feedback.NoticeKind;
import at.koopro.wizardsandbeasts.feedback.PlayerFeedback;
import at.koopro.wizardsandbeasts.ministry.law.CodeOfWandUse;
import at.koopro.wizardsandbeasts.ministry.law.MagicalOffence;
import at.koopro.wizardsandbeasts.ministry.law.TraceService;
import at.koopro.wizardsandbeasts.ministry.trace.CastSurvey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Dangerous Creatures licence, given teeth: breeding a beast the Ministry classifies XXXX or above without one is
 * an offence when an official sees it — the Code of Wand Use's rule, that the Ministry knows only what it sees,
 * applied to Hagrid's dragon. Out of sight, the breeder is warned and nothing is filed: illegal, not impossible.
 *
 * <p>An event rather than a mixin: NeoForge fires {@link BabyEntitySpawnEvent} for every bred pair, with the player
 * who caused it.
 */
@NullMarked
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class DangerousCreatureLicensing {

    /** The classification at which a beast needs a licence to keep and breed: XXXX. */
    public static final int LICENSED_RATING = CastSurvey.DANGEROUS_RATING;

    private DangerousCreatureLicensing() {}

    /** Whether breeding these parents needs a Dangerous Creatures licence. */
    public static boolean needsLicence(int ratingA, int ratingB) {
        return Math.max(ratingA, ratingB) >= LICENSED_RATING;
    }

    @SubscribeEvent
    public static void onBred(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer breeder) || !TraceService.isActive()) {
            return;
        }
        check(breeder, event.getParentA(), event.getParentB());
    }

    /**
     * Files the offence for an unlicensed breeding an official saw.
     *
     * @return whether it was filed
     */
    public static boolean check(ServerPlayer breeder, @Nullable Mob parentA, @Nullable Mob parentB) {
        int a = parentA == null ? 0 : CastSurvey.ratingOf(parentA);
        int b = parentB == null ? 0 : CastSurvey.ratingOf(parentB);
        if (!needsLicence(a, b) || MinistryLicences.has(breeder, LicenseType.DANGEROUS_CREATURES)) {
            return false;
        }
        if (!CodeOfWandUse.seenByOfficial(breeder)) {
            PlayerFeedback.actionBar(breeder, Component.translatable(
                    "ministry.wizards_and_beasts.dangerous_creatures.unseen"));
            return false;
        }
        TraceService.report(breeder, MagicalOffence.UNLICENSED_DANGEROUS_CREATURE);
        PlayerFeedback.toast(breeder, NoticeKind.FAIL,
                Component.translatable("ministry.wizards_and_beasts.dangerous_creatures.title"),
                Component.translatable("ministry.wizards_and_beasts.dangerous_creatures.body"));
        return true;
    }
}
