package at.koopro.wizardsandbeasts.currency.vault;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class CurrencyHelper {

    public static final int KNUTS_PER_SICKLE = 29;
    public static final int SICKLES_PER_GALLEON = 17;
    public static final int KNUTS_PER_GALLEON = KNUTS_PER_SICKLE * SICKLES_PER_GALLEON; // 493
    public static final int DRAGOTS_PER_10_GALLEONS = 43;
    public static final Identifier KNUT_ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "knut");
    public static final Identifier SICKLE_ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "sickle");
    public static final Identifier GALLEON_ID = Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, "galleon");

    private CurrencyHelper() {
    }

    public static long toKnuts(long galleons, long sickles, long knuts) {
        return galleons * KNUTS_PER_GALLEON + sickles * KNUTS_PER_SICKLE + knuts;
    }

    public static int countItem(Inventory inventory, Item item) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static boolean removeItems(Inventory inventory, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        return remaining == 0;
    }

    public static void giveItems(Inventory inventory, Item item, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int batch = Math.min(remaining, item.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(item, batch);
            if (!inventory.add(stack)) {
                inventory.player.drop(stack, false);
            }
            remaining -= batch;
        }
    }

    /**
     * Break a total knut value into optimal Galleon/Sickle/Knut denominations.
     * Returns {galleons, sickles, knuts}.
     */
    public static long[] fromKnuts(long totalKnuts) {
        long g = totalKnuts / KNUTS_PER_GALLEON;
        long remainder = totalKnuts % KNUTS_PER_GALLEON;
        long s = remainder / KNUTS_PER_SICKLE;
        long k = remainder % KNUTS_PER_SICKLE;
        return new long[]{g, s, k};
    }

    /**
     * Format currency as a compact string like "2G 5S 14K".
     */
    public static String formatCurrency(long galleons, long sickles, long knuts) {
        StringBuilder sb = new StringBuilder();
        if (galleons > 0) sb.append(String.format("%,dG", galleons));
        if (sickles > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(String.format("%,dS", sickles));
        }
        if (knuts > 0 || sb.length() == 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(String.format("%,dK", knuts));
        }
        return sb.toString();
    }

    /**
     * Format a total knut value as a denomination breakdown string.
     */
    public static String formatFromKnuts(long totalKnuts) {
        long[] parts = fromKnuts(totalKnuts);
        return formatCurrency(parts[0], parts[1], parts[2]);
    }

    public static boolean isCoin(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return WizardsAndBeastsMod.MODID.equals(id.getNamespace());
    }

    public static boolean isCanonicalCoin(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return isCanonicalCoinId(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean isCanonicalCoinId(Identifier id) {
        return KNUT_ID.equals(id) || SICKLE_ID.equals(id) || GALLEON_ID.equals(id);
    }

    public static int canonicalCoinValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return canonicalCoinValue(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static int canonicalCoinValue(Identifier id) {
        if (GALLEON_ID.equals(id)) return KNUTS_PER_GALLEON;
        if (SICKLE_ID.equals(id)) return KNUTS_PER_SICKLE;
        if (KNUT_ID.equals(id)) return 1;
        return 0;
    }

    /**
     * Returns the slot index containing the highest-value canonical coin id.
     * Non-canonical ids are ignored. Ties prefer the lowest slot index.
     */
    public static int selectHighestCanonicalCoinSlot(List<Identifier> itemIdsBySlot) {
        int bestSlot = -1;
        int bestValue = -1;
        for (int i = 0; i < itemIdsBySlot.size(); i++) {
            int value = canonicalCoinValue(itemIdsBySlot.get(i));
            if (value > bestValue) {
                bestValue = value;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
}
