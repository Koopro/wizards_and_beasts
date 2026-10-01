package at.koopro.wizardsandbeasts.admin.wand;

import at.koopro.wizardsandbeasts.admin.AdminSettings;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.admin.config.AdminSetting;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.network.admin.AdminSettingDescriptor;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.network.admin.AdminWandPayloads.PartInfo;
import at.koopro.wizardsandbeasts.network.admin.AdminWandPayloads.Preview;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.cast.WandStats;
import at.koopro.wizardsandbeasts.wand.cast.WandStatsResolver;
import at.koopro.wizardsandbeasts.wand.customization.WandPreset;
import at.koopro.wizardsandbeasts.wand.customization.WandPresetRegistry;
import at.koopro.wizardsandbeasts.wand.recipe.WandmakingRecipe;
import at.koopro.wizardsandbeasts.wand.registry.WandCastModifiers;
import at.koopro.wizardsandbeasts.wand.registry.WandCoreDefinition;
import at.koopro.wizardsandbeasts.wand.registry.WandDatapackRegistries;
import at.koopro.wizardsandbeasts.wand.registry.WandTemperament;
import at.koopro.wizardsandbeasts.wand.registry.WandWoodDefinition;
import at.koopro.wizardsandbeasts.wand.rules.WandRules;
import at.koopro.wizardsandbeasts.wand.rules.WandRulesService;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the wand registries, recipes and presets for the Wands section, and makes the one kind of wand an
 * administrator may ask for: a test wand of a pairing the server allows.
 *
 * <p>Everything shown is read, when asked, from what the game itself uses: the {@code wand_woods} and
 * {@code wand_cores} datapack registries, the {@code wandmaking} recipes (the pairings), the presets
 * ({@code WandPresetRegistry}), and — for a previewed wand's numbers — {@link WandStatsResolver}, the same resolver a
 * cast uses. Each wood's and core's canonical lore is the {@code _lore} note in its own datapack file, shown as
 * written.
 */
@NullMarked
public final class WandAdminService {

    public static final float MIN_LENGTH = 8.0f;
    public static final float MAX_LENGTH = 16.0f;
    private static final String FACT = "admin.wizards_and_beasts.wand_fact.";
    private static final String KEY = "admin.wizards_and_beasts.wand_action.";
    private static final Logger LOGGER = LogUtils.getLogger();

    private WandAdminService() {}

    public static boolean authorised(AdminContext actor) {
        return actor.canRead() && actor.canModify(AdminCapability.CONTENT);
    }

    // ── catalog ──

    public record Catalog(List<PartInfo> woods, List<PartInfo> cores, List<String> pairs, List<String> presets,
                          List<AdminSettingDescriptor> settings) {}

    public static Catalog catalog(MinecraftServer server, AdminContext viewer) {
        List<PartInfo> woods = new ArrayList<>();
        List<AdminSettingDescriptor> settings = new ArrayList<>();
        Registry<WandWoodDefinition> woodRegistry = server.registryAccess().lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY);
        for (Map.Entry<ResourceKey<WandWoodDefinition>, WandWoodDefinition> entry : sorted(woodRegistry.entrySet())) {
            Identifier id = entry.getKey().identifier();
            woods.add(woodInfo(server, id, entry.getValue()));
            add(settings, WandSettingProvider.woodId(id), viewer);
        }
        List<PartInfo> cores = new ArrayList<>();
        Registry<WandCoreDefinition> coreRegistry = server.registryAccess().lookupOrThrow(WandDatapackRegistries.WAND_CORE_REGISTRY);
        for (Map.Entry<ResourceKey<WandCoreDefinition>, WandCoreDefinition> entry : sorted(coreRegistry.entrySet())) {
            Identifier id = entry.getKey().identifier();
            cores.add(coreInfo(server, id, entry.getValue()));
            add(settings, WandSettingProvider.coreId(id), viewer);
        }
        List<String> pairs = new ArrayList<>(WandRulesService.recipePairs(server));
        pairs.sort(String::compareTo);
        for (String pair : pairs) {
            String[] halves = pair.split("\\|", 2);
            add(settings, WandSettingProvider.pairId(Identifier.parse(halves[0]), Identifier.parse(halves[1])), viewer);
        }
        List<String> presets = new ArrayList<>();
        for (WandPreset preset : WandPresetRegistry.all()) {
            presets.add(preset.id() + "|" + preset.displayName());
        }
        return new Catalog(List.copyOf(woods), List.copyOf(cores), List.copyOf(pairs), List.copyOf(presets), List.copyOf(settings));
    }

    private static <T> List<Map.Entry<ResourceKey<T>, T>> sorted(java.util.Set<Map.Entry<ResourceKey<T>, T>> entries) {
        List<Map.Entry<ResourceKey<T>, T>> out = new ArrayList<>(entries);
        out.sort((a, b) -> a.getKey().identifier().compareTo(b.getKey().identifier()));
        return out;
    }

    private static void add(List<AdminSettingDescriptor> out, Identifier id, AdminContext viewer) {
        AdminSetting<?> setting = AdminSettings.registry().get(id);
        if (setting != null) {
            out.add(AdminSettingDescriptor.of(setting, viewer));
        }
    }

    static PartInfo woodInfo(MinecraftServer server, Identifier id, WandWoodDefinition d) {
        List<AdminSpellFact> facts = new ArrayList<>();
        facts.add(text("rarity", d.rarity()));
        facts.add(text("affinity_tags", String.join(", ", d.affinityTags())));
        facts.add(text("personality", String.join(", ", d.personalityAffinity())));
        if (!d.spellModifiers().isEmpty()) {
            List<String> mods = new ArrayList<>();
            d.spellModifiers().forEach((k, v) -> mods.add(k + " " + signed(v)));
            mods.sort(String::compareTo);
            facts.add(text("spell_modifiers", String.join(", ", mods)));
        }
        facts.add(text("refuse_threshold", format(d.refuseThreshold())));
        castFacts(facts, d.castModifiers());
        temperamentFacts(facts, d.temperament());
        List<String> modules = new ArrayList<>();
        d.appearance().handle().ifPresent(m -> modules.add("handle " + m.getPath()));
        d.appearance().shaft().ifPresent(m -> modules.add("shaft " + m.getPath()));
        d.appearance().tip().ifPresent(m -> modules.add("tip " + m.getPath()));
        if (!modules.isEmpty()) {
            facts.add(text("appearance", String.join(", ", modules)));
        }
        Component name = d.displayName();
        return new PartInfo(id.toString(), nameOf(name), name.getContents() instanceof TranslatableContents,
                lore(server, "wand_woods", id), List.copyOf(facts), d.appearance().tint().orElse(0xFFA9784B));
    }

    static PartInfo coreInfo(MinecraftServer server, Identifier id, WandCoreDefinition d) {
        List<AdminSpellFact> facts = new ArrayList<>();
        facts.add(text("source", d.sourceKey()));
        facts.add(text("raw_power", format(d.rawPower())));
        facts.add(text("consistency", format(d.consistency())));
        facts.add(text("loyalty", format(d.loyalty())));
        facts.add(text("dark_affinity", format(d.darkAffinity())));
        facts.add(text("initiative", format(d.initiative())));
        facts.add(text("transfer_resistance", format(d.allegianceTransferResistance())));
        castFacts(facts, d.castModifiers());
        temperamentFacts(facts, d.temperament());
        Component name = d.displayName();
        return new PartInfo(id.toString(), nameOf(name), name.getContents() instanceof TranslatableContents,
                lore(server, "wand_cores", id), List.copyOf(facts), 0);
    }

    private static void castFacts(List<AdminSpellFact> facts, WandCastModifiers mods) {
        facts.add(text("cast_modifiers", String.format(Locale.ROOT, "damage ×%s · cooldown ×%s · range ×%s · fizzle %s",
                format(mods.damage()), format(mods.cooldown()), format(mods.range()), signed(mods.fizzle()))));
    }

    private static void temperamentFacts(List<AdminSpellFact> facts, WandTemperament t) {
        if (t.equals(WandTemperament.NEUTRAL)) {
            return;
        }
        List<String> parts = new ArrayList<>();
        if (t.bondGrowth() != 1.0f) parts.add("bond growth ×" + format(t.bondGrowth()));
        if (t.extraWins() != 0) parts.add("defeats " + signed(t.extraWins()));
        if (t.transferBondBonus() != 0f) parts.add("transfer bond " + signed(t.transferBondBonus()));
        if (t.darkArtsBondCost() > 0f) parts.add("resents Dark Arts");
        if (t.bondNeedsDanger()) parts.add("bonds in danger");
        if (t.backfiresInForeignHands()) parts.add("backfires on strangers");
        if (t.foreignHandPower() != 1.0f) parts.add("stranger's hand ×" + format(t.foreignHandPower()));
        if (t.passedOnPower() != 1.0f) parts.add("passed on ×" + format(t.passedOnPower()));
        if (t.masteryNeedsDeathWitness()) parts.add("mastery needs a death witnessed");
        if (t.loyalCooldown() != 1.0f) parts.add("loyal cooldown ×" + format(t.loyalCooldown()));
        facts.add(text("temperament", String.join(", ", parts)));
    }

    private static String nameOf(Component name) {
        return name.getContents() instanceof TranslatableContents t ? t.getKey() : name.getString();
    }

    /** The {@code _lore} note in the part's own datapack file, as written; "" when there is none. */
    static String lore(MinecraftServer server, String directory, Identifier id) {
        Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(),
                id.getNamespace() + "/" + directory + "/" + id.getPath() + ".json");
        try (Reader reader = server.getResourceManager().openAsReader(file)) {
            JsonElement json = JsonParser.parseReader(reader);
            if (json instanceof JsonObject object && object.has("_lore")) {
                return object.get("_lore").getAsString();
            }
        } catch (Exception missing) {
            // A datapack part without a lore note, or one loaded from elsewhere: nothing to show.
        }
        return "";
    }

    private static AdminSpellFact text(String label, String value) {
        return new AdminSpellFact(FACT + label, value, false);
    }

    private static String format(float value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String signed(float value) {
        return (value >= 0 ? "+" : "") + format(value);
    }

    private static String signed(int value) {
        return (value >= 0 ? "+" : "") + value;
    }

    // ── preview and test wands ──

    /** A requested wand, as the client names it: ids and numbers only, every one re-checked here. */
    public record Request(String wood, String core, float lengthInches, String flexibility, String preset) {}

    /**
     * Checks a requested wand against the server: the wood and core exist, a recipe pairs them, no rule withdraws them,
     * the length is a wand's, the flexibility and preset are real. Returns the reason key when it is not makeable.
     */
    public static @Nullable String problem(MinecraftServer server, Request request) {
        Identifier wood = Identifier.tryParse(request.wood());
        Identifier core = Identifier.tryParse(request.core());
        if (wood == null || server.registryAccess().lookupOrThrow(WandDatapackRegistries.WAND_WOOD_REGISTRY).getValue(wood) == null) {
            return "unknown_wood";
        }
        if (core == null || server.registryAccess().lookupOrThrow(WandDatapackRegistries.WAND_CORE_REGISTRY).getValue(core) == null) {
            return "unknown_core";
        }
        if (WandRulesService.recipeFor(server, wood, core).isEmpty()) {
            return "no_recipe";
        }
        if (!WandRules.mayMake(wood, core)) {
            return "withdrawn";
        }
        if (!Float.isFinite(request.lengthInches()) || request.lengthInches() < MIN_LENGTH || request.lengthInches() > MAX_LENGTH) {
            return "bad_length";
        }
        if (flexibility(request.flexibility()) == null) {
            return "bad_flexibility";
        }
        if (!request.preset().isEmpty() && preset(request.preset()).isEmpty()) {
            return "bad_preset";
        }
        return null;
    }

    private static @Nullable WandFlexibility flexibility(String name) {
        for (WandFlexibility flexibility : WandFlexibility.values()) {
            if (flexibility.name().equalsIgnoreCase(name)) {
                return flexibility;
            }
        }
        return null;
    }

    private static Optional<WandPreset> preset(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null ? Optional.empty() : WandPresetRegistry.get(parsed);
    }

    /** The wand a valid request makes: the recipe's own {@code createWand}, unbonded, with the preset's look if any. */
    static ItemStack build(MinecraftServer server, Request request) {
        WandmakingRecipe recipe = WandRulesService.recipeFor(server, Identifier.parse(request.wood()), Identifier.parse(request.core()))
                .orElseThrow();
        ItemStack wand = recipe.createWand(flexibility(request.flexibility()), request.lengthInches());
        preset(request.preset()).ifPresent(p -> wand.set(WandComponents.WAND_CONFIGURATION.get(), p.configuration()));
        return wand;
    }

    /** What the requested wand would be: valid or why not, and — when valid — the numbers a cast would use. */
    public static Preview preview(MinecraftServer server, Request request) {
        String problem = problem(server, request);
        if (problem != null) {
            return new Preview(false, KEY + problem, List.of());
        }
        ItemStack wand = build(server, request);
        WandStats stats = WandStatsResolver.resolve(wand, server.registryAccess());
        List<AdminSpellFact> facts = new ArrayList<>();
        facts.add(text("stat_damage", "×" + format(stats.damageMultiplier())));
        facts.add(text("stat_cooldown", "×" + format(stats.cooldownMultiplier())));
        facts.add(text("stat_range", "×" + format(stats.rangeMultiplier())));
        facts.add(text("stat_fizzle", String.format(Locale.ROOT, "%.1f%%", 100 * stats.fizzleChance())));
        if (!stats.categoryDamageBonus().isEmpty()) {
            List<String> bonuses = new ArrayList<>();
            stats.categoryDamageBonus().forEach((category, bonus) -> bonuses.add(category.name().toLowerCase(Locale.ROOT) + " " + signed(bonus)));
            bonuses.sort(String::compareTo);
            facts.add(text("stat_categories", String.join(", ", bonuses)));
        }
        WandRulesService.recipeFor(server, Identifier.parse(request.wood()), Identifier.parse(request.core())).ifPresent(recipe ->
                facts.add(text("bench", String.format(Locale.ROOT, "bench tier %s · %s–%s in · integrity %s",
                        format(recipe.minimumBenchTier()), format(recipe.resultLengthMin()), format(recipe.resultLengthMax()),
                        format(recipe.resultIntegrity())))));
        return new Preview(true, KEY + "valid", List.copyOf(facts));
    }

    public record Outcome(boolean success, String messageKey, String detail) {}

    /**
     * Puts a test wand of a valid request into the administrator's inventory. Unbonded: the allegiance system decides
     * whom it answers to, exactly as for a wand from the bench. Needs the world capability.
     */
    public static Outcome giveTestWand(ServerPlayer admin, Request request) {
        AdminContext actor = AdminContext.of(admin);
        if (!actor.canRead() || !actor.canModify(AdminCapability.WORLD)) {
            LOGGER.warn("[Admin] Refused test wand for unauthorised {} ({})", admin.getName().getString(), admin.getUUID());
            return new Outcome(false, KEY + "unauthorized", "");
        }
        MinecraftServer server = admin.level().getServer();
        String problem = problem(server, request);
        if (problem != null) {
            return new Outcome(false, KEY + problem, request.wood() + " + " + request.core());
        }
        ItemStack wand = build(server, request);
        if (!(wand.getItem() instanceof WandItem) || !admin.getInventory().add(wand)) {
            return new Outcome(false, KEY + "inventory_full", "");
        }
        LOGGER.info("[Admin] {} took a test wand: {} + {}", admin.getName().getString(), request.wood(), request.core());
        return new Outcome(true, KEY + "given", request.wood() + " + " + request.core());
    }
}
