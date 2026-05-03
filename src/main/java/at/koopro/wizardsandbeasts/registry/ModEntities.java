package at.koopro.wizardsandbeasts.registry;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.entity.BroomEntity;
import at.koopro.wizardsandbeasts.entity.FormMannequinEntity;
import at.koopro.wizardsandbeasts.entity.GoblinTellerEntity;
import at.koopro.wizardsandbeasts.entity.NifflerEntity;
import at.koopro.wizardsandbeasts.entity.SpellProjectileEntity;
import at.koopro.wizardsandbeasts.entity.WizardingThrownEntity;
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

    public static final DeferredHolder<EntityType<?>, EntityType<GoblinTellerEntity>> GOBLIN_TELLER =
            EntityHelper.register(ENTITY_TYPES, "goblin_teller", GoblinTellerEntity::new, MobCategory.CREATURE, 0.6f, 1.5f);
    static { EntityAttributes.queue(GOBLIN_TELLER, GoblinTellerEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<NifflerEntity>> NIFFLER =
            EntityHelper.register(ENTITY_TYPES, "niffler", NifflerEntity::new, MobCategory.CREATURE, 0.5f, 0.4f);
    static { EntityAttributes.queue(NIFFLER, NifflerEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<FormMannequinEntity>> FORM_MANNEQUIN =
            EntityHelper.register(ENTITY_TYPES, "form_mannequin", FormMannequinEntity::new, MobCategory.MISC, 0.6f, 1.8f);
    static { EntityAttributes.queue(FORM_MANNEQUIN, FormMannequinEntity::createAttributes); }

    public static final DeferredHolder<EntityType<?>, EntityType<WizardingThrownEntity>> WIZARDING_THROWN =
            EntityHelper.register(ENTITY_TYPES, "wizarding_thrown", WizardingThrownEntity::new, MobCategory.MISC, 0.25f, 0.25f, 8, 2);
}
