package at.koopro.wizardsandbeasts.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.module.ModuleTags;
import at.koopro.wizardsandbeasts.registry.ModCreatures;
import at.koopro.wizardsandbeasts.registry.ModEntities;

import java.util.concurrent.CompletableFuture;

/**
 * Declares which module owns each entity type. The mod had no entity-type tag provider before this; the
 * one hand-authored entity tag ({@code dementors}) stays where it is in {@code src/main/resources}.
 *
 * <p>Only mobs are tagged. Spell projectiles, the Patronus and the Protego shield are deliberately left
 * untagged: they are transient effects a live cast owns, already gated at the cast site, and suppressing
 * their tick from a second place would only give a spell two ways to half-die.
 */
public class ModEntityTypeTagsProvider extends EntityTypeTagsProvider {

    public ModEntityTypeTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, WizardsAndBeastsMod.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookupProvider) {
        var creatures = tag(ModuleTags.entityTypes(Module.CREATURES));
        for (var entity : ModCreatures.ENTITIES.values()) {
            creatures.add(entity.get());
        }
        creatures.add(
                ModEntities.GOBLIN_TELLER.get(), ModEntities.NIFFLER.get(), ModEntities.BABY_NIFFLER.get(),
                ModEntities.BOWTRUCKLE.get(), ModEntities.CORNISH_PIXIE.get(), ModEntities.THESTRAL.get(),
                ModEntities.PHOENIX.get(), ModEntities.AUGUREY.get(), ModEntities.MOONCALF.get(),
                ModEntities.STREELER.get(), ModEntities.RUNESPOOR.get(), ModEntities.HIDEBEHIND.get());

        tag(ModuleTags.entityTypes(Module.AZKABAN)).add(ModEntities.DEMENTOR.get());
        tag(ModuleTags.entityTypes(Module.BROOM_FLIGHT)).add(ModEntities.BROOM.get());
    }
}
