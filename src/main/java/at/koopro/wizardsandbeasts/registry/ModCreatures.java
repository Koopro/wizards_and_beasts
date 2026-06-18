package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.creature.Locomotion;
import at.koopro.wizardsandbeasts.entity.creature.GenericAquaticBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.GenericBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.GenericFlyingBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.GenericGroundBeastEntity;
import at.koopro.wizardsandbeasts.entity.creature.GenericSessileBeastEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-configured generic creature roster. Mirrors the per-creature {@code CreatureDefinition} JSON
 * with the registration-time facts Minecraft requires up front (id, locomotion class, hitbox size).
 * Registers one {@code EntityType} (generic class by locomotion) + one spawn egg + a baseline
 * attribute supplier per creature. Runtime attributes come from the JSON definition on spawn.
 */
public final class ModCreatures {

    /** Registration-time spec for one generic creature. */
    public record Spec(String id, Locomotion loco, float width, float height) {}

    public static final List<Spec> MANIFEST = List.of(
            new Spec("abraxan", Locomotion.FLYING, 2.7f, 2.9f),
            new Spec("acromantula", Locomotion.GROUND, 1.7f, 1.9f),
            new Spec("aethonan", Locomotion.FLYING, 1.7f, 1.9f),
            new Spec("antipodean_opaleye", Locomotion.FLYING, 2.7f, 2.9f),
            new Spec("ashwinder", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("basilisk", Locomotion.GROUND, 2.7f, 2.9f),
            new Spec("billywig", Locomotion.FLYING, 0.45f, 0.45f),
            new Spec("blast_ended_skrewt", Locomotion.GROUND, 1.7f, 1.9f),
            new Spec("boggart", Locomotion.GROUND, 0.95f, 1.25f),
            new Spec("centaur", Locomotion.GROUND, 1.7f, 1.9f),
            new Spec("chimaera", Locomotion.GROUND, 1.7f, 1.9f),
            new Spec("chinese_fireball", Locomotion.FLYING, 2.7f, 2.9f),
            new Spec("clabbert", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("common_welsh_green", Locomotion.FLYING, 2.7f, 2.9f),
            new Spec("crup", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("demiguise", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("diricawl", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("doxy", Locomotion.FLYING, 0.45f, 0.45f),
            new Spec("erumpent", Locomotion.GROUND, 1.7f, 1.9f),
            new Spec("flobberworm", Locomotion.GROUND, 0.45f, 0.45f),
            new Spec("fwooper", Locomotion.FLYING, 0.65f, 0.75f),
            new Spec("giant", Locomotion.GROUND, 2.7f, 2.9f),
            new Spec("graphorn", Locomotion.GROUND, 1.7f, 1.9f),
            new Spec("grindylow", Locomotion.AQUATIC, 0.65f, 0.75f),
            new Spec("hebridean_black", Locomotion.FLYING, 2.7f, 2.9f),
            new Spec("hippogriff", Locomotion.FLYING, 1.7f, 1.9f),
            new Spec("hungarian_horntail", Locomotion.FLYING, 2.7f, 2.9f),
            new Spec("jobberknoll", Locomotion.FLYING, 0.45f, 0.45f),
            new Spec("kelpie", Locomotion.AQUATIC, 1.7f, 1.9f),
            new Spec("kneazle", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("lethifold", Locomotion.GROUND, 0.95f, 1.25f),
            new Spec("maledictus", Locomotion.GROUND, 0.95f, 1.25f),
            new Spec("manticore", Locomotion.GROUND, 1.7f, 1.9f),
            new Spec("merperson", Locomotion.AQUATIC, 0.95f, 1.25f),
            new Spec("norwegian_ridgeback", Locomotion.FLYING, 2.7f, 2.9f),
            new Spec("nundu", Locomotion.GROUND, 2.7f, 2.9f),
            new Spec("obscurus", Locomotion.FLYING, 0.95f, 1.25f),
            new Spec("occamy", Locomotion.GROUND, 1.7f, 1.9f),
            new Spec("peruvian_vipertooth", Locomotion.FLYING, 1.7f, 1.9f),
            new Spec("plimpy", Locomotion.AQUATIC, 0.65f, 0.75f),
            new Spec("qilin", Locomotion.GROUND, 0.95f, 1.25f),
            new Spec("reem", Locomotion.GROUND, 2.7f, 2.9f),
            new Spec("romanian_longhorn", Locomotion.FLYING, 2.7f, 2.9f),
            new Spec("runespoor", Locomotion.GROUND, 0.95f, 1.25f),
            new Spec("sphinx", Locomotion.GROUND, 1.7f, 1.9f),
            new Spec("swedish_short_snout", Locomotion.FLYING, 2.7f, 2.9f),
            new Spec("swooping_evil", Locomotion.FLYING, 0.95f, 1.25f),
            new Spec("thunderbird", Locomotion.FLYING, 1.7f, 1.9f),
            new Spec("troll", Locomotion.GROUND, 2.7f, 2.9f),
            new Spec("ukrainian_ironbelly", Locomotion.FLYING, 2.7f, 2.9f),
            new Spec("unicorn", Locomotion.GROUND, 0.95f, 1.25f),
            new Spec("werewolf", Locomotion.GROUND, 1.7f, 1.9f),
            new Spec("zouwu", Locomotion.GROUND, 2.7f, 2.9f),
            // --- canonical sweep batch 2 (new bestiary entries) ---
            new Spec("knarl", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("griffin", Locomotion.FLYING, 1.7f, 1.9f),
            new Spec("fairy", Locomotion.FLYING, 0.45f, 0.45f),
            new Spec("puffskein", Locomotion.GROUND, 0.45f, 0.45f),
            new Spec("pygmy_puff", Locomotion.GROUND, 0.45f, 0.45f),
            new Spec("chizpurfle", Locomotion.GROUND, 0.45f, 0.45f),
            new Spec("horklump", Locomotion.SESSILE, 0.65f, 0.75f),
            new Spec("red_cap", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("erkling", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("leprechaun", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("wampus_cat", Locomotion.GROUND, 1.7f, 1.9f),
            new Spec("salamander", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("murtlap", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("moke", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("jarvey", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("kappa", Locomotion.AQUATIC, 0.95f, 1.25f),
            new Spec("gnome", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("hippocampus", Locomotion.AQUATIC, 1.7f, 1.9f),
            new Spec("sea_serpent", Locomotion.AQUATIC, 2.7f, 2.9f),
            new Spec("fire_crab", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("imp", Locomotion.GROUND, 0.45f, 0.45f),
            new Spec("porlock", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("nogtail", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("dugbog", Locomotion.GROUND, 0.95f, 1.25f),
            new Spec("mackled_malaclaw", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("ramora", Locomotion.AQUATIC, 0.95f, 1.25f),
            new Spec("shrake", Locomotion.AQUATIC, 0.95f, 1.25f),
            new Spec("tebo", Locomotion.GROUND, 1.7f, 1.9f),
            new Spec("hodag", Locomotion.GROUND, 0.95f, 1.25f),
            new Spec("snallygaster", Locomotion.FLYING, 1.7f, 1.9f),
            new Spec("glumbumble", Locomotion.FLYING, 0.45f, 0.45f),
            new Spec("pogrebin", Locomotion.GROUND, 0.65f, 0.75f),
            new Spec("quintaped", Locomotion.GROUND, 0.95f, 1.25f)
    );

    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<GenericBeastEntity>>> ENTITIES =
            new LinkedHashMap<>();
    public static final Map<String, DeferredItem<SpawnEggItem>> SPAWN_EGGS = new LinkedHashMap<>();

    private ModCreatures() {}

    /** Register every creature's {@code EntityType} + baseline attributes. Call during mod construction. */
    public static void register() {
        for (Spec spec : MANIFEST) {
            DeferredHolder<EntityType<?>, EntityType<GenericBeastEntity>> holder =
                    ModEntities.ENTITY_TYPES.register(spec.id(), () -> EntityType.Builder.of(factory(spec.loco()), MobCategory.CREATURE)
                            .sized(spec.width(), spec.height())
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build(key(spec.id())));
            ENTITIES.put(spec.id(), holder);
            EntityAttributeBindings.queue(holder, () -> baseline(spec.loco()));
        }
    }

    /** Register one spawn egg per creature. Call during mod construction (after {@code ModItems}). */
    public static void registerSpawnEggs() {
        for (Spec spec : MANIFEST) {
            SPAWN_EGGS.put(spec.id(), ModItems.ITEMS.registerItem(spec.id() + "_spawn_egg",
                    props -> new SpawnEggItem(props.spawnEgg(ENTITIES.get(spec.id()).get()))));
        }
    }

    private static EntityType.EntityFactory<GenericBeastEntity> factory(Locomotion loco) {
        return switch (loco) {
            case FLYING -> GenericFlyingBeastEntity::new;
            case AQUATIC -> GenericAquaticBeastEntity::new;
            case SESSILE -> GenericSessileBeastEntity::new;
            case GROUND -> GenericGroundBeastEntity::new;
        };
    }

    private static AttributeSupplier.Builder baseline(Locomotion loco) {
        AttributeSupplier.Builder builder = Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                // ATTACK_DAMAGE is not part of createMobAttributes(); generic creatures need it so the
                // trait-driven melee/combat layer can set a real value per CreatureDefinition on spawn.
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0);
        if (loco == Locomotion.FLYING) {
            builder.add(Attributes.FLYING_SPEED, 0.5);
        }
        return builder;
    }

    private static ResourceKey<EntityType<?>> key(String name) {
        return ResourceKey.create(Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, name));
    }
}
