package at.koopro.wizardsandbeasts.equipment.wardrobe;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
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
        if (liveryId.equals(item.defaultLivery()) && rank == 0) {
            stack.remove(ModDataComponents.OUTFIT.get());
        } else {
            stack.set(ModDataComponents.OUTFIT.get(), new Outfit(Optional.of(liveryId), rank));
        }
        return Result.STAMPED;
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
}
