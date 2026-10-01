package at.koopro.wizardsandbeasts.admin.creature;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.admin.access.AdminCapability;
import at.koopro.wizardsandbeasts.admin.access.AdminContext;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntry;
import at.koopro.wizardsandbeasts.bestiary.BestiaryEntryRegistry;
import at.koopro.wizardsandbeasts.creature.CreatureDefinition;
import at.koopro.wizardsandbeasts.creature.CreatureDefinitionRegistry;
import at.koopro.wizardsandbeasts.creature.Locomotion;
import at.koopro.wizardsandbeasts.creature.Trait;
import at.koopro.wizardsandbeasts.creature.ability.CreatureAbility;
import at.koopro.wizardsandbeasts.creature.bond.BondProfile;
import at.koopro.wizardsandbeasts.creature.bond.BondProfileRegistry;
import at.koopro.wizardsandbeasts.creature.rules.CreatureRules;
import at.koopro.wizardsandbeasts.creature.rules.CreatureRulesService;
import at.koopro.wizardsandbeasts.creature.rules.SpawnConditionNotes;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariant;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariants;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleManager;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads.CreatureSummary;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads.SpawnEntry;
import at.koopro.wizardsandbeasts.network.admin.AdminCreaturePayloads.VariantInfo;
import at.koopro.wizardsandbeasts.network.admin.AdminSpellFact;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Reads the creature roster for the Creature Lab. Nothing here stores anything: every value is read, when asked,
 * from the store the game itself uses —
 * <ul>
 *   <li>stats: the creature's {@code CreatureDefinition} (what {@code GenericBeastEntity#applyDefinition} applies
 *       on spawn), falling back to the {@code EntityType}'s registered attribute supplier for anything the
 *       definition does not set (armour, knockback resistance, and everything for the ten bespoke creatures);</li>
 *   <li>classification and icon: the creature's {@code BestiaryEntry};</li>
 *   <li>taming and breeding: its {@code BondProfile};</li>
 *   <li>natural spawns: the {@code neoforge:add_spawns} biome modifiers loaded into this server's registry, and the
 *       placement conditions {@link SpawnConditionNotes} recorded beside each predicate;</li>
 *   <li>variants: the creature's variant enum through {@link CreatureVariants}, under the current rules.</li>
 * </ul>
 * A property the game does not have for a creature is left out rather than filled with a default.
 */
@NullMarked
public final class CreatureAdminService {

    private static final String FACT = "admin.wizards_and_beasts.creature_fact.";

    private CreatureAdminService() {}

    public static boolean authorised(AdminContext actor) {
        return actor.canRead() && actor.canModify(AdminCapability.CONTENT);
    }

    /** Every roster creature, sorted by id. */
    public static List<String> roster() {
        return List.copyOf(new TreeSet<>(ModCreatures.ROSTER));
    }

    public static @Nullable EntityType<?> typeOf(String creatureId) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, creatureId)).orElse(null);
    }

    public static @Nullable CreatureDefinition definitionOf(String creatureId) {
        return CreatureDefinitionRegistry.get(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, creatureId));
    }

    // ── list ──

    public static List<CreatureSummary> list(MinecraftServer server) {
        Map<String, BestiaryEntry> bestiary = bestiaryByCreature();
        Map<String, List<SpawnEntry>> spawns = spawnEntries(server);
        List<CreatureSummary> out = new ArrayList<>();
        for (String id : roster()) {
            EntityType<?> type = typeOf(id);
            if (type != null) {
                out.add(summary(id, type, bestiary.get(id), spawns.getOrDefault(id, List.of()).size()));
            }
        }
        return List.copyOf(out);
    }

    static CreatureSummary summary(String id, EntityType<?> type, @Nullable BestiaryEntry entry, int spawnEntries) {
        CreatureDefinition def = definitionOf(id);
        Optional<BondProfile> bond = BondProfileRegistry.bonds(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id))
                ? Optional.of(BondProfileRegistry.get(Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, id)))
                : Optional.empty();
        return new CreatureSummary(id, type.getDescriptionId(),
                entry == null ? "" : entry.category().name(),
                entry != null && entry.mmRating().isPresent(),
                temperament(type, def),
                flying(type, def),
                bond.isPresent(),
                bond.flatMap(BondProfile::breeding).isPresent(),
                CreatureRules.naturalSpawn(id),
                spawnEntries,
                CreatureVariants.of(id).size(),
                entry == null ? "" : entry.iconTexture().toString(),
                ModCreatures.BESPOKE_IDS.contains(id));
    }

    /** HOSTILE / NEUTRAL / PASSIVE from the definition; for a bespoke creature only what its type says, else "". */
    private static String temperament(EntityType<?> type, @Nullable CreatureDefinition def) {
        if (def != null) {
            return def.temperament().name();
        }
        return type.getCategory() == MobCategory.MONSTER ? "HOSTILE" : "";
    }

    private static boolean flying(EntityType<?> type, @Nullable CreatureDefinition def) {
        if (def != null) {
            return def.locomotion() == Locomotion.FLYING;
        }
        AttributeSupplier supplier = supplierOf(type);
        return supplier != null && supplier.hasAttribute(Attributes.FLYING_SPEED);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable AttributeSupplier supplierOf(EntityType<?> type) {
        try {
            return DefaultAttributes.hasSupplier(type)
                    ? DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type) : null;
        } catch (RuntimeException notLiving) {
            return null;
        }
    }

    private static Map<String, BestiaryEntry> bestiaryByCreature() {
        Map<String, BestiaryEntry> out = new HashMap<>();
        for (BestiaryEntry entry : BestiaryEntryRegistry.getAll()) {
            entry.entityType().filter(key -> WizardsAndBeastsMod.MODID.equals(key.getNamespace()))
                    .ifPresent(key -> out.put(key.getPath(), entry));
        }
        return out;
    }

    // ── detail ──

    /** One creature's page. Null when the id is not a roster creature. */
    public static @Nullable Detail detail(MinecraftServer server, String id) {
        EntityType<?> type = ModCreatures.ROSTER.contains(id) ? typeOf(id) : null;
        if (type == null) {
            return null;
        }
        List<SpawnEntry> spawns = spawnEntries(server).getOrDefault(id, List.of());
        CreatureSummary summary = summary(id, type, bestiaryByCreature().get(id), spawns.size());
        CreatureDefinition def = definitionOf(id);
        return new Detail(summary, attributes(type, def), behaviour(type, def), abilities(def), spawns,
                SpawnConditionNotes.of(id), variants(id));
    }

    public record Detail(CreatureSummary summary, List<AdminSpellFact> attributes, List<AdminSpellFact> behaviour,
                         List<String> abilities, List<SpawnEntry> spawns, List<String> conditions,
                         List<VariantInfo> variants) {}

    /**
     * The six base values the creature spawns with. A definition value wins exactly where
     * {@code GenericBeastEntity#applyDefinition} applies it (attack only when above zero, flying speed likewise);
     * everything else is the registered supplier's.
     */
    static List<AdminSpellFact> attributes(EntityType<?> type, @Nullable CreatureDefinition def) {
        AttributeSupplier supplier = supplierOf(type);
        List<AdminSpellFact> out = new ArrayList<>();
        add(out, "max_health", def != null ? Optional.of(def.maxHealth()) : base(supplier, Attributes.MAX_HEALTH));
        add(out, "attack_damage", def != null && def.attackDamage() > 0 ? Optional.of(def.attackDamage())
                : base(supplier, Attributes.ATTACK_DAMAGE));
        add(out, "movement_speed", def != null ? Optional.of(def.movementSpeed()) : base(supplier, Attributes.MOVEMENT_SPEED));
        if ((def != null && def.flyingSpeed() > 0) || base(supplier, Attributes.FLYING_SPEED).isPresent()) {
            add(out, "flying_speed", def != null && def.flyingSpeed() > 0 ? Optional.of(def.flyingSpeed())
                    : base(supplier, Attributes.FLYING_SPEED));
        }
        add(out, "follow_range", def != null ? Optional.of(def.followRange()) : base(supplier, Attributes.FOLLOW_RANGE));
        add(out, "armor", base(supplier, Attributes.ARMOR));
        add(out, "knockback_resistance", base(supplier, Attributes.KNOCKBACK_RESISTANCE));
        if (def != null && def.scale() != 1.0f) {
            add(out, "scale", Optional.of((double) def.scale()));
        }
        out.add(new AdminSpellFact(FACT + "hitbox", format(type.getWidth()) + " × " + format(type.getHeight()), false));
        out.add(new AdminSpellFact(FACT + "source", FACT + (def != null ? "source_definition" : "source_supplier"), true));
        return List.copyOf(out);
    }

    private static Optional<Double> base(@Nullable AttributeSupplier supplier, Holder<Attribute> attribute) {
        return supplier != null && supplier.hasAttribute(attribute) ? Optional.of(supplier.getBaseValue(attribute)) : Optional.empty();
    }

    private static void add(List<AdminSpellFact> out, String label, Optional<Double> value) {
        value.ifPresent(v -> out.add(new AdminSpellFact(FACT + label, format(v), false)));
    }

    private static String format(double value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static List<AdminSpellFact> behaviour(EntityType<?> type, @Nullable CreatureDefinition def) {
        List<AdminSpellFact> out = new ArrayList<>();
        out.add(new AdminSpellFact(FACT + "mob_category", type.getCategory().getName(), false));
        if (def == null) {
            out.add(new AdminSpellFact(FACT + "behaviour", FACT + "bespoke_class", true));
            return List.copyOf(out);
        }
        out.add(new AdminSpellFact(FACT + "temperament", lower(def.temperament().name()), false));
        out.add(new AdminSpellFact(FACT + "locomotion", lower(def.locomotion().name()), false));
        out.add(new AdminSpellFact(FACT + "body_plan", lower(def.bodyPlan().name()), false));
        if (!def.traits().isEmpty()) {
            out.add(new AdminSpellFact(FACT + "traits",
                    String.join(", ", def.traits().stream().map(Trait::name).map(CreatureAdminService::lower).toList()), false));
        }
        List<String> profile = new ArrayList<>();
        def.behaviour().idle().ifPresent(p -> profile.add("idle"));
        def.behaviour().sounds().ifPresent(p -> profile.add("sounds"));
        if (!def.behaviour().reactions().isEmpty()) {
            profile.add(def.behaviour().reactions().size() + " reactions");
        }
        def.behaviour().combat().ifPresent(p -> profile.add("combat"));
        if (!profile.isEmpty()) {
            out.add(new AdminSpellFact(FACT + "profile", String.join(", ", profile), false));
        }
        if (!def.clips().isEmpty()) {
            out.add(new AdminSpellFact(FACT + "clips", String.join(", ", def.clips()), false));
        }
        out.add(new AdminSpellFact(FACT + "model", def.model().toString(), false));
        out.add(new AdminSpellFact(FACT + "texture", def.texture().toString(), false));
        out.add(new AdminSpellFact(FACT + "animation", def.animation().toString(), false));
        return List.copyOf(out);
    }

    static List<String> abilities(@Nullable CreatureDefinition def) {
        if (def == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (CreatureAbility ability : def.abilities()) {
            out.add(ability.type().getSerializedName());
        }
        return List.copyOf(out);
    }

    static List<VariantInfo> variants(String id) {
        List<VariantInfo> out = new ArrayList<>();
        for (CreatureVariant variant : CreatureVariants.of(id)) {
            String texture = variant.variantTexture();
            out.add(new VariantInfo(variant.variantId(),
                    texture == null ? "" : WizardsAndBeastsMod.MODID + ":textures/entity/" + texture + ".png",
                    variant.authoredWeight(), CreatureRules.variantWeight(id, variant),
                    CreatureRules.variantEnabled(id, variant)));
        }
        return List.copyOf(out);
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    // ── natural spawns as the server loaded them ──

    /**
     * The {@code neoforge:add_spawns} entries this server loaded, per roster creature. Read from the biome modifier
     * registry, so a datapack that adds, changes or removes a spawn shows up exactly as the world uses it.
     */
    public static Map<String, List<SpawnEntry>> spawnEntries(MinecraftServer server) {
        Map<String, List<SpawnEntry>> out = new HashMap<>();
        var modifiers = server.registryAccess().lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
        modifiers.listElements().forEach(holder -> {
            BiomeModifier modifier = holder.value();
            if (!(modifier instanceof BiomeModifiers.AddSpawnsBiomeModifier spawns)) {
                return;
            }
            String source = holder.key().identifier().toString();
            spawns.spawners().unwrap().forEach(weighted -> {
                MobSpawnSettings.SpawnerData data = weighted.value();
                String id = CreatureRulesService.creatureIdOf(data.type());
                if (id != null) {
                    out.computeIfAbsent(id, k -> new ArrayList<>()).add(new SpawnEntry(
                            describe(spawns.biomes()), weighted.weight(), data.minCount(), data.maxCount(), source));
                }
            });
        });
        return out;
    }

    /** A tag by its name, a short list by its biome ids. */
    private static String describe(HolderSet<Biome> biomes) {
        Optional<net.minecraft.tags.TagKey<Biome>> tag = biomes.unwrapKey();
        if (tag.isPresent()) {
            return "#" + tag.get().location();
        }
        List<String> names = new ArrayList<>();
        for (Holder<Biome> biome : biomes) {
            biome.unwrapKey().ifPresent(key -> names.add(key.identifier().toString()));
            if (names.size() >= 6) {
                names.add("…");
                break;
            }
        }
        return String.join(", ", names);
    }

    /** Whether natural spawning can happen at all right now, globally. */
    public static boolean creaturesModuleOpen() {
        return ModuleManager.isEnabled(Module.CREATURES);
    }
}
