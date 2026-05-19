package at.koopro.wizardsandbeasts.data;

import at.koopro.wizardsandbeasts.currency.vault.PlayerVaultData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerVaultDataMigratorTest {

    @Test
    void load_unversionedTag_stampsCurrentVersion() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Knuts", 10);
        tag.putLong("Sickles", 1);
        tag.putLong("Galleons", 0);
        PlayerVaultData data = new PlayerVaultData();
        data.load(tag);

        assertEquals(10, data.getKnuts());
        assertEquals(1, data.getSickles());
        assertEquals(0, data.getGalleons());
        assertEquals(PlayerVaultData.CURRENT_VERSION, tag.getInt(PlayerVaultData.VERSION_KEY).orElse(0));
    }
}
