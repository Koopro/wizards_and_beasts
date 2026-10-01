package at.koopro.wizardsandbeasts.client.admin.screen;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.client.admin.ClientAdminState;
import at.koopro.wizardsandbeasts.currency.vault.CurrencyHelper;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/** Small formatting helpers the read-only pages share: money, module states, a setting's current value. */
@NullMarked
final class AdminFacts {

    private AdminFacts() {}

    /** {@code 2 G 3 S 4 K}, dropping zero denominations ({@code 0 K} for nothing). */
    static String money(long knuts) {
        long g = knuts / CurrencyHelper.KNUTS_PER_GALLEON;
        long rest = knuts % CurrencyHelper.KNUTS_PER_GALLEON;
        long s = rest / CurrencyHelper.KNUTS_PER_SICKLE;
        long k = rest % CurrencyHelper.KNUTS_PER_SICKLE;
        StringBuilder out = new StringBuilder();
        if (g > 0) {
            out.append(g).append(" G");
        }
        if (s > 0) {
            out.append(out.isEmpty() ? "" : " ").append(s).append(" S");
        }
        if (k > 0 || out.isEmpty()) {
            out.append(out.isEmpty() ? "" : " ").append(k).append(" K");
        }
        return out.toString();
    }

    /** The value the server last reported for a setting, or {@code fallback} when it is not loaded. */
    static String setting(String path, String fallback) {
        AdminSettingDescriptor setting = ClientAdminState.get(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path));
        return setting == null ? fallback : setting.value();
    }

    static long settingLong(String path, long fallback) {
        try {
            return Math.round(Double.parseDouble(setting(path, Long.toString(fallback))));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** "Floo network: enabled" with the module's synced state. */
    static Component moduleState(Module module) {
        return Component.translatable("admin.wizards_and_beasts.module_state."
                + ModuleManager.state(module).name().toLowerCase(Locale.ROOT));
    }

    /** "Chamber of secrets" from {@code CHAMBER_OF_SECRETS}. */
    static Component moduleName(Module module) {
        String text = module.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Component.literal(Character.toUpperCase(text.charAt(0)) + text.substring(1));
    }

    static String seconds(long ticks) {
        return ticks % 20 == 0 ? ticks / 20 + " s" : String.format(Locale.ROOT, "%.1f s", ticks / 20.0);
    }

    static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.0f%%", fraction * 100.0);
    }
}
