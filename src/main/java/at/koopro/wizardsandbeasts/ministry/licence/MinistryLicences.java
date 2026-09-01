package at.koopro.wizardsandbeasts.ministry.licence;

import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one place anything asks "may this wizard do that".
 *
 * <p>Every gate in the mod that answers to paperwork — Apparating, mounting a racing broom, walking
 * into the Ministry, dealing in controlled ingredients, being spoken to by a goblin — goes through
 * {@link #verdict}. That is the point of the class: a licence type is added by adding an enum
 * constant and a call site, never by teaching a fifth subsystem what a revocation means.
 *
 * <h2>Carried, not brandished</h2>
 * The brief says the checks apply "when held". This searches the hands first and then the rest of the
 * pack, which is a deliberate widening: a wizard has one free hand and a wand in the other, so a
 * licence that only worked while gripped would make Apparating with a wand out impossible and would
 * turn every broom mount into an inventory shuffle. Papers live in your pocket. The hands are searched
 * first so that a wizard carrying two licences of the same type — one real, one forged — decides which
 * one they are presenting by holding it.
 *
 * <h2>Any stack carrying the component counts</h2>
 * The lookup keys off {@link ModDataComponents#LICENSE_DATA} rather than off the scroll item, so a
 * datapack can put a licence on a warrant card, a badge or a signet ring without this class changing.
 * The scroll is the shipped carrier, not the definition.
 */
@NullMarked
public final class MinistryLicences {

    /** The refusal, in the Ministry's own words. */
    public static final String REFUSAL_KEY = "ministry.wizards_and_beasts.licence.refused";

    private MinistryLicences() {}

    /** Why a check came out the way it did. {@code reason} is presentable either way. */
    public record Verdict(boolean allowed, Component reason, @Nullable ItemStack document) {

        public static Verdict allow(ItemStack document, LicenseType type) {
            return new Verdict(true,
                    Component.translatable("ministry.wizards_and_beasts.licence.accepted", type.displayName()),
                    document);
        }

        public static Verdict refuse() {
            return new Verdict(false, Component.translatable(REFUSAL_KEY), null);
        }

        public static Verdict refuse(String key, Object... args) {
            return new Verdict(false, Component.translatable(key, args), null);
        }
    }

    /**
     * The licence stack this player would present for {@code type}, valid or not.
     *
     * <p>Returns revoked and expired documents too — an official has to be able to look at the reason
     * a wizard is being turned away, and the forgery roll has to be able to find the forgery.
     */
    public static @Nullable ItemStack findDocument(Player player, LicenseType type) {
        ItemStack main = player.getMainHandItem();
        if (matches(main, type)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (matches(off, type)) {
            return off;
        }
        for (ItemStack carried : player.getInventory().getNonEquipmentItems()) {
            if (matches(carried, type)) {
                return carried;
            }
        }
        return null;
    }

    /** The {@link LicenseData} on a stack, or {@code null} if it is not a licence at all. */
    public static @Nullable LicenseData read(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(ModDataComponents.LICENSE_DATA.get());
    }

    /** Full check at a required endorsement rank. Rank 0 means "any valid licence of this type". */
    public static Verdict verdict(Player player, LicenseType type, int requiredRank) {
        ItemStack document = findDocument(player, type);
        if (document == null) {
            return Verdict.refuse();
        }
        LicenseData data = read(document);
        if (data == null) {
            return Verdict.refuse();
        }
        if (data.revoked()) {
            return Verdict.refuse("ministry.wizards_and_beasts.licence.deny.revoked");
        }
        if (data.hasExpired(player.level().getGameTime())) {
            return Verdict.refuse("ministry.wizards_and_beasts.licence.deny.expired");
        }
        if (!data.issuedTo().equals(player.getUUID())) {
            return Verdict.refuse("ministry.wizards_and_beasts.licence.deny.not_yours");
        }
        if (!LicenceRules.rankSatisfies(data.rank(), requiredRank)) {
            return Verdict.refuse("ministry.wizards_and_beasts.licence.deny.rank", requiredRank, data.rank());
        }
        return Verdict.allow(document, type);
    }

    public static Verdict verdict(Player player, LicenseType type) {
        return verdict(player, type, 0);
    }

    /** Terse form for call sites that only branch. */
    public static boolean has(Player player, LicenseType type) {
        return verdict(player, type, 0).allowed();
    }

    public static boolean has(Player player, LicenseType type, int requiredRank) {
        return verdict(player, type, requiredRank).allowed();
    }

    private static boolean matches(ItemStack stack, LicenseType type) {
        LicenseData data = read(stack);
        return data != null && data.type() == type;
    }
}
