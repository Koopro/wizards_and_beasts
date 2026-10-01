package at.koopro.wizardsandbeasts.admin.broom;

import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.broom.BroomDefinition;
import at.koopro.wizardsandbeasts.broom.rules.BroomRules;
import at.koopro.wizardsandbeasts.broom.rules.BroomStat;
import at.koopro.wizardsandbeasts.network.admin.AdminBroomPayloads.BroomSummary;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reads the broom roster for the Travel section: every authored broom definition, its stats as authored and as they
 * fly (overrides and the server's speed scale applied — {@link BroomRules#effective}), and the settings that edit it.
 * Nothing is kept here; every value change is an ordinary broom setting through {@code AdminSettingService}.
 */
@NullMarked
public final class BroomAdminService {

    private static final String FACT = "admin.wizards_and_beasts.broom_fact.";

    private BroomAdminService() {}

    public static boolean authorised(AdminContext actor) {
        return actor.canRead() && actor.canModify(AdminCapability.CONTENT);
    }

    public record Listing(List<BroomSummary> brooms, List<AdminSettingDescriptor> settings) {}

    public static Listing list(AdminContext viewer) {
        List<BroomDefinition> all = new ArrayList<>(BroomRules.authoredAll());
        all.sort(Comparator.comparing((BroomDefinition d) -> d.tier().ordinal()).thenComparing(d -> d.id().toString()));
        List<BroomSummary> brooms = new ArrayList<>();
        List<AdminSettingDescriptor> settings = new ArrayList<>();
        for (BroomDefinition authored : all) {
            brooms.add(summary(authored));
            add(settings, BroomSettingProvider.id(authored.id(), BroomSettingProvider.ENABLED), viewer);
            for (BroomStat stat : BroomStat.values()) {
                add(settings, BroomSettingProvider.id(authored.id(), stat.id()), viewer);
            }
        }
        return new Listing(List.copyOf(brooms), List.copyOf(settings));
    }

    private static void add(List<AdminSettingDescriptor> out, Identifier id, AdminContext viewer) {
        AdminSetting<?> setting = AdminSettings.registry().get(id);
        if (setting != null) {
            out.add(AdminSettingDescriptor.of(setting, viewer));
        }
    }

    static BroomSummary summary(BroomDefinition authored) {
        BroomDefinition flies = BroomRules.effective(authored);
        List<Float> authoredStats = new ArrayList<>();
        List<Float> effectiveStats = new ArrayList<>();
        for (BroomStat stat : BroomStat.values()) {
            authoredStats.add(stat.read(authored));
            effectiveStats.add(stat.read(flies));
        }
        List<AdminSpellFact> facts = new ArrayList<>();
        facts.add(fact("handling_profile", authored.handling().profile().name().toLowerCase(Locale.ROOT)));
        facts.add(fact("boost", String.format(Locale.ROOT, "%d ticks, cooldown %d ticks",
                authored.boostDurationTicks(), authored.boostCooldownTicks())));
        facts.add(fact("landing", String.format(Locale.ROOT, "hover sink %s · crash damage ×%s",
                format(authored.weakGravity()), format(authored.handling().crashDamageMultiplier()))));
        facts.add(fact("fov_punch", format(authored.handling().boostFovPunch())));
        facts.add(fact("drift", String.format(Locale.ROOT, "yaw drift %s · momentum %s · boost wobble %s",
                format(authored.handling().yawDrift()), format(authored.handling().momentumRetention()),
                format(authored.handling().wobbleAtBoost()))));
        facts.add(fact("durability", Integer.toString(authored.durability())));
        List<String> slots = new ArrayList<>();
        authored.modelSlots().forEach((slot, model) -> slots.add(slot.name().toLowerCase(Locale.ROOT) + " " + model.getPath()));
        slots.sort(String::compareTo);
        if (!slots.isEmpty()) {
            facts.add(fact("model", String.join(", ", slots)));
        }
        authored.assets().model().ifPresent(model -> facts.add(fact("geo_model", model.toString())));
        String name = authored.displayName().getContents() instanceof TranslatableContents t
                ? t.getKey() : authored.displayName().getString();
        boolean overridden = !BroomRules.overridesFor(authored.id().toString()).isEmpty();
        return new BroomSummary(authored.id().toString(), name, authored.tier().name().toLowerCase(Locale.ROOT),
                BroomRules.enabled(authored.id()), overridden,
                authored.woodTint() == BroomDefinition.UNTINTED ? 0 : authored.woodTint(),
                List.copyOf(authoredStats), List.copyOf(effectiveStats), List.copyOf(facts));
    }

    private static AdminSpellFact fact(String label, String value) {
        return new AdminSpellFact(FACT + label, value, false);
    }

    private static String format(float value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
