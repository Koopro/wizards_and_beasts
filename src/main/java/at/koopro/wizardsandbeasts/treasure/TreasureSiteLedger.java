package at.koopro.wizardsandbeasts.treasure;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The treasure sites whose treasure has been taken, for the whole world: a site is spent once any of its treasure
 * chests has rolled its loot. Chests roll once, when first opened, so a spent site's chests can no longer yield an
 * artefact to anyone, and an expedition chart is never drawn to it again ({@link SiteSearch}). Kept on the Overworld
 * with the other world records.
 *
 * <p>World-wide rather than per player because a chest is a block in a shared world: what one wizard took is gone for
 * all. Charts already handed out are untouched: a map item carries its own target.
 */
@NullMarked
public final class TreasureSiteLedger extends SavedData {

    public static final Codec<TreasureSiteLedger> CODEC = Codec.STRING.listOf()
            .xmap(TreasureSiteLedger::new, ledger -> List.copyOf(ledger.spent));

    public static final SavedDataType<TreasureSiteLedger> TYPE = new SavedDataType<>(
            WizardsAndBeastsMod.MODID + "_treasure_sites", TreasureSiteLedger::new, CODEC);

    private final Set<String> spent;

    public TreasureSiteLedger() {
        this.spent = new LinkedHashSet<>();
    }

    private TreasureSiteLedger(List<String> spent) {
        this.spent = new LinkedHashSet<>(spent);
    }

    public static TreasureSiteLedger get(ServerLevel anyLevel) {
        return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isSpent(SiteSearch.Site site) {
        return spent.contains(site.key());
    }

    public boolean isSpent(String key) {
        return spent.contains(key);
    }

    /** Records a site as spent. Returns whether it was new. */
    public boolean spend(String key) {
        if (!spent.add(key)) {
            return false;
        }
        setDirty();
        return true;
    }

    /** Forgets every spent site: an operator's (or a test's) reset. */
    public void clear() {
        if (!spent.isEmpty()) {
            spent.clear();
            setDirty();
        }
    }

    public int size() {
        return spent.size();
    }
}
