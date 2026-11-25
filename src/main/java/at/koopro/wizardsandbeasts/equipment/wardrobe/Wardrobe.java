package at.koopro.wizardsandbeasts.equipment.wardrobe;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.house.House;
import at.koopro.wizardsandbeasts.item.armor.WizardArmorItem;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * The wardrobe's one door for giving a robe its identity (WARDROBE_RENAISSANCE §B3).
 *
 * <p>Cut × livery × marks: the cut is the item, the livery a datapack entry pointing at a baked
 * sheet, the marks ({@link Outfit#rank()}) bone groups the renderer shows cumulatively. Every
 * write goes through {@link #stamp}, server-side, so admission is checked in one place.
 */
@NullMarked
public final class Wardrobe {

    public static final ResourceKey<Registry<LiveryDefinition>> LIVERY_REGISTRY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "livery"));

    private Wardrobe() {}

    public static void registerDatapackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(LIVERY_REGISTRY, LiveryDefinition.CODEC, LiveryDefinition.CODEC);
    }

    /** Why a stamp was refused, or {@link #STAMPED}. */
    public enum Result { STAMPED, NOT_A_CUT, UNKNOWN_LIVERY, LIVERY_REFUSES_CUT, BAD_RANK }

    /**
     * Stitches {@code stack} in {@code liveryId} at {@code rank}. Refuses anything that is not a
     * wardrobe cut, a livery the registry does not hold, a livery that does not list this cut, and a
     * rank outside {@code 0..MAX_RANK}. Stamping a cut's own default livery at rank 0 removes the
     * component, so a plain robe stays byte-identical to one that was never stamped.
     */
    public static Result stamp(ItemStack stack, Identifier liveryId, int rank, RegistryAccess access) {
        if (!(stack.getItem() instanceof WizardArmorItem item) || item.defaultLivery() == null) {
            return Result.NOT_A_CUT;
        }
        if (rank < 0 || rank > Outfit.MAX_RANK) {
            return Result.BAD_RANK;
        }
        Optional<LiveryDefinition> livery = access.lookup(LIVERY_REGISTRY)
                .flatMap(registry -> registry.getOptional(liveryId));
        if (livery.isEmpty()) {
            return Result.UNKNOWN_LIVERY;
        }
        if (!livery.get().admits(BuiltInRegistries.ITEM.getKey(item))) {
            return Result.LIVERY_REFUSES_CUT;
        }
        // Restamping keeps the wearer's fit: a livery or a rank is not a reason to change trousers for a skirt.
        Outfit.Fit fit = Outfit.of(stack).fit();
        if (liveryId.equals(item.defaultLivery()) && rank == 0 && fit == Outfit.Fit.TROUSERS) {
            stack.remove(ModDataComponents.OUTFIT.get());
        } else {
            stack.set(ModDataComponents.OUTFIT.get(), new Outfit(Optional.of(liveryId), rank, fit));
        }
        return Result.STAMPED;
    }

    /**
     * Switches an outfit between its trousers and its skirt, keeping livery and rank. The caller checks that the
     * piece has both fits ({@code WizardArmorItem#hasFits}).
     *
     * @return the fit the stack has now
     */
    public static Outfit.Fit toggleFit(ItemStack stack) {
        Outfit old = Outfit.of(stack);
        Outfit now = new Outfit(old.livery(), old.rank(), old.fit().other());
        if (now.equals(Outfit.DEFAULT)) {
            stack.remove(ModDataComponents.OUTFIT.get());
        } else {
            stack.set(ModDataComponents.OUTFIT.get(), now);
        }
        return now.fit();
    }

    /**
     * The sheet a cut draws in a livery, or {@code null} for the cut's base sheet. Pure: the client
     * renderer and the asset test both call it, so the path rule exists once.
     *
     * @param assetName      the cut's model name ({@code student_robe})
     * @param defaultLivery  the cut's default livery; drawing it uses the base sheet
     * @param livery         the stack's livery, if any
     */
    public static @Nullable Identifier liveryTexture(String assetName, @Nullable Identifier defaultLivery,
                                                     Optional<Identifier> livery) {
        if (livery.isEmpty() || livery.get().equals(defaultLivery)) {
            return null;
        }
        Identifier id = livery.get();
        return Identifier.fromNamespaceAndPath(id.getNamespace(),
                "textures/armor/" + assetName + "/" + id.getPath() + ".png");
    }

    /** Cuts whose plain livery takes the wearer's house colours once the Sorting Hat has sorted them. */
    public static final Set<String> HOUSE_FOLLOWING_CUTS = Set.of("scarf");

    /**
     * The livery a worn piece draws in. A livery stitched on by choice always wins, so a Gryffindor scarf stays
     * red and gold on anyone. A plain piece of a {@link #HOUSE_FOLLOWING_CUTS house-following cut} draws in the
     * wearer's house colours once they are sorted, and stays plain (the cut's grey-and-white base) before that.
     * Pure, so the renderer and the test share the rule.
     *
     * @param assetName      the cut's model name ({@code scarf})
     * @param defaultLivery  the cut's default livery
     * @param stamped        the stack's stitched livery, if any
     * @param wearerHouse    the wearer's house, or {@code null} if unsorted or not a player
     */
    public static Optional<Identifier> wornLivery(String assetName, @Nullable Identifier defaultLivery,
                                                  Optional<Identifier> stamped, @Nullable House wearerHouse) {
        boolean plain = stamped.isEmpty() || stamped.get().equals(defaultLivery);
        if (plain && wearerHouse != null && HOUSE_FOLLOWING_CUTS.contains(assetName)) {
            return Optional.of(wearerHouse.livery());
        }
        return stamped.isPresent() ? stamped : Optional.ofNullable(defaultLivery);
    }
}
