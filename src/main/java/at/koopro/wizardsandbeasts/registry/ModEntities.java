package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.azkaban.DementorEntity;
import at.koopro.wizardsandbeasts.entity.beast.AugureyEntity;
import at.koopro.wizardsandbeasts.entity.beast.BowtruckleEntity;
import at.koopro.wizardsandbeasts.entity.beast.CornishPixieEntity;
import at.koopro.wizardsandbeasts.entity.beast.HidebehindEntity;
import at.koopro.wizardsandbeasts.entity.beast.MooncalfEntity;
import at.koopro.wizardsandbeasts.entity.beast.PhoenixEntity;
import at.koopro.wizardsandbeasts.entity.beast.RunespoorEntity;
import at.koopro.wizardsandbeasts.entity.beast.StreelerEntity;
import at.koopro.wizardsandbeasts.entity.beast.ThestralEntity;
import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.entity.creature.BeastHexProjectile;
import at.koopro.wizardsandbeasts.entity.form.FormMannequinEntity;
import at.koopro.wizardsandbeasts.entity.goblin.GoblinTellerEntity;
import at.koopro.wizardsandbeasts.entity.niffler.BabyNifflerEntity;
import at.koopro.wizardsandbeasts.entity.niffler.NifflerEntity;
import at.koopro.wizardsandbeasts.entity.spell.PatronusEntity;
import at.koopro.wizardsandbeasts.entity.spell.ProtegoShieldEntity;
import at.koopro.wizardsandbeasts.entity.spell.SpellProjectileEntity;
import at.koopro.wizardsandbeasts.entity.spell.WizardingThrownEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WizardsAndBeastsMod.MODID);

    private ModEntities() {}

    public static final DeferredHolder<EntityType<?>, EntityType<BroomEntity>> BROOM =
            EntityHelper.registerMisc(ENTITY_TYPES, "broom", BroomEntity::new, 1.5f, 0.6f);

    public static final DeferredHolder<EntityType<?>, EntityType<SpellProjectileEntity>> SPELL_PROJECTILE =
            EntityHelper.register(ENTITY_TYPES, "spell_projectile", SpellProjectileEntity::new, MobCategory.MISC, 0.25f, 0.25f, 8, 2);

    public static final DeferredHolder<EntityType<?>, EntityType<BeastHexProjectile>> BEAST_HEX_PROJECTILE =
            EntityHelper.register(ENTITY_TYPES, "beast_hex_projectile", BeastHexProjectile::new, MobCategory.MISC, 0.25f, 0.25f, 8, 2);

    public static final DeferredHolder<EntityType<?>, EntityType<PatronusEntity>> PATRONUS =
            EntityHelper.register(ENTITY_TYPES, "patronus", PatronusEntity::new, MobCategory.MISC, 0.6f, 1.2f, 8, 2);
    static { EntityAttributeBindings.queue(PATRONUS, PatronusEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<ProtegoShieldEntity>> PROTEGO_SHIELD =
            EntityHelper.register(ENTITY_TYPES, "protego_shield", ProtegoShieldEntity::new,
                    MobCategory.MISC, 0.5f, 0.5f, 16, 2);

    public static final DeferredHolder<EntityType<?>, EntityType<GoblinTellerEntity>> GOBLIN_TELLER =
            EntityHelper.register(ENTITY_TYPES, "goblin_teller", GoblinTellerEntity::new, MobCategory.CREATURE, 0.6f, 1.5f);
    static { EntityAttributeBindings.queue(GOBLIN_TELLER, GoblinTellerEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<NifflerEntity>> NIFFLER =
            EntityHelper.register(ENTITY_TYPES, "niffler", NifflerEntity::new, MobCategory.CREATURE, 0.4f, 0.5f);
    static { EntityAttributeBindings.queue(NIFFLER, NifflerEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<BabyNifflerEntity>> BABY_NIFFLER =
            EntityHelper.register(ENTITY_TYPES, "baby_niffler", BabyNifflerEntity::new, MobCategory.CREATURE, 0.2f, 0.25f);
    static { EntityAttributeBindings.queue(BABY_NIFFLER, BabyNifflerEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<FormMannequinEntity>> FORM_MANNEQUIN =
            EntityHelper.register(ENTITY_TYPES, "form_mannequin", FormMannequinEntity::new, MobCategory.MISC, 0.6f, 1.8f);
    static { EntityAttributeBindings.queue(FORM_MANNEQUIN, FormMannequinEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<WizardingThrownEntity>> WIZARDING_THROWN =
            EntityHelper.register(ENTITY_TYPES, "wizarding_thrown", WizardingThrownEntity::new, MobCategory.MISC, 0.25f, 0.25f, 8, 2);

    public static final DeferredHolder<EntityType<?>, EntityType<DementorEntity>> DEMENTOR =
            EntityHelper.register(ENTITY_TYPES, "dementor", DementorEntity::new, MobCategory.MONSTER, 0.9f, 3.2f, 10, 3);
    static { EntityAttributeBindings.queue(DEMENTOR, DementorEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<BowtruckleEntity>> BOWTRUCKLE =
            EntityHelper.register(ENTITY_TYPES, "bowtruckle", BowtruckleEntity::new, MobCategory.CREATURE, 0.4f, 0.8f);
    static { EntityAttributeBindings.queue(BOWTRUCKLE, BowtruckleEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<CornishPixieEntity>> CORNISH_PIXIE =
            EntityHelper.register(ENTITY_TYPES, "cornish_pixie", CornishPixieEntity::new, MobCategory.CREATURE, 0.4f, 0.6f);
    static { EntityAttributeBindings.queue(CORNISH_PIXIE, CornishPixieEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<ThestralEntity>> THESTRAL =
            EntityHelper.register(ENTITY_TYPES, "thestral", ThestralEntity::new, MobCategory.CREATURE, 1.4f, 1.8f);
    static { EntityAttributeBindings.queue(THESTRAL, ThestralEntity::createAttributes); }

    /** Bespoke — not part of the generic {@code CreatureDefinition} pipeline; see {@link RunespoorEntity}. */
    public static final DeferredHolder<EntityType<?>, EntityType<RunespoorEntity>> RUNESPOOR =
            EntityHelper.register(ENTITY_TYPES, "runespoor", RunespoorEntity::new, MobCategory.CREATURE, 0.95f, 1.25f);
    static { EntityAttributeBindings.queue(RUNESPOOR, RunespoorEntity::createAttributes); }

    /** Bespoke — not part of the generic {@code CreatureDefinition} pipeline; see {@link HidebehindEntity}. */
    public static final DeferredHolder<EntityType<?>, EntityType<HidebehindEntity>> HIDEBEHIND =
            EntityHelper.register(ENTITY_TYPES, "hidebehind", HidebehindEntity::new, MobCategory.CREATURE, 0.9f, 1.9f);
    static { EntityAttributeBindings.queue(HIDEBEHIND, HidebehindEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<PhoenixEntity>> PHOENIX =
            EntityHelper.register(ENTITY_TYPES, "phoenix", PhoenixEntity::new, MobCategory.CREATURE, 0.7f, 1.0f);
    static { EntityAttributeBindings.queue(PHOENIX, PhoenixEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<AugureyEntity>> AUGUREY =
            EntityHelper.register(ENTITY_TYPES, "augurey", AugureyEntity::new, MobCategory.CREATURE, 0.5f, 0.7f);
    static { EntityAttributeBindings.queue(AUGUREY, AugureyEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<MooncalfEntity>> MOONCALF =
            EntityHelper.register(ENTITY_TYPES, "mooncalf", MooncalfEntity::new, MobCategory.CREATURE, 0.7f, 1.2f);
    static { EntityAttributeBindings.queue(MOONCALF, MooncalfEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<StreelerEntity>> STREELER =
            EntityHelper.register(ENTITY_TYPES, "streeler", StreelerEntity::new, MobCategory.CREATURE, 0.7f, 0.7f);
    static { EntityAttributeBindings.queue(STREELER, StreelerEntity::createAttributes); }
}
